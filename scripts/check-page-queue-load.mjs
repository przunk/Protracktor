// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later
export default async function checkLoadRaces({ window, check }) {
  const api = window.__api, $ = (id) => window.document.getElementById(id);
  const settle = () => new Promise((resolve) => setTimeout(resolve, 80));
  const tracks = ['loading-A', 'loading-B'].map((name) => ({ name, url: `https://example.test/queue-load/${name}.mod` }));
  api.showPanel(null);
  if (api.dirtyNow()) await api.saveEdits();
  await api.storeNow.save({ id: 'queue-load', name: 'Queue load', tracks, index: 0 });
  await api.choosePlaylist('queue-load');
  let entered, release, signal;
  const started = new Promise((resolve) => { entered = resolve; });
  const original = window.fetch;
  window.fetch = (url, options) => {
    if (String(url) !== tracks[1].url) return original(url, options);
    signal = options?.signal;
    return new Promise((resolve, reject) => {
      release = () => resolve(original(url, options));
      signal?.addEventListener('abort', () => reject(new window.DOMException('Aborted', 'AbortError')), { once: true });
      entered();
    });
  };
  try {
    $('queue').querySelectorAll('li.track')[1].click();
    await started;
    $('showqueue').click();
    $('queue-clear').click();
    check(!signal.aborted && api.sourceNow().kind === 'none' && api.actualNow().url === tracks[1].url,
      'Clear through the panel preserves the exact in-flight current download');
    release(); await settle();
    const opened = window.__toWorklet.filter((message) => message.type === 'open').at(-1);
    check(opened.name === 'loading-B.mod' && api.sourceNow().kind === 'none',
      'the preserved download opens its original file without reattaching the cleared source');
    api.onWorklet({ type: 'opened', requestId: opened.requestId, describe: 'title\tLoading preserved', duration: 90,
      rate: 44100, subsongs: 1, current: 0, canSeek: true });
    const count = window.__toWorklet.length;
    $('snackundo').click();
    check(api.sourceNow().id === 'queue-load' && api.actualNow().url === tracks[1].url
      && window.__toWorklet.length === count, 'Clear Undo after a download completes restores context without reopening audio');
  } finally { release?.(); window.fetch = original; }
  $('queue-close').click(); api.showPanel(null);
}
