import { weatherFromWmo, type Weather, type WorldState } from './formRules';
import { attempts, filter, toPlace, type GeoResult, type Place } from './placeQuery';

/**
 * The weather for one chosen place, from Open-Meteo — the same source and the same rules as
 * the Android `WeatherSource`. No key, no account.
 *
 * The place and the last reading are kept in localStorage, shared by the setup window and
 * every widget window, so one city serves every Castform on the desktop.
 */

export type { Place } from './placeQuery';

export interface SunWindow {
  rise: number;
  set: number;
}

export interface Reading {
  weather: Weather;
  isDay: boolean;
  takenAt: number;
  /** The place's offset from UTC, so its hour is its own and not this computer's. */
  utcOffsetSec: number | null;
  /** Two days of sunrise and sunset, epoch seconds, so day and night never go stale. */
  sun: SunWindow[];
}

interface Saved {
  place: Place | null;
  reading: Reading | null;
}

const KEY = 'pokewidget.weather.v1';

/** Weather does not change fast enough to ask more often than this. */
export const FRESH_FOR_MS = 60 * 60 * 1000;

function read(): Saved {
  try {
    const raw = localStorage.getItem(KEY);
    if (!raw) return { place: null, reading: null };
    const parsed = JSON.parse(raw) as Partial<Saved>;
    return { place: parsed.place ?? null, reading: parsed.reading ?? null };
  } catch {
    return { place: null, reading: null };
  }
}

function write(saved: Saved): void {
  try {
    localStorage.setItem(KEY, JSON.stringify(saved));
  } catch {
    // Nothing to do: this session still has the answer in memory via the caller.
  }
}

export const currentPlace = (): Place | null => read().place;
export const cachedReading = (): Reading | null => read().reading;

/** Choosing a place drops the old reading: it belongs to the place it was taken in. */
export function setPlace(place: Place | null): void {
  write({ place, reading: null });
}

export const isStale = (now = Date.now()): boolean => {
  const { place, reading } = read();
  return place !== null && (reading === null || now - reading.takenAt >= FRESH_FOR_MS);
};

// ---- The place's own clock -------------------------------------------------------

/** The hour, 0..23, in a place `offsetSec` from UTC; this computer's hour when unknown. */
export function hourAt(nowMs: number, offsetSec: number | null, deviceHour: number): number {
  if (offsetSec === null) return deviceHour;
  const localSec = Math.floor(nowMs / 1000) + offsetSec;
  return (((Math.floor(localSec / 3600) % 24) + 24) % 24);
}

/** Whether the sun is up, from the stored windows; `fallback` when they do not reach now. */
export function isDayAt(nowMs: number, sun: SunWindow[], fallback: boolean): boolean {
  if (!sun.length || sun.some((w) => w.set <= w.rise)) return fallback;
  const now = Math.floor(nowMs / 1000);
  if (now >= Math.max(...sun.map((w) => w.set))) return fallback;
  return sun.some((w) => now >= w.rise && now < w.set);
}

export function worldStateOf(reading: Reading | null, nowMs: number, deviceHour: number): WorldState {
  const hour = hourAt(nowMs, reading?.utcOffsetSec ?? null, deviceHour);
  return {
    weather: reading?.weather ?? 'cloudy',
    isDay: isDayAt(nowMs, reading?.sun ?? [], reading?.isDay ?? (hour >= 6 && hour <= 18)),
    hour,
  };
}

export const worldState = (now = new Date()): WorldState => worldStateOf(cachedReading(), now.getTime(), now.getHours());

/** "9:14 PM" in the chosen place, or null when its offset is not known yet. */
export function localTime(reading: Reading | null, now = Date.now()): string | null {
  if (reading?.utcOffsetSec == null) return null;
  return new Intl.DateTimeFormat(undefined, { hour: 'numeric', minute: '2-digit', timeZone: 'UTC' }).format(
    new Date(now + reading.utcOffsetSec * 1000),
  );
}

// ---- The network -----------------------------------------------------------------

async function getJson(url: string): Promise<unknown | null> {
  try {
    const response = await fetch(url);
    return response.ok ? await response.json() : null;
  } catch {
    return null;
  }
}

/** Fetches and stores a fresh reading for the chosen place. False when there was none. */
export async function refresh(): Promise<boolean> {
  const { place } = read();
  if (!place) return false;
  const body = (await getJson(
    'https://api.open-meteo.com/v1/forecast' +
      `?latitude=${place.latitude}&longitude=${place.longitude}` +
      '&current=weather_code,is_day&daily=sunrise,sunset&forecast_days=2&timezone=auto&timeformat=unixtime',
  )) as {
    utc_offset_seconds?: number;
    current?: { weather_code?: number; is_day?: number };
    daily?: { sunrise?: number[]; sunset?: number[] };
  } | null;
  const code = body?.current?.weather_code;
  if (typeof code !== 'number') return false;

  const rises = body?.daily?.sunrise ?? [];
  const sets = body?.daily?.sunset ?? [];
  const reading: Reading = {
    weather: weatherFromWmo(code),
    isDay: body?.current?.is_day !== 0,
    takenAt: Date.now(),
    utcOffsetSec: typeof body?.utc_offset_seconds === 'number' ? body.utc_offset_seconds : null,
    sun: rises.flatMap((rise, i) => {
      const set = sets[i];
      return typeof rise === 'number' && typeof set === 'number' ? [{ rise, set }] : [];
    }),
  };
  // The place may have changed while this was in flight; a reading for the old one is dropped.
  const now = read();
  if (now.place?.latitude !== place.latitude || now.place?.longitude !== place.longitude) return false;
  write({ place: now.place, reading });
  return true;
}

async function geocode(name: string): Promise<GeoResult[]> {
  const body = (await getJson(
    `https://geocoding-api.open-meteo.com/v1/search?name=${encodeURIComponent(name)}&count=20&format=json`,
  )) as { results?: Array<Record<string, unknown>> } | null;
  return (body?.results ?? []).flatMap((o) =>
    typeof o.name === 'string' && typeof o.latitude === 'number' && typeof o.longitude === 'number'
      ? [
          {
            name: o.name,
            latitude: o.latitude,
            longitude: o.longitude,
            admin1: typeof o.admin1 === 'string' ? o.admin1 : null,
            country: typeof o.country === 'string' ? o.country : null,
            countryCode: typeof o.country_code === 'string' ? o.country_code : null,
          },
        ]
      : [],
  );
}

/** City search, read several ways so "Richmond VA" finds Richmond, Virginia. */
export async function searchPlaces(query: string, limit = 8): Promise<Place[]> {
  let fallback: GeoResult[] | null = null;
  for (const attempt of attempts(query)) {
    const results = await geocode(attempt.name);
    if (!fallback && results.length) fallback = results;
    const fitting = filter(results, attempt.qualifier);
    if (fitting) return fitting.slice(0, limit).map(toPlace);
  }
  return (fallback ?? []).slice(0, limit).map(toPlace);
}

/**
 * One fix of where this computer is, from the browser. There is no free reverse geocoder
 * without a key, so the place is named by its coordinates. Rejects when location is
 * unavailable or refused — the caller points at search instead.
 */
export function locate(): Promise<Place> {
  return new Promise((resolve, reject) => {
    if (typeof navigator === 'undefined' || !navigator.geolocation) {
      reject(new Error('no geolocation'));
      return;
    }
    navigator.geolocation.getCurrentPosition(
      (position) => {
        const { latitude, longitude } = position.coords;
        resolve({ label: `Near ${latitude.toFixed(2)}°, ${longitude.toFixed(2)}°`, latitude, longitude });
      },
      reject,
      { enableHighAccuracy: false, timeout: 10_000, maximumAge: 30 * 60 * 1000 },
    );
  });
}

/** Lets every window hear about a new city or reading, like `store.onWidgetsChanged`. */
export function onWeatherChanged(listener: () => void): () => void {
  const handler = (event: StorageEvent) => {
    if (event.key === null || event.key === KEY) listener();
  };
  window.addEventListener('storage', handler);
  return () => window.removeEventListener('storage', handler);
}

export const announceWeather = (): void => {
  window.dispatchEvent(new StorageEvent('storage', { key: KEY }));
};
