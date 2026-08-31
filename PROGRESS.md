# Progress

Status snapshot for PokéWidget. See `README.md` for architecture and how-it-works detail.

## Done

**Core animation pipeline**
- [x] `FramePlanner` — resamples variable-delay GIF timing onto `ViewFlipper`'s uniform
      interval, fits the system's widget bitmap ceiling (`screenW*screenH*4*1.5`), spends
      "free" scale before frame rate. Covered by 13 JVM unit tests (216 planning
      permutations against measured worst-case sprites).
- [x] Pixel-hash frame dedupe (`BitmapOps.canonicalFrames`) — collapses pixel-identical
      frames onto one shared bitmap. Verified against the framework's own
      `RemoteViews.estimateMemoryUsage()` via reflection in an instrumented test.
- [x] GIF decoding via Glide's standalone `gifdecoder`, nearest-neighbour integer
      upscaling, transparent-bounds cropping.
- [x] Generated idle animation for still sprites (`IdleAnimator`: breathe, bob, sway,
      hover) — one bitmap per distinct shape rather than per step, translation via
      `setViewPadding`.
- [x] Crash-guard: `updateAppWidget` wrapped in try/catch, re-plans at a halved budget on
      `IllegalArgumentException`, falls back to a single still frame.

**Widget**
- [x] Three provider sizes (Small/Medium/Large) sharing one renderer, freely resizable,
      re-plans on `onAppWidgetOptionsChanged`.
- [x] Background plate (color/opacity/corner radius, or fully transparent).
- [x] Tap actions: play cry (legacy or modern), toggle shiny, flip front/back, speed-up
      burst, open app, none.
- [x] Cry playback respects ringer/silent mode, requests transient ducking audio focus,
      times out instead of holding the broadcast receiver open indefinitely.
- [x] `onDeleted` / `onRestored` (backup restore remaps widget ids) handled in
      `WidgetConfigStore`.
- [x] Placeholder `initialLayout` so an unconfigured/loading widget doesn't run an
      auto-starting `ViewFlipper` against zero children.

**Catalog & sprite delivery**
- [x] `tools/build-catalog.mjs` — walks `PokeAPI/sprites` (chunked, since the repo-wide
      recursive tree API truncates at 62k entries), cross-references PokeAPI CSV dumps,
      emits `catalog.json` / `sets.json` / packed offline icon blob.
- [x] 27 sprite sets classified and shipped; 3 animated (Showdown, Black/White, Crystal),
      24 static. Any upstream set not in `tools/sets.config.mjs` is reported and skipped
      rather than silently ignored.
- [x] Download-on-demand + permanent disk cache, pinned to one commit SHA of
      `PokeAPI/sprites` (never goes stale, so cache-forever is safe).
- [x] Cries from `PokeAPI/cries` (legacy + latest), same cache-forever policy.

**App UI**
- [x] Main screen: searchable/filterable grid of all 1,345 Pokémon+forms, offline box
      icons, "animated only" filter, generation filter.
- [x] Pokémon detail sheet: every available sprite set previewed live side-by-side
      (animated GIFs render inline via Coil), pin-to-home-screen shortcut.
- [x] Widget config screen: Pokémon picker, sprite-set picker (animated sets badged),
      shiny/back/female variant toggles (only shown when the selected set supports
      them), background controls, smoothness/fill/tap-action pickers, live GBA-styled
      preview panel.
- [x] Settings sheet: cache size, clear cache, credits/attribution.
- [x] Material 3 theme with pixel-font (Press Start 2P, SIL OFL) headings and
      type-colored chips; dynamic color on API 31+.
- [x] `requestPinAppWidget` flow from the detail sheet, with the chosen Pokémon/set
      carried through a short-lived pending-pin slot (the pin API itself has no payload
      and skips the configure activity).

**Build & verification**
- [x] AGP 8.10.1 / Gradle 8.11.1 / Kotlin 2.1.0, compileSdk 36, minSdk 26 — builds clean
      on JDK 17 from the command line (Android Studio 2022.1 on this machine can't open
      it; needs an update to use the IDE directly).
- [x] Debug build installs and runs on a Pixel 6 Pro API 30 emulator; widget verified
      animating, transparent background over wallpaper, tap-to-cry verified end to end.
- [x] Release (minified/R8) build verified: signs, installs, launches, browses the full
      catalog, places a working animated widget — confirms R8 didn't break
      kotlinx.serialization or reflection-based Coil/Glide paths.
- [x] Found and fixed two real bugs via on-device instrumented tests:
      `ViewFlipper.setAutoStart(boolean)` is not a remotable method (must be
      `android:autoStart` in XML, not pushed via `RemoteViews`); WorkManager caused a
      self-sustaining ~1/sec re-render loop via its own component-enable toggling
      triggering `PACKAGE_CHANGED` → `AppWidgetServiceImpl` re-broadcast (replaced with a
      plain coroutine off `goAsync()`).

## Not delivered / known gaps

- **Ruby/Sapphire and FireRed/LeafGreen are still static.** Emerald and all of Gen 4 now
  use their real in-game animation from veekun, but veekun has no `animated/` or
  `frame2/` tree for R/S or FR/LG, and no other public host carries them. Those two fall
  back to the generated idle.
- **Unown has no animated Emerald sprite.** veekun stores #201 per form (`201-a.gif`)
  rather than by id, and there is no reliable mapping from those suffixes back to a
  PokéAPI form id. It falls back to another set.
- **Gen 4 previews in the app are static.** The picker previews a single URL through Coil,
  which cannot stitch the two frames the way the widget renderer does. The widget itself
  animates correctly.
- **veekun has no CDN.** Its sets come from one origin with no mirror
  (`veekun/pokedex-media` holds only a README). Acceptable because each sprite is fetched
  once and cached forever, and a failed fetch degrades to the generated idle.
- ~~**Gen 5 animated icon set (APNG) skipped.**~~ Fixed. `ApngFrames` rebuilds each frame
  from the `fcTL`/`fdAT` chunks as a standalone PNG, so the set ships as
  "Box icons (Gen 5, animated)". `ImageDecoder` was not an option: API 28+ against a
  minSdk of 26, and it exposes no per-frame access for a `ViewFlipper`. The format is
  detected by sniffing `acTL`, not by extension — an APNG's extension is `.png`, and a
  missed detection is silent, because `BitmapFactory` reads the first frame quite happily.
- **No automated instrumented-test run in CI** — `connectedDebugAndroidTest` was run
  manually against a local emulator during development; there's no CI workflow wired up
  in this repo yet.
- **No app icon / branding beyond a placeholder Poké Ball vector** — functional adaptive
  icon exists (`ic_launcher_foreground.xml`), not custom-designed artwork.
- **No handling for launcher-specific pin-callback quirks** — `requestPinAppWidget`'s
  optional success callback isn't relied on (it didn't fire reliably in testing); the
  chosen-Pokémon hand-off instead happens the first time the widget actually renders,
  which works but means there's a brief default-Pikachu placeholder frame possible on
  launchers that render before the pending-pin pickup completes. Not observed in testing
  on the stock Pixel launcher, but not proven across other launchers.
- **Play Store distribution is out of scope on purpose** — sprites/cries are fetched at
  runtime from community mirrors of trademarked Nintendo/Creatures/GAME FREAK assets;
  this is fine for personal/sideloaded use but would not survive store review. No store
  listing assets, privacy policy, or signing/release-track setup exist.
- **No unit/instrumented tests for the UI layer** (Compose screens, ViewModels) — only
  the frame-planning core and the end-to-end widget pipeline are covered by automated
  tests. UI behavior was verified manually via emulator screenshots during this session.
