# Changelog

What changed, written for someone using the app rather than reading the diff.

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
- **Shiny is easy to find.** The Shiny switch now sits right under the preview and is always
  there. If a sprite set has no shiny of your Pokémon, tapping it tells you why instead of the
  option silently disappearing. The Pokémon page has a Shiny switch too, and "Add to home
  screen" keeps it.

None of this changes a widget you have already placed, or a new one you don't customise:
trainers, backgrounds and mirroring are all off until you turn them on.

**Also new: PokéWidget on computers.** The same widgets now run in a browser and as a
desktop app, built from one shared set of rules and the very same Pokémon data. It is a
separate download and is not part of this Android release.

Trainer sprites and battle backgrounds come from [Pokémon Showdown](https://play.pokemonshowdown.com);
trainers seen from behind come from the [pret](https://github.com/pret) game decompilations.

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
