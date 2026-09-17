import { useMemo, useState } from 'preact/hooks';
import type { Catalogs } from '../core/data';
import {
  covers,
  searchPokemon,
  searchTrainers,
  spriteUrl,
  trainerFrontUrl,
  type BattleBackground,
  type SpriteSet,
} from '../core/catalog';
import { battleFrame } from '../core/scene';
import type { WidgetConfig } from '../core/store';

/** The Pokémon list, searchable and filterable by generation. */
export function PokemonPicker({
  catalogs,
  selectedId,
  shiny,
  onSelect,
}: {
  catalogs: Catalogs;
  selectedId: number;
  shiny: boolean;
  onSelect: (id: number) => void;
}) {
  const [query, setQuery] = useState('');
  const [generation, setGeneration] = useState<number | null>(null);
  const results = useMemo(
    () => searchPokemon(catalogs.pokemon, query, generation).slice(0, 300),
    [catalogs, query, generation],
  );

  return (
    <div>
      <input
        type="search"
        value={query}
        placeholder={`Search ${catalogs.pokemon.length.toLocaleString()} Pokémon and forms`}
        onInput={(e) => setQuery((e.target as HTMLInputElement).value)}
      />
      <div class="row" style={{ margin: '10px 0' }}>
        <button aria-pressed={generation === null} onClick={() => setGeneration(null)}>
          All gens
        </button>
        {[1, 2, 3, 4, 5, 6, 7, 8, 9].map((gen) => (
          <button key={gen} aria-pressed={generation === gen} onClick={() => setGeneration(gen)}>
            Gen {gen}
          </button>
        ))}
      </div>
      <div class="grid">
        {results.map((entry) => {
          const set =
            catalogs.setsFor(entry.i).find((s) => s.animated) ?? catalogs.setsFor(entry.i)[0];
          const url =
            set &&
            spriteUrl(catalogs.sets, set, {
              setId: set.id,
              pokemonId: entry.i,
              shiny: shiny && covers(set, entry.i, false, true, false, null),
            });
          return (
            <button
              key={entry.i}
              class="tile"
              aria-pressed={entry.i === selectedId}
              onClick={() => onSelect(entry.i)}
            >
              <span class="art">{url && <img src={url} alt="" class="pixel-art" loading="lazy" />}</span>
              <span class="name">{entry.n}</span>
              <span class="sub">
                {entry.f ?? `#${String(entry.d).padStart(3, '0')}`}
              </span>
            </button>
          );
        })}
        {results.length === 0 && <p class="empty">Nothing matches “{query}”.</p>}
      </div>
    </div>
  );
}

/** Every set that can draw this Pokémon, shown as the art rather than described. */
export function SetPicker({
  catalogs,
  config,
  onSelect,
}: {
  catalogs: Catalogs;
  config: WidgetConfig;
  onSelect: (set: SpriteSet) => void;
}) {
  const sets = catalogs.setsFor(config.pokemonId);
  return (
    <div class="grid wide">
      {sets.map((set) => {
        const url = spriteUrl(catalogs.sets, set, {
          setId: set.id,
          pokemonId: config.pokemonId,
          shiny: config.shiny && covers(set, config.pokemonId, false, true, false, null),
        });
        return (
          <button key={set.id} class="tile" aria-pressed={set.id === config.setId} onClick={() => onSelect(set)}>
            <span class="art">{url && <img src={url} alt="" class="pixel-art" loading="lazy" />}</span>
            <span class="name">{set.label}</span>
            <span class="sub">
              {set.hardware}
              {set.animated ? ' · Animated' : ''}
            </span>
          </button>
        );
      })}
    </div>
  );
}

/** Trainers, grouped by region with a role filter — 1,500 sprites is too many to scroll blind. */
export function TrainerPicker({
  catalogs,
  selectedId,
  onSelect,
}: {
  catalogs: Catalogs;
  selectedId: string | null;
  onSelect: (id: string) => void;
}) {
  const [query, setQuery] = useState('');
  const [role, setRole] = useState<string | null>(null);
  const results = useMemo(() => searchTrainers(catalogs.trainers, query, role), [catalogs, query, role]);
  const regions = catalogs.trainers.regions;

  return (
    <div>
      <input
        type="search"
        value={query}
        placeholder="Search trainers or games"
        onInput={(e) => setQuery((e.target as HTMLInputElement).value)}
      />
      <div class="row" style={{ margin: '10px 0' }}>
        <button aria-pressed={role === null} onClick={() => setRole(null)}>
          All
        </button>
        {catalogs.trainers.roles.map((r) => (
          <button key={r.id} aria-pressed={role === r.id} onClick={() => setRole(role === r.id ? null : r.id)}>
            {r.label}
          </button>
        ))}
      </div>
      <div class="grid">
        {regions.flatMap((region) => {
          const inRegion = results.filter((t) => t.r === region.id).slice(0, 200);
          if (inRegion.length === 0) return [];
          return [
            <p key={`h-${region.id}`} class="section-title" style={{ gridColumn: '1 / -1', margin: '6px 0 0' }}>
              {region.label}
            </p>,
            ...inRegion.map((trainer) => (
              <button
                key={trainer.i}
                class="tile"
                aria-pressed={trainer.i === selectedId}
                onClick={() => onSelect(trainer.i)}
              >
                <span class="art" style={{ position: 'relative' }}>
                  <img src={trainerFrontUrl(catalogs.trainers, trainer)} alt="" class="pixel-art" loading="lazy" />
                  {trainer.b && (
                    <span class="badge" style={{ position: 'absolute', top: 2, right: 2 }}>
                      BACK
                    </span>
                  )}
                </span>
                <span class="name">{trainer.n}</span>
                <span class="sub">{trainer.v ?? catalogs.trainers.roles.find((r) => r.id === trainer.o)?.label}</span>
              </button>
            )),
          ];
        })}
        {results.length === 0 && <p class="empty">No trainers match “{query}”.</p>}
      </div>
    </div>
  );
}

/** Battlefields, each thumbnail cropped exactly as the widget crops it. */
export function BackgroundPicker({
  catalogs,
  selectedId,
  onSelect,
}: {
  catalogs: Catalogs;
  selectedId: string | null;
  onSelect: (background: BattleBackground) => void;
}) {
  return (
    <div class="thumbs">
      {catalogs.backgrounds.backgrounds.map((background) => {
        const { crop } = battleFrame(background, 128, 64);
        const scale = Math.max(128 / crop.width, 64 / crop.height);
        return (
          <button
            key={background.id}
            class="thumb"
            aria-pressed={background.id === selectedId}
            onClick={() => onSelect(background)}
          >
            <span class="shot">
              <img
                src={background.url}
                alt=""
                class="pixel-art"
                loading="lazy"
                style={{
                  position: 'absolute',
                  width: `${background.w * scale}px`,
                  height: `${background.h * scale}px`,
                  left: `${-crop.left * scale - (crop.width * scale - 128) / 2}px`,
                  top: `${-crop.top * scale - (crop.height * scale - 64) / 2}px`,
                  maxWidth: 'none',
                }}
              />
            </span>
            {background.gen <= 4 ? `Gen ${background.gen} · ${background.label}` : background.label}
          </button>
        );
      })}
    </div>
  );
}

