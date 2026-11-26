# PokéWidget iOS prototype

**A prototype on branch `proto/ios` only. It is not part of any release, nothing here is built
by CI, and it is not merged into `main`.**

It answers one question before anyone commits to a full iPhone app: **can a PokéWidget widget
animate on the real iPhone Home Screen?** Apple's supported widget APIs can't loop an
animation, so the prototype tries the workarounds that App Store apps such as Gifling and
Video Widget rely on, side by side. It also exports GIFs, which works whatever the answer is.

If it works, the idea is to distribute a full app through SideStore/AltStore: each user's own
free Apple ID signs it, with no App Store review, the same way the Mac `.dmg` skips signing.
The catch is a re-sign every 7 days.

## The three widgets and the app

| # | Widget | How it moves | Status |
|---|---|---|---|
| 1 | **Baseline** | Doesn't: a still frame. Tapping swaps to the shiny via an App Intent. Apple's supported API only. | to try on a phone |
| 2 | **Frames by timer fonts** | Real frames. Timer text keeps ticking inside a widget on its own; drawn in generated fonts whose "digits" are frames, 20 stacked timers flip at 10 fps. Bryce Bostwick's [WidgetAnimation](https://github.com/brycebostwick/WidgetAnimation) technique (MIT). | to try on a phone |
| 3 | **Idle by rotation** | Bob, Sway or Hover for a still sprite. Three nested rotations from SwiftUI's private `_clockHandRotationEffect` cancel into a straight or oval glide. Breathe and Swell can't be done this way, since they change the scale. | **blocked**: the iOS 27.2 SDK (Xcode 27.2) no longer declares the API to Swift, so it's built without it and stands still |

Each widget has a **Background** option, *System* or *None*, to see whether a widget can sit
straight on the wallpaper.

**The app** previews the three test sprites and exports any of them as a looping GIF, with
the idle movement rendered in for the still one. *Save GIF to Photos* puts it where Gifling
or Video Widget can pick it up; *Share GIF…* sends it anywhere else.

The test sprites are Pikachu in Showdown (animated), Black/White (animated) and Emerald
(still). They come from the same pinned PokeAPI commit as the other apps, downloaded on first
use; nothing is bundled.

## Already checked on a Mac (no Xcode needed)

- `Shared/Idle.swift` matches `web/src/core/idle.ts` on every style, for both a pixel-art and
  a 3D-render set: 42 checks, 0 failures. The fixture is `Tests/idle-fixture.json`.
- GIF export, run on this Mac with the same `Shared/` code:
  - Showdown: 33 frames, 1.32 s.
  - Black/White: 58 frames, 6 s.
  - Emerald idle loops: Breathe 0.90 s, Bob 1.04 s, Sway 1.70 s, Hover 0.91 s.
  - All upscaled with square pixels, transparent, and looping forever.
- The frame fonts work in CoreText, the text engine iOS shares. Timer strings map to the right
  frame glyph, the mask font alternates between full and blank, and the colour SVG glyphs draw
  crisp and transparent.
- The app and the widgets type-check as iOS code against the Mac Catalyst SDK. The exception is
  the private rotation call, which only exists in the iOS SDK.

## Checked with Xcode 27.2 (iOS 27.2 SDK, iPhone 17 simulator)

- The app and the widget build for a real iPhone (`generic/platform=iOS`, unsigned). The widget
  carries all 41 fonts, lists them in `UIAppFonts`, and ships `frame-fonts.json`.
- `IdleTests` passes in the simulator.
- The app runs in the simulator: it downloads the Showdown Pikachu, plays it crisp over the
  checkerboard, and exports a 480 px, 33-frame, 1.32 s GIF that loops forever with transparency.
- `_clockHandRotationEffect` is still in WidgetKit's binary but is no longer in its
  `.swiftinterface`, so Swift can't call it. Calling the raw symbol would be too fragile, so
  `PRIVATE_ROTATION` is off and widget 3 says "Built without the rotation API".
- Widgets can't be placed on the simulator's Home Screen from the command line, so the widgets
  themselves are still for the phone.

## Building it

One-time setup:

1. Install **Xcode** from the Mac App Store, run `sudo xcode-select -s /Applications/Xcode.app`,
   and open it once.
2. In Xcode → Settings → Accounts, add your Apple ID. A free account is fine: it gives a
   "Personal Team", and its builds stop opening after 7 days.
3. On the iPhone: Settings → Privacy & Security → **Developer Mode** on. Connect it by cable
   the first time.
4. Run `brew install xcodegen`.

Then, from the repo root:

```bash
# Frame fonts for widget 2, made from the sprites (git-ignored, never committed)
python3 -m venv ios/tools/.venv
ios/tools/.venv/bin/pip install -r ios/tools/requirements.txt
ios/tools/.venv/bin/python ios/tools/make-frame-fonts.py

# The Xcode project, then open it
cd ios && xcodegen generate && open PokeWidgetProto.xcodeproj
```

In Xcode:
1. Select both the **PokeWidgetProto** and **PokeWidgetProtoWidget** targets. Under Signing &
   Capabilities, pick your Personal Team. If Xcode says the bundle ID is taken, change
   `com.pokewidgets.proto` to something of your own.
2. Choose your iPhone and press **Run**.
3. The first time, iOS asks you to trust the developer: Settings → General → VPN & Device
   Management.

Add the widgets: long-press the Home Screen → **Edit** → **Add Widget** → PokéWidget Proto.
Long-press a placed widget → **Edit Widget** to change its sprite, movement or background.

`PRIVATE_ROTATION` is off in `project.yml` because the iOS 27.2 SDK hides
`_clockHandRotationEffect`. With an older Xcode whose SDK still declares it, turn it back on
there and regenerate.

Tests: `xcodebuild test -scheme PokeWidgetProto -destination 'platform=iOS Simulator,name=iPhone 17'`.
The simulator runtime isn't part of the Xcode download: Xcode → Settings → Components, or
`xcodebuild -downloadPlatform iOS`.
If idle.ts changes, regenerate the fixture with
`node --experimental-strip-types ios/tools/dump-idle.mjs > ios/Tests/idle-fixture.json`.

## What to look for on the phone

The simulator can behave differently from a real phone, so the phone decides. For each widget:

- Does it move at all? How smooth is it, and are the pixels crisp?
- Does it keep going after:
  - locking and unlocking;
  - swiping the app away;
  - 30 minutes untouched;
  - a restart?
- With *Background: None*, does it sit on the wallpaper, or does iOS draw something behind it?
- How does battery look after a day with it on the Home Screen?

Also: does an exported GIF animate as a widget in Gifling or Video Widget?

The outcome is a go or no-go for a full SideStore app. Record it in the table above.

## Known limits

- Timer-font fonts are made at build time for two fixed sprites. A real app would have to
  generate fonts on the phone for any Pokémon and register them at runtime. That's untested.
- At 10 fps, Showdown's faster GIFs lose frames. The technique reportedly reaches 30 fps,
  at 3× the fonts and stacked timers.
- Apple calls the timer-font trick unsupported and it can break in any iOS release.
  `_clockHandRotationEffect` is private API. Both are fine without App Store review, but
  either could stop working after an update.
