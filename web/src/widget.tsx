import { render } from 'preact';
import { useEffect, useState } from 'preact/hooks';
import './styles.css';
import { loadCatalogs, type Catalogs } from './core/data';
import { displayName } from './core/catalog';
import { announceChange, getWidget, onWidgetsChanged, requestEdit, setOnTop, type PlacedWidget } from './core/store';
import { playCry, preloadCry } from './core/audio';
import { applyLayer, removeFromDesktop, showSettings, startMove, startResize } from './desktop';
import { WidgetView } from './ui/WidgetView';
import { usePressOrDrag, WidgetChrome } from './ui/WidgetChrome';

/**
 * A widget on its own, filling the window it is in: what each desktop widget window loads.
 *
 * The window has no frame and no background, so all that shows on the desktop is the widget
 * itself. Dragging it anywhere moves the window, the grip resizes it, and it follows the
 * settings window's changes as they are made.
 */
function WidgetWindow({ id }: { id: number }) {
  const [catalogs, setCatalogs] = useState<Catalogs | null>(null);
  const [widget, setWidget] = useState<PlacedWidget | undefined>(() => getWidget(id));
  const size = useWindowSize();

  useEffect(() => {
    loadCatalogs().then(setCatalogs).catch(() => setCatalogs(null));
  }, []);

  useEffect(() => onWidgetsChanged(() => setWidget(getWidget(id))), [id]);

  // On top of every window, or down on the desktop: set when the page loads, and again
  // whenever it is changed, here or in the settings window.
  const onTop = Boolean(widget?.onTop);
  useEffect(() => {
    void applyLayer(onTop);
  }, [onTop]);

  useEffect(() => {
    if (widget?.config.cryEnabled) preloadCry(widget.config.pokemonId, widget.config.legacyCry);
  }, [widget?.config.pokemonId, widget?.config.legacyCry, widget?.config.cryEnabled]);

  const press = usePressOrDrag({
    onTap: () => {
      if (widget?.config.cryEnabled) void playCry(widget.config.pokemonId, widget.config.legacyCry);
    },
    onDragStart: (event) => {
      // The OS takes the pointer from here and moves the whole window with it.
      (event.currentTarget as HTMLElement | null)?.releasePointerCapture?.(event.pointerId);
      startMove();
    },
  });

  if (!catalogs || !widget) return null;
  const entry = catalogs.entry(widget.config.pokemonId);
  const name = entry ? displayName(entry) : 'Pokémon';

  return (
    <div class="placed desktop" aria-label={`${name} widget`} {...press} onDragStart={(event) => event.preventDefault()}>
      <WidgetView config={widget.config} catalogs={catalogs} width={size.width} height={size.height} />
      <WidgetChrome
        name={name}
        removeLabel="Take off the desktop"
        onEdit={() => {
          requestEdit(id);
          void showSettings();
        }}
        onRemove={() => void removeFromDesktop(id)}
        pinned={onTop}
        onPin={() => {
          setOnTop(id, !onTop);
          announceChange();
        }}
        onGripDown={(event) => {
          event.preventDefault();
          startResize();
        }}
      />
    </div>
  );
}

function useWindowSize() {
  const [size, setSize] = useState({ width: window.innerWidth, height: window.innerHeight });
  useEffect(() => {
    const onResize = () => setSize({ width: window.innerWidth, height: window.innerHeight });
    window.addEventListener('resize', onResize);
    return () => window.removeEventListener('resize', onResize);
  }, []);
  return size;
}

const id = Number(new URLSearchParams(window.location.search).get('id') ?? '1');
render(<WidgetWindow id={Number.isFinite(id) ? id : 1} />, document.getElementById('widget')!);
