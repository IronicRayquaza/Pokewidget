import type { Trainer, TrainerBack, TrainerIndex } from './catalog';
import { trainerFrontUrl } from './catalog';
import { loadFirst, withProxy } from './imageSource';

/**
 * Turns a trainer's downloaded image into something drawable, the same way
 * `sprite/TrainerArt.kt` does:
 *
 * - Showdown's fronts are used as they are.
 * - Backs come from game graphics: Emerald and FireRed store the throw animation as a strip
 *   of frames, so only the first standing pose is kept, and Game Boy sprites are four shades
 *   of grey whose colours live elsewhere in the ROM, so they are recoloured and their white
 *   background cleared.
 *
 * Fronts come from play.pokemonshowdown.com, which sends no CORS header, so their pixels
 * cannot be read — they need no processing, and are handed back as a plain URL. The backs
 * are on jsDelivr, which does, so those can go through a canvas. Either can come through the
 * image proxy when its own host is out of reach (see `imageSource.ts`); the proxy allows
 * cross-origin reads too, so a proxied back is still recoloured.
 */

export interface TrainerArt {
  /** A URL or data URL to draw. */
  src: string;
  width: number;
  height: number;
  /** True when this is the front standing in for a back sprite no game ever drew. */
  standIn: boolean;
}

const cache = new Map<string, TrainerArt>();

const parseColor = (hex: string): [number, number, number] => {
  const value = Number.parseInt(hex.replace('#', ''), 16);
  return [(value >> 16) & 0xff, (value >> 8) & 0xff, value & 0xff];
};

/** Maps each grey to its palette entry: white, light, dark, black. */
function recolorShades(data: Uint8ClampedArray, palette: string[]): void {
  const colors = palette.map(parseColor);
  const last = colors.length - 1;
  for (let i = 0; i < data.length; i += 4) {
    if (data[i + 3] === 0) continue;
    const grey = data[i]!;
    const shade = Math.min(Math.max(Math.round(((255 - grey) * last) / 255), 0), last);
    const [r, g, b] = colors[shade]!;
    data[i] = r;
    data[i + 1] = g;
    data[i + 2] = b;
  }
}

/** Clears the background colour where it touches the edge, so the whites of eyes survive. */
function clearEdgeBackground(data: Uint8ClampedArray, w: number, h: number, background: string): void {
  const [br, bg, bb] = parseColor(background);
  const matches = (i: number) => data[i] === br && data[i + 1] === bg && data[i + 2] === bb && data[i + 3] !== 0;
  const stack: number[] = [];
  const push = (x: number, y: number) => {
    const i = (y * w + x) * 4;
    if (matches(i)) {
      data[i + 3] = 0;
      stack.push(x, y);
    }
  };
  for (let x = 0; x < w; x++) {
    push(x, 0);
    push(x, h - 1);
  }
  for (let y = 0; y < h; y++) {
    push(0, y);
    push(w - 1, y);
  }
  while (stack.length) {
    const y = stack.pop()!;
    const x = stack.pop()!;
    if (x > 0) push(x - 1, y);
    if (x < w - 1) push(x + 1, y);
    if (y > 0) push(x, y - 1);
    if (y < h - 1) push(x, y + 1);
  }
}

async function processBack(back: TrainerBack): Promise<TrainerArt> {
  const { image } = await loadFirst(withProxy([back.u]), true);
  const w = Math.min(back.w, image.naturalWidth);
  const h = Math.min(back.h, image.naturalHeight);
  const canvas = document.createElement('canvas');
  canvas.width = w;
  canvas.height = h;
  const ctx = canvas.getContext('2d')!;
  ctx.imageSmoothingEnabled = false;
  // Only the first frame of the throw animation: the trainer standing.
  ctx.drawImage(image, 0, 0, w, h, 0, 0, w, h);

  if (back.p?.length) {
    const pixels = ctx.getImageData(0, 0, w, h);
    recolorShades(pixels.data, back.p);
    clearEdgeBackground(pixels.data, w, h, back.p[0]!);
    ctx.putImageData(pixels, 0, 0);
  }
  return { src: canvas.toDataURL('image/png'), width: w, height: h, standIn: false };
}

/**
 * The trainer as they should be drawn. A back that no game ever drew comes back as the
 * front with `standIn` set, so the caller can turn it round and say so.
 */
export async function trainerArt(index: TrainerIndex, trainer: Trainer, back: boolean): Promise<TrainerArt> {
  const key = `${trainer.i}/${back}`;
  const cached = cache.get(key);
  if (cached) return cached;

  let art: TrainerArt;
  if (back && trainer.b) {
    try {
      art = await processBack(trainer.b);
    } catch {
      art = await front(index, trainer, true);
    }
  } else {
    art = await front(index, trainer, back);
  }
  cache.set(key, art);
  return art;
}

async function front(index: TrainerIndex, trainer: Trainer, standIn: boolean): Promise<TrainerArt> {
  const { src, image } = await loadFirst(withProxy([trainerFrontUrl(index, trainer)]));
  return { src, width: image.naturalWidth, height: image.naturalHeight, standIn };
}
