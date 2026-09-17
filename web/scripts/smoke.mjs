#!/usr/bin/env node
/**
 * Drives the built app in a real Chrome and takes screenshots: the setup page with a widget,
 * and a widget window on its own. A quick way to see that the pieces still render — the unit
 * tests cover the rules, this covers the wiring.
 *
 * Usage: node scripts/smoke.mjs <base-url> <out-dir>
 */
import puppeteer from 'puppeteer-core';
import fs from 'node:fs';

const base = process.argv[2] ?? 'http://localhost:5178';
const out = process.argv[3] ?? '.';
const CHROME = process.env.CHROME_PATH ?? 'C:/Program Files/Google/Chrome/Application/chrome.exe';

const widget = {
  version: 1,
  nextId: 3,
  widgets: [
    {
      id: 1,
      config: {
        pokemonId: 2, setId: 'other_showdown', shiny: false, back: false, female: false, style: null,
        flipHorizontal: false, showBackground: true, backgroundColor: '#1b1f27', cornerRadius: 20,
        backgroundId: 'gen3-cave', trainerId: 'red-gen3', trainerPose: 'back', trainerSide: 'left',
        trainerFlip: false, scene: 'battle', fill: 'fit', cryEnabled: true, legacyCry: true,
      },
    },
    {
      id: 2,
      config: {
        pokemonId: 389, setId: 'other_showdown', shiny: true, back: false, female: false, style: null,
        flipHorizontal: true, showBackground: false, backgroundColor: '#1b1f27', cornerRadius: 20,
        backgroundId: null, trainerId: null, trainerPose: 'front', trainerSide: 'left',
        trainerFlip: false, scene: 'solo', fill: 'true-size', cryEnabled: true, legacyCry: true,
      },
    },
  ],
};

const browser = await puppeteer.launch({ executablePath: CHROME, headless: 'new', args: ['--disable-gpu'] });
const errors = [];
try {
  const page = await browser.newPage();
  page.on('pageerror', (e) => errors.push(String(e)));
  page.on('console', (m) => m.type() === 'error' && errors.push(m.text()));
  page.on('requestfailed', (r) => errors.push(`request failed: ${r.url()}`));
  page.on('response', (r) => r.status() >= 400 && errors.push(`HTTP ${r.status()} ${r.url()}`));
  await page.setViewport({ width: 1280, height: 1000 });

  await page.goto(base, { waitUntil: 'networkidle2' });
  await page.evaluate((state) => localStorage.setItem('pokewidget.widgets.v1', JSON.stringify(state)), widget);
  await page.reload({ waitUntil: 'networkidle2' });
  await new Promise((r) => setTimeout(r, 2500));
  await page.screenshot({ path: `${out}/web-setup.png` });

  const widgetPage = await browser.newPage();
  widgetPage.on('pageerror', (e) => errors.push(String(e)));
  await widgetPage.setViewport({ width: 420, height: 240 });
  await widgetPage.goto(`${base}/widget.html?id=1`, { waitUntil: 'networkidle2' });
  await new Promise((r) => setTimeout(r, 2500));
  await widgetPage.screenshot({ path: `${out}/web-widget.png`, omitBackground: true });

  await widgetPage.goto(`${base}/widget.html?id=2`, { waitUntil: 'networkidle2' });
  await new Promise((r) => setTimeout(r, 2500));
  await widgetPage.screenshot({ path: `${out}/web-widget-solo.png`, omitBackground: true });
} finally {
  await browser.close();
}

if (errors.length) {
  console.error('page errors:');
  for (const e of errors) console.error(`  ${e}`);
  process.exit(1);
}
console.log(`screenshots in ${fs.realpathSync(out)}`);
