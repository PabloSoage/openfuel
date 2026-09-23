# 🚀 openfuel Beta v0.1.0

Spanish fuel prices on a map — what each litre is made of, and what you actually pay.

> **Context:** first release. An Android app and a web page on top of the Ministry's open
> data for every road fuel station in Spain: today's prices, the per-litre split into VAT,
> hydrocarbon excise and the rest, the loyalty discounts each station publishes applied to
> the price, what each brand says its fuel contains, and the station's recent history. From
> the idea to this release in one day, 23 September 2026 — the API research, the design and
> the code in the morning, written without a compiler; the first build, a review, an
> emulator and a round of fixes in the afternoon.
>
> **Method:** every figure in the app comes from a measurement or a legal text, written down
> in the private notebook before the code that uses it. The tax rates cite their BOE article
> period by period; the additive claims cite the brand page and the day it was read. Where
> something is believed rather than measured, section 8 says so.
>
> **Deliverable:** `android-arm64-v8a-release.apk` and `android-x86_64-release.apk`, minSdk 26,
> English and Spanish, 15 MB each after R8. A web version with the same features at
> [pablosoage.github.io/openfuel](https://pablosoage.github.io/openfuel/). 52 Kotlin files, 84
> core tests, no keys, no accounts, no trackers.

---

## 1. What it is

Pick a region — one or more communities or provinces, or all of Spain — and the app downloads
that area's stations and keeps them on the device. From there:

1. a **map** with every station's brand and price, coloured cheap / middle / dear by thirds,
   the cheaper station winning wherever labels collide;
2. a **station sheet** with every fuel sold, the tax breakdown of a litre, the discount plans
   published for that station, what the brand says about each fuel, the price history, and
   how it compares with the stations around it;
3. a **list**, sorted by the price you pay or by distance, with favourites and a brand filter.

Room is the single source for the UI: the network only ever writes into it, so the last
prices stay on screen with no signal.

---

## 2. Change catalogue

| ID | Area | Item | Type |
|----|------|------|------|
| **D1** | Data | Official prices by community (`FiltroCCAA`) or province, not the 12 MB national file | feature |
| **D2** | Data | Coordinates checked against Spain: swapped pairs put right, impossible ones skipped | fix |
| **D3** | Data | `brands.json`: 3,538 free-text signs → 17 brands, shared by app, web and scripts | feature |
| **D4** | Data | `fuel-products.json`: commercial name and additive claims per brand and fuel, with sources | feature |
| **F1** | Tax | `tax-schedule.json`: 2026's month-by-month temporary regime, one legal source per period | feature |
| **F2** | Tax | Installed apps take a newer schedule from the repository, validated, without an update | feature |
| **F3** | Tax | Periods that depend on an unverified CPI figure are shown as unconfirmed | feature |
| **A1** | App | Region picker, map, fuel selector, status line with the Ministry's own timestamp | feature |
| **A2** | App | Station sheet: prices, tax bar, open in Maps, share, favourite | feature |
| **A3** | App | Discount plans per station; tick the ones you hold and the map ranks by effective price | feature |
| **A4** | App | Each fuel expands to what its brand claims about it, with the source | feature |
| **A5** | App | Relative margin against the cheapest within 5 / 10 / 25 km, and the list of those stations | feature |
| **A6** | App | Price history of 7 / 30 / 90 days, downloaded per province and fuel when first needed | feature |
| **A7** | App | Today's price against the station's own average | feature |
| **A8** | App | Logo cascade: geoportal → Wikimedia Commons → a badge with the brand's initials | feature |
| **A9** | App | List view, favourites, brand filter, English / Spanish per app | feature |
| **A10** | App | Release builds through R8 with resource shrinking: 27 MB → 15 MB | feature |
| **W1** | Web | The same features in the browser, no build step, logic ported to `core.js` | feature |
| **J1** | Jobs | `archive`: a daily compact snapshot of every station's prices on an orphan branch | feature |
| **J2** | Jobs | `enrich`: weekly discount plans and logos for the web, which the geoportal will not serve | feature |

---

## 3. The data, as measured

### 3.1 The official service

The Ministry's `ServiciosRESTCarburantes` answers with JSON prefixed by a UTF-8 BOM, keys with
accents, spaces and parentheses (`Rótulo`, `Longitud (WGS84)`, `C.P.`), decimal commas and an
empty string for "not sold". It sends `Access-Control-Allow-Origin: *`, which is what lets the
web version exist without a backend.

The national file is 12 MB. It turned out not to be needed: `FiltroCCAA` and `FiltroProvincia`
exist for current prices **and for history**, and history can be filtered down to one province
and one product — about 85 KB a day. So nothing is mirrored: the app asks for the days it is
missing, when a station is opened, and never asks for the same day twice.

It is also not clean. The 23 September national file had **three stations at 0,0 and one with
latitude and longitude swapped** — a station in Tui drawn on the coast of Tanzania. That one
station made the map frame the whole world. Pairs that land in Spain once swapped are swapped
back; the rest are skipped and counted.

### 3.2 Brands

`Rótulo` is free text: 3,538 distinct signs for 11,486 stations, 55 of them containing REPSOL.
Signs are normalised (no accents, upper case, single spaces) and matched against word-bounded
patterns in order. Petronor comes before Repsol because some Petronor signs mention the group;
Cepsa and Moeve are one brand mid-rebrand. Anything unmatched is independent and gets a grey
badge with two letters of its sign.

### 3.3 Logos

The geoportal carries a brand logo inside some stations' records and not others. Sampled on 23
September, twelve stations per brand across Spain:

| Every time | Sometimes | Never |
|---|---|---|
| Repsol, Moeve, Galp, BP, Shell, Ballenoil, Plenergy, Q8, DISA, Petronor | bonÀrea 7/12, Petroprix 8/12, Carrefour 1/12 | Alcampo, Avia, Eroski, Esclatoil |

The brands' own sites were meant to be the second step. They are not: `apple-touch-icon.png`
answers 404, 403 or an HTML page on almost every one. The app now tries eight stations of a
brand, spread over the list, then a logo from **Wikimedia Commons** (public domain or CC BY-SA)
listed in `brands.json`. One wrinkle: the geoportal gives Petronor stations the **Repsol** logo,
so Petronor prefers its own. Only Esclatoil is left with a badge.

---

## 4. What a litre is made of

```
base  = price / (1 + VAT)      the excise sits inside the VAT base
VAT   = price − base
rest  = base − excise          product + logistics + margin
```

2026 is not a normal year. Real Decreto-ley 7/2026 and 18/2026 cut the hydrocarbon excise month
by month, and some months take one branch or another depending on whether the INE's annual CPI
for fuels rose more than 15 %. Diesel in September 2026 pays **0.179 €/l** of excise instead of
the base law's 0.379; petrol 95 pays 0.42269 and 98 pays 0.45392, because they fell on the other
side of the threshold.

So the schedule is data, not code. Every period cites its article; the app validates the file
(contiguous periods, sane rates, allowed VAT) and adopts a newer version from the repository once
a day. A tax change is a commit, not a release.

A diesel at 1.769 €/l in September:

| | €/l | share |
|---|---:|---:|
| VAT 21 % | 0.307 | 17.4 % |
| Hydrocarbon excise | 0.179 | 10.1 % |
| Product, logistics and margin | 1.283 | 72.5 % |

No breakdown in Canarias, Ceuta and Melilla: they have no VAT (IGIC and IPSI instead).

---

## 5. What you pay, and what the brand says

**Discounts.** The geoportal publishes each station's plans with a figure and a type: a
percentage of the total, or cents per litre. Plans for everyone can be ticked; fleets,
professionals and "other" types are shown and never applied. One plan at a time — nothing in
the data says they stack. With a plan ticked, the map and the list rank by the price you pay,
and the list still shows the pump price beside it.

**Additives.** Each petrol and diesel row in the sheet expands to what the brand says about that
fuel, with the page it comes from and the date it was read:

| Brand | Standard | Premium |
|---|---|---|
| Repsol | Diesel e+ Neotech, Efitec 95 Neotech | Diesel e+10 Neotech, Efitec 95 Premium, Efitec 98 |
| Moeve | — | Diésel MAX, Gasolina MAX 95 / 98 (formerly Óptima) |
| Galp | Evologic | Hi-Energy |
| bp | ACTIVE technology | Ultimate with ACTIVE technology |
| Shell | FuelSave Diesel / 95 | V-Power Diesel, V-Power |

These are marketing claims, labelled as such. The API says whether a station sells premium
diesel, not which formulation is in the tank, and every road fuel sold in Spain already meets EN
590 / EN 228 — the app says that under every product, and when a brand has nothing researched.

**Comparisons.** Two stations on the same day buy the same product and pay the same excise, so
the difference of what is left after taxes **is** the difference of margins — exact, no outside
data. Ten cents at the pump is 8.26 cents of margin, because VAT takes its share of the gap.
"See the N stations within 10 km" lists them, cheapest first by the price you pay; each one opens
its own sheet.

---

## 6. The web

The web does what the app does: region, map with icons and prices, the full station panel,
discount plans you can tick, history straight from the official API, list, favourites, brand
filter, settings, English and Spanish. It shares the three data files with the app and ports
`:core` to `core.js`. MapLibre is pinned with SRI, a newer region choice wins over a slower
older answer, and nothing is downloaded until a region is chosen.

One thing it cannot do is what the app does for plans and logos. The geoportal answers any
request carrying another site's `Origin` with **403**, so a page on GitHub Pages cannot ask it.
Logos come from the Wikimedia fallbacks and a weekly job; plans come **only** from that job —
see section 8.

---

## 7. Verified

| | |
|---|---|
| `:core` tests | ✅ 84 |
| Debug build, no compiler warnings | ✅ |
| Release build with R8, installed and run | ✅ 15 MB |
| Galicia downloaded, 727 stations, framed on the region | ✅ |
| Tax breakdown against the September 2026 rates | ✅ 0.179 €/l diesel |
| Plans at a station that publishes none | ✅ |
| History: 30 days fetched per province and fuel, chart with gaps | ✅ |
| Relative margin and the list of stations within 10 km | ✅ 38 stations |
| Logos: 13 brands from the geoportal, 3 from Wikimedia | ✅ |
| Distances once a location fix arrives | ✅ |
| Web: region, map, station panel, list, history | ✅ in Chrome on Android |
| `archive` from a GitHub runner | ✅ 11,496 stations, 153 KB a day |
| Tax schedule served from the public repository | ✅ HTTP 200 |

All of the app on an emulator (Pixel 10 Pro XL image) and the web in Chrome on that emulator,
against the live services.

---

## 8. What is **not** verified, or not done

- **On a real phone.** Everything above ran on an emulator. A report of discount plans that
  never stopped loading at a Plenergy station could not be reproduced there — the geoportal
  answered `[]` in two seconds — so this release shortens its timeouts, loads plans and history
  in parallel, and says "the geoportal did not answer", with a retry, instead of spinning.
- **Discount plans on the web.** The weekly `enrich` job worked from GitHub for a 30-station test
  and was then throttled: the full run's probe got 1 answer in 30, at 22 s each. The probe did
  its job — it stopped in a minute and wrote nothing — but the web has plans for only 17
  stations. The fix is to run it from a machine the geoportal accepts.
- **October 2026 onwards.** The temporary regime ends on 30 September according to the texts
  known on the 23rd. From 1 October the app uses the base law and flags it unconfirmed until the
  BOE says otherwise; the schedule will change by commit.
- **June 2026** depends on an INE aggregate that was not retrieved, and is flagged unconfirmed.
- **The additive table** covers five brands. Low-cost brands publish no additive package of
  their own, and Moeve's standard range makes no claim to summarise.

---

## 9. Building

```bash
./gradlew :core:test                 # parsing, brands, products, tax maths, discounts
./gradlew :android:assembleRelease   # arm64-v8a and x86_64, R8 on
node tools/check-resources.mjs       # translations and placeholders, in a second
```

JDK 21 (downloaded by the toolchain resolver), Android SDK with API 37. The web has no build
step: `web/` plus the three JSON files from `core/src/main/resources/`.

## 📦 Installation

```bash
adb install android-arm64-v8a-release.apk
```

> Or install the APK normally. The first launch asks for a region; a community is under 2 MB.

> **Location** is optional: it centres the map and gives distances, and never leaves the phone.

---

## 10. Credits and licence

MIT. Prices: Ministerio para la Transición Ecológica y el Reto Demográfico, open data. Discount
plans and most logos: geoportalgasolineras.es. Other logos: Wikimedia Commons. Tax rates: BOE
and INE. Map © OpenStreetMap contributors, tiles by OpenFreeMap, rendered with MapLibre. No logo
is stored in the source tree.

**Full Changelog**: [commits up to v0.1.0-beta](https://github.com/PabloSoage/openfuel/commits/v0.1.0-beta)
