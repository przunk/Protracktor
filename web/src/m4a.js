// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later
//
// AAC frames in, an `.m4a` out (`docs/BACKLOG.md` A62). The phone has Android's `MediaMuxer` for
// this; a browser's WebCodecs encodes but writes no container, and the libraries that do would be
// a dependency for about two hundred lines. So the boxes are written here, the fewest a player
// needs: one track, one chunk, `moov` before `mdat` so a player can start before the end arrives.
//
// Pure: nothing here touches a browser, which is what lets `check-page.mjs` read the result back.

const encoder = new TextEncoder();

function u8(value) { return Uint8Array.of(value & 0xff); }
function u16(value) { return Uint8Array.of((value >>> 8) & 0xff, value & 0xff); }
function u24(value) { return Uint8Array.of((value >>> 16) & 0xff, (value >>> 8) & 0xff, value & 0xff); }
function u32(value) {
  return Uint8Array.of((value >>> 24) & 0xff, (value >>> 16) & 0xff, (value >>> 8) & 0xff, value & 0xff);
}
function zeros(count) { return new Uint8Array(count); }
function ascii(text) { return encoder.encode(text); }

function concat(parts) {
  const size = parts.reduce((sum, part) => sum + part.length, 0);
  const out = new Uint8Array(size);
  let at = 0;
  for (const part of parts) { out.set(part, at); at += part.length; }
  return out;
}

function box(type, ...parts) {
  const body = concat(parts);
  return concat([u32(8 + body.length), ascii(type), body]);
}

/** A box with a version and flags, which most of them are. */
function fullBox(type, version, flags, ...parts) {
  return box(type, u8(version), u24(flags), ...parts);
}

/** An MPEG-4 descriptor: a tag, a length in the four-byte form every reader takes, the body. */
function descriptor(tag, ...parts) {
  const body = concat(parts);
  const n = body.length;
  return concat([u8(tag), Uint8Array.of(0x80 | ((n >>> 21) & 0x7f), 0x80 | ((n >>> 14) & 0x7f), 0x80 | ((n >>> 7) & 0x7f), n & 0x7f), body]);
}

const MATRIX = concat([u32(0x00010000), u32(0), u32(0), u32(0), u32(0x00010000), u32(0), u32(0), u32(0), u32(0x40000000)]);

/** AAC-LC codes 1024 samples a frame, whatever the rate. */
export const AAC_FRAME_SAMPLES = 1024;

/**
 * The file, from the encoder's frames and its AudioSpecificConfig ([config], the two to five bytes
 * WebCodecs hands over as `decoderConfig.description`).
 */
export function muxM4a({ sampleRate, channels = 2, config, frames, bitRate = 128000 }) {
  const count = frames.length;
  const duration = count * AAC_FRAME_SAMPLES;
  const payload = frames.reduce((sum, frame) => sum + frame.length, 0);
  const largest = frames.reduce((most, frame) => Math.max(most, frame.length), 0);

  const esds = fullBox('esds', 0, 0, descriptor(0x03,
    u16(1), u8(0),
    descriptor(0x04,
      u8(0x40),                 // MPEG-4 audio
      u8((0x05 << 2) | 1),      // an audio stream
      u24(largest), u32(bitRate), u32(bitRate),
      descriptor(0x05, config)),
    descriptor(0x06, u8(0x02))));
  const mp4a = box('mp4a',
    zeros(6), u16(1),           // reserved, data reference 1
    zeros(8), u16(channels), u16(16), u16(0), u16(0), u32(sampleRate << 16),
    esds);

  // `moov` is built twice: once to learn its size, which says where `mdat`'s bytes start, and once
  // with that offset in `stco`. Its size does not depend on the offset's value.
  const moovWith = (offset) => box('moov',
    fullBox('mvhd', 0, 0, u32(0), u32(0), u32(sampleRate), u32(duration), u32(0x00010000), u16(0x0100),
      zeros(10), MATRIX, zeros(24), u32(2)),
    box('trak',
      fullBox('tkhd', 0, 3, u32(0), u32(0), u32(1), u32(0), u32(duration), zeros(8), u16(0), u16(0),
        u16(0x0100), u16(0), MATRIX, u32(0), u32(0)),
      box('mdia',
        fullBox('mdhd', 0, 0, u32(0), u32(0), u32(sampleRate), u32(duration), u16(0x55c4), u16(0)),
        fullBox('hdlr', 0, 0, u32(0), ascii('soun'), zeros(12), ascii('SoundHandler\0')),
        box('minf',
          fullBox('smhd', 0, 0, u16(0), u16(0)),
          box('dinf', fullBox('dref', 0, 0, u32(1), fullBox('url ', 0, 1))),
          box('stbl',
            fullBox('stsd', 0, 0, u32(1), mp4a),
            fullBox('stts', 0, 0, u32(1), u32(count), u32(AAC_FRAME_SAMPLES)),
            fullBox('stsc', 0, 0, u32(1), u32(1), u32(count), u32(1)),
            fullBox('stsz', 0, 0, u32(0), u32(count), ...frames.map((frame) => u32(frame.length))),
            fullBox('stco', 0, 0, u32(1), u32(offset)))))));

  const ftyp = box('ftyp', ascii('M4A '), u32(0), ascii('M4A '), ascii('isom'), ascii('mp42'));
  const moovSize = moovWith(0).length;
  const moov = moovWith(ftyp.length + moovSize + 8);
  const out = new Uint8Array(ftyp.length + moov.length + 8 + payload);
  out.set(ftyp, 0);
  out.set(moov, ftyp.length);
  let at = ftyp.length + moov.length;
  out.set(u32(8 + payload), at);
  out.set(ascii('mdat'), at + 4);
  at += 8;
  for (const frame of frames) { out.set(frame, at); at += frame.length; }
  return out;
}
