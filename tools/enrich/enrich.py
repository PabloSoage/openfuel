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
whose request fails keeps the plans of the previous run. Standard library only.
"""
import argparse
import base64
import concurrent.futures
import datetime as dt
import json
import os
import re
import sys
import time
import unicodedata
import urllib.request

OFFICIAL = "https://sedeaplicaciones.minetur.gob.es/ServiciosRESTCarburantes/PreciosCarburantes/EstacionesTerrestres/"
GEOPORTAL = "https://geoportalgasolineras.es/geoportal/rest"
USER_AGENT = "openfuel-enrich (+https://github.com/PabloSoage/openfuel)"
ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
BRANDS = os.path.join(ROOT, "core", "src", "main", "resources", "brands.json")
KINDS = {1: "PERCENT", 2: "CENTS_PER_LITRE"}
LOGO_ATTEMPTS = 8
PROBE = 30
WORKERS = 3
PAUSE_S = 0.25


def get(url, timeout=60):
    req = urllib.request.Request(url, headers={"Accept": "application/json", "User-Agent": USER_AGENT})
    with urllib.request.urlopen(req, timeout=timeout) as resp:
        return json.loads(resp.read().decode("utf-8-sig"))


def normalise(sign):
    s = unicodedata.normalize("NFD", sign)
    s = "".join(c for c in s if not unicodedata.combining(c))
    return re.sub(r"\s+", " ", s.upper()).strip()


def classifier():
    with open(BRANDS, encoding="utf-8") as f:
        brands = json.load(f)["brands"]
    compiled = [(b["key"], [re.compile(p) for p in b["patterns"]]) for b in brands]

    def classify(sign):
        n = normalise(sign)
        for key, patterns in compiled:
            if any(p.search(n) for p in patterns):
                return key
        return None
    return classify


def plans_of(station_id):
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
    args = parser.parse_args()

    stations = [
        (s["IDEESS"].strip(), s.get("Rótulo") or "")
        for s in get(OFFICIAL, timeout=180)["ListaEESSPrecio"] if (s.get("IDEESS") or "").strip()
    ]
    if args.limit:
        stations = stations[: args.limit]

    previous_path = os.path.join(args.out, "plans.json")
    previous = {}
    if os.path.exists(previous_path):
        with open(previous_path, encoding="utf-8") as f:
            previous = json.load(f)

    # Probe first: if the geoportal does not answer this machine (it may refuse
    # runners outside Spain), say so in a minute instead of timing out in hours.
    started = time.monotonic()
    probe = [plans_of(sid)[1] for sid, _ in stations[:PROBE]]
    probe_failed = sum(r is None for r in probe)
    per_request = (time.monotonic() - started) / max(len(probe), 1)
    print(f"probe: {len(probe) - probe_failed}/{len(probe)} answered, {per_request:.2f} s per request", flush=True)
    if probe_failed > len(probe) // 2:
        print("the geoportal does not answer this machine: nothing written", file=sys.stderr)
        return 3

    plans = dict(previous.get("plans", {}))
    by_station = {}
    failed = 0
    with concurrent.futures.ThreadPoolExecutor(WORKERS) as pool:
        for done, (station_id, result) in enumerate(pool.map(plans_of, [sid for sid, _ in stations]), 1):
            if done % 1000 == 0:
                print(f"{done}/{len(stations)} stations, {failed} failed, {time.monotonic() - started:.0f} s", flush=True)
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

    if failed > len(stations) // 2:
        print(f"{failed} of {len(stations)} plan requests failed: not overwriting", file=sys.stderr)
        return 2

    # Logos: one per brand, from the first stations of that brand that carry one.
    classify = classifier()
    by_brand = {}
    for sid, sign in stations:
        key = classify(sign)
        if key:
            by_brand.setdefault(key, []).append(sid)
    logo_dir = os.path.join(args.out, "logos")
    os.makedirs(logo_dir, exist_ok=True)
    index = {}
    for key, ids in sorted(by_brand.items()):
        for sid in ids[:LOGO_ATTEMPTS]:
            png = logo_of(sid)
            if png:
                with open(os.path.join(logo_dir, f"{key}.png"), "wb") as f:
                    f.write(png)
                index[key] = "geoportal"
                break
        else:
            if os.path.exists(os.path.join(logo_dir, f"{key}.png")):
                index[key] = "geoportal"  # kept from a previous run
    with open(os.path.join(logo_dir, "index.json"), "w", encoding="utf-8") as f:
        json.dump(index, f, sort_keys=True, indent=0)
        f.write("\n")

    used = {str(i) for ids in by_station.values() for i in ids}
    payload = {
        "generated": dt.datetime.now(dt.timezone.utc).strftime("%Y-%m-%dT%H:%MZ"),
        "plans": {k: v for k, v in sorted(plans.items(), key=lambda kv: int(kv[0])) if k in used},
        "stations": dict(sorted(by_station.items())),
    }
    with open(previous_path, "w", encoding="utf-8") as f:
        json.dump(payload, f, ensure_ascii=False, sort_keys=False, separators=(",", ":"))
        f.write("\n")

    print(f"{len(stations)} stations, {len(by_station)} with plans, {len(payload['plans'])} plans, "
          f"{failed} failed, {len(index)} logos")
    return 0


if __name__ == "__main__":
    sys.exit(main())
