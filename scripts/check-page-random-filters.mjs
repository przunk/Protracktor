// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later
// Production page counterparts of RandomScopeSheetTest and the C97 controller contract.
import * as archive from '../web/src/catalogue.js';
import { settings } from '../web/src/store.js';
import { PLATFORM_FINGERPRINT } from '../web/src/random-scope.js';

export default async function checkRandomFilters({ window, check }) {
  console.log('\nRandom filters through actual page controls:');
  const api=window.__api, $=(id)=>window.document.getElementById(id);
  const settle=(ms=100)=>new Promise((resolve)=>setTimeout(resolve,ms));
  const deferred=()=>{let resolve;const promise=new Promise((done)=>{resolve=done;});return {promise,resolve};};
  const bounded=(promise)=>Promise.race([promise,new Promise((_,reject)=>window.setTimeout(()=>reject(Error('Filter boundary not reached')),7000))]);
  const chip=(scope)=>$('randomfilter').querySelector(`[data-scope="${scope}"]`);
  const tap=async(id)=>{ $(id).dispatchEvent(new window.Event('pointerdown'));$(id).dispatchEvent(new window.Event('pointerup'));$(id).click();await settle(); };
  const command=()=>window.__toWorklet.filter((message)=>message.type==='open').at(-1);
  const opened=()=>api.onWorklet({type:'opened',requestId:command()?.requestId,describe:'title\tFilter test',duration:90,subsongs:1,current:0,rate:44100,canSeek:true});
  const records=(source,lines)=>{
    const built=archive.toRecords(lines,source,(name)=>!name.startsWith('bad'));
    return [...built.records,{key:`${source}:meta`,tracks:built.tracks,total:built.total,complete:true,
      fingerprint:lines,platformCounts:built.platformCounts,platformFingerprint:PLATFORM_FINGERPRINT}];
  };
  const modland=[...Array.from({length:6},(_,i)=>`1\tProtracker/Amiga/a${i}.mod`),
    ...Array.from({length:3},(_,i)=>`1\tFasttracker 2/PC/p${i}.xm`),'1\tFasttracker 2/PC/bad.xm',
    '1\tSpectrum/Pro Tracker 3/Author/ambiguous.psm'].join('\n');
  const asma=Array.from({length:4},(_,i)=>`1\tComposers/Atari/atari${i}.sap`).join('\n');
  api.showPanel(null);api.clearPlaybackQueue();api.setRandomScope('everything');
  await api.catalogueStore.clear('modland:');await api.catalogueStore.clear('asma:');await api.catalogueStore.clear('modland-favourites:');
  await api.catalogueStore.putAll(records('modland',modland));await api.catalogueStore.putAll(records('asma',asma));await settle();

  let reads=0;
  const originalGet=api.catalogueStore.get,entered=deferred(),release=deferred();
  api.catalogueStore.get=async(key)=>{
    if(key==='modland:meta') { reads++;if(reads===1) { entered.resolve();await release.promise; } }
    return originalGet(key);
  };
  try {
    await api.openRandom();await bounded(entered.promise);$('random-filter').click();
    check(!$('randomfilter').hidden&&$('random-filter').getAttribute('aria-expanded')==='true'
      &&!chip('everything').disabled&&chip('platform:amiga').disabled&&chip('favourites').disabled,
      'cold Filter opens with Everything usable and unknown platform/Favourites counts visibly pending');
    check($('random-filter-status').textContent==='Checking available filters…'&&$('random-favourites-download').hidden,
      'an unread availability snapshot cannot claim that the favourites list is missing');
    release.resolve();await settle();
    check(!chip('platform:amiga').disabled&&!chip('platform:pc').disabled&&!chip('platform:atari-8bit').disabled
      &&chip('platform:sharp').disabled&&chip('platform:sony').disabled,
      'fresh actual indexes enable known WEB platforms, including ASMA, while unsupported empty platforms stay disabled');
    check(api.randomFilterStateNow().availability.platformCounts.pc===3
      &&api.randomFilterStateNow().availability.platformCounts['atari-8bit']===4,
      'filter availability counts playable entries and applies whole-archive ASMA classification');
    check(!$('random-favourites-download').hidden&&$('random-favourites-note').textContent.includes("Modland's own list"),
      'only a completed read offers the missing curated-list download with its reason');
    const checkedReads=reads;
    $('random-filter').click();$('random-filter').click();await settle();
    check(reads===checkedReads&&!chip('platform:amiga').disabled,
      'closing and reopening an active Filter keeps verified chips without recounting unchanged resources');
  } finally {release.resolve();api.catalogueStore.get=originalGet;}
  const beforeChoice=window.__toWorklet.length;
  chip('platform:pc').click();await settle();
  check(api.randomScopeNow()==='platform:pc'&&$('randomfilter').hidden&&$('randomscope').textContent==='PC'
    &&window.__toWorklet.length===beforeChoice&&await settings.get('randomScope')==='platform:pc',
    'the actual platform chip selects and persists one visible scope without starting unrelated playback');
  $('random-filter').click();
  check([...$('randomfilter').querySelectorAll('.chip[aria-pressed="true"]')].length===1
    &&chip('platform:pc').querySelector('.scope-dot').style.visibility==='visible'&&chip('everything').querySelector('.scope-dot').style.visibility==='hidden',
    'one selected scope has a visible shape as well as colour and an accessible pressed state');

  await api.rerollRandom();await settle();opened();
  check(api.actualNow().name.startsWith('p')&&api.actualNow().name.endsWith('.xm'),
    'a new session really draws from the selected PC pool instead of merely changing its caption');
  for(let i=0;i<9;i++){await tap('next');opened();}
  $('queue').querySelectorAll('li.track')[4].click();await settle();opened();
  const history=api.randomState().history.playedPicks,actual=api.actualNow(),commands=window.__toWorklet.length;
  $('random-filter').click();chip('platform:atari-8bit').click();await settle();
  check(history.length===10&&api.randomState().history.playedPicks===history&&api.indexNow()===4
    &&api.actualNow()===actual&&window.__toWorklet.length===commands,
    'C97: choosing Atari in the middle of ten heard PC tracks preserves the full record, cursor and actual audio');
  for(let i=5;i<10;i++){await tap('next');opened();}
  check(api.indexNow()===9&&api.randomState().history.playedPicks===history,
    'C97: Next traverses the entire remaining heard suffix before drawing from the new filter');
  await tap('next');opened();
  check(api.indexNow()===10&&api.randomState().history.playedPicks.length===11
    &&api.actualNow().name.startsWith('atari')&&api.actualNow().url.startsWith('https://asma.atari.org/'),
    'C97: the first new draw appends exactly one Atari occurrence after the preserved heard history');

  $('random-filter').click();
  const originalFetch=window.fetch,fetched=deferred(),download=deferred();
  const full=(path)=>['hash','1','200','player','uade','mod','0','100','crc','samplecrc',path].join('\t');
  const favourites=[full('Protracker/Amiga/a1.mod'),full('Spectrum/Pro Tracker 3/Author/ambiguous.psm'),full('Protracker/Gone/missing.mod')].join('\n');
  window.fetch=(url,options)=>{
    if(String(url).endsWith('/modland_favourites.tsv')) { fetched.resolve();return download.promise; }
    return originalFetch(url,options);
  };
  try {
    $('random-favourites-download').click();await bounded(fetched.promise);await settle();
    check($('random-favourites-download').disabled&&!chip('platform:amiga').disabled&&!$('randomfilter').hidden,
      'downloading the curated list keeps known platform chips usable and the same Filter panel open');
    download.resolve({ok:true,arrayBuffer:async()=>new window.TextEncoder().encode(favourites).buffer});await settle(180);
    check(!chip('favourites').disabled&&$('random-favourites-download').hidden&&!$('randomfilter').hidden
      &&api.randomFilterStateNow().availability.favouriteCount===2&&api.randomFilterStateNow().availability.favouritesListed===3,
      'C96: a completed favourites download refreshes every chip in place using actual listed/indexed/playable counts');
  } finally {download.resolve({ok:false,status:503});window.fetch=originalFetch;}

  check(chip('favourites').getAttribute('aria-describedby')===null,
    'an enabled Favourites chip never describes itself through a hidden unavailable-list message');
  chip('favourites').click();await settle();
  const beforeFavourites=window.__toWorklet.length;
  await api.rerollRandom();await settle();opened();
  check(['a1.mod','ambiguous.psm'].includes(api.actualNow().name)&&window.__toWorklet.length>beforeFavourites,
    'the actual Favourites chip and deliberate reroll play from the held curated intersection');
  const authors=['author0','author1'].map((name)=>({name,url:`https://example.test/filter-owner/${name}.mod`}));
  api.openAway(authors,0,'browse');await settle();opened();await api.openRandom();await settle();
  const authorPlan=api.planNow(),authorCommands=window.__toWorklet.length;
  $('random-filter').click();chip('platform:pc').click();await settle();
  check(api.planNow().context===authorPlan.context&&api.scheduleNow()===authorPlan.schedule
    &&api.actualNow().url===authors[0].url&&window.__toWorklet.length===authorCommands,
    'changing a visible remembered Random filter never steals active author playback or its schedule');
  await tap('next');opened();
  check(api.actualNow().url===authors[1].url&&api.sourceNow().origin==='browse',
    'Next after that inactive Random filter choice still follows the author context');
  await api.rerollRandom();await settle();opened();$('random-filter').click();
  const obsoleteEntered=deferred(),obsoleteRelease=deferred();let heldOld=false;
  api.catalogueStore.get=async(key)=>{const result=await originalGet(key);if(key==='modland:meta'&&!heldOld){heldOld=true;obsoleteEntered.resolve();await obsoleteRelease.promise;}return result;};
  try {
    const pending=api.refreshRandomAvailability(true);await bounded(obsoleteEntered.promise);
    await api.catalogueStore.replacePrefix('modland:',records('modland',modland.split('\n').filter((line)=>!line.endsWith('/p2.xm')).join('\n')));await settle(180);
    check(api.randomFilterStateNow().availability?.platformCounts.pc===2,
      'a newer atomic catalogue replacement refreshes counts while an older noncancellable read is suspended');
    obsoleteRelease.resolve();await pending;await settle();
    check(!api.randomFilterStateNow().failed&&api.randomFilterStateNow().availability?.platformCounts.pc===2,
      'the obsolete availability read cannot restore old counts or report a stale failure after the newer result');
  } finally {obsoleteRelease.resolve();api.catalogueStore.get=originalGet;}
  const heldAvailability=api.randomFilterStateNow().availability;
  api.catalogueStore.get=async(key)=>{if(key==='modland:meta')throw Error('Controlled availability read failure');return originalGet(key);};
  try {
    await api.refreshRandomAvailability(true);
    check(api.randomFilterStateNow().failed&&api.randomFilterStateNow().availability===heldAvailability
      &&!chip('platform:amiga').disabled&&!$('random-filters-retry').hidden,
      'a warm read failure preserves verified usable chips and exposes the actual Retry action');
  } finally {api.catalogueStore.get=originalGet;}
  $('random-filters-retry').click();await settle();
  check(!api.randomFilterStateNow().failed&&$('random-filters-retry').hidden&&!chip('favourites').disabled,
    'the real Retry button refreshes availability without losing the selected scope or panel');

  const originalClear=api.catalogueStore.clear,clearEntered=deferred(),clearRelease=deferred(),confirm=window.confirm;
  api.catalogueStore.clear=async(prefix)=>{if(prefix==='modland:'){clearEntered.resolve();await clearRelease.promise;}return originalClear(prefix);};
  window.confirm=()=>true;
  try {
    api.showPanel(null);$('tab-settings').click();await settle();
    const label=[...$('settingsfields').querySelectorAll('dt')].find((node)=>node.textContent==='Modland index');
    label.nextElementSibling.querySelector('button').click();await bounded(clearEntered.promise);
    check(api.randomFilterStateNow().availability===null&&api.randomFilterStateNow().loading&&chip('platform:amiga').disabled,
      'index deletion blocks all derived availability before the asynchronous delete finishes');
    clearRelease.resolve();await settle(180);api.showPanel(null);
    if($('randomfilter').hidden)$('random-filter').click();
    check(chip('platform:amiga').disabled&&chip('favourites').disabled&&!chip('platform:atari-8bit').disabled
      &&$('random-favourites-download').hidden&&$('random-favourites-note').textContent.includes('Index Modland first'),
      'a deleted Modland index leaves ASMA usable and distinguishes a retained favourites list from an indexed playable join');
  } finally {clearRelease.resolve();api.catalogueStore.clear=originalClear;window.confirm=confirm;api.showPanel(null);}

  api.endSession();let inactiveReads=0;
  api.catalogueStore.get=async(key)=>{if(key==='modland:meta')inactiveReads++;return originalGet(key);};
  try {
    await api.catalogueStore.putAll(records('modland',modland));await settle();
    check(inactiveReads===0&&api.randomFilterStateNow().availability===null,
      'changed resources while Random is inactive invalidate old chips without querying hidden filter counts');
  } finally {api.catalogueStore.get=originalGet;}
  await api.openRandom();await settle();$('random-filter').click();
  check(!chip('platform:amiga').disabled&&!chip('favourites').disabled,
    'returning to Random reads current resources rather than reusing old disabled Browse/filter counters');
  const beforeLanguage=window.__toWorklet.length;
  window.__i18n.useLanguage('pl');api.render();await api.refreshRandomAvailability(true);
  check(chip('everything').textContent.includes('Wszystkiego')&&chip('favourites').textContent.includes('Ulubionych')
    &&window.__toWorklet.length===beforeLanguage,'the new scope controls use native Polish labels without any playback command');
  window.__i18n.useLanguage('en');api.render();await api.refreshRandomAvailability(true);
  api.endSession();api.setRandomScope('everything');
}
