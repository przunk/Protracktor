// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later
import EngineModule from '../vendor/engine.mjs';

/** One separate decoder, through the same native analysis helper Android calls. */
export function analyseLoudness(engine, { bytes, name, subsong, width }) {
  const data = new Uint8Array(bytes), text = new TextEncoder().encode(`${name}\0`);
  let buffer = 0, label = 0, output = 0;
  try {
    buffer = engine._malloc(data.length); label = engine._malloc(text.length); output = engine._malloc(40);
    engine.HEAPU8.set(data, buffer); engine.HEAPU8.set(text, label);
    if (!engine._pt_measure_loudness(buffer, data.length, label, subsong, width, output)) return null;
    return [...new Float64Array(engine.HEAPU8.buffer, output, 5)];
  } finally {
    if (buffer) engine._free(buffer); if (label) engine._free(label); if (output) engine._free(output);
  }
}

export function createLoudnessAnalyser(factory = EngineModule) {
  let engine = null;
  return async (request) => {
    engine ??= Promise.resolve().then(() => factory({ wasmBinary: request.wasmBinary, locateFile: (path) => path }))
      .catch((error) => { engine = null; throw error; });
    return analyseLoudness(await engine, request);
  };
}

if (typeof WorkerGlobalScope !== 'undefined' && globalThis instanceof WorkerGlobalScope) {
  const analyse = createLoudnessAnalyser();
  globalThis.onmessage = async ({ data }) => {
    try { globalThis.postMessage({ id: data.id, values: await analyse(data) }); }
    catch { globalThis.postMessage({ id: data.id, values: null }); }
  };
}
