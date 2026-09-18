// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later
//
// Makes a phone screenshot fit Google Play's rules for the store listing.
//
// **A modern phone does not produce a screenshot Play will take.** Play wants each side between
// 320 and 3840 px, an aspect ratio no taller than 2:1, and — for the screenshot-led recommendation
// surfaces `store/graphics/README.md` is about — at least 1080 px on the short side. A phone that
// captures 864×1920 gives 2.22:1 and 864 on the short side: wrong on both counts, and
// wrong in a way that is invisible until Play refuses the upload or quietly drops the listing out
// of those surfaces.
//
// So the image is **padded, never scaled**. Widening 864 to 1080 gives 1080×1920 — 1.78:1, short
// side 1080, both rules satisfied — and costs 108 px of background down each side rather than a
// resample of every pixel in a picture whose whole job is to show what the app looks like.
//
// The padding colour is read from the source's own top-left pixel, so on this app's dark screens
// the bars are the same colour as the screen and the result reads as a wider phone rather than as
// a letterboxed one.
//
//   node scripts/fit-store-screenshots.mjs store/graphics/*.png
//
// Writes `<name>-store.png` beside each input and leaves the original alone.

import fs from 'fs';
import path from 'path';
import zlib from 'zlib';

/** The smallest canvas that satisfies every rule for a 9:20-ish portrait capture. */
const WIDTH = 1080;

function readPng(file) {
  const buf = fs.readFileSync(file);
  let at = 8;
  let width = 0;
  let height = 0;
  let depth = 0;
  let colour = 0;
  const idat = [];
  while (at < buf.length) {
    const len = buf.readUInt32BE(at);
    const type = buf.toString('latin1', at + 4, at + 8);
    if (type === 'IHDR') {
      width = buf.readUInt32BE(at + 8);
      height = buf.readUInt32BE(at + 12);
      depth = buf[at + 16];
      colour = buf[at + 17];
    } else if (type === 'IDAT') idat.push(buf.subarray(at + 8, at + 8 + len));
    at += 12 + len;
  }
  if (depth !== 8) throw new Error(`${file}: ${depth}-bit, and this reads 8`);
  const channels = { 0: 1, 2: 3, 4: 2, 6: 4 }[colour];
  if (!channels) throw new Error(`${file}: colour type ${colour}`);

  const raw = zlib.inflateSync(Buffer.concat(idat));
  const bpp = channels;
  const stride = width * bpp;
  const px = Buffer.alloc(height * stride);
  let pos = 0;
  for (let y = 0; y < height; y++) {
    const filter = raw[pos++];
    const line = raw.subarray(pos, pos + stride);
    pos += stride;
    const out = px.subarray(y * stride, (y + 1) * stride);
    const prior = y > 0 ? px.subarray((y - 1) * stride, y * stride) : Buffer.alloc(stride);
    for (let i = 0; i < stride; i++) {
      const a = i >= bpp ? out[i - bpp] : 0;
      const b = prior[i];
      const c = i >= bpp ? prior[i - bpp] : 0;
      let v = line[i];
      if (filter === 1) v += a;
      else if (filter === 2) v += b;
      else if (filter === 3) v += (a + b) >> 1;
      else if (filter === 4) {
        const p = a + b - c;
        const pa = Math.abs(p - a);
        const pb = Math.abs(p - b);
        const pc = Math.abs(p - c);
        v += pa <= pb && pa <= pc ? a : pb <= pc ? b : c;
      }
      out[i] = v & 0xff;
    }
  }
  return { width, height, channels, px };
}

/** Writes 24-bit RGB with no alpha, which is what Play asks for and what a screenshot needs. */
function writePng(file, width, height, rgb) {
  const stride = width * 3;
  const raw = Buffer.alloc(height * (stride + 1));
  for (let y = 0; y < height; y++) {
    raw[y * (stride + 1)] = 0; // filter: none. These are flat images and it costs a few per cent.
    rgb.copy(raw, y * (stride + 1) + 1, y * stride, (y + 1) * stride);
  }
  const chunk = (type, data) => {
    const out = Buffer.alloc(data.length + 12);
    out.writeUInt32BE(data.length, 0);
    out.write(type, 4, 'latin1');
    data.copy(out, 8);
    out.writeInt32BE(crc(Buffer.concat([Buffer.from(type, 'latin1'), data])), data.length + 8);
    return out;
  };
  const ihdr = Buffer.alloc(13);
  ihdr.writeUInt32BE(width, 0);
  ihdr.writeUInt32BE(height, 4);
  ihdr[8] = 8;
  ihdr[9] = 2; // truecolour, no alpha
  fs.writeFileSync(file, Buffer.concat([
    Buffer.from([0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a]),
    chunk('IHDR', ihdr),
    chunk('IDAT', zlib.deflateSync(raw, { level: 9 })),
    chunk('IEND', Buffer.alloc(0)),
  ]));
}

const CRC_TABLE = (() => {
  const table = new Int32Array(256);
  for (let n = 0; n < 256; n++) {
    let c = n;
    for (let k = 0; k < 8; k++) c = c & 1 ? 0xedb88320 ^ (c >>> 1) : c >>> 1;
    table[n] = c;
  }
  return table;
})();

function crc(buf) {
  let c = 0xffffffff;
  for (const byte of buf) c = CRC_TABLE[(c ^ byte) & 0xff] ^ (c >>> 8);
  return (c ^ 0xffffffff) | 0;
}

const files = process.argv.slice(2);
if (files.length === 0) {
  console.error('usage: node scripts/fit-store-screenshots.mjs <png> [...]');
  process.exit(2);
}

for (const file of files) {
  if (file.endsWith('-store.png')) continue; // already made, not a source
  const { width, height, channels, px } = readPng(file);
  if (width >= WIDTH && height / width <= 2) {
    console.log(`✓ ${path.basename(file)} ${width}×${height} already fits`);
    continue;
  }
  if (width > WIDTH) throw new Error(`${file}: ${width}×${height} is wider than the canvas`);

  // The screen's own colour, so the bars read as more screen rather than as letterboxing.
  const bg = [px[0], px[1], px[2]];
  const out = Buffer.alloc(WIDTH * height * 3);
  for (let i = 0; i < WIDTH * height; i++) {
    out[i * 3] = bg[0];
    out[i * 3 + 1] = bg[1];
    out[i * 3 + 2] = bg[2];
  }
  const left = Math.floor((WIDTH - width) / 2);
  for (let y = 0; y < height; y++) {
    for (let x = 0; x < width; x++) {
      const from = (y * width + x) * channels;
      const to = (y * WIDTH + left + x) * 3;
      out[to] = px[from];
      out[to + 1] = px[from + 1];
      out[to + 2] = px[from + 2];
    }
  }
  const target = file.replace(/\.png$/, '-store.png');
  writePng(target, WIDTH, height, out);
  const ratio = (height / WIDTH).toFixed(2);
  console.log(`→ ${path.basename(target)} ${WIDTH}×${height} (${ratio}:1), from ${width}×${height}`);
}
