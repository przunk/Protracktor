// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later

/** Navigation may copy the editor wrapper; every document field, including its order, must survive. */
export function sameEditorDocument(actual, expected) {
  const keys = Reflect.ownKeys(expected);
  return keys.length === Reflect.ownKeys(actual).length
    && keys.every(key => Object.hasOwn(actual, key) && Object.is(actual[key], expected[key]));
}

/** A fixed sleep can acknowledge the previous file, especially for two equal manual requests. */
export async function decoderOpenAfter(window, action, timeoutMs = 5000) {
  const latest = () => window.__toWorklet.filter(message => message.type === 'open').at(-1);
  const before = latest()?.requestId;
  await action();
  const deadline = Date.now() + timeoutMs;
  for (;;) {
    const next = latest();
    if (next?.requestId != null && next.requestId !== before) return next;
    if (Date.now() >= deadline) throw new Error('The fixture action did not issue a new correlated decoder open');
    await new Promise(resolve => setTimeout(resolve, 5));
  }
}
