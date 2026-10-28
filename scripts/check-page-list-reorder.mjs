// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later

/** Production playlist and pending-Queue grip events; synthetic geometry is host evidence only. */
export default async function checkListReorder({ window, check }) {
  const { document } = window, $ = (id) => document.getElementById(id), api = window.__api;
  const settle = (ms = 40) => new Promise((resolve) => setTimeout(resolve, ms));
  const viewport = document.querySelector('main'), list = $('queue');
  const oldRect = window.HTMLElement.prototype.getBoundingClientRect;
  const rows = () => [...list.querySelectorAll('li.track')];
  const urls = (values) => values.map((track) => track.url).join();
  const order = () => urls(api.editorNow().order.tracks);
  const grip = (index) => rows()[index].querySelector('.playlist-grip');
  const moveKey = (index, key) => grip(index).dispatchEvent(new window.KeyboardEvent('keydown', { key, bubbles: true, cancelable: true }));
  const event = (target, type, y, x = 0, pointer = 1) => {
    const e = new window.MouseEvent(type, { clientY: y, clientX: x, bubbles: true, cancelable: true, button: 0 });
    Object.defineProperty(e, 'pointerId', { value: pointer }); target.dispatchEvent(e);
  };
  const move = (y) => event(document, 'pointermove', y);
  const end = (y) => event(document, 'pointerup', y);
  const tracks = [0, 1, 2, 3].map((i) => ({ url: `https://example.test/reorder/${i}.mod`, name: `Reorder ${i}` }));
  const a = 'reorder-a', b = 'reorder-b';
  await api.discardEdits(); api.showPanel(null);
  await api.storeNow.save({ id: a, name: 'Reorder A', tracks });
  await api.storeNow.save({ id: b, name: 'Reorder B', tracks });
  await api.choosePlaylist(a);
  rows()[1].click(); await settle();
  api.onWorklet({ type: 'opened', describe: 'title\tReorder current\nformat\tProTracker MOD', duration: 90, subsongs: 1, current: 0, canSeek: true, rate: 44100 });
  api.onWorklet({ type: 'position', seconds: 17, subsong: 0 });
  if ($('shuffle').classList.contains('on')) $('shuffle').click();
  api.enqueueTrack(tracks[3]); api.enqueueTrack(tracks[3]); api.enqueueTrack(tracks[2]);
  const before = api.planNow(), commands = window.__toWorklet.length;
  window.HTMLElement.prototype.getBoundingClientRect = function () {
    if (this === viewport || this === $('playback-list')) return { top: 0, bottom: 1000, height: 1000, width: 400 };
    const parent = this.closest('li.track');
    if (parent && [list, $('playback-list')].includes(parent.parentElement)) {
      const scroll = parent.parentElement === list ? viewport.scrollTop : $('playback-list').scrollTop;
      const top = [...parent.parentElement.children].filter((row) => !row._exit).indexOf(parent) * 60 - scroll
        + (Number(/translateY\(([-\d.]+)px\)/.exec(parent.style.transform)?.[1]) || 0);
      return { top, bottom: top + 60, height: 60, width: 400 };
    }
    return oldRect.call(this);
  };
  try {
    check(rows().every((row) => row.querySelector('.playlist-grip')?.textContent === 'Reorder'
      && row.querySelector('.playlist-grip svg')), 'each editable playlist row has a labelled grip');
    viewport.scrollTop = 0;
    const handle = grip(0), physical = rows();
    event(handle, 'pointerdown', 30); move(51); await settle(20);
    check(order() === urls(tracks), 'partial playlist-row motion waits for a whole row');
    move(123); await settle(20); move(195);
    check(order() === urls([tracks[1], tracks[2], tracks[0], tracks[3]]), 'actual playlist grip commits successive moves before release');
    check(rows().every((row, i) => row === physical[i]) && handle.isConnected,
      'live model renders retain physical row/grip nodes while the pointer is held');
    check(Math.round(physical[0].getBoundingClientRect().top) === 165 && viewport.scrollTop === 0,
      'the first dragged row follows the pointer without moving the playlist viewport');
    const moved = api.planNow();
    check(moved.context.current?.url === before.context.current?.url && moved.context.currentEntryId === before.context.currentEntryId
      && moved.schedule === before.schedule && $('elapsed').textContent === '0:17', 'playlist moves preserve playback occurrence, elapsed time and manual FIFO');
    check(window.__toWorklet.length === commands, 'playlist grip events issue no decoder commands');
    end(195); handle.click();
    check(rows()[2].querySelector('.title').textContent === tracks[0].name,
      'release synchronizes the visible playlist to its committed model');
    check($('menu').hidden && document.activeElement === grip(2), 'release restores grip focus and consumes its synthesized click');
    check(api.dirtyNow() && urls((await api.storeNow.get(a)).tracks) === urls(tracks), 'reorder edits stay unsaved until Save');
    $('tab-save').click(); await settle();
    check(urls((await api.storeNow.get(a)).tracks) === order() && !api.dirtyNow(), 'the actual Save commits the reordered draft');
    const saved = order();
    const cancelled = grip(0); event(cancelled, 'pointerdown', 30); move(100); event(document, 'pointercancel', 100);
    check(order() !== saved && api.dirtyNow(), 'playlist pointer cancellation retains the already committed move');
    $('tab-discard').click(); await settle();
    check(order() === saved && !api.dirtyNow(), 'Discard restores that playlist saved order');
    await api.choosePlaylist(b); const source = api.planNow().context, pending = api.scheduleNow();
    moveKey(0, 'ArrowDown');
    check(order() === urls([tracks[1], tracks[0], tracks[2], tracks[3]]) && api.planNow().context === source
      && api.scheduleNow() === pending, 'keyboard reorder of viewed B leaves independently playing A and its requests untouched');
    check(document.activeElement === grip(1), 'keyboard reorder retains focused logical occurrence');
    grip(1).click();
    const earlier = [...$('menu').querySelectorAll('button')].find((button) => button.textContent === 'Move earlier');
    check(!!earlier?.querySelector('svg'), 'the grip fallback menu exposes a labelled accessible move action');
    earlier.click();
    check(order() === urls(tracks), 'the actual menu move changes the intended playlist occurrence');
    const stale = grip(0); event(stale, 'pointerdown', 30); move(100); api.showPanel('browse'); move(300); end(300);
    check(order() === urls([tracks[1], tracks[0], tracks[2], tracks[3]]), 'leaving the playlist cancels further movement while retaining committed changes');
    api.showPanel(null); await api.discardEdits();

    $('showqueue').click();
    const queueList = $('playback-list'), requests = api.scheduleNow().pending, first = requests[0].id;
    const manual = (id) => queueList.querySelector(`[data-request="${id}"]`), queueGrip = manual(first).querySelector('.queue-grip');
    const initialNodes = [...queueList.children], firstTop = manual(first).getBoundingClientRect().top;
    event(queueGrip, 'pointerdown', firstTop + 30); move(firstTop + 100);
    check(api.scheduleNow().pending.map((request) => request.id).join() === [requests[1].id, first, requests[2].id].join(),
      'actual manual-Queue grip commits a move before release, including duplicate URLs');
    check([...queueList.children].every((node, i) => node === initialNodes[i]) && queueGrip.isConnected,
      'Queue live refresh retains physical pending-row nodes during the gesture');
    event(document, 'pointercancel', firstTop + 100);
    check(manual(first).querySelector('.queue-grip') === queueGrip && !manual(first).classList.contains('dragging'),
      'Queue cancellation keeps its move, stable grip and cleared preview');
    queueGrip.dispatchEvent(new window.KeyboardEvent('keydown', { key: 'ArrowUp', bubbles: true, cancelable: true }));
    check(api.scheduleNow().pending.map((request) => request.id).join() === requests.map((request) => request.id).join(),
      'Queue keyboard action still moves only within the pending block');
    event(queueGrip, 'pointerdown', firstTop + 30); move(firstTop + 100);
    $('queue-close').click();
    const closedOrder = api.scheduleNow().pending;
    move(firstTop + 240); end(firstTop + 240);
    check(api.scheduleNow().pending === closedOrder && $('playback-panel').hidden,
      'closing Queue cancels its active grip and prevents hidden pointer moves');
    api.showPanel(null);
    const local = { url: 'content://reorder/missing.mod', name: 'Missing local file', local: true };
    await api.storeNow.save({ id: 'reorder-local', name: 'Missing references', tracks: [local, tracks[0], tracks[1]] });
    await api.choosePlaylist('reorder-local'); viewport.scrollTop = 0;
    const localRow = rows()[0], localGrip = grip(0);
    event(localGrip, 'pointerdown', 30); move(100);
    const layer = window.getComputedStyle(localRow);
    check(layer.position === 'relative' && layer.zIndex === '3',
      'the whole dragged local row has a positioned layer above neighbours, including unavailable references');
    check(api.editorNow().order.tracks[1].url === local.url && localGrip.isConnected,
      'an unavailable local reference can still be reordered without trying to play it');
    end(100); await api.discardEdits();
    api.receive({ queue: tracks.map((track) => ({ url: track.url, title: track.name })), index: 0 }); await settle();
    check(!list.querySelector('.playlist-grip'), 'protected From the phone has no playlist reorder grips');
  } finally { window.HTMLElement.prototype.getBoundingClientRect = oldRect; api.showPanel(null); }
}
