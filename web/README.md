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
