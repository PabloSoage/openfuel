// openfuel web — the pure logic, a port of :core (Kotlin). No DOM here.
// The data files (tax-schedule.json, brands.json, fuel-products.json) are the
// same ones the Android app embeds, copied next to this file by the pages workflow.

export const OFFICIAL = 'https://sedeaplicaciones.minetur.gob.es/ServiciosRESTCarburantes/PreciosCarburantes';

// key, API price field, IDProducto (history), excise epígrafe, en, es, pickable
export const FUELS = [
  ['GOA', 'Precio Gasoleo A', 4, '1.3', 'Diesel', 'Gasóleo A', true],
  ['GOA_PREMIUM', 'Precio Gasoleo Premium', 5, '1.3', 'Premium diesel', 'Gasóleo premium', true],
  ['G95E5', 'Precio Gasolina 95 E5', 1, '1.2.2', 'Petrol 95 E5', 'Gasolina 95 E5', true],
  ['G95E10', 'Precio Gasolina 95 E10', 23, '1.2.2', 'Petrol 95 E10', 'Gasolina 95 E10', true],
  ['G95E5_PREMIUM', 'Precio Gasolina 95 E5 Premium', 20, '1.2.2', 'Premium petrol 95', 'Gasolina 95 premium', true],
  ['G98E5', 'Precio Gasolina 98 E5', 3, '1.2.1', 'Petrol 98 E5', 'Gasolina 98 E5', true],
  ['G98E10', 'Precio Gasolina 98 E10', 21, '1.2.1', 'Petrol 98 E10', 'Gasolina 98 E10', true],
  ['BIODIESEL', 'Precio Biodiesel', 8, '1.14', 'Biodiesel', 'Biodiésel', true],
  ['GOB', 'Precio Gasoleo B', 6, null, 'Agricultural diesel (B)', 'Gasóleo B', true],
  ['GLP', 'Precio Gases licuados del petróleo', 17, null, 'LPG (Autogas)', 'GLP (Autogas)', true],
  ['GNC', 'Precio Gas Natural Comprimido', 18, null, 'Compressed natural gas', 'Gas natural comprimido', true],
  ['GNL', 'Precio Gas Natural Licuado', 19, null, 'Liquefied natural gas', 'Gas natural licuado', true],
  ['DIESEL_RENOVABLE', 'Precio Diésel Renovable', 27, null, 'Renewable diesel (HVO)', 'Diésel renovable (HVO)', true],
  ['ADBLUE', 'Precio Adblue', 26, null, 'AdBlue', 'AdBlue', true],
  ['G95E85', 'Precio Gasolina 95 E85', 25, null, 'Petrol E85', 'Gasolina E85', true],
  ['BIOETANOL', 'Precio Bioetanol', 16, null, 'Bioethanol', 'Bioetanol', true],
  ['HIDROGENO', 'Precio Hidrogeno', 22, null, 'Hydrogen', 'Hidrógeno', true],
].map(([key, field, productId, epigrafe, en, es, pickable]) => ({ key, field, productId, epigrafe, en, es, pickable }));
export const FUEL = Object.fromEntries(FUELS.map((f) => [f.key, f]));

export const COMMUNITIES = [
  ['01', 'Andalucía'], ['02', 'Aragón'], ['03', 'Asturias'], ['04', 'Illes Balears'], ['05', 'Canarias'],
  ['06', 'Cantabria'], ['07', 'Castilla-La Mancha'], ['08', 'Castilla y León'], ['09', 'Cataluña'],
  ['10', 'Comunitat Valenciana'], ['11', 'Extremadura'], ['12', 'Galicia'], ['13', 'Comunidad de Madrid'],
  ['14', 'Región de Murcia'], ['15', 'Navarra'], ['16', 'País Vasco'], ['17', 'La Rioja'], ['18', 'Ceuta'], ['19', 'Melilla'],
];

// id, name, community — the Ministry's list (Regions.kt), names already in display form.
export const PROVINCES = [
  ['01', 'Araba/Álava', '16'], ['02', 'Albacete', '07'], ['03', 'Alicante', '10'], ['04', 'Almería', '01'],
  ['05', 'Ávila', '08'], ['06', 'Badajoz', '11'], ['07', 'Illes Balears', '04'], ['08', 'Barcelona', '09'],
  ['09', 'Burgos', '08'], ['10', 'Cáceres', '11'], ['11', 'Cádiz', '01'], ['12', 'Castellón/Castelló', '10'],
  ['13', 'Ciudad Real', '07'], ['14', 'Córdoba', '01'], ['15', 'A Coruña', '12'], ['16', 'Cuenca', '07'],
  ['17', 'Girona', '09'], ['18', 'Granada', '01'], ['19', 'Guadalajara', '07'], ['20', 'Gipuzkoa', '16'],
  ['21', 'Huelva', '01'], ['22', 'Huesca', '02'], ['23', 'Jaén', '01'], ['24', 'León', '08'],
  ['25', 'Lleida', '09'], ['26', 'La Rioja', '17'], ['27', 'Lugo', '12'], ['28', 'Madrid', '13'],
  ['29', 'Málaga', '01'], ['30', 'Murcia', '14'], ['31', 'Navarra', '15'], ['32', 'Ourense', '12'],
  ['33', 'Asturias', '03'], ['34', 'Palencia', '08'], ['35', 'Las Palmas', '05'], ['36', 'Pontevedra', '12'],
  ['37', 'Salamanca', '08'], ['38', 'Santa Cruz de Tenerife', '05'], ['39', 'Cantabria', '06'], ['40', 'Segovia', '08'],
  ['41', 'Sevilla', '01'], ['42', 'Soria', '08'], ['43', 'Tarragona', '09'], ['44', 'Teruel', '02'],
  ['45', 'Toledo', '07'], ['46', 'Valencia/València', '10'], ['47', 'Valladolid', '08'], ['48', 'Bizkaia', '16'],
  ['49', 'Zamora', '08'], ['50', 'Zaragoza', '02'], ['51', 'Ceuta', '18'], ['52', 'Melilla', '19'],
];

export const pad = (id) => String(id).trim().padStart(2, '0');

/** Province ids a selection covers: {kind: 'all'|'ccaa'|'prov', ids: [...]}. */
export function provinceIds(sel) {
  if (sel.kind === 'all') return PROVINCES.map((p) => p[0]);
  if (sel.kind === 'ccaa') return PROVINCES.filter((p) => sel.ids.includes(p[2])).map((p) => p[0]);
  return sel.ids.map(pad);
}

export function selectionUrls(sel) {
  if (sel.kind === 'all') return [`${OFFICIAL}/EstacionesTerrestres/`];
  if (sel.kind === 'ccaa') return [...sel.ids].sort().map((id) => `${OFFICIAL}/EstacionesTerrestres/FiltroCCAA/${pad(id)}`);
  return [...sel.ids].sort().map((id) => `${OFFICIAL}/EstacionesTerrestres/FiltroProvincia/${pad(id)}`);
}

export function historyUrl(day, provinceId, productId) {
  const [y, m, d] = day.split('-');
  return `${OFFICIAL}/EstacionesTerrestresHist/FiltroProvinciaProducto/${d}-${m}-${y}/${pad(provinceId)}/${productId}`;
}

/** "1,879" → 1.879; "" → null. */
export function num(raw) {
  const s = (raw ?? '').toString().trim();
  if (!s) return null;
  const n = Number(s.replace(',', '.'));
  return Number.isFinite(n) ? n : null;
}

/**
 * Coordinates checked against Spain: on 2026-09-23 three stations came at 0,0
 * and one with lat/lon swapped (OfficialApiParser.coordinates).
 */
export function coordinates(lat, lon) {
  if (lat == null || lon == null) return null;
  const inSpain = (la, lo) => la >= 27 && la <= 44.5 && lo >= -19 && lo <= 5;
  if (inSpain(lat, lon)) return [lat, lon];
  if (inSpain(lon, lat)) return [lon, lat];
  return null;
}

// --- brands -----------------------------------------------------------------

export function normalise(sign) {
  return sign.normalize('NFD').replace(/\p{M}+/gu, '').toUpperCase().replace(/\s+/g, ' ').trim();
}

export function brandCatalog(json) {
  const entries = json.brands.map((b) => ({ ...b, independent: false, regexes: b.patterns.map((p) => new RegExp(p)) }));
  const byKey = Object.fromEntries(entries.map((b) => [b.key, b]));
  const independent = (sign) => {
    const initials = normalise(sign).replace(/[^A-Z]/g, '').slice(0, 2) || '?';
    return { key: `ind:${initials}`, name: sign || '?', color: '#6B7280', textColor: '#FFFFFF', initials, independent: true };
  };
  return {
    brands: entries,
    byKey,
    classify(sign) {
      const n = normalise(sign);
      return entries.find((b) => b.regexes.some((r) => r.test(n))) || independent(sign);
    },
  };
}

// --- stations ---------------------------------------------------------------

export function parseStations(json, catalog) {
  if (json.ResultadoConsulta && json.ResultadoConsulta !== 'OK') throw new Error(json.ResultadoConsulta);
  const out = [];
  for (const s of json.ListaEESSPrecio || []) {
    const id = (s.IDEESS || '').trim();
    const c = coordinates(num(s['Latitud']), num(s['Longitud (WGS84)']));
    if (!id || !c) continue;
    const prices = {};
    for (const f of FUELS) {
      const p = num(s[f.field]);
      if (p && p > 0) prices[f.key] = p;
    }
    const sign = (s['Rótulo'] || '').trim();
    out.push({
      id, sign, brand: catalog.classify(sign),
      address: (s['Dirección'] || '').trim(), locality: (s['Localidad'] || '').trim(),
      postalCode: (s['C.P.'] || '').trim(), schedule: (s['Horario'] || '').trim(),
      provinceId: pad(s['IDProvincia'] || ''), ccaa: pad(s['IDCCAA'] || ''),
      lat: c[0], lon: c[1], prices,
    });
  }
  return { publishedAt: json.Fecha || '', stations: out };
}

export function parseProductPrices(json) {
  const out = {};
  for (const s of json.ListaEESSPrecio || []) {
    const id = (s.IDEESS || '').trim();
    const p = num(s.PrecioProducto);
    if (id && p && p > 0) out[id] = p;
  }
  return out;
}

// --- geo --------------------------------------------------------------------

export function distanceKm(lat1, lon1, lat2, lon2) {
  const r = (d) => (d * Math.PI) / 180;
  const a = Math.sin(r(lat2 - lat1) / 2) ** 2 + Math.cos(r(lat1)) * Math.cos(r(lat2)) * Math.sin(r(lon2 - lon1) / 2) ** 2;
  return 2 * 6371.0088 * Math.asin(Math.sqrt(a));
}

// --- tax --------------------------------------------------------------------

export function todayMadrid() {
  return new Intl.DateTimeFormat('sv-SE', { timeZone: 'Europe/Madrid' }).format(new Date());
}

export function addDays(isoDay, n) {
  const d = new Date(`${isoDay}T12:00:00Z`);
  d.setUTCDate(d.getUTCDate() + n);
  return d.toISOString().slice(0, 10);
}

export const territoryOf = (ccaa) => (['05', '18', '19'].includes(ccaa) ? 'other' : 'peninsula');

/** {reason} or {vat, excise, rest, vatRate, confirmed, source}. */
export function breakdown(price, fuelKey, ccaa, schedule, day = todayMadrid()) {
  if (territoryOf(ccaa) !== 'peninsula') return { reason: 'territory' };
  const cat = FUEL[fuelKey]?.epigrafe;
  if (!cat) return { reason: 'product' };
  const p = schedule?.periods.find((x) => day >= x.from && (x.to === null || day <= x.to));
  if (!p) return { reason: 'period' };
  const rate = p.rates[cat];
  if (!rate) return { reason: 'product' };
  const excise = (rate.general + rate.especial) / 1000;
  const base = price / (1 + p.vat);
  return { price, vat: price - base, excise, rest: base - excise, vatRate: p.vat, confirmed: p.confirmed, source: p.source };
}

// --- discounts --------------------------------------------------------------

export function applyPlan(plan, price) {
  if (plan.kind === 'PERCENT') return price * (1 - plan.amount / 100);
  if (plan.kind === 'CENTS_PER_LITRE') return price - plan.amount / 100;
  return null;
}

export const appliable = (plan) => plan.audienceId === 1 && plan.kind !== 'OTHER';

/** Best price with the plans the user holds; one plan at a time (EffectivePriceCalculator). */
export function effectivePrice(price, plans, owned) {
  let best = { price, plan: null };
  for (const plan of plans) {
    if (!owned.has(plan.id) || !appliable(plan)) continue;
    const p = applyPlan(plan, price);
    if (p != null && p < best.price) best = { price: p, plan };
  }
  return best;
}

// --- comparisons ------------------------------------------------------------

/** Cents per litre of margin above the cheapest nearby (RelativeMargin). */
export function relativeMargin(target, fuelKey, candidates, radiusKm, schedule) {
  const rest = (s) => {
    const price = s.prices[fuelKey];
    if (!price) return null;
    const b = breakdown(price, fuelKey, s.ccaa, schedule);
    return b.reason ? null : b.rest;
  };
  const own = rest(target);
  if (own == null) return null;
  let cheapest = null;
  let count = 0;
  for (const s of candidates) {
    if (territoryOf(s.ccaa) !== territoryOf(target.ccaa)) continue;
    if (s.id !== target.id && distanceKm(target.lat, target.lon, s.lat, s.lon) > radiusKm) continue;
    const r = rest(s);
    if (r == null) continue;
    count++;
    if (!cheapest || r < cheapest.rest) cheapest = { station: s, rest: r };
  }
  if (!cheapest) return null;
  return { cents: (own - cheapest.rest) * 100, cheapest: cheapest.station, compared: count };
}

/** Stations within the radius, cheapest first, the target included (Nearby). */
export function nearby(target, radiusKm, candidates, priceOf) {
  return candidates
    .map((s) => {
      const price = priceOf(s);
      if (price == null) return null;
      const d = s.id === target.id ? 0 : distanceKm(target.lat, target.lon, s.lat, s.lon);
      return d <= radiusKm ? { station: s, price, distanceKm: d } : null;
    })
    .filter(Boolean)
    .sort((a, b) => a.price - b.price || a.distanceKm - b.distanceKm);
}

/** Today's price against the station's own history; null with fewer than 7 days. */
export function ownAverage(today, history, minDays = 7) {
  if (history.length < minDays) return null;
  const avg = history.reduce((a, b) => a + b, 0) / history.length;
  return { cents: (today - avg) * 100, average: avg, days: history.length };
}

/** Thirds by rank: the same bands as the app. */
export function band(index, n) {
  if (n < 3) return 'mid';
  if (index < Math.floor(n / 3)) return 'cheap';
  if (index >= n - Math.floor(n / 3)) return 'dear';
  return 'mid';
}

// --- search (port of core/search) --------------------------------------------

const ISO_PROVINCE = {
  'ES-VI': '01', 'ES-AB': '02', 'ES-A': '03', 'ES-AL': '04', 'ES-AV': '05', 'ES-BA': '06', 'ES-PM': '07', 'ES-B': '08',
  'ES-BU': '09', 'ES-CC': '10', 'ES-CA': '11', 'ES-CS': '12', 'ES-CR': '13', 'ES-CO': '14', 'ES-C': '15', 'ES-CU': '16',
  'ES-GI': '17', 'ES-GR': '18', 'ES-GU': '19', 'ES-SS': '20', 'ES-H': '21', 'ES-HU': '22', 'ES-J': '23', 'ES-LE': '24',
  'ES-L': '25', 'ES-LO': '26', 'ES-LU': '27', 'ES-M': '28', 'ES-MA': '29', 'ES-MU': '30', 'ES-NA': '31', 'ES-OR': '32',
  'ES-O': '33', 'ES-P': '34', 'ES-GC': '35', 'ES-PO': '36', 'ES-SA': '37', 'ES-TF': '38', 'ES-S': '39', 'ES-SG': '40',
  'ES-SE': '41', 'ES-SO': '42', 'ES-T': '43', 'ES-TE': '44', 'ES-TO': '45', 'ES-V': '46', 'ES-VA': '47', 'ES-BI': '48',
  'ES-ZA': '49', 'ES-Z': '50', 'ES-CE': '51', 'ES-ML': '52',
};
const ISO_SINGLE_PROVINCE_COMMUNITY = {
  'ES-AS': '33', 'ES-CB': '39', 'ES-MD': '28', 'ES-MC': '30', 'ES-NC': '31', 'ES-RI': '26', 'ES-IB': '07', 'ES-CE': '51', 'ES-ML': '52',
};

/** INE province of a Nominatim address: postcode, else ISO province code, else single-province community. */
export function provinceOf(address = {}) {
  const pc = (address.postcode || '').trim();
  if (/^\d{5}$/.test(pc) && +pc.slice(0, 2) >= 1 && +pc.slice(0, 2) <= 52) return pc.slice(0, 2);
  return ISO_PROVINCE[(address['ISO3166-2-lvl6'] || '').toUpperCase()]
    ?? ISO_SINGLE_PROVINCE_COMMUNITY[(address['ISO3166-2-lvl4'] || '').toUpperCase()] ?? null;
}

export function nominatimUrl(query, language) {
  const q = new URLSearchParams({ format: 'jsonv2', addressdetails: '1', countrycodes: 'es', limit: '6', 'accept-language': language, q: query.trim() });
  return `https://nominatim.openstreetmap.org/search?${q}`;
}

export function parseNominatim(json) {
  if (!Array.isArray(json)) return [];
  return json.flatMap((r) => {
    const lat = Number(r.lat); const lon = Number(r.lon);
    if (!Number.isFinite(lat) || !Number.isFinite(lon)) return [];
    const display = r.display_name || '';
    const detail = display.includes(', ') ? display.slice(display.indexOf(', ') + 2).replace(/, (España|Spain)$/, '') : '';
    return [{ name: r.name || display.split(',')[0], detail, lat, lon, provinceId: provinceOf(r.address), kind: 'address' }];
  });
}

/** "VIGO" → "Vigo"; mixed-case text is left alone. */
export const title = (text) => (/[a-zà-ÿ]/.test(text) ? text : text.toLowerCase().replace(/(^|\s)(\S)/g, (m, s, c) => s + c.toUpperCase()));

/**
 * Among the downloaded stations, as the user types: postcodes, localities
 * (at the centre of their stations) and stations by sign or address.
 */
export function localSearch(query, stations, provinceName = (id) => id, limit = 8) {
  const q = normalise(query);
  if (q.length < 2) return [];
  const words = q.split(' ');
  const matches = (text) => text.includes(q) || words.every((w) => text.split(/[ ,.\-/]/).some((t) => t.startsWith(w)));
  const centre = (name, detail, list, kind) => ({
    name, detail: detail ? `${detail} · ${list.length}` : `${list.length}`,
    lat: list.reduce((a, s) => a + s.lat, 0) / list.length, lon: list.reduce((a, s) => a + s.lon, 0) / list.length,
    provinceId: list[0].provinceId, kind,
  });
  const group = (key) => {
    const m = new Map();
    for (const s of stations) { const k = key(s); if (!m.has(k)) m.set(k, []); m.get(k).push(s); }
    return [...m.entries()];
  };
  const out = [];
  if (/^\d+$/.test(q)) {
    group((s) => s.postalCode).filter(([pc]) => pc.startsWith(q)).sort((a, b) => b[1].length - a[1].length)
      .forEach(([pc, list]) => out.push(centre(pc, title(list[0].locality), list, 'postcode')));
  }
  group((s) => `${s.locality}|${s.provinceId}`).filter(([k]) => matches(normalise(k.split('|')[0])))
    .sort((a, b) => (normalise(a[0]).startsWith(q) ? 0 : 1) - (normalise(b[0]).startsWith(q) ? 0 : 1) || b[1].length - a[1].length)
    .forEach(([k, list]) => out.push(centre(title(k.split('|')[0]), provinceName(list[0].provinceId), list, 'locality')));
  for (const s of stations) {
    if (out.filter((p) => p.kind === 'station').length >= limit) break;
    if (matches(normalise(`${s.sign} ${s.address}`))) {
      out.push({ name: s.sign || s.brand.name, detail: [s.address, s.locality].filter(Boolean).map(title).join(', '), lat: s.lat, lon: s.lon, provinceId: s.provinceId, kind: 'station', stationId: s.id });
    }
  }
  const seen = new Set();
  return out.filter((p) => { const k = `${p.kind}|${p.name}|${p.detail}`; if (seen.has(k)) return false; seen.add(k); return true; }).slice(0, limit);
}
