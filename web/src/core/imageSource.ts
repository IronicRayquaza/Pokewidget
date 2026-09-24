/**
 * Where an image actually comes from, when the host it lives on cannot be reached.
 *
 * Sprites are fetched from community hosts, and some networks break some of them: a school or
 * office firewall that inspects HTTPS re-signs play.pokemonshowdown.com and veekun.com with
 * its own certificate, and every trainer front and every Gen 3–4 veekun sprite fails, while
 * jsDelivr goes through untouched. So every image has a last resort, the wsrv.nl image proxy,
 * which fetches the same file on its side and sends it on (animated GIFs stay animated, and it
 * allows cross-origin reads, so trainer backs can still go through a canvas).
 *
 * The proxy is only ever a fallback. Once it has worked for a file whose own host failed, that
 * host is taken as unreachable for the rest of the session and the proxy is tried first for
 * it, so a picker full of trainers does not wait on hundreds of doomed requests.
 */

const PROXY = 'https://wsrv.nl/?url=';

export const isProxied = (url: string): boolean => url.startsWith(PROXY);

/** The same file, fetched through the proxy. `n=-1` keeps every frame of an animated GIF. */
export function viaProxy(url: string): string {
  const bare = url.replace(/^https?:\/\//, '');
  return `${PROXY}${encodeURIComponent(bare)}${/\.gif$/i.test(bare) ? '&n=-1' : ''}`;
}

/** `urls`, best first, with the proxy of the first one as the last resort. */
export const withProxy = (urls: string[]): string[] => (urls[0] ? [...urls, viaProxy(urls[0])] : urls);

const hostOf = (url: string): string => {
  try {
    return new URL(url).host;
  } catch {
    return '';
  }
};

/** Hosts this network could not reach, though the file was there: learned, not configured. */
const unreachable = new Set<string>();

/** `urls` in the order worth trying: a host already known to be unreachable goes last. */
export const inTryOrder = (urls: string[]): string[] => {
  if (unreachable.size === 0) return urls;
  const blocked = (url: string) => !isProxied(url) && unreachable.has(hostOf(url));
  return [...urls.filter((u) => !blocked(u)), ...urls.filter(blocked)];
};

/**
 * Called once an image has loaded from `loaded` after `failed` did not. Only a proxy success
 * says anything about a host: a direct URL can fail simply because that one file is missing.
 */
export function learnFrom(failed: string[], loaded: string): void {
  if (!isProxied(loaded)) return;
  for (const url of failed) if (!isProxied(url)) unreachable.add(hostOf(url));
}

const loadImage = (src: string, crossOrigin: boolean): Promise<HTMLImageElement> =>
  new Promise((resolve, reject) => {
    const image = new Image();
    if (crossOrigin) image.crossOrigin = 'anonymous';
    image.onload = () => resolve(image);
    image.onerror = () => reject(new Error(`could not load ${src}`));
    image.src = src;
  });

/** The first of `urls` that loads, and which one it was. */
export async function loadFirst(
  urls: string[],
  crossOrigin = false,
): Promise<{ src: string; image: HTMLImageElement }> {
  const failed: string[] = [];
  for (const src of inTryOrder(urls)) {
    try {
      const image = await loadImage(src, crossOrigin);
      learnFrom(failed, src);
      return { src, image };
    } catch {
      failed.push(src);
    }
  }
  throw new Error(`could not load any of ${urls.join(', ')}`);
}
