/**
 * Pokémon whose form follows the real world, and which form to show — the same rules as
 * `catalog/FormRules.kt`, so a Castform on the desktop and one on a phone agree.
 *
 * Every form is its own Pokémon id upstream, so following the world is a substitution and
 * nothing more: no new sprite plumbing.
 */

export type Weather = 'clear' | 'cloudy' | 'rain' | 'snow';

export interface WorldState {
  weather: Weather;
  isDay: boolean;
  /** The hour in the chosen city, 0..23. Separate from `isDay` because dusk is neither. */
  hour: number;
}

/** WMO code → weather. Fog counts as cloudy, storms and drizzle as rain; unknown is cloudy. */
export function weatherFromWmo(code: number): Weather {
  if (code === 0 || code === 1) return 'clear';
  if (code === 2 || code === 3 || code === 45 || code === 48) return 'cloudy';
  if ((code >= 51 && code <= 67) || (code >= 80 && code <= 82) || (code >= 95 && code <= 99)) return 'rain';
  if ((code >= 71 && code <= 77) || code === 85 || code === 86) return 'snow';
  return 'cloudy';
}

const CASTFORM = 351;
const CASTFORM_SUNNY = 10013;
const CASTFORM_RAINY = 10014;
const CASTFORM_SNOWY = 10015;
const LYCANROC_MIDDAY = 745;
const LYCANROC_MIDNIGHT = 10126;
const LYCANROC_DUSK = 10152;
const SHAYMIN_LAND = 492;
const SHAYMIN_SKY = 10006;
const EISCUE_ICE = 875;
const EISCUE_NOICE = 10185;
const MORPEKO_FULL = 877;
const MORPEKO_HANGRY = 10187;

interface Rule {
  label: string;
  members: number[];
  pick: (world: WorldState) => number;
}

const RULES: Rule[] = [
  {
    label: 'follows the weather',
    members: [CASTFORM, CASTFORM_SUNNY, CASTFORM_RAINY, CASTFORM_SNOWY],
    pick: (w) =>
      w.weather === 'rain'
        ? CASTFORM_RAINY
        : w.weather === 'snow'
          ? CASTFORM_SNOWY
          : // Sunny Form comes from harsh sunlight, so a clear sky at midnight is not it.
            w.weather === 'clear' && w.isDay
            ? CASTFORM_SUNNY
            : CASTFORM,
  },
  {
    label: 'follows the time of day',
    members: [LYCANROC_MIDDAY, LYCANROC_MIDNIGHT, LYCANROC_DUSK],
    pick: (w) => (w.hour >= 17 && w.hour <= 18 ? LYCANROC_DUSK : w.isDay ? LYCANROC_MIDDAY : LYCANROC_MIDNIGHT),
  },
  {
    label: 'flies by day',
    members: [SHAYMIN_LAND, SHAYMIN_SKY],
    pick: (w) => (w.isDay && w.weather !== 'snow' ? SHAYMIN_SKY : SHAYMIN_LAND),
  },
  {
    label: 'keeps its ice in the cold',
    members: [EISCUE_ICE, EISCUE_NOICE],
    pick: (w) => (w.weather === 'clear' && w.isDay ? EISCUE_NOICE : EISCUE_ICE),
  },
  {
    label: 'gets hangry after dark',
    members: [MORPEKO_FULL, MORPEKO_HANGRY],
    pick: (w) => (w.isDay ? MORPEKO_FULL : MORPEKO_HANGRY),
  },
];

const byMember = new Map<number, Rule>(RULES.flatMap((rule) => rule.members.map((id) => [id, rule] as const)));

/** Whether this Pokémon has anything to follow. */
export const appliesTo = (pokemonId: number): boolean => byMember.has(pokemonId);

/** Which form of `pokemonId` suits `world`, or `pokemonId` itself when it has no rule. */
export const formFor = (pokemonId: number, world: WorldState): number =>
  byMember.get(pokemonId)?.pick(world) ?? pokemonId;

/**
 * Every form `pokemonId` can be turned into, itself included; just itself without a rule.
 * `FormRules.familyOf` on Android.
 */
export const familyOf = (pokemonId: number): number[] => byMember.get(pokemonId)?.members ?? [pokemonId];

/** Short phrase for the editor, e.g. "follows the weather". */
export const describeRule = (pokemonId: number): string | null => byMember.get(pokemonId)?.label ?? null;
