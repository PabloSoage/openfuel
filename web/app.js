// openfuel web (phase 5): the Android app's features in the browser, no build step.
// Prices and history come straight from the Ministry's API (it sends CORS *).
// The geoportal refuses other origins, so discount plans and logos come from
// data/, which the weekly enrich workflow fills (tools/enrich/enrich.py).
import * as core from './core.js';

const STYLE = 'https://tiles.openfreemap.org/styles/liberty';
const RAW = 'https://raw.githubusercontent.com/PabloSoage/openfuel/main/core/src/main/resources/';
const ICON = 64; // px of the map icons (drawn at 2x, shown at 32 css px)

// --- i18n -------------------------------------------------------------------

const STRINGS = {
  en: {
    locate: 'My location', brands: 'Brands', refresh: 'Refresh', list: 'List', map: 'Map', settings: 'Settings',
    sortPrice: 'Price', sortDistance: 'Distance', favourites: 'Favourites', close: 'Close', cancel: 'Cancel',
    download: 'Download', apply: 'Apply', showAll: 'Show all', brandsTitle: 'Brands on the map', independent: 'Independent',
    regionTitle: 'Where do you refuel?', communities: 'Communities', provinces: 'Provinces', allSpain: 'All of Spain',
    regionExplain: 'Only this area is downloaded: a community is under 2 MB, a province well under 1 MB.',
    allWarning: 'All of Spain is about 12 MB per update. Better on Wi-Fi.',
    loading: 'Downloading prices…', pricesOf: (f, r, n) => `Prices of ${f} · ${r} · ${n} stations`,
    loadError: 'Could not update the prices.', noRegion: 'Choose a region to start.',
    prices: 'Prices', perLitre: (p) => `${p} €/l`, withPlan: (p, n) => `With ${n}: ${p} €/l`,
    premium: 'premium', claimsNote: (d) => `What the brand says, not verified · checked ${d}`, source: 'Source',
    noClaims: 'No additive claims researched for this brand.',
    breakdownOf: (f) => `What a litre of ${f} is made of`, vat: (r) => `VAT (${r})`, excise: 'Hydrocarbon excise',
    rest: 'Product, logistics and margin', taxes: (a, b) => `Taxes: ${a} per litre (${b} of the price)`,
    unconfirmed: 'The tax rate for this date could not be confirmed.', taxSource: (s) => `Source: ${s}`,
    noTerritory: 'No breakdown in Canarias, Ceuta and Melilla: they have no VAT (IGIC and IPSI instead).',
    noProduct: 'No breakdown for this fuel: its excise is not charged per litre.', noPeriod: 'No tax rates on record for this date.',
    noPrice: 'This station does not sell the selected fuel.',
    compare: 'Compared with', cheapest: (km, n) => `The cheapest within ${km} km (${n} stations compared)`,
    above: (c, km, n) => `${c} c/l more margin than the cheapest within ${km} km (${n} stations)`,
    below: (c, d) => `${c} c/l below its ${d}-day average`, over: (c, d) => `${c} c/l above its ${d}-day average`,
    nearbyShow: (n, km) => `See the ${n} stations within ${km} km`, nearbyHide: 'Hide the list', thisOne: 'this one',
    same: 'same price',
    discounts: 'Discount plans at this station', noPlans: 'No discount plans published for this station.',
    plansUnknown: 'Discount plans are not available yet (they are published weekly).', haveIt: 'I have it',
    notApplied: 'Shown only: not applied automatically.',
    history: (d) => `Last ${d} days`, historyOff: 'History is off. Turn it on in Settings.', historyNone: 'Not enough days of data yet.',
    historyLoading: 'Downloading history…', max: 'max', min: 'min',
    openMaps: 'Open in Maps', share: 'Share', copied: 'Copied', fav: 'Add to favourites', unfav: 'Remove from favourites',
    listEmpty: 'No stations match these filters.', pump: (p) => `pump ${p}`,
    region: 'Region', change: 'Change', historySetting: 'Price history', historyExplain: 'Downloaded per province and fuel when you open a station, about 85 KB per day the first time.',
    off: 'Off', days: (n) => `${n} days`, radius: 'Comparison radius', plansOwned: 'Discount plans I have',
    plansEmpty: 'No plans for this region yet.', language: 'Language', system: 'System', about: 'About',
    aboutText: 'Prices from the open data of the Ministerio para la Transición Ecológica y el Reto Demográfico. Discount plans and most logos from geoportalgasolineras.es, the rest from Wikimedia Commons.',
    attribution: 'Map © OpenStreetMap contributors, tiles by OpenFreeMap, rendered with MapLibre.',
    schedule: (v, d) => `Tax schedule version ${v}, updated ${d}.`, plansDate: (d) => `Discount plans of ${d}.`,
    km: (v) => `${v} km`, hours: (h) => `Hours: ${h}`, locError: 'Location not available.',
  },
  es: {
    locate: 'Mi ubicación', brands: 'Marcas', refresh: 'Actualizar', list: 'Lista', map: 'Mapa', settings: 'Ajustes',
    sortPrice: 'Precio', sortDistance: 'Distancia', favourites: 'Favoritas', close: 'Cerrar', cancel: 'Cancelar',
    download: 'Descargar', apply: 'Aplicar', showAll: 'Mostrar todas', brandsTitle: 'Marcas en el mapa', independent: 'Independientes',
    regionTitle: '¿Dónde repostas?', communities: 'Comunidades', provinces: 'Provincias', allSpain: 'Toda España',
    regionExplain: 'Solo se descarga esta zona: una comunidad ocupa menos de 2 MB y una provincia bastante menos de 1 MB.',
    allWarning: 'Toda España son unos 12 MB por actualización. Mejor con wifi.',
    loading: 'Descargando precios…', pricesOf: (f, r, n) => `Precios del ${f} · ${r} · ${n} estaciones`,
    loadError: 'No se pudieron actualizar los precios.', noRegion: 'Elige una región para empezar.',
    prices: 'Precios', perLitre: (p) => `${p} €/l`, withPlan: (p, n) => `Con ${n}: ${p} €/l`,
    premium: 'premium', claimsNote: (d) => `Lo que dice la marca, sin verificar · comprobado el ${d}`, source: 'Fuente',
    noClaims: 'Sin datos de aditivos investigados para esta marca.',
    breakdownOf: (f) => `De qué está hecho un litro de ${f}`, vat: (r) => `IVA (${r})`, excise: 'Impuesto sobre hidrocarburos',
    rest: 'Producto, logística y margen', taxes: (a, b) => `Impuestos: ${a} por litro (${b} del precio)`,
    unconfirmed: 'No se ha podido confirmar el tipo impositivo de esta fecha.', taxSource: (s) => `Fuente: ${s}`,
    noTerritory: 'Sin desglose en Canarias, Ceuta y Melilla: no tienen IVA (IGIC e IPSI).',
    noProduct: 'Sin desglose para este carburante: su impuesto no se cobra por litro.', noPeriod: 'No hay tipos impositivos para esta fecha.',
    noPrice: 'Esta estación no vende el carburante elegido.',
    compare: 'Comparada con', cheapest: (km, n) => `La más barata a ${km} km (${n} estaciones comparadas)`,
    above: (c, km, n) => `${c} c/l más de margen que la más barata a ${km} km (${n} estaciones)`,
    below: (c, d) => `${c} c/l por debajo de su media de ${d} días`, over: (c, d) => `${c} c/l por encima de su media de ${d} días`,
    nearbyShow: (n, km) => `Ver las ${n} estaciones a ${km} km`, nearbyHide: 'Ocultar la lista', thisOne: 'esta',
    same: 'mismo precio',
    discounts: 'Planes de descuento de esta estación', noPlans: 'Esta estación no tiene planes de descuento publicados.',
    plansUnknown: 'Los planes de descuento aún no están disponibles (se publican cada semana).', haveIt: 'Lo tengo',
    notApplied: 'Solo informativo: no se aplica automáticamente.',
    history: (d) => `Últimos ${d} días`, historyOff: 'El histórico está desactivado. Actívalo en Ajustes.', historyNone: 'Aún no hay días suficientes.',
    historyLoading: 'Descargando histórico…', max: 'máx', min: 'mín',
    openMaps: 'Abrir en Maps', share: 'Compartir', copied: 'Copiado', fav: 'Añadir a favoritas', unfav: 'Quitar de favoritas',
    listEmpty: 'Ninguna estación cumple estos filtros.', pump: (p) => `surtidor ${p}`,
    region: 'Región', change: 'Cambiar', historySetting: 'Histórico de precios', historyExplain: 'Se descarga por provincia y carburante al abrir una estación, unos 85 KB por día la primera vez.',
    off: 'No', days: (n) => `${n} días`, radius: 'Radio de comparación', plansOwned: 'Planes de descuento que tengo',
    plansEmpty: 'Aún no hay planes para esta región.', language: 'Idioma', system: 'Sistema', about: 'Acerca de',
    aboutText: 'Precios de los datos abiertos del Ministerio para la Transición Ecológica y el Reto Demográfico. Planes de descuento y la mayoría de logos de geoportalgasolineras.es; el resto, de Wikimedia Commons.',
    attribution: 'Mapa © colaboradores de OpenStreetMap, teselas de OpenFreeMap, dibujado con MapLibre.',
    schedule: (v, d) => `Calendario fiscal versión ${v}, actualizado el ${d}.`, plansDate: (d) => `Planes de descuento del ${d}.`,
    km: (v) => `${v} km`, hours: (h) => `Horario: ${h}`, locError: 'Ubicación no disponible.',
  },
};

// --- persistent settings (per browser) ------------------------------------

const store = {
  get(key, fallback) {
    try { const v = localStorage.getItem(`openfuel.${key}`); return v == null ? fallback : JSON.parse(v); } catch { return fallback; }
  },
  set(key, value) { try { localStorage.setItem(`openfuel.${key}`, JSON.stringify(value)); } catch { /* private mode */ } },
};

const settings = {
  region: store.get('region', null),
  fuel: store.get('fuel', 'GOA'),
  hidden: new Set(store.get('hidden', [])),
  owned: new Set(store.get('owned', [])),
  favourites: new Set(store.get('favourites', [])),
  historyDays: store.get('historyDays', 30),
  radiusKm: store.get('radiusKm', 10),
  lang: store.get('lang', ''),
};
const save = () => {
  store.set('region', settings.region); store.set('fuel', settings.fuel); store.set('hidden', [...settings.hidden]);
  store.set('owned', [...settings.owned]); store.set('favourites', [...settings.favourites]);
  store.set('historyDays', settings.historyDays); store.set('radiusKm', settings.radiusKm); store.set('lang', settings.lang);
};

const LANG = (settings.lang || navigator.language || 'en').toLowerCase().startsWith('es') ? 'es' : 'en';
const T = STRINGS[LANG];
const LOCALE = LANG === 'es' ? 'es-ES' : 'en-GB';

// --- state ------------------------------------------------------------------

const state = {
  schedule: null, catalog: null, products: null, plans: null, logoIndex: {},
  stations: [], byId: new Map(), publishedAt: '', rows: [], location: null,
  view: 'map', sort: 'price', favOnly: false, openId: null, nearbyOpen: false, loadToken: 0,
};

// --- helpers ----------------------------------------------------------------

const $ = (id) => document.getElementById(id);
const esc = (s) => String(s ?? '').replace(/[&<>"']/g, (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));
const fmt = (v, d = 3) => v.toLocaleString(LOCALE, { minimumFractionDigits: d, maximumFractionDigits: d });
const pct = (share) => `${fmt(share * 100, 1)} %`;
const km = (v) => T.km(fmt(v, v < 10 ? 1 : 0));
const fuelName = (key) => core.FUEL[key]?.[LANG] ?? key;
const mapsUrl = (s) => `https://www.google.com/maps/search/?api=1&query=${s.lat},${s.lon}`;
const dayLabel = (iso) => `${iso.slice(8, 10)}/${iso.slice(5, 7)}`;

async function json(url, options) {
  const r = await fetch(url, options);
  if (!r.ok) throw new Error(`HTTP ${r.status}`);
  return JSON.parse((await r.text()).replace(/^﻿/, ''));
}

/** Local copy first (published next to the page), then the repository. */
async function data(name) {
  for (const url of [name, RAW + name]) {
    try { return await json(url); } catch { /* next */ }
  }
  return null;
}

function regionSummary(sel) {
  if (!sel) return '';
  if (sel.kind === 'all') return T.allSpain;
  const names = sel.kind === 'ccaa'
    ? sel.ids.map((id) => core.COMMUNITIES.find((c) => c[0] === id)?.[1] ?? id)
    : sel.ids.map((id) => core.PROVINCES.find((p) => p[0] === id)?.[1] ?? id);
  return names.sort().join(', ');
}

function plansAt(stationId) {
  const ids = state.plans?.stations?.[stationId] ?? [];
  return ids.map((id) => ({ id, ...state.plans.plans[String(id)] })).filter((p) => p.name);
}

// --- icons ------------------------------------------------------------------

const logoCache = new Map(); // brand key → Promise<HTMLImageElement|null>

function logoUrl(brand) {
  if (brand.independent) return null;
  if (state.logoIndex[brand.key]) return `data/logos/${brand.key}.png`;
  return brand.logoUrl || null;
}

function loadLogo(brand) {
  if (!logoCache.has(brand.key)) {
    const url = logoUrl(brand);
    logoCache.set(brand.key, !url ? Promise.resolve(null) : new Promise((resolve) => {
      const img = new Image();
      img.crossOrigin = 'anonymous';
      img.onload = () => resolve(img);
      img.onerror = () => resolve(null);
      img.src = url;
    }));
  }
  return logoCache.get(brand.key);
}

/** The same icon as the app's MarkerIcons: logo in a white disc, or initials on the brand colour. */
function drawIcon(brand, img, size = ICON) {
  const c = document.createElement('canvas');
  c.width = c.height = size;
  const g = c.getContext('2d');
  const r = size / 2;
  g.beginPath(); g.arc(r, r, r - 1, 0, Math.PI * 2);
  g.fillStyle = img ? '#ffffff' : brand.color; g.fill();
  g.lineWidth = size / 16; g.strokeStyle = img ? '#00000033' : '#ffffff';
  g.beginPath(); g.arc(r, r, r - g.lineWidth, 0, Math.PI * 2); g.stroke();
  if (img) {
    const box = size * 0.64;
    const scale = Math.min(box / img.naturalWidth, box / img.naturalHeight);
    const w = img.naturalWidth * scale; const h = img.naturalHeight * scale;
    g.drawImage(img, r - w / 2, r - h / 2, w, h);
  } else {
    g.fillStyle = brand.textColor; g.textAlign = 'center'; g.textBaseline = 'middle';
    g.font = `bold ${Math.round(size * (brand.initials.length <= 1 ? 0.5 : 0.36))}px system-ui, sans-serif`;
    g.fillText(brand.initials, r, r + 1);
  }
  return c;
}

async function iconCanvas(brand, size) {
  return drawIcon(brand, await loadLogo(brand), size);
}

async function iconImg(brand, size = 36) {
  const c = await iconCanvas(brand, size * 2);
  return `<img class="logo" alt="" width="${size}" height="${size}" src="${c.toDataURL()}">`;
}

// --- ranking ----------------------------------------------------------------

function buildRows() {
  const fuel = settings.fuel;
  const priced = [];
  for (const s of state.stations) {
    const filterKey = s.brand.independent ? 'independent' : s.brand.key;
    if (settings.hidden.has(filterKey)) continue;
    const price = s.prices[fuel];
    if (!price) continue;
    const eff = core.effectivePrice(price, plansAt(s.id), settings.owned);
    priced.push({ station: s, price, effective: eff });
  }
  priced.sort((a, b) => a.effective.price - b.effective.price);
  const here = state.location;
  state.rows = priced.map((row, i) => ({
    ...row, rank: i, band: core.band(i, priced.length),
    distanceKm: here ? core.distanceKm(here.lat, here.lon, row.station.lat, row.station.lon) : null,
    favourite: settings.favourites.has(row.station.id),
  }));
}

// --- map --------------------------------------------------------------------

let map;

function geojson() {
  const css = getComputedStyle(document.documentElement);
  const colors = Object.fromEntries(['cheap', 'mid', 'dear'].map((b) => [b, css.getPropertyValue(`--${b}`).trim()]));
  return {
    type: 'FeatureCollection',
    features: state.rows.map((r) => ({
      type: 'Feature',
      geometry: { type: 'Point', coordinates: [r.station.lon, r.station.lat] },
      properties: {
        id: r.station.id, icon: r.station.brand.key, rank: r.rank,
        label: fmt(r.effective.price), color: colors[r.band],
      },
    })),
  };
}

function initMap() {
  map = new maplibregl.Map({ container: 'map', style: STYLE, center: [-3.7, 40.3], zoom: 5.2, attributionControl: { compact: true } });
  map.addControl(new maplibregl.NavigationControl({ showCompass: false }), 'top-right');
  map.on('styleimagemissing', async (e) => {
    const brand = state.stations.find((s) => s.brand.key === e.id)?.brand;
    if (!brand || map.hasImage(e.id)) return;
    map.addImage(e.id, { width: ICON, height: ICON, data: new Uint8Array(ICON * ICON * 4) }, { pixelRatio: 2 }); // placeholder
    const c = await iconCanvas(brand, ICON);
    const data = c.getContext('2d').getImageData(0, 0, ICON, ICON);
    map.updateImage(e.id, data);
  });
  return new Promise((resolve) => map.on('load', () => {
    map.addSource('stations', { type: 'geojson', data: { type: 'FeatureCollection', features: [] } });
    // Far out, coloured dots: thousands of icons would be noise.
    map.addLayer({
      id: 'dots', type: 'circle', source: 'stations', maxzoom: 10,
      layout: { 'circle-sort-key': ['-', 0, ['get', 'rank']] }, // cheapest drawn on top
      paint: {
        'circle-color': ['get', 'color'], 'circle-stroke-color': '#ffffff', 'circle-stroke-width': 1,
        'circle-radius': ['interpolate', ['linear'], ['zoom'], 5, 2.5, 10, 6],
      },
    });
    // Close in, the app's symbols: brand icon and price; the cheaper wins collisions.
    map.addLayer({
      id: 'symbols', type: 'symbol', source: 'stations', minzoom: 10,
      layout: {
        'icon-image': ['get', 'icon'], 'icon-size': 1, 'text-field': ['get', 'label'], 'text-font': ['Noto Sans Regular'],
        'text-size': 12, 'text-offset': [0, 1.9], 'symbol-sort-key': ['get', 'rank'],
      },
      paint: { 'text-color': ['get', 'color'], 'text-halo-color': '#ffffff', 'text-halo-width': 1.6 },
    });
    for (const layer of ['dots', 'symbols']) {
      map.on('click', layer, (e) => openStation(e.features[0].properties.id));
      map.on('mouseenter', layer, () => { map.getCanvas().style.cursor = 'pointer'; });
      map.on('mouseleave', layer, () => { map.getCanvas().style.cursor = ''; });
    }
    resolve();
  }));
}

function renderMap() {
  map?.getSource('stations')?.setData(geojson());
}

function frame() {
  if (!map || !state.stations.length) return;
  const here = state.location;
  const b = new maplibregl.LngLatBounds();
  state.stations.forEach((s) => b.extend([s.lon, s.lat]));
  if (here && b.contains([here.lon, here.lat])) map.jumpTo({ center: [here.lon, here.lat], zoom: 12 });
  else map.fitBounds(b, { padding: 40, maxZoom: 12, duration: 0 });
}

// --- loading ----------------------------------------------------------------

async function loadPrices() {
  const sel = settings.region;
  if (!sel) { status(T.noRegion); return; }
  const token = ++state.loadToken; // a newer region choice wins over a slower older answer
  status(T.loading);
  $('btn-refresh').disabled = true;
  try {
    const parts = await Promise.all(core.selectionUrls(sel).map((u) => json(u, { headers: { Accept: 'application/json' } })));
    if (token !== state.loadToken) return;
    const parsed = parts.map((p) => core.parseStations(p, state.catalog));
    const seen = new Map();
    parsed.flatMap((p) => p.stations).forEach((s) => seen.set(s.id, s));
    state.stations = [...seen.values()];
    state.byId = seen;
    state.publishedAt = parsed.map((p) => p.publishedAt).sort().pop() || '';
    refresh();
    frame();
  } catch (e) {
    if (token === state.loadToken) status(T.loadError, true);
    console.warn(e);
  } finally {
    if (token === state.loadToken) $('btn-refresh').disabled = false;
  }
}

function refresh() {
  buildRows();
  renderMap();
  if (state.view === 'list') renderList();
  if (state.openId) renderPanel();
  status(T.pricesOf(state.publishedAt.replace(/:\d\d$/, ''), regionSummary(settings.region), state.rows.length));
}

function status(text, error = false) {
  $('status').textContent = text;
  $('status').classList.toggle('error', error);
}

// --- list -------------------------------------------------------------------

async function renderList() {
  document.querySelectorAll('[data-sort]').forEach((b) => b.classList.toggle('on', b.dataset.sort === state.sort));
  document.querySelector('[data-sort="distance"]').disabled = !state.location;
  $('chip-fav').classList.toggle('on', state.favOnly);
  let rows = state.rows.filter((r) => !state.favOnly || r.favourite);
  if (state.sort === 'distance' && state.location) rows = [...rows].sort((a, b) => a.distanceKm - b.distanceKm);
  const shown = rows.slice(0, 400);
  const html = await Promise.all(shown.map(async (r) => {
    const s = r.station;
    const where = [s.locality, r.distanceKm != null ? km(r.distanceKm) : ''].filter(Boolean).join(' · ');
    return `<li data-id="${esc(s.id)}">${await iconImg(s.brand)}
      <div class="row-main"><b>${esc(s.sign || s.brand.name)}${r.favourite ? ' ★' : ''}</b><small>${esc(where)}</small></div>
      <div class="row-price ${r.band}">${fmt(r.effective.price)}${r.effective.plan ? `<small>${T.pump(fmt(r.price))}</small>` : ''}</div></li>`;
  }));
  $('list-rows').innerHTML = html.join('') || `<li>${T.listEmpty}</li>`;
}

// --- station panel ------------------------------------------------------------

function openStation(id) {
  state.openId = id;
  state.nearbyOpen = false;
  history.replaceState(null, '', `#${encodeURIComponent(id)}`);
  renderPanel();
}

function closePanel() {
  state.openId = null;
  $('panel').hidden = true;
  history.replaceState(null, '', location.pathname);
}

async function renderPanel() {
  const s = state.byId.get(state.openId);
  if (!s) { closePanel(); return; }
  const id = s.id;
  const fuel = settings.fuel;
  const plans = plansAt(s.id);
  const fav = settings.favourites.has(s.id);
  const here = state.location;
  const parts = [];

  parts.push(`<button class="icon close" data-act="close" aria-label="${T.close}">✕</button>
    <div class="head">${await iconImg(s.brand, 48)}<div class="grow"><h2>${esc(s.sign || s.brand.name)}</h2>
    <div class="muted">${esc([s.brand.independent ? '' : s.brand.name, here ? km(core.distanceKm(here.lat, here.lon, s.lat, s.lon)) : ''].filter(Boolean).join(' · '))}</div></div>
    <button class="icon" data-act="fav" title="${fav ? T.unfav : T.fav}">${fav ? '★' : '☆'}</button></div>
    <p>${esc(s.address)}<br>${esc([s.postalCode, s.locality].filter(Boolean).join(' '))}${s.schedule ? `<br><small>${esc(T.hours(s.schedule))}</small>` : ''}</p>
    <div class="actions"><a href="${mapsUrl(s)}" target="_blank" rel="noopener">${T.openMaps}</a><button data-act="share">${T.share}</button></div>`);

  // Prices, with what the brand says about each fuel.
  const ordered = Object.entries(s.prices).sort(([a], [b]) => (a !== fuel) - (b !== fuel) || core.FUELS.findIndex((f) => f.key === a) - core.FUELS.findIndex((f) => f.key === b));
  const rows = ordered.map(([key, price]) => {
    const product = state.products?.find((p) => p.brand === s.brand.key && p.fuels.includes(key));
    const hasClaims = !!core.FUEL[key]?.epigrafe;
    const name = `<span class="name">${esc(fuelName(key))}${product ? `<small>${esc(product.name)}${product.premium ? ` · ${T.premium}` : ''}</small>` : ''}</span>`;
    const eff = key === fuel ? core.effectivePrice(price, plans, settings.owned) : null;
    const claims = !hasClaims ? '' : `<div class="claims">${product
      ? `<ul>${(product.claims[LANG] || product.claims.en).map((c) => `<li>${esc(c)}</li>`).join('')}</ul>
         <div class="note">${esc(T.claimsNote(product.checked.split('-').reverse().join('/')))} · <a href="${esc(product.source)}" target="_blank" rel="noopener">${T.source}</a></div>`
      : `<div>${T.noClaims}</div>`}<div class="note">${esc(state.productsBaseline?.[LANG] ?? '')}</div></div>`;
    return `<details class="${key === fuel ? 'sel' : ''} ${hasClaims ? '' : 'plain'}" ${key === fuel ? 'open' : ''}>
      <summary>${name}<span>${T.perLitre(fmt(price))}${hasClaims ? ' ▾' : ''}</span></summary>
      ${eff?.plan ? `<div class="effective">${esc(T.withPlan(fmt(eff.price), eff.plan.name))}</div>` : ''}${claims}</details>`;
  });
  parts.push(`<h3>${T.prices}</h3><div class="prices">${rows.join('')}</div>`);

  // Tax breakdown.
  parts.push(`<h3>${esc(T.breakdownOf(fuelName(fuel)))}</h3>`);
  const price = s.prices[fuel];
  if (!price) parts.push(`<p class="muted">${T.noPrice}</p>`);
  else {
    const b = core.breakdown(price, fuel, s.ccaa, state.schedule);
    if (b.reason) parts.push(`<p class="muted">${{ territory: T.noTerritory, product: T.noProduct, period: T.noPeriod }[b.reason]}</p>`);
    else {
      const line = (color, label, v) => `<div><i style="background:var(--${color})"></i>${esc(label)}<span class="v">${fmt(v)} € · ${pct(v / b.price)}</span></div>`;
      parts.push(`<div class="bar-tax"><span style="flex:${Math.max(b.vat, 0.001)}"></span><span style="flex:${Math.max(b.excise, 0.001)}"></span><span style="flex:${Math.max(b.rest, 0.001)}"></span></div>
        <div class="legend">${line('vat', T.vat(pct(b.vatRate)), b.vat)}${line('excise', T.excise, b.excise)}${line('rest', T.rest, b.rest)}</div>
        <p>${esc(T.taxes(`${fmt(b.vat + b.excise)} €`, pct((b.vat + b.excise) / b.price)))}</p>
        ${b.confirmed ? '' : `<p class="warn">${T.unconfirmed}</p>`}<p class="src">${esc(T.taxSource(b.source))}</p>`);
    }
  }

  // Comparisons and the list behind them.
  const radius = settings.radiusKm;
  const rel = core.relativeMargin(s, fuel, state.stations, radius, state.schedule);
  const effById = new Map(state.rows.map((r) => [r.station.id, r.effective.price]));
  const near = core.nearby(s, radius, state.rows.map((r) => r.station), (x) => effById.get(x.id));
  parts.push(`<h3>${T.compare}</h3>`);
  if (rel) parts.push(`<p>${esc(rel.cheapest.id === s.id || rel.cents < 0.05 ? T.cheapest(radius, rel.compared) : T.above(fmt(rel.cents, 1), radius, rel.compared))}</p>`);
  parts.push('<p id="own-average"></p>');
  if (near.length > 1) {
    parts.push(`<button class="linkish" data-act="nearby">${state.nearbyOpen ? T.nearbyHide : T.nearbyShow(near.length, radius)}</button>`);
    if (state.nearbyOpen) {
      const own = near.find((n) => n.station.id === s.id)?.price;
      const items = await Promise.all(near.map(async (n) => {
        const isThis = n.station.id === s.id;
        const d = own == null ? '' : isThis ? T.thisOne : Math.abs(n.price - own) < 0.0005 ? T.same
          : n.price > own ? `+${fmt((n.price - own) * 100, 1)} c/l` : `−${fmt((own - n.price) * 100, 1)} c/l`;
        return `<li class="${isThis ? 'this' : ''}" data-id="${esc(n.station.id)}">${await iconImg(n.station.brand, 28)}
          <div class="row-main"><b>${esc(n.station.sign || n.station.brand.name)}</b><small>${esc([n.station.locality, isThis ? '' : km(n.distanceKm)].filter(Boolean).join(' · '))}</small></div>
          <div class="row-price">${fmt(n.price)}<small>${esc(d)}</small></div></li>`;
      }));
      parts.push(`<ul class="near">${items.join('')}</ul>`);
    }
  }

  // Discount plans.
  parts.push(`<h3>${T.discounts}</h3>`);
  if (!state.plans) parts.push(`<p class="muted">${T.plansUnknown}</p>`);
  else if (!plans.length) parts.push(`<p class="muted">${T.noPlans}</p>`);
  else {
    parts.push(plans.map((p) => {
      const amount = p.kind === 'PERCENT' ? `${fmt(p.amount, 1)} %` : p.kind === 'CENTS_PER_LITRE' ? `${fmt(p.amount, 1)} c/l` : esc(p.kindLabel);
      const ok = core.appliable(p);
      return `<label class="plan"><input type="checkbox" data-plan="${p.id}" ${settings.owned.has(p.id) ? 'checked' : ''} ${ok ? '' : 'disabled'}>
        <span>${esc(p.name)}<small>${amount}${p.audienceId === 1 ? '' : ` · ${esc(p.audienceLabel)}`}</small>${ok ? '' : `<small>${T.notApplied}</small>`}</span></label>`;
    }).join(''));
  }

  // History.
  parts.push(`<h3>${settings.historyDays ? T.history(settings.historyDays) : T.historySetting}</h3><div id="history">${settings.historyDays ? T.historyLoading : T.historyOff}</div>`);

  if (state.openId !== id) return; // another station was opened while building
  const panel = $('panel');
  panel.innerHTML = parts.join('');
  panel.hidden = false;
  if (settings.historyDays && price) loadHistory(s, fuel);
  else if (settings.historyDays) $('history').textContent = T.historyNone;
}

// --- history ------------------------------------------------------------------

async function provinceDay(provinceId, fuelKey, day) {
  const key = `h:${provinceId}:${fuelKey}:${day}`;
  const cached = store.get(key, null);
  if (cached) return cached;
  const f = core.FUEL[fuelKey];
  try {
    const prices = core.parseProductPrices(await json(core.historyUrl(day, provinceId, f.productId), { headers: { Accept: 'application/json' } }));
    // An empty answer (day not published yet) is not cached, so it is asked again.
    if (Object.keys(prices).length) store.set(key, prices);
    return prices;
  } catch { return null; }
}

async function loadHistory(s, fuelKey) {
  const id = s.id;
  const today = core.todayMadrid();
  const days = Array.from({ length: settings.historyDays }, (_, i) => core.addDays(today, -(settings.historyDays - i)));
  const points = new Array(days.length).fill(null);
  let next = 0;
  const worker = async () => {
    while (next < days.length) {
      const i = next++;
      const prices = await provinceDay(s.provinceId, fuelKey, days[i]);
      if (prices?.[s.id]) points[i] = { day: days[i], price: prices[s.id] };
    }
  };
  await Promise.all([worker(), worker(), worker(), worker()]);
  if (state.openId !== id || settings.fuel !== fuelKey) return;
  const past = points.filter(Boolean);
  const series = s.prices[fuelKey] ? [...past, { day: today, price: s.prices[fuelKey] }] : past;
  const avg = s.prices[fuelKey] ? core.ownAverage(s.prices[fuelKey], past.map((p) => p.price)) : null;
  if (avg) {
    $('own-average').textContent = avg.cents <= 0 ? T.below(fmt(-avg.cents, 1), avg.days) : T.over(fmt(avg.cents, 1), avg.days);
  }
  $('history').innerHTML = series.length < 2 ? T.historyNone : chart(series);
}

function chart(points) {
  const W = 400; const H = 150; const P = 6;
  const prices = points.map((p) => p.price);
  const min = Math.min(...prices); const max = Math.max(...prices);
  const range = max - min > 1e-9 ? max - min : 0.01;
  const t0 = Date.parse(points[0].day); const span = Math.max(Date.parse(points.at(-1).day) - t0, 86400000);
  const x = (p) => P + ((W - 2 * P) * (Date.parse(p.day) - t0)) / span;
  const y = (v) => P + (H - 2 * P) * (1 - (v - min) / range);
  // Missing days are gaps, not interpolations.
  let d = '';
  points.forEach((p, i) => {
    const gap = i === 0 || Date.parse(p.day) - Date.parse(points[i - 1].day) > 86400000 * 1.5;
    d += `${gap ? 'M' : 'L'}${x(p).toFixed(1)},${y(p.price).toFixed(1)} `;
  });
  const last = points.at(-1);
  return `<div class="chart-labels"><span>${T.max} ${fmt(max)}</span><span>${T.min} ${fmt(min)}</span></div>
    <svg class="chart" viewBox="0 0 ${W} ${H}" preserveAspectRatio="none">
      <line class="grid" x1="${P}" x2="${W - P}" y1="${y(max)}" y2="${y(max)}"/><line class="grid" x1="${P}" x2="${W - P}" y1="${y(min)}" y2="${y(min)}"/>
      <path class="line" d="${d}" vector-effect="non-scaling-stroke"/><circle cx="${x(last)}" cy="${y(last.price)}" r="4" fill="var(--primary)"/></svg>
    <div class="chart-labels"><span>${dayLabel(points[0].day)}</span><span>${dayLabel(last.day)}</span></div>`;
}

// --- dialogs ------------------------------------------------------------------

function openRegion(first = false) {
  const dlg = $('dlg-region');
  const form = dlg.querySelector('form');
  const sel = settings.region ?? { kind: 'ccaa', ids: [] };
  form.kind.value = sel.kind;
  $('region-cancel').hidden = first;
  const chosen = new Set(sel.ids);
  const draw = () => {
    const kind = form.kind.value;
    const list = kind === 'ccaa' ? core.COMMUNITIES.map(([id, n]) => [id, n])
      : kind === 'prov' ? [...core.PROVINCES].sort((a, b) => a[1].localeCompare(b[1], 'es')).map(([id, n, c]) => [id, `${n} · ${core.COMMUNITIES.find((x) => x[0] === c)[1]}`])
        : [];
    $('region-options').innerHTML = kind === 'all' ? `<p>${T.allWarning}</p>`
      : list.map(([id, n]) => `<label><input type="checkbox" value="${id}" ${chosen.has(id) && kind === sel.kind ? 'checked' : ''}> ${esc(n)}</label>`).join('');
  };
  form.onchange = (e) => { if (e.target.name === 'kind') draw(); };
  draw();
  dlg.onclose = () => {
    if (dlg.returnValue !== 'ok') return;
    const kind = form.kind.value;
    const ids = [...$('region-options').querySelectorAll('input:checked')].map((i) => i.value);
    if (kind !== 'all' && !ids.length) { if (!settings.region) openRegion(true); return; }
    settings.region = { kind, ids: kind === 'all' ? [] : ids };
    save();
    closePanel();
    loadPrices();
  };
  dlg.showModal();
}

function openBrands() {
  const dlg = $('dlg-brands');
  const counts = new Map();
  for (const s of state.stations) {
    if (!s.prices[settings.fuel]) continue;
    const k = s.brand.independent ? 'independent' : s.brand.key;
    counts.set(k, (counts.get(k) ?? 0) + 1);
  }
  const items = [...counts.entries()].sort((a, b) => b[1] - a[1]);
  $('brand-options').innerHTML = items.map(([k, n]) => `<label><input type="checkbox" value="${esc(k)}" ${settings.hidden.has(k) ? '' : 'checked'}>
    ${esc(k === 'independent' ? T.independent : state.catalog.byKey[k]?.name ?? k)} (${n})</label>`).join('');
  $('brands-all').onclick = (e) => { e.preventDefault(); $('brand-options').querySelectorAll('input').forEach((i) => { i.checked = true; }); };
  dlg.onclose = () => {
    if (dlg.returnValue !== 'ok') return;
    settings.hidden = new Set([...$('brand-options').querySelectorAll('input:not(:checked)')].map((i) => i.value));
    save();
    refresh();
  };
  dlg.showModal();
}

function openSettings() {
  const chips = (name, options, current, label) => `<div class="chips">${options.map((o) => `<button type="button" class="chip ${o === current ? 'on' : ''}" data-set="${name}" data-value="${o}">${label(o)}</button>`).join('')}</div>`;
  // Only the plans published at stations of this region.
  const ids = new Set(state.stations.flatMap((s) => state.plans?.stations?.[s.id] ?? []));
  const byOperator = new Map();
  for (const id of ids) {
    const p = { id, ...state.plans.plans[String(id)] };
    if (!p.name) continue;
    if (!byOperator.has(p.operator)) byOperator.set(p.operator, []);
    byOperator.get(p.operator).push(p);
  }
  const plans = [...byOperator.entries()].sort().map(([op, list]) => `<h4>${esc(op)}</h4>${list.sort((a, b) => a.name.localeCompare(b.name)).map((p) => `
    <label class="plan"><input type="checkbox" data-plan="${p.id}" ${settings.owned.has(p.id) ? 'checked' : ''} ${core.appliable(p) ? '' : 'disabled'}> <span>${esc(p.name)}</span></label>`).join('')}`).join('');
  $('settings-body').innerHTML = `
    <h3>${T.region}</h3><p>${esc(regionSummary(settings.region))} <button type="button" class="btn" data-act="region">${T.change}</button></p>
    <h3>${T.historySetting}</h3>${chips('historyDays', [0, 7, 30, 90], settings.historyDays, (d) => (d ? T.days(d) : T.off))}<p class="muted">${T.historyExplain}</p>
    <h3>${T.radius}</h3>${chips('radiusKm', [5, 10, 25], settings.radiusKm, (k) => T.km(k))}
    <h3>${T.plansOwned}</h3>${plans || `<p class="muted">${state.plans ? T.plansEmpty : T.plansUnknown}</p>`}
    <h3>${T.language}</h3>${chips('lang', ['', 'en', 'es'], settings.lang, (l) => ({ '': T.system, en: 'English', es: 'Español' }[l]))}
    <h3>${T.about}</h3><p class="muted">${T.aboutText}</p><p class="muted">${T.attribution}</p>
    <p class="muted">${state.schedule ? esc(T.schedule(state.schedule.version, state.schedule.updated)) : ''} ${state.plans?.generated ? esc(T.plansDate(state.plans.generated.slice(0, 10))) : ''}</p>`;
  $('dlg-settings').showModal();
}

// --- events -------------------------------------------------------------------

function wire() {
  $('fuel').innerHTML = core.FUELS.filter((f) => f.pickable).map((f) => `<option value="${f.key}">${esc(f[LANG])}</option>`).join('');
  $('fuel').value = settings.fuel;
  $('fuel').addEventListener('change', () => { settings.fuel = $('fuel').value; save(); refresh(); });
  $('btn-refresh').addEventListener('click', loadPrices);
  $('btn-brands').addEventListener('click', openBrands);
  $('btn-settings').addEventListener('click', openSettings);
  $('btn-locate').addEventListener('click', () => locate(true));
  $('btn-view').addEventListener('click', () => {
    state.view = state.view === 'map' ? 'list' : 'map';
    $('list').hidden = state.view !== 'list';
    $('btn-view').textContent = state.view === 'list' ? '🗺' : '☰';
    $('btn-view').title = state.view === 'list' ? T.map : T.list;
    if (state.view === 'list') renderList(); else map?.resize();
  });
  document.querySelectorAll('[data-sort]').forEach((b) => b.addEventListener('click', () => { state.sort = b.dataset.sort; renderList(); }));
  $('chip-fav').addEventListener('click', () => { state.favOnly = !state.favOnly; renderList(); });
  $('list-rows').addEventListener('click', (e) => { const li = e.target.closest('li[data-id]'); if (li) openStation(li.dataset.id); });

  $('panel').addEventListener('click', async (e) => {
    const act = e.target.closest('[data-act]')?.dataset.act;
    const li = e.target.closest('li[data-id]:not(.this)');
    const s = state.byId.get(state.openId);
    if (li) openStation(li.dataset.id);
    else if (act === 'close') closePanel();
    else if (act === 'nearby') { state.nearbyOpen = !state.nearbyOpen; renderPanel(); }
    else if (act === 'fav' && s) {
      if (settings.favourites.has(s.id)) settings.favourites.delete(s.id); else settings.favourites.add(s.id);
      save(); refresh();
    } else if (act === 'share' && s) {
      const text = `${s.sign || s.brand.name}, ${s.address}\n${fuelName(settings.fuel)}: ${s.prices[settings.fuel] ? fmt(s.prices[settings.fuel]) : '—'} €/l\n${mapsUrl(s)}`;
      if (navigator.share) navigator.share({ text }).catch(() => {});
      else { await navigator.clipboard?.writeText(text); e.target.textContent = T.copied; }
    }
  });
  const togglePlan = (e) => {
    const box = e.target.closest('input[data-plan]');
    if (!box) return;
    const id = Number(box.dataset.plan);
    if (box.checked) settings.owned.add(id); else settings.owned.delete(id);
    save(); refresh();
  };
  $('panel').addEventListener('change', togglePlan);
  $('settings-body').addEventListener('change', togglePlan);
  $('settings-body').addEventListener('click', (e) => {
    const b = e.target.closest('[data-set], [data-act]');
    if (!b) return;
    if (b.dataset.act === 'region') { $('dlg-settings').close(); openRegion(); return; }
    const { set, value } = b.dataset;
    if (set === 'lang') { settings.lang = value; save(); location.reload(); return; }
    settings[set] = Number(value);
    save();
    openSettings();
    refresh();
  });
  document.addEventListener('keydown', (e) => { if (e.key === 'Escape' && !$('panel').hidden) closePanel(); });
}

function locate(fly) {
  if (!navigator.geolocation) return;
  navigator.geolocation.getCurrentPosition((pos) => {
    state.location = { lat: pos.coords.latitude, lon: pos.coords.longitude };
    $('btn-locate').classList.add('on');
    refresh();
    if (fly) map?.flyTo({ center: [state.location.lon, state.location.lat], zoom: 12 });
  }, () => { if (fly) status(T.locError, true); }, { enableHighAccuracy: false, maximumAge: 600000, timeout: 15000 });
}

// --- start --------------------------------------------------------------------

async function main() {
  document.documentElement.lang = LANG;
  document.querySelectorAll('[data-i18n]').forEach((el) => { el.textContent = T[el.dataset.i18n]; });
  document.querySelectorAll('[data-i18n-title]').forEach((el) => { el.title = T[el.dataset.i18nTitle]; el.setAttribute('aria-label', el.title); });
  wire();

  const [schedule, brands, products, plans, logoIndex] = await Promise.all([
    data('tax-schedule.json'), data('brands.json'), data('fuel-products.json'),
    json('data/plans.json').catch(() => null), json('data/logos/index.json').catch(() => ({})),
  ]);
  state.schedule = schedule;
  state.catalog = core.brandCatalog(brands);
  state.products = products?.products ?? [];
  state.productsBaseline = products?.baseline;
  state.plans = plans;
  state.logoIndex = logoIndex ?? {};

  await initMap();
  // Location only when already allowed: asking on page load is rude.
  navigator.permissions?.query({ name: 'geolocation' }).then((p) => { if (p.state === 'granted') locate(false); }).catch(() => {});

  if (!settings.region) { status(T.noRegion); openRegion(true); return; }
  await loadPrices();
  const deep = decodeURIComponent(location.hash.slice(1));
  if (deep && state.byId.has(deep)) openStation(deep);
}

main();
