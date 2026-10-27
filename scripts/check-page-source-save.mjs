// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later
// Actual Queue-header counterparts of the guarded native source-read/save scenarios.
export default async function checkSourceSaves({ window, check }) {
  const api = window.__api;
  const $ = (id) => window.document.getElementById(id);
  const settle = (ms = 90) => new Promise((resolve) => setTimeout(resolve, ms));
  const store = api.storeNow;
  const originalSave = store.save, originalGet = store.get;
  const source = ['a0', 'a1'].map((name) => ({ name, url: `https://example.test/source-save/${name}.mod` }));
  const other = ['b0', 'b1'].map((name) => ({ name, url: `https://example.test/source-save/${name}.mod` }));
  const mainRows = () => [...$('queue').querySelectorAll('li.track')];
  const openQueue = () => { if ($('playback-panel').hidden) $('showqueue').click(); };
  const chooseSource = async () => { openQueue(); $('queue-source').click(); await settle(); };
  const seed = async () => {
    store.save = originalSave; store.get = originalGet;
    if (!$('unsaved').hidden) $('unsaved').click();
    api.showPanel(null);
    api.clearPlaybackQueue();
    if (api.dirtyNow()) await api.discardEdits();
    await store.save({ id: 'source-save-a', name: 'Saved source A', tracks: source, index: 0 });
    await store.save({ id: 'source-save-b', name: 'Edited B', tracks: other, index: 0 });
    await api.choosePlaylist('source-save-a');
    mainRows()[0].click(); await settle();
    const command = window.__toWorklet.filter((message) => message.type === 'open').at(-1);
    api.onWorklet({ type: 'opened', requestId: command.requestId, describe: 'title\tSource save fixture',
      duration: 90, rate: 44100, subsongs: 1, current: 0, canSeek: true });
    await api.choosePlaylist('source-save-b');
  };
  const dirty = () => {
    mainRows()[0].querySelector('.rowmenu').click();
    [...$('menu').children].find((button) => button.textContent.startsWith('Remove')).click();
  };
  const deferred = () => {
    let resolve;
    const promise = new Promise((done) => { resolve = done; });
    return { promise, resolve };
  };
  const unchanged = (before, commands) => api.planNow().context === before.context
    && api.scheduleNow() === before.schedule && window.__toWorklet.length === commands;

  await seed(); dirty(); api.enqueueTrack(other[1]);
  const beforeSave = api.planNow(), saveCommands = window.__toWorklet.length;
  await chooseSource(); $('unsaved-save').click(); await settle(150);
  check($('playback-panel').hidden && api.editorNow().id === 'source-save-a'
    && (await store.get('source-save-b')).tracks.length === 1 && unchanged(beforeSave, saveCommands),
    'source-header Save persists B before revealing A without changing playback or pending requests');

  await seed(); dirty();
  const failedBefore = api.planNow(), failedCommands = window.__toWorklet.length;
  store.save = async (playlist) => {
    if (playlist.id === 'source-save-b') throw new Error('Controlled source Save failure');
    return originalSave(playlist);
  };
  try {
    await chooseSource(); $('unsaved-save').click(); await settle(150);
    check(!$('playback-panel').hidden && api.editorNow().id === 'source-save-b' && api.dirtyNow()
      && $('snacktext').textContent.includes('Controlled source Save failure') && unchanged(failedBefore, failedCommands),
      'a failed source Save retains Queue, the selected draft and the original playback');
  } finally { store.save = originalSave; $('unsaved').click(); }

  await seed(); dirty();
  const cancelBefore = api.planNow(), cancelCommands = window.__toWorklet.length;
  await chooseSource();
  window.document.body.dispatchEvent(new window.KeyboardEvent('keydown', { key: 'Escape', bubbles: true }));
  await settle();
  check($('unsaved').hidden && !$('playback-panel').hidden && api.editorNow().id === 'source-save-b'
    && api.dirtyNow() && unchanged(cancelBefore, cancelCommands),
    'Escape cancels the source-return prompt while retaining Queue and the unsaved draft');

  await seed(); dirty();
  const missingBefore = api.planNow(), missingCommands = window.__toWorklet.length;
  let reads = 0;
  store.get = async (id) => id === 'source-save-a' && ++reads === 2 ? undefined : originalGet(id);
  try {
    await chooseSource(); $('unsaved-discard').click(); await settle(150);
    check(!$('playback-panel').hidden && api.editorNow().id === 'source-save-b' && api.dirtyNow()
      && (await originalGet('source-save-b')).tracks.length === 2 && unchanged(missingBefore, missingCommands),
      'a destination lost after Discard authorization cannot erase B or create an empty source');
  } finally { store.get = originalGet; }

  await seed(); dirty();
  const entered = deferred(), release = deferred();
  store.save = async (playlist) => {
    if (playlist.id === 'source-save-b') { entered.resolve(); await release.promise; }
    return originalSave(playlist);
  };
  try {
    await chooseSource(); $('unsaved-save').click(); await entered.promise;
    mainRows()[0].click(); await settle();
    const changed = api.planNow(), commands = window.__toWorklet.length;
    release.resolve(); await settle(150);
    check(api.sourceNow().id === 'source-save-b' && !$('playback-panel').hidden
      && (await originalGet('source-save-b')).tracks.length === 1 && unchanged(changed, commands)
      && !$('snacktext').textContent.includes('no longer available'),
      'successful delayed Save persists B but a replaced source retires the old navigation quietly');
  } finally { release.resolve(); store.save = originalSave; }

  await seed();
  const readEntered = deferred(), readRelease = deferred();
  store.get = async (id) => {
    const value = await originalGet(id);
    if (id === 'source-save-a') { readEntered.resolve(); await readRelease.promise; }
    return value;
  };
  try {
    openQueue(); $('queue-source').click(); await readEntered.promise;
    mainRows()[0].click(); await settle();
    const changed = api.planNow(), commands = window.__toWorklet.length;
    readRelease.resolve(); await settle(150);
    check(api.editorNow().id === 'source-save-b' && api.sourceNow().id === 'source-save-b'
      && !$('playback-panel').hidden && unchanged(changed, commands),
      'a late source read cannot select an obsolete playlist or close the current Queue');
  } finally { readRelease.resolve(); store.get = originalGet; }

  await seed();
  await originalSave({ id: 'source-save-a', name: 'Changed on disk', tracks: source.slice(0, 1), index: 0 });
  const captured = api.planNow(), capturedCommands = window.__toWorklet.length;
  await chooseSource();
  check(api.editorNow().id === 'source-save-a' && api.dirtyNow()
    && api.editorNow().order === captured.context.order
    && (await originalGet('source-save-a')).tracks.length === 1 && unchanged(captured, capturedCommands),
    'source navigation reveals captured membership as an unsaved draft without rewriting the stored playlist');

  await seed();
  const titled = source.map((track) => ({ ...track, name: `Stored ${track.name}` }));
  await originalSave({ id: 'source-save-a', name: 'Renamed source', tracks: titled, index: 0 });
  const metadata = api.planNow(), metadataCommands = window.__toWorklet.length;
  await chooseSource();
  check(api.editorNow().id === 'source-save-a' && !api.dirtyNow()
    && api.editorNow().order === metadata.context.order && unchanged(metadata, metadataCommands)
    && (await originalGet('source-save-a')).tracks[0].name === titled[0].name,
    'metadata-only source differences neither dirty the captured view nor overwrite newer stored titles');

  await seed();
  $('playpause').click(); await settle();
  const paused = api.planNow(), pauseCommands = window.__toWorklet.length;
  await chooseSource();
  check($('playpause').title === 'Play' && api.editorNow().id === 'source-save-a' && unchanged(paused, pauseCommands),
    'returning to the source while paused never resumes or reopens the file');
  api.enqueueTrack(other[0]);
  $('next').dispatchEvent(new window.Event('pointerdown'));
  $('next').dispatchEvent(new window.Event('pointerup'));
  $('next').click(); await settle();
  await api.choosePlaylist('source-save-b');
  const manual = api.planNow(), manualCommands = window.__toWorklet.length;
  await chooseSource();
  check(api.editorNow().id === 'source-save-a' && api.scheduleNow().current.manual
    && api.actualNow().url === other[0].url && unchanged(manual, manualCommands),
    'the source header follows the parked playlist even while a manual occurrence is playing');
  api.showPanel(null);
}
