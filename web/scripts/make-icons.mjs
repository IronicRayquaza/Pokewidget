#!/usr/bin/env node
/**
 * Renders the desktop app's icon from the Android launcher icon, so both apps wear the same
 * face and there is no second copy of the artwork to keep in step.
 *
 * The Android icon is an adaptive icon: a vector foreground on a flat background colour. This
 * reads both out of `app/src/main/res`, rebuilds them as an SVG, renders that in Chrome at
 * 1024×1024, and hands the PNG to `tauri icon`, which writes every size and format the
 * installers need.
 *
 * Usage: node scripts/make-icons.mjs   (then commit web/src-tauri/icons)
 */
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { execFileSync } from 'node:child_process';
import puppeteer from 'puppeteer-core';

const WEB = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const RES = path.resolve(WEB, '../app/src/main/res');
const ICONS = path.join(WEB, 'src-tauri/icons');
const CHROME = process.env.CHROME_PATH ?? 'C:/Program Files/Google/Chrome/Application/chrome.exe';

const vector = fs.readFileSync(path.join(RES, 'drawable/ic_launcher_foreground.xml'), 'utf8');
const colors = fs.readFileSync(path.join(RES, 'values/colors.xml'), 'utf8');

const background = colors.match(/name="ic_launcher_background">\s*(#[0-9a-fA-F]+)/)?.[1] ?? '#101318';
const viewport = Number(vector.match(/android:viewportWidth="([\d.]+)"/)?.[1] ?? 108);
const paths = [...vector.matchAll(/<path\b[\s\S]*?\/>/g)].map((match) => {
  const tag = match[0];
  return {
    fill: tag.match(/android:fillColor="(#[0-9a-fA-F]+)"/)?.[1] ?? '#000000',
    d: tag.match(/android:pathData="([^"]+)"/)?.[1] ?? '',
  };
});

if (paths.length === 0) throw new Error('no paths found in the launcher vector');

// Android's adaptive icon crops to the middle 72 of its 108 grid; drawing the whole grid keeps
// the Poké Ball at the size the launcher shows it, on a square icon that is not masked.
const svg = `<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 ${viewport} ${viewport}" width="1024" height="1024">
  <rect width="${viewport}" height="${viewport}" fill="${background}" rx="${viewport * 0.14}" />
  ${paths.map((p) => `<path fill="${p.fill}" d="${p.d}" />`).join('\n  ')}
</svg>`;

fs.mkdirSync(ICONS, { recursive: true });
const source = path.join(ICONS, 'icon.png');

const browser = await puppeteer.launch({ executablePath: CHROME, headless: 'new', args: ['--disable-gpu'] });
try {
  const page = await browser.newPage();
  await page.setViewport({ width: 1024, height: 1024, deviceScaleFactor: 1 });
  await page.setContent(`<body style="margin:0">${svg}</body>`, { waitUntil: 'load' });
  await page.screenshot({ path: source, omitBackground: true });
} finally {
  await browser.close();
}

console.log(`rendered ${path.relative(WEB, source)} from the Android launcher icon`);
execFileSync('npx', ['tauri', 'icon', source, '--output', ICONS], { cwd: WEB, stdio: 'inherit', shell: true });
