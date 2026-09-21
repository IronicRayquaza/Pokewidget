#!/usr/bin/env node
/**
 * Generates the trainer and battle-background catalogs the app ships:
 *
 *   app/src/main/assets/trainers.json
 *   app/src/main/assets/backgrounds.json
 *
 * Usage:
 *   node tools/build-trainers.mjs
 *
 * Needs the network once, at build time. The app never lists a remote directory: it reads
 * these files, and fetches each image only when someone picks it.
 *
 *  - Trainer fronts are every PNG in Pokémon Showdown's `sprites/trainers/` index. They are
 *    not in the client's git repository, so they are served from play.pokemonshowdown.com,
 *    one file at a time and cached forever on the device.
 *  - Trainer backs come from pret's decompilations, pinned to one commit each, and exist
 *    only for the handful of player characters the games show from behind.
 *  - Battle backgrounds come from the Showdown client repository, pinned to one commit.
 *  - Scenery — calm biome backdrops, used without their platforms — comes from PokéRogue's art
 *    repository, pinned to one commit. See SCENERY in trainers.config.mjs.
 *
 * Who each trainer is — region, role, a readable name — lives in trainers.config.mjs.
 */

import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import {
  BACK_SPRITES, BACKGROUNDS, CHARACTERS, CLASS_NAMES, POKEROGUE_SHA, PRET, REGION_BY_GEN, REGIONS, ROLES,
  SCENERY, SHOWDOWN_CLIENT_SHA, VARIANT_LABELS,
} from './trainers.config.mjs';

const ROOT = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const ASSETS = path.join(ROOT, 'app/src/main/assets');
const UA = 'PokeWidget-build (+https://github.com/IronicRayquaza/Pokewidget)';

const TRAINER_INDEX = 'https://play.pokemonshowdown.com/sprites/trainers/';

async function get(url) {
  const res = await fetch(url, { headers: { 'User-Agent': UA } });
  if (!res.ok) throw new Error(`${res.status} for ${url}`);
  return Buffer.from(await res.arrayBuffer());
}

/** Width and height from a PNG's IHDR, which is always the first chunk. */
function pngSize(buf) {
  if (buf.readUInt32BE(12) !== 0x49484452) throw new Error('not a PNG');
  return { w: buf.readUInt32BE(16), h: buf.readUInt32BE(20) };
}

const titleCase = (s) => s.replace(/(^|[^a-z])([a-z])/g, (_, p, c) => p + c.toUpperCase());

function describe(file) {
  const id = file.replace(/\.png$/, '');
  const dash = id.indexOf('-');
  const base = dash < 0 ? id : id.slice(0, dash);
  const variant = dash < 0 ? '' : id.slice(dash + 1);
  const genMatch = variant.match(/^gen(\d)/);
  const suffixGen = genMatch ? Number(genMatch[1]) : null;

  const character = CHARACTERS[base];
  const region = character?.region ?? (suffixGen ? REGION_BY_GEN[suffixGen] : 'other');
  // Hisui is Legends: Arceus, a Gen 8 game, and has no -genN suffix of its own.
  const regionGen = Number(Object.entries(REGION_BY_GEN).find(([, r]) => r === region)?.[0] ?? (region === 'hisui' ? 8 : 0));
  return {
    id,
    base,
    known: Boolean(character || CLASS_NAMES[base]),
    name: character?.name ?? CLASS_NAMES[base] ?? titleCase(base),
    variant: variant ? (VARIANT_LABELS[variant] ?? titleCase(variant.replace(/(\d+)/, ' $1'))) : '',
    region,
    role: character?.role ?? 'generic',
    gen: suffixGen ?? (regionGen >= 1 && regionGen <= 9 ? regionGen : 0),
  };
}

async function backSprite(id) {
  const spec = BACK_SPRITES[id];
  if (!spec) return null;
  const sha = PRET[spec.repo];
  const url = `https://cdn.jsdelivr.net/gh/pret/${spec.repo}@${sha}/${spec.path}`;
  const { w, h } = pngSize(await get(url));
  return {
    u: url,
    f: `https://raw.githubusercontent.com/pret/${spec.repo}/${sha}/${spec.path}`,
    w,
    h: spec.frameH ?? h,
    ...(spec.palette ? { p: spec.palette } : {}),
  };
}

async function main() {
  console.log('==> trainers');
  const index = (await get(TRAINER_INDEX)).toString('utf8');
  const files = [...new Set([...index.matchAll(/href="([a-z0-9-]+\.png)"/g)].map((m) => m[1]))];
  console.log(`     ${files.length} trainer sprites listed by Showdown`);
  if (files.length < 500) throw new Error('suspiciously few trainers — did the index format change?');

  const regionOrder = Object.fromEntries(REGIONS.map(([k], i) => [k, i]));
  const roleOrder = Object.fromEntries(ROLES.map(([k], i) => [k, i]));
  const trainers = files.map(describe).sort((a, b) =>
    regionOrder[a.region] - regionOrder[b.region] ||
    roleOrder[a.role] - roleOrder[b.role] ||
    a.name.localeCompare(b.name) ||
    a.variant.localeCompare(b.variant),
  );

  const unnamed = [...new Set(trainers.filter((t) => !t.known).map((t) => t.base))];
  console.log(`     ${unnamed.length} names shown as-is (add them to trainers.config.mjs to tidy):`);
  console.log(`       ${unnamed.join(' ')}`);

  const missingBacks = Object.keys(BACK_SPRITES).filter((id) => !files.includes(`${id}.png`));
  if (missingBacks.length) console.log(`     ! back sprites with no Showdown front: ${missingBacks.join(', ')}`);

  const out = [];
  for (const t of trainers) {
    const back = await backSprite(t.id);
    out.push({
      i: t.id,
      n: t.name,
      ...(t.variant ? { v: t.variant } : {}),
      r: t.region,
      o: t.role,
      g: t.gen,
      ...(back ? { b: back } : {}),
    });
  }
  console.log(`     ${out.filter((t) => t.b).length} with a real back sprite`);

  write('trainers.json', {
    frontBase: TRAINER_INDEX,
    pret: PRET,
    regions: REGIONS.map(([id, label]) => ({ id, label })),
    roles: ROLES.map(([id, label]) => ({ id, label })),
    trainers: out,
  });

  console.log('==> battle backgrounds');
  const backgrounds = [];
  for (const [id, label, gen, crop, stage] of BACKGROUNDS) {
    const file = `play.pokemonshowdown.com/fx/bg-${id}.png`;
    const url = `https://cdn.jsdelivr.net/gh/smogon/pokemon-showdown-client@${SHOWDOWN_CLIENT_SHA}/${file}`;
    const { w, h } = pngSize(await get(url));
    backgrounds.push({
      id,
      label,
      gen,
      url,
      fallbackUrl: `https://raw.githubusercontent.com/smogon/pokemon-showdown-client/${SHOWDOWN_CLIENT_SHA}/${file}`,
      w,
      h,
      ...(crop ? { crop } : {}),
      ...(stage ? { foe: stage.foe, player: stage.player } : {}),
    });
    console.log(`     ${id.padEnd(16)} ${w}x${h}`);
  }

  console.log('==> scenery');
  const PR = `https://cdn.jsdelivr.net/gh/pagefaultgames/pokerogue-assets@${POKEROGUE_SHA}/images/arenas`;
  const PR_RAW = `https://raw.githubusercontent.com/pagefaultgames/pokerogue-assets/${POKEROGUE_SHA}/images/arenas`;
  for (const [id, label] of SCENERY) {
    const { w, h } = pngSize(await get(`${PR}/${id}_bg.png`));
    backgrounds.push({
      id: `scenery-${id.replace(/_/g, '-')}`,
      label,
      gen: 0,
      kind: 'scenery',
      url: `${PR}/${id}_bg.png`,
      fallbackUrl: `${PR_RAW}/${id}_bg.png`,
      w,
      h,
    });
    console.log(`     ${id.padEnd(18)} ${w}x${h}`);
  }
  write('backgrounds.json', { sha: SHOWDOWN_CLIENT_SHA, pokerogueSha: POKEROGUE_SHA, backgrounds });
}

function write(name, data) {
  const p = path.join(ASSETS, name);
  fs.writeFileSync(p, JSON.stringify(data));
  console.log(`     wrote ${path.relative(ROOT, p)} (${(fs.statSync(p).size / 1024).toFixed(1)} KB)`);
}

main().catch((err) => {
  console.error(err);
  process.exit(1);
});
