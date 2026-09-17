import { cryUrls } from './catalog';

/**
 * Cries, kept decoded so a click makes a sound immediately.
 *
 * The same idea as the Android `CryPlayer`: decode once, keep it, and play the decoded
 * sample on every click rather than loading audio each time. Browsers also refuse to start
 * audio before the page has been interacted with, so the context is created on the first
 * click and resumed if the browser suspended it.
 */

let context: AudioContext | null = null;
const buffers = new Map<number, AudioBuffer>();
const pending = new Map<number, Promise<AudioBuffer | null>>();
let playing: AudioBufferSourceNode | null = null;

/** Safari decodes no Ogg Vorbis at all; there the cries are simply silent. */
export const canPlayCries = (): boolean => {
  const probe = document.createElement('audio');
  return probe.canPlayType('audio/ogg; codecs=vorbis') !== '';
};

function audioContext(): AudioContext | null {
  if (typeof window === 'undefined') return null;
  if (!context) {
    const Ctor = window.AudioContext ?? (window as unknown as { webkitAudioContext?: typeof AudioContext }).webkitAudioContext;
    if (!Ctor) return null;
    context = new Ctor();
  }
  return context;
}

async function load(pokemonId: number, legacy: boolean): Promise<AudioBuffer | null> {
  const cached = buffers.get(pokemonId);
  if (cached) return cached;
  const inFlight = pending.get(pokemonId);
  if (inFlight) return inFlight;

  const ctx = audioContext();
  if (!ctx) return null;

  const attempt = (async () => {
    // Upstream has no legacy cry above #649 or for any alternate form, so the other flavour
    // is always tried before giving up.
    for (const url of cryUrls(pokemonId, legacy)) {
      try {
        const response = await fetch(url);
        if (!response.ok) continue;
        const buffer = await ctx.decodeAudioData(await response.arrayBuffer());
        buffers.set(pokemonId, buffer);
        return buffer;
      } catch {
        // Try the other flavour, then give up quietly: a missing cry is not an error worth
        // showing anyone.
      }
    }
    return null;
  })();

  pending.set(pokemonId, attempt);
  try {
    return await attempt;
  } finally {
    pending.delete(pokemonId);
  }
}

/** Downloads and decodes ahead of the first click. Failure is ignored. */
export const preloadCry = (pokemonId: number, legacy: boolean): void => {
  void load(pokemonId, legacy);
};

/** Plays a cry, cutting off whichever one was still sounding. */
export async function playCry(pokemonId: number, legacy: boolean): Promise<void> {
  const ctx = audioContext();
  if (!ctx) return;
  if (ctx.state === 'suspended') await ctx.resume();

  const buffer = await load(pokemonId, legacy);
  if (!buffer) return;

  playing?.stop();
  const source = ctx.createBufferSource();
  source.buffer = buffer;
  source.connect(ctx.destination);
  source.onended = () => {
    if (playing === source) playing = null;
  };
  source.start();
  playing = source;
}
