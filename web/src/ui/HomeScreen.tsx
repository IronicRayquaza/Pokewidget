import { useEffect, useLayoutEffect, useRef, useState } from 'preact/hooks';
import type { Catalogs } from '../core/data';
import { displayName } from '../core/catalog';
import { arrange, defaultSize, GAP, nextFreeSpot, type Rect } from '../core/homeScreen';
import {
  addWidget,
  announceChange,
  effectiveScene,
  listWidgets,
  onWidgetsChanged,
  placeOnPage,
  removeWidget,
  restoreWidget,
  updateWidget,
  type PlacedWidget,
  type WidgetConfig,
} from '../core/store';
import { canPlayCries } from '../core/audio';
import { Editor, type Panel } from './Editor';
import { PlacedWidgetFrame } from './PlacedWidgetFrame';

/** How long the Undo for a removed widget stays up. */
const UNDO_MS = 6000;

/**
 * The browser build: the tab itself is the home screen.
 *
 * Widgets sit straight on the page, each at the spot it was dragged to, with nothing behind
 * them unless their own Background setting asks for it. Editing one slides a sheet in from the
 * side rather than leaving the page, so the widget being changed stays in view as its preview.
 */
export function HomeScreen({ catalogs }: { catalogs: Catalogs }) {
  const [widgets, setWidgets] = useState<PlacedWidget[]>(() => listWidgets());
  const [editingId, setEditingId] = useState<number | null>(null);
  const [panel, setPanel] = useState<Panel>(null);
  const [removed, setRemoved] = useState<PlacedWidget | null>(null);
  const canvasRef = useRef<HTMLDivElement>(null);
  const canvas = useCanvasSize(canvasRef);

  const refresh = () => setWidgets(listWidgets());
  useEffect(() => onWidgetsChanged(refresh), []);

  // Widgets made before the home screen existed, or in another tab, get a spot of their own.
  useEffect(() => {
    if (!canvas.width) return;
    const unplaced = widgets.filter((w) => !w.page);
    if (unplaced.length === 0) return;
    const taken = widgets.flatMap((w) => (w.page ? [w.page] : []));
    for (const widget of unplaced) {
      const spot = nextFreeSpot(taken, defaultSize(effectiveScene(widget.config)), canvas.width);
      placeOnPage(widget.id, spot);
      taken.push(spot);
    }
    refresh();
  }, [widgets, canvas.width]);

  useEffect(() => {
    if (!removed) return;
    const timer = setTimeout(() => setRemoved(null), UNDO_MS);
    return () => clearTimeout(timer);
  }, [removed]);

  const editing = widgets.find((w) => w.id === editingId);

  const closeEditor = () => {
    const id = editingId;
    setEditingId(null);
    setPanel(null);
    // Focus goes back to the widget that was being edited, not to the top of the page.
    if (id !== null) requestAnimationFrame(() => document.getElementById(`widget-${id}`)?.focus());
  };

  useEffect(() => {
    if (editingId === null) return;
    const onKey = (event: KeyboardEvent) => {
      if (event.key === 'Escape') closeEditor();
    };
    document.addEventListener('keydown', onKey);
    return () => document.removeEventListener('keydown', onKey);
  }, [editingId]);

  const create = () => {
    const widget = addWidget();
    const taken = listWidgets().flatMap((w) => (w.page ? [w.page] : []));
    placeOnPage(widget.id, nextFreeSpot(taken, defaultSize('solo'), canvas.width || window.innerWidth));
    refresh();
    announceChange();
    setPanel(null);
    setEditingId(widget.id);
  };

  const remove = (widget: PlacedWidget) => {
    removeWidget(widget.id);
    if (editingId === widget.id) closeEditor();
    setRemoved(widget);
    refresh();
    announceChange();
  };

  const undo = () => {
    if (!removed) return;
    restoreWidget(removed);
    setRemoved(null);
    refresh();
    announceChange();
  };

  const change = (patch: Partial<WidgetConfig>) => {
    if (!editing) return;
    updateWidget(editing.id, patch);
    refresh();
    announceChange();
  };

  const commit = (id: number, rect: Rect) => {
    placeOnPage(id, rect);
    refresh();
    announceChange();
  };

  const onPage = widgets.filter((w): w is PlacedWidget & { page: Rect } => Boolean(w.page));
  const drawn = canvas.width > 0 ? arrange(onPage.map((w) => w.page), canvas.width) : [];
  // `arrange` keeps the order it was given, so each drawn spot belongs to the widget at its index.
  const spots = drawn.flatMap((rect, index) => {
    const widget = onPage[index];
    return widget ? [{ widget, rect }] : [];
  });
  // The page grows downwards to hold the lowest widget, with room to drag one lower still.
  const lowest = drawn.reduce((bottom, r) => Math.max(bottom, r.y + r.height), 0);
  const height = Math.max(canvas.height, lowest + defaultSize('solo').height + GAP);
  const editingName = editing ? displayName(catalogs.entry(editing.config.pokemonId) ?? { i: 0, n: 'Pokémon', d: 0, g: 0 }) : '';

  return (
    <div class="home-page" data-editing={editing ? true : undefined}>
      <header class="home-bar">
        <h1>
          Poké<span style={{ color: 'var(--red)' }}>Widget</span>
        </h1>
        <p class="caption">Drag a widget anywhere on this tab. Click it to hear its cry.</p>
        <span style={{ flex: 1 }} />
        <button class="primary square" onClick={create}>
          + New widget
        </button>
      </header>

      {!canPlayCries() && (
        <p class="caption home-note">
          This browser cannot play Ogg audio, so cries are silent here. Chrome, Firefox and the desktop app all
          play them.
        </p>
      )}

      <div
        ref={canvasRef}
        class="home"
        style={{ height: `${height}px` }}
        onPointerDown={(event) => {
          if (event.target === event.currentTarget && editingId !== null) closeEditor();
        }}
      >
        {spots.map(({ widget, rect }) => (
            <PlacedWidgetFrame
              key={widget.id}
              widget={widget}
              rect={rect}
              canvas={{ width: canvas.width, height }}
              catalogs={catalogs}
              selected={widget.id === editingId}
              onCommit={(next) => commit(widget.id, next)}
              onEdit={() => {
                setPanel(null);
                setEditingId(widget.id);
              }}
              onRemove={() => remove(widget)}
            />
        ))}

        {widgets.length === 0 && (
          <div class="card home-empty">
            <p class="section-title">Your home screen is empty</p>
            <p class="caption">Widgets you make sit right here on this tab, wherever you drag them.</p>
            <div class="row" style={{ marginTop: 14 }}>
              <button class="primary square" onClick={create}>
                + New widget
              </button>
            </div>
          </div>
        )}
      </div>

      {editing && (
        <aside class="sheet" aria-labelledby="sheet-title">
          <div class="sheet-head">
            <h2 id="sheet-title">Edit {editingName}</h2>
            <span style={{ flex: 1 }} />
            <SheetDone onClose={closeEditor} />
          </div>
          <Editor
            key={editing.id}
            catalogs={catalogs}
            widget={editing}
            panel={panel}
            setPanel={setPanel}
            change={change}
            onRemove={() => remove(editing)}
            showPreview={false}
          />
        </aside>
      )}

      <div class="toast-slot" role="status" aria-live="polite">
        {removed && (
          <div class="toast">
            <span>
              Removed {displayName(catalogs.entry(removed.config.pokemonId) ?? { i: 0, n: 'Pokémon', d: 0, g: 0 })}
            </span>
            <button onClick={undo}>Undo</button>
          </div>
        )}
      </div>
    </div>
  );
}

/** The sheet's close button, which takes focus when the sheet opens so the keyboard lands in it. */
function SheetDone({ onClose }: { onClose: () => void }) {
  const ref = useRef<HTMLButtonElement>(null);
  useEffect(() => ref.current?.focus(), []);
  return (
    <button ref={ref} onClick={onClose}>
      Done
    </button>
  );
}

/** The home screen's width, and the height of the window below its top. */
function useCanvasSize(ref: { current: HTMLDivElement | null }) {
  const [size, setSize] = useState({ width: 0, height: 0 });
  useLayoutEffect(() => {
    const element = ref.current;
    if (!element) return;
    const measure = () =>
      setSize({
        width: element.clientWidth,
        height: Math.max(0, window.innerHeight - element.getBoundingClientRect().top - window.scrollY),
      });
    measure();
    const observer = new ResizeObserver(measure);
    observer.observe(element);
    window.addEventListener('resize', measure);
    return () => {
      observer.disconnect();
      window.removeEventListener('resize', measure);
    };
  }, []);
  return size;
}
