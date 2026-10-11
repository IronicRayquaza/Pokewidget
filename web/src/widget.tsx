import { render } from 'preact';
import { useEffect, useState } from 'preact/hooks';
import './styles.css';
import { loadCatalogs, type Catalogs } from './core/data';
import { getWidget, onWidgetsChanged, type PlacedWidget } from './core/store';
import { preloadCry } from './core/audio';
import { WidgetView } from './ui/WidgetView';
import { useShownConfig, useSurprisePrep, useWidgetTap } from './ui/useWidgetTap';

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

  useEffect(() => {
    loadCatalogs().then(setCatalogs).catch(() => setCatalogs(null));
  }, []);

  useEffect(() => onWidgetsChanged(() => setWidget(getWidget(id))), [id]);

  if (!catalogs || !widget) return null;
  return <PlacedWidgetView widget={widget} catalogs={catalogs} />;
}

function PlacedWidgetView({ widget, catalogs }: { widget: PlacedWidget; catalogs: Catalogs }) {
  const size = useWindowSize();
  const shown = useShownConfig(widget.config);
  const { onClick, excited } = useWidgetTap(widget.id, widget.config, shown, catalogs);
  useSurprisePrep(widget.id, widget.config, catalogs);

  useEffect(() => {
    if (shown.cryEnabled) preloadCry(shown.pokemonId, shown.legacyCry);
  }, [shown.pokemonId, shown.legacyCry, shown.cryEnabled]);

  return (
    <div
      data-tauri-drag-region
      style={{ width: '100vw', height: '100vh', display: 'grid', placeItems: 'center' }}
    >
      <WidgetView
        config={shown}
        catalogs={catalogs}
        width={size.width}
        height={size.height}
        excited={excited}
        onClick={widget.config.tapAction === 'none' ? undefined : onClick}
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
