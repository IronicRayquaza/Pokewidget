# Changelog

What changed, written for someone using the app rather than reading the diff.

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
