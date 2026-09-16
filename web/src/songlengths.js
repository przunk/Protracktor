// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later

// HVSC's song lengths, for the page.
//
// **The phone's `data/SongLengths.kt`, in the other language** (`docs/STATUS.md` C56). A SID file
// carries no duration: the tune is a program, it plays until somebody stops it, and "how long is
// it" is a question the format cannot answer. The High Voltage SID Collection answers it by hand --
// a person listened and wrote a number down -- and publishes the result as
// `DOCUMENTS/Songlengths.md5`, keyed by the plain MD5 of the whole file.
//
// Without it the page played every SID for ever, and the phone did too until its database was
// downloaded. The fallback length added for C56 stays either way: it is the answer for every format
// nobody has measured, and HVSC only knows about the C64.
//
// The time tokens are checked against `docs/rules/queue-cases.tsv` on both sides, so the two cannot
// drift. The fraction is the trap that file exists for.

/**
 * `3:55.594` to 235.594, or null when the token is not a time.
 *
 * Null is the caller's cue to drop the **whole line**: a tune whose third subsong is unreadable is
 * a tune whose lengths cannot be trusted.
 */
export function parseSongLengthTime(token) {
  const match = /^(\d+):([0-5]\d)(?:\.(\d{1,3}))?$/.exec(token);
  if (!match) return null;
  const [, minutes, seconds, fraction] = match;
  // **A decimal fraction of a second, not milliseconds.** ".5" is half a second. The field is
  // described as milliseconds in places, and reading it that way makes every fractional length
  // wrong by up to a second -- in a way nobody would notice from the numbers.
  const frac = fraction ? Number(fraction) / 10 ** fraction.length : 0;
  return Number(minutes) * 60 + Number(seconds) + frac;
}

/**
 * Reads the database into `{ md5, seconds }` entries.
 *
 * Anything that is not an entry line is skipped rather than rejected. The `[Database]` header and a
 * comment naming the path above every entry are both expected, and a future release adding a
 * section this does not know about should cost the lengths it does know rather than all of them.
 */
export function parseSongLengths(text) {
  const entries = [];
  for (const raw of text.split('\n')) {
    const line = raw.trim();
    const match = /^([0-9a-fA-F]{32})=(.+)$/.exec(line);
    if (!match) continue;
    const tokens = match[2].trim().split(' ').filter(Boolean);
    const seconds = [];
    let good = tokens.length > 0;
    for (const token of tokens) {
      const value = parseSongLengthTime(token);
      if (value == null) { good = false; break; }
      seconds.push(value);
    }
    if (good) entries.push({ md5: match[1].toLowerCase(), seconds });
  }
  return entries;
}

// --- MD5 ----------------------------------------------------------------------------------------
//
// **Written out rather than fetched from anywhere**, and it is the one thing on this page that had
// to be: `crypto.subtle` does not offer MD5 and never will, because MD5 is broken as a *security*
// hash. Nothing here is security -- HVSC chose it as a **key**, twenty years ago, and the lookup
// has to use the key the database was written with or it finds nothing.
//
// Sixty lines, no dependency, and checked against the published vectors in `check-page.mjs`.

const S = [
  7, 12, 17, 22, 7, 12, 17, 22, 7, 12, 17, 22, 7, 12, 17, 22,
  5, 9, 14, 20, 5, 9, 14, 20, 5, 9, 14, 20, 5, 9, 14, 20,
  4, 11, 16, 23, 4, 11, 16, 23, 4, 11, 16, 23, 4, 11, 16, 23,
  6, 10, 15, 21, 6, 10, 15, 21, 6, 10, 15, 21, 6, 10, 15, 21,
];

/** floor(abs(sin(i + 1)) * 2^32), the constants the algorithm is defined with. */
const K = new Uint32Array(64);
for (let i = 0; i < 64; i++) K[i] = Math.floor(Math.abs(Math.sin(i + 1)) * 4294967296);

const rotate = (value, by) => (value << by) | (value >>> (32 - by));

/**
 * The MD5 of some bytes, lower-case hex.
 *
 * Takes a `Uint8Array` -- the page holds a tune as one, having just fetched it -- and pads into a
 * copy rather than growing the caller's buffer.
 */
export function md5(bytes) {
  const length = bytes.length;
  // The message, a 0x80 byte, zeros to 56 mod 64, then the bit length as 64 little-endian bits.
  const padded = new Uint8Array(((length + 8) >> 6 << 6) + 64);
  padded.set(bytes);
  padded[length] = 0x80;
  const bits = length * 8;
  const view = new DataView(padded.buffer);
  view.setUint32(padded.length - 8, bits >>> 0, true);
  // **The high word matters**, because a 5 MB file is 41 million bits and `bits >>> 0` keeps only
  // 32 of them. Written as a float division so it stays exact past 2^32.
  view.setUint32(padded.length - 4, Math.floor(bits / 4294967296), true);

  let [a0, b0, c0, d0] = [0x67452301, 0xefcdab89, 0x98badcfe, 0x10325476];
  const chunk = new Uint32Array(16);

  for (let at = 0; at < padded.length; at += 64) {
    for (let i = 0; i < 16; i++) chunk[i] = view.getUint32(at + i * 4, true);
    let [a, b, c, d] = [a0, b0, c0, d0];
    for (let i = 0; i < 64; i++) {
      let f;
      let g;
      if (i < 16) { f = (b & c) | (~b & d); g = i; }
      else if (i < 32) { f = (d & b) | (~d & c); g = (5 * i + 1) % 16; }
      else if (i < 48) { f = b ^ c ^ d; g = (3 * i + 5) % 16; }
      else { f = c ^ (b | ~d); g = (7 * i) % 16; }
      f = (f + a + K[i] + chunk[g]) | 0;
      a = d;
      d = c;
      c = b;
      b = (b + rotate(f, S[i])) | 0;
    }
    a0 = (a0 + a) | 0;
    b0 = (b0 + b) | 0;
    c0 = (c0 + c) | 0;
    d0 = (d0 + d) | 0;
  }

  const out = new Uint8Array(16);
  new DataView(out.buffer).setUint32(0, a0 >>> 0, true);
  new DataView(out.buffer).setUint32(4, b0 >>> 0, true);
  new DataView(out.buffer).setUint32(8, c0 >>> 0, true);
  new DataView(out.buffer).setUint32(12, d0 >>> 0, true);
  return [...out].map((byte) => byte.toString(16).padStart(2, '0')).join('');
}
