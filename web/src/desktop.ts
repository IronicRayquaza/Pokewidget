/**
 * The handful of things that only exist in the desktop build.
 *
 * Tauri injects `window.__TAURI__` into its own windows; in a browser it is simply absent, and
 * everything here falls back to a plain browser window. Nothing else in the app has to know
 * which it is running in.
 */

interface TauriWindow {
  close(): Promise<void>;
  show?(): Promise<void>;
  unminimize?(): Promise<void>;
  setFocus?(): Promise<void>;
}

interface TauriWindowApi {
  WebviewWindow: (new (label: string, options: Record<string, unknown>) => TauriWindow) & {
    getByLabel?: (label: string) => Promise<TauriWindow | null>;
  };
  getCurrentWindow?: () => { close(): Promise<void> };
}

interface TauriApi {
  webviewWindow?: TauriWindowApi;
  window?: { getCurrentWindow?: () => { close(): Promise<void> } };
}

const tauri = (): TauriApi | undefined => (window as unknown as { __TAURI__?: TauriApi }).__TAURI__;

export const isDesktop = (): boolean => Boolean(tauri());

/**
 * Opens a widget in its own window: a frameless, transparent, always-on-top window on the
 * desktop, or an ordinary popup in a browser.
 */
export async function openWidgetWindow(id: number, width: number, height: number): Promise<void> {
  const api = tauri();
  const url = `widget.html?id=${id}`;
  if (!api?.webviewWindow) {
    window.open(`${url}`, `pokewidget-${id}`, `popup=yes,width=${width},height=${height}`);
    return;
  }
  const { WebviewWindow } = api.webviewWindow;
  new WebviewWindow(`widget-${id}`, {
    url,
    width,
    height,
    resizable: true,
    decorations: false,
    transparent: true,
    alwaysOnTop: true,
    skipTaskbar: true,
    shadow: false,
    title: 'PokéWidget',
  });
}

/**
 * Brings the setup window forward — the desktop's version of the Android "Open PokéWidget"
 * tap action. It is reopened if it was closed while widgets stayed on the desktop. In a
 * browser the setup page opens in a tab, reusing one already open.
 */
export async function openSetupWindow(): Promise<void> {
  const api = tauri();
  if (!api?.webviewWindow) {
    window.open('index.html', 'pokewidget-setup');
    return;
  }
  const { WebviewWindow } = api.webviewWindow;
  const main = await WebviewWindow.getByLabel?.('main').catch(() => null);
  if (main) {
    await main.unminimize?.().catch(() => undefined);
    await main.show?.().catch(() => undefined);
    await main.setFocus?.().catch(() => undefined);
    return;
  }
  new WebviewWindow('main', { url: 'index.html', title: 'PokeWidget', width: 1100, height: 820 });
}
