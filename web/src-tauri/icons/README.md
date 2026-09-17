# App icon slot

The desktop bundle needs an icon here before `npm run tauri build` can package it:

- `icon.png` — 1024×1024, square, transparent background
- Windows and macOS installers also want `icon.ico` and `icon.icns`

`npx tauri icon path/to/your-1024.png` generates all of them from one PNG.

Left empty on purpose: the artwork is yours to choose. Development (`npm run tauri dev`)
runs without it.
