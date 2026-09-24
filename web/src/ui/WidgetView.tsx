import { useEffect, useRef, useState } from 'preact/hooks';
import type { Catalogs } from '../core/data';
import { backgroundById, spriteUrls } from '../core/catalog';
import { withProxy } from '../core/imageSource';
import { IDLE_STYLES, idleFrames, idleReach, resolveIdle, type IdleFrame } from '../core/idle';
import { trainerArt, type TrainerArt } from '../core/trainerArt';
import {
  battleFrame,
  bottom,
  displayScale,
  fit,
  layout,
  right,
  sceneryBox,
  FILL_MULTIPLE,
  OPEN_STAGE,
  type Box,
} from '../core/scene';
import { effectiveScene, type WidgetConfig } from '../core/store';
import { FallbackImg, useLoadedImage } from './images';

/**
 * One widget, drawn the way the Android renderer draws it — same layout, same sizes, same
 * crop — but out of plain elements rather than bitmaps.
 *
 * Nothing decodes a GIF here: a browser animates one correctly on its own, including the
 * frame disposal that Android needed its own decoder for. That also keeps sprites on hosts
 * that send no CORS header (veekun) usable, since they are only ever displayed.
 */
export function WidgetView({
  config,
  catalogs,
  width,
  height,
  onClick,
}: {
  config: WidgetConfig;
  catalogs: Catalogs;
  width: number;
  height: number;
  onClick?: () => void;
}) {
  const set = catalogs.set(config.setId);
  const spriteSources = set
    ? spriteUrls(catalogs.sets, set, {
        setId: set.id,
        pokemonId: config.pokemonId,
        back: config.back,
        shiny: config.shiny,
        female: config.female,
        style: config.style,
      })
    : [];

  // The first host that answers, and the sprite's own pixel size from it.
  const natural = useLoadedImage(withProxy(spriteSources));
  const sprite = natural?.src ?? null;
  const trainer = useTrainerArt(catalogs, config);
  const background = backgroundById(catalogs.backgrounds, config.backgroundId);

  const scene = trainer ? effectiveScene(config) : 'solo';
  const framed = background ? battleFrame(background, width, height) : null;
  const stage = scene === 'battle' && framed ? framed.stage : OPEN_STAGE;
  const places = layout(scene, config.trainerSide, width, height, stage);
  const mirrorScene = scene === 'battle' && config.trainerSide === 'right';

  // A still set moves by itself: the generated idle loop, as on the Android widget.
  const idle = set && !set.animated ? idleFrames(config.idleStyle, set.id) : null;
  const idleInterval = set ? IDLE_STYLES[resolveIdle(config.idleStyle, set.id)].intervalMs : 0;

  const spriteScale = (() => {
    if (!natural) return 0;
    // Leave room for the widest pose, so a breath does not clip at a snug widget's edges.
    const reach = idle ? idleReach(idle) : { widest: 1, tallest: 1 };
    return displayScale(
      Math.max(1, Math.round(natural.width * reach.widest)),
      Math.max(1, Math.round(natural.height * reach.tallest)),
      places.pokemon.width,
      places.pokemon.height,
      FILL_MULTIPLE[config.fill],
      config.fill === 'true-size' ? set?.referencePx ?? null : null,
    );
  })();

  const spriteRef = useRef<HTMLImageElement>(null);
  useIdleLoop(spriteRef, sprite, idle, idleInterval, spriteScale, config.flipHorizontal);

  const spriteBox = (() => {
    if (!natural) return null;
    const scale = spriteScale;
    return fit(
      Math.round(natural.width * scale),
      Math.round(natural.height * scale),
      places.pokemon,
      places.anchorBottom,
    );
  })();

  const trainerBox = trainer && places.trainer ? fit(trainer.width, trainer.height, places.trainer, true) : null;
  const trainerMirrored = Boolean(trainer?.standIn) !== config.trainerFlip;

  const trainerImage = trainer && trainerBox && (
    <img
      class="layer sprite"
      src={trainer.src}
      alt=""
      style={{
        ...boxStyle(trainerBox),
        transform: trainerMirrored ? 'scaleX(-1)' : undefined,
      }}
    />
  );

  return (
    <div
      class="widget"
      onClick={onClick}
      style={{
        width: `${width}px`,
        height: `${height}px`,
        borderRadius: `${config.cornerRadius}px`,
        background: background ? undefined : config.showBackground ? config.backgroundColor : 'transparent',
        cursor: onClick ? 'pointer' : undefined,
      }}
    >
      {background && framed && (
        <FallbackImg
          class="layer"
          urls={withProxy([background.url, background.fallbackUrl].filter(Boolean))}
          alt=""
          style={{
            ...coverStyle(framed.crop, background.w, background.h, width, height),
            transform: mirrorScene ? 'scaleX(-1)' : undefined,
          }}
        />
      )}

      {!places.trainerInFront && trainerImage}

      {sprite && spriteBox && (
        <img
          ref={spriteRef}
          class="layer sprite"
          src={sprite}
          alt={catalogs.entry(config.pokemonId)?.n ?? 'Pokémon'}
          style={{
            ...boxStyle(spriteBox),
            transform: config.flipHorizontal ? 'scaleX(-1)' : undefined,
            // A squash is about the feet, so the sprite settles rather than shrinks.
            transformOrigin: '50% 100%',
          }}
        />
      )}

      {places.trainerInFront && trainerImage}
    </div>
  );
}

const boxStyle = (box: Box) => ({
  left: `${box.left}px`,
  top: `${box.top}px`,
  width: `${box.width}px`,
  height: `${box.height}px`,
});

/** Shows `crop` of an image filling the widget, without stretching it. */
function coverStyle(crop: Box, imageW: number, imageH: number, boxW: number, boxH: number) {
  const scale = Math.max(boxW / crop.width, boxH / crop.height);
  return {
    left: `${-crop.left * scale - (crop.width * scale - boxW) / 2}px`,
    top: `${-crop.top * scale - (crop.height * scale - boxH) / 2}px`,
    width: `${imageW * scale}px`,
    height: `${imageH * scale}px`,
  };
}

/**
 * Runs a still sprite's idle loop on its image.
 *
 * Stepped, not tweened: every pose is held for `intervalMs` and then jumps to the next, as the
 * Android widget's flipper does. Offsets are in the sprite's own pixels, multiplied by how much
 * it is scaled up, so the motion keeps its proportions at every size. Someone who has asked
 * their system for less motion gets the still sprite.
 */
function useIdleLoop(
  ref: { current: HTMLImageElement | null },
  /** The image being shown; the loop starts once it is on the page, and again if it changes. */
  src: string | null,
  frames: IdleFrame[] | null,
  intervalMs: number,
  pxPerSourcePx: number,
  mirrored: boolean,
) {
  const key = frames ? JSON.stringify(frames) : '';
  useEffect(() => {
    const image = ref.current;
    if (!image || !frames || frames.length < 2 || !pxPerSourcePx || typeof image.animate !== 'function') return;
    if (window.matchMedia?.('(prefers-reduced-motion: reduce)').matches) return;
    // A mirrored sprite sways the other way, so it still leans into its own motion.
    const sign = mirrored ? -1 : 1;
    const pose = (f: IdleFrame) =>
      `translate(${Math.round(f.dxSource * pxPerSourcePx) * sign}px, ${Math.round(f.dySource * pxPerSourcePx)}px) ` +
      `scale(${(f.scaleXPermille / 1000) * sign}, ${f.scaleYPermille / 1000})`;
    const n = frames.length;
    const keyframes = [...frames, frames[0]!].map((f, i) => ({ offset: i / n, transform: pose(f) }));
    const animation = image.animate(keyframes, {
      duration: n * intervalMs,
      iterations: Infinity,
      // n held poses: progress only ever lands exactly on a keyframe.
      easing: `steps(${n}, jump-end)`,
    });
    return () => animation.cancel();
  }, [src, key, intervalMs, pxPerSourcePx, mirrored]);
}

function useTrainerArt(catalogs: Catalogs, config: WidgetConfig): TrainerArt | null {
  const [art, setArt] = useState<TrainerArt | null>(null);
  const trainerId = config.trainerId;
  const wantsBack = config.trainerPose === 'back';
  useEffect(() => {
    if (!trainerId) {
      setArt(null);
      return;
    }
    const trainer = catalogs.trainers.trainers.find((t) => t.i === trainerId);
    if (!trainer) {
      setArt(null);
      return;
    }
    let live = true;
    trainerArt(catalogs.trainers, trainer, wantsBack)
      .then((loaded) => {
        if (live) setArt(loaded);
      })
      .catch(() => {
        if (live) setArt(null);
      });
    return () => {
      live = false;
    };
  }, [catalogs, trainerId, wantsBack]);
  return art;
}

/** The scenery rectangle, re-exported so the pickers can crop their thumbnails the same way. */
export { sceneryBox, right, bottom };
