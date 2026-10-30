// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later

import { legacyUnknownAuthorUrl } from './catalogue-urls.js';

// Fetch cannot separate TCP connect from response headers: bound their combined native allowance.
export const MUSIC_HEADERS_TIMEOUT = 15_000 + 30_000;
export const MUSIC_READ_TIMEOUT = 30_000;
const cancelled = () => new DOMException('Aborted', 'AbortError');

function deadline(action, timeout, controller) {
  return new Promise((resolve, reject) => {
    let settled = false;
    const finish = (done, value) => {
      if (settled) return; settled = true;
      clearTimeout(timer); controller.signal.removeEventListener('abort', abort); done(value);
    };
    const abort = () => finish(reject, controller.signal.reason ?? cancelled());
    const timer = setTimeout(() => {
      const error = new DOMException('The music download timed out.', 'TimeoutError');
      finish(reject, error); controller.abort(error);
    }, timeout);
    controller.signal.addEventListener('abort', abort, { once: true });
    if (controller.signal.aborted) { abort(); return; }
    Promise.resolve().then(action).then((value) => finish(resolve, value), (error) => finish(reject, error));
  });
}

/** Shared music owns its signal; explicit archive jobs may additionally cancel their whole transfer. */
export async function downloadMusic(fetch, url, { signal, onProgress } = {}) {
  const controller = new AbortController(), abort = () => controller.abort(cancelled());
  signal?.addEventListener('abort', abort, { once: true });
  if (signal?.aborted) abort();
  try {
    let response = await deadline(() => fetch(url, { signal: controller.signal }), MUSIC_HEADERS_TIMEOUT, controller);
    if (!response.ok) {
      discard(response);
      const replacement = response.status === 404 ? legacyUnknownAuthorUrl(url) : null;
      if (!replacement) throw new Error(`HTTP ${response.status}`);
      // Each native connection gets its own deadline; recovery must not inherit an almost-expired one.
      response = await deadline(() => fetch(replacement, { signal: controller.signal }), MUSIC_HEADERS_TIMEOUT, controller);
      if (!response.ok) { discard(response); throw new Error(`HTTP ${response.status}`); }
    }
    const contentLength = Number(response.headers?.get('content-length')), total = contentLength > 0 ? contentLength : null;
    onProgress?.({ stage: 'fetching', done: 0, total });
    // Null bodies are valid HTTP; the buffer path also supports platform response adapters.
    if (!response.body?.getReader) {
      const bytes = await deadline(() => response.arrayBuffer(), MUSIC_READ_TIMEOUT, controller);
      onProgress?.({ stage: 'fetching', done: bytes.byteLength, total }); return bytes;
    }
    const reader = response.body.getReader(), parts = []; let size = 0, complete = false;
    try {
      for (;;) {
        const { value, done } = await deadline(() => reader.read(), MUSIC_READ_TIMEOUT, controller);
        if (done) { complete = true; break; }
        parts.push(value); size += value.byteLength;
        onProgress?.({ stage: 'fetching', done: size, total });
      }
      const bytes = new Uint8Array(size); let offset = 0;
      for (const part of parts) { bytes.set(part, offset); offset += part.byteLength; }
      return bytes.buffer;
    } finally {
      if (!complete) void reader.cancel?.()?.catch(() => {});
      reader.releaseLock?.();
    }
  } finally { signal?.removeEventListener('abort', abort); }
}

function discard(response) {
  // Refused HTTP bodies are neither music nor worth waiting for before a single recovery attempt.
  try { void response.body?.cancel?.()?.catch(() => {}); } catch { /* Already closed or locked. */ }
}
