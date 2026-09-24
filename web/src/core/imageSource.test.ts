import { describe, expect, it } from 'vitest';
import { inTryOrder, isProxied, learnFrom, viaProxy, withProxy } from './imageSource';

const showdown = 'https://play.pokemonshowdown.com/sprites/trainers/red.png';
const veekunGif = 'https://veekun.com/dex/media/pokemon/main-sprites/emerald/animated/25.gif';

describe('falling back to the image proxy', () => {
  it('asks the proxy for the same file, keeping every frame of a GIF', () => {
    expect(viaProxy(showdown)).toBe('https://wsrv.nl/?url=play.pokemonshowdown.com%2Fsprites%2Ftrainers%2Fred.png');
    expect(viaProxy(veekunGif)).toMatch(/&n=-1$/);
    expect(isProxied(viaProxy(showdown))).toBe(true);
  });

  it('tries the proxy last, after every real host', () => {
    const urls = withProxy(['https://cdn.jsdelivr.net/a.png', 'https://raw.githubusercontent.com/a.png']);
    expect(urls).toHaveLength(3);
    expect(isProxied(urls[2]!)).toBe(true);
    expect(withProxy([])).toEqual([]);
  });

  it('learns nothing from a direct URL failing on its own: the file may just be missing', () => {
    learnFrom(['https://cdn.jsdelivr.net/missing.png'], 'https://raw.githubusercontent.com/missing.png');
    const urls = withProxy(['https://cdn.jsdelivr.net/b.png']);
    expect(inTryOrder(urls)).toEqual(urls);
  });

  it('once the proxy works where a host failed, tries the proxy first for that host', () => {
    learnFrom([showdown], viaProxy(showdown));
    const other = withProxy(['https://play.pokemonshowdown.com/sprites/trainers/blue.png']);
    expect(isProxied(inTryOrder(other)[0]!)).toBe(true);
    // Other hosts are left alone.
    const jsdelivr = withProxy(['https://cdn.jsdelivr.net/c.png']);
    expect(inTryOrder(jsdelivr)).toEqual(jsdelivr);
  });
});
