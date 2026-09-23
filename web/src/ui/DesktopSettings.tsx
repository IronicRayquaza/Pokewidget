import { useEffect, useState } from 'preact/hooks';
import type { Catalogs } from '../core/data';
import { displayName } from '../core/catalog';
import {
  addWidget,
  announceChange,
  listWidgets,
  onEditRequested,
  onWidgetsChanged,
  removeWidget,
  updateWidget,
  type PlacedWidget,
  type WidgetConfig,
} from '../core/store';
import { placeOnDesktop, removeFromDesktop, restoreDesktopWidgets } from '../desktop';
import { Editor, type Panel } from './Editor';
import { WidgetView } from './WidgetView';

/**
 * The desktop app's settings window. The widgets themselves live out on the desktop, each in
 * a window of its own; this is where they are made, changed and sent out there.
 */
export function DesktopSettings({ catalogs }: { catalogs: Catalogs }) {
  const [widgets, setWidgets] = useState<PlacedWidget[]>(() => listWidgets());
  const [selectedId, setSelectedId] = useState<number | null>(() => listWidgets()[0]?.id ?? null);
  const [panel, setPanel] = useState<Panel>(null);

  const refresh = () => setWidgets(listWidgets());

  useEffect(() => onWidgetsChanged(refresh), []);
  // The ✎ on a desktop widget lands here, on that widget.
  useEffect(
    () =>
      onEditRequested((id) => {
        refresh();
        setPanel(null);
        setSelectedId(id);
      }),
    [],
  );
  useEffect(() => {
    void restoreDesktopWidgets();
  }, []);

  const selected = widgets.find((w) => w.id === selectedId) ?? widgets[0];

  const change = (patch: Partial<WidgetConfig>) => {
    if (!selected) return;
    updateWidget(selected.id, patch);
    refresh();
    announceChange();
  };

  const create = () => {
    const widget = addWidget();
    refresh();
    setSelectedId(widget.id);
    announceChange();
  };

  const toggleDesktop = async (widget: PlacedWidget) => {
    if (widget.onDesktop) await removeFromDesktop(widget.id);
    else await placeOnDesktop(widget.id);
    refresh();
  };

  return (
    <div class="wrap">
      <header class="top">
        <h1>
          Poké<span style={{ color: 'var(--red)' }}>Widget</span>
        </h1>
        <p>Animated Pokémon sprites, right on your desktop.</p>
      </header>

      <div class="columns">
        <div>
          <div class="card">
            <p class="section-title">Your widgets</p>
            <div class="widget-list">
              {widgets.map((widget) => (
                <div
                  key={widget.id}
                  class="widget-row"
                  role="button"
                  tabIndex={0}
                  aria-pressed={widget.id === selected?.id}
                  onClick={() => setSelectedId(widget.id)}
                  onKeyDown={(e) => {
                    if (e.key === 'Enter' || e.key === ' ') setSelectedId(widget.id);
                  }}
                >
                  <div style={{ width: 56, height: 56, display: 'grid', placeItems: 'center' }}>
                    <WidgetView config={widget.config} catalogs={catalogs} width={56} height={56} />
                  </div>
                  <div class="who">
                    <strong>{displayName(catalogs.entry(widget.config.pokemonId) ?? { i: 0, n: '—', d: 0, g: 0 })}</strong>
                    <span class="caption">
                      {catalogs.set(widget.config.setId)?.label ?? 'Unknown set'}
                      {widget.onDesktop ? ' · On the desktop' : ''}
                    </span>
                  </div>
                </div>
              ))}
              {widgets.length === 0 && <p class="empty">No widgets yet.</p>}
            </div>
            <div class="row" style={{ marginTop: 14 }}>
              <button class="primary square" onClick={create}>
                + New widget
              </button>
            </div>
          </div>
        </div>

        <div>
          {!selected && (
            <div class="card">
              <p class="empty">Make a widget to get started.</p>
            </div>
          )}

          {selected && (
            <Editor
              key={selected.id}
              catalogs={catalogs}
              widget={selected}
              panel={panel}
              setPanel={setPanel}
              change={change}
              showPreview
              actions={
                <button
                  class={selected.onDesktop ? undefined : 'primary'}
                  onClick={() => void toggleDesktop(selected)}
                >
                  {selected.onDesktop ? 'Take off the desktop' : 'Put on the desktop'}
                </button>
              }
              onRemove={async () => {
                await removeFromDesktop(selected.id);
                removeWidget(selected.id);
                const rest = listWidgets();
                setWidgets(rest);
                setSelectedId(rest[0]?.id ?? null);
                announceChange();
              }}
            />
          )}
        </div>
      </div>
    </div>
  );
}
