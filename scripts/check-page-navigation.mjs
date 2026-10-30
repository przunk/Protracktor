// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later
import * as archive from '../web/src/catalogue.js';

/** ProtracktorApp Back/PlaylistReturnAction and RandomDice through actual WEB controls. */
export async function verify({ window, check }) {
  const { document } = window, api = window.__api, $ = id => document.getElementById(id);
  const pause = () => new Promise(resolve => setTimeout(resolve, 15));
  const until = async predicate => { for (let n = 0; n < 300; n++) { if (predicate()) return true; await pause(); } return false; };
  const label = button => button?.textContent.trim();
  const visible = node => node && !node.closest('[hidden]') && window.getComputedStyle(node).display !== 'none';
  const sourceState = () => ({ context: api.planNow().context, schedule: api.scheduleNow(),
    actual: api.actualNow(), commands: window.__toWorklet.length });
  const unchanged = before => api.planNow().context === before.context && api.scheduleNow() === before.schedule
    && api.actualNow() === before.actual && window.__toWorklet.length === before.commands;
  const editor = () => !document.querySelector('main').hidden && api.editorShown() && $('browse').hidden && $('randomhead').hidden;
  api.showPanel(null);
  await api.storeNow.save({ id: 'navigation', name: 'Navigation playlist', tracks: [{ url: 'https://example.test/navigation/kept.mod', name: 'Kept' }] });
  await api.choosePlaylist('navigation');
  const built = archive.toRecords('1\tProtracker/Navigation/a.mod\n1\tProtracker/Navigation/b.mod');
  await api.catalogueStore.replacePrefix('modland:', [...built.records, { key: 'modland:meta', tracks: 2, complete: true }]);
  const enterRandom = async () => {
    $('tab-browse').click();
    await until(() => [...$('browselist').children].some(row => row.querySelector('.bname')?.textContent === 'Random'));
    [...$('browselist').children].find(row => row.querySelector('.bname')?.textContent === 'Random').click();
    await until(() => !$('randomhead').hidden && api.rememberedRandomNow()?.history.playedPicks.length);
  };
  await enterRandom();
  check(await until(() => window.__toWorklet.some(message => message.type === 'open')), 'Random selection reaches its real decoder open');
  const open = window.__toWorklet.filter(message => message.type === 'open').at(-1);
  api.onWorklet({ type: 'opened', requestId: open?.requestId, describe: 'title\tNavigation tune', subsongs: 1,
    current: 0, duration: 90, rate: 44100, canSeek: true });
  const before = sourceState();
  check(label($('random-leave')) === 'Playlist', 'Random has the same explicit Playlist exit as Android');
  check(visible($('random-back')) && label($('random-back')) === 'Back', 'Random separates Back from Playlist');
  check($('sessionicon').hasAttribute('hidden') && !visible($('sessionicon')),
    'the decorative SVG dice is actually hidden, not an unreflected JavaScript hidden property');
  check($('random-dice').textContent.trim() === '' && $('random-dice').getAttribute('aria-label') === 'Start a new Random list'
    && $('random-dice').getAttribute('aria-describedby') === 'randomhint', 'one accessible icon dice uses the native gesture hint without a second New list button');
  check(!visible(document.querySelector('header')), 'Random replaces playlist-only toolbar actions with its destination controls');
  if (!$('random-back')) return;
  $('random-leave').click();
  check(editor() && visible(document.querySelector('header')) && unchanged(before), 'Playlist goes directly to the editor and keeps Random playback');
  check(document.activeElement === $('playlistchip'), 'Playlist return restores focus to the visible playlist chooser');
  await enterRandom(); $('random-back').click();
  check(await until(() => !$('browse').hidden && $('browsetitle').textContent === 'Browse') && unchanged(before),
    'Random Back returns to Browse root without replacing the playback source');
  check(await until(() => document.activeElement === $('browsetitle')), 'Random Back places focus on the destination heading');
  check(visible($('browseback')) && label($('browseclose')) === 'Playlist', 'Browse root keeps separate Back and Playlist actions');
  $('browseback').click();
  check(editor() && unchanged(before), 'Back at Browse root exits to the playlist in one action');
  for (const path of [['history'], ['search'], ['saved'], ['catalogues'], ['modland'], ['modland', 'Protracker'], ['modland', 'Protracker', 'Navigation']]) {
    api.showPanel('browse'); await api.browseTo(path);
    check(visible($('browseclose')) && label($('browseclose')) === 'Playlist'
      && !visible(document.querySelector('header')), `${path.join('/')} exposes the common Playlist exit instead of Close`);
    $('browseclose').click();
    check(editor() && unchanged(before), `${path.join('/')} Playlist preserves the audible source and returns to the editor`);
  }
  await enterRandom();
  const session = api.rememberedRandomNow();
  const commands = window.__toWorklet.length;
  $('random-dice').dispatchEvent(new window.MouseEvent('click', { bubbles: true, detail: 1 }));
  check(api.rememberedRandomNow() === session && window.__toWorklet.length === commands, 'one pointer click on the only dice does not reroll');
  $('showqueue').click();
  check(label($('queue-close')) === 'Back' && label($('queue-clear')) === 'Clear queue', 'Queue names its Back and Clear queue actions as Android does');
  $('queue-close').click();
  check(!$('randomhead').hidden && api.rememberedRandomNow() === session && unchanged(before), 'Queue Back restores the Random destination without playing anything');
  document.querySelector('input[name="language"][value="pl"]').click();
  check(label($('random-back')) === 'Wstecz' && label($('random-leave')) === 'Playlista', 'Random navigation translates in place into Polish');
  $('random-back').click(); await until(() => !$('browse').hidden);
  check(label($('browseclose')) === 'Playlista' && label($('browseback')) === 'Wstecz', 'Browse uses the same Polish exit and Back captions');
  $('showqueue').click();
  check(label($('queue-close')) === 'Wstecz' && label($('queue-clear')) === 'Wyczyść kolejkę', 'Queue uses the Native Polish Back and Clear queue captions');
  document.querySelector('input[name="language"][value="en"]').click();
  check(unchanged(before), 'language and destination changes preserve source, queue and audio commands');
  check(window.__browserErrors.length === 0, 'navigation leaves no unhandled browser errors');
}
