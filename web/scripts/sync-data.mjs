#!/usr/bin/env node
/**
 * Copies the generated catalogs out of the Android app into the web app's public folder.
 *
 * They are generated once, by `tools/build-catalog.mjs` and `tools/build-trainers.mjs`, and
 * both apps read the same files — so a Pokémon, a sprite set or a trainer can never mean one
 * thing on a phone and another on a desktop. Run before dev and build; it is part of both.
 */
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const WEB = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const ASSETS = path.resolve(WEB, '../app/src/main/assets');
const OUT = path.join(WEB, 'public/data');

const FILES = ['catalog.json', 'sets.json', 'trainers.json', 'backgrounds.json'];

fs.mkdirSync(OUT, { recursive: true });
for (const name of FILES) {
  const from = path.join(ASSETS, name);
  if (!fs.existsSync(from)) {
    console.error(`missing ${path.relative(WEB, from)} — run the generators in tools/ first`);
    process.exit(1);
  }
  fs.copyFileSync(from, path.join(OUT, name));
  console.log(`  ${name.padEnd(18)} ${(fs.statSync(from).size / 1024).toFixed(0)} KB`);
}
