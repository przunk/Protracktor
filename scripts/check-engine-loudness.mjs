// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later
import assert from 'node:assert/strict';
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import { spawnSync } from 'node:child_process';
import Engine from '../web/vendor/engine.mjs';
import { stereoFixture, openFixture, renderPcm } from './fixtures/stereo.mjs';
const engine = await Engine();
const bytes = stereoFixture('correlated');
let count = 0;
function test(name, run) { run(); count++; console.log(`✓ ${name}`); }
const pcmBytes = (pcm) => Buffer.from(pcm.buffer, pcm.byteOffset, pcm.byteLength);
function measure(data, subsong = -1, width = 100) {
  const buffer = engine._malloc(data.length), name = engine._malloc(12), output = engine._malloc(40);
  try {
    engine.HEAPU8.set(data, buffer); engine.HEAPU8.set(Buffer.from('source.mod\0'), name);
    const got = engine._pt_measure_loudness(buffer, data.length, name, subsong, width, output);
    assert.equal(got, 1, engine.UTF8ToString(engine._pt_last_error()));
    return [...new Float64Array(engine.HEAPU8.buffer, output, 5)];
  } finally { engine._free(buffer); engine._free(name); engine._free(output); }
}
function profile(handle) {
  const output = engine._malloc(32);
  try { return engine._pt_loudness_profile(handle, output) ? [...new Float64Array(engine.HEAPU8.buffer, output, 4)] : null; }
  finally { engine._free(output); }
}
function normalizedPair(run) {
  const raw = openFixture(engine, bytes), normalized = openFixture(engine, bytes);
  try { run(raw, normalized); } finally { engine._pt_close(raw); engine._pt_close(normalized); }
}
function scaled(raw, actual, gain) {
  assert.equal(raw.length, actual.length); assert(raw.some((x) => Math.abs(x) > .001));
  for (let n = 0; n < raw.length; n++) assert(Math.abs(actual[n] - raw[n] * gain) < 1e-7);
}

test('the packaged production C API exports both independent analysis and live controls', () => {
  for (const fn of ['_pt_measure_loudness', '_pt_set_normalization', '_pt_loudness_profile']) assert.equal(typeof engine[fn], 'function');
});
for (const rate of [32000, 44100, 48000, 96000]) test(`default and explicitly disabled handles preserve the same PCM at ${rate}Hz`, () => {
  const h = openFixture(engine, bytes), off = openFixture(engine, bytes);
  try {
    engine._pt_set_normalization(off, 0, 20, -45, .01, 1, 100);
    assert.deepEqual(pcmBytes(renderPcm(engine, h, rate, 4096)), pcmBytes(renderPcm(engine, off, rate, 4096)));
  } finally { engine._pt_close(h); engine._pt_close(off); }
});
test('the native prepared target is applied to the first PCM, not an inherited gain', () => {
  normalizedPair((raw, h) => {
    engine._pt_set_normalization(h, 1, 20, -6, .5, 1, 100);
    assert.equal(profile(h), null);
    scaled(renderPcm(engine, raw, 44100, 4096), renderPcm(engine, h, 44100, 4096), Math.fround(10 ** (-12 / 20)));
  });
});
test('unknown or another-width preparation starts with the native conservative fallback', () => {
  for (const p of [[0, -100, 0, 0, 100], [20, -6, .5, 1, 50], [NaN, -6, .5, 1, 100]]) normalizedPair((raw, h) => {
    engine._pt_set_normalization(h, 1, ...p);
    scaled(renderPcm(engine, raw, 44100, 4096), renderPcm(engine, h, 44100, 4096), .5);
  });
});
test('a short source produces a complete native measurement on its own backend', () => {
  const h = openFixture(engine, bytes), before = engine._pt_position(h), p = measure(bytes);
  assert.equal(engine._pt_position(h), before); engine._pt_close(h);
  assert(p[0] > .4 && p[0] < 20 && p[1] >= -70 && p[1] <= 30 && p[2] > 0 && p[3] === 1 && p[4] === 0);
  console.log('MEASUREMENT complete', JSON.stringify(p));
});
test('a long source stops at the native twenty-second prefix and remains partial', () => {
  const long = Buffer.from(bytes); long[950] = 128;
  const p = measure(long); assert(p[0] >= .4 && p[0] <= 20 && p[3] === 0);
  console.log('MEASUREMENT partial', JSON.stringify(p));
});
test('a sub-window source cannot acquire a usable boost measurement', () => {
  const short = Buffer.from(bytes); short[1086] |= 0x0d; short[1087] = 0;
  const p = measure(short); assert(p[0] < .4 && p[1] < -70);
  console.log('MEASUREMENT short', JSON.stringify(p));
});
test('live natural completion is whole-song, while seek and restart retain gain but lose completeness', () => {
  for (const discontinuity of [null, 'seek', 'rewind']) {
    const h = openFixture(engine, bytes);
    try {
      engine._pt_set_normalization(h, 1, 20, -6, .5, 1, 100);
      renderPcm(engine, h, 44100, 4096);
      if (discontinuity === 'seek') engine._pt_seek(h, .1);
      if (discontinuity === 'rewind') engine._pt_rewind(h);
      for (let n = 0; n < 200; n++) if (renderPcm(engine, h, 44100, 4096).length < 8192) break;
      const p = profile(h); assert(p && p[0] > .4 && p[1] >= -70 && p[2] > 0);
      assert.equal(p[3], discontinuity ? 0 : 1);
    } finally { engine._pt_close(h); }
  }
});
test('a successful subsong reset cannot inherit a boosted trim', () => {
  normalizedPair((raw, h) => {
    engine._pt_set_normalization(h, 1, 20, -45, .01, 1, 100);
    renderPcm(engine, h, 44100, 4096);
    engine._pt_select_subsong(h, 0);
    scaled(renderPcm(engine, raw, 44100, 128), renderPcm(engine, h, 44100, 128), .5);
  });
});

for (const [name, lufs, peak, complete, gain] of [
  ['partial +6 dB cap', -45, .01, 0, 10 ** (6 / 20)],
  ['complete +12 dB cap', -45, .01, 1, 10 ** (12 / 20)],
  ['-36 dB attenuation floor', 30, .01, 1, 10 ** (-36 / 20)],
  ['sample-peak headroom overrides target', -45, .9, 1, Math.fround(.794328235) / .9],
]) test(`actual decoder PCM observes the native ${name}`, () => {
  const quiet = Buffer.from(bytes); quiet[45] = 8;
  const raw = openFixture(engine, quiet), h = openFixture(engine, quiet);
  try {
    engine._pt_set_normalization(h, 1, 20, lufs, peak, complete, 100);
    scaled(renderPcm(engine, raw, 48000, 2048), renderPcm(engine, h, 48000, 2048), Math.fround(gain));
  } finally { engine._pt_close(raw); engine._pt_close(h); }
});
test('seek and restart preserve the established gain and measured input duration', () => {
  for (const action of ['seek', 'rewind']) normalizedPair((raw, h) => {
    engine._pt_set_normalization(h, 1, 20, -6, .5, 1, 100);
    renderPcm(engine, raw, 44100, 4096); renderPcm(engine, h, 44100, 4096);
    const before = profile(h);
    if (action === 'seek') { engine._pt_seek(raw, .2); engine._pt_seek(h, .2); }
    else { engine._pt_rewind(raw); engine._pt_rewind(h); }
    scaled(renderPcm(engine, raw, 44100, 4096), renderPcm(engine, h, 44100, 4096), Math.fround(10 ** (-12 / 20)));
    assert(profile(h)[0] > before[0], 'seek must retain existing input measurements');
  });
});
test('a live width control invalidates the old getter before any mixed PCM is measured', () => {
  const h = openFixture(engine, bytes);
  try {
    engine._pt_set_normalization(h, 1, 20, -6, .5, 1, 100);
    renderPcm(engine, h, 44100, 4096); assert(profile(h));
    engine._pt_set_stereo_width(h, 0); assert.equal(profile(h), null);
    renderPcm(engine, h, 44100, 1024);
    assert(Math.abs(profile(h)[0] - 1024 / 44100) < 1e-12);
  } finally { engine._pt_close(h); }
});
test('live sample-rate discontinuity cannot be cached as whole-song listening', () => {
  const h = openFixture(engine, bytes);
  try {
    engine._pt_set_normalization(h, 1, 20, -6, .5, 1, 100);
    renderPcm(engine, h, 44100, 4096);
    for (let n = 0; n < 200; n++) if (renderPcm(engine, h, 48000, 4096).length < 8192) break;
    assert.equal(profile(h)[3], 0);
  } finally { engine._pt_close(h); }
});
test('invalid analysis calls refuse without advancing an audible handle', () => {
  const h = openFixture(engine, bytes), output = engine._malloc(40);
  try {
    const before = engine._pt_position(h);
    assert.equal(engine._pt_measure_loudness(0, 0, 0, -1, 100, output), 0);
    assert.equal(engine._pt_measure_loudness(0, -1, 0, -1, 100, output), 0);
    assert.equal(engine._pt_loudness_profile(0, output), 0);
    assert.equal(engine._pt_loudness_profile(h, 0), 0);
    assert.equal(engine._pt_position(h), before);
  } finally { engine._free(output); engine._pt_close(h); }
});
const directory = fs.mkdtempSync(path.join(os.tmpdir(), 'protracktor-loudness-oracle-'));
try {
  const executable = path.join(directory, 'loudness-oracle');
  const compile = spawnSync('c++', ['-std=c++20', '-O2', '-Wall', '-Wextra', '-Werror',
    'scripts/audio-host-test/loudness_oracle.cpp', '-o', executable], { encoding: 'utf8', timeout: 30000 });
  assert.equal(compile.error, undefined); assert.equal(compile.status, 0, compile.stderr);
  for (const rate of [32000, 44100, 48000, 96000]) for (const [mode, width, enabled, preparation] of [
    ['Off bypass', 100, 0, [20, -45, .01, 1]],
    ['known Original', 100, 1, [20, -6, .5, 1]],
    ['live Narrow fallback', 50, 1, [0, -100, 0, 0]],
    ['Mono peak guard', 0, 1, [20, -45, .01, 1]],
  ]) test(`${rate}Hz ${mode}: decoded PCM and meter match the standalone native header oracle`, () => {
    const raw = openFixture(engine, bytes), h = openFixture(engine, bytes), decoded = [], actual = [];
    try {
      engine._pt_set_stereo_width(h, width);
      engine._pt_set_normalization(h, enabled, ...preparation, width);
      for (let frames = 0; frames < rate * 4; frames += 2048) {
        decoded.push(pcmBytes(renderPcm(engine, raw, rate, 2048)));
        actual.push(pcmBytes(renderPcm(engine, h, rate, 2048)));
      }
      const result = spawnSync(executable, [enabled, width, rate, ...preparation, 2048].map(String),
        { input: Buffer.concat(decoded), timeout: 30000, maxBuffer: 16 * 1024 * 1024 });
      assert.equal(result.error, undefined); assert.equal(result.status, 0, result.stderr.toString());
      const output = Buffer.concat(actual); assert.equal(output.length, result.stdout.length);
      let largest = 0;
      for (let n = 0; n < output.length; n += 4)
        largest = Math.max(largest, Math.abs(output.readFloatLE(n) - result.stdout.readFloatLE(n)));
      assert(largest < 5e-6, `WASM/native PCM differed by ${largest}`);
      const expected = result.stderr.toString().trim().split(' ').map(Number), measured = profile(h);
      for (let n = 0; n < 3; n++) assert(Math.abs(measured[n] - expected[n]) < (n === 1 ? 1e-5 : 1e-7),
        `WASM/native profile ${n}: ${measured[n]} differs from ${expected[n]}`);
    } finally { engine._pt_close(raw); engine._pt_close(h); }
  });
} finally { fs.rmSync(directory, { recursive: true, force: true }); }
assert(count > 0); console.log(`${count} actual native-WASM loudness policy/PCM cases passed`);
