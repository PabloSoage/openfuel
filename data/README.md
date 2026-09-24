# Data

Three data files live in `core/src/main/resources/`, embedded in the app and published
with the web:

| File | What |
|---|---|
| `tax-schedule.json` | Excise and VAT per period, with the legal source of each |
| `brands.json` | Sign patterns → brand, colours, and a Wikimedia logo where the geoportal has none. A logo under a licence that asks for attribution (CC BY-SA) must carry `logoCredit` (author, licence, licence URL, Commons page); `BrandCatalogTest` checks it and both "About" screens show it |
| `fuel-products.json` | Per brand and fuel: commercial name and additive claims, with source and date |

## Tax schedule

The tax schedule the app uses lives in one place:

```
core/src/main/resources/tax-schedule.json
```

It is embedded in the app **and** fetched by installed apps from
`https://raw.githubusercontent.com/PabloSoage/openfuel/main/core/src/main/resources/tax-schedule.json`,
so a tax change is a commit to that file with a higher `version`, not a release.

Rules the app enforces before accepting a new copy (`TaxSchedule.parse`):

- periods sorted and contiguous, no gaps or overlaps, only the last one open-ended;
- every rate between 0 and 1,000 € per 1,000 litres; VAT one of 4, 5, 10, 21 %;
- every period cites its legal source;
- `version` higher than the copy already on the device.

`confirmed: false` makes the app say *"the tax rate for this date could not be
confirmed"* next to the breakdown. Use it whenever a rate depends on something that
has not been checked.

## Fuel products

Every entry cites the brand page it summarises and the day it was checked. Claims are the
brand's, in short; keep the wording neutral ("per the brand") and never add a product
whose page could not be read. `FuelProductsTest` rejects a brand/fuel listed twice, an
unknown fuel, a non-https source or missing English claims.
