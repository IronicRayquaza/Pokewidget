import { describe, expect, it } from 'vitest';
import { IDLE_STYLE_ORDER, idleFrames, idleReach, NATURAL, resolveIdle, type IdleFrame } from './idle';

const shapes = (frames: IdleFrame[]) => new Set(frames.map((f) => `${f.scaleXPermille}x${f.scaleYPermille}`)).size;

// The same promises IdleAnimatorTest.kt holds the Android widget to.
describe('idle movement for still sprites', () => {
  it('loops back to where it started, so it never visibly snaps once a cycle', () => {
    for (const style of IDLE_STYLE_ORDER) {
      const frames = idleFrames(style, 'versions_generation_iii_emerald');
      expect(frames.length).toBeGreaterThan(0);
      const first = frames[0]!;
      const last = frames[frames.length - 1]!;
      expect(Math.abs(last.dxSource - first.dxSource)).toBeLessThanOrEqual(1);
      expect(Math.abs(last.dySource - first.dySource)).toBeLessThanOrEqual(1);
      expect(Math.abs(last.scaleXPermille - first.scaleXPermille)).toBeLessThanOrEqual(15);
      expect(Math.abs(last.scaleYPermille - first.scaleYPermille)).toBeLessThanOrEqual(15);
    }
  });

  it('moves bob, sway and hover without ever reshaping the sprite', () => {
    for (const style of ['bob', 'sway', 'hover'] as const) {
      const frames = idleFrames(style, 'x');
      expect(shapes(frames)).toBe(1);
      expect(frames.length).toBeGreaterThan(1);
    }
  });

  it('breathes by conserving volume: what it loses in height it gains in width', () => {
    const frames = idleFrames('breathe', 'x');
    expect(shapes(frames)).toBeGreaterThan(1);
    for (const f of frames) expect(f.scaleXPermille - NATURAL).toBe(NATURAL - f.scaleYPermille);
  });

  it('swells without distorting', () => {
    for (const f of idleFrames('settle', 'x')) expect(f.scaleXPermille).toBe(f.scaleYPermille);
  });

  it('picks a breath for pixel art and a swell for a 3D render when left on Auto', () => {
    expect(resolveIdle('auto', 'versions_generation_iii_emerald')).toBe('breathe');
    expect(resolveIdle('auto', 'versions_generation_ix_scarlet_violet')).toBe('settle');
    expect(resolveIdle('auto', 'versions_generation_vii_icons')).toBe('breathe');
    expect(resolveIdle('sway', 'versions_generation_ix_scarlet_violet')).toBe('sway');
  });

  it('knows how far the loop reaches, so the sprite can be sized not to clip', () => {
    expect(idleReach(idleFrames('breathe', 'x'))).toEqual({ widest: 1.03, tallest: 1 });
    expect(idleReach(idleFrames('none', 'x'))).toEqual({ widest: 1, tallest: 1 });
  });
});
