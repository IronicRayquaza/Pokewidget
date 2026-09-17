import { render } from 'preact';
import { useEffect, useState } from 'preact/hooks';
import './styles.css';
import { loadCatalogs, type Catalogs } from './core/data';
import { getWidget, onWidgetsChanged, type PlacedWidget } from './core/store';
import { playCry, preloadCry } from './core/audio';
import { WidgetView } from './ui/WidgetView';

/**
 * A widget on its own, filling whatever window it is in.
 *
 * This is what a desktop widget window loads, and what the browser shows when a widget is
 * opened in its own tab. It follows the same settings the setup app writes, and redraws when
 * they change in another window.
 */
function WidgetWindow({ id }: { id: number }) {
  const [catalogs, setCatalogs] = useState<Catalogs | null>(null);
  const [widget, setWidget] = useState<PlacedWidget | undefined>(() => getWidget(id));
  const size = useWindowSize();

  useEffect(() => {
    loadCatalogs().then(setCatalogs).catch(() => setCatalogs(null));
  }, []);

  useEffect(() => onWidgetsChanged(() => setWidget(getWidget(id))), [id]);

  useEffect(() => {
    if (widget?.config.cryEnabled) preloadCry(widget.config.pokemonId, widget.config.legacyCry);
  }, [widget?.config.pokemonId, widget?.config.legacyCry, widget?.config.cryEnabled]);

  if (!catalogs || !widget) return null;

  return (
    <div
      data-tauri-drag-region
      style={{ width: '100vw', height: '100vh', display: 'grid', placeItems: 'center' }}
    >
      <WidgetView
        config={widget.config}
        catalogs={catalogs}
        width={size.width}
        height={size.height}
        onClick={() => {
          if (widget.config.cryEnabled) void playCry(widget.config.pokemonId, widget.config.legacyCry);
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
