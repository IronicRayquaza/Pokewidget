/**
 * The handful of things that only exist in the desktop build.
 *
 * Tauri injects `window.__TAURI__` into its own windows; in a browser it is simply absent, and
 * everything here falls back to a plain browser window. Nothing else in the app has to know
 * which it is running in.
 */

interface TauriWindowApi {
  WebviewWindow: new (label: string, options: Record<string, unknown>) => { close(): Promise<void> };
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
