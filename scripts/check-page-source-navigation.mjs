// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later
export default async function checkSourcePage({window,check}) {
  const api=window.__api, $=(id)=>window.document.getElementById(id);
  const settle=(ms=90)=>new Promise((resolve)=>setTimeout(resolve,ms));
  const rows=()=>[...$('queue').querySelectorAll('li.track')];
  const tracks=['a0','a1'].map((name)=>({name,url:`https://example.test/source/${name}.mod`}));
  const other=['b0','b1'].map((name)=>({name,url:`https://example.test/source/${name}.mod`}));
  await api.storeNow.save({id:'source-a',name:'Source A',tracks,index:0});
  await api.storeNow.save({id:'source-b',name:'Source B',tracks:other,index:0});
  await api.choosePlaylist('source-a'); rows()[0].click(); await settle();
  const opened=window.__toWorklet.filter((entry)=>entry.type==='open').at(-1);
  api.onWorklet({type:'opened',requestId:opened.requestId,describe:'title\tSource playback',duration:90,rate:44100,subsongs:1,current:0,canSeek:true});
  await api.choosePlaylist('source-b'); $('showqueue').click();
  const before=api.planNow(), commands=window.__toWorklet.length;
  check(!$('queue-source').hidden && $('queue-source').getAttribute('aria-label')==='Go to source: Source A',
    'the existing Queue source label is an accessible inline navigation action');
  $('queue-source').click(); await settle();
  check(api.editorNow().id==='source-a' && $('playback-panel').hidden && api.planNow().context===before.context
    && api.scheduleNow()===before.schedule && window.__toWorklet.length===commands,
    'the actual source-header click reveals A while preserving its playback and manual schedule');
  check(window.document.activeElement === $('playlistchip'), 'playlist source navigation moves focus to the visible destination');
  await api.choosePlaylist('source-b'); rows()[0].querySelector('.rowmenu').click();
  [...$('menu').children].find((button)=>button.textContent.startsWith('Remove')).click();
  check(api.dirtyNow(),'the source-return fixture has a real unsaved draft');
  $('showqueue').click(); $('queue-source').click(); await settle();
  check(!$('unsaved').hidden && !$('playback-panel').hidden,'source return keeps Queue beneath the unsaved-edits choice');
  $('unsaved').click(); await settle();
  check($('unsaved').hidden && !$('playback-panel').hidden && api.editorNow().id==='source-b' && api.dirtyNow(),
    'Cancel through the dialog scrim retains Queue and the selected draft');
  $('queue-source').click(); await settle(); $('unsaved-discard').click(); await settle();
  check($('playback-panel').hidden && api.editorNow().id==='source-a' && (await api.storeNow.get('source-b')).tracks.length===2
    && window.__toWorklet.length===commands,'Discard abandons the other draft only when revealing the guarded source, without a playlist write');
  await api.choosePlaylist('source-b');
  await api.storeNow.remove('source-a'); $('showqueue').click(); $('queue-source').click(); await settle();
  check(!$('playback-panel').hidden && api.editorNow().id==='source-b' && !await api.storeNow.get('source-a')
    && $('snacktext').textContent==='The playback source is no longer available.',
    'a deleted source is explained and never recreated as an empty playlist');

  $('queue-close').click();
  const { catalogue, played } = await import('../web/src/store.js');
  const path = ['modland', 'Video Game Music/Sega Megadrive', 'Navigation'];
  await catalogue.putAll([{key:`modland:tracks:${path[1]}/${path[2]}`,tracks:[{t:'one.vgz',s:64},{t:'two.vgz',s:64}]}]);
  api.showPanel('browse'); await api.browseTo(path);
  $('browselist').querySelector('.btrack').click(); await settle();
  const sourceUrls=[...$('browselist').querySelectorAll('.btrack')].map((row)=>row.dataset.url);
  check(api.planNow().context.route.path.join('\0')===path.join('\0'),
    'a real folder playback captures path components even when the format contains slashes');
  api.showPanel(null); api.endSession(); await catalogue.clear('modland:');
  const folderBefore=api.planNow(), folderCommands=window.__toWorklet.length;
  $('showqueue').click(); $('queue-source').click(); await settle();
  check(!$('browse').hidden && $('browsetitle').textContent===`${path[1]} / ${path[2]}`
    && [...$('browselist').querySelectorAll('.btrack')].map((row)=>row.dataset.url).join()===sourceUrls.join()
    && api.planNow().context===folderBefore.context && window.__toWorklet.length===folderCommands,
    'folder source return restores captured rows without reloading an index or changing playback');
  check(window.document.activeElement === $('browsetitle'), 'folder source navigation focuses its visible heading without opening the keyboard');
  $('browseback').click(); await settle();
  check($('browsetitle').textContent===path[1], 'Back from a restored folder follows its real catalogue hierarchy');
  const h={url:'https://example.test/history-source/old.mod',name:'History source row',file:'old.mod',replayable:true};
  await played.record(h); await api.browseTo(['history']);
  const historyUrls=[...$('browselist').querySelectorAll('.btrack')].map((row)=>row.dataset.url);
  [...$('browselist').querySelectorAll('.btrack')].find((row)=>row.dataset.url===h.url).click(); await settle();
  api.showPanel(null); api.endSession();
  await played.record({url:'https://example.test/history-source/new.mod',name:'New after capture',file:'new.mod',replayable:true});
  const historyBefore=api.planNow(), historyCommands=window.__toWorklet.length;
  $('browselist').scrollTop = 777;
  $('showqueue').click(); $('queue-source').click(); await settle();
  check(!$('browse').hidden && $('browsetitle').textContent==='History'
    && [...$('browselist').querySelectorAll('.btrack')].map((row)=>row.dataset.url).join()===historyUrls.join()
    && api.planNow().context===historyBefore.context && window.__toWorklet.length===historyCommands,
    'History source return restores the captured page without recording or starting a replay');
  check($('browselist').scrollTop === 0, 'returning to the History source starts its fresh scroll visit at the top');
  $('browseback').click(); await settle();
  check($('browsetitle').textContent==='Browse','Back from restored History goes to Browse root');
  api.clearPlaybackQueue(); api.showPanel(null); $('showqueue').click();
  check($('queue-source').hidden && !$('queue-source-plain').hidden && $('queue-source-plain').textContent==='Manually queued',
    'a manual-only queue retains a plain source label without a fabricated destination');
  check(window.getComputedStyle($('queue-source')).display === 'none',
    'the plain-source header hides its jump action in computed styling');
  api.openAway(tracks,0,'link'); await settle();
  check($('queue-source').hidden && $('queue-source-plain').textContent==='Sent to you' && !$('next').disabled,
    'finite shared WEB links retain Next but expose a plain external-source label');
}
