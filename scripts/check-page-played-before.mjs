// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later
// Native ListeningSession, HistoryWrites and PlayedBeforeUi scenarios through page/port routes.
import * as archive from '../web/src/catalogue.js';
export default async function checkPlayedBefore({ window, check }) {
  console.log('\nPlayed before through playback and real page controls:');
  const api = window.__api, played = window.__played, $ = (id) => window.document.getElementById(id);
  const settle = (ms = 70) => new Promise((resolve) => setTimeout(resolve, ms));
  const originalClock = window.performance.now; let now = 1000000;
  window.performance.now = () => now;
  const send = (message) => window.__workletPort.onmessage({ data: { ...message, requestId: latestOpen()?.requestId } });
  const latestOpen = () => window.__toWorklet.filter((message) => message.type === 'open').at(-1);
  const track = (name) => ({ url: `https://modland.com/pub/modules/Protracker/Played/${name}.mod`, name: `${name}.mod`, meta: 'Protracker/Played' });
  const a = track('five'), b = track('short'), c = track('seek'), d = track('clear');
  const row = async (entry) => (await played.recent()).find((item) => item.url === entry.url);
  const position = (seconds, milliseconds = 1000) => { now += milliseconds; send({ type: 'position', seconds }); };
  const open = async (entry, duration = 90) => {
    api.openAway([entry], 0, 'browse'); await settle();
    window.document.body.dispatchEvent(new window.Event('pointerdown', { bubbles: true })); await settle(0);
    send({ type: 'opened', duration, subsongs: 1, current: 0, describe: `title\tKnown ${entry.name}`, rate: 44100, canSeek: true });
    await settle();
  };
  const clearViaHistory = async () => {
    api.showPanel('browse'); await api.browseTo(['history']);
    const action = [...$('browselist').children].find((item) => item.textContent.includes('Clear the history'));
    if (!action) throw Error('The actual History clear action is missing');
    action.click(); await settle();
  };
  try {
    api.showPanel(null); api.clearPlaybackQueue(); await played.clear();
    for (let i = 0; i < 3 && $('repeat').classList.contains('on'); i++) $('repeat').click();
    if ($('shuffle').classList.contains('on')) $('shuffle').click();
    if ($('allsubsongs').getAttribute('aria-checked') === 'true') $('allsubsongs').click();
    await open(a); check((await row(a))?.listened === false, 'a successful start records History without immediately qualifying');
    for (let i = 1; i <= 4; i++) position(i);
    await settle(); check((await row(a)).listened === false, 'four progressing seconds remain unqualified');
    const before = await row(a); position(5); await settle();
    const qualified = await row(a);
    check(qualified.listened === true && qualified.playCount === before.playCount && qualified.playedAt === before.playedAt,
      'five progressing seconds qualify without reordering History or incrementing its count');

    await open(b, 3); now += 3000; send({ type: 'ended', seconds: 3 }); await settle();
    check((await row(b)).listened === true, 'the actual EOF message qualifies a naturally completed three-second file');
    const tiny = track('tiny'); await open(tiny, 0.08); now += 80; send({ type: 'ended', seconds: 0.08 }); await settle();
    check((await row(tiny)).listened === true, 'EOF qualifies an eighty-millisecond file before any periodic position report');
    const timed = track('known-length'); await open(timed, 3); position(3, 3000); await settle();
    check((await row(timed)).listened === true, 'a verified known-length cutoff also counts as natural completion');

    await open(c, 3); position(0.5, 500);
    $('seek').value = '1000'; $('seek').dispatchEvent(new window.Event('input')); $('seek').dispatchEvent(new window.Event('change'));
    send({ type: 'seeked', seconds: 3 }); now += 20; send({ type: 'ended', seconds: 3 }); await settle();
    check((await row(c)).listened === false, 'seeking to the end through the actual slider never qualifies a short listen');

    const paused = track('paused'); await open(paused); position(1);
    $('playpause').click(); await settle(); now += 60000;
    send({ type: 'position', seconds: 1 }); await settle();
    check((await row(paused)).listened === false, 'paused wall time and an unchanged decoder position cannot qualify');
    $('playpause').click(); await settle(); position(2); position(3); position(4); position(5); await settle();
    check((await row(paused)).listened === true, 'resume retains earned audio rather than earning the paused minute');

    const subs = track('subs'); await open(subs); position(1); position(2); position(3);
    send({ type: 'subsong', index: 1, duration: 90, describe: 'title\tSecond' }); position(1); position(2); await settle();
    check((await row(subs)).listened === true, 'a subsong position reset preserves the five seconds earned for its file');

    const skip = track('skip'); await open(skip); position(1); await open(d); await settle();
    check((await row(skip)).listened === false, 'switching files does not treat an interrupted short listen as completion');
    position(1); await clearViaHistory(); position(8, 7000); send({ type: 'ended', seconds: 8 }); await settle();
    check(await played.count() === 0, 'History Clear prevents the same continuing occurrence and EOF from restoring its row');
    $('playpause').click(); await settle(); position(5, 5000); await settle();
    check((await row(d))?.listened === true, 'an explicit fresh replay after Clear may record and qualify again');

    const queueKept = track('queue-kept'); await open(queueKept); position(1); position(2);
    api.clearPlaybackQueue(); position(3); position(4); position(5); await settle();
    check((await row(queueKept)).listened === true, 'Queue Clear keeps the listening candidate of its retained audible occurrence');

    const delayed = track('delayed'); api.openAway([delayed], 0, 'browse'); await settle();
    await clearViaHistory(); send({ type: 'opened', duration: 3, subsongs: 1, current: 0, describe: '', rate: 44100 });
    now += 3000; send({ type: 'ended', seconds: 3 }); await settle();
    check(await played.count() === 0, 'Clear during a pending decoder open retires its captured write and qualification ticket');

    await open(a); position(5, 5000); await settle();
    const raw = await row(a); api.openAway([a], 0, 'history'); await settle();
    send({ type: 'opened', duration: 90, subsongs: 1, current: 0, describe: '', rate: 44100 }); position(5, 5000); await settle();
    const unchanged = await row(a);
    check(raw.playedAt === unchanged.playedAt && raw.playCount === unchanged.playCount,
      'History-origin playback may qualify its row while preserving recency and play count');

    const failed = track('failed'); api.openAway([failed], 0, 'browse'); await settle();
    send({ type: 'failed', reason: 'decoder refusal' }); position(6, 6000); send({ type: 'ended', seconds: 6 }); await settle();
    check(!(await row(failed)), 'a decoder refusal cannot record or qualify a nonexistent listen');

    await api.storeNow.save({ id: 'played-test', name: 'Played test', tracks: [a, b] });
    await api.switchTo('played-test'); api.showPanel(null); api.render(); await settle();
    check($('queue').querySelectorAll('.played-before').length === 0, 'playlist rows opt out even for qualified tracks');
    api.showPanel('browse'); await api.browseTo(['history']); await settle();
    let badge = $('browselist').querySelector(`[data-url="${a.url}"] .played-before`);
    check(badge && !badge.hidden && badge.getAttribute('aria-label') === 'Played before', 'Browse History shows an accessible discreet leading mark');
    check(badge?.closest('.track-leading') && !badge.closest('.btext'), 'the mark occupies the lower leading slot and never prefixes the source path');
    const phoneUrl = 'content://phone/previous-listen';
    await played.record({ url: phoneUrl, name: 'From phone', file: 'phone.mod', replayable: false });
    await played.qualify({ trackId: phoneUrl, historyGeneration: played.generation });
    await api.browseTo(['history']); await settle();
    const phoneRow = $('browselist').querySelector(`[data-url="${phoneUrl}"]`);
    check(phoneRow.classList.contains('gone') && !phoneRow.querySelector('.played-before').hidden && !phoneRow.onclick,
      'History also shows an earned mark for a phone-only entry without offering an impossible replay');
    const info = api.informAbout(a); await settle();
    const describe = window.__toWorklet.filter((message) => message.type === 'describe').at(-1);
    send({ type: 'described', id: describe.id, ok: true, describe: 'title\tInformation', subsongs: 1 }); await info; await settle();
    check(!$('np-played').hidden && $('np-played').textContent.includes('Clearing History removes this mark'), 'Browse Info explains exactly what the mark proves and how Clear removes it');
    api.showPanel(null); $('queue').querySelector('.rowmenu').click();
    [...$('menu').querySelectorAll('button')].find((button) => button.textContent.includes('Information')).click(); await settle();
    const playlistInfo = window.__toWorklet.filter((message) => message.type === 'describe').at(-1);
    send({ type: 'described', id: playlistInfo.id, ok: true, describe: 'title\tPlaylist info', subsongs: 1 }); await settle();
    check($('np-played').hidden, 'Information opened through a playlist row also opts out of the mark');
    api.showPanel('browse'); await api.browseTo(['history']); await settle();
    badge = $('browselist').querySelector(`[data-url="${a.url}"] .played-before`);
    const beforeStyle = window.getComputedStyle(badge.closest('.track-leading'));
    const size = [beforeStyle.width, beforeStyle.minWidth, beforeStyle.height].join('/');
    await clearViaHistory(); await settle();
    check([...window.document.querySelectorAll('[data-played-url]')].every((item) => item.hidden), 'Clear immediately retires all displayed marks');
    check(size.includes('28px') && size.endsWith('40px'), 'the leading slot has a stable 28 by 40 CSS geometry independent of the mark');

    // A one-track actual Random pool still includes the track even though it is qualified and saved.
    const built = archive.toRecords('1\tProtracker/Played/five.mod', 'modland', () => true);
    await api.catalogueStore.clear('asma:');
    await api.catalogueStore.replacePrefix('modland:', [...built.records, { key: 'modland:meta', tracks: 1, total: 1, complete: true }]);
    api.setRandomScope('everything');
    send({ type: 'ready', backends: 'openmpt:check;uade:none' });
    await open(a); position(5, 5000); await settle();
    await api.rerollRandom(); await settle();
    const randomBadge = $('queue').querySelector(`[data-played-url="${a.url}"]`);
    check(api.actualNow()?.url === a.url && randomBadge && !randomBadge.hidden,
      'Random draws qualified tracks and shows their mark even when they also belong to a playlist');

    const live = track('live'); await open(live);
    api.showPanel('browse'); await api.browseTo(['history']); await settle();
    const liveRow = $('browselist').querySelector(`[data-url="${live.url}"]`), liveBadge = liveRow.querySelector('.played-before');
    check(liveBadge.hidden, 'a visible unqualified Browse row reserves its mark slot without claiming a listen');
    position(5, 5000); await settle();
    check(!liveBadge.hidden && $('browselist').querySelector(`[data-url="${live.url}"]`) === liveRow,
      'qualification refreshes the existing visible row without rebuilding or reordering its list');

    const stalled = track('stalled'); await open(stalled); position(4.9, 60000); await settle();
    check((await row(stalled)).listened === false, 'a delayed poll showing less than five seconds cannot earn a minute of wall time');
    const empty = track('empty'); await open(empty); now += 3000; send({ type: 'ended', seconds: 0 }); await settle();
    check((await row(empty)).listened === false, 'EOF with no progressing audio never qualifies');

    window.__i18n.useLanguage('pl'); await open(a); position(5, 5000); await settle();
    api.showPanel('browse'); await api.browseTo(['history']); await settle();
    badge = $('browselist').querySelector(`[data-url="${a.url}"] .played-before`);
    check(badge && !badge.hidden && badge.getAttribute('aria-label') === 'Już odsłuchane', 'the displayed mark and its accessible description are localized in Polish');
  } finally { window.performance.now = originalClock; window.__i18n.useLanguage('en'); }
}
