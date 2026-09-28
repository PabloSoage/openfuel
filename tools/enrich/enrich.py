#!/usr/bin/env python3
"""Discount plans and brand logos for the web version (openfuel).

The geoportal answers browsers from other origins with 403, so the web cannot
ask it for plans or logos the way the app does. This script does it every 3 hours
and writes static files into a directory that is the checkout of the
`enrichment` branch; the Pages workflow publishes them under data/:

    plans.json          {"generated", "covered", "total", "plans": {id: {...}}, "stations": {IDEESS: [planId, ...]}}
    logos/<brand>.png   one logo per brand, from the geoportal's imagenEESS
    logos/index.json    {brand: "geoportal"}
    state.json          when each station last answered and how many plans it had (not published)

The geoportal throttles a client after a few thousand requests: on 28 September
2026 a GitHub runner went from 1 s to 25 s per request after ~9,000 stations
and the job was killed at 5 hours with nothing written. So each run asks one
batch (2,500 by default), least recently asked first, and stops early when the
latency climbs; 8 runs a day cover Spain within the day. Brands whose every station has
answered with no plan (low-cost ones) are only sampled. A station not asked, or
whose request fails, keeps its last answer. Standard library only.

The geoportal sends its certificate without the FNMT intermediate; curl and
browsers cope, Python does not, so the intermediate ships next to this script
(fnmt-ac-componentes-informaticos.pem, from http://www.cert.fnmt.es/certs/ACCOMP.crt,
valid until 2028-06-24) and is added to the default trust store.
"""
import argparse
import base64
import collections
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
BATCH = 2500
# Stop when the last 60 requests average over 4 s (1 s is normal) or half of them fail.
THROTTLE_WINDOW = 60
THROTTLE_MEAN_S = 4.0


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


# Stops asking: past the deadline, or once the geoportal is throttling this machine.
DEADLINE = float("inf")
STOP = []  # non-empty once throttling is detected; holds the reason
SKIPPED = "skipped"
LATENCIES = collections.deque(maxlen=THROTTLE_WINDOW)


def plans_of(station_id):
    if STOP or time.monotonic() > DEADLINE:
        return station_id, SKIPPED
    time.sleep(PAUSE_S)
    started = time.monotonic()
    try:
        result = get(f"{GEOPORTAL}/{station_id}/planesDescuentoEstacion", timeout=20)
    except Exception:
        result = None
    LATENCIES.append((time.monotonic() - started, result is None))
    if len(LATENCIES) == THROTTLE_WINDOW and not STOP:
        mean = sum(t for t, _ in LATENCIES) / THROTTLE_WINDOW
        failures = sum(f for _, f in LATENCIES)
        if mean > THROTTLE_MEAN_S or failures > THROTTLE_WINDOW // 2:
            STOP.append(f"last {THROTTLE_WINDOW} requests: {mean:.1f} s mean, {failures} failed")
    return station_id, result


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


def load(path, default):
    if os.path.exists(path):
        with open(path, encoding="utf-8") as f:
            return json.load(f)
    return default


def main():
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("--out", required=True, help="checkout of the enrichment branch")
    parser.add_argument("--batch", type=int, default=BATCH, help="stations asked in this run, least recently asked first")
    parser.add_argument("--limit", type=int, default=0, help="only the first N stations of the official list (testing)")
    parser.add_argument("--budget-min", type=float, default=60,
                        help="stop asking for plans after this many minutes and write what there is")
    args = parser.parse_args()
    global DEADLINE
    DEADLINE = time.monotonic() + args.budget_min * 60
    today = dt.date.today().isoformat()

    classify, wrong_logo = load_brands()
    stations = [
        (s["IDEESS"].strip(), s.get("Rótulo") or "")
        for s in get_official()["ListaEESSPrecio"] if (s.get("IDEESS") or "").strip()
    ]
    if args.limit:
        stations = stations[: args.limit]
    current = {sid for sid, _ in stations}
    brand_of = {sid: classify(sign) or INDEPENDENT for sid, sign in stations}
    by_brand = {}
    for sid, _ in stations:
        by_brand.setdefault(brand_of[sid], []).append(sid)

    plans_path = os.path.join(args.out, "plans.json")
    state_path = os.path.join(args.out, "state.json")
    previous = load(plans_path, {})
    # {IDEESS: [date last answered, number of plans]}, and {brand: date its logo was last tried}.
    state = load(state_path, {})
    seen = {sid: v for sid, v in state.get("stations", {}).items() if sid in current}
    logo_tried = state.get("logos", {})

    # A brand whose every station has been asked, none with a plan, is only sampled.
    without = sorted(
        b for b, ids in by_brand.items()
        if all(sid in seen for sid in ids) and not any(seen[sid][1] for sid in ids)
    )
    random.seed(today)
    sample = {sid for b in without for sid in random.sample(by_brand[b], min(SKIP_SAMPLE, len(by_brand[b])))}
    candidates = [sid for sid, _ in stations if brand_of[sid] not in without or sid in sample]
    # Never asked first, then the oldest answers; ties in random order, so a batch
    # is spread over Spain instead of following the Ministry's list.
    order = {sid: random.random() for sid in candidates}
    candidates.sort(key=lambda sid: (seen.get(sid, [""])[0], order[sid]))
    wanted = candidates[: args.batch]
    fresh = sum(1 for sid in current if sid in seen)
    print(f"{len(stations)} stations, {fresh} asked before; this run asks {len(wanted)}, least recently asked first; "
          f"sampled brands without plans: {', '.join(without) or 'none'}", flush=True)

    # Probe first: if the geoportal does not answer this machine, say so in a minute.
    started = time.monotonic()
    results = {}
    for sid in wanted[:PROBE]:
        results[sid] = plans_of(sid)[1]
    probe = [r for r in results.values() if r is not SKIPPED]
    probe_failed = sum(r is None for r in probe)
    per_request = (time.monotonic() - started) / max(len(probe), 1)
    print(f"probe: {len(probe) - probe_failed}/{len(probe)} answered, {per_request:.2f} s per request", flush=True)
    if probe_failed > len(probe) // 2:
        # A warning, not a failure: the next run, 3 hours later, picks these stations up.
        print("::warning::the geoportal does not answer this machine today: nothing written", flush=True)
        return 0

    rest = [sid for sid in wanted if sid not in results]
    with concurrent.futures.ThreadPoolExecutor(WORKERS) as pool:
        for done, (station_id, result) in enumerate(pool.map(plans_of, rest), 1):
            results[station_id] = result
            if done % 500 == 0:
                print(f"{done}/{len(rest)} stations, {time.monotonic() - started:.0f} s", flush=True)

    not_asked = sum(r is SKIPPED for r in results.values())
    if STOP:
        print(f"the geoportal is slowing this machine down ({STOP[0]}): stopped", flush=True)
    elif not_asked:
        print("out of time: stopped", flush=True)
    results = {sid: r for sid, r in results.items() if r is not SKIPPED}

    # Everything not answered now keeps what the last answer said.
    plans = dict(previous.get("plans", {}))
    by_station = {sid: ids for sid, ids in previous.get("stations", {}).items() if sid in current}
    failed = 0
    for station_id, result in results.items():
        if result is None:
            failed += 1
            continue
        ids = sorted({p["id"] for p in result if isinstance(p, dict) and p.get("id") is not None})
        for p in result:
            if isinstance(p, dict) and p.get("id") is not None:
                plans[str(p["id"])] = plan_entry(p)
        if ids:
            by_station[station_id] = ids
        else:
            by_station.pop(station_id, None)
        seen[station_id] = [today, len(ids)]

    if results and failed > len(results) // 2:
        print(f"{failed} of {len(results)} plan requests failed: not overwriting", file=sys.stderr)
        return 2

    # Logos: brands with none stored, or not tried for a week; stations spread over the brand.
    logo_dir = os.path.join(args.out, "logos")
    os.makedirs(logo_dir, exist_ok=True)
    week_ago = (dt.date.today() - dt.timedelta(days=7)).isoformat()
    index = {}
    for key, ids in sorted(by_brand.items()):
        path = os.path.join(logo_dir, f"{key}.png")
        if key == INDEPENDENT:
            continue
        if key in wrong_logo:
            if os.path.exists(path):
                os.remove(path)  # stored by an earlier version: it is another brand's logo
            continue
        due = not os.path.exists(path) or logo_tried.get(key, "") <= week_ago
        if due and not STOP and time.monotonic() < DEADLINE + 10 * 60:
            logo_tried[key] = today
            for sid in spread(ids, LOGO_ATTEMPTS):
                png = logo_of(sid)
                if png:
                    with open(path, "wb") as f:
                        f.write(png)
                    break
        if os.path.exists(path):
            index[key] = "geoportal"
    with open(os.path.join(logo_dir, "index.json"), "w", encoding="utf-8") as f:
        json.dump(index, f, sort_keys=True, indent=0)
        f.write("\n")

    used = {str(i) for ids in by_station.values() for i in ids}
    covered = sum(1 for sid in current if sid in seen)
    payload = {
        "generated": dt.datetime.now(dt.timezone.utc).strftime("%Y-%m-%dT%H:%MZ"),
        # Stations with an answer at some point; the rest have not been asked yet.
        "covered": covered,
        "total": len(current),
        "plans": {k: v for k, v in sorted(plans.items(), key=lambda kv: int(kv[0])) if k in used},
        "stations": dict(sorted(by_station.items())),
    }
    with open(plans_path, "w", encoding="utf-8") as f:
        json.dump(payload, f, ensure_ascii=False, separators=(",", ":"))
        f.write("\n")
    with open(state_path, "w", encoding="utf-8") as f:
        json.dump({"stations": dict(sorted(seen.items())), "logos": dict(sorted(logo_tried.items()))},
                  f, separators=(",", ":"))
        f.write("\n")

    print(f"{len(results)} stations asked, {failed} failed, {time.monotonic() - started:.0f} s; "
          f"covered {covered}/{len(current)}; {len(by_station)} stations with plans, {len(payload['plans'])} plans, "
          f"{len(index)} logos")
    return 0


if __name__ == "__main__":
    sys.exit(main())
