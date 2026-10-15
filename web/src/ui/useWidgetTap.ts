import { useEffect, useRef, useState } from 'preact/hooks';
import type { Catalogs } from '../core/data';
import { covers, spriteUrl } from '../core/catalog';
import { appliesTo, formFor } from '../core/formRules';
import { announceChange, updateWidget, type WidgetConfig } from '../core/store';
import { aheadIds, aheadLooks, applyTap, pickSurprise, surpriseStillFits } from '../core/tap';
import { isStale, onWeatherChanged, refresh, worldState } from '../core/weather';
import { playCry, preloadCry } from '../core/audio';
import { openSetupWindow } from '../desktop';

/** How long an excited hop lasts; the same as the Android burst. */
const EXCITED_MS = 2200;

/** How often a live form looks at the clock again. Dusk is two hours long; this is plenty. */
const LIVE_FORM_TICK_MS = 15 * 60 * 1000;

/** Whether the widget's sprite set drew a Pokémon at all, front and plain. */
const coveredBy = (catalogs: Catalogs, config: WidgetConfig) => {
  const set = catalogs.set(config.setId);
  return (id: number) => Boolean(set && covers(set, id, false, false, false, null));
};

/**
 * The config as it should be drawn: the chosen Pokémon, or the form the real world calls for
 * when live form is on. Never written back — the widget keeps the Pokémon that was chosen.
 *
 * Looks again every quarter hour, when the window regains focus, and when another window
 * picks a city or fetches the sky; and fetches the sky itself when it has gone stale.
 */
export function useShownConfig(config: WidgetConfig): WidgetConfig {
  const live = config.liveForm && appliesTo(config.pokemonId);
  const [tick, setTick] = useState(0);

  useEffect(() => {
    if (!live) return;
    const bump = () => setTick((t) => t + 1);
    if (isStale()) void refresh().then(bump);
    const timer = window.setInterval(() => {
      if (isStale()) void refresh().then(bump);
      else bump();
    }, LIVE_FORM_TICK_MS);
    window.addEventListener('focus', bump);
    const stop = onWeatherChanged(bump);
    return () => {
      window.clearInterval(timer);
      window.removeEventListener('focus', bump);
      stop();
    };
  }, [live]);

  if (!live) return config;
  void tick;
  const formId = formFor(config.pokemonId, worldState());
  return formId === config.pokemonId ? config : { ...config, pokemonId: formId };
}

/**
 * Keeps the next "Surprise me" chosen and downloaded — its cry decoded and its sprite in the
 * browser cache — so the click that swaps it in has nothing to wait for.
 */
export function useSurprisePrep(widgetId: number, config: WidgetConfig, catalogs: Catalogs): void {
  const surprise = config.tapAction === 'surprise';
  useEffect(() => {
    if (!surprise) return;
    const covered = coveredBy(catalogs, config);
    let next = config.surpriseNext;
    if (!surpriseStillFits(next, config.surprisePool, covered, config.pokemonId)) {
      const picked = pickSurprise(config.surprisePool, covered, config.pokemonId);
      if (picked === null || picked === config.pokemonId) return;
      next = picked;
      updateWidget(widgetId, { surpriseNext: next });
      announceChange();
    }
    if (config.cryEnabled) preloadCry(next, config.legacyCry);
    const set = catalogs.set(config.setId);
    const url = set && spriteUrl(catalogs.sets, set, { setId: set.id, pokemonId: next, back: config.back, shiny: config.shiny });
    if (url) new Image().src = url;
  }, [
    surprise,
    widgetId,
    catalogs,
    config.setId,
    config.pokemonId,
    config.surpriseNext,
    config.surprisePool.join(','),
    config.cryEnabled,
    config.legacyCry,
    config.back,
    config.shiny,
  ]);
}

/**
 * Downloads everything the widget can show without anyone choosing again — all its picks,
 * every form Live form can choose, and the shiny or back sprite its click toggles to — so
 * neither a click nor a change of sky waits on the network. Cries are decoded and kept;
 * sprites land in the browser's cache, the same plain image request the widget makes to draw
 * them. Already-fetched ones cost nothing. `Prefetch.kt` on Android.
 */
export function useAheadPrep(config: WidgetConfig, catalogs: Catalogs): void {
  const ids = [...new Set([config.pokemonId, ...aheadIds(config)])];
  const looks = aheadLooks(config);
  useEffect(() => {
    const set = catalogs.set(config.setId);
    for (const id of ids) {
      // Both cry flavours are settled here, so a Pokémon with no Game Boy cry has its modern
      // one decoded before the click rather than found missing at it.
      if (config.cryEnabled) preloadCry(id, config.legacyCry);
      if (!set) continue;
      for (const look of looks) {
        // The widget's own Pokémon keeps its style and female choice; picks and forms drop
        // both when they arrive, as applyTap does.
        const own = id === config.pokemonId;
        const female = own && config.female;
        const style = own ? config.style : null;
        if (!covers(set, id, look.back, look.shiny, female, style)) continue;
        const url = spriteUrl(catalogs.sets, set, { setId: set.id, pokemonId: id, ...look, female, style });
        if (url) new Image().src = url;
      }
    }
  }, [
    catalogs,
    ids.join(','),
    JSON.stringify(looks),
    config.setId,
    config.female,
    config.style,
    config.cryEnabled,
    config.legacyCry,
  ]);
}

/**
 * The click handler for a widget, doing whatever its tap action says, and whether it is
 * mid-hop. `shown` is the config as drawn, so a live form cries as itself.
 */
export function useWidgetTap(
  widgetId: number,
  config: WidgetConfig,
  shown: WidgetConfig,
  catalogs: Catalogs,
  // The setup window's own preview has nothing to open.
  inSetup = false,
): { onClick: () => void; excited: boolean } {
  const [excited, setExcited] = useState(false);
  const timer = useRef<number | undefined>(undefined);
  useEffect(() => () => window.clearTimeout(timer.current), []);

  const onClick = () => {
    const result = applyTap(config, shown.pokemonId, coveredBy(catalogs, config));
    if (result.cryId !== null) void playCry(result.cryId, config.legacyCry);
    if (result.change) {
      updateWidget(widgetId, result.change);
      announceChange();
    }
    if (result.effect === 'open-app' && !inSetup) void openSetupWindow();
    if (result.effect === 'excite') {
      setExcited(true);
      window.clearTimeout(timer.current);
      timer.current = window.setTimeout(() => setExcited(false), EXCITED_MS);
    }
  };

  return { onClick, excited };
}
