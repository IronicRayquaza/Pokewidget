import type { BattleBackground } from './catalog';

/**
 * Where the Pokémon and its trainer stand, and how big a sprite is drawn.
 *
 * A port of `widget/SceneLayout.kt` and `FramePlanner.displayScale`, with the same numbers,
 * so a widget laid out here matches the Android one. What is deliberately *not* ported is
 * the frame planner's memory budget: that exists because a home-screen widget's bitmaps live
 * in the launcher's process under a hard ceiling. A desktop has no such ceiling, and the
 * browser animates a GIF by itself, so sprites here are always drawn at full size.
 */

export interface Box {
  left: number;
  top: number;
  width: number;
  height: number;
}

export interface Point {
  x: number;
  y: number;
}

export interface Stage {
  foe: Point;
  player: Point;
}

export type Scene = 'solo' | 'side-by-side' | 'battle';
export type TrainerSide = 'left' | 'right';

export interface Layout {
  pokemon: Box;
  trainer: Box | null;
  trainerInFront: boolean;
  anchorBottom: boolean;
}

export const OPEN_STAGE: Stage = { foe: { x: 0.72, y: 0.6 }, player: { x: 0.25, y: 1 } };

export const right = (box: Box): number => box.left + box.width;
export const bottom = (box: Box): number => box.top + box.height;

export function layout(
  scene: Scene,
  side: TrainerSide,
  width: number,
  height: number,
  stage: Stage = OPEN_STAGE,
): Layout {
  const onLeft = side === 'left';
  const mirror = (box: Box): Box => (onLeft ? box : { ...box, left: width - right(box) });

  if (scene === 'solo') {
    return {
      pokemon: { left: 0, top: 0, width, height },
      trainer: null,
      trainerInFront: false,
      anchorBottom: false,
    };
  }

  if (scene === 'side-by-side') {
    const trainerW = Math.round(width * 0.42);
    const trainerTop = Math.round(height * 0.08);
    return {
      pokemon: mirror({ left: trainerW, top: 0, width: width - trainerW, height }),
      trainer: mirror({ left: 0, top: trainerTop, width: trainerW, height: height - trainerTop }),
      trainerInFront: false,
      anchorBottom: true,
    };
  }

  // The opening of a battle: the trainer from behind in the foreground, the Pokémon on the
  // far platform. Feet sit a little below the platform's centre, the way the games plant a
  // sprite in its shadow rather than balance it on the rim.
  const footX = stage.foe.x * width;
  const footY = (stage.foe.y + 0.06) * height;
  const halfWidth = Math.min(Math.min(footX, width - footX), width * 0.3);
  const floor = Math.min(height, footY);
  const tall = Math.min(floor, height * 0.7);
  const pokemon: Box = {
    left: Math.round(footX - halfWidth),
    top: Math.round(floor - tall),
    width: Math.max(1, Math.round(halfWidth * 2)),
    height: Math.max(1, Math.round(tall)),
  };

  const trainerTop = Math.round(height * 0.36);
  const trainerW = Math.round(Math.min(width * 0.6, height - trainerTop));
  const centre = Math.min(Math.max(stage.player.x * width, trainerW / 2), width - trainerW / 2);
  const trainer: Box = {
    left: Math.round(centre - trainerW / 2),
    top: trainerTop,
    width: trainerW,
    height: height - trainerTop,
  };

  return { pokemon: mirror(pokemon), trainer: mirror(trainer), trainerInFront: true, anchorBottom: true };
}

/** The scenery inside a background image; the Gen 3 and 4 ones are whole battle screens. */
export const sceneryBox = (background: BattleBackground): Box =>
  background.crop?.length === 4
    ? { left: background.crop[0]!, top: background.crop[1]!, width: background.crop[2]!, height: background.crop[3]! }
    : { left: 0, top: 0, width: background.w, height: background.h };

/** The largest source rectangle with the box's shape, centred. */
export function coverCrop(srcW: number, srcH: number, boxW: number, boxH: number): Box {
  if (srcW <= 0 || srcH <= 0 || boxW <= 0 || boxH <= 0) {
    return { left: 0, top: 0, width: Math.max(srcW, 0), height: Math.max(srcH, 0) };
  }
  if (srcW / srcH > boxW / boxH) {
    const w = Math.min(Math.max(Math.round(srcH * (boxW / boxH)), 1), srcW);
    return { left: Math.round((srcW - w) / 2), top: 0, width: w, height: srcH };
  }
  const h = Math.min(Math.max(Math.round(srcW / (boxW / boxH)), 1), srcH);
  return { left: 0, top: Math.round((srcH - h) / 2), width: srcW, height: h };
}

/**
 * Which part of a battle background shows, and where its platforms land. Positioned so the
 * far platform stays in view — a plain centre crop loses it entirely in a tall widget.
 */
export function battleFrame(background: BattleBackground, boxW: number, boxH: number): { crop: Box; stage: Stage } {
  const scenery = sceneryBox(background);
  const foe: Point = background.foe?.length === 2
    ? { x: background.foe[0]!, y: background.foe[1]! }
    : OPEN_STAGE.foe;
  const player: Point = background.player?.length === 2
    ? { x: background.player[0]!, y: background.player[1]! }
    : OPEN_STAGE.player;

  const cover = coverCrop(scenery.width, scenery.height, boxW, boxH);
  const clamp = (v: number, lo: number, hi: number) => Math.min(Math.max(v, lo), hi);
  const left = clamp(Math.round(foe.x * scenery.width - cover.width * 0.74), 0, scenery.width - cover.width);
  const top = clamp(Math.round(foe.y * scenery.height - cover.height * 0.55), 0, scenery.height - cover.height);

  const inCrop = (p: Point): Point => ({
    x: clamp((p.x * scenery.width - left) / cover.width, 0.08, 0.92),
    y: clamp((p.y * scenery.height - top) / cover.height, 0.2, 1),
  });

  return {
    crop: { left: scenery.left + left, top: scenery.top + top, width: cover.width, height: cover.height },
    stage: { foe: inCrop(foe), player: inCrop(player) },
  };
}

/** Where an image lands in a box: as large as fits, on the floor or centred. */
export function fit(imageW: number, imageH: number, box: Box, anchorBottom: boolean): Box {
  if (imageW <= 0 || imageH <= 0) return box;
  const scale = Math.min(box.width / imageW, box.height / imageH);
  const w = Math.max(1, Math.round(imageW * scale));
  const h = Math.max(1, Math.round(imageH * scale));
  return {
    left: box.left + Math.round((box.width - w) / 2),
    top: anchorBottom ? bottom(box) - h : box.top + Math.round((box.height - h) / 2),
    width: w,
    height: h,
  };
}

/** Leaves a little air around a sprite that fills its box, so it never kisses an edge. */
export const FIT_MARGIN = 0.92;

export type FillMode = 'fit' | 'true-size' | 'x4' | 'x3' | 'x2' | 'x1';

export const FILL_MULTIPLE: Record<FillMode, number | null> = {
  fit: null,
  'true-size': null,
  x4: 4,
  x3: 3,
  x2: 2,
  x1: 1,
};

/**
 * Screen pixels per source pixel.
 *
 * @param referencePx "True size": the source size of a *large* Pokémon in this set, so every
 *   sprite is drawn at the scale that would make one of that size fill the box, and relative
 *   sizes between species survive. Wins over `multiple` when both are given.
 */
export function displayScale(
  contentW: number,
  contentH: number,
  boxW: number,
  boxH: number,
  multiple: number | null = null,
  referencePx: number | null = null,
): number {
  if (boxW <= 0 || boxH <= 0 || contentW <= 0 || contentH <= 0) return 1;
  const fitScale = Math.min(boxW / contentW, boxH / contentH) * FIT_MARGIN;
  if (referencePx && referencePx > 0) {
    return Math.min(fitScale, (Math.min(boxW, boxH) * FIT_MARGIN) / referencePx);
  }
  if (multiple) return Math.min(multiple, fitScale);
  return fitScale;
}
