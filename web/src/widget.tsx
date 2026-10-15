import { render } from 'preact';
import { useEffect, useState } from 'preact/hooks';
import './styles.css';
import { loadCatalogs, type Catalogs } from './core/data';
import { displayName } from './core/catalog';
import { announceChange, getWidget, onWidgetsChanged, requestEdit, setOnTop, type PlacedWidget } from './core/store';
import { preloadCry } from './core/audio';
import { applyLayer, removeFromDesktop, showSettings, startMove, startResize } from './desktop';
import { WidgetView } from './ui/WidgetView';
import { usePressOrDrag, WidgetChrome } from './ui/WidgetChrome';
import { useAheadPrep, useShownConfig, useSurprisePrep, useWidgetTap } from './ui/useWidgetTap';

/**
 * A widget on its own, filling the window it is in: what each desktop widget window loads.
 *
 * The window has no frame and no background, so all that shows on the desktop is the widget
 * itself. Dragging it anywhere moves the window, the grip resizes it, a click does its tap
 * action, and it follows the settings window's changes as they are made.
 */
function WidgetWindow({ id }: { id: number }) {
  const [catalogs, setCatalogs] = useState<Catalogs | null>(null);
  const [widget, setWidget] = useState<PlacedWidget | undefined>(() => getWidget(id));

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

  if (!catalogs || !widget) return null;
  return <DesktopWidget widget={widget} catalogs={catalogs} onTop={onTop} />;
}

function DesktopWidget({ widget, catalogs, onTop }: { widget: PlacedWidget; catalogs: Catalogs; onTop: boolean }) {
  const id = widget.id;
  const size = useWindowSize();
  // The same click as the Android widget: live form, every tap action, surprises.
  const shown = useShownConfig(widget.config);
  const tap = useWidgetTap(id, widget.config, shown, catalogs);
  useSurprisePrep(id, widget.config, catalogs);
  useAheadPrep(widget.config, catalogs);

  useEffect(() => {
    if (shown.cryEnabled) preloadCry(shown.pokemonId, shown.legacyCry);
  }, [shown.pokemonId, shown.legacyCry, shown.cryEnabled]);

  const press = usePressOrDrag({
    onTap: () => {
      if (widget.config.tapAction !== 'none') tap.onClick();
    },
    onDragStart: (event) => {
      // The OS takes the pointer from here and moves the whole window with it.
      (event.currentTarget as HTMLElement | null)?.releasePointerCapture?.(event.pointerId);
      startMove();
    },
  });

  const entry = catalogs.entry(widget.config.pokemonId);
  const name = entry ? displayName(entry) : 'Pokémon';

  return (
    <div class="placed desktop" aria-label={`${name} widget`} {...press} onDragStart={(event) => event.preventDefault()}>
      <WidgetView config={shown} catalogs={catalogs} width={size.width} height={size.height} excited={tap.excited} />
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
