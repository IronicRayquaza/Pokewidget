import { useEffect, useRef, useState } from 'preact/hooks';
import type { Catalogs } from '../core/data';
import { displayName } from '../core/catalog';
import { appliesTo, describeRule, formFor } from '../core/formRules';
import type { WidgetConfig } from '../core/store';
import {
  announceWeather,
  cachedReading,
  currentPlace,
  isDayAt,
  localTime,
  locate,
  onWeatherChanged,
  refresh,
  searchPlaces,
  setPlace,
  worldState,
  type Place,
  type Reading,
} from '../core/weather';

const SKY: Record<Reading['weather'], string> = { clear: 'Clear', cloudy: 'Cloudy', rain: 'Rain', snow: 'Snow' };

/** The chosen city and its last reading, kept in step across windows. */
function useWeather(): { place: Place | null; reading: Reading | null } {
  const [state, setState] = useState(() => ({ place: currentPlace(), reading: cachedReading() }));
  useEffect(() => onWeatherChanged(() => setState({ place: currentPlace(), reading: cachedReading() })), []);
  return state;
}

/**
 * "Live form", offered only for the few Pokémon with a real-world trigger, with the city it
 * follows right there — the desktop has no settings page to send anyone to.
 */
export function LiveFormSection({
  config,
  catalogs,
  change,
}: {
  config: WidgetConfig;
  catalogs: Catalogs;
  change: (patch: Partial<WidgetConfig>) => void;
}) {
  const weather = useWeather();
  const rule = describeRule(config.pokemonId);
  if (!rule || !appliesTo(config.pokemonId)) return null;
  const name = catalogs.entry(config.pokemonId)?.n ?? 'This Pokémon';
  const now = catalogs.entry(formFor(config.pokemonId, worldState()));

  return (
    <div class="card">
      <p class="section-title">Live form</p>
      <div class="row">
        <button aria-pressed={config.liveForm} onClick={() => change({ liveForm: !config.liveForm })}>
          Follow the real world
        </button>
      </div>
      <p class="caption">
        {name} {rule}. The widget keeps the Pokémon you chose — only its form changes.
      </p>
      {config.liveForm && (
        <>
          {now && <p class="caption">Right now: {displayName(now)}</p>}
          <WeatherPlace weather={weather} />
        </>
      )}
    </div>
  );
}

function WeatherPlace({ weather }: { weather: { place: Place | null; reading: Reading | null } }) {
  const [query, setQuery] = useState('');
  const [results, setResults] = useState<Place[]>([]);
  const [busy, setBusy] = useState<'locate' | 'check' | null>(null);
  const [searching, setSearching] = useState(false);
  const [message, setMessage] = useState<string | null>(null);
  const searchId = useRef(0);

  useEffect(() => {
    const trimmed = query.trim();
    if (trimmed.length < 2) {
      searchId.current++;
      setResults([]);
      setSearching(false);
      return;
    }
    const id = ++searchId.current;
    const timer = window.setTimeout(async () => {
      setSearching(true);
      const found = await searchPlaces(trimmed);
      if (id !== searchId.current) return;
      setResults(found);
      setSearching(false);
    }, 360);
    return () => window.clearTimeout(timer);
  }, [query]);

  const choose = async (place: Place | null) => {
    setPlace(place);
    setQuery('');
    setResults([]);
    setMessage(null);
    announceWeather();
    if (place) {
      setBusy('check');
      await refresh();
      setBusy(null);
      announceWeather();
    }
  };

  const useMyLocation = async () => {
    setBusy('locate');
    setMessage(null);
    try {
      await choose(await locate());
    } catch {
      setMessage("Couldn't tell where you are. Search for your city instead.");
    } finally {
      setBusy(null);
    }
  };

  const { place, reading } = weather;
  if (place) {
    const now = Date.now();
    const time = localTime(reading, now);
    return (
      <>
        <label class="field">Following</label>
        <div class="row">
          <strong>{place.label}</strong>
        </div>
        <p class="caption">
          {busy === 'check'
            ? 'Checking…'
            : reading
              ? `${SKY[reading.weather]}, ${isDayAt(now, reading.sun, reading.isDay) ? 'daytime' : 'night'}` +
                (time ? ` · ${time} in ${place.label.split(',')[0]}` : '')
              : 'Not checked yet'}
        </p>
        {/* Otherwise "Check now" reads as something that has to be pressed. Same line as Android. */}
        <p class="caption">Updates itself about once an hour while a widget uses Live form.</p>
        <div class="row" style={{ marginTop: 8 }}>
          <button
            disabled={busy !== null}
            onClick={async () => {
              setBusy('check');
              await refresh();
              setBusy(null);
              announceWeather();
            }}
          >
            Check now
          </button>
          <button onClick={() => void choose(null)}>Change city</button>
        </div>
      </>
    );
  }

  return (
    <>
      <label class="field">Whose weather</label>
      <div class="row">
        <button disabled={busy === 'locate'} onClick={() => void useMyLocation()}>
          {busy === 'locate' ? 'Finding you…' : 'Use my location'}
        </button>
      </div>
      <input
        type="search"
        style={{ marginTop: 10 }}
        value={query}
        placeholder="Search for a city"
        aria-label="Search for a city"
        onInput={(e) => setQuery((e.target as HTMLInputElement).value)}
      />
      {searching && <p class="caption">Searching…</p>}
      {!searching && query.trim().length >= 2 && results.length === 0 && (
        <p class="caption">No match. Try just the city name — “Richmond” — or a larger one nearby.</p>
      )}
      {results.length > 0 && (
        <div class="widget-list" style={{ marginTop: 8 }}>
          {results.map((r) => (
            <button key={`${r.latitude},${r.longitude}`} class="widget-row" onClick={() => void choose(r)}>
              {r.label}
            </button>
          ))}
        </div>
      )}
      {message && <p class="caption">{message}</p>}
      <p class="caption">
        Your location is read once, only when you click “Use my location”. Only the place is kept, and
        only on this computer. Until a city is set, the widget follows this computer's clock.
      </p>
    </>
  );
}
