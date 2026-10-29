// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later

/** Legacy duplicate playlists through actual row/menu/Undo events and the production ESM graph. */
export async function verify({ window, check }) {
  const api = window.__api, $ = id => window.document.getElementById(id);
  const rows = () => [...$('queue').querySelectorAll('li.track')];
  const messages = () => window.__toWorklet;
  const until = async condition => {
    for (let n = 0; n < 400; n++) {
      if (condition()) return true;
      await new Promise(resolve => setTimeout(resolve, 5));
    }
    return false;
  };
  const play = async index => {
    const before = messages().filter(message => message.type === 'open').length;
    rows()[index].click();
    check(await until(() => messages().filter(message => message.type === 'open').length > before), 'row selection reaches a fresh decoder open');
    const open = messages().filter(message => message.type === 'open').at(-1);
    api.onWorklet({ type: 'opened', requestId: open.requestId, describe: 'title\tDuplicate test',
      duration: 90, subsongs: 1, current: 0, canSeek: true, rate: 44100 });
    api.onWorklet({ type: 'position', requestId: open.requestId, seconds: 17, subsong: 0 });
  };
  const remove = index => {
    rows()[index].querySelector('.rowmenu').click();
    [...$('menu').querySelectorAll('button')].find(button => button.textContent === 'Remove from this playlist').click();
  };
  for (const shared of [false, true]) {
    await api.discardEdits(); api.showPanel(null); api.clearPlaybackQueue();
    const a = { url: 'https://example.test/duplicates/a.mod', name: 'First A' };
    const second = shared ? a : { ...a, name: 'Second A' };
    const tracks = [a, { url: 'https://example.test/duplicates/b.mod', name: 'B' }, second,
      { url: 'https://example.test/duplicates/c.mod', name: 'C' }];
    await api.storeNow.save({ id: 'duplicate-edit', name: 'Duplicate edits', tracks });
    await api.choosePlaylist('duplicate-edit');
    if ($('shuffle').classList.contains('on')) $('shuffle').click();
    await play(0);
    api.enqueueTrack(tracks[3]);
    const first = api.planNow(), commands = messages().length;
    remove(3);
    check(api.indexNow() === 0 && api.planNow().context.currentEntryId === first.context.currentEntryId,
      'removing unrelated C keeps the first duplicate occurrence and its highlight');
    check(api.planNow().context.next().context?.current.url === tracks[1].url && api.scheduleNow() === first.schedule
      && messages().length === commands && $('elapsed').textContent === '0:17',
      'the edit preserves the next source row, manual FIFO, decoder and elapsed position');
    $('snackundo').click();
    check(api.indexNow() === 0 && api.editorNow().order.tracks.length === 4, 'Undo of unrelated C leaves the first occurrence active');
    await play(2);
    const secondId = api.planNow().context.currentEntryId;
    remove(0);
    check(api.indexNow() === 1 && api.planNow().context.currentEntryId === secondId && rows()[1].classList.contains('playing'),
      'removing earlier A preserves the distinct later A occurrence');
    $('snackundo').click();
    check(api.indexNow() === 2 && api.planNow().context.currentEntryId === secondId && rows()[2].classList.contains('playing'),
      'Undo inserts earlier A without stealing the current occurrence identity');
    remove(2);
    check($('playpause').title === 'Play', 'removing the currently playing duplicate pauses instead of advancing audio');
    $('snackundo').click();
    check(api.indexNow() === 2 && $('playpause').title === 'Play', 'Undo parks the exact removed duplicate without auto-starting');
  }
  await api.discardEdits(); api.showPanel(null);
  const swipe = row => {
    row.getBoundingClientRect = () => ({ width: 300, height: 60, top: 0 });
    for (const [type, x] of [['pointerdown', 150], ['pointermove', 265], ['pointerup', 265]]) {
      const event = new window.MouseEvent(type, { clientX: x, clientY: 10, bubbles: true, cancelable: true, button: 0 });
      Object.defineProperty(event, 'pointerId', { value: 29 }); row.dispatchEvent(event);
    }
  };
  for (const retargeted of [false, true]) {
    api.clearPlaybackQueue(); await api.choosePlaylist('duplicate-edit'); await play(0);
    const before = api.planNow(), commands = messages().length, oldRow = rows()[0];
    swipe(oldRow);
    (retargeted ? rows()[0] : oldRow).click();
    check(api.scheduleNow().pending.length === 1 && api.planNow().context === before.context
      && messages().length === commands && !api.dirtyNow(),
      `swipe enqueue consumes its ${retargeted ? 'retargeted' : 'detached-row'} click without restarting playback`);
  }
  swipe(rows()[0]);
  rows()[1].dispatchEvent(new window.KeyboardEvent('keydown', { key: 'Enter', bubbles: true }));
  rows()[1].click();
  check(api.indexNow() === 1, 'a fresh keyboard activation is not swallowed by an unfinished swipe click');
  check(window.__browserErrors.length === 0, 'duplicate edit events have no unhandled browser errors');
}
