#!/usr/bin/env node
/**
 * Generates the sprite catalog the app ships in `app/src/main/assets/`.
 *
 *   node tools/build-catalog.mjs [--no-cache] [--skip-icons]
 *
 * Outputs
 *   assets/sets.json      every sprite set, its variants, and which Pokémon each covers
 *   assets/catalog.json   every Pokémon: name, dex number, generation, types, form
 *   assets/icons.bin      concatenated box-icon PNGs for the offline picker grid
 *   assets/icons.idx      byte offsets into icons.bin, keyed by Pokémon id
 *
 * PokeAPI content is pinned to one commit, so the output is reproducible and the app's
 * on-device cache never goes stale. veekun's dump is a finished archive of shipped games
 * and is not versioned, but it is equally immutable in practice.
 *
 * Two sources, because PokeAPI has no copy of the original animation for a third of the
 * series: veekun is the only public host of Emerald's real battle animations and of the
 * second frame of every Generation 4 idle. See VEEKUN_SETS in sets.config.mjs.
 *
 * Network shape: ~15 GitHub *API* calls (60/hour unauthenticated — hence the disk
 * cache in tools/.cache), a handful of veekun directory indexes, plus unmetered
 * raw.githubusercontent / jsDelivr fetches.
 */

import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { SET_META, VARIANT_SEGMENTS, SKIPPED_SETS, VEEKUN_SETS } from './sets.config.mjs';

const SPRITES_SHA = 'c10459b9b0129eaca5c5d9b1cac65336debb1d08';
const REPO = 'PokeAPI/sprites';
const POKEAPI_DATA = 'https://raw.githubusercontent.com/PokeAPI/pokeapi/master/data/v2/csv';
const CDN = (p) => `https://cdn.jsdelivr.net/gh/${REPO}@${SPRITES_SHA}/${p}`;

const HERE = path.dirname(fileURLToPath(import.meta.url));
const ROOT = path.resolve(HERE, '..');
const ASSETS = path.join(ROOT, 'app', 'src', 'main', 'assets');
const CACHE = path.join(HERE, '.cache');

const args = new Set(process.argv.slice(2));
const useCache = !args.has('--no-cache');
const skipIcons = args.has('--skip-icons');

// ---------------------------------------------------------------------------
// Fetch helpers
// ---------------------------------------------------------------------------

fs.mkdirSync(CACHE, { recursive: true });

const cacheKey = (url) => path.join(CACHE, url.replace(/[^a-z0-9]+/gi, '_').slice(-180));

async function fetchText(url, { cache = true } = {}) {
  const key = cacheKey(url);
  if (cache && useCache && fs.existsSync(key)) return fs.readFileSync(key, 'utf8');
  const res = await fetchWithRetry(url);
  const text = await res.text();
  if (cache) fs.writeFileSync(key, text);
  return text;
}

async function fetchBuffer(url, { cache = true } = {}) {
  const key = cacheKey(url) + '.bin';
  if (cache && useCache && fs.existsSync(key)) return fs.readFileSync(key);
  const res = await fetchWithRetry(url);
  const buf = Buffer.from(await res.arrayBuffer());
  if (cache) fs.writeFileSync(key, buf);
  return buf;
}

async function fetchWithRetry(url, attempts = 4) {
  let lastErr;
  for (let i = 0; i < attempts; i++) {
    try {
      const res = await fetch(url, {
        headers: { 'user-agent': 'pokewidget-catalog-generator' },
      });
      if (res.status === 403 || res.status === 429) {
        const reset = res.headers.get('x-ratelimit-reset');
        throw new Error(
          `GitHub rate limit hit on ${url}` +
            (reset ? ` (resets ${new Date(+reset * 1000).toLocaleTimeString()})` : '') +
            '. Existing responses are cached in tools/.cache — rerun later to resume.',
        );
      }
      if (!res.ok) throw new Error(`HTTP ${res.status} for ${url}`);
      return res;
    } catch (err) {
      lastErr = err;
      if (String(err.message).includes('rate limit')) throw err;
      await new Promise((r) => setTimeout(r, 500 * 2 ** i));
    }
  }
  throw lastErr;
}

// ---------------------------------------------------------------------------
// Step 1 — discover every sprite file under sprites/pokemon/
// ---------------------------------------------------------------------------

/**
 * The repo-wide recursive tree API truncates (62k entries / 7 MB cap), so walk it in
 * pieces: one recursive tree request per generation directory plus one for `other/`.
 */
async function listSpriteFiles() {
  const tree = async (sha, recursive) =>
    JSON.parse(
      await fetchText(
        `https://api.github.com/repos/${REPO}/git/trees/${sha}${recursive ? '?recursive=1' : ''}`,
      ),
    );

  const find = (node, name) => {
    const hit = node.tree.find((e) => e.path === name);
    if (!hit) throw new Error(`expected "${name}" in tree`);
    return hit.sha;
  };

  const root = await tree(SPRITES_SHA, false);
  const sprites = await tree(find(root, 'sprites'), false);
  const pokemon = await tree(find(sprites, 'pokemon'), false);

  /** @type {string[]} paths relative to sprites/pokemon/ */
  const files = [];

  const collect = async (sha, prefix) => {
    const t = await tree(sha, true);
    if (t.truncated) {
      throw new Error(
        `tree for "${prefix}" was truncated; split the walk further before trusting the output`,
      );
    }
    for (const e of t.tree) {
      if (e.type === 'blob') files.push(`${prefix}/${e.path}`);
    }
  };

  for (const entry of pokemon.tree) {
    // Files sitting directly in sprites/pokemon/ are PokeAPI's "default" front sprites.
    if (entry.type === 'blob') { files.push(entry.path); continue; }
    if (entry.path === 'versions') {
      const versions = await tree(entry.sha, false);
      for (const gen of versions.tree) {
        if (gen.type === 'tree') await collect(gen.sha, `versions/${gen.path}`);
      }
    } else {
      await collect(entry.sha, entry.path);
    }
  }
  return files;
}

/**
 * Split `versions/generation-v/black-white/animated/back/shiny/384.gif` into
 * `{ set: 'versions/generation-v/black-white/animated', variant: 'back/shiny', id: 384 }`.
 *
 * Returns null for anything that isn't a numeric sprite file (README, .gitkeep, and
 * the handful of named files upstream keeps around).
 */
function classify(relPath) {
  const segments = relPath.split('/');
  const file = segments.pop();
  const m = /^(\d+)\.(png|gif|svg)$/.exec(file);
  if (!m) return null;

  const setParts = [];
  const variantParts = [];
  let inVariants = false;
  for (const s of segments) {
    if (!inVariants && VARIANT_SEGMENTS.has(s)) inVariants = true;
    (inVariants ? variantParts : setParts).push(s);
  }
  return {
    set: setParts.join('/'),
    variant: variantParts.join('/'),
    id: Number(m[1]),
    ext: m[2],
  };
}

// ---------------------------------------------------------------------------
// Step 1b — veekun's sprite dump
// ---------------------------------------------------------------------------

const VEEKUN_BASE = 'https://veekun.com/dex/media';
const VEEKUN_ROOT = 'pokemon/main-sprites';

/**
 * Ids present in one veekun directory.
 *
 * veekun has no API and no GitHub mirror — `veekun/pokedex-media` contains only a README
 * — so coverage is read off the plain Apache directory index, which is stable and cheap
 * (one request per directory). Files named for a form rather than an id, such as Unown's
 * `201-a.gif`, are skipped: the app addresses sprites by PokéAPI id, and there is no
 * reliable mapping from those suffixes back to a form id.
 */
async function listVeekunIds(dir, ext) {
  const html = await fetchText(`${VEEKUN_BASE}/${VEEKUN_ROOT}/${dir}/`);
  const ids = new Set();
  const pattern = new RegExp(`href="(\\d+)\\.${ext}"`, 'gi');
  for (const m of html.matchAll(pattern)) ids.add(Number(m[1]));
  if (!ids.size) throw new Error(`no ${ext} sprites found in veekun "${dir}" — did the index change?`);
  return ids;
}

/**
 * Builds the `variants` map for one veekun set.
 *
 * For a composite set the answer is the *intersection* across frame directories: a
 * Pokémon whose second frame is missing cannot be animated, and claiming otherwise would
 * put a widget on a sprite that silently renders as a still.
 */
async function collectVeekunSet(setPath, meta) {
  const frameDirs = meta.frameDirs ?? [''];
  const variants = {};
  for (const variant of meta.variants) {
    let ids = null;
    for (const frame of frameDirs) {
      const dir = [setPath, frame, variant].filter(Boolean).join('/');
      const found = await listVeekunIds(dir, meta.ext);
      ids = ids === null ? found : new Set([...ids].filter((id) => found.has(id)));
    }
    variants[variant] = toRanges(ids ?? new Set());
  }
  return variants;
}

/** Collapse a sorted id list into `"1-1025,10001-10099"`. Sprite ids are near-contiguous. */
function toRanges(ids) {
  const sorted = [...ids].sort((a, b) => a - b);
  const out = [];
  let start = null;
  let prev = null;
  for (const n of sorted) {
    if (start === null) {
      start = prev = n;
    } else if (n === prev + 1) {
      prev = n;
    } else {
      out.push(start === prev ? `${start}` : `${start}-${prev}`);
      start = prev = n;
    }
  }
  if (start !== null) out.push(start === prev ? `${start}` : `${start}-${prev}`);
  return out.join(',');
}

// ---------------------------------------------------------------------------
// Step 2 — Pokémon metadata from the PokeAPI CSV dumps
// ---------------------------------------------------------------------------

/** Minimal RFC 4180 parser — these files do contain quoted fields with commas. */
function parseCsv(text) {
  const rows = [];
  let row = [];
  let field = '';
  let quoted = false;
  for (let i = 0; i < text.length; i++) {
    const c = text[i];
    if (quoted) {
      if (c === '"') {
        if (text[i + 1] === '"') { field += '"'; i++; } else quoted = false;
      } else field += c;
    } else if (c === '"') quoted = true;
    else if (c === ',') { row.push(field); field = ''; }
    else if (c === '\n') { row.push(field); field = ''; rows.push(row); row = []; }
    else if (c !== '\r') field += c;
  }
  if (field.length || row.length) { row.push(field); rows.push(row); }

  const header = rows.shift();
  return rows
    .filter((r) => r.length === header.length)
    .map((r) => Object.fromEntries(header.map((h, i) => [h, r[i]])));
}

const csv = async (name) => parseCsv(await fetchText(`${POKEAPI_DATA}/${name}`));

const ENGLISH = '9';

async function buildPokemonMetadata() {
  const [pokemon, species, speciesNames, pokemonTypes, types, forms, formNames] =
    await Promise.all([
      csv('pokemon.csv'),
      csv('pokemon_species.csv'),
      csv('pokemon_species_names.csv'),
      csv('pokemon_types.csv'),
      csv('types.csv'),
      csv('pokemon_forms.csv'),
      csv('pokemon_form_names.csv'),
    ]);

  const typeName = new Map(types.map((t) => [t.id, t.identifier]));
  const genBySpecies = new Map(species.map((s) => [s.id, Number(s.generation_id)]));
  const nameBySpecies = new Map(
    speciesNames.filter((n) => n.local_language_id === ENGLISH).map((n) => [n.pokemon_species_id, n.name]),
  );

  const typesByPokemon = new Map();
  for (const t of pokemonTypes.sort((a, b) => Number(a.slot) - Number(b.slot))) {
    if (!typesByPokemon.has(t.pokemon_id)) typesByPokemon.set(t.pokemon_id, []);
    typesByPokemon.get(t.pokemon_id).push(typeName.get(t.type_id));
  }

  // A Pokémon id can have several forms; the first non-default one carries the label
  // ("Attack Forme", "Alolan"). Default forms have an empty form_name upstream.
  const formLabelByPokemon = new Map();
  const formNameById = new Map(
    formNames.filter((f) => f.local_language_id === ENGLISH).map((f) => [f.pokemon_form_id, f.form_name]),
  );
  for (const f of forms) {
    const label = formNameById.get(f.id);
    if (label && !formLabelByPokemon.has(f.pokemon_id)) {
      formLabelByPokemon.set(f.pokemon_id, label);
    }
  }

  const out = new Map();
  for (const p of pokemon) {
    const id = Number(p.id);
    const speciesId = p.species_id;
    const base = nameBySpecies.get(speciesId) ?? titleCase(p.identifier);
    const form = p.is_default === '1' ? undefined : formLabelByPokemon.get(p.id);
    out.set(id, {
      i: id,
      n: base,
      d: Number(speciesId),
      g: genBySpecies.get(speciesId) ?? 0,
      t: typesByPokemon.get(p.id) ?? [],
      ...(form ? { f: form } : {}),
      // Kept for search: "deoxys-attack" matches a user typing "attack".
      s: p.identifier,
    });
  }
  return out;
}

const titleCase = (slug) =>
  slug.split('-').map((w) => w.charAt(0).toUpperCase() + w.slice(1)).join(' ');

// ---------------------------------------------------------------------------
// Step 3 — offline box icons for the picker grid
// ---------------------------------------------------------------------------

/**
 * Packs every box icon into one blob plus an index, rather than ~1000 asset files.
 * Android's asset packaging and open() cost per file makes the blob meaningfully
 * faster to load and smaller on disk.
 */
async function buildIconPack(iconSets, wantedIds) {
  const chunks = [];
  const index = [];
  let offset = 0;
  let hits = 0;

  const limit = 16;
  const queue = [...wantedIds].sort((a, b) => a - b);
  const results = new Map();

  await Promise.all(
    Array.from({ length: limit }, async () => {
      while (queue.length) {
        const id = queue.shift();
        for (const set of iconSets) {
          if (!set.ids.has(id)) continue;
          try {
            results.set(id, await fetchBuffer(CDN(`sprites/pokemon/${set.path}/${id}.png`)));
          } catch {
            /* a missing icon is not fatal — the picker falls back to a placeholder */
          }
          break;
        }
      }
    }),
  );

  for (const id of [...results.keys()].sort((a, b) => a - b)) {
    const buf = results.get(id);
    chunks.push(buf);
    index.push(`${id}:${offset}:${buf.length}`);
    offset += buf.length;
    hits++;
  }
  return { blob: Buffer.concat(chunks), index: index.join('\n'), hits };
}

// ---------------------------------------------------------------------------
// Main
// ---------------------------------------------------------------------------

async function main() {
  console.log(`PokeAPI/sprites @ ${SPRITES_SHA.slice(0, 10)}\n`);

  console.log('1/4  walking sprite tree…');
  const files = await listSpriteFiles();

  /** @type {Map<string, Map<string, Set<number>>>} set -> variant -> ids */
  const bySet = new Map();
  let skippedFiles = 0;
  for (const f of files) {
    const c = classify(f);
    if (!c) { skippedFiles++; continue; }
    if (!bySet.has(c.set)) bySet.set(c.set, new Map());
    const variants = bySet.get(c.set);
    if (!variants.has(c.variant)) variants.set(c.variant, new Set());
    variants.get(c.variant).add(c.id);
  }
  console.log(`     ${files.length} files, ${bySet.size} sprite sets, ${skippedFiles} non-sprite files ignored`);

  const unknown = [...bySet.keys()].filter((k) => !SET_META[k] && !SKIPPED_SETS[k]);
  if (unknown.length) {
    console.log('\n     ! sprite sets found upstream but missing from sets.config.mjs:');
    for (const u of unknown) console.log(`       ${u}  (${bySet.get(u).get('')?.size ?? 0} sprites)`);
    console.log('       They are omitted from the app. Add them to SET_META or SKIPPED_SETS.\n');
  }

  console.log('2/4  fetching Pokémon metadata…');
  const meta = await buildPokemonMetadata();
  console.log(`     ${meta.size} Pokémon (species + alternate forms)`);

  // Build sets.json, ordered as configured.
  const sets = [];
  for (const [setPath, m] of Object.entries(SET_META)) {
    const variants = bySet.get(setPath);
    if (!variants) {
      console.log(`     ! configured set "${setPath}" has no files upstream — skipped`);
      continue;
    }
    const variantMap = {};
    for (const [variant, ids] of [...variants.entries()].sort()) {
      variantMap[variant] = toRanges(ids);
    }
    sets.push({
      // The default set lives at the root of sprites/pokemon/, so it has an empty path.
      id: setPath === '' ? 'default' : setPath.replace(/[^a-z0-9]+/gi, '_'),
      path: setPath === '' ? 'sprites/pokemon' : `sprites/pokemon/${setPath}`,
      label: m.label,
      game: m.game,
      hardware: m.hardware,
      gen: m.gen,
      animated: m.animated,
      ext: m.ext,
      order: m.order,
      ...(m.note ? { note: m.note } : {}),
      variants: variantMap,
    });
  }

  // veekun's sets carry the animation PokeAPI has no copy of: Emerald's real battle
  // sequences, and the second frame of every Gen 4 idle.
  console.log('     reading veekun directory indexes…');
  for (const [setPath, m] of Object.entries(VEEKUN_SETS)) {
    let variantMap;
    try {
      variantMap = await collectVeekunSet(setPath, m);
    } catch (err) {
      console.log(`     ! veekun set "${setPath}" unavailable (${err.message}) — skipped`);
      continue;
    }
    const covered = Object.values(variantMap).some(Boolean);
    if (!covered) {
      console.log(`     ! veekun set "${setPath}" matched no sprites — skipped`);
      continue;
    }
    sets.push({
      id: `veekun_${setPath.replace(/[^a-z0-9]+/gi, '_')}`,
      path: `${VEEKUN_ROOT}/${setPath}`,
      label: m.label,
      game: m.game,
      hardware: m.hardware,
      gen: m.gen,
      animated: m.animated,
      ext: m.ext,
      order: m.order,
      ...(m.note ? { note: m.note } : {}),
      variants: variantMap,
      provider: 'veekun',
      ...(m.frameDirs ? { frameDirs: m.frameDirs } : {}),
      ...(m.frameDelaysMs ? { frameDelaysMs: m.frameDelaysMs } : {}),
    });
    const front = variantMap[''] ?? '';
    const count = front.split(',').filter(Boolean)
      .reduce((n, p) => { const [a, b] = p.split('-').map(Number); return n + (b ?? a) - a + 1; }, 0);
    console.log(`       ${m.label.padEnd(26)} ${String(count).padStart(4)} sprites` +
      `${m.frameDirs ? `, ${m.frameDirs.length} frames each` : ''}`);
  }

  sets.sort((a, b) => a.order - b.order);

  // Only ship metadata for Pokémon at least one set can actually render.
  const renderable = new Set();
  for (const s of sets) {
    for (const range of Object.values(s.variants)) {
      for (const part of range.split(',')) {
        if (!part) continue;
        const [a, b] = part.split('-').map(Number);
        for (let i = a; i <= (b ?? a); i++) renderable.add(i);
      }
    }
  }
  const catalog = [...renderable]
    .sort((a, b) => a - b)
    .map((id) => meta.get(id))
    .filter(Boolean);

  const missingMeta = [...renderable].filter((id) => !meta.has(id));
  console.log(`     ${catalog.length} renderable Pokémon; ${missingMeta.length} sprite ids with no metadata (ignored)`);

  fs.mkdirSync(ASSETS, { recursive: true });
  write('sets.json', JSON.stringify({ spritesSha: SPRITES_SHA, sets }));
  write('catalog.json', JSON.stringify({ pokemon: catalog }));

  console.log('3/4  packing offline box icons…');
  if (skipIcons) {
    console.log('     skipped (--skip-icons)');
  } else {
    const iconSets = ['versions/generation-viii/icons', 'versions/generation-vii/icons']
      .filter((p) => bySet.has(p))
      .map((p) => ({ path: p, ids: bySet.get(p).get('') ?? new Set() }));
    const { blob, index, hits } = await buildIconPack(iconSets, renderable);
    write('icons.bin', blob);
    write('icons.idx', index);
    console.log(`     ${hits}/${catalog.length} Pokémon have an offline icon`);
  }

  console.log('\n4/4  summary');
  const animated = sets.filter((s) => s.animated);
  console.log(`     ${sets.length} sprite sets — ${animated.length} animated, ${sets.length - animated.length} still (generated idle)`);
  for (const s of sets) {
    const front = s.variants[''] ?? '';
    const count = front.split(',').filter(Boolean).reduce((acc, part) => {
      const [a, b] = part.split('-').map(Number);
      return acc + (b ?? a) - a + 1;
    }, 0);
    const flags = Object.keys(s.variants).filter(Boolean).join(' ') || '—';
    console.log(
      `       ${s.animated ? '▶' : ' '} ${s.label.padEnd(34)} ${String(count).padStart(5)} sprites   ${flags}`,
    );
  }
}

function write(name, data) {
  const p = path.join(ASSETS, name);
  fs.writeFileSync(p, data);
  const size = fs.statSync(p).size;
  console.log(`     wrote assets/${name} (${(size / 1024).toFixed(0)} KB)`);
}

main().catch((err) => {
  console.error(`\nFAILED: ${err.message}`);
  process.exit(1);
});
