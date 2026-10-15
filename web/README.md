# PokéWidget for desktops and browsers

The same widgets, outside Android, and they behave like widgets rather than windows:

- **In a browser, the tab is the home screen.** Widgets sit straight on the page at the spot they
  were dragged to, with nothing behind them unless their Background setting asks for it. Drag to
  move, the corner grip resizes, and editing slides a sheet in beside the widget.
- **On a desktop, widgets sit on the wallpaper, behind your apps.** Each is a frameless,
  transparent window with no taskbar button (Windows, macOS and Linux via Tauri). The app's own
  window is only for setting them up; close it and they stay, with a tray icon to bring it back.

```bash
npm install
npm run dev          # http://localhost:5178
npm test             # the ported rules, checked against the shipped catalogs
npm run build        # writes the browser build into ../docs/app
npm run tauri dev    # the desktop app, against the dev server
npm run tauri build  # installers (needs an icon — see src-tauri/icons/README.md)
```

## One set of rules, two apps

The catalogs are *not* copied by hand: `npm run sync-data` (part of `dev` and `build`) copies
`catalog.json`, `sets.json`, `trainers.json` and `backgrounds.json` straight out of
`app/src/main/assets/`, where the generators in `tools/` put them. A Pokémon, a sprite set, a
trainer and a battlefield therefore mean exactly the same thing on a phone and on a desktop.

The rules that decide what to draw are ports of the Kotlin, kept deliberately close so the two
cannot drift:

| Web | Android |
|---|---|
| `src/core/catalog.ts` | `catalog/SpriteSet.kt`, `SpriteKey.kt`, `TrainerCatalog.kt` |
| `src/core/scene.ts` | `widget/SceneLayout.kt`, `FramePlanner.displayScale` |
| `src/core/trainerArt.ts` | `sprite/TrainerArt.kt` |
| `src/core/audio.ts` | `widget/CryPlayer.kt` |
| `src/core/tap.ts` | `widget/PokemonWidgetProvider.runTap`, `catalog/SurprisePicker.kt` |
| `src/core/formRules.ts` | `catalog/FormRules.kt` |
| `src/core/placeQuery.ts`, `weather.ts` | `data/PlaceQuery.kt`, `WorldClock.kt`, `WeatherSource.kt` |

`src/core/*.test.ts` checks the ports against the real catalogs, the way the Kotlin tests do.

## What is deliberately different

- **No GIF decoder.** Android needs one because `RemoteViews` can only flip between still
  bitmaps, and Glide's decoder drew some sprites twice (the doubled Fidough). A browser
  animates a GIF correctly by itself, so the web app simply shows the image.
- **No memory budget.** The Android planner exists because a widget's bitmaps live in the
  launcher's process under a hard ceiling. A desktop has no such ceiling, so sprites are always
  drawn at the size the settings ask for.
- **No opaque-bounds crop.** Sizes are measured from the sprite's own canvas rather than its
  opaque pixels, because most sprite hosts do not allow reading pixels back.
- **"Get excited" hops instead of speeding up.** A browser has no way to change a GIF's frame
  rate, so the sprite does three small hops (none at all under reduced motion) with its cry.
- **"Use my location" is named by its coordinates.** There is no free reverse geocoder without
  a key, so a located place reads "Near 37.55°, −77.46°" rather than a city name. Search by name
  works everywhere and gives a proper label.

## Test builds for Windows, macOS and Linux

Nothing is released from here. To get installers for testers, run the **desktop** workflow by
hand (Actions → desktop → Run workflow, or `gh workflow run desktop.yml --ref <branch>`). Each
run uploads three artifacts — a Windows `.exe` installer, a universal macOS `.dmg` and a Linux
AppImage — kept for 14 days and downloadable from the run page by anyone signed in to GitHub
with access to the repo. No tag, release or draft release is created.

### For Mac testers

The `.dmg` is a universal build: the same file runs on Apple Silicon (M1 and later) and on Intel
Macs, on macOS 10.15 or newer.

It is not signed by Apple, so macOS will refuse it the first time — usually saying the app "is
damaged and can't be opened" or that Apple "could not verify" it. That is Gatekeeper reacting
to any unsigned download, not a fault in the file. After dragging PokeWidget into Applications,
either:

- open Terminal and run `xattr -cr /Applications/PokeWidget.app`, then open it normally; or
- try to open it once, then go to System Settings → Privacy & Security and click
  **Open Anyway** next to the message about PokeWidget.

Only needed once per installed version.

"Use my location" asks for permission the first time it is clicked. If macOS never asks, or it
fails, search for the city by name instead — that path needs no permission at all.

Removing the warning for good means signing and notarising the app: an Apple Developer account
(US$99 a year), a Developer ID certificate, and four repository secrets (`APPLE_CERTIFICATE`,
`APPLE_CERTIFICATE_PASSWORD`, `APPLE_ID` / `APPLE_PASSWORD` or an API key, and
`APPLE_TEAM_ID`) that Tauri reads during `tauri build`. Not set up yet.

## Hosts and what each allows

| Host | Used for | Notes |
|---|---|---|
| `cdn.jsdelivr.net` | Pokémon sprites, cries, trainer back sprites, backgrounds | Sends CORS headers, so pixels and audio can be read |
| `play.pokemonshowdown.com` | Trainer front sprites | No CORS: only ever displayed, never read |
| `veekun.com` | Emerald and Gen 4 animation | No CORS: displayed only |
| `wsrv.nl` | Any of the above, as a last resort | An image proxy, only used when a file's own host fails |

That is why trainer *backs* can be sliced and recoloured in a canvas but fronts cannot — and
they need no processing anyway.

Some networks cannot reach every host. A firewall that inspects HTTPS (a Fortinet on a school
or office network, for one) re-signs play.pokemonshowdown.com and veekun.com with a
certificate the computer does not trust, so every trainer front and the Gen 3–4 veekun sets
fail while jsDelivr works. `src/core/imageSource.ts` therefore gives every image the wsrv.nl
proxy as a last resort, and once the proxy has worked for a host that failed, tries it first
for that host for the rest of the session.

## Known limits

- **Safari plays no Ogg**, so cries are silent there. The app says so rather than looking broken.
- **Cries need a click first.** Browsers refuse to start audio before the page is interacted
  with, which suits a widget you click anyway.
- **Widgets live in this browser profile.** They are kept in `localStorage`, so they do not
  follow you to another browser or machine, and every tab of the app shows the same home screen.
- **A desktop widget's whole window catches the mouse**, including the see-through space around
  the sprite, so desktop icons under that space cannot be clicked. Keep widgets clear of icons,
  or size them snugly.
- **Widgets on top of everything cover what is under their whole window**, see-through space
  included, so size a pinned widget snugly.
- **Always-on-bottom has only been checked on Windows.** macOS and Linux get the same window
  option, but how their window managers treat it has not been tried yet.
- **Transparent windows on macOS** need `macOSPrivateApi`, which the config turns on. That is
  fine for direct downloads but would block an App Store submission.
- **No autostart yet**: widgets come back when the app is started, not when the computer is.
