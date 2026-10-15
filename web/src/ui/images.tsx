import type { JSX } from 'preact';
import { useEffect, useMemo, useRef, useState } from 'preact/hooks';
import { inTryOrder, learnFrom, loadFirst } from '../core/imageSource';

export interface LoadedImage {
  src: string;
  width: number;
  height: number;
}

/**
 * The first of `urls` that loads, with its own pixel size, or null while none has (or none
 * can). Every size setting is measured against that pixel size, so the widget waits for it.
 */
export function useLoadedImage(urls: string[]): LoadedImage | null {
  const [image, setImage] = useState<LoadedImage | null>(null);
  const key = urls.join('\n');
  useEffect(() => {
    if (urls.length === 0) {
      setImage(null);
      return;
    }
    let live = true;
    loadFirst(urls)
      .then(({ src, image: loaded }) => {
        if (live) setImage({ src, width: loaded.naturalWidth, height: loaded.naturalHeight });
      })
      .catch(() => {
        if (live) setImage(null);
      });
    return () => {
      live = false;
    };
  }, [key]);
  return image;
}

/**
 * An `<img>` that moves on to the next of `urls` when one fails, for the pickers' many
 * thumbnails, which load lazily and so cannot all be resolved up front.
 */
export function FallbackImg({
  urls,
  ...props
}: { urls: string[] } & Omit<JSX.IntrinsicElements['img'], 'src' | 'onError' | 'onLoad'>) {
  const key = urls.join('\n');
  // Fixed per list: a host learned to be unreachable meanwhile must not reshuffle it mid-way.
  const ordered = useMemo(() => inTryOrder(urls), [key]);
  const [index, setIndex] = useState(0);
  const failed = useRef<string[]>([]);
  useEffect(() => {
    setIndex(0);
    failed.current = [];
  }, [key]);
  const src = ordered[index];
  if (!src) return null;
  return (
    <img
      {...props}
      src={src}
      onError={() => {
        failed.current.push(src);
        setIndex(index + 1);
      }}
      onLoad={() => learnFrom(failed.current, src)}
    />
  );
}
