// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later
// Node supplies worker transport only; the module, handler, WASM and decoder are production code.
import { parentPort } from 'node:worker_threads';
globalThis.WorkerGlobalScope = class {
  static [Symbol.hasInstance](value) { return value === globalThis; }
};
globalThis.postMessage = (message) => parentPort.postMessage(message);
await import('../../web/src/loudness-worker.js');
parentPort.on('message', (data) => globalThis.onmessage({ data }));
