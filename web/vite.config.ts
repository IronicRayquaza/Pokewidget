import { defineConfig } from 'vite';
import preact from '@preact/preset-vite';
import { resolve } from 'node:path';

/**
 * Two pages, not one: the app people set widgets up in, and a chromeless widget view.
 * The widget page is what a Tauri window loads, and what the browser build shows when a
 * widget is opened on its own, so neither carries the other's chrome.
 *
 * `base` is relative so the same build works from GitHub Pages under /Pokewidget/app/ and
 * from a file:// URL inside the desktop app.
 */
export default defineConfig({
  base: './',
  plugins: [preact()],
  // `tauri dev` waits for the app on this exact port (devUrl in tauri.conf.json). On Vite's
  // default port the desktop window never opens, and only the browser version can be seen.
  server: { port: 5178, strictPort: true },
  // An empty inline config stops Vite walking up the drive looking for one: a stray
  // postcss.config.mjs in a parent folder would otherwise be applied to this app.
  css: { postcss: {} },
  build: {
    outDir: '../docs/app',
    emptyOutDir: true,
    rollupOptions: {
      input: {
        main: resolve(__dirname, 'index.html'),
        widget: resolve(__dirname, 'widget.html'),
      },
    },
  },
  test: {
    environment: 'node',
    include: ['src/**/*.test.ts'],
  },
});
