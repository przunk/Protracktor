// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later

/** Settings and dock mode events must reach the existing actual transport without replacing it. */
export default async function checkPlaybackPreferences({window,check}) {
  const {document}=window,$=(id)=>document.getElementById(id),api=window.__api;
  const wait=(ms=70)=>new Promise((resolve)=>setTimeout(resolve,ms));
  const tracks=['first.kss','second.kss'].map((file)=>({url:`https://example.test/mode-controls/${file}`,name:file,file}));
  await api.discardEdits(); api.showPanel(null);
  await api.storeNow.save({id:'mode-controls',name:'Mode controls',tracks,index:0});
  await api.choosePlaylist('mode-controls');
  $('queue').firstElementChild.querySelector('.text').click(); await wait();
  api.onWorklet({type:'opened',requestId:window.__toWorklet.filter((m)=>m.type==='open').at(-1)?.requestId,
    describe:'title\tMode fixture',duration:120,rate:44100,canSeek:true,subsongs:3,current:0});
  for(let n=0;n<3&&$('repeat').title!=='Repeat off';n++) $('repeat').click();
  if($('shuffle').getAttribute('aria-pressed')==='true') $('shuffle').click();
  if($('allsubsongs').getAttribute('aria-checked')==='true') $('allsubsongs').click();
  await api.enqueueTrack(tracks[1]); await api.enqueueTrack(tracks[1]);
  const pending=api.scheduleNow().pending.map((r)=>r.id).join('|'),current=api.actualNow(),audio=api.contextNow();
  const opens=()=>window.__toWorklet.filter((m)=>m.type==='open').length, before=opens();
  $('shuffle').click();
  check(api.planNow().context.order.shuffle&&window.localStorage.getItem('protracktor.shuffle')==='1', 'actual Shuffle action persists the active transport choice');
  $('repeat').click();
  check(api.planNow().context.order.repeat==='all'&&window.localStorage.getItem('protracktor.repeat')==='all','actual Repeat action persists All');
  $('repeat').click();
  check(api.planNow().context.order.repeat==='one'&&window.localStorage.getItem('protracktor.repeat')==='one','actual Repeat action persists One');
  $('repeat').click();
  check(api.planNow().context.order.repeat==='off'&&window.localStorage.getItem('protracktor.repeat')==='off','actual Repeat action persists Off');
  api.showPanel('settings');
  $('settings-allsubsongs').click();
  check($('settings-allsubsongs').checked&&$('allsubsongs').getAttribute('aria-checked')==='true'&&window.localStorage.getItem('protracktor.allsubsongs')==='1','actual Settings checkbox updates the player switch and existing retained preference');
  $('settings-allsubsongs').focus();
  check(document.activeElement===$('settings-allsubsongs')&&document.querySelector('label[for="settings-allsubsongs"]')?.textContent.includes('Play every tune in a file')&&window.getComputedStyle(document.querySelector('label[for="settings-allsubsongs"]')).minHeight==='48px','Settings subsong choice has a real keyboard focus target and associated label');
  api.showPanel(null); $('nowcard').click(); $('allsubsongs').click();
  check(!$('settings-allsubsongs').checked&&$('allsubsongs').getAttribute('aria-checked')==='false'&&window.localStorage.getItem('protracktor.allsubsongs')==='0','actual player switch updates the same Settings choice and retained value');
  check(api.actualNow()===current&&api.contextNow()===audio&&opens()===before&&$('playpause').title==='Pause','mode controls do not reopen audio, change current or pause playback');
  check(!api.dirtyNow()&&api.scheduleNow().pending.map((r)=>r.id).join('|')===pending,'mode controls preserve playlist contents and pending duplicate FIFO occurrences');
  await api.storeNow.save({id:'mode-editor',name:'Another editor',tracks:[tracks[1]],index:0});
  await api.choosePlaylist('mode-editor');
  $('shuffle').click(); $('repeat').click();
  check(api.editorNow().id==='mode-editor'&&api.actualNow()===current&&api.planNow().context.order.repeat==='all'&&window.localStorage.getItem('protracktor.repeat')==='all','changing modes with another visible playlist persists the actual playing context');
  const set=window.Storage.prototype.setItem,stored=window.localStorage.getItem('protracktor.repeat');
  try {
    window.Storage.prototype.setItem=function(key,value){if(key==='protracktor.repeat')throw new Error('mode quota refused');return set.call(this,key,value);};
    $('repeat').click();
    check(api.planNow().context.order.repeat==='one'&&window.localStorage.getItem('protracktor.repeat')===stored,'refused mode persistence retains the in-memory choice without claiming a stored change');
    check($('snacktext').textContent.includes('mode quota refused')&&!$('snackbar').hidden,'actual mode write refusal gives visible feedback');
  } finally {window.Storage.prototype.setItem=set;}
  $('repeat').click();
  check(window.localStorage.getItem('protracktor.repeat')==='off','a later successful mode action can persist after refusal');
  api.showPanel('settings'); $('settings-allsubsongs').click(); api.showPanel(null);
  const commands=window.__toWorklet.length; $('next').click();
  check(window.__toWorklet.slice(commands).some((m)=>m.type==='subsong'&&m.index===1)&&api.actualNow()===current,'Settings-enabled play-all reaches the same Next subsong path before leaving the file');
  api.onWorklet({type:'subsong',index:1,describe:'title\tMode fixture tune 2',duration:120});
  $('nowcard').click(); $('allsubsongs').click();
  const fileCommands=window.__toWorklet.length; $('next').click(); await wait();
  check(window.__toWorklet.slice(fileCommands).some((m)=>m.type==='open')&&api.actualNow()?.url===tracks[1].url,'player-disabled play-all makes Next use the pending manual file instead of another subsong');
  check(api.scheduleNow().pending.length===1,'the first manual request is consumed independently of its duplicate');
  api.showPanel(null);
}
