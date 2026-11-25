#!/usr/bin/env python3
"""Builds the frame fonts for the timer-font widget experiment.

The technique is Bryce Bostwick's WidgetAnimation (MIT): https://github.com/brycebostwick/WidgetAnimation
A widget can't run code, but `Text(date, style: .timer)` keeps ticking on its own. Drawn in a
font whose two-digit seconds ligature is an animation frame, a timer becomes a flip-book that
changes every second. `2 * fps` such timers, offset by one frame each and revealed in turn by
a blinking mask (another timer, in a font whose glyph is a full square on even seconds and
nothing on odd ones), give `fps` frames a second.

Font `j` holds frames j, j + N, j + 2N, ... where N = 2 * fps; its glyph for seconds `ss` is
frame (ss // 2 mod G) * N + j, with G = period / 2. So the loop lasts `period` seconds, which
must divide 60 and be even. The sprite's own loop is stretched (by a few percent at most) so a
whole number of loops fits the period, which keeps the seam invisible.

Each frame is an OpenType-SVG glyph: one path per colour, one rectangle per run of pixels,
so pixel art stays crisp at any size. Nothing here is committed: run it before building, and
it fetches the sprites from the same pinned PokeAPI commit the apps use.

    python3 -m venv ios/tools/.venv
    ios/tools/.venv/bin/pip install -r ios/tools/requirements.txt
    ios/tools/.venv/bin/python ios/tools/make-frame-fonts.py
"""
from __future__ import annotations

import io
import json
import math
import os
import sys
import urllib.request
from dataclasses import dataclass

from fontTools.feaLib.builder import addOpenTypeFeaturesFromString
from fontTools.fontBuilder import FontBuilder
from fontTools.pens.ttGlyphPen import TTGlyphPen
from fontTools.ttLib import newTable
from fontTools.ttLib.tables.S_V_G_ import SVGDocument
from PIL import Image, ImageSequence

HERE = os.path.dirname(os.path.abspath(__file__))
IOS = os.path.dirname(HERE)
OUT = os.path.join(IOS, "Widget", "Fonts")
CACHE = os.path.join(HERE, ".cache")
IDLE_FIXTURE = os.path.join(IOS, "Tests", "idle-fixture.json")

# Fixed, because the widget's Info.plist lists every font file by name (see project.yml).
FPS = 10
N = 2 * FPS
PERIODS = [2, 4, 6, 10, 12, 20, 30]  # even and dividing 60
MAX_STRETCH = 0.03

SPRITES_SHA = "c10459b9b0129eaca5c5d9b1cac65336debb1d08"  # app/src/main/assets/sets.json
DIGITS = ["zero", "one", "two", "three", "four", "five", "six", "seven", "eight", "nine"]
IDLE_MARGIN = 4  # source pixels of room around a still sprite, as in PixelArt.swift


@dataclass
class Subject:
    id: str
    label: str
    path: str
    set_id: str
    idle: str | None = None  # a style from idle.ts, for stills


SUBJECTS = [
    Subject("showdown25", "Pikachu · Showdown", "sprites/pokemon/other/showdown/25.gif", "other_showdown"),
    Subject("emerald25", "Pikachu · Emerald + Breathe", "sprites/pokemon/versions/generation-iii/emerald/25.png",
            "versions_generation_iii_emerald", idle="auto"),
]


def fetch(path: str) -> bytes:
    os.makedirs(CACHE, exist_ok=True)
    cached = os.path.join(CACHE, path.replace("/", "_"))
    if os.path.exists(cached):
        with open(cached, "rb") as f:
            return f.read()
    url = f"https://cdn.jsdelivr.net/gh/PokeAPI/sprites@{SPRITES_SHA}/{path}"
    with urllib.request.urlopen(url, timeout=30) as response:
        data = response.read()
    with open(cached, "wb") as f:
        f.write(data)
    return data


# --- Frames -------------------------------------------------------------------------------

@dataclass
class Pose:
    sx: float = 1.0
    sy: float = 1.0
    dx: int = 0
    dy: int = 0


@dataclass
class Timeline:
    """What is on screen when: images (all the same size) plus per-step image index and pose."""
    images: list[Image.Image]
    steps: list[tuple[int, Pose, float]]  # (image index, pose, seconds)
    margin: int  # source pixels of padding to leave around the art

    @property
    def duration(self) -> float:
        return sum(s for _, _, s in self.steps)

    def at(self, t: float) -> tuple[int, Pose]:
        t %= self.duration
        for image, pose, seconds in self.steps:
            if t < seconds:
                return image, pose
            t -= seconds
        return self.steps[-1][0], self.steps[-1][1]


def timeline(subject: Subject) -> Timeline:
    image = Image.open(io.BytesIO(fetch(subject.path)))
    if subject.idle is None:
        frames, steps = [], []
        for i, frame in enumerate(ImageSequence.Iterator(image)):
            frames.append(frame.convert("RGBA"))
            ms = frame.info.get("duration", 100) or 100
            steps.append((i, Pose(), (100 if ms < 20 else ms) / 1000))
        return Timeline(frames, steps, margin=0)
    with open(IDLE_FIXTURE) as f:
        entry = json.load(f)[subject.set_id][subject.idle]
    seconds = entry["intervalMs"] / 1000
    poses = [Pose(p["scaleXPermille"] / 1000, p["scaleYPermille"] / 1000, p["dxSource"], p["dySource"])
             for p in entry["frames"]]
    return Timeline([image.convert("RGBA")], [(0, pose, seconds) for pose in poses], margin=IDLE_MARGIN)


def choose_period(duration: float) -> tuple[int, float]:
    """The shortest period a whole number of loops fits within MAX_STRETCH, else the best one."""
    best = None
    for period in PERIODS:
        loops = max(1, round(period / duration))
        stretch = period / loops / duration
        if abs(stretch - 1) <= MAX_STRETCH:
            return period, stretch
        if best is None or abs(stretch - 1) < abs(best[1] - 1):
            best = (period, stretch)
    return best


# --- SVG glyphs -------------------------------------------------------------------------------

def runs_svg(image: Image.Image, unit: int, x0: int, y0: int) -> str:
    """One <path> per colour; each run of same-coloured pixels in a row is one rectangle."""
    width, height = image.size
    pixels = image.load()
    by_colour: dict[tuple, list[str]] = {}
    for y in range(height):
        x = 0
        while x < width:
            colour = pixels[x, y]
            if colour[3] == 0:
                x += 1
                continue
            start = x
            while x < width and pixels[x, y] == colour:
                x += 1
            w = (x - start) * unit
            by_colour.setdefault(colour, []).append(f"M{x0 + start * unit} {y0 + y * unit}h{w}v{unit}h-{w}z")
    parts = []
    for (r, g, b, a), rects in by_colour.items():
        opacity = "" if a == 255 else f' fill-opacity="{a / 255:.3f}"'
        parts.append(f'<path fill="#{r:02x}{g:02x}{b:02x}"{opacity} d="{"".join(rects)}"/>')
    return "".join(parts)


def frame_svgs(tl: Timeline, period: int, stretch: float) -> tuple[list[str], int, int]:
    """Every frame of the loop as glyph SVG (without the glyph id yet), plus em size and unit."""
    # Crop everything to the union of opaque pixels, so the glyph is as big as the art allows.
    box = None
    for image in tl.images:
        b = image.getbbox()
        if b:
            box = b if box is None else (min(box[0], b[0]), min(box[1], b[1]), max(box[2], b[2]), max(box[3], b[3]))
    images = [image.crop(box) for image in tl.images]
    art_w, art_h = images[0].size
    side = max(art_w, art_h) + 2 * tl.margin
    unit = max(1, round(1024 / side))
    em = side * unit
    x0 = (side - art_w) // 2 * unit
    y0 = (side - art_h - tl.margin) * unit  # sits on the bottom margin, like a sprite on the ground

    bodies = {image_index: runs_svg(image, unit, x0, y0) for image_index, image in enumerate(images)}
    cx = x0 + art_w * unit / 2
    bottom = y0 + art_h * unit
    svgs = []
    for k in range(period * FPS):
        t = (k / FPS) / stretch
        image_index, pose = tl.at(t)
        body = bodies[image_index]
        if (pose.sx, pose.sy, pose.dx, pose.dy) != (1.0, 1.0, 0, 0):
            # As WidgetView.tsx: translate by the offset, then scale about the bottom centre.
            body = (f'<g transform="translate({pose.dx * unit + cx:g} {pose.dy * unit + bottom:g}) '
                    f'scale({pose.sx:g} {pose.sy:g}) translate({-cx:g} {-bottom:g})">{body}</g>')
        svgs.append(f'<g transform="translate(0 -{em})">{body}</g>')
    return svgs, em, unit


# --- Fonts ----------------------------------------------------------------------------------

def build_font(path: str, name: str, em: int, ligatures: dict[str, str], extra: list[str],
               outlines: dict[str, object], svgs: dict[str, str]) -> None:
    order = [".notdef", "space", *DIGITS, "colon", *extra]
    fb = FontBuilder(em, isTTF=True)
    fb.setupGlyphOrder(order)
    cmap = {0x20: "space", 0xA0: "space", 0x3A: "colon"}
    cmap.update({0x30 + i: d for i, d in enumerate(DIGITS)})
    fb.setupCharacterMap(cmap)
    empty = TTGlyphPen(None).glyph()
    fb.setupGlyf({g: outlines.get(g, empty) for g in order})
    # Everything is one em wide, so the ligature (always the last glyph) sits exactly where the
    # widget's trailing alignment puts it.
    fb.setupHorizontalMetrics({g: (em, 0) for g in order})
    fb.setupHorizontalHeader(ascent=em, descent=0)
    fb.setupNameTable({"familyName": name, "styleName": "Regular", "psName": name, "fullName": name})
    fb.setupOS2(sTypoAscender=em, sTypoDescender=0, sTypoLineGap=0, usWinAscent=em, usWinDescent=0)
    fb.setupPost()
    rules = "".join(f"sub {pair} by {glyph};\n" for pair, glyph in ligatures.items())
    addOpenTypeFeaturesFromString(fb.font, f"feature liga {{\n{rules}}} liga;\n")
    if svgs:
        table = newTable("SVG ")
        table.docList = []
        for glyph, svg in svgs.items():
            gid = order.index(glyph)
            data = f'<svg xmlns="http://www.w3.org/2000/svg" id="glyph{gid}">{svg}</svg>'
            table.docList.append(SVGDocument(data, gid, gid, False))
        fb.font["SVG "] = table
    fb.save(path)


def seconds_pairs():
    for ss in range(60):
        yield f"{DIGITS[ss // 10]} {DIGITS[ss % 10]}", ss


def build_subject(subject: Subject) -> dict:
    tl = timeline(subject)
    period, stretch = choose_period(tl.duration)
    svgs, em, unit = frame_svgs(tl, period, stretch)
    glyphs_per_font = period // 2
    names = [f"f{g:02d}" for g in range(glyphs_per_font)]
    total = 0
    for j in range(N):
        name = f"PWFrames-{subject.id}-{j}"
        ligatures = {pair: names[(ss // 2) % glyphs_per_font] for pair, ss in seconds_pairs()}
        glyph_svgs = {names[g]: svgs[g * N + j] for g in range(glyphs_per_font)}
        file = os.path.join(OUT, f"{name}.ttf")
        build_font(file, name, em, ligatures, names, {}, glyph_svgs)
        total += os.path.getsize(file)
    print(f"{subject.id}: loop {tl.duration:.2f}s -> period {period}s (x{stretch:.3f}), "
          f"{len(svgs)} frames in {N} fonts, {total / 1024:.0f} KB")
    return {"id": subject.id, "label": subject.label, "periodSeconds": period, "fonts": N}


def build_blink() -> None:
    """Full square on even seconds, nothing on odd ones: the mask that reveals each frame."""
    pen = TTGlyphPen(None)
    em = 1000
    pen.moveTo((0, 0)); pen.lineTo((0, em)); pen.lineTo((em, em)); pen.lineTo((em, 0)); pen.closePath()
    ligatures = {pair: ("full" if ss % 2 == 0 else "blank") for pair, ss in seconds_pairs()}
    build_font(os.path.join(OUT, "PWBlink.ttf"), "PWBlink", em, ligatures, ["full", "blank"], {"full": pen.glyph()}, {})


def main() -> int:
    os.makedirs(OUT, exist_ok=True)
    build_blink()
    manifest = {"fps": FPS, "blinkFont": "PWBlink", "subjects": [build_subject(s) for s in SUBJECTS]}
    with open(os.path.join(OUT, "frame-fonts.json"), "w") as f:
        json.dump(manifest, f, indent=2)
    print("wrote", OUT)
    return 0


if __name__ == "__main__":
    sys.exit(main())
