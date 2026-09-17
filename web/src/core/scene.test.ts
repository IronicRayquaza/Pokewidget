import { describe, expect, it } from 'vitest';
import type { BattleBackground } from './catalog';
import { battleFrame, bottom, coverCrop, displayScale, fit, layout, right } from './scene';

const gen4: BattleBackground = {
  id: 'gen4',
  label: 'Diamond & Pearl',
  gen: 4,
  url: '',
  fallbackUrl: '',
  w: 753,
  h: 500,
  crop: [156, 96, 428, 284],
  foe: [0.757, 0.532],
  player: [0.243, 1],
};

describe('scene layout, matching the Android widget', () => {
  it('gives a solo Pokémon the whole box', () => {
    const l = layout('solo', 'left', 400, 300);
    expect(l.pokemon).toEqual({ left: 0, top: 0, width: 400, height: 300 });
    expect(l.trainer).toBeNull();
  });

  it('splits side by side without overlap, either way round', () => {
    for (const side of ['left', 'right'] as const) {
      const l = layout('side-by-side', side, 400, 300);
      const trainer = l.trainer!;
      expect(trainer.width + l.pokemon.width).toBe(400);
      expect(right(trainer) <= l.pokemon.left || right(l.pokemon) <= trainer.left).toBe(true);
      expect(trainer.left < l.pokemon.left).toBe(side === 'left');
    }
  });

  it('stands the Pokémon on the platform and the trainer in front, at any shape', () => {
    const stage = { foe: { x: 0.75, y: 0.53 }, player: { x: 0.25, y: 1 } };
    for (const [w, h] of [[400, 300], [1000, 250], [480, 800]] as const) {
      const l = layout('battle', 'left', w, h, stage);
      expect(l.trainerInFront).toBe(true);
      expect(bottom(l.trainer!)).toBe(h);
      expect(l.pokemon.left + l.pokemon.width / 2).toBeCloseTo(0.75 * w, 0);
      expect(bottom(l.pokemon)).toBeGreaterThanOrEqual(0.53 * h);
      expect(bottom(l.pokemon)).toBeLessThanOrEqual(0.65 * h);
      expect(l.pokemon.left).toBeGreaterThanOrEqual(0);
      expect(right(l.pokemon)).toBeLessThanOrEqual(w);
    }
    const l = layout('battle', 'right', 400, 300, stage);
    expect(l.pokemon.left).toBe(400 - right(layout('battle', 'left', 400, 300, stage).pokemon));
  });

  it('keeps the far platform in view whatever shape the widget is', () => {
    for (const [w, h] of [[1000, 250], [400, 400], [480, 800], [300, 900]] as const) {
      const { crop, stage } = battleFrame(gen4, w, h);
      expect(crop.left).toBeGreaterThanOrEqual(156);
      expect(right(crop)).toBeLessThanOrEqual(156 + 428);
      expect(stage.foe.x).toBeGreaterThanOrEqual(0.3);
      expect(stage.foe.x).toBeLessThanOrEqual(0.92);
      expect(stage.foe.y).toBeGreaterThanOrEqual(0.2);
      expect(stage.foe.y).toBeLessThanOrEqual(0.9);
    }
  });

  it('crops a background to the box shape rather than stretching it', () => {
    const wide = coverCrop(753, 500, 400, 200);
    expect(wide.width).toBe(753);
    expect(wide.height).toBe(377);
    const square = coverCrop(753, 500, 300, 300);
    expect(square.width).toBe(500);
    expect(square.left).toBe(Math.round((753 - 500) / 2));
  });

  it('fits an image to a box, on the floor when asked', () => {
    const box = { left: 10, top: 20, width: 100, height: 200 };
    const standing = fit(64, 64, box, true);
    expect(standing).toEqual({ left: 10, top: bottom(box) - 100, width: 100, height: 100 });
    expect(fit(64, 64, box, false).top).toBe(70);
  });
});

describe('sprite size', () => {
  it('fills the box, less the margin', () => {
    expect(displayScale(64, 64, 256, 256)).toBeCloseTo(3.68, 2);
  });

  it('honours an exact multiple, clamped to the box', () => {
    expect(displayScale(51, 53, 470, 470, 4)).toBe(4);
    expect(displayScale(142, 153, 350, 350, 4) * 153).toBeLessThanOrEqual(350);
  });

  it('keeps species in proportion at true size', () => {
    const torterra = displayScale(98, 106, 470, 470, null, 140) * 106;
    const psyduck = displayScale(51, 53, 470, 470, null, 140) * 53;
    expect(torterra / psyduck).toBeCloseTo(2, 1);
  });
});
