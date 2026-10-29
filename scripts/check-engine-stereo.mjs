// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later
import assert from 'node:assert/strict';
import path from 'node:path';
import { stereoFixture, openFixture, renderPcm } from './fixtures/stereo.mjs';

const engine = await (await import(path.resolve('web/vendor/engine.mjs'))).default();
let count = 0;
function test(name, run) {
  try { run(); } catch (error) { console.error(`✗ ${name}`); throw error; }
  count++; console.log(`✓ ${name}`);
}
const f = Math.fround;
const near = (actual, expected) => assert(Math.abs(actual - expected) <= 1e-7,
  `PCM ${actual} differs from ${expected}`);
const pcmBytes = (samples) => Buffer.from(samples.buffer, samples.byteOffset, samples.byteLength);
const pair = (channels, run) => {
  const bytes = stereoFixture(channels), source = openFixture(engine, bytes), mixed = openFixture(engine, bytes);
  try { run(source, mixed); } finally { engine._pt_close(source); engine._pt_close(mixed); }
};

test('the packaged production engine exports the per-handle stereo control', () => {
  assert.equal(typeof engine._pt_set_stereo_width, 'function');
});

for (const rate of [32000, 44100, 96000]) {
  for (const channels of ['left', 'right', 'correlated', 'opposite']) {
    for (const width of [100, 50, 0]) test(`${rate} Hz ${channels}: first PCM uses native ${width}% matrix`, () => {
      pair(channels, (source, mixed) => {
        engine._pt_set_stereo_width(mixed, width);
        const raw = renderPcm(engine, source, rate, 2048), actual = renderPcm(engine, mixed, rate, 2048);
        assert.equal(raw.length, actual.length); assert(raw.some((value) => Math.abs(value) > 0.001));
        if (width === 100) assert.deepEqual(pcmBytes(actual), pcmBytes(raw));
        else for (let n = 0; n < raw.length; n += 2) {
          const cross = width === 50 ? .25 : .5, keep = 1 - cross;
          near(actual[n], f(f(keep * raw[n]) + f(cross * raw[n + 1])));
          near(actual[n + 1], f(f(keep * raw[n + 1]) + f(cross * raw[n])));
          if (width === 0) assert.equal(actual[n], actual[n + 1]);
        }
        if (channels === 'correlated') for (let n = 0; n < raw.length; n++) near(actual[n], raw[n]);
        if (channels === 'opposite' && width === 0) assert(actual.every((value) => Math.abs(value) < 1e-7));
      });
    });
  }
  test(`${rate} Hz: live changes and a mid-ramp retarget follow the native 10 ms progression`, () => {
    pair('left', (source, mixed) => {
      assert.deepEqual(pcmBytes(renderPcm(engine, mixed, rate, 1024)), pcmBytes(renderPcm(engine, source, rate, 1024)));
      let width = 1;
      for (const [target, frames] of [[.5, Math.floor(rate / 200)], [0, Math.ceil(rate / 100) + 2], [1, Math.ceil(rate / 100) + 2]]) {
        const position = engine._pt_position(mixed);
        engine._pt_set_stereo_width(mixed, target * 100);
        assert.equal(engine._pt_position(mixed), position, 'changing width must not seek');
        const raw = renderPcm(engine, source, rate, frames), actual = renderPcm(engine, mixed, rate, frames);
        const step = f(1 / f(rate * f(.01)));
        for (let n = 0; n < raw.length; n += 2) {
          width = f(width + Math.max(-step, Math.min(step, f(target - width))));
          const cross = f(f(1 - width) * .5), keep = f(1 - cross);
          near(actual[n], width === 1 ? raw[n] : f(f(keep * raw[n]) + f(cross * raw[n + 1])));
          near(actual[n + 1], width === 1 ? raw[n + 1] : f(f(keep * raw[n + 1]) + f(cross * raw[n])));
        }
        assert.equal(engine._pt_current_subsong(mixed), 0);
      }
    });
  });
}

for (const invalid of [-1, 25, 200]) test(`unknown native width ${invalid} falls back to bit-preserving Original`, () => {
  pair('left', (source, mixed) => {
    engine._pt_set_stereo_width(mixed, invalid);
    assert.deepEqual(pcmBytes(renderPcm(engine, mixed, 44100, 2048)), pcmBytes(renderPcm(engine, source, 44100, 2048)));
  });
});

test('a new decoder retains Original independently of another handle in Mono', () => {
  pair('left', (source, mixed) => {
    engine._pt_set_stereo_width(mixed, 0);
    renderPcm(engine, mixed, 44100, 2048);
    const clean = openFixture(engine, stereoFixture('left'));
    try { assert.deepEqual(pcmBytes(renderPcm(engine, clean, 44100, 2048)), pcmBytes(renderPcm(engine, source, 44100, 2048))); }
    finally { engine._pt_close(clean); }
  });
});

test('zero and invalid render requests leave the decoder and initial target untouched', () => {
  const h = openFixture(engine, stereoFixture()), out = engine._malloc(8);
  try {
    engine._pt_set_stereo_width(0, 0);
    engine._pt_set_stereo_width(h, 0);
    const position = engine._pt_position(h);
    for (const [handle, rate, frames, pointer] of [[0, 44100, 1, out], [h, 0, 1, out], [h, 44100, 0, out], [h, 44100, -1, out], [h, 44100, 1, 0]])
      assert.equal(engine._pt_render(handle, rate, frames, pointer), 0);
    assert.equal(engine._pt_position(h), position);
    const actual = renderPcm(engine, h, 44100, 2048);
    assert(actual.some((value) => value !== 0));
    for (let n = 0; n < actual.length; n += 2) assert.equal(actual[n], actual[n + 1]);
  } finally { engine._pt_close(h); engine._free(out); }
});

test('seek, rewind and selecting a tune preserve the live mix', () => {
  const h = openFixture(engine, stereoFixture());
  try {
    engine._pt_set_stereo_width(h, 0); renderPcm(engine, h, 44100, 1024);
    for (const move of [() => engine._pt_seek(h, .1), () => engine._pt_rewind(h), () => engine._pt_select_subsong(h, 0)]) {
      move(); const actual = renderPcm(engine, h, 44100, 1024);
      assert(actual.length > 0 && actual.some((value) => Math.abs(value) > .001));
      for (let n = 0; n < actual.length; n += 2) assert.equal(actual[n], actual[n + 1]);
    }
  } finally { engine._pt_close(h); }
});

assert(count > 0);
console.log(`${count} production WASM stereo PCM cases passed`);
