import { useState } from 'preact/hooks';
import type { Catalogs } from '../core/data';
import type { WidgetConfig } from '../core/store';
import { TAP_ACTIONS } from '../core/tap';
import { PokemonPicker } from './pickers';

/**
 * "When clicked": every tap action the Android widget has. Surprise me gets its pool —
 * any Pokémon, or only the ones picked here — and the actions that make a sound get the cry
 * switches.
 */
export function TapSection({
  config,
  catalogs,
  change,
}: {
  config: WidgetConfig;
  catalogs: Catalogs;
  change: (patch: Partial<WidgetConfig>) => void;
}) {
  const [picking, setPicking] = useState(false);
  const action = TAP_ACTIONS.find((a) => a.id === config.tapAction) ?? TAP_ACTIONS[0]!;
  const picks = config.surprisePool.length;
  const sounding = config.tapAction === 'cry' || config.tapAction === 'excite' || config.tapAction === 'surprise';

  const toggle = (id: number) => {
    const pool = config.surprisePool.includes(id)
      ? config.surprisePool.filter((p) => p !== id)
      : [...config.surprisePool, id];
    // The chosen next surprise may have just left the pool; let it be chosen again.
    change({ surprisePool: pool, surpriseNext: 0 });
  };

  return (
    <div class="card">
      <p class="section-title">When clicked</p>
      <div class="row">
        {TAP_ACTIONS.map((a) => (
          <button key={a.id} aria-pressed={config.tapAction === a.id} onClick={() => change({ tapAction: a.id })}>
            {a.label}
          </button>
        ))}
      </div>
      <p class="caption">{action.description}</p>

      {config.tapAction === 'surprise' && (
        <>
          <p class="caption">Each click replaces this widget's Pokémon. It never adds another.</p>
          <label class="field">Pick from</label>
          <div class="row">
            <button
              aria-pressed={picks === 0}
              onClick={() => {
                change({ surprisePool: [], surpriseNext: 0 });
                setPicking(false);
              }}
            >
              Any Pokémon
            </button>
            <button aria-pressed={picks > 0} onClick={() => setPicking(!picking)}>
              {picks ? `Only my picks (${picks})` : 'Only my picks'}
            </button>
            {picking && (
              <button class="primary" onClick={() => setPicking(false)}>
                Done
              </button>
            )}
          </div>
          {picking && (
            <div style={{ marginTop: 12 }}>
              <PokemonPicker
                catalogs={catalogs}
                selectedId={config.pokemonId}
                shiny={false}
                chosen={config.surprisePool}
                onSelect={toggle}
              />
            </div>
          )}
        </>
      )}

      {sounding && (
        <>
          <label class="field">Cry</label>
          <div class="row">
            <button aria-pressed={config.cryEnabled} onClick={() => change({ cryEnabled: !config.cryEnabled })}>
              Play the cry
            </button>
            {config.cryEnabled && (
              <>
                <button aria-pressed={config.legacyCry} onClick={() => change({ legacyCry: true })}>
                  Game Boy cry
                </button>
                <button aria-pressed={!config.legacyCry} onClick={() => change({ legacyCry: false })}>
                  Modern cry
                </button>
              </>
            )}
          </div>
        </>
      )}
    </div>
  );
}
