// Writes the idle table straight out of web/src/core/idle.ts, so the Swift port and the frame
// fonts are checked against the real thing rather than a hand copy.
//
//   node --experimental-strip-types ios/tools/dump-idle.mjs > ios/Tests/idle-fixture.json
//
// Node 22.6+ can load the .ts file directly; there is nothing to install.
import { IDLE_STYLES, IDLE_STYLE_ORDER, idleFrames, resolveIdle } from '../../web/src/core/idle.ts';

// One pixel-art set and one 3D-render set, so both answers of `auto` are covered.
const SETS = ['versions_generation_iii_emerald', 'versions_generation_ix_scarlet_violet'];

const out = {};
for (const setId of SETS) {
  out[setId] = {};
  for (const style of IDLE_STYLE_ORDER) {
    const resolved = resolveIdle(style, setId);
    out[setId][style] = {
      resolved,
      intervalMs: IDLE_STYLES[resolved].intervalMs,
      frames: idleFrames(style, setId),
    };
  }
}
process.stdout.write(JSON.stringify(out, null, 2) + '\n');
