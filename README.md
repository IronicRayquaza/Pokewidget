# PokéWidget

Animated Pokémon sprites as Android home-screen widgets. Pick any Pokémon from any
game's sprite set, choose whether it sits on a background, whether tapping plays its cry,
and how large it renders.

<!-- SCREENSHOT SLOT — images to come. -->

Android 8.0 and up. Free, no ads, no accounts, no tracking of any kind.

## Get it

**[Download the latest release](https://github.com/IronicRayquaza/Pokewidget/releases/latest)**
&nbsp;·&nbsp; [the page for people, not developers](https://ironicrayquaza.github.io/Pokewidget/)
&nbsp;·&nbsp; [what changed in each version](CHANGELOG.md)

It is not on the Play Store, so Android checks twice that you meant it. Both warnings are
normal:

1. Open the downloaded APK. Android asks whether your browser may install apps — say yes, once.
2. Play Protect says it does not recognise the app. It says that about everything that did not
   come from the Play Store. Tap **Install anyway** (behind **More details** if it is hidden).
3. Long-press your home screen, tap **Widgets**, find PokéWidget, drag one out.

A sideloaded app cannot update itself, so new versions are announced in the Discord, along with
install help and somewhere to report what breaks. <!-- DISCORD INVITE — its own code, separate
from the app's and the landing page's, so Discord's per-invite counter says where people came
from. -->

## Not affiliated with anyone

Pokémon and all related art are trademarks of Nintendo, Creatures Inc. and GAME FREAK inc. This
is an unofficial fan project with no affiliation, made for fun and given away free.

**No Pokémon assets are bundled with the app or stored in this repository.** Sprites and cries
are fetched at runtime from the community PokéAPI mirrors and veekun's archive, each pinned to
a fixed revision.

## Building it

`./gradlew :app:assembleDebug` and `./gradlew :app:testDebugUnitTest` work on a fresh clone with
nothing else set up.

`assembleRelease` additionally needs a `keystore.properties` in the repo root, which is
deliberately not committed — see [`keystore.properties.example`](keystore.properties.example).
Without it the release APK comes out unsigned rather than quietly signed with a debug key.

---

## How the animation actually works

This is the interesting part, and it is why the app is written in Kotlin rather than
React Native.

A home-screen widget is not a view you own. It is a `RemoteViews` — a serialised view
tree that the **launcher** inflates in **its** process. You cannot run code in it. No
`ObjectAnimator`, no `ValueAnimator`, no custom views, no `ConstraintLayout`. Jetpack
Glance compiles down to `RemoteViews` and inherits every one of those restrictions.

Exactly one animation primitive survives: **`ViewFlipper`**. You hand the launcher a stack
of pre-rendered frames and a flip interval, and it cycles them itself:

```kotlin
views.removeAllViews(R.id.widget_flipper)
for (index in plan.sourceIndices) {
    val child = RemoteViews(packageName, R.layout.widget_frame)
    child.setImageViewBitmap(R.id.widget_frame_image, bitmaps.getValue(index))
    views.addView(R.id.widget_flipper, child)
}
views.setInt(R.id.widget_flipper, "setFlipInterval", plan.frameIntervalMs)
```

That is the whole animation engine. Once the frames are handed over **nothing of ours runs
again** — no alarms, no services, no wakelocks, and `updatePeriodMillis` is `0`. The
launcher even stops the flipping on its own when the widget scrolls off screen.

Two things about this are easy to get wrong, and both cost real debugging here:

- **`setAutoStart(boolean)` is not remotable.** `setFlipInterval(int)` carries a
  `@RemotableViewMethod` annotation; `setAutoStart` does not. Pushing it through
  `RemoteViews` throws `ActionException: ViewFlipper can't use method with RemoteViews`.
  Auto-start has to be declared as `android:autoStart` in the layout XML.
- **The initial layout must not be the flipper layout.** An auto-starting `ViewFlipper`
  with zero children re-posts `showNext()` every `flipInterval` inside the launcher's
  process, burning CPU for a widget that is not showing anything yet. There is a separate
  `widget_initial.xml` placeholder for that reason.

### The memory ceiling

The system rejects any widget update whose total bitmap memory exceeds
`screenWidth * screenHeight * 4 * 1.5` bytes, and it does so by **throwing**, which takes
the launcher down with it. That is **5.3 MB on a 720p phone**.

The sprites blow straight through it. Measured from the pinned source:

| Sprite | Size | Frames | Delays | All frames at 1:1 |
|---|---|---|---|---|
| Showdown Rayquaza | 142×153 | **95** | 30 ms | **7.9 MB** |
| B/W animated Rayquaza | 110×98 | 74 | 60 & 120 ms | 3.0 MB |
| Crystal Bulbasaur | 56×56 | 14 | **10 ms – 990 ms** | 0.2 MB |

`ViewFlipper` also has a *single* interval, so those variable per-frame delays have to be
resampled onto a uniform grid no matter what.

[`FramePlanner`](app/src/main/java/com/pokewidgets/app/sprite/FramePlanner.kt) handles
both problems. It is pure arithmetic on frame metadata — no `Bitmap`, no `Context` — so
the rules that keep the launcher alive are covered by ordinary JVM unit tests:

1. **Resample** the timeline at evenly spaced instants across exactly one loop, so the
   loop closes with no drift.
2. **Collapse identical frames.** Idle loops repeat a lot of artwork; B/W Rayquaza is 74
   frames but only **28 unique**, Crystal Bulbasaur 14 but only **6**.
3. **Fit the budget** by spending whichever resource is cheapest to lose. Above 4× upscale
   it shrinks the sprite — the difference between 8× and 6× pixel art is invisible, the
   difference between 12 fps and 8 fps is not. Below that it defends the size and drops
   frame rate instead.

Two mechanisms make the budget go far:

- **Bitmap dedupe.** `RemoteViews.BitmapCache` keys on object identity, so handing the
  *same* `Bitmap` instance to several frames costs one bitmap. A 990 ms hold that becomes
  a dozen uniform steps is charged once. This is verified against the framework's own
  `estimateMemoryUsage()` in [`WidgetPipelineTest`](app/src/androidTest/java/com/pokewidgets/app/WidgetPipelineTest.kt).
- **Nearest-neighbour integer upscaling.** `Bitmap.createScaledBitmap(..., filter = false)`
  to an exact multiple, with `scaleType="center"` so the `ImageView` never resamples.
  Bilinear filtering is what turns 8-bit art to mush.

The renderer still wraps `updateAppWidget` in a `try/catch` that re-plans against a halved
budget. The planner should never overshoot — but a launcher crash is not a thing to bet
someone's home screen on.

### Still sprites get a generated idle

Ruby/Sapphire, FireRed/LeafGreen and everything from Gen 6 on exist only as still PNGs.
Rather than sit dead on the home screen they get a generated idle — breathe, bob, sway or
hover, chosen per widget in `IdleAnimator`.

It is nearly free, because the cost is one bitmap per distinct *shape*, not per step:
translation is expressed as `setViewPadding` and `RemoteViews.BitmapCache` dedupes by
object identity, so a ten-step sway is one bitmap and a six-step breath is three. The
breath conserves volume — what the sprite loses in height it gains in width — and is
anchored at its feet, so it reads as weight rather than as the creature shrinking.

---

## Sprites

Sprite art is immutable, so everything is cached forever: [`PokeAPI/sprites`][sprites] is
pinned to one commit, and veekun's dump is a finished archive of shipped games.
**32 sprite sets, 1,345 Pokémon and alternate forms.** Eight are genuinely animated:

| Set | Hardware | Source | Coverage |
|---|---|---|---|
| **Showdown** | Fan-made, Gen 5 style | PokeAPI | 1,283 sprites — every Pokémon through Gen 9 |
| **Black / White** | Nintendo DS | PokeAPI | 872 — the original in-game battle sprites |
| **Emerald** | Game Boy Advance | veekun | 385 + shiny — the real Gen 3 battle animation |
| **Diamond / Pearl** | Nintendo DS | veekun | 493 — the two-frame in-game idle |
| **Platinum** | Nintendo DS | veekun | 493 — the two-frame in-game idle |
| **HeartGold / SoulSilver** | Nintendo DS | veekun | 493 — the two-frame in-game idle |
| **Crystal** | Game Boy Color | PokeAPI | 250 — the first animated sprites in the series |
| **Box icons (Gen 5, animated)** | Nintendo DS | PokeAPI | 674 — the bobbing PC-box icons, as APNG |

PokeAPI has no copy of the animation for Gen 3 and Gen 4, which is why a second provider
exists. veekun hosts Emerald's real battle sequences as GIFs, and stores the second frame
of each Gen 4 idle in a parallel `frame2/` tree — so those three games are reassembled
from two PNGs each at render time (`SpriteSet.frameDirs`).

The remaining 24 sets cover Red/Blue through Scarlet/Violet, box icons, HOME renders and
official artwork, all with the generated idle.

Sprites and cries download on first use and are cached permanently, so a widget keeps
animating with no connection. Only the searchable catalog and ~1,100 box icons (631 KB)
ship in the APK, which keeps it around 10 MB. Cries come from [`PokeAPI/cries`][cries] in
both `legacy` (the harsher Game Boy-era cry) and `latest` flavours.

### Regenerating the catalog

```bash
node tools/build-catalog.mjs          # writes app/src/main/assets/
node tools/build-catalog.mjs --no-cache
```

It walks the sprite tree via the GitHub API (in per-generation chunks — the repo-wide
recursive call truncates at 62k entries), cross-references the PokeAPI CSV dumps for
names, types and generations, and packs the box icons into one blob. Set metadata that a
machine cannot infer — real game names, hardware, ordering — lives in
[`tools/sets.config.mjs`](tools/sets.config.mjs); any set found upstream but missing from
that table is reported and skipped, so new sprite sets are a visible, deliberate addition.

---

## Building

Requires **JDK 17** — not 18, which AGP 7.4.1 rejects. `gradle.properties` points
`org.gradle.java.home` at a specific Temurin 17 install; change or delete that line on
another machine.

```bash
./gradlew assembleDebug
./gradlew testDebugUnitTest        # planner, idle styles, sprite-set resolution
./gradlew connectedDebugAndroidTest # end-to-end, needs a device and a connection
```

AGP 7.4.1 · Gradle 7.6.3 · Kotlin 2.1.0 · compileSdk 34 · minSdk 26.

### Handing a build to a tester

`assembleDebug` produces `app/build/outputs/apk/debug/app-debug.apk`, already signed with
the debug key and installable as-is. It carries the `.debug` application id, so it sits
alongside any other build of the app rather than replacing it.

Release builds are **not** set up: there is no `signingConfigs` block, so `assembleRelease`
emits an unsigned APK that will not install. `isMinifyEnabled` is on but has never been
exercised, and the riskiest part is silent rather than loud — `WidgetConfigStore` persists
enum *names*, and reads them back through a `runCatching { … } ?: fallback`, so an R8 rename
would not crash, it would quietly reset every widget to its defaults. Shake that out before
shipping a minified build.

### Testing

`FramePlannerTest` runs the planner over every combination of the three measured
worst-case sprites × three screen sizes × four widget sizes × six frame rates and asserts
the budget is never exceeded — 216 plans per run.

`WidgetPipelineTest` runs the real thing on a device: fetch, decode, plan, build the
`RemoteViews`, inflate it, and check the figure the system itself would check. It is what
caught the `setAutoStart` bug.

---

## A note on WorkManager

Widget renders originally ran through WorkManager. Do not put them back.

WorkManager toggles its own broadcast-receiver components with
`PackageManager.setComponentEnabledSetting`. Every toggle fires `ACTION_PACKAGE_CHANGED`
for the package, and `AppWidgetServiceImpl` responds to a package change by
re-broadcasting `APPWIDGET_UPDATE` to every widget that package owns. That update enqueued
more work, which toggled the components again. A single widget on the home screen
re-rendered **once per second, forever**.

A render is a few hundred milliseconds of decode-and-scale with no need to survive reboots
or wait on constraints, so it runs as a plain coroutine started from the receiver's
`goAsync()` window instead.

---

## Legal

Pokémon and all related sprites, cries and names are trademarks of Nintendo, Creatures
Inc. and GAME FREAK inc. This is an unofficial fan project with no affiliation, and it
bundles none of that material — sprites and cries are fetched at runtime from public
community mirrors. Intended for personal use; distributing it through an app store would
invite a takedown.

The pixel typeface is [Press Start 2P](https://fonts.google.com/specimen/Press+Start+2P),
SIL Open Font License (`app/src/main/res/raw/ofl_press_start_2p.txt`).

[sprites]: https://github.com/PokeAPI/sprites
[cries]: https://github.com/PokeAPI/cries
