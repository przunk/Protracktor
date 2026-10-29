// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later

// Mirror Native Float arithmetic so a generated cover has the same colour in both runtimes.
const f = Math.fround;
export const ARTWORK_FOREGROUND = '#f4f1ea';
const SATURATION = f(.55), LUMINANCE = f(.115);

export function artworkSeed(fileName, title, id) {
  return fileName?.trim() ? fileName : title?.trim() ? title : id ?? '';
}

export function artworkHue(seed) {
  let hash = 0x811c9dc5;
  for (const byte of new TextEncoder().encode(seed)) hash = Math.imul(hash ^ byte, 0x01000193);
  hash = Math.imul(hash ^ (hash >>> 15), 0x2545f491);
  hash ^= hash >>> 13;
  return (hash >>> 0) % 360;
}

function hsl(hue, lightness) {
  const c = f(f(1 - Math.abs(f(f(2 * lightness) - 1))) * SATURATION);
  const h = f(hue / 60), x = f(c * f(1 - Math.abs(f(h % 2 - 1))));
  const rgb = h < 1 ? [c,x,0] : h < 2 ? [x,c,0] : h < 3 ? [0,c,x] : h < 4 ? [0,x,c] : h < 5 ? [x,0,c] : [c,0,x];
  const m = f(lightness - f(c / 2));
  return rgb.map((value) => Math.trunc(f(f(Math.min(1, Math.max(0, f(value + m))) * 255) + .5)));
}

function luminance(rgb) {
  const channels = rgb.map((value) => {
    const v = f(value / 255);
    return v <= f(.03928) ? f(v / f(12.92)) : f(f(f(v + f(.055)) / f(1.055)) ** 2.4);
  });
  return f(f(f(f(.2126) * channels[0]) + f(f(.7152) * channels[1])) + f(f(.0722) * channels[2]));
}

export function artworkBackgroundForHue(hue) {
  let low = 0, high = 1;
  for (let n = 0; n < 24; n++) {
    const middle = f(f(low + high) / 2);
    if (luminance(hsl(hue, middle)) < LUMINANCE) low = middle; else high = middle;
  }
  return '#' + hsl(hue, f(f(low + high) / 2)).map((value) => value.toString(16).padStart(2, '0')).join('');
}

export function artworkBackground(seed) { return artworkBackgroundForHue(artworkHue(seed)); }

export function artworkLabel(fileName, table) {
  const name = String(fileName ?? '').toLowerCase(), dot = name.lastIndexOf('.');
  const extension = dot >= 0 ? name.slice(dot + 1) : '';
  if (extension && table?.extensions.has(extension)) return extension.toUpperCase();
  const prefix = name.includes('.') ? name.slice(0, name.indexOf('.')) : '';
  if (prefix && table?.prefixes.has(prefix)) return prefix.toUpperCase();
  return extension.toUpperCase();
}

/** One local PNG, replaced only when the seed/format changes; the system owns its layout. */
export class TrackArtwork {
  constructor(document) {
    this.document = document; this.key = null;
    this.fallback = [{ src: new URL('./icons/icon-512.png', document.baseURI).href, sizes: '512x512', type: 'image/png' }];
    this.image = this.fallback;
  }
  forTrack(fileName, title, id, table) {
    const seed = artworkSeed(fileName, title, id), label = artworkLabel(fileName, table) || '?';
    const key = JSON.stringify([seed, label]);
    if (key === this.key) return this.image;
    this.key = key; this.image = this.fallback;
    try {
      const canvas = this.document.createElement('canvas'); canvas.width = 512; canvas.height = 512;
      const ctx = canvas.getContext('2d');
      if (!ctx) return this.image;
      ctx.fillStyle = artworkBackground(seed); ctx.fillRect(0, 0, 512, 512);
      ctx.fillStyle = ARTWORK_FOREGROUND; ctx.textAlign = 'center';
      ctx.font = 'bold 256px sans-serif';
      const measured = ctx.measureText(label);
      const width = measured.actualBoundingBoxLeft + measured.actualBoundingBoxRight || measured.width;
      const size = width > 0 ? 256 * 512 * .62 / width : 256;
      ctx.font = `bold ${size}px sans-serif`;
      const bounds = ctx.measureText(label);
      const ascent = bounds.actualBoundingBoxAscent ?? size * .75, descent = bounds.actualBoundingBoxDescent ?? 0;
      ctx.fillText(label, 256, 256 + (ascent - descent) / 2);
      const src = canvas.toDataURL('image/png');
      if (src.startsWith('data:image/png;')) this.image = [{ src, sizes: '512x512', type: 'image/png' }];
    } catch { /* Artwork support cannot prevent transport or metadata publication. */ }
    return this.image;
  }
}
