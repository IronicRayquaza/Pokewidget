/**
 * Generated idle movement for sprite sets that ship as still images.
 *
 * A port of `sprite/IdleAnimator.kt`, with the same steps and timings, so a still Emerald or
 * Scarlet/Violet sprite moves the same way on a desktop as on a phone. Most of the series is
 * still art — every Game Boy Advance set, all of Generation 4 and everything from Generation 6
 * on — so without this two thirds of the catalogue would sit dead. These are the idle motions
 * the games use in their own menus: a breath, a bob, a sway, a hover.
 *
 * The loop steps from held pose to held pose rather than tweening, as the Android widget has
 * to: on pixel art a smooth tween reads as the image being resampled, a stepped one as the
 * creature moving.
 */

/** One step of an idle loop. Scales in thousandths, offsets in the sprite's own pixels. */
export interface IdleFrame {
  scaleXPermille: number;
  scaleYPermille: number;
  /** Horizontal offset in source pixels; multiplied by the sprite's upscale when drawn. */
  dxSource: number;
  /** Vertical offset in source pixels. Negative is up. */
  dySource: number;
}

export const NATURAL = 1000;

export type IdleStyle = 'none' | 'bob' | 'breathe' | 'sway' | 'hover' | 'settle' | 'auto';

const frame = (scaleXPermille = NATURAL, scaleYPermille = NATURAL, dxSource = 0, dySource = 0): IdleFrame => ({
  scaleXPermille,
  scaleYPermille,
  dxSource,
  dySource,
});

/** Name, description and step length, each loop running about a second. */
export const IDLE_STYLES: Record<IdleStyle, { label: string; description: string; intervalMs: number }> = {
  none: { label: 'Still', description: 'No movement at all', intervalMs: 1000 },
  bob: { label: 'Bob', description: 'A gentle rise and fall, like the party screen', intervalMs: 260 },
  breathe: { label: 'Breathe', description: 'Squashes and stretches as if breathing', intervalMs: 150 },
  sway: { label: 'Sway', description: 'Rocks slowly from side to side', intervalMs: 170 },
  hover: { label: 'Hover', description: 'Floats, for Pokémon that never touch the ground', intervalMs: 130 },
  settle: { label: 'Swell', description: 'Grows and sinks without changing shape — best for 3D renders', intervalMs: 170 },
  auto: { label: 'Auto', description: 'Matches the artwork — a breath for pixel art, a swell for 3D renders', intervalMs: 150 },
};

/** The order the choices are offered in, the same as the Android app's. */
export const IDLE_STYLE_ORDER: IdleStyle[] = ['none', 'bob', 'breathe', 'sway', 'hover', 'settle', 'auto'];

/** The style a still sprite gets when nobody has chosen one. */
export const DEFAULT_IDLE: IdleStyle = 'auto';

const FRAMES: Record<Exclude<IdleStyle, 'auto'>, IdleFrame[]> = {
  none: [frame()],
  // 0, -1, -2, -1: a smooth rise and fall with a slight pause at the bottom.
  bob: [frame(NATURAL, NATURAL, 0, 0), frame(NATURAL, NATURAL, 0, -1), frame(NATURAL, NATURAL, 0, -2), frame(NATURAL, NATURAL, 0, -1)],
  // Volume-conserving squash and stretch; the extremes are held for two steps each, which is
  // what gives the loop its ease without any interpolation.
  breathe: [frame(1000, 1000), frame(1015, 985), frame(1030, 970), frame(1030, 970), frame(1015, 985), frame(1000, 1000)],
  // A weight shift, long on purpose: a fast sway reads as a shiver.
  sway: [0, 1, 2, 2, 1, 0, -1, -2, -2, -1].map((dx) => frame(NATURAL, NATURAL, dx, 0)),
  // A lazy oval rather than a straight line, so it does not look like a bouncing ball.
  hover: [
    [0, 0],
    [1, -1],
    [1, -2],
    [0, -3],
    [-1, -3],
    [-1, -2],
    [0, -1],
  ].map(([dx, dy]) => frame(NATURAL, NATURAL, dx, dy)),
  // A uniform swell with a slight rise: bigger, never a different shape.
  settle: [
    frame(1000, 1000, 0, 0),
    frame(1010, 1010, 0, -1),
    frame(1020, 1020, 0, -2),
    frame(1020, 1020, 0, -2),
    frame(1010, 1010, 0, -1),
    frame(1000, 1000, 0, 0),
  ],
};

/**
 * Sets whose art is a rendered 3D model rather than a drawn sprite. Listed rather than derived
 * from the generation, because the Gen 7 and 8 icon sets are pixel art despite theirs.
 */
const RENDER_SETS = new Set([
  'versions_generation_vi_x_y',
  'versions_generation_vi_omegaruby_alphasapphire',
  'versions_generation_vii_ultra_sun_ultra_moon',
  'versions_generation_viii_brilliant_diamond_shining_pearl',
  'versions_generation_ix_scarlet_violet',
  'versions_generation_ix_champions',
  'other_home',
  'other_official_artwork',
]);

/** Sets whose games do animate, but only inside the cartridge, where no one has dumped it. */
export const ANIMATED_IN_ROM_ONLY = new Set(['versions_generation_iii_ruby_sapphire', 'versions_generation_iii_firered_leafgreen']);

export const isRendered = (setId: string): boolean => RENDER_SETS.has(setId);

/** Turns Auto into a real style for this set: a breath for pixel art, a swell for a render. */
export const resolveIdle = (style: IdleStyle, setId: string): Exclude<IdleStyle, 'auto'> =>
  style !== 'auto' ? style : isRendered(setId) ? 'settle' : 'breathe';

export const idleFrames = (style: IdleStyle, setId: string): IdleFrame[] => FRAMES[resolveIdle(style, setId)];

/** The widest and tallest the loop ever draws the sprite, so it can be sized not to clip. */
export function idleReach(frames: IdleFrame[]): { widest: number; tallest: number } {
  return {
    widest: Math.max(...frames.map((f) => f.scaleXPermille)) / NATURAL,
    tallest: Math.max(...frames.map((f) => f.scaleYPermille)) / NATURAL,
  };
}
