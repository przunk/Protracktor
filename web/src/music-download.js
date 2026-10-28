// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later

// Fetch cannot separate TCP connect from response headers: bound their combined native allowance.
export const MUSIC_HEADERS_TIMEOUT = 15_000 + 30_000;
export const MUSIC_READ_TIMEOUT = 30_000;

function deadline(action, timeout, controller) {
  return new Promise((resolve, reject) => {
    const timer = setTimeout(() => {
      const error = new DOMException('The music download timed out.', 'TimeoutError');
      reject(error); controller.abort(error);
    }, timeout);
    Promise.resolve().then(action).then(resolve, reject).finally(() => clearTimeout(timer));
  });
}

/** One shared transfer has its own cancellation/deadlines, independent of waiting views. */
export async function downloadMusic(fetch, url) {
  const controller = new AbortController();
  const response = await deadline(() => fetch(url, { signal: controller.signal }), MUSIC_HEADERS_TIMEOUT, controller);
  if (!response.ok) throw new Error(`HTTP ${response.status}`);
  // Null bodies are valid HTTP; the buffer path also supports platform response adapters.
  if (!response.body?.getReader) return deadline(() => response.arrayBuffer(), MUSIC_READ_TIMEOUT, controller);
  const reader = response.body.getReader(), parts = []; let size = 0, complete = false;
  try {
    for (;;) {
      const { value, done } = await deadline(() => reader.read(), MUSIC_READ_TIMEOUT, controller);
      if (done) { complete = true; break; }
      parts.push(value); size += value.byteLength;
    }
    const bytes = new Uint8Array(size); let offset = 0;
    for (const part of parts) { bytes.set(part, offset); offset += part.byteLength; }
    return bytes.buffer;
  } finally {
    if (!complete) void reader.cancel?.()?.catch(() => {});
    reader.releaseLock?.();
  }
}
