# openfuel

Spanish fuel prices on a map — **what each litre is made of, and what you actually pay.**

An Android app built on the Ministry's open data: every station of your region with
today's prices, a per-litre breakdown into VAT, hydrocarbon excise and the rest, the
loyalty discounts published for each station applied to the price, and the price
history of the last days. Offline-first, no keys, no accounts, no trackers.

> **Status:** Beta v0.2.0. Releases: [GitHub](https://github.com/PabloSoage/openfuel/releases).

## What it does

- **Map of your region** — download one or more communities or provinces (0.2–2 MB), or
  all of Spain (12 MB). Brand logos, prices coloured cheap / middle / dear, and where
  labels collide the cheaper station wins.
- **Station sheet** — every fuel sold, address, opening hours, *Open in Maps*, share.
- **What the brand says about each fuel** — commercial name (e.g. *Repsol Diesel e+* vs
  *e+10*, *bp Ultimate*, *Moeve MAX*) and the additive claims the brand makes, each with
  its source page and the date it was checked. Marketing statements, labelled as such
  ([`fuel-products.json`](core/src/main/resources/fuel-products.json)).
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
  one nearby (exact: taxes and product cost are the same for both), the list of every
  station in that radius, and how today's price compares with its own recent average.
- **Search** a town, postcode, address or station: the downloaded stations answer as you
  type, with no network; any other address goes to OpenStreetMap when you submit. A place
  outside your region offers to add its province.
- **My location** on the map, list view, favourites, a brand filter (all, none, or just the
  ones you pick), English and Spanish.
- **Updates** — Settings shows when GitHub has a newer release, with its notes, and downloads
  and installs the APK for your phone. Checked when the app starts; can be turned off.
- **Web version** at [pablosoage.github.io/openfuel](https://pablosoage.github.io/openfuel/),
  no install, with the same features. Its discount plans come from a weekly job (below);
  until that job completes a national run, the web has plans for only a few stations.

## Where the data comes from

| Data | Source |
|---|---|
| Stations, prices, history | [Ministerio para la Transición Ecológica y el Reto Demográfico](https://sede.minetur.gob.es/es-ES/datosabiertos/catalogo/precios-carburantes), `ServiciosRESTCarburantes` open-data service |
| Brand logos, discount plans | The backend of [geoportalgasolineras.es](https://geoportalgasolineras.es). Optional: when it fails the app says so and keeps what it had |
| Logos the geoportal lacks | [Wikimedia Commons](https://commons.wikimedia.org), listed in [`brands.json`](core/src/main/resources/brands.json). Ten are public domain; the [Ballenoil](https://commons.wikimedia.org/wiki/File:Logo_Ballenoil.svg) and [Petroprix](https://commons.wikimedia.org/wiki/File:Logo_Petroprix.svg) logos are by Autopistero20502020, [CC BY-SA 4.0](https://creativecommons.org/licenses/by-sa/4.0/), credited in the app and the web |
| Additive claims | Each brand's own website, linked per product |
| Address search | [OpenStreetMap Nominatim](https://nominatim.openstreetmap.org), only when you submit a search, at most once a second |
| New versions | [GitHub releases](https://github.com/PabloSoage/openfuel/releases) of this repository, once per start unless turned off in Settings |
| Tax rates | BOE: Ley 38/1992 art. 50, Real Decreto-ley 7/2026, Real Decreto-ley 18/2026; INE CPI series for the conditional months |
| Map | © OpenStreetMap contributors, tiles by [OpenFreeMap](https://openfreemap.org), rendered with [MapLibre](https://maplibre.org) |

No logo is stored in the source tree. The app fetches them at runtime and keeps them on
the device; for the web, which the geoportal does not serve, a weekly job caches them
with the discount plans on the `enrichment` branch.

The official data has its own mistakes: on 2026-09-23 three stations came at 0,0 and one
with latitude and longitude swapped. Swapped pairs are put right, the rest are skipped.

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
tools/    Resource checker; daily price archive; weekly plans-and-logos enrichment.
web/      Browser version (no build step): the same data files, logic ported to core.js.
```

## Scheduled jobs

| Workflow | What | Where |
|---|---|---|
| `archive` | Daily compact snapshot of every station's prices, so long-term history does not depend on how far back the official service goes | `archive` branch |
| `enrich` | Weekly discount plans and one logo per brand, for the web. Brands with no plan at any station in the last full run are only sampled (15 stations), and asked in full again if the sample finds one | `enrichment` branch |
| `pages` | Publishes `web/` with the data files and the enrichment under `data/` | GitHub Pages |

## License

[MIT](LICENSE).
