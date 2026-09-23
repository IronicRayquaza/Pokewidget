#!/usr/bin/env node
/**
 * Drives the built app in a real Chrome: the home screen with two widgets on it, dragging,
 * resizing, the edit sheet, removing and undoing, and a desktop widget page on its own. A quick
 * way to see that the pieces still work together — the unit tests cover the rules, this covers
 * the wiring.
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
      page: { x: 24, y: 24, width: 420, height: 240 },
      config: {
        pokemonId: 2, setId: 'other_showdown', shiny: false, back: false, female: false, style: null,
        flipHorizontal: false, showBackground: true, backgroundColor: '#1b1f27', cornerRadius: 20,
        backgroundId: 'gen3-cave', trainerId: 'red-gen3', trainerPose: 'back', trainerSide: 'left',
        trainerFlip: false, scene: 'battle', fill: 'fit', cryEnabled: true, legacyCry: true,
      },
    },
    {
      id: 2,
      page: { x: 480, y: 24, width: 240, height: 240 },
      config: {
        pokemonId: 389, setId: 'other_showdown', shiny: true, back: false, female: false, style: null,
        flipHorizontal: true, showBackground: false, backgroundColor: '#1b1f27', cornerRadius: 20,
        backgroundId: null, trainerId: null, trainerPose: 'front', trainerSide: 'left',
        trainerFlip: false, scene: 'solo', fill: 'true-size', cryEnabled: true, legacyCry: true,
      },
    },
  ],
};

const TRANSPARENT = 'rgba(0, 0, 0, 0)';
const failures = [];
const check = (ok, what) => {
  if (!ok) failures.push(what);
  console.log(`${ok ? 'ok  ' : 'FAIL'} ${what}`);
};
const pause = (ms) => new Promise((r) => setTimeout(r, ms));

const browser = await puppeteer.launch({ executablePath: CHROME, headless: 'new', args: ['--disable-gpu'] });
const errors = [];
try {
  const page = await browser.newPage();
  page.on('pageerror', (e) => errors.push(String(e)));
  page.on('console', (m) => m.type() === 'error' && !m.text().includes('404') && errors.push(m.text()));
  page.on('requestfailed', (r) => errors.push(`request failed: ${r.url()}`));
  page.on('response', (r) => r.status() >= 400 && !r.url().endsWith('/favicon.ico') && errors.push(`HTTP ${r.status()} ${r.url()}`));
  await page.setViewport({ width: 1280, height: 900 });

  await page.goto(base, { waitUntil: 'networkidle2' });
  await page.evaluate((state) => localStorage.setItem('pokewidget.widgets.v1', JSON.stringify(state)), widget);
  await page.reload({ waitUntil: 'networkidle2' });
  await pause(2500);
  await page.screenshot({ path: `${out}/web-home.png` });

  const saved = () => page.evaluate(() => JSON.parse(localStorage.getItem('pokewidget.widgets.v1')));
  const placed = async (id) => (await saved()).widgets.find((w) => w.id === id)?.page;
  const box = (id) => page.$eval(`#widget-${id}`, (el) => el.getBoundingClientRect().toJSON());
  const background = (id) => page.$eval(`#widget-${id} .widget`, (el) => getComputedStyle(el).backgroundColor);
  const button = async (scope, label) => (await page.$$(`xpath/.//${scope}//button[contains(., "${label}")]`))[0];

  check((await page.$$('.placed')).length === 2, 'both widgets are on the home screen');
  check(!(await page.$('.sheet')), 'no edit sheet until one is asked for');
  check((await background(2)) === TRANSPARENT, 'a widget with no background setting draws nothing behind it');

  // Drag the solo widget 200px right and 150px down.
  let b = await box(2);
  await page.mouse.move(b.x + b.width / 2, b.y + b.height / 2);
  await page.mouse.down();
  for (let i = 1; i <= 10; i++) await page.mouse.move(b.x + b.width / 2 + 20 * i, b.y + b.height / 2 + 15 * i);
  await page.mouse.up();
  let rect = await placed(2);
  check(rect.x === 680 && rect.y === 174, `dragging moves the widget and saves it (${rect.x}, ${rect.y})`);
  check(!(await page.$('.sheet')), 'a drag does not open the editor');

  // Resize from the grip.
  b = await box(2);
  await page.mouse.move(b.x + b.width / 2, b.y + b.height / 2);
  await pause(250);
  const grip = await page.$eval('#widget-2 .grip', (el) => el.getBoundingClientRect().toJSON());
  await page.mouse.move(grip.x + grip.width / 2, grip.y + grip.height / 2);
  await page.mouse.down();
  for (let i = 1; i <= 6; i++) await page.mouse.move(grip.x + grip.width / 2 + 10 * i, grip.y + grip.height / 2 + 10 * i);
  await page.mouse.up();
  rect = await placed(2);
  check(rect.width === 300 && rect.height === 300, `the grip resizes it (${rect.width}×${rect.height})`);

  // Keyboard: an arrow moves it, Enter opens the sheet, Escape closes it.
  await page.focus('#widget-2');
  await page.keyboard.press('ArrowLeft');
  check((await placed(2)).x === 672, 'an arrow key moves the focused widget 8px');
  await page.keyboard.press('Enter');
  await pause(300);
  check(Boolean(await page.$('.sheet')), 'Enter opens the edit sheet');
  const title = await page.$eval('#sheet-title', (el) => el.textContent);
  check(/Torterra/.test(title ?? ''), `the sheet is for that widget (${title})`);
  await page.screenshot({ path: `${out}/web-home-editing.png` });
  await page.keyboard.press('Escape');
  await pause(100);
  check(!(await page.$('.sheet')), 'Escape closes it');
  check(await page.evaluate(() => document.activeElement?.id === 'widget-2'), 'and focus goes back to the widget');

  // Background → Colour draws a box behind it; None takes it away again.
  await page.keyboard.press('Enter');
  await pause(300);
  await (await button('aside', 'Colour')).click();
  const boxed = await background(2);
  check(boxed !== TRANSPARENT, `choosing Colour gives it a background (${boxed})`);
  await (await button('aside', 'None')).click();
  check((await background(2)) === TRANSPARENT, 'None takes it away again');
  await page.keyboard.press('Escape');

  // Remove, then Undo.
  await page.focus('#widget-1');
  await page.keyboard.press('Delete');
  await pause(200);
  check((await page.$$('.placed')).length === 1, 'Delete removes the focused widget');
  await (await button('div[contains(@class,"toast")]', 'Undo')).click();
  await pause(200);
  check((await page.$$('.placed')).length === 2, 'Undo puts it back');
  check((await placed(1))?.x === 24, 'where it was');

  // A phone-sized tab keeps every widget on screen.
  await page.setViewport({ width: 375, height: 800 });
  await pause(300);
  for (const id of [1, 2]) {
    b = await box(id);
    check(b.x >= 0 && b.x + b.width <= 375, `widget ${id} stays on a 375px screen`);
  }
  await page.screenshot({ path: `${out}/web-home-narrow.png`, fullPage: true });

  // A new widget lands clear of the others, with its editor open.
  await page.setViewport({ width: 1280, height: 900 });
  await pause(300);
  await (await button('header', 'New widget')).click();
  await pause(500);
  const all = (await saved()).widgets;
  check(all.length === 3 && Boolean(all[2].page), 'a new widget gets a spot of its own');
  check(Boolean(await page.$('.sheet')), 'and opens its editor');

  const widgetPage = await browser.newPage();
  widgetPage.on('pageerror', (e) => errors.push(String(e)));
  await widgetPage.setViewport({ width: 420, height: 240 });
  await widgetPage.goto(`${base}/widget.html?id=1`, { waitUntil: 'networkidle2' });
  await pause(2500);
  await widgetPage.screenshot({ path: `${out}/web-widget.png`, omitBackground: true });
  const pageBackgrounds = await widgetPage.evaluate(() =>
    [document.documentElement, document.body].map((el) => getComputedStyle(el).backgroundColor),
  );
  check(pageBackgrounds.every((c) => c === TRANSPARENT), 'a desktop widget page paints no background of its own');
} finally {
  await browser.close();
}

if (errors.length) {
  console.error('page errors:');
  for (const e of errors) console.error(`  ${e}`);
}
if (errors.length || failures.length) process.exit(1);
console.log(`screenshots in ${fs.realpathSync(out)}`);
