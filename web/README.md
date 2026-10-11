# PokéWidget for desktops and browsers

The same widgets, outside Android: a page you set them up on, and a chromeless widget view
that runs either as a browser window or as a frameless, transparent, always-on-top window on
Windows, macOS and Linux.

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

That is why trainer *backs* can be sliced and recoloured in a canvas but fronts cannot — and
they need no processing anyway.

## Known limits

- **Safari plays no Ogg**, so cries are silent there. The app says so rather than looking broken.
- **Cries need a click first.** Browsers refuse to start audio before the page is interacted
  with, which suits a widget you click anyway.
- **Widgets live in this browser profile.** They are kept in `localStorage`, so they do not
  follow you to another browser or machine.
- **Transparent windows on macOS** need `macOSPrivateApi`, which the config turns on. That is
  fine for direct downloads but would block an App Store submission.
- **The desktop app has no tray icon or autostart yet**, and widget windows float above other
  windows rather than sitting on the desktop behind icons.
