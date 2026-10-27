// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later
// Physical page events complement the Android-derived schedule/controller fixtures.
export default async function checkQueueView({ window, check }) {
  const $ = (id) => window.document.getElementById(id);
  check(!!$('showqueue') && !!$('playback-panel'), 'the shared dock exposes a separate Queue panel');
  if (!$('showqueue') || !$('playback-panel')) return;
  const api = window.__api;
  const settle = (ms = 60) => new Promise((resolve) => setTimeout(resolve, ms));
  const pointer = (node, type, x, y) => {
    const event = new window.MouseEvent(type, { bubbles: true, cancelable: true, clientX: x, clientY: y, button: 0 });
    Object.defineProperty(event, 'pointerId', { value: 29 });
    node.dispatchEvent(event);
    return event;
  };
  const swipe = (node, x) => {
    node.getBoundingClientRect = () => ({ width: 300, height: 60, top: 0 });
    pointer(node, 'pointerdown', 150, 10); pointer(node, 'pointermove', 150 + x, 12);
    pointer(node, 'pointerup', 150 + x, 12); node.click();
  };
  const tracks = ['source-A', 'source-B', 'source-C'].map((name) => ({ name, url: `https://example.test/queue-view/${name}.mod` }));
  const rows = () => [...$('playback-list').querySelectorAll('li.track:not([aria-hidden="true"])')];
  const pendingRows = () => rows().filter((row) => row.querySelector('.queue-grip'));
  api.clearPlaybackQueue();
  if (api.dirtyNow()) await api.saveEdits();
  await api.storeNow.save({ id: 'queue-view', name: 'Queue view fixture', tracks, index: 0 });
  await api.choosePlaylist('queue-view');
  api.showPanel(null);
  $('queue').querySelector('li.track').click(); await settle();
  const command = window.__toWorklet.filter((message) => message.type === 'open').at(-1);
  api.onWorklet({ type: 'opened', requestId: command.requestId, describe: 'title\tQueue fixture', duration: 90,
    rate: 44100, subsongs: 1, current: 0, canSeek: true });
  const beforeSwipe = window.__toWorklet.length;
  swipe($('queue').querySelector('li.track'), 115);
  check(api.scheduleNow().pending.length === 1 && window.__toWorklet.length === beforeSwipe && !api.dirtyNow(),
    'a physical playlist swipe queues once without playing or editing the document');
  api.showPanel('browse'); await api.browseTo(['history']);
  const beforePanel = window.__toWorklet.length;
  // Opening moves focus into the current row; the dock remains in normal tab order.
  $('showqueue').click();
  check(!$('playback-panel').hidden && $('browse').hidden && window.document.querySelector('main').hidden
    && window.__toWorklet.length === beforePanel && rows().length === 4,
    'Queue projects the source and manual request above the unchanged shared dock');
  check(!$('showqueue').closest('#nowcard') && !$('playback-panel').contains($('playpause')),
    'Queue has a separate dock action and uses the shared transport');
  check(window.document.activeElement === $('playback-list').querySelector('[aria-current="true"] .text'),
    'opening Queue moves keyboard focus to its current row without another scroll');
  $('queue-close').click();
  check(!$('browse').hidden && $('playback-panel').hidden && window.__toWorklet.length === beforePanel,
    'closing Queue restores Browse without replacing playback');
  $('showqueue').click();
  check($('queue-instructions').hidden, 'Queue gesture help starts collapsed');
  $('queue-help').click();
  check(!$('queue-instructions').hidden && $('queue-help').getAttribute('aria-expanded') === 'true',
    'the labelled Help action reveals concise gesture and keyboard instructions');
  $('queue-help').click();
  const first = rows()[0];
  api.render();
  check(rows()[0] === first, 'a Queue refresh retains existing keyed row nodes');
  const beforeVertical = api.scheduleNow().pending.length;
  pointer(first, 'pointerdown', 0, 0);
  const vertical = pointer(first, 'pointermove', 45, 70);
  pointer(first, 'pointermove', 160, 75); pointer(first, 'pointerup', 160, 75); first.click();
  check(!vertical.defaultPrevented && api.scheduleNow().pending.length === beforeVertical
    && window.__toWorklet.length === beforePanel, 'vertical Queue drags never enqueue or play a row');
  swipe(first, 115);
  check(api.scheduleNow().pending.length === beforeVertical + 1,
    'a physical Queue right swipe appends an independent duplicate');
  const pending = api.scheduleNow().pending;
  const gripRows = pendingRows();
  const grip = gripRows[0].querySelector('.queue-grip');
  grip.dispatchEvent(new window.KeyboardEvent('keydown', { key: 'ArrowDown', bubbles: true, cancelable: true }));
  check(api.scheduleNow().pending[1].id === pending[0].id && api.scheduleNow().pending.length === 2,
    'the pending grip keyboard action reorders only manual requests');
  const physicalRows = pendingRows();
  physicalRows.forEach((row, i) => {
    row.getBoundingClientRect = () => ({ top: 100 + i * 60, width: 300, height: 60 });
  });
  const physicalIds = api.scheduleNow().pending.map((entry) => entry.id);
  const dragged = physicalRows[0], physicalGrip = dragged.querySelector('.queue-grip');
  pointer(physicalGrip, 'pointerdown', 250, 130);
  pointer(physicalGrip, 'pointermove', 250, 195);
  pointer(physicalGrip, 'pointerup', 250, 195);
  physicalGrip.click();
  check(api.scheduleNow().pending.map((entry) => entry.id).join() === [...physicalIds].reverse().join()
    && pendingRows()[1] === dragged && window.__toWorklet.length === beforePanel && $('menu').hidden,
    'a physical production grip drag moves the same pending node without playback or a stray menu');
  const pendingBeforeRemove = api.scheduleNow().pending;
  swipe(pendingRows()[0], -115);
  check(api.scheduleNow().pending.length === 1 && !api.dirtyNow(), 'a physical Queue left swipe removes just its manual occurrence');
  $('snackundo').click();
  check(api.scheduleNow().pending.length === 2
    && api.scheduleNow().pending[0].id === pendingBeforeRemove[0].id, 'the queue removal snackbar restores the same occurrence');
  const source = rows().find((row) => row.dataset.kind === 'source' && !row.classList.contains('playing'));
  swipe(source, -115);
  check(rows().filter((row) => row.dataset.kind === 'source').length === 2
    && api.editorNow().order.tracks.length === 3 && !api.dirtyNow(), 'source left swipe omits a session row without editing its playlist');
  $('snackundo').click();
  check(rows().filter((row) => row.dataset.kind === 'source').length === 3, 'source omission Undo restores its stable occurrence');
  const beforeClear = window.__toWorklet.length;
  const actual = api.actualNow();
  $('queue-clear').click();
  check(rows().length === 1 && api.actualNow() === actual && window.__toWorklet.length === beforeClear
    && !rows()[0].classList.contains('inserted'), 'the actual Clear action retains the sounding row without manual indentation');
  $('snackundo').click();
  check(rows().length === 5 && api.sourceNow().id === 'queue-view' && api.scheduleNow().pending.length === 2,
    'Clear snackbar Undo restores the source and manual FIFO without playing');
  const infoRow = rows().find((row) => row.dataset.kind === 'manual');
  infoRow.querySelector('.text').dispatchEvent(new window.KeyboardEvent('keydown', { key: 'F10', shiftKey: true,
    bubbles: true, cancelable: true }));
  check([...$('menu').children].some((button) => button.textContent === 'Information')
    && [...$('menu').children].some((button) => button.textContent === 'Remove'),
    'keyboard users can open the same Queue row actions as a pointer context menu');
  window.document.body.click();
  const opensBeforeInfo = window.__toWorklet.filter((message) => message.type === 'open').length;
  pointer(infoRow, 'pointerdown', 100, 10); await settle(520);
  pointer(infoRow, 'pointerup', 100, 10); infoRow.click(); await settle();
  const description = window.__toWorklet.filter((message) => message.type === 'describe').at(-1);
  check(!!description && !$('nowplaying').hidden
    && window.__toWorklet.filter((message) => message.type === 'open').length === opensBeforeInfo,
    'a physical Queue long press opens Information without playing its row');
  if (description) api.onWorklet({ type: 'described', id: description.id, ok: true, describe: 'title\tInformation only', subsongs: 1 });
  await settle();
  api.showPanel('browse'); $('showqueue').click();
  window.document.body.dispatchEvent(new window.KeyboardEvent('keydown', { key: 'Escape', bubbles: true }));
  check($('playback-panel').hidden && !$('browse').hidden && window.document.activeElement === $('showqueue'),
    'Escape restores the underlying Browse view and Queue-button focus');
  $('queue-close').click();
  api.showPanel(null);
  api.showPanel('browse'); await api.browseTo(['history']);
  const historyRow = [...$('browselist').querySelectorAll('.btrack')].find((row) => row.dataset.url === tracks[0].url);
  check(!!historyRow, 'the History gesture check uses a retained replayable row');
  if (historyRow) {
    const count = api.scheduleNow().pending.length, before = window.__toWorklet.length;
    swipe(historyRow, 115);
    check(api.scheduleNow().pending.length === count + 1 && api.scheduleNow().pending.at(-1).fromHistory
      && window.__toWorklet.length === before, 'a physical History right swipe preserves replay origin without starting audio');
  }
  const { catalogue } = await import('../web/src/store.js');
  await catalogue.putAll([{ key: 'modland:tracks:Protracker/QueueView', tracks: [{ t: 'extra.mod', s: 64 }] }]);
  await api.browseTo(['modland', 'Protracker', 'QueueView']);
  const browseRow = $('browselist').querySelector('.btrack');
  check(!!browseRow, 'the Browse gesture check uses a catalogue track row');
  if (browseRow) {
    const count = api.scheduleNow().pending.length, before = window.__toWorklet.length;
    swipe(browseRow, 115);
    check(api.scheduleNow().pending.length === count + 1 && !api.scheduleNow().pending.at(-1).fromHistory
      && window.__toWorklet.length === before, 'a physical Browse right swipe queues a new occurrence with ordinary History recording');
  }
  api.showPanel(null);
}
