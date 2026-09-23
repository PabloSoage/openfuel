# openfuel

Spanish fuel prices on a map — **what each litre is made of, and what you actually pay.**

An Android app built on the Ministry's open data: every station of your region with
today's prices, a per-litre breakdown into VAT, hydrocarbon excise and the rest, the
loyalty discounts published for each station applied to the price, and the price
history of the last days. Offline-first, no keys, no accounts, no trackers.

> **Status:** 0.1.0 in development.

## What it does

- **Map of your region** — download one or more communities or provinces (0.2–2 MB), or
  all of Spain (12 MB). Brand logos, prices coloured cheap / middle / dear, and where
  labels collide the cheaper station wins.
- **Station sheet** — every fuel sold, address, opening hours, *Open in Maps*, share.
- **Tax breakdown** — VAT, *Impuesto sobre Hidrocarburos* and the rest, per litre, with
  the legal source of the rates. 2026 has a temporary, month-by-month regime whose rates
  sometimes depend on CPI thresholds; the schedule is data
  ([`tax-schedule.json`](core/src/main/resources/tax-schedule.json)) and installed apps
  pick up corrections without an update.
- **Discounts** — the plans each station publishes (percentage or cents per litre).
  Tick the ones you hold and the map ranks stations by the price *you* pay.
- **History** — the last 7 / 30 / 90 days of a station, downloaded per province and fuel
  only when you open it (~85 KB per day).
- **Comparisons** — how many cents per litre of margin a station keeps above the cheapest
  one nearby (exact: taxes and product cost are the same for both), and how today's price
  compares with its own recent average.
- **List view**, favourites, brand filter, English and Spanish.

## Where the data comes from

| Data | Source |
|---|---|
| Stations, prices, history | [Ministerio para la Transición Ecológica y el Reto Demográfico](https://sede.minetur.gob.es/es-ES/datosabiertos/catalogo/precios-carburantes), `ServiciosRESTCarburantes` open-data service |
| Brand logos, discount plans | The backend of [geoportalgasolineras.es](https://geoportalgasolineras.es). Optional: when it fails the app shows a badge and no plans |
| Tax rates | BOE: Ley 38/1992 art. 50, Real Decreto-ley 7/2026, Real Decreto-ley 18/2026; INE CPI series for the conditional months |
| Map | © OpenStreetMap contributors, tiles by [OpenFreeMap](https://openfreemap.org), rendered with [MapLibre](https://maplibre.org) |

Nothing proprietary is stored in this repository. Logos are fetched at runtime and kept
only on the device.

## Building

Requirements: JDK 21 (downloaded automatically through the toolchain resolver) and the
Android SDK with API 37.

```bash
./gradlew :core:test            # parsing, brands, tax maths, discounts — no device needed
./gradlew :android:assembleDebug
node tools/check-resources.mjs  # translations and placeholders, in a second
```

## Architecture

```
core/     Kotlin/JVM, no Android: API parsing, brand normalisation, tax schedule and
          breakdown, discounts, distances, comparisons. All the unit tests live here.
android/  Compose + Material 3, Room (offline-first: the UI only reads the database),
          DataStore, MapLibre Native.
tools/    Resource checker; archive script.
web/      Browser version (no build step).
```

## Archive

A scheduled workflow stores a compact daily snapshot of every station's prices on the
`archive` branch, so long-term history does not depend on how far back the official
service goes.

## License

[MIT](LICENSE).
