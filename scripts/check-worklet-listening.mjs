// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later
import assert from 'node:assert/strict';
globalThis.sampleRate = 48000;
let Processor;
globalThis.registerProcessor = (_name, constructor) => { Processor = constructor; };
globalThis.AudioWorkletProcessor = class { constructor() { this.port = { postMessage: () => {} }; } };
await import('../web/src/processor.js');
// Supply the decoder boundary directly; exercise the production audio callback and reply path.
const messages = [], processor = Object.create(Processor.prototype);
processor.port = { postMessage: (message) => messages.push(message) };
processor.scratchFrames = 0;
processor.handle = 1; processor.playing = true; processor.requestId = 17;
processor.engine = { HEAPF32: new Float32Array(1024), _malloc: () => 0, _pt_position: () => 0.08, _pt_render: () => 1 };
const out = [[new Float32Array(128), new Float32Array(128)]];
processor.process([], out);
assert.deepEqual(messages.at(-1), { type: 'ended', seconds: 0.08, requestId: 17 }, 'a sub-poll short track must send its actual last position through the real EOF message');
assert.equal(processor.ended, true);
processor.process([], out); assert.equal(messages.length, 1, 'EOF cannot be emitted twice');
let selected = false;
Object.assign(processor.engine, { _pt_position: () => selected ? 0 : 4.9,
  _pt_select_subsong: () => { selected = true; }, _pt_current_subsong: () => 1,
  _pt_duration: () => 60, _pt_describe: () => '', UTF8ToString: (text) => text });
processor.onMessage({ type: 'subsong', index: 1 });
assert.equal(messages.at(-1).previousSeconds, 4.9, 'the subsong reply carries the final old position before resetting it');
console.log('3 final-position/subsong worklet cases passed.');
