import type { TapAction, WidgetConfig } from './store';

/**
 * What a click on a widget does — every tap action the Android widget has, decided here once
 * for the desktop widget windows and the editor preview alike.
 *
 * Pure: it says what should change and which cry should play, and the caller does it. That
 * keeps the rules testable without a window, audio or storage.
 */

export const TAP_ACTIONS: ReadonlyArray<{ id: TapAction; label: string; description: string }> = [
  { id: 'cry', label: 'Play cry', description: "Plays the Pokémon's cry" },
  { id: 'surprise', label: 'Surprise me', description: 'Swaps in a random Pokémon and plays its cry' },
  { id: 'shiny', label: 'Toggle shiny', description: 'Swaps between the normal and shiny sprite' },
  { id: 'flip', label: 'Turn around', description: 'Swaps between the front and back sprite' },
  { id: 'mirror', label: 'Face the other way', description: 'Mirrors the sprite left to right' },
  { id: 'excite', label: 'Get excited', description: 'A happy little hop, with its cry' },
  { id: 'open-app', label: 'Open PokéWidget', description: 'Opens the setup window' },
  { id: 'none', label: 'Do nothing', description: 'Ignores clicks' },
];

/** Every action that makes a sound, when cries are on. */
const SOUNDING: ReadonlySet<TapAction> = new Set(['cry', 'excite', 'surprise']);

export interface TapResult {
  change: Partial<WidgetConfig> | null;
  /** Whose cry to play, or null for silence. */
  cryId: number | null;
  effect: 'excite' | 'open-app' | null;
}

/** The last species number; everything above is an alternate form. */
export const LAST_SPECIES = 1025;

/** Everything the pool allows that the sprite set drew. Empty pool means every species. */
export function surpriseCandidates(pool: number[], covered: (id: number) => boolean): number[] {
  const base = pool.length ? [...new Set(pool)].sort((a, b) => a - b) : Array.from({ length: LAST_SPECIES }, (_, i) => i + 1);
  return base.filter(covered);
}

/** A random candidate other than `current`; `current` if it is the only one; else null. */
export function pickSurprise(
  pool: number[],
  covered: (id: number) => boolean,
  current: number,
  random: () => number = Math.random,
): number | null {
  const all = surpriseCandidates(pool, covered);
  const others = all.filter((id) => id !== current);
  if (others.length) return others[Math.floor(random() * others.length)] ?? null;
  return all[0] ?? null;
}

/** Whether an already chosen next surprise still fits the pool and the set. */
export function surpriseStillFits(next: number, pool: number[], covered: (id: number) => boolean, current: number): boolean {
  if (next <= 0 || next === current) return false;
  const allowed = pool.length ? pool.includes(next) : next >= 1 && next <= LAST_SPECIES;
  return allowed && covered(next);
}

/**
 * @param shownId the Pokémon actually on screen — a live form may differ from the one
 *   chosen, and its cry is the one that belongs to the click.
 * @param covered whether the widget's sprite set drew a Pokémon at all.
 */
export function applyTap(
  config: WidgetConfig,
  shownId: number,
  covered: (id: number) => boolean,
  random: () => number = Math.random,
): TapResult {
  const cries = config.cryEnabled && SOUNDING.has(config.tapAction);
  switch (config.tapAction) {
    case 'surprise': {
      const next = surpriseStillFits(config.surpriseNext, config.surprisePool, covered, config.pokemonId)
        ? config.surpriseNext
        : pickSurprise(config.surprisePool, covered, config.pokemonId, random);
      if (next === null || next === config.pokemonId) return { change: null, cryId: cries ? shownId : null, effect: null };
      // Replaces the Pokémon on this widget; it never adds another. Style and female are
      // per-Pokémon choices, so they do not carry over.
      return {
        change: { pokemonId: next, style: null, female: false, surpriseNext: 0 },
        cryId: cries ? next : null,
        effect: null,
      };
    }
    case 'shiny':
      return { change: { shiny: !config.shiny }, cryId: null, effect: null };
    case 'flip':
      return { change: { back: !config.back }, cryId: null, effect: null };
    case 'mirror':
      return { change: { flipHorizontal: !config.flipHorizontal }, cryId: null, effect: null };
    case 'excite':
      return { change: null, cryId: cries ? shownId : null, effect: 'excite' };
    case 'open-app':
      return { change: null, cryId: null, effect: 'open-app' };
    case 'none':
      return { change: null, cryId: null, effect: null };
    case 'cry':
    default:
      return { change: null, cryId: cries ? shownId : null, effect: null };
  }
}
