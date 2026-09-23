#!/usr/bin/env python3
"""Daily archive of Spanish fuel prices (openfuel, phase 3).

Downloads one day of the official historic service for all of Spain and stores it
compactly in a directory that is the checkout of the `archive` branch:

    prices/YYYY/MM/YYYY-MM-DD.json.gz   {"date", "published", "fuels": {FUEL: {IDEESS: price}}}
    stations.json                       {IDEESS: {sign, lat, lon, ...}}, rewritten only if it changed
    README.md                           written once

Standard library only. Output is deterministic (sorted keys, gzip mtime 0), so a
re-run of the same day produces no diff.
"""
import argparse
import datetime as dt
import gzip
import json
import os
import sys
import urllib.request
from zoneinfo import ZoneInfo

BASE = "https://sedeaplicaciones.minetur.gob.es/ServiciosRESTCarburantes/PreciosCarburantes"
USER_AGENT = "openfuel-archive (+https://github.com/PabloSoage/openfuel)"

# Same names as core/.../model/Fuel.kt, so the app and the archive agree.
FUELS = {
    "Precio Gasoleo A": "GOA",
    "Precio Gasoleo Premium": "GOA_PREMIUM",
    "Precio Gasolina 95 E5": "G95E5",
    "Precio Gasolina 95 E10": "G95E10",
    "Precio Gasolina 95 E5 Premium": "G95E5_PREMIUM",
    "Precio Gasolina 98 E5": "G98E5",
    "Precio Gasolina 98 E10": "G98E10",
    "Precio Biodiesel": "BIODIESEL",
    "Precio Gasoleo B": "GOB",
    "Precio Gases licuados del petróleo": "GLP",
    "Precio Gas Natural Comprimido": "GNC",
    "Precio Gas Natural Licuado": "GNL",
    "Precio Diésel Renovable": "DIESEL_RENOVABLE",
    "Precio Adblue": "ADBLUE",
    "Precio Gasolina 95 E85": "G95E85",
    "Precio Bioetanol": "BIOETANOL",
    "Precio Hidrogeno": "HIDROGENO",
    "Precio Gasolina 95 E25": "G95E25",
    "Precio Gasolina Renovable": "GASOLINA_RENOVABLE",
    "Precio Biogas Natural Comprimido": "BIOGAS_GNC",
    "Precio Biogas Natural Licuado": "BIOGAS_GNL",
}

STATION_FIELDS = {
    "sign": "Rótulo", "address": "Dirección", "locality": "Localidad", "municipality": "Municipio",
    "province": "Provincia", "provinceId": "IDProvincia", "ccaaId": "IDCCAA", "postalCode": "C.P.",
    "schedule": "Horario",
}

README = """# openfuel archive

Daily snapshots of the prices of every road fuel station in Spain, from the official
historic service `EstacionesTerrestresHist/{dd-MM-yyyy}` of the Ministerio para la
Transición Ecológica y el Reto Demográfico. Written by `tools/archive/snapshot.py` on
the `main` branch; this branch holds data only.

- `prices/YYYY/MM/YYYY-MM-DD.json.gz` — `{"date", "published", "fuels": {FUEL: {IDEESS: price}}}`
  with prices in €/litre and fuel names as in `core/.../model/Fuel.kt`.
- `stations.json` — station metadata, rewritten only when it changes (see git history).
"""


def number(raw):
    raw = (raw or "").strip()
    if not raw:
        return None
    try:
        return float(raw.replace(",", "."))
    except ValueError:
        return None


def fetch(day):
    url = f"{BASE}/EstacionesTerrestresHist/{day.strftime('%d-%m-%Y')}"
    req = urllib.request.Request(url, headers={"Accept": "application/json", "User-Agent": USER_AGENT})
    with urllib.request.urlopen(req, timeout=120) as resp:
        body = resp.read().decode("utf-8-sig")
    data = json.loads(body)
    if data.get("ResultadoConsulta", "OK") != "OK":
        raise RuntimeError(f"ResultadoConsulta={data.get('ResultadoConsulta')}")
    return data


def convert(data):
    fuels, stations = {}, {}
    for s in data.get("ListaEESSPrecio", []):
        sid = (s.get("IDEESS") or "").strip()
        lat, lon = number(s.get("Latitud")), number(s.get("Longitud (WGS84)"))
        if not sid or lat is None or lon is None:
            continue
        meta = {k: (s.get(v) or "").strip() for k, v in STATION_FIELDS.items()}
        meta["lat"], meta["lon"] = lat, lon
        stations[sid] = meta
        for field, fuel in FUELS.items():
            price = number(s.get(field))
            if price and price > 0:
                fuels.setdefault(fuel, {})[sid] = price
    return fuels, stations


def write_if_changed(path, text):
    if os.path.exists(path):
        with open(path, encoding="utf-8") as f:
            if f.read() == text:
                return False
    os.makedirs(os.path.dirname(path) or ".", exist_ok=True)
    with open(path, "w", encoding="utf-8") as f:
        f.write(text)
    return True


def main():
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("--out", required=True, help="checkout of the archive branch")
    parser.add_argument("--date", help="dd-mm-yyyy; default: yesterday in Europe/Madrid")
    args = parser.parse_args()

    if args.date:
        day = dt.datetime.strptime(args.date, "%d-%m-%Y").date()
    else:
        day = dt.datetime.now(ZoneInfo("Europe/Madrid")).date() - dt.timedelta(days=1)

    data = fetch(day)
    fuels, stations = convert(data)
    if not stations:
        print(f"{day}: empty response, nothing written", file=sys.stderr)
        return 2

    payload = json.dumps(
        {"date": day.isoformat(), "published": data.get("Fecha"), "fuels": fuels},
        ensure_ascii=False, sort_keys=True, separators=(",", ":"),
    ).encode("utf-8")
    price_path = os.path.join(args.out, "prices", f"{day:%Y}", f"{day:%m}", f"{day.isoformat()}.json.gz")
    os.makedirs(os.path.dirname(price_path), exist_ok=True)
    with open(price_path, "wb") as raw, gzip.GzipFile(fileobj=raw, mode="wb", mtime=0, filename="") as gz:
        gz.write(payload)

    stations_changed = write_if_changed(
        os.path.join(args.out, "stations.json"),
        json.dumps(stations, ensure_ascii=False, sort_keys=True, indent=0) + "\n",
    )
    write_if_changed(os.path.join(args.out, "README.md"), README)

    size = os.path.getsize(price_path)
    print(f"{day}: {len(stations)} stations, {sum(len(v) for v in fuels.values())} prices, "
          f"{size} bytes gzipped, stations.json {'updated' if stations_changed else 'unchanged'}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
