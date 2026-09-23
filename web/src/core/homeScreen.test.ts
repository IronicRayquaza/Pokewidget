import { describe, expect, it } from 'vitest';
import { clampRect, defaultSize, GAP, MIN_SIZE, nextFreeSpot, type Rect } from './homeScreen';

const overlap = (a: Rect, b: Rect) =>
  a.x < b.x + b.width && b.x < a.x + a.width && a.y < b.y + b.height && b.y < a.y + a.height;

describe('placing a new widget on the home screen', () => {
  it('puts the first one in the top left corner', () => {
    expect(nextFreeSpot([], defaultSize('solo'), 1200)).toEqual({ x: GAP, y: GAP, width: 240, height: 240 });
  });

  it('never covers a widget that is already there', () => {
    const placed: Rect[] = [];
    for (let i = 0; i < 12; i++) {
      const size = defaultSize(i % 3 === 0 ? 'battle' : 'solo');
      const spot = nextFreeSpot(placed, size, 1000);
      for (const other of placed) expect(overlap(spot, other)).toBe(false);
      expect(spot.x + spot.width).toBeLessThanOrEqual(1000);
      placed.push(spot);
    }
  });

  it('fills a gap in the first row before starting a second', () => {
    const left: Rect = { x: GAP, y: GAP, width: 240, height: 240 };
    const spot = nextFreeSpot([left], defaultSize('solo'), 1200);
    expect(spot.y).toBe(GAP);
    expect(spot.x).toBeGreaterThanOrEqual(left.x + left.width + GAP);
  });

  it('still finds a spot on a screen narrower than the widget', () => {
    const spot = nextFreeSpot([{ x: GAP, y: GAP, width: 420, height: 240 }], defaultSize('battle'), 320);
    expect(spot.y).toBeGreaterThanOrEqual(GAP + 240 + GAP);
  });
});

describe('keeping a widget on screen', () => {
  it('leaves a widget that fits exactly where it is', () => {
    const rect = { x: 100, y: 80, width: 240, height: 240 };
    expect(clampRect(rect, 1200, 800)).toEqual(rect);
  });

  it('pulls a widget back in when the tab narrows', () => {
    const drawn = clampRect({ x: 900, y: 600, width: 240, height: 240 }, 600, 700);
    expect(drawn).toEqual({ x: 360, y: 460, width: 240, height: 240 });
  });

  it('shrinks a widget wider than the whole tab, but not below the minimum', () => {
    expect(clampRect({ x: 0, y: 0, width: 420, height: 240 }, 320, 700).width).toBe(320);
    expect(clampRect({ x: 0, y: 0, width: 20, height: 20 }, 1200, 800)).toMatchObject({
      width: MIN_SIZE,
      height: MIN_SIZE,
    });
  });
});
