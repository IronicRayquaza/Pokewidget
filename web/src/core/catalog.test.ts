import { describe, expect, it } from 'vitest';
import { readFileSync } from 'node:fs';
import { resolve } from 'node:path';
import {
  type BackgroundIndex,
  type PokemonEntry,
  type SpriteSetIndex,
  type TrainerIndex,
  covers,
  cryUrls,
  defaultBackground,
  defaultTrainer,
  parseRanges,
  resolveVariant,
  searchPokemon,
  searchTrainers,
  shinyAvailability,
  spriteUrl,
} from './catalog';

// Corrected path: Assumes data files are in a 'data/' directory at the repository root.
// From web/src/core/, ' Corsica/../..' takes it to the repository root.
// Then 'data' points to the 'data' folder at the root.
const read = <T>(name: string): T =>
  JSON.parse(readFileSync(resolve(__dirname, '../../../data', name), 'utf8')) as T;

const sets = read<SpriteSetIndex>('sets.json');
const pokemon = read<{ pokemon: PokemonEntry[] }>('catalog.json').pokemon;
const trainers = read<TrainerIndex>('trainers.json');
const backgrounds = read<BackgroundIndex>('backgrounds.json');

const set = (id: string) => sets.sets.find((s) => s.id === id)!;

describe('the shared catalogs', () => {
  it('are the ones the Android app ships', () => {
    expect(sets.sets.length).toBeGreaterThan(20);
    expect(pokemon.length).toBeGreaterThan(1000);
    expect(trainers.trainers.length).toBeGreaterThan(500);
    expect(backgrounds.backgrounds.length).toBeGreaterThan(20);
  });

  it('decodes run-length id ranges', () => {
    const test = parseRanges('1-3,10,20-21');
    expect([1, 2, 3, 10, 20, 21].every(test)).toBe(true);
    expect([0, 4, 9, 11, 19, 22].some(test)).toBe(false);
  });
});

describe('variants, exactly as the app resolves them', () => {
  it('finds the plain front of a Pokémon a set really has', () => {
    expect(resolveVariant(set('other_showdown'), 25, false, false, false, null)).toEqual({ path: '', exact: true });
  });

  it('gives up nothing for a set that never drew a Pokémon', () => {
    // Showdown stops short of the last Gen 9 additions.
    expect(resolveVariant(set('other_showdown'), 1025, false, false, false, null)).toBeNull();
  });

  it('drops the least deliberate choice first', () => {
    // Charizard has no female sprite: the female flag goes, the shiny stays.
    const resolved = resolveVariant(set('other_showdown'), 6, false, true, true, null);
    expect(resolved).toEqual({ path: 'shiny', exact: false });
  });

  it('takes Game Boy stills without their white card', () => {
    expect(resolveVariant(set('versions_generation_i_red_blue'), 1, false, false, false, null)?.path).toBe(
      'transparent',
    );
    expect(resolveVariant(set('versions_generation_i_red_blue'), 1, true, false, false, null)?.path).toBe(
      'transparent/back',
    );
    // A style chosen on purpose is left alone, and Gold has no transparent shiny.
    expect(resolveVariant(set('versions_generation_i_red_blue'), 1, false, false, false, 'gray')?.path).toBe('gray');
    expect(resolveVariant(set('versions_generation_ii_gold'), 1, false, true, false, null)?.path).toBe('shiny');
  });

  it('says why a shiny is missing', () => {
    expect(shinyAvailability(set('other_showdown'), 25, false, false, null)).toBe('available');
    expect(shinyAvailability(set('versions_generation_ix_scarlet_violet'), 25, false, false, null)).toBe(
      'set-has-none',
    );
    expect(shinyAvailability(set('other_showdown'), 1025, false, false, null)).toBe('not-for-this-pokemon');
  });

  it('knows what a set covers', () => {
    expect(covers(set('other_showdown'), 25, false, true, false, null)).toBe(true);
    expect(covers(set('versions_generation_i_red_blue'), 25, false, true, false, null)).toBe(false);
  });
});

describe('urls', () => {
  it('pins PokeAPI sprites to the commit in sets.json', () => {
    const url = spriteUrl(sets, set('other_showdown'), { setId: 'other_showdown', pokemonId: 25, shiny: true })!;
    expect(url).toBe(
      `https://cdn.jsdelivr.net/gh/PokeAPI/sprites@${sets.spritesSha}/sprites/pokemon/other/showdown/shiny/25.gif`,
    );
  });

  it('puts a Gen 4 frame directory above the variant, like veekun stores it', () => {
    const platinum = set('veekun_platinum');
    expect(spriteUrl(sets, platinum, { setId: platinum.id, pokemonId: 25 }, 1)).toBe(
      'https://veekun.com/dex/media/pokemon/main-sprites/platinum/frame2/25.png',
    );
  });

  it('asks for the preferred cry first and the other as a fallback', () => {
    expect(cryUrls(25, true)[0]).toContain('/legacy/25.ogg');
    expect(cryUrls(25, false)[0]).toContain('/latest/25.ogg');
  });
});

describe('searching', () => {
  it('finds a Pokémon by name, dex number or form', () => {
    expect(searchPokemon(pokemon, 'ivysaur').map((e) => e.i)).toContain(2);
    expect(searchPokemon(pokemon, '6').some((e) => e.d === 6)).toBe(true);
    expect(searchPokemon(pokemon, 'charizard', 1).every((e) => e.g === 1)).toBe(true);
  });

  it('finds trainers whatever the case or accents', () => {
    expect(searchTrainers(trainers, 'CYNTHIA').length).toBeGreaterThan(0);
    expect(searchTrainers(trainers, 'pokemon ranger').length).toBeGreaterThan(0);
    expect(searchTrainers(trainers, '', 'leader').every((t) => t.o === 'leader')).toBe(true);
  });

  it('starts a battle scene with someone a game drew from behind', () => {
    expect(defaultTrainer(trainers, 3)?.b).toBeTruthy();
    expect(defaultBackground(backgrounds, 3).id).toBe('gen3');
  });
});
