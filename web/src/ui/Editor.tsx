import type { ComponentChildren } from 'preact';
import { useEffect, useState } from 'preact/hooks';
import type { Catalogs } from '../core/data';
import { covers, defaultBackground, defaultTrainer, displayName, shinyAvailability, type SpriteSet } from '../core/catalog';
import { effectiveScene, type PlacedWidget, type WidgetConfig } from '../core/store';
import { playCry, preloadCry } from '../core/audio';
import { WidgetView } from './WidgetView';
import { BackgroundPicker, PokemonPicker, SetPicker, TrainerPicker } from './pickers';

export type Panel = 'pokemon' | 'set' | 'trainer' | null;

const SWATCHES = ['#1b1f27', '#ffffff', '#2e4b12', '#30435e', '#5b2333', '#00000066'];

export function Editor({
  catalogs,
  widget,
  panel,
  setPanel,
  change,
  onRemove,
  showPreview,
  actions,
}: {
  catalogs: Catalogs;
  widget: PlacedWidget;
  panel: Panel;
  setPanel: (panel: Panel) => void;
  change: (patch: Partial<WidgetConfig>) => void;
  onRemove: () => void;
  /** The browser tab has none: the widget on the home screen is already the live preview. */
  showPreview: boolean;
  /** Whatever puts the widget somewhere, at the end of the first row. */
  actions?: ComponentChildren;
}) {
  const config = widget.config;
  const set = catalogs.set(config.setId);
  const entry = catalogs.entry(config.pokemonId);
  const trainer = catalogs.trainers.trainers.find((t) => t.i === config.trainerId);
  const [notice, setNotice] = useState<string | null>(null);

  useEffect(() => {
    if (config.cryEnabled) preloadCry(config.pokemonId, config.legacyCry);
  }, [config.pokemonId, config.legacyCry, config.cryEnabled]);

  const shiny = set ? shinyAvailability(set, config.pokemonId, config.back, config.female, config.style) : 'set-has-none';

  const toggleShiny = () => {
    setNotice(null);
    if (config.shiny) {
      change({ shiny: false });
      return;
    }
    if (shiny === 'available') change({ shiny: true });
    else if (shiny === 'not-with-these-options') {
      change({ shiny: true, back: false, female: false, style: null });
      setNotice(`${set?.label} only has a front shiny of this Pokémon, so the widget shows that.`);
    } else if (shiny === 'set-has-none') {
      setNotice(`${set?.label} has no shiny sprites. Showdown, Black / White and most other sets do.`);
    } else {
      setNotice(`${set?.label} never drew a shiny of this Pokémon. Try another sprite set.`);
    }
  };

  const backgroundMode = config.backgroundId ? 'battle' : config.showBackground ? 'colour' : 'none';

  const battleScene = () => {
    const gen = set?.gen ?? 5;
    const chosen = trainer ?? defaultTrainer(catalogs.trainers, gen);
    change({
      scene: 'battle',
      trainerId: chosen?.i ?? config.trainerId,
      trainerPose: 'back',
      back: false,
      showBackground: true,
      backgroundId: config.backgroundId ?? defaultBackground(catalogs.backgrounds, gen).id,
    });
    setNotice(null);
  };

  const previewWidth = 420;
  const previewHeight = 240;

  return (
    <>
      <div class="card">
        {showPreview ? (
          <div class="stage" style={{ width: '100%', height: previewHeight + 24, marginBottom: 14 }}>
            <WidgetView
              config={config}
              catalogs={catalogs}
              width={previewWidth}
              height={previewHeight}
              onClick={() => {
                if (config.cryEnabled) void playCry(config.pokemonId, config.legacyCry);
              }}
            />
          </div>
        ) : (
          <p class="section-title">Look</p>
        )}
        <div class="row">
          <button
            aria-pressed={config.shiny}
            onClick={toggleShiny}
            style={{ opacity: shiny === 'available' || shiny === 'not-with-these-options' ? 1 : 0.55 }}
          >
            ✦ Shiny
          </button>
          <button aria-pressed={config.flipHorizontal} onClick={() => change({ flipHorizontal: !config.flipHorizontal })}>
            ⇋ Mirror
          </button>
          {set && covers(set, config.pokemonId, true, config.shiny, config.female, config.style) && (
            <button aria-pressed={config.back} onClick={() => change({ back: !config.back })}>
              Back sprite
            </button>
          )}
          <button aria-pressed={config.cryEnabled} onClick={() => change({ cryEnabled: !config.cryEnabled })}>
            Cry on click
          </button>
          {actions && (
            <>
              <span style={{ flex: 1 }} />
              {actions}
            </>
          )}
        </div>
        {notice && <p class="caption">{notice}</p>}
      </div>

      <div class="card">
        <p class="section-title">Pokémon</p>
        <div class="row">
          <strong style={{ fontSize: 16 }}>{entry ? displayName(entry) : 'Choose a Pokémon'}</strong>
          <span style={{ flex: 1 }} />
          <button onClick={() => setPanel(panel === 'pokemon' ? null : 'pokemon')}>
            {panel === 'pokemon' ? 'Done' : 'Change'}
          </button>
        </div>
        {panel === 'pokemon' && (
          <div style={{ marginTop: 12 }}>
            <PokemonPicker
              catalogs={catalogs}
              selectedId={config.pokemonId}
              shiny={config.shiny}
              onSelect={(id) => {
                const sets = catalogs.setsFor(id);
                const keep = sets.some((s) => s.id === config.setId);
                const next = keep ? config.setId : (sets.find((s) => s.animated) ?? sets[0])?.id ?? config.setId;
                const target = catalogs.set(next);
                change({
                  pokemonId: id,
                  setId: next,
                  // A variant the new Pokémon has no art for would be a permanent 404.
                  back: config.back && !!target && covers(target, id, true, config.shiny, config.female, config.style),
                  shiny: config.shiny && !!target && covers(target, id, config.back, true, config.female, config.style),
                  female: config.female && !!target && covers(target, id, config.back, config.shiny, true, config.style),
                });
              }}
            />
          </div>
        )}

        <p class="section-title" style={{ marginTop: 18 }}>
          Sprite set
        </p>
        <div class="row">
          <strong style={{ fontSize: 16 }}>{set?.label ?? '—'}</strong>
          <span class="caption" style={{ marginLeft: 8 }}>
            {set?.hardware}
            {set?.animated ? ' · Animated' : ''}
          </span>
          <span style={{ flex: 1 }} />
          <button onClick={() => setPanel(panel === 'set' ? null : 'set')}>
            {panel === 'set' ? 'Done' : `${catalogs.setsFor(config.pokemonId).length} sets`}
          </button>
        </div>
        {panel === 'set' && (
          <div style={{ marginTop: 12 }}>
            <SetPicker
              catalogs={catalogs}
              config={config}
              onSelect={(next: SpriteSet) =>
                change({
                  setId: next.id,
                  back: config.back && covers(next, config.pokemonId, true, config.shiny, config.female, config.style),
                  shiny: config.shiny && covers(next, config.pokemonId, config.back, true, config.female, config.style),
                  female:
                    config.female && covers(next, config.pokemonId, config.back, config.shiny, true, config.style),
                })
              }
            />
          </div>
        )}
      </div>

      <div class="card">
        <p class="section-title">Background</p>
        <div class="row">
          <button aria-pressed={backgroundMode === 'none'} onClick={() => change({ showBackground: false, backgroundId: null })}>
            None
          </button>
          <button aria-pressed={backgroundMode === 'colour'} onClick={() => change({ showBackground: true, backgroundId: null })}>
            Colour
          </button>
          <button
            aria-pressed={backgroundMode === 'battle'}
            onClick={() =>
              change({
                showBackground: true,
                backgroundId: config.backgroundId ?? defaultBackground(catalogs.backgrounds, set?.gen ?? 5).id,
              })
            }
          >
            Battle scene
          </button>
        </div>

        {backgroundMode === 'colour' && (
          <div class="swatches" style={{ marginTop: 12 }}>
            {SWATCHES.map((colour) => (
              <button
                key={colour}
                class="swatch"
                aria-pressed={config.backgroundColor === colour}
                style={{ background: colour }}
                aria-label={`Background ${colour}`}
                onClick={() => change({ backgroundColor: colour })}
              />
            ))}
          </div>
        )}

        {backgroundMode === 'battle' && (
          <div style={{ marginTop: 12 }}>
            <BackgroundPicker
              catalogs={catalogs}
              selectedId={config.backgroundId}
              onSelect={(background) => change({ showBackground: true, backgroundId: background.id })}
            />
          </div>
        )}

        {backgroundMode !== 'none' && (
          <>
            <label class="field">Corner radius · {config.cornerRadius}px</label>
            <input
              type="range"
              min={0}
              max={48}
              value={config.cornerRadius}
              onInput={(e) => change({ cornerRadius: Number((e.target as HTMLInputElement).value) })}
            />
          </>
        )}
      </div>

      <div class="card">
        <p class="section-title">Trainer</p>
        {!trainer && (
          <>
            <p class="caption" style={{ marginTop: 0 }}>
              Pair your Pokémon with a trainer — gym leaders, champions and rivals from every region.
            </p>
            <div class="row" style={{ marginTop: 12 }}>
              <button onClick={() => setPanel(panel === 'trainer' ? null : 'trainer')}>Choose a trainer</button>
              <button class="primary" onClick={battleScene}>
                Battle scene
              </button>
            </div>
          </>
        )}

        {trainer && (
          <>
            <div class="row">
              <strong style={{ fontSize: 16 }}>{trainer.n}</strong>
              <span class="caption" style={{ marginLeft: 8 }}>
                {catalogs.trainers.roles.find((r) => r.id === trainer.o)?.label} ·{' '}
                {catalogs.trainers.regions.find((r) => r.id === trainer.r)?.label}
                {trainer.v ? ` · ${trainer.v}` : ''}
              </span>
              <span style={{ flex: 1 }} />
              <button onClick={() => setPanel(panel === 'trainer' ? null : 'trainer')}>
                {panel === 'trainer' ? 'Done' : 'Change'}
              </button>
            </div>

            <label class="field">Layout</label>
            <div class="row">
              <button aria-pressed={effectiveScene(config) === 'side-by-side'} onClick={() => change({ scene: 'side-by-side' })}>
                Side by side
              </button>
              <button aria-pressed={effectiveScene(config) === 'battle'} onClick={() => change({ scene: 'battle' })}>
                Battle
              </button>
            </div>

            <label class="field">Trainer faces</label>
            <div class="row">
              <button aria-pressed={config.trainerPose === 'front'} onClick={() => change({ trainerPose: 'front' })}>
                Front
              </button>
              <button aria-pressed={config.trainerPose === 'back'} onClick={() => change({ trainerPose: 'back' })}>
                Back
              </button>
            </div>
            {config.trainerPose === 'back' && !trainer.b && (
              <p class="caption">No game drew {trainer.n} from behind, so the front is shown turned around.</p>
            )}

            <label class="field">Stands on the</label>
            <div class="row">
              <button aria-pressed={config.trainerSide === 'left'} onClick={() => change({ trainerSide: 'left' })}>
                Left
              </button>
              <button aria-pressed={config.trainerSide === 'right'} onClick={() => change({ trainerSide: 'right' })}>
                Right
              </button>
            </div>

            <div class="row" style={{ marginTop: 14 }}>
              <button aria-pressed={config.trainerFlip} onClick={() => change({ trainerFlip: !config.trainerFlip })}>
                ⇋ Mirror trainer
              </button>
              <button onClick={() => change({ trainerId: null, scene: 'solo' })}>Remove trainer</button>
              {effectiveScene(config) !== 'battle' && (
                <button class="primary" onClick={battleScene}>
                  Make it a battle scene
                </button>
              )}
            </div>
          </>
        )}

        {panel === 'trainer' && (
          <div style={{ marginTop: 12 }}>
            <TrainerPicker
              catalogs={catalogs}
              selectedId={config.trainerId}
              onSelect={(id) => {
                change({ trainerId: id, scene: effectiveScene(config) === 'solo' ? 'side-by-side' : config.scene });
                setPanel(null);
              }}
            />
          </div>
        )}
      </div>

      <div class="card">
        <p class="section-title">Sprite size</p>
        <div class="row">
          {(
            [
              ['fit', 'Fill the widget'],
              ['true-size', 'True size'],
              ['x4', '4×'],
              ['x3', '3×'],
              ['x2', '2×'],
              ['x1', 'Original size'],
            ] as const
          ).map(([mode, label]) => (
            <button key={mode} aria-pressed={config.fill === mode} onClick={() => change({ fill: mode })}>
              {label}
            </button>
          ))}
        </div>
        <p class="caption">
          {config.fill === 'true-size'
            ? 'Big Pokémon look big and small ones look small.'
            : config.fill === 'fit'
              ? 'As big as the widget allows.'
              : 'An exact multiple of the sprite’s own pixels.'}
        </p>

        <div class="row" style={{ marginTop: 18 }}>
          <button onClick={onRemove}>Delete this widget</button>
        </div>
      </div>
    </>
  );
}
