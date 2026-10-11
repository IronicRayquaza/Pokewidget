import { describe, expect, it } from 'vitest';
import { appliesTo, formFor, weatherFromWmo, type WorldState } from './formRules';
import { attempts, filter, toPlace, type GeoResult } from './placeQuery';
import { hourAt, isDayAt, worldStateOf, type Reading } from './weather';
import { applyTap, pickSurprise, surpriseStillFits, LAST_SPECIES } from './tap';
import { DEFAULT_CONFIG } from './store';

const world = (w: Partial<WorldState> = {}): WorldState => ({ weather: 'cloudy', isDay: true, hour: 12, ...w });

describe('live forms, matching FormRulesTest.kt', () => {
  it('Castform takes the form of the weather, and needs the sun to be sunny', () => {
    expect(formFor(351, world({ weather: 'rain' }))).toBe(10014);
    expect(formFor(351, world({ weather: 'snow' }))).toBe(10015);
    expect(formFor(351, world({ weather: 'clear' }))).toBe(10013);
    expect(formFor(351, world({ weather: 'clear', isDay: false, hour: 1 }))).toBe(351);
    for (const member of [351, 10013, 10014, 10015]) expect(formFor(member, world({ weather: 'snow' }))).toBe(10015);
  });

  it('Lycanroc follows the clock, with dusk getting the evening window', () => {
    expect(formFor(745, world({ hour: 17 }))).toBe(10152);
    expect(formFor(745, world({ hour: 12 }))).toBe(745);
    expect(formFor(745, world({ isDay: false, hour: 23 }))).toBe(10126);
  });

  it('Shaymin, Eiscue and Morpeko, and nobody else', () => {
    expect(formFor(492, world({ isDay: false }))).toBe(492);
    expect(formFor(492, world({ weather: 'snow' }))).toBe(492);
    expect(formFor(492, world())).toBe(10006);
    expect(formFor(875, world({ weather: 'clear' }))).toBe(10185);
    expect(formFor(877, world({ isDay: false }))).toBe(10187);
    expect(appliesTo(25)).toBe(false);
    expect(formFor(25, world({ weather: 'rain' }))).toBe(25);
  });

  it('reads WMO codes the same way', () => {
    expect([0, 1, 2, 45, 61, 95, 71, 86, 999].map(weatherFromWmo)).toEqual([
      'clear', 'clear', 'cloudy', 'cloudy', 'rain', 'rain', 'snow', 'snow', 'cloudy',
    ]);
  });
});

describe('city search, matching PlaceQueryTest.kt', () => {
  const richmonds: GeoResult[] = [
    { name: 'Richmond', latitude: 37.55, longitude: -77.46, admin1: 'Virginia', country: 'United States', countryCode: 'US' },
    { name: 'Richmond', latitude: 49.17, longitude: -123.14, admin1: 'British Columbia', country: 'Canada', countryCode: 'CA' },
    { name: 'Richmond', latitude: 37.94, longitude: -122.35, admin1: 'California', country: 'United States', countryCode: 'US' },
    { name: 'Richmond', latitude: 39.83, longitude: -84.89, admin1: 'Indiana', country: 'United States', countryCode: 'US' },
  ];

  it('reads "Richmond VA" as Richmond in Virginia', () => {
    const first = attempts("Richmond VA")[0]!;
    expect(first).toEqual({ name: 'Richmond', qualifier: 'VA' });
    expect(filter(richmonds, first.qualifier)!.map((r) => r.admin1)).toEqual(['Virginia']);
  });

  it('understands commas, full names, countries and codes', () => {
    expect(attempts('Richmond, Virginia')).toEqual([{ name: 'Richmond', qualifier: 'Virginia' }]);
    expect(filter(richmonds, 'virg')!.length).toBe(1);
    expect(filter(richmonds, 'USA')!.length).toBe(3);
    expect(filter(richmonds, 'Virginia, United States')!.length).toBe(1);
    expect(filter(richmonds, "BC")![0]!.admin1).toBe('British Columbia');
    expect(filter(richmonds, 'Francisco')).toBeNull();
  });

  it('keeps a multi-word city whole first, and tries the raw text last', () => {
    const list = attempts('New York NY');
    expect(list[0]).toEqual({ name: 'New York', qualifier: 'NY' });
    expect(list[list.length - 1]).toEqual({ name: 'New York NY', qualifier: null });
    expect(attempts('K')).toEqual([]);
  });

  it('labels with region and country', () => {
    expect(toPlace(richmonds[0]!).label).toBe('Richmond, Virginia, United States');
  });
});

describe("the chosen city's clock, matching WorldClockTest.kt", () => {
  const offset = -4 * 3600;
  const midnight = Date.UTC(2026, 9, 11) / 1000 - offset;
  const at = (hour: number, minute = 0) => (midnight + hour * 3600 + minute * 60) * 1000;
  const sun = [
    { rise: midnight + 7 * 3600 + 16 * 60, set: midnight + 18 * 3600 + 41 * 60 },
    { rise: midnight + 86_400 + 7 * 3600 + 17 * 60, set: midnight + 86_400 + 18 * 3600 + 39 * 60 },
  ];

  it("uses the city's hour and sun, not this computer's", () => {
    expect(hourAt(at(21), offset, 6)).toBe(21);
    expect(isDayAt(at(12), sun, false)).toBe(true);
    expect(isDayAt(at(21), sun, true)).toBe(false);
    expect(isDayAt(at(24 + 12), sun, false)).toBe(true);
    expect(isDayAt(at(48 + 21), sun, true)).toBe(true);
    expect(hourAt(at(21), null, 15)).toBe(15);
  });

  it('gives Lycanroc its midnight form in a city where it is night', () => {
    const reading: Reading = { weather: 'clear', isDay: true, takenAt: 0, utcOffsetSec: offset, sun };
    expect(formFor(745, worldStateOf(reading, at(23), 12))).toBe(10126);
    expect(formFor(745, worldStateOf(reading, at(17, 30), 3))).toBe(10152);
  });
});

describe('clicks, matching the Android tap actions', () => {
  const everything = () => true;
  const config = { ...DEFAULT_CONFIG, pokemonId: 25 };

  it('cries by default, and only when cries are on', () => {
    expect(applyTap(config, 25, everything)).toEqual({ change: null, cryId: 25, effect: null });
    expect(applyTap({ ...config, cryEnabled: false }, 25, everything).cryId).toBeNull();
    // A live form on screen cries as itself.
    expect(applyTap(config, 10013, everything).cryId).toBe(10013);
  });

  it('a surprise replaces the Pokémon and plays the newcomer', () => {
    const result = applyTap({ ...config, tapAction: 'surprise', surpriseNext: 133 }, 25, everything);
    expect(result.change).toEqual({ pokemonId: 133, style: null, female: false, surpriseNext: 0 });
    expect(result.cryId).toBe(133);
  });

  it('a surprise with nothing chosen picks from the pool, never the one on screen', () => {
    for (let i = 0; i < 200; i++) {
      const result = applyTap({ ...config, tapAction: 'surprise', surprisePool: [25, 6, 150] }, 25, everything);
      expect([6, 150]).toContain(result.change!.pokemonId);
    }
    for (let i = 0; i < 500; i++) {
      const id = pickSurprise([], (n) => n <= 151, 25)!;
      expect(id).toBeGreaterThanOrEqual(1);
      expect(id).toBeLessThanOrEqual(151);
    }
    expect(pickSurprise([25], everything, 25)).toBe(25);
    expect(pickSurprise([9999], (n) => n < LAST_SPECIES, 25)).toBeNull();
  });

  it('drops a chosen surprise that no longer fits', () => {
    expect(surpriseStillFits(133, [], everything, 25)).toBe(true);
    expect(surpriseStillFits(133, [6], everything, 25)).toBe(false);
    expect(surpriseStillFits(10034, [], everything, 25)).toBe(false);
    expect(surpriseStillFits(25, [], everything, 25)).toBe(false);
  });

  it('toggles shiny, back and mirror, hops when excited, and opens the app', () => {
    expect(applyTap({ ...config, tapAction: 'shiny' }, 25, everything).change).toEqual({ shiny: true });
    expect(applyTap({ ...config, tapAction: 'flip' }, 25, everything).change).toEqual({ back: true });
    expect(applyTap({ ...config, tapAction: 'mirror' }, 25, everything).change).toEqual({ flipHorizontal: true });
    expect(applyTap({ ...config, tapAction: 'excite' }, 25, everything)).toEqual({ change: null, cryId: 25, effect: 'excite' });
    expect(applyTap({ ...config, tapAction: 'open-app' }, 25, everything).effect).toBe('open-app');
    expect(applyTap({ ...config, tapAction: 'none' }, 25, everything)).toEqual({ change: null, cryId: null, effect: null });
  });
});
