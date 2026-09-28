#!/usr/bin/env python3
"""Discount plans and brand logos for the web version (openfuel).

The geoportal answers browsers from other origins with 403, so the web cannot
ask it for plans or logos the way the app does. This script does it once a week
and writes static files into a directory that is the checkout of the
`enrichment` branch; the Pages workflow publishes them under data/:

    plans.json          {"generated", "plans": {id: {...}}, "stations": {IDEESS: [planId, ...]}}
    logos/<brand>.png   one logo per brand, from the geoportal's imagenEESS
    logos/index.json    {brand: "geoportal"}

Polite by design: three requests in flight, a pause after each, and a station
whose request fails keeps the plans of the previous run. The geoportal slows a
client down after a long run, so brands that had no plan at any station in the
last full run (low-cost and independent stations, about 5,000 of 11,500) are
only sampled, and asked in full again if the sample finds a plan. Standard
library only.

The geoportal sends its certificate without the FNMT intermediate; curl and
browsers cope, Python does not, so the intermediate ships next to this script
(fnmt-ac-componentes-informaticos.pem, from http://www.cert.fnmt.es/certs/ACCOMP.crt,
valid until 2028-06-24) and is added to the default trust store.
"""
import argparse
import base64
import concurrent.futures
import datetime as dt
import json
import os
import random
import re
import ssl
import sys
import time
import unicodedata
import urllib.error
import urllib.request

OFFICIAL = "https://sedeaplicaciones.minetur.gob.es/ServiciosRESTCarburantes/PreciosCarburantes/EstacionesTerrestres/"
GEOPORTAL = "https://geoportalgasolineras.es/geoportal/rest"
USER_AGENT = "openfuel-enrich (+https://github.com/PabloSoage/openfuel)"
ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
BRANDS = os.path.join(ROOT, "core", "src", "main", "resources", "brands.json")
INTERMEDIATE = os.path.join(os.path.dirname(os.path.abspath(__file__)), "fnmt-ac-componentes-informaticos.pem")
KINDS = {1: "PERCENT", 2: "CENTS_PER_LITRE"}
INDEPENDENT = "independent"
LOGO_ATTEMPTS = 8
PROBE = 30
SKIP_SAMPLE = 15
WORKERS = 3
PAUSE_S = 0.25


def tls_context():
    ctx = ssl.create_default_context()
    # The Ministry's server, at times, speaks only TLS 1.2 with RSA key exchange, which
    # Python stopped offering in 3.10: it then resets the handshake (see snapshot.py).
    ctx.set_ciphers("ECDHE+AESGCM:ECDHE+CHACHA20:AES256-GCM-SHA384:AES128-GCM-SHA256")
    if os.path.exists(INTERMEDIATE):
        ctx.load_verify_locations(cafile=INTERMEDIATE)
    return ctx


CTX = tls_context()


def get(url, timeout=60):
    req = urllib.request.Request(url, headers={"Accept": "application/json", "User-Agent": USER_AGENT})
    with urllib.request.urlopen(req, timeout=timeout, context=CTX) as resp:
        return json.loads(resp.read().decode("utf-8-sig"))


# The station list is the one request the whole run depends on, so it gets about
# 12 minutes of retries on top of the cipher fix above.
OFFICIAL_RETRY_WAITS = (30, 60, 120, 240, 300)


def get_official():
    for attempt, wait in enumerate((*OFFICIAL_RETRY_WAITS, None), start=1):
        try:
            return get(OFFICIAL, timeout=180)
        except urllib.error.HTTPError as e:
            if e.code < 500 or wait is None:
                raise
            error = f"HTTP {e.code}"
        except OSError as e:  # URLError, connection reset, timeout
            if wait is None:
                raise
            error = str(getattr(e, "reason", e))
        print(f"station list: attempt {attempt} failed ({error}), retrying in {wait} s", file=sys.stderr)
        time.sleep(wait)


def normalise(sign):
    s = unicodedata.normalize("NFD", sign)
    s = "".join(c for c in s if not unicodedata.combining(c))
    return re.sub(r"\s+", " ", s.upper()).strip()


def load_brands():
    with open(BRANDS, encoding="utf-8") as f:
        brands = json.load(f)["brands"]
    compiled = [(b["key"], [re.compile(p) for p in b["patterns"]]) for b in brands]
    # The geoportal's logo for these brands is wrong (Petronor gets Repsol's): never stored.
    wrong_logo = {b["key"] for b in brands if b.get("preferLogoUrl")}

    def classify(sign):
        n = normalise(sign)
        for key, patterns in compiled:
            if any(p.search(n) for p in patterns):
                return key
        return None
    return classify, wrong_logo


def spread(items, n):
    """n items spread over the list, not the first n: some brands carry a logo on few stations."""
    step = max(len(items) // n, 1)
    return items[::step][:n]


# Past this, stations are no longer asked and keep last week's plans: the workflow
# kills the job at 300 min, and a killed job writes nothing at all.
DEADLINE = float("inf")
SKIPPED = "skipped"


def plans_of(station_id):
    if time.monotonic() > DEADLINE:
        return station_id, SKIPPED
    time.sleep(PAUSE_S)
    try:
        return station_id, get(f"{GEOPORTAL}/{station_id}/planesDescuentoEstacion", timeout=20)
    except Exception:
        return station_id, None


def logo_of(station_id):
    time.sleep(PAUSE_S)
    try:
        b64 = (get(f"{GEOPORTAL}/{station_id}/busquedaEstacion", timeout=20).get("imagenEESS") or "").strip()
        png = base64.b64decode(b64) if b64 else b""
        return png if png[:4] == b"\x89PNG" else None
    except Exception:
        return None


def plan_entry(p):
    kind = (p.get("tipoDescuento") or {})
    audience = (p.get("tipoDestinatario") or {})
    return {
        "name": (p.get("nombre") or "").strip(),
        "description": (p.get("descripcion") or "").strip(),
        "amount": p.get("cifraDescuento") or 0,
        "kind": KINDS.get(kind.get("id"), "OTHER"),
        "kindLabel": kind.get("tipo") or "",
        "audienceId": audience.get("id"),
        "audienceLabel": audience.get("tipo") or "",
        "operator": ((p.get("operador") or {}).get("nombre") or "").strip(),
    }


def main():
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("--out", required=True, help="checkout of the enrichment branch")
    parser.add_argument("--limit", type=int, default=0, help="only the first N stations (testing)")
    parser.add_argument("--budget-min", type=float, default=240,
                        help="stop asking for plans after this many minutes and write what there is")
    args = parser.parse_args()
    global DEADLINE
    DEADLINE = time.monotonic() + args.budget_min * 60

    classify, wrong_logo = load_brands()
    stations = [
        (s["IDEESS"].strip(), s.get("Rótulo") or "")
        for s in get_official()["ListaEESSPrecio"] if (s.get("IDEESS") or "").strip()
    ]
    if args.limit:
        stations = stations[: args.limit]
    brand_of = {sid: classify(sign) or INDEPENDENT for sid, sign in stations}
    by_brand = {}
    for sid, _ in stations:
        by_brand.setdefault(brand_of[sid], []).append(sid)

    previous_path = os.path.join(args.out, "plans.json")
    previous = {}
    if os.path.exists(previous_path):
        with open(previous_path, encoding="utf-8") as f:
            previous = json.load(f)

    # Brands with no plan anywhere in the last full run are only sampled this time.
    skipped = set(previous.get("brandsWithoutPlans", [])) if previous.get("full") else set()
    random.seed(dt.date.today().isoformat())
    sample = {b: set(random.sample(by_brand.get(b, []), min(SKIP_SAMPLE, len(by_brand.get(b, []))))) for b in skipped}
    wanted = [sid for sid, _ in stations if brand_of[sid] not in skipped or sid in sample[brand_of[sid]]]
    print(f"{len(stations)} stations; {len(stations) - len(wanted)} skipped from brands without plans: "
          f"{', '.join(sorted(skipped)) or 'none'}", flush=True)

    # Probe first: if the geoportal does not answer this machine (it may refuse
    # or slow down runners), say so in a minute instead of timing out in hours.
    started = time.monotonic()
    results = {}
    for sid in wanted[:PROBE]:
        results[sid] = plans_of(sid)[1]
    probe = list(results.values())
    probe_failed = sum(r is None for r in probe)
    per_request = (time.monotonic() - started) / max(len(probe), 1)
    print(f"probe: {len(probe) - probe_failed}/{len(probe)} answered, {per_request:.2f} s per request", flush=True)
    if probe_failed > len(probe) // 2:
        print("the geoportal does not answer this machine: nothing written", file=sys.stderr)
        return 3

    def fetch(ids):
        with concurrent.futures.ThreadPoolExecutor(WORKERS) as pool:
            for done, (station_id, result) in enumerate(pool.map(plans_of, ids), 1):
                results[station_id] = result
                if done % 1000 == 0:
                    print(f"{done}/{len(ids)} stations, {time.monotonic() - started:.0f} s", flush=True)

    fetch([sid for sid in wanted if sid not in results])

    # A sampled brand that turned out to have plans is asked in full.
    revived = {b for b in skipped if any(results.get(sid) for sid in sample[b])}
    if revived:
        print(f"brands with plans again: {', '.join(sorted(revived))}", flush=True)
        fetch([sid for b in revived for sid in by_brand[b] if sid not in results])

    out_of_time = sum(r is SKIPPED for r in results.values())
    if out_of_time:
        print(f"out of time: {out_of_time} stations not asked, they keep last week's plans", flush=True)
    results = {sid: r for sid, r in results.items() if r is not SKIPPED}
    truncated = bool(args.limit or out_of_time)

    plans = dict(previous.get("plans", {}))
    by_station = {sid: ids for sid, ids in previous.get("stations", {}).items() if sid not in results} if out_of_time else {}
    failed = 0
    for station_id, result in results.items():
        if result is None:
            failed += 1
            if station_id in previous.get("stations", {}):
                by_station[station_id] = previous["stations"][station_id]
            continue
        ids = []
        for p in result:
            if isinstance(p, dict) and p.get("id") is not None:
                plans[str(p["id"])] = plan_entry(p)
                ids.append(p["id"])
        if ids:
            by_station[station_id] = sorted(set(ids))

    if failed > len(results) // 2:
        print(f"{failed} of {len(results)} plan requests failed: not overwriting", file=sys.stderr)
        return 2

    # Brands where every station asked answered with no plan: sampled next week.
    asked = {}
    for sid, result in results.items():
        if result is not None:
            b = brand_of[sid]
            asked.setdefault(b, [0, 0])
            asked[b][0] += 1
            asked[b][1] += 1 if result else 0
    without = sorted(b for b, (n, with_plans) in asked.items() if n and with_plans == 0)

    # Logos: one per brand, from stations spread over the brand's list.
    logo_dir = os.path.join(args.out, "logos")
    os.makedirs(logo_dir, exist_ok=True)
    index = {}
    for key, ids in sorted(by_brand.items()):
        path = os.path.join(logo_dir, f"{key}.png")
        if key == INDEPENDENT:
            continue
        if key in wrong_logo:
            if os.path.exists(path):
                os.remove(path)  # stored by an earlier version: it is another brand's logo
            continue
        # Logos get 20 minutes past the plans' deadline; a brand not tried keeps its old file.
        attempts = spread(ids, LOGO_ATTEMPTS) if time.monotonic() < DEADLINE + 20 * 60 else []
        for sid in attempts:
            png = logo_of(sid)
            if png:
                with open(path, "wb") as f:
                    f.write(png)
                index[key] = "geoportal"
                break
        else:
            if os.path.exists(path):
                index[key] = "geoportal"  # kept from a previous run
    with open(os.path.join(logo_dir, "index.json"), "w", encoding="utf-8") as f:
        json.dump(index, f, sort_keys=True, indent=0)
        f.write("\n")

    used = {str(i) for ids in by_station.values() for i in ids}
    payload = {
        "generated": dt.datetime.now(dt.timezone.utc).strftime("%Y-%m-%dT%H:%MZ"),
        # Only a full run may decide which brands to skip next time.
        "full": not truncated,
        "brandsWithoutPlans": without if not truncated else sorted(skipped),
        "plans": {k: v for k, v in sorted(plans.items(), key=lambda kv: int(kv[0])) if k in used},
        "stations": dict(sorted(by_station.items())),
    }
    with open(previous_path, "w", encoding="utf-8") as f:
        json.dump(payload, f, ensure_ascii=False, sort_keys=False, separators=(",", ":"))
        f.write("\n")

    print(f"{len(results)} stations asked, {len(by_station)} with plans, {len(payload['plans'])} plans, "
          f"{failed} failed, {len(index)} logos; without plans: {', '.join(without) or 'none'}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
