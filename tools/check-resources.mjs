// Fails fast, without Gradle, on the translation mistakes that are silent until
// someone switches language: a key missing in one language, a placeholder that
// differs between languages, a key used in code that does not exist, or a bare
// '%' inside a formatted string. Idea taken from opendash's check-resources.mjs.
import { readFileSync, readdirSync, statSync } from 'node:fs';
import { join } from 'node:path';

const RES = 'android/src/main/res';
const LANGS = { en: 'values', es: 'values-es' };

function strings(dir) {
  const xml = readFileSync(join(RES, dir, 'strings.xml'), 'utf8');
  const out = new Map();
  for (const m of xml.matchAll(/<string\s+name="([^"]+)"([^>]*)>([\s\S]*?)<\/string>/g)) {
    out.set(m[1], { value: m[3], translatable: !/translatable="false"/.test(m[2]) });
  }
  return out;
}

function placeholders(value) {
  return [...value.matchAll(/%(\d+)\$([sdf])/g)].map((m) => `${m[1]}$${m[2]}`).sort().join(',');
}

function walk(dir, files = []) {
  for (const name of readdirSync(dir)) {
    const p = join(dir, name);
    if (statSync(p).isDirectory()) walk(p, files);
    else if (p.endsWith('.kt')) files.push(p);
  }
  return files;
}

const errors = [];
const en = strings(LANGS.en);
const es = strings(LANGS.es);

for (const [key, s] of en) {
  if (!s.translatable) continue;
  if (!es.has(key)) errors.push(`missing in es: ${key}`);
  else if (placeholders(s.value) !== placeholders(es.get(key).value)) {
    errors.push(`placeholders differ for ${key}: en[${placeholders(s.value)}] es[${placeholders(es.get(key).value)}]`);
  }
}
for (const key of es.keys()) if (!en.has(key)) errors.push(`only in es: ${key}`);

for (const [lang, map] of [['en', en], ['es', es]]) {
  for (const [key, s] of map) {
    const formatted = /%\d+\$/.test(s.value);
    const stray = s.value.replace(/%%/g, '').replace(/%\d+\$[sdf]/g, '');
    if (formatted && stray.includes('%')) errors.push(`${lang}: bare % in formatted string ${key} (write %%)`);
    if (/(^|[^\\])'/.test(s.value)) errors.push(`${lang}: unescaped apostrophe in ${key}`);
  }
}

const used = new Set();
for (const file of walk('android/src/main/java')) {
  for (const m of readFileSync(file, 'utf8').matchAll(/R\.string\.([a-z0-9_]+)/g)) used.add(m[1]);
}
for (const key of used) if (!en.has(key)) errors.push(`used in code but not defined: ${key}`);

if (errors.length) {
  console.error(errors.join('\n'));
  process.exit(1);
}
console.log(`resources ok: ${en.size} strings, ${used.size} used in code, ${Object.keys(LANGS).length} languages`);
