import { useRef, useState } from 'preact/hooks';
import type { Catalogs } from '../core/data';
import { displayName } from '../core/catalog';
import { clampRect, MIN_SIZE, type Rect } from '../core/homeScreen';
import type { PlacedWidget } from '../core/store';
import { playCry } from '../core/audio';
import { WidgetView } from './WidgetView';
import { usePressOrDrag, WidgetChrome } from './WidgetChrome';

const STEP = 8;
const BIG_STEP = 32;

/**
 * One widget on the browser tab's home screen, at its own spot.
 *
 * Click it and it cries; drag it and it moves; the grip in its corner resizes it. Nothing is
 * drawn around it but what its own Background setting asks for.
 */
export function PlacedWidgetFrame({
  widget,
  rect,
  canvas,
  catalogs,
  selected,
  onCommit,
  onEdit,
  onRemove,
}: {
  widget: PlacedWidget;
  /** Where to draw it: its saved spot, already kept on screen. */
  rect: Rect;
  canvas: { width: number; height: number };
  catalogs: Catalogs;
  selected: boolean;
  onCommit: (rect: Rect) => void;
  onEdit: () => void;
  onRemove: () => void;
}) {
  // While a drag is in progress the widget follows the pointer here, and is saved once, on release.
  const [live, setLive] = useState<Rect | null>(null);
  const origin = useRef(rect);
  const shown = live ?? rect;
  const entry = catalogs.entry(widget.config.pokemonId);
  const name = entry ? displayName(entry) : 'Pokémon';

  const keep = (next: Rect) => clampRect(next, canvas.width, canvas.height);

  const move = usePressOrDrag({
    onTap: () => {
      if (widget.config.cryEnabled) void playCry(widget.config.pokemonId, widget.config.legacyCry);
    },
    onDragStart: () => {
      origin.current = rect;
      setLive(rect);
    },
    onDragMove: (dx, dy) => setLive(keep({ ...origin.current, x: origin.current.x + dx, y: origin.current.y + dy })),
    onDragEnd: (dx, dy) => {
      setLive(null);
      onCommit(keep({ ...origin.current, x: origin.current.x + dx, y: origin.current.y + dy }));
    },
  });

  const resizeFrom = (down: PointerEvent) => {
    const grip = down.currentTarget as HTMLElement;
    grip.setPointerCapture(down.pointerId);
    const start = rect;
    const sized = (event: PointerEvent) =>
      keep({
        ...start,
        width: Math.max(MIN_SIZE, start.width + event.clientX - down.clientX),
        height: Math.max(MIN_SIZE, start.height + event.clientY - down.clientY),
      });
    const onMove = (event: PointerEvent) => setLive(sized(event));
    const onUp = (event: PointerEvent) => {
      grip.removeEventListener('pointermove', onMove);
      grip.removeEventListener('pointerup', onUp);
      grip.removeEventListener('pointercancel', onUp);
      setLive(null);
      onCommit(sized(event));
    };
    grip.addEventListener('pointermove', onMove);
    grip.addEventListener('pointerup', onUp);
    grip.addEventListener('pointercancel', onUp);
  };

  const onKeyDown = (event: KeyboardEvent) => {
    if (event.target !== event.currentTarget) return;
    if (event.key === 'Enter') {
      event.preventDefault();
      onEdit();
      return;
    }
    if (event.key === 'Delete' || event.key === 'Backspace') {
      event.preventDefault();
      onRemove();
      return;
    }
    const step = event.shiftKey ? BIG_STEP : STEP;
    const arrows: Record<string, [number, number]> = {
      ArrowLeft: [-step, 0],
      ArrowRight: [step, 0],
      ArrowUp: [0, -step],
      ArrowDown: [0, step],
    };
    const delta = arrows[event.key];
    if (!delta) return;
    event.preventDefault();
    // Alt resizes instead of moving, so the grip has a keyboard equivalent.
    onCommit(
      keep(
        event.altKey
          ? { ...rect, width: rect.width + delta[0], height: rect.height + delta[1] }
          : { ...rect, x: rect.x + delta[0], y: rect.y + delta[1] },
      ),
    );
  };

  return (
    <div
      id={`widget-${widget.id}`}
      class="placed"
      data-selected={selected || undefined}
      data-moving={live ? true : undefined}
      role="group"
      tabIndex={0}
      aria-label={`${name} widget`}
      aria-keyshortcuts="Enter Delete ArrowUp ArrowDown ArrowLeft ArrowRight"
      title="Drag to move · Enter to edit · Alt+arrows to resize"
      style={{ left: `${shown.x}px`, top: `${shown.y}px`, width: `${shown.width}px`, height: `${shown.height}px` }}
      onKeyDown={onKeyDown}
      onDragStart={(event) => event.preventDefault()}
      {...move}
    >
      <WidgetView config={widget.config} catalogs={catalogs} width={shown.width} height={shown.height} />
      <WidgetChrome name={name} removeLabel="Remove" onEdit={onEdit} onRemove={onRemove} onGripDown={resizeFrom} />
    </div>
  );
}
