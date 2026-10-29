// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later

/** Generated looping ProTracker tones; no downloaded music or decoder-specific test hook. */
export function stereoFixture(channels = 'left') {
  const opposite = channels === 'opposite';
  const header = Buffer.alloc(1084);
  header.write('stereo mix check');
  for (let n = 0; n < (opposite ? 2 : 1); n++) {
    const base = 20 + n * 30;
    header.write(`tone ${n + 1}`, base);
    header.writeUInt16BE(32, base + 22);
    header[base + 25] = 64;
    header.writeUInt16BE(32, base + 28);
  }
  header[950] = 1;
  header[951] = 127;
  header.write('M.K.', 1080);
  const pattern = Buffer.alloc(1024);
  const note = (channel, sample) => {
    pattern[channel * 4] = 0x01;
    pattern[channel * 4 + 1] = 0xac;
    pattern[channel * 4 + 2] = sample << 4;
  };
  if (channels !== 'right') note(0, 1);
  if (channels !== 'left') note(1, opposite ? 2 : 1);
  const sample = Buffer.from(Array.from({ length: 64 }, (_, n) => n < 32 ? 64 : 192));
  return Buffer.concat([header, pattern, sample,
    ...(opposite ? [Buffer.from(sample.map((value) => (-value) & 255))] : [])]);
}

export function openFixture(engine, bytes, name = 'stereo.mod') {
  const data = engine._malloc(bytes.length), text = Buffer.from(`${name}\0`);
  const label = engine._malloc(text.length);
  try {
    engine.HEAPU8.set(bytes, data);
    engine.HEAPU8.set(text, label);
    const handle = engine._pt_open(data, bytes.length, label);
    if (!handle) throw new Error(engine.UTF8ToString(engine._pt_last_error()));
    return handle;
  } finally { engine._free(data); engine._free(label); }
}

export function renderPcm(engine, handle, rate, frames) {
  const out = engine._malloc(Math.max(1, frames) * 8);
  try {
    const got = engine._pt_render(handle, rate, frames, out);
    return new Float32Array(engine.HEAPF32.buffer, out, got * 2).slice();
  } finally { engine._free(out); }
}
