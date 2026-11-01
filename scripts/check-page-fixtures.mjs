// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later
import { sameEditorDocument, decoderOpenAfter } from './fixtures/page-playback.mjs';

export default async function ({ check }) {
  const order = Object.freeze({ tracks: ['one', 'two'], currentIndex: 0 });
  const document = Object.freeze({ id: 'a', name: 'Draft', order, dirty: true, revision: 3 });
  check(sameEditorDocument({ ...document }, document), 'fixture continuity accepts an equivalent navigation wrapper');
  for (const [key, value] of [['id', 'b'], ['name', 'Other'], ['order', { ...order, currentIndex: 1 }],
    ['dirty', false], ['revision', 4]]) {
    check(!sameEditorDocument({ ...document, [key]: value }, document), `fixture continuity rejects a changed editor ${key}`);
  }
  check(!sameEditorDocument({ ...document, additional: undefined }, document), 'fixture continuity also compares newly added fields');
  const window = { __toWorklet: [{ type: 'open', name: 'same.mod', requestId: 1 }] };
  const opened = await decoderOpenAfter(window, () => queueMicrotask(() => {
    window.__toWorklet.push({ type: 'open', name: 'same.mod', requestId: 2 });
  }));
  check(opened.requestId === 2, 'fixture opening waits for the new request even when filenames are equal');
  let refused = false;
  try { await decoderOpenAfter(window, () => {}, 20); } catch { refused = true; }
  check(refused, 'fixture opening rejects an action that never reaches the decoder');
}
