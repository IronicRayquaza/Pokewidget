/**
 * The catalogs, and the rules for turning a choice into a URL.
 *
 * Deliberately a port of the Kotlin in `app/src/main/java/com/pokewidgets/app/catalog/`,
 * reading the very same generated JSON: a Pokémon, a sprite set, a trainer and a background
 * must mean the same thing on a phone and on a desktop. The tests check the ported rules
 * against the real shipped files, as the Kotlin ones do.
 */

export interface PokemonEntry {
  /** Short keys: the files are generated, and ship to phones over mobile data. */
  i: number;
  n: string;
  d: number;
  g: number;
  t?: string[];
  f?: string;
  s?: string;
}

export interface SpriteSet {
  id: string;
  path: string;
  label: string;
  game: string;
  hardware: string;
  gen: number;
  animated: boolean;
  ext: string;
  order: number;
  note?: string;
  variants: Record<string, string>;
  provider?: 'pokeapi' | 'veekun';
  frameDirs?: string[];
  frameDelaysMs?: number[];
  referencePx?: number;
}

export interface SpriteSetIndex {
  spritesSha: string;
  sets: SpriteSet[];
}

export interface TrainerBack {
  u: string;
  f: string;
  w: number;
  h: number;
  p?: string[];
}

export interface Trainer {
  i: string;
  n: string;
  v?: string;
  r: string;
  o: string;
  g: number;
  b?: TrainerBack;
}

export interface TrainerIndex {
  frontBase: string;
  regions: { id: string; label: string }[];
  roles: { id: string; label: string }[];
  trainers: Trainer[];
}

export interface BattleBackground {
  id: string;
  label: string;
  gen: number;
  url: string;
  fallbackUrl: string;
  w: number;
  h: number;
  crop?: number[];
  foe?: number[];
  player?: number[];
}

export interface BackgroundIndex {
  sha: string;
  backgrounds: BattleBackground[];
}

export interface SpriteKey {
  setId: string;
  pokemonId: number;
  back?: boolean;
  shiny?: boolean;
  female?: boolean;
  style?: string | null;
}

/** Upstream's name for a still with its white card removed; see `resolveVariant`. */
const TRANSPARENT = 'transparent';

/** `"1-151,10001-10010"` → a membership test. The ranges are sorted, so a binary search. */
export function parseRanges(encoded: string): (id: number) => boolean {
  const ranges: [number, number][] = [];
  for (const part of encoded.split(',')) {
    if (!part) continue;
    const [a, b] = part.split('-');
    const from = Number(a);
    if (Number.isNaN(from)) continue;
    ranges.push([from, b === undefined ? from : Number(b)]);
  }
  return (id) => {
    let lo = 0;
    let hi = ranges.length - 1;
    while (lo <= hi) {
      const mid = (lo + hi) >> 1;
      const range = ranges[mid]!;
      if (id < range[0]) hi = mid - 1;
      else if (id > range[1]) lo = mid + 1;
      else return true;
    }
    return false;
  };
}

const idTests = new WeakMap<SpriteSet, Map<string, (id: number) => boolean>>();

function idsFor(set: SpriteSet, variant: string): (id: number) => boolean {
  let perSet = idTests.get(set);
  if (!perSet) {
    perSet = new Map();
    idTests.set(set, perSet);
  }
  let test = perSet.get(variant);
  if (!test) {
    test = parseRanges(set.variants[variant] ?? '');
    perSet.set(variant, test);
  }
  return test;
}

function permutations<T>(items: T[]): T[][] {
  if (items.length <= 1) return [items];
  const out: T[][] = [];
  items.forEach((item, i) => {
    const rest = [...items.slice(0, i), ...items.slice(i + 1)];
    for (const tail of permutations(rest)) out.push([item, ...tail]);
  });
  return out;
}

/**
 * The real directory for a variant combination, or null when this set has no such variant.
 * Every segment order is tried because upstream is not consistent: `back/gray` but
 * `transparent/back`.
 */
export function variantPath(
  set: SpriteSet,
  back: boolean,
  shiny: boolean,
  female: boolean,
  style: string | null,
): string | null {
  const parts: string[] = [];
  if (style) parts.push(style);
  if (back) parts.push('back');
  if (shiny) parts.push('shiny');
  if (female) parts.push('female');
  if (parts.length === 0) return '' in set.variants ? '' : null;
  for (const candidate of permutations(parts)) {
    const key = candidate.join('/');
    if (key in set.variants) return key;
  }
  return null;
}

export function covers(
  set: SpriteSet,
  pokemonId: number,
  back: boolean,
  shiny: boolean,
  female: boolean,
  style: string | null,
): boolean {
  const path = variantPath(set, back, shiny, female, style);
  return path === null ? false : idsFor(set, path)(pokemonId);
}

export const hasShinies = (set: SpriteSet): boolean =>
  Object.keys(set.variants).some((key) => key.split('/').includes('shiny'));

/** Game Boy stills whose plain art sits on a white card, and which have a clean copy. */
export const prefersTransparent = (set: SpriteSet): boolean =>
  !set.animated && TRANSPARENT in set.variants;

export interface ResolvedVariant {
  path: string;
  exact: boolean;
}

/**
 * The variant to actually fetch, giving up flags one at a time until something exists —
 * female first, then style, then back, then shiny, so the most deliberate choice survives
 * longest. Null means this set never drew this Pokémon at all.
 */
export function resolveVariant(
  set: SpriteSet,
  pokemonId: number,
  back: boolean,
  shiny: boolean,
  female: boolean,
  style: string | null,
): ResolvedVariant | null {
  if (!style && prefersTransparent(set)) {
    const clean = variantPath(set, back, shiny, female, TRANSPARENT);
    if (clean !== null && idsFor(set, clean)(pokemonId)) return { path: clean, exact: true };
  }
  let b = back;
  let s = shiny;
  let f = female;
  let st = style;
  let exact = true;
  for (;;) {
    const path = variantPath(set, b, s, f, st);
    if (path !== null && idsFor(set, path)(pokemonId)) return { path, exact };
    if (f) f = false;
    else if (st) st = null;
    else if (b) b = false;
    else if (s) s = false;
    else return null;
    exact = false;
  }
}

export type ShinyAvailability = 'available' | 'set-has-none' | 'not-for-this-pokemon' | 'not-with-these-options';

export function shinyAvailability(
  set: SpriteSet,
  pokemonId: number,
  back: boolean,
  female: boolean,
  style: string | null,
): ShinyAvailability {
  if (covers(set, pokemonId, back, true, female, style)) return 'available';
  if (!hasShinies(set)) return 'set-has-none';
  if (covers(set, pokemonId, false, true, false, null)) return 'not-with-these-options';
  return 'not-for-this-pokemon';
}

const VEEKUN_ROOT = 'https://veekun.com/dex/media';

/** `<set path>/<frame dir>/<variant>/<id>.<ext>`, the frame directory above the variant. */
function spritePath(set: SpriteSet, pokemonId: number, variant: string, part: number): string {
  const frameDir = set.frameDirs?.[part];
  return [set.path, frameDir || null, variant || null, `${pokemonId}.${set.ext}`]
    .filter((segment): segment is string => Boolean(segment))
    .join('/');
}

/** Where to look for one file, best host first. */
export function spriteUrls(index: SpriteSetIndex, set: SpriteSet, key: SpriteKey, part = 0): string[] {
  const resolved = resolveVariant(
    set,
    key.pokemonId,
    key.back ?? false,
    key.shiny ?? false,
    key.female ?? false,
    key.style ?? null,
  );
  if (!resolved) return [];
  const path = spritePath(set, key.pokemonId, resolved.path, part);
  if (set.provider === 'veekun') return [`${VEEKUN_ROOT}/${path}`];
  return [
    `https://cdn.jsdelivr.net/gh/PokeAPI/sprites@${index.spritesSha}/${path}`,
    `https://raw.githubusercontent.com/PokeAPI/sprites/${index.spritesSha}/${path}`,
  ];
}

export const spriteUrl = (index: SpriteSetIndex, set: SpriteSet, key: SpriteKey, part = 0): string | null =>
  spriteUrls(index, set, key, part)[0] ?? null;

/** How many files one sprite of this set is assembled from; Gen 4 keeps its two frames apart. */
export const partCount = (set: SpriteSet): number => Math.max(set.frameDirs?.length ?? 0, 1);

export const cryUrls = (pokemonId: number, legacy: boolean): string[] =>
  (legacy ? ['legacy', 'latest'] : ['latest', 'legacy']).map(
    (flavour) => `https://cdn.jsdelivr.net/gh/PokeAPI/cries@main/cries/pokemon/${flavour}/${pokemonId}.ogg`,
  );

export const displayName = (entry: PokemonEntry): string => (entry.f ? `${entry.n} (${entry.f})` : entry.n);

export const trainerName = (trainer: Trainer): string => (trainer.v ? `${trainer.n} · ${trainer.v}` : trainer.n);

export const trainerFrontUrl = (index: TrainerIndex, trainer: Trainer): string =>
  `${index.frontBase}${trainer.i}.png`;

const MARKS = /\p{Mn}+/gu;

const fold = (s: string): string => s.normalize('NFD').replace(MARKS, '').toLowerCase();

export function searchTrainers(index: TrainerIndex, query: string, role?: string | null): Trainer[] {
  const q = fold(query.trim());
  const regionLabel = (id: string) => index.regions.find((r) => r.id === id)?.label ?? id;
  return index.trainers.filter(
    (t) =>
      (!role || t.o === role) &&
      (!q || fold(t.n).includes(q) || fold(t.v ?? '').includes(q) || fold(regionLabel(t.r)).includes(q)),
  );
}

export function searchPokemon(all: PokemonEntry[], query: string, generation?: number | null): PokemonEntry[] {
  const q = query.trim().toLowerCase();
  return all.filter(
    (e) =>
      (!generation || e.g === generation) &&
      (!q ||
        e.n.toLowerCase().includes(q) ||
        (e.s ?? '').includes(q) ||
        (e.f ?? '').toLowerCase().includes(q) ||
        String(e.d) === q),
  );
}

/** The trainer a battle scene starts with: this generation's player, seen from behind. */
export function defaultTrainer(index: TrainerIndex, gen: number): Trainer | undefined {
  const players = index.trainers.filter((t) => t.o === 'player');
  return players.find((t) => t.g === gen && t.b) ?? players.find((t) => t.b) ?? players[0];
}

export const backgroundById = (index: BackgroundIndex, id: string | null): BattleBackground | undefined =>
  id ? index.backgrounds.find((b) => b.id === id) : undefined;

export const defaultBackground = (index: BackgroundIndex, gen: number): BattleBackground =>
  index.backgrounds.find((b) => b.gen === gen && !b.id.includes('-')) ??
  index.backgrounds.find((b) => b.id === 'route')!;
