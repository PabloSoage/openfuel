# Data

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
