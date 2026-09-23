/**
 * The handful of things that only exist in the desktop build.
 *
 * Tauri injects `window.__TAURI__` into its own windows; in a browser it is simply absent, and
 * the browser build puts widgets on its own tab instead. Nothing else in the app has to know
 * which it is running in.
 */
import { listWidgets, setOnDesktop } from './core/store';

type ResizeDirection = 'SouthEast';

interface TauriWindow {
  label: string;
  close(): Promise<void>;
  show(): Promise<void>;
  unminimize(): Promise<void>;
  setFocus(): Promise<void>;
  startDragging(): Promise<void>;
  startResizeDragging(direction: ResizeDirection): Promise<void>;
}

interface TauriApi {
  webviewWindow?: {
    WebviewWindow: {
      new (label: string, options: Record<string, unknown>): TauriWindow;
      getByLabel(label: string): Promise<TauriWindow | null>;
    };
  };
  window?: { getCurrentWindow(): TauriWindow };
}

const tauri = (): TauriApi | undefined => (window as unknown as { __TAURI__?: TauriApi }).__TAURI__;

export const isDesktop = (): boolean => Boolean(tauri());

const labelFor = (id: number) => `widget-${id}`;

/** How big a widget's window is the first time it goes out. Afterwards the window keeps its own. */
const FIRST_SIZE = { width: 240, height: 240 };

/**
 * Puts a widget on the desktop: a window with no frame, no background, no taskbar button, that
 * sits on the wallpaper behind every app like a widget on a phone's home screen.
 *
 * Asking twice only brings the existing window back: a second window with the same label is
 * an error in Tauri, and one widget should never be on the desktop twice.
 */
export async function placeOnDesktop(id: number): Promise<void> {
  const api = tauri()?.webviewWindow;
  if (!api) return;
  setOnDesktop(id, true);
  const existing = await api.WebviewWindow.getByLabel(labelFor(id));
  if (existing) {
    await existing.show();
    return;
  }
  new api.WebviewWindow(labelFor(id), {
    url: `widget.html?id=${id}`,
    ...FIRST_SIZE,
    minWidth: 96,
    minHeight: 96,
    resizable: true,
    decorations: false,
    transparent: true,
    shadow: false,
    skipTaskbar: true,
    alwaysOnBottom: true,
    focus: false,
    title: 'PokéWidget',
  });
}

/** Takes a widget off the desktop. It stays in the list, ready to go back out. */
export async function removeFromDesktop(id: number): Promise<void> {
  setOnDesktop(id, false);
  const existing = await tauri()?.webviewWindow?.WebviewWindow.getByLabel(labelFor(id));
  await existing?.close();
}

/**
 * Brings back every widget that was on the desktop when the app last closed. Each window's
 * size and position come back with it, from the window-state plugin, keyed by its label.
 */
export async function restoreDesktopWidgets(): Promise<void> {
  if (!isDesktop()) return;
  for (const widget of listWidgets()) {
    if (widget.onDesktop) await placeOnDesktop(widget.id);
  }
}

/** Brings the settings window forward, from wherever it was hidden. */
export async function showSettings(): Promise<void> {
  const main = await tauri()?.webviewWindow?.WebviewWindow.getByLabel('main');
  if (!main) return;
  await main.show();
  await main.unminimize();
  await main.setFocus();
}

const current = (): TauriWindow | undefined => tauri()?.window?.getCurrentWindow();

/** Hands a drag in progress to the OS, which moves the whole window with the pointer. */
export const startMove = (): void => void current()?.startDragging();

/** Hands a drag on the resize grip to the OS, which resizes the window from its bottom right. */
export const startResize = (): void => void current()?.startResizeDragging('SouthEast');
