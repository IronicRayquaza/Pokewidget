import { useRef } from 'preact/hooks';

/** How far a pointer travels before a press becomes a drag, so a click still plays the cry. */
const DRAG_THRESHOLD = 4;

/** How long a finger rests on a widget before it lifts, as on an Android home screen. */
const LONG_PRESS_MS = 400;

interface Press {
  pointerId: number;
  x: number;
  y: number;
  armed: boolean;
  dragging: boolean;
  timer?: ReturnType<typeof setTimeout>;
}

/**
 * Tells a tap from a drag on one element.
 *
 * With a mouse, moving a few pixels starts a drag and anything less is a tap. With a finger,
 * the widget first has to be held for a moment, so brushing past one never moves it.
 */
export function usePressOrDrag({
  onTap,
  onDragStart,
  onDragMove,
  onDragEnd,
}: {
  onTap: () => void;
  onDragStart: (event: PointerEvent) => void;
  onDragMove?: (dx: number, dy: number) => void;
  onDragEnd?: (dx: number, dy: number) => void;
}) {
  const press = useRef<Press | null>(null);

  const clear = () => {
    if (press.current?.timer) clearTimeout(press.current.timer);
    press.current = null;
  };

  const onPointerDown = (event: PointerEvent) => {
    if (event.button !== 0) return;
    clear();
    (event.currentTarget as HTMLElement).setPointerCapture(event.pointerId);
    const touch = event.pointerType === 'touch';
    const state: Press = { pointerId: event.pointerId, x: event.clientX, y: event.clientY, armed: !touch, dragging: false };
    if (touch) {
      state.timer = setTimeout(() => {
        state.armed = true;
        state.dragging = true;
        navigator.vibrate?.(10);
        onDragStart(event);
      }, LONG_PRESS_MS);
    }
    press.current = state;
  };

  const onPointerMove = (event: PointerEvent) => {
    const state = press.current;
    if (!state || state.pointerId !== event.pointerId) return;
    const dx = event.clientX - state.x;
    const dy = event.clientY - state.y;
    if (state.dragging) {
      onDragMove?.(dx, dy);
      return;
    }
    if (Math.hypot(dx, dy) < DRAG_THRESHOLD) return;
    if (!state.armed) {
      // A finger that moves before the long press lands was never meant for this widget.
      clear();
      return;
    }
    state.dragging = true;
    onDragStart(event);
  };

  const onPointerUp = (event: PointerEvent) => {
    const state = press.current;
    if (!state || state.pointerId !== event.pointerId) return;
    clear();
    if (state.dragging) onDragEnd?.(event.clientX - state.x, event.clientY - state.y);
    else onTap();
  };

  const onPointerCancel = () => {
    const state = press.current;
    clear();
    if (state?.dragging) onDragEnd?.(0, 0);
  };

  return { onPointerDown, onPointerMove, onPointerUp, onPointerCancel };
}

/**
 * The controls a widget shows only while it is hovered, focused or being edited: edit,
 * remove, and a grip in the corner to resize by. A desktop widget also gets a pin, to float
 * it above every window or put it back down on the desktop. The rest of the time a widget has no
 * chrome at all, like one on a phone.
 */
export function WidgetChrome({
  name,
  removeLabel,
  onEdit,
  onRemove,
  onGripDown,
  pinned,
  onPin,
}: {
  name: string;
  removeLabel: string;
  onEdit: () => void;
  onRemove: () => void;
  onGripDown: (event: PointerEvent) => void;
  /** Desktop only: whether the widget floats above every window. */
  pinned?: boolean;
  onPin?: () => void;
}) {
  // A press on a control must not also start moving the widget underneath it.
  const stop = (event: Event) => event.stopPropagation();
  return (
    <div class="placed-chrome">
      <div class="placed-actions">
        {onPin && (
          <button
            class="chip"
            aria-pressed={pinned}
            aria-label={`Keep ${name} on top of other windows`}
            title={pinned ? 'On top of everything · click to put it back on the desktop' : 'Keep on top of other windows'}
            onPointerDown={stop}
            onClick={onPin}
          >
            <svg viewBox="0 0 16 16" aria-hidden="true">
              <path d="M6 2h4M7 2v4L4.5 9h7L9 6V2M8 9v5" />
            </svg>
          </button>
        )}
        <button class="chip" aria-label={`Edit ${name}`} title="Edit" onPointerDown={stop} onClick={onEdit}>
          <svg viewBox="0 0 16 16" aria-hidden="true">
            <path d="M10.5 2.5l3 3L6 13H3v-3z" />
          </svg>
        </button>
        <button class="chip" aria-label={`${removeLabel} ${name}`} title={removeLabel} onPointerDown={stop} onClick={onRemove}>
          <svg viewBox="0 0 16 16" aria-hidden="true">
            <path d="M4 4l8 8M12 4l-8 8" />
          </svg>
        </button>
      </div>
      <span
        class="grip"
        aria-hidden="true"
        title="Drag to resize"
        onPointerDown={(event) => {
          event.stopPropagation();
          onGripDown(event);
        }}
      />
    </div>
  );
}
