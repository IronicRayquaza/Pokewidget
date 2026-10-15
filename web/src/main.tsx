import { render } from 'preact';
import { useEffect, useState } from 'preact/hooks';
import './styles.css';
import { loadCatalogs, type Catalogs } from './core/data';
import { isDesktop } from './desktop';
import { DesktopSettings } from './ui/DesktopSettings';
import { HomeScreen } from './ui/HomeScreen';

/**
 * In a browser, the tab is the home screen and widgets sit on it. In the desktop app, widgets
 * sit on the real desktop, and this window is only where they are set up.
 */
function App() {
  const [catalogs, setCatalogs] = useState<Catalogs | null>(null);
  const [failed, setFailed] = useState(false);

  useEffect(() => {
    loadCatalogs().then(setCatalogs).catch(() => setFailed(true));
  }, []);

  if (failed) {
    return (
      <div class="wrap">
        <div class="card">
          <h2>Could not load the Pokémon catalogue</h2>
          <p class="caption">
            The data files are missing from this build. Run <code>npm run sync-data</code> in{' '}
            <code>web/</code> and reload.
          </p>
        </div>
      </div>
    );
  }

  if (!catalogs) {
    return (
      <div class="wrap">
        <p class="empty">Loading the Pokédex…</p>
      </div>
    );
  }

  return isDesktop() ? <DesktopSettings catalogs={catalogs} /> : <HomeScreen catalogs={catalogs} />;
}

render(<App />, document.getElementById('app')!);
