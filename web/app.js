// openfuel web (phase 5). No build step: plain JS, MapLibre GL JS from jsDelivr.
// Same data sources and the same tax schedule as the Android app.
'use strict';

const API = 'https://sedeaplicaciones.minetur.gob.es/ServiciosRESTCarburantes/PreciosCarburantes';
const STYLE = 'https://tiles.openfreemap.org/styles/liberty';
const SCHEDULE_URLS = [
  'tax-schedule.json',
  'https://raw.githubusercontent.com/PabloSoage/openfuel/main/core/src/main/resources/tax-schedule.json',
];

// API price field -> fuel key (same names as core/.../model/Fuel.kt) and excise epígrafe.
const FUELS = [
  ['GOA', 'Precio Gasoleo A', '1.3', 'Gasóleo A', 'Diesel'],
  ['GOA_PREMIUM', 'Precio Gasoleo Premium', '1.3', 'Gasóleo premium', 'Premium diesel'],
  ['G95E5', 'Precio Gasolina 95 E5', '1.2.2', 'Gasolina 95 E5', 'Petrol 95 E5'],
  ['G95E10', 'Precio Gasolina 95 E10', '1.2.2', 'Gasolina 95 E10', 'Petrol 95 E10'],
  ['G95E5_PREMIUM', 'Precio Gasolina 95 E5 Premium', '1.2.2', 'Gasolina 95 premium', 'Premium petrol 95'],
  ['G98E5', 'Precio Gasolina 98 E5', '1.2.1', 'Gasolina 98 E5', 'Petrol 98 E5'],
  ['G98E10', 'Precio Gasolina 98 E10', '1.2.1', 'Gasolina 98 E10', 'Petrol 98 E10'],
  ['BIODIESEL', 'Precio Biodiesel', '1.14', 'Biodiésel', 'Biodiesel'],
  ['GOB', 'Precio Gasoleo B', null, 'Gasóleo B', 'Agricultural diesel (B)'],
  ['GLP', 'Precio Gases licuados del petróleo', null, 'GLP', 'LPG'],
  ['GNC', 'Precio Gas Natural Comprimido', null, 'GNC', 'CNG'],
  ['GNL', 'Precio Gas Natural Licuado', null, 'GNL', 'LNG'],
  ['DIESEL_RENOVABLE', 'Precio Diésel Renovable', null, 'Diésel renovable (HVO)', 'Renewable diesel (HVO)'],
  ['ADBLUE', 'Precio Adblue', null, 'AdBlue', 'AdBlue'],
];

const COMMUNITIES = [
  ['01', 'Andalucía'], ['02', 'Aragón'], ['03', 'Asturias'], ['04', 'Illes Balears'], ['05', 'Canarias'],
  ['06', 'Cantabria'], ['07', 'Castilla-La Mancha'], ['08', 'Castilla y León'], ['09', 'Cataluña'],
  ['10', 'Comunitat Valenciana'], ['11', 'Extremadura'], ['12', 'Galicia'], ['13', 'Comunidad de Madrid'],
  ['14', 'Región de Murcia'], ['15', 'Navarra'], ['16', 'País Vasco'], ['17', 'La Rioja'], ['18', 'Ceuta'], ['19', 'Melilla'],
];

const ES = (navigator.language || 'en').toLowerCase().startsWith('es');
const T = ES ? {
  fuel: 'Carburante', region: 'Región', reload: 'Recargar', all: 'Toda España (12 MB)',
  loading: 'Descargando precios…', prices: (f, n) => `Precios del ${f} · ${n} estaciones`,
  error: (e) => `No se pudo cargar: ${e}`, maps: 'Abrir en Google Maps',
  vat: 'IVA', excise: 'Impuesto sobre hidrocarburos', rest: 'Producto, logística y margen',
  taxes: (a, b) => `Impuestos: ${a} €/l (${b} del precio)`, unconfirmed: 'Tipo impositivo sin confirmar para esta fecha.',
  source: 'Fuente', noTerritory: 'Sin desglose en Canarias, Ceuta y Melilla (IGIC / IPSI).',
  noProduct: 'Sin desglose para este carburante.', noPeriod: 'Sin tipos impositivos para esta fecha.',
} : {
  fuel: 'Fuel', region: 'Region', reload: 'Reload', all: 'All of Spain (12 MB)',
  loading: 'Downloading prices…', prices: (f, n) => `Prices of ${f} · ${n} stations`,
  error: (e) => `Could not load: ${e}`, maps: 'Open in Google Maps',
  vat: 'VAT', excise: 'Hydrocarbon excise', rest: 'Product, logistics and margin',
  taxes: (a, b) => `Taxes: ${a} €/l (${b} of the price)`, unconfirmed: 'Tax rate not confirmed for this date.',
  source: 'Source', noTerritory: 'No breakdown in Canarias, Ceuta and Melilla (IGIC / IPSI).',
  noProduct: 'No breakdown for this fuel.', noPeriod: 'No tax rates on record for this date.',
};

const $ = (id) => document.getElementById(id);
const num = (raw) => { const s = (raw || '').trim(); if (!s) return null; const n = Number(s.replace(',', '.')); return Number.isFinite(n) ? n : null; };
const fmt = (v, d = 3) => v.toLocaleString(ES ? 'es-ES' : 'en-GB', { minimumFractionDigits: d, maximumFractionDigits: d });
const esc = (s) => String(s).replace(/[&<>"']/g, (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));
const todayMadrid = () => new Intl.DateTimeFormat('sv-SE', { timeZone: 'Europe/Madrid' }).format(new Date());

let schedule = null;
let stations = [];
let published = '';

async function loadSchedule() {
  for (const url of SCHEDULE_URLS) {
    try {
      const r = await fetch(url);
      if (r.ok) return await r.json();
    } catch (_) { /* next */ }
  }
  return null;
}

function breakdown(price, fuelKey, ccaa) {
  if (['05', '18', '19'].includes(ccaa)) return { reason: T.noTerritory };
  const cat = FUELS.find((f) => f[0] === fuelKey)?.[2];
  if (!cat) return { reason: T.noProduct };
  const day = todayMadrid();
  const p = schedule?.periods.find((x) => day >= x.from && (x.to === null || day <= x.to));
  if (!p) return { reason: T.noPeriod };
  const rate = p.rates[cat];
  const excise = (rate.general + rate.especial) / 1000;
  const base = price / (1 + p.vat);
  return { vat: price - base, excise, rest: base - excise, vatRate: p.vat, confirmed: p.confirmed, source: p.source };
}

async function loadPrices(region) {
  $('status').textContent = T.loading;
  const url = region ? `${API}/EstacionesTerrestres/FiltroCCAA/${region}` : `${API}/EstacionesTerrestres/`;
  const r = await fetch(url, { headers: { Accept: 'application/json' } });
  if (!r.ok) throw new Error(`HTTP ${r.status}`);
  const data = JSON.parse((await r.text()).replace(/^﻿/, ''));
  if (data.ResultadoConsulta && data.ResultadoConsulta !== 'OK') throw new Error(data.ResultadoConsulta);
  published = data.Fecha || '';
  stations = [];
  for (const s of data.ListaEESSPrecio || []) {
    const lat = num(s['Latitud']);
    const lon = num(s['Longitud (WGS84)']);
    if (lat === null || lon === null) continue;
    const prices = {};
    for (const [key, field] of FUELS) {
      const p = num(s[field]);
      if (p && p > 0) prices[key] = p;
    }
    stations.push({
      id: s['IDEESS'], sign: (s['Rótulo'] || '').trim(), address: s['Dirección'] || '', locality: s['Localidad'] || '',
      schedule: s['Horario'] || '', ccaa: s['IDCCAA'] || '', lat, lon, prices,
    });
  }
}

function geojson(fuelKey) {
  const priced = stations.filter((s) => s.prices[fuelKey]).sort((a, b) => a.prices[fuelKey] - b.prices[fuelKey]);
  const n = priced.length;
  return {
    type: 'FeatureCollection',
    features: priced.map((s, i) => ({
      type: 'Feature',
      geometry: { type: 'Point', coordinates: [s.lon, s.lat] },
      properties: {
        id: s.id, rank: i, label: fmt(s.prices[fuelKey]),
        color: n < 3 ? '#b7791f' : i < n / 3 ? '#1b8a3a' : i >= n - n / 3 ? '#c62828' : '#b7791f',
      },
    })),
  };
}

function popupHtml(s, fuelKey) {
  const rows = FUELS.filter(([k]) => s.prices[k]).map(([k, , , es, en]) =>
    `<tr class="${k === fuelKey ? 'sel' : ''}"><td>${esc(ES ? es : en)}</td><td>${fmt(s.prices[k])} €/l</td></tr>`).join('');
  let tax = '';
  if (s.prices[fuelKey]) {
    const b = breakdown(s.prices[fuelKey], fuelKey, s.ccaa);
    if (b.reason) tax = `<p class="src">${esc(b.reason)}</p>`;
    else {
      const price = s.prices[fuelKey];
      const pct = (v) => fmt((v / price) * 100, 1) + ' %';
      tax = `<div class="bar"><span style="flex:${b.vat}"></span><span style="flex:${b.excise}"></span><span style="flex:${b.rest}"></span></div>
        <div class="legend"><i style="background:var(--vat)"></i>${T.vat} ${fmt(b.vatRate * 100, 0)} %: ${fmt(b.vat)} € (${pct(b.vat)})<br>
        <i style="background:var(--excise)"></i>${T.excise}: ${fmt(b.excise)} € (${pct(b.excise)})<br>
        <i style="background:var(--rest)"></i>${T.rest}: ${fmt(b.rest)} € (${pct(b.rest)})</div>
        <p>${T.taxes(fmt(b.vat + b.excise), pct(b.vat + b.excise))}</p>
        ${b.confirmed ? '' : `<p class="warn">${T.unconfirmed}</p>`}
        <p class="src">${T.source}: ${esc(b.source)}</p>`;
    }
  }
  const maps = `https://www.google.com/maps/search/?api=1&query=${s.lat},${s.lon}`;
  return `<div class="popup"><h2>${esc(s.sign || '—')}</h2>
    <p class="sub">${esc(s.address)}, ${esc(s.locality)}<br>${esc(s.schedule)}</p>
    <table>${rows}</table>${tax}
    <p><a href="${maps}" target="_blank" rel="noopener">${T.maps}</a></p></div>`;
}

function main() {
  document.documentElement.lang = ES ? 'es' : 'en';
  document.querySelectorAll('[data-i18n]').forEach((el) => { el.textContent = T[el.dataset.i18n]; });
  $('fuel').innerHTML = FUELS.map(([k, , , es, en]) => `<option value="${k}">${esc(ES ? es : en)}</option>`).join('');
  $('region').innerHTML = `<option value="">${esc(T.all)}</option>` +
    COMMUNITIES.map(([id, name]) => `<option value="${id}">${esc(name)}</option>`).join('');

  const map = new maplibregl.Map({ container: 'map', style: STYLE, center: [-3.7, 40.3], zoom: 5.2 });
  map.addControl(new maplibregl.NavigationControl(), 'top-right');
  map.addControl(new maplibregl.GeolocateControl({ positionOptions: { enableHighAccuracy: false } }), 'top-right');

  const render = () => {
    const fuelKey = $('fuel').value;
    const data = geojson(fuelKey);
    map.getSource('stations').setData(data);
    $('status').textContent = T.prices(published, data.features.length);
  };

  const reload = async () => {
    try {
      await loadPrices($('region').value);
      render();
      if ($('region').value && stations.length) {
        const b = new maplibregl.LngLatBounds();
        stations.forEach((s) => b.extend([s.lon, s.lat]));
        map.fitBounds(b, { padding: 40, maxZoom: 11 });
      }
    } catch (e) {
      $('status').textContent = T.error(e.message);
    }
  };

  map.on('load', async () => {
    schedule = await loadSchedule();
    map.addSource('stations', { type: 'geojson', data: { type: 'FeatureCollection', features: [] } });
    map.addLayer({
      id: 'dots', type: 'circle', source: 'stations',
      paint: {
        'circle-color': ['get', 'color'],
        'circle-radius': ['interpolate', ['linear'], ['zoom'], 5, 2.5, 10, 6, 14, 9],
        'circle-stroke-color': '#ffffff', 'circle-stroke-width': 1,
      },
    });
    map.addLayer({
      id: 'labels', type: 'symbol', source: 'stations', minzoom: 10,
      layout: {
        'text-field': ['get', 'label'], 'text-size': 12, 'text-offset': [0, 1.2],
        'symbol-sort-key': ['get', 'rank'], 'text-font': ['Noto Sans Regular'],
      },
      paint: { 'text-color': ['get', 'color'], 'text-halo-color': '#ffffff', 'text-halo-width': 1.5 },
    });
    map.on('click', 'dots', (e) => {
      const id = e.features[0].properties.id;
      const s = stations.find((x) => x.id === id);
      if (s) new maplibregl.Popup({ maxWidth: '320px' }).setLngLat([s.lon, s.lat]).setHTML(popupHtml(s, $('fuel').value)).addTo(map);
    });
    map.on('mouseenter', 'dots', () => { map.getCanvas().style.cursor = 'pointer'; });
    map.on('mouseleave', 'dots', () => { map.getCanvas().style.cursor = ''; });
    await reload();
  });

  $('fuel').addEventListener('change', () => { if (map.getSource('stations')) render(); });
  $('region').addEventListener('change', reload);
  $('reload').addEventListener('click', reload);
}

main();
