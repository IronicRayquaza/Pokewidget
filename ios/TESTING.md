# Testing the PokéWidget iPhone prototype

Thanks for helping! This is an early test build, not a release. It's here to answer one
question: **can a PokéWidget widget animate on the iPhone Home Screen?** It takes about 30
minutes to set up, then a day of just having it on your Home Screen.

You need:

- an iPhone on **iOS 17 or later**;
- a **Windows or Mac laptop** on the same Wi-Fi as the iPhone, plus a cable for the first
  install;
- a free **Apple ID**. Your usual one works; AltStore suggests a spare one if you run into
  problems;
- the file **`PokeWidgetProto-0.1.ipa`** you were sent.

The app is installed with **AltStore Classic**. It signs the app with your own Apple ID, so
it needs no App Store. The catch is that apps installed this way stop opening after 7 days
unless AltStore refreshes them. That's fine for this test.

AltStore's own guide, if anything below doesn't match what you see: https://faq.altstore.io

## 1. Install AltServer on the laptop

**Windows**
1. Install **iTunes** and **iCloud** from Apple's website, *not* the Microsoft Store versions.
   AltStore's Windows guide links to the right downloads.
2. Download AltServer from https://altstore.io, unzip it and run `Setup.exe`.
3. Search the Start menu for **AltServer**, right-click it, choose **Run as administrator**,
   and allow it through the firewall on private networks. Its icon shows up in the taskbar
   tray.

**Mac**
1. Download AltServer from https://altstore.io, move `AltServer.app` to Applications and open
   it. Its icon shows up in the menu bar.

Then plug the iPhone in, unlock it, and tap **Trust** when it asks about the computer.

## 2. Install AltStore on the iPhone

1. Click the AltServer icon → **Install AltStore** → pick your iPhone.
2. Sign in with your Apple ID. AltStore sends it only to Apple.
3. When AltServer says it's done, find AltStore on the iPhone. Restart the phone if the icon
   doesn't appear.
4. On the iPhone, go to **Settings → General → VPN & Device Management**, tap your Apple ID,
   then **Trust**.
5. Go to **Settings → Privacy & Security → Developer Mode**, turn it on, and let the phone
   restart.

## 3. Install PokéWidget Proto

1. Put `PokeWidgetProto-0.1.ipa` on the iPhone, for example in the Files app via AirDrop,
   iCloud Drive or a chat app's "Save to Files".
2. Open AltStore → **My Apps** → the **+** at the top → pick the `.ipa`.
3. Keep the laptop on, on the same Wi-Fi, with AltServer running, until it finishes.
4. Open **PokéWidget Proto** once. Wait for Pikachu to appear, so it has downloaded its
   sprites.

Free accounts can have only 3 sideloaded apps active, including AltStore. This app uses 2 of
the 10 weekly "App IDs" (the app and its widget).

## 4. What to test

### The app
- [ ] Pikachu animates in the preview. Try each sprite, and **Shiny**.
- [ ] Pick **Pikachu · Emerald** and try each **Idle movement**.
- [ ] **Save GIF to Photos**. Does it ask for permission, and does the GIF animate in Photos?
- [ ] *(Optional)* Install **Gifling** or **Video Widget** from the App Store, make a widget
      from that GIF, and see whether it animates on the Home Screen.

### The widgets

Long-press the Home Screen → **Edit** → **Add Widget** → search **PokéWidget Proto**. There
are three; add all of them. Long-press a widget → **Edit Widget** to change its options.

| Widget | What it should do |
|---|---|
| **1 · Baseline** | A still Pikachu. Tapping it switches to the shiny. |
| **2 · Frames by timer fonts** | **The important one.** Pikachu should actually animate. |
| **3 · Idle by rotation** | Expected to stand still, with the caption "Built without the rotation API". That's known; just confirm. |

For **widget 2**, try both sprites (Showdown, and Emerald + Breathe). Then check:

- [ ] Does it move at all? Is it smooth or jerky? Are the pixels sharp or blurry?
- [ ] Does it still move after locking and unlocking the phone?
- [ ] After swiping PokéWidget Proto away in the app switcher?
- [ ] After leaving the phone alone for 30+ minutes?
- [ ] After restarting the phone?
- [ ] With **Background → None**: does it sit straight on your wallpaper, or is there a box
      behind it?
- [ ] After a day: anything odd in **Settings → Battery** for PokéWidget Proto?

A short screen recording of widget 2 on the Home Screen helps a lot.

## 5. Send back

Copy this, fill it in, and send it with any screenshots or recordings:

```
iPhone model:
iOS version (Settings → General → About):
Laptop (Windows/Mac):

Install: worked? any errors (exact text):

App preview animates:            yes / no
Idle movements work:             yes / no
GIF saved to Photos, animates:   yes / no
Gifling / Video Widget:          animates / doesn't / didn't try

Widget 1 shows Pikachu:          yes / no      tap → shiny: yes / no
Widget 2 animates:               yes / no      smooth / jerky   sharp / blurry
  after lock/unlock:             yes / no
  after swiping app away:        yes / no
  after 30+ min:                 yes / no
  after restart:                 yes / no
  Background None:               on wallpaper / box behind it
  battery after a day:
Widget 3 shows the caption:      yes / no

Anything else:
```

## Removing it

Delete PokéWidget Proto from the Home Screen like any app. To undo everything, delete
AltStore too, turn Developer Mode off, and uninstall AltServer from the laptop.
