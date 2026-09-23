# openfuel archive

Daily snapshots of the prices of every road fuel station in Spain, from the official
historic service `EstacionesTerrestresHist/{dd-MM-yyyy}` of the Ministerio para la
Transición Ecológica y el Reto Demográfico. Written by `tools/archive/snapshot.py` on
the `main` branch; this branch holds data only.

- `prices/YYYY/MM/YYYY-MM-DD.json.gz` — `{"date", "published", "fuels": {FUEL: {IDEESS: price}}}`
  with prices in €/litre and fuel names as in `core/.../model/Fuel.kt`.
- `stations.json` — station metadata, rewritten only when it changes (see git history).
