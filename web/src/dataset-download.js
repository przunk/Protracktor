// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later
import { catalogue } from './store.js';

export const checkDownload = (signal) => { if (signal?.aborted) throw new DOMException('Aborted', 'AbortError'); };
/** Capture the previous installation before network/parse work can yield to a replacement. */
export async function downloadSnapshot(prefix, signal) {
  checkDownload(signal); const key = `${prefix}meta`, value = await catalogue.get(key);
  checkDownload(signal); return { key, value };
}
/** One transaction publishes rows and counts; cancellation/quota cannot erase the old dataset. */
export async function commitDownload(prefix, records, expected, { signal, onProgress } = {}) {
  checkDownload(signal); onProgress?.({ stage: 'storing', done: 0, total: records.length });
  checkDownload(signal);
  if (!await catalogue.replacePrefix(prefix, records, { signal, expected }))
    throw new DOMException('A newer installation or deletion replaced this download.', 'AbortError');
}

/** Cancel this job's wait while a shared engine/format request keeps its own lifetime. */
export function waitForDownload(promise, signal) {
  return new Promise((resolve, reject) => {
    let settled = false;
    const finish = (done, value) => { if (settled) return; settled = true; signal?.removeEventListener('abort', abort); done(value); };
    const abort = () => finish(reject, new DOMException('Aborted', 'AbortError'));
    Promise.resolve(promise).then((value) => finish(resolve, value), (error) => finish(reject, error));
    signal?.addEventListener('abort', abort, { once: true }); if (signal?.aborted) abort();
  });
}
