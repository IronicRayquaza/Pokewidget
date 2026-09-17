import type {
  BackgroundIndex,
  PokemonEntry,
  SpriteSet,
  SpriteSetIndex,
  TrainerIndex,
} from './catalog';

/** Everything generated, loaded once per window and shared by every view in it. */
export interface Catalogs {
  pokemon: PokemonEntry[];
  sets: SpriteSetIndex;
  trainers: TrainerIndex;
  backgrounds: BackgroundIndex;
  set(id: string): SpriteSet | undefined;
  entry(id: number): PokemonEntry | undefined;
  /** Sets that can draw this Pokémon at all, animated ones first. */
  setsFor(pokemonId: number): SpriteSet[];
}

let loading: Promise<Catalogs> | null = null;

const json = async <T>(name: string): Promise<T> => {
  const response = await fetch(new URL(`data/${name}`, document.baseURI));
  if (!response.ok) throw new Error(`could not load ${name}`);
  return (await response.json()) as T;
};

export function loadCatalogs(): Promise<Catalogs> {
  if (!loading) {
    loading = (async () => {
      const [pokemonFile, sets, trainers, backgrounds] = await Promise.all([
        json<{ pokemon: PokemonEntry[] }>('catalog.json'),
        json<SpriteSetIndex>('sets.json'),
        json<TrainerIndex>('trainers.json'),
        json<BackgroundIndex>('backgrounds.json'),
      ]);
      const pokemon = pokemonFile.pokemon;
      const byId = new Map(pokemon.map((e) => [e.i, e]));
      const bySetId = new Map(sets.sets.map((s) => [s.id, s]));
      // Which set covers which Pokémon is asked constantly while browsing, so the front
      // ranges are parsed once here rather than on every keystroke.
      const covered = new Map<string, (id: number) => boolean>();
      for (const set of sets.sets) {
        covered.set(set.id, parseFront(set));
      }
      return {
        pokemon,
        sets,
        trainers,
        backgrounds,
        set: (id) => bySetId.get(id),
        entry: (id) => byId.get(id),
        setsFor: (pokemonId) =>
          sets.sets
            .filter((s) => covered.get(s.id)?.(pokemonId))
            .sort((a, b) => Number(b.animated) - Number(a.animated) || a.order - b.order),
      } satisfies Catalogs;
    })();
  }
  return loading;
}

function parseFront(set: SpriteSet): (id: number) => boolean {
  const ranges = (set.variants[''] ?? '')
    .split(',')
    .filter(Boolean)
    .map((part) => {
      const [a, b] = part.split('-');
      return [Number(a), b === undefined ? Number(a) : Number(b)] as const;
    });
  return (id) => ranges.some(([from, to]) => id >= from && id <= to);
}
