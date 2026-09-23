import type { Scene } from './scene';

/**
 * Where widgets sit on the browser tab's home screen.
 *
 * The tab is laid out like a phone's home screen: each widget has its own rectangle on the
 * page, and nothing else. These are the rules for that rectangle — how big a new widget
 * starts, where it goes so it covers nothing, and how it stays on screen when the tab is
 * narrower than the one it was placed in.
 */

/** A rectangle in CSS pixels, measured from the top left of the home screen. */
export interface Rect {
  x: number;
  y: number;
  width: number;
  height: number;
}

/** The smallest a widget can be resized to: still big enough to see and grab. */
export const MIN_SIZE = 96;

/** Space kept between widgets, and between a widget and the edge. */
export const GAP = 24;

/**
 * A new widget's size. A solo Pokémon is square; a scene with a trainer is wide, the same
 * 420×240 the setup preview has always used.
 */
export const defaultSize = (scene: Scene): { width: number; height: number } =>
  scene === 'solo' ? { width: 240, height: 240 } : { width: 420, height: 240 };

const clamp = (value: number, low: number, high: number) => Math.min(Math.max(value, low), high);

/**
 * `rect`, kept inside a `canvasWidth` × `canvasHeight` screen. Only the drawing uses this:
 * the stored rectangle is left alone, so a widget goes back where it was when the tab widens.
 */
export function clampRect(rect: Rect, canvasWidth: number, canvasHeight: number, min = MIN_SIZE): Rect {
  const width = Math.round(clamp(rect.width, min, Math.max(min, canvasWidth)));
  const height = Math.round(clamp(rect.height, min, Math.max(min, canvasHeight)));
  return {
    x: Math.round(clamp(rect.x, 0, Math.max(0, canvasWidth - width))),
    y: Math.round(clamp(rect.y, 0, Math.max(0, canvasHeight - height))),
    width,
    height,
  };
}

const overlaps = (a: Rect, b: Rect, gap: number) =>
  a.x < b.x + b.width + gap && b.x < a.x + a.width + gap && a.y < b.y + b.height + gap && b.y < a.y + a.height + gap;

/**
 * The first spot, reading left to right and top to bottom, where a `size` widget covers none
 * of `existing`. There is always one: below the lowest widget, if nowhere else.
 */
export function nextFreeSpot(
  existing: Rect[],
  size: { width: number; height: number },
  canvasWidth: number,
): Rect {
  const lowest = existing.reduce((bottom, r) => Math.max(bottom, r.y + r.height), 0);
  const rightmost = Math.max(GAP, canvasWidth - size.width - GAP);
  for (let y = GAP; y <= lowest + GAP; y += GAP) {
    for (let x = GAP; x <= rightmost; x += GAP) {
      const candidate = { x, y, ...size };
      if (!existing.some((r) => overlaps(candidate, r, GAP))) return candidate;
    }
  }
  return { x: GAP, y: lowest + GAP, ...size };
}
