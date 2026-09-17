import type { FillMode, Scene, TrainerSide } from './scene';

/**
 * A widget's settings, and where they live.
 *
 * The same fields as `data/WidgetConfig.kt`, minus the ones that only mean something on a
 * home screen (animation smoothness is the launcher's memory budget talking, and the tap
 * action is always "play the cry" here). Everything optional is off by default, so a widget
 * nobody has touched is a plain Pokémon on nothing — the rule the Android app follows too.
 */
export interface WidgetConfig {
  pokemonId: number;
  setId: string;
  shiny: boolean;
  back: boolean;
  female: boolean;
  style: string | null;
  flipHorizontal: boolean;

  showBackground: boolean;
  backgroundColor: string;
  cornerRadius: number;
  backgroundId: string | null;

  trainerId: string | null;
  trainerPose: 'front' | 'back';
  trainerSide: TrainerSide;
  trainerFlip: boolean;
  scene: Scene;

  fill: FillMode;
  cryEnabled: boolean;
  legacyCry: boolean;
}

export interface PlacedWidget {
  id: number;
  config: WidgetConfig;
  /** Desktop only: the last size and position of this widget's own window. */
  window?: { width: number; height: number; x?: number; y?: number };
}

export const DEFAULT_CONFIG: WidgetConfig = {
  pokemonId: 25,
  setId: 'other_showdown',
  shiny: false,
  back: false,
  female: false,
  style: null,
  flipHorizontal: false,
  showBackground: false,
  backgroundColor: '#1b1f27',
  cornerRadius: 20,
  backgroundId: null,
  trainerId: null,
  trainerPose: 'front',
  trainerSide: 'left',
  trainerFlip: false,
  scene: 'solo',
  fill: 'fit',
  cryEnabled: true,
  legacyCry: true,
};

/** A scene needs a trainer to be anything but solo; see `WidgetConfig.effectiveScene`. */
export const effectiveScene = (config: WidgetConfig): Scene => (config.trainerId ? config.scene : 'solo');

const KEY = 'pokewidget.widgets.v1';

interface Saved {
  version: 1;
  nextId: number;
  widgets: PlacedWidget[];
}

const EMPTY: Saved = { version: 1, nextId: 1, widgets: [] };

/**
 * Reading and writing can both throw — a private window, storage turned off, a quota — and
 * none of that is worth losing the page over, so a failure reads as "no widgets yet".
 */
function read(): Saved {
  try {
    const raw = localStorage.getItem(KEY);
    if (!raw) return { ...EMPTY };
    const parsed = JSON.parse(raw) as Saved;
    if (parsed.version !== 1 || !Array.isArray(parsed.widgets)) return { ...EMPTY };
    // Fields added after a widget was saved fall back to their defaults.
    parsed.widgets = parsed.widgets.map((w) => ({ ...w, config: { ...DEFAULT_CONFIG, ...w.config } }));
    return parsed;
  } catch {
    return { ...EMPTY };
  }
}

function write(saved: Saved): void {
  try {
    localStorage.setItem(KEY, JSON.stringify(saved));
  } catch {
    // Nothing to do: the widgets on screen keep working for this session.
  }
}

export const listWidgets = (): PlacedWidget[] => read().widgets;

export const getWidget = (id: number): PlacedWidget | undefined => read().widgets.find((w) => w.id === id);

export function addWidget(config: Partial<WidgetConfig> = {}): PlacedWidget {
  const saved = read();
  const widget: PlacedWidget = { id: saved.nextId, config: { ...DEFAULT_CONFIG, ...config } };
  saved.widgets.push(widget);
  saved.nextId += 1;
  write(saved);
  return widget;
}

export function updateWidget(id: number, change: Partial<WidgetConfig>): PlacedWidget | undefined {
  const saved = read();
  const widget = saved.widgets.find((w) => w.id === id);
  if (!widget) return undefined;
  widget.config = { ...widget.config, ...change };
  write(saved);
  return widget;
}

export function removeWidget(id: number): void {
  const saved = read();
  saved.widgets = saved.widgets.filter((w) => w.id !== id);
  write(saved);
}

export function rememberWindow(id: number, window: PlacedWidget['window']): void {
  const saved = read();
  const widget = saved.widgets.find((w) => w.id === id);
  if (!widget) return;
  widget.window = window;
  write(saved);
}

/**
 * Widgets are edited in one window and shown in others, so every window listens for the
 * storage event the browser fires when another one writes.
 */
export function onWidgetsChanged(listener: () => void): () => void {
  const handler = (event: StorageEvent) => {
    if (event.key === null || event.key === KEY) listener();
  };
  window.addEventListener('storage', handler);
  return () => window.removeEventListener('storage', handler);
}

/** Tells other windows to re-read, including this one's own widget views. */
export const announceChange = (): void => {
  window.dispatchEvent(new StorageEvent('storage', { key: KEY }));
};
