import { useEffect, useState } from 'preact/hooks';
import type { Catalogs } from '../core/data';
import { backgroundById, spriteUrls } from '../core/catalog';
import { withProxy } from '../core/imageSource';
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

  const spriteBox = (() => {
    if (!natural) return null;
    const scale = displayScale(
      natural.width,
      natural.height,
      places.pokemon.width,
      places.pokemon.height,
      FILL_MULTIPLE[config.fill],
      config.fill === 'true-size' ? set?.referencePx ?? null : null,
    );
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
          class="layer sprite"
          src={sprite}
          alt={catalogs.entry(config.pokemonId)?.n ?? 'Pokémon'}
          style={{
            ...boxStyle(spriteBox),
            transform: config.flipHorizontal ? 'scaleX(-1)' : undefined,
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
