# Changelog

What changed, written for someone using the app rather than reading the diff.

## 1.6 (in testing, not released)

**New**

- **Surprise me.** A new choice under "When tapped": each tap swaps the Pokémon on the widget
  for a random one and plays the newcomer's cry. It replaces the sprite — it never adds a
  second one — and the swap sticks. Pick from any Pokémon, or choose your own shortlist with
  "Only my picks". The next surprise is downloaded ahead of time, so the cry starts the moment
  you tap.
- **Use my location.** Settings → Weather can now take your location instead of a typed city.
  It asks for permission only when you tap the button, reads your position once, and keeps
  only the city.
- **The desktop app catches up.** Clicking a desktop widget can now do everything tapping one
  on a phone can — Surprise me included — and live forms work there too, with the city picker
  right in the widget's settings. (Desktop widgets hop when excited, since a computer can't
  speed up the animation the way a phone does.)

**Fixes**

- **Cities you couldn't find.** Searching "Richmond VA" found Richmond Vale in the Caribbean,
  and "Richmond Virginia" found nothing. City search now understands state, province and
  country names and abbreviations, with or without a comma.
- **Live forms used your clock, not the city's.** Pick a city where it's night while it's day
  where you are, and Lycanroc, Shaymin, Morpeko and Castform followed your time of day instead
  of the city's. They now follow the chosen city's own clock and sunrise and sunset, and
  Settings shows the time there so you can check.
- Widget setup shows which form a live form is in right now.

- **"Only my picks" holds ten.** Surprise me's shortlist stops at ten Pokémon, and says so.
- **Nothing waits for a download at the tap.** Every pick, every live form and the shiny or
  back sprite a tap switches to are downloaded ahead, cries included — a Pokémon with no
  Game Boy cry has its modern one ready before you tap.
- **No freeze on "Use my location".** Finding the city could leave the app not responding for
  a few seconds on some phones. If the app ever does freeze, Settings now keeps a report of
  it, like it does for a crash.
- **The weather card fits the city on one line** and says it updates itself about once an hour.
- **A short note under "Add to home screen"** that a new widget can take a few seconds to
  appear the first time, while its animation and cry download.

**Desktop and browser: widgets live where you'd expect them to**

<!-- screenshot slot: the browser home screen with a widget or two on it -->

- **In the browser, the tab is the home screen.** Widgets sit right on the page, wherever you
  drag them, with nothing drawn behind them unless you choose a background. Click one and it does
  its tap action; drag it to move it; pull the corner to resize it. Editing opens a panel beside it
  instead of a new tab.
- **On the PC, widgets sit on the desktop**, on your wallpaper and behind your open apps, like a
  widget on a phone's home screen. No window frame, no taskbar button, no background unless you
  pick one. Drag one by the Pokémon itself, pull its corner to resize it, and hover it for edit
  and remove.
- **On the desktop, or on top of everything — your choice, per widget.** Pin a widget from
  its hover controls (or in settings) and it floats above every window while you work; unpin
  it and it goes back down onto the wallpaper.
- **Still sprites move, as on your phone.** Emerald, FireRed/LeafGreen, Gen 4 and everything
  from Gen 6 on now get the same idle movement as the Android widget — Bob, Breathe, Sway,
  Hover, Swell, or Auto, which picks a breath for pixel art and a swell for 3D renders. Choose
  it under Idle movement; if your computer is set to reduce motion, they stay still.
- **Sprites load on restrictive networks.** Trainer sprites and the Emerald, Diamond/Pearl,
  Platinum and HeartGold/SoulSilver sets failed on networks whose firewall blocks their hosts;
  they now come through another route when that happens.
- **They stay put.** Widgets come back after a restart, at the size and place you left them.
- **A tray icon** keeps PokeWidget running with the settings window closed. Quit it from there.

Like before: none of this changes a widget you've already placed. Tapping still plays the cry
until you choose otherwise, and nothing asks for your location unless you tap the button.

## 1.5

Installs over 1.4 and keeps your widgets. Thanks to everyone who sent feedback after the first
release — nearly everything below came from you.

**Fixes**

- **Animated sprites really fill the widget now.** "Fill the widget" and "4×" were being quietly
  overruled by the widget's memory limit, so big animated Pokémon came out small — Torterra
  ended up smaller than Psyduck. The size you pick is now the size you get; on a tight memory
  budget the sprite is drawn a little softer instead of smaller.
- **New size option: True size.** Big Pokémon look big and small ones look small, instead of
  every Pokémon being stretched to fill the widget.
- **No more doubled sprites.** Some Showdown sprites, Fidough among them, showed two copies
  stacked on top of each other. The GIF decoder has been replaced with one that handles those
  frames correctly.
- **Cries play the moment you tap.** They are now kept ready to play instead of being loaded on
  every tap, a second tap no longer waits for the first cry to finish, and a cry that failed
  to download mid-write can no longer come out clipped.
- **Widgets are sized for the space they really have**, which some launchers report confusingly
  in portrait.

**New**

- **Mirror a sprite** so your Pokémon can face the other way. It's next to the preview, and
  also available as a tap action.
- **Trainers.** Pair your Pokémon with a trainer: gym leaders, Elite Four, champions, rivals,
  villains and ordinary trainer classes from every region. Side by side, or as a **battle
  scene** seen from behind. Where a game really drew the player from behind (Red, Leaf, Ethan,
  Kris, Brendan, May, Wally, Steven) that sprite is used; otherwise the front is shown turned
  around, and the app says so.
- **Battle backgrounds.** A library of battlefields from the games, as an alternative to a
  plain colour behind the sprite.
- **Scenery.** Somewhere for your Pokémon to actually stand: plains, forest, beach, cave,
  volcano, open sea, a back alley, space, and thirty or so more. Unlike a battlefield, there
  is no platform painted in, so a Pokémon of any size stands on the ground rather than
  hovering over a stage built for something else. Seven of them — Sunny Day, Sunset, Starry
  Night, Snowfield, Seaside, Autumn and Blossom — are **drawn by the app itself** and need
  nothing downloaded, so there is always something to pick even on a fresh install with no
  connection. Battlefields are now only offered for battle scenes, which is the only place
  they ever looked right.
- **Shiny is easy to find.** The Shiny switch now sits right under the preview and is always
  there. If a sprite set has no shiny of your Pokémon, tapping it tells you why instead of the
  option silently disappearing. The Pokémon page has a Shiny switch too, and "Add to home
  screen" keeps it.

None of this changes a widget you have already placed, or a new one you don't customise:
trainers, backgrounds and mirroring are all off until you turn them on.

**Also new: PokéWidget on computers.** The same widgets now run in a browser, built from one
shared set of rules and the very same Pokémon data — open it at
[ironicrayquaza.github.io/Pokewidget/app](https://ironicrayquaza.github.io/Pokewidget/app/),
with nothing to install. A desktop app built from that same code is still being tested and is
not part of this Android release.

Trainer sprites and battle backgrounds come from [Pokémon Showdown](https://play.pokemonshowdown.com);
trainers seen from behind come from the [pret](https://github.com/pret) game decompilations;
scenery comes from [PokéRogue](https://github.com/pagefaultgames/pokerogue-assets), whose art is
CC-BY-NC-SA 4.0 — credited here, and never sold.

## 1.4

**You have to uninstall the old version first.** This is the first build signed with a real
key rather than a throwaway debug one, which changes the app's identity as far as Android is
concerned — it will not install over the top. Any widgets you have set up will need adding
again. This is a one-time cost and the reason for paying it now: from here on, every update
installs over the last one and keeps everything.

- Signed properly, so future versions update in place.
- **Join the Discord** in Settings — new versions get announced there, since a sideloaded app
  cannot update itself.
- If the app has crashed, the crash report in Settings now has a **Send in Discord** button
  that copies the report and opens the server, instead of leaving it on your clipboard with
  nowhere obvious to put it.

## 1.3

- **Tapping a widget for its cry no longer piles up.** Tapping two or three times used to play
  nothing, then play all of them at once and hang the app until Android offered to close it.
  Now a tap cancels whatever was sounding and plays one cry — the one you just asked for.
- **A cry that has not downloaded yet no longer freezes the widget.** It plays from the cache
  if it is there, waits a moment if it is not, and downloads in the background so the next tap
  is instant.
- **Setting a city in Settings no longer closes the app**, and the city is actually saved.
- **Settings now shows the weather it is using** — "Rain, daytime — checked 12 minutes ago" —
  with a **Check now** button. Live forms like Castform were previously impossible to tell
  apart from broken, because nothing on screen said what the app thought the sky was doing.
- If the app does crash, Settings keeps the last crash report so it can be copied and sent.

## 1.2

- **Fixed the crash when tapping the search box.** A library was pinned to a version older
  than the one the text field needed, so the app died the moment the field took focus.

## 1.1

- Light Pokédex theme, and sprite sets moved onto their own page.
- Live forms and animated Generation 5 icons.
