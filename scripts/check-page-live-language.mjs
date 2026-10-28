// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later

/** Native language-policy cases through radio events; audio output itself remains a host stub. */
export default async function checkLiveLanguage({window,check}) {
  const {document}=window,$=(id)=>document.getElementById(id),api=window.__api;
  const settle=(ms=70)=>new Promise((resolve)=>setTimeout(resolve,ms));
  const radio=(choice)=>document.querySelector(`input[name="language"][value="${choice}"]`);
  const oldLanguages=Object.getOwnPropertyDescriptor(window.navigator,'languages'), oldClock=window.performance.now;
  let now=2000000; window.performance.now=()=>now;
  const bytes=new Uint8Array(64);bytes[0]=42;
  const tracks=[{url:'https://example.test/language/source.it',name:'Search',file:'source.it',data:bytes.buffer},
    {url:'https://example.test/language/manual.it',name:'Random',file:'manual.it',data:bytes.slice().buffer}];
  const send=(message)=>window.__workletPort.onmessage({data:{...message,requestId:window.__toWorklet.filter((m)=>m.type==='open').at(-1)?.requestId}});
  const navigations=()=>window.__browserErrors.filter((e)=>/navigation|reload/i.test(e.message)).length;
  try {
    await api.discardEdits();api.showPanel(null);api.clearPlaybackQueue();
    await api.storeNow.save({id:'language-raw-name',name:'Random',tracks:[{url:'https://example.test/language/editor.it',name:'Search'}],index:0});await api.choosePlaylist('language-raw-name');
    api.playFromBrowse(tracks,0,'browse');await settle();
    send({type:'opened',describe:'title\tNothing playing\nartist\tSearch\nyear\t1998\nformat\tIT',duration:120,rate:44100,canSeek:true,subsongs:3,current:0});
    await settle();now+=2000;send({type:'position',seconds:2});await settle();
    await api.choosePlaylist('language-raw-name');
    await api.enqueueTrack(tracks[1]);await api.enqueueTrack(tracks[1]);api.showPanel('settings');
    const before={audio:api.contextNow(),context:api.planNow().context,editor:api.editorNow(),schedule:api.scheduleNow(),track:api.actualNow(),
      data:api.actualNow().data,commands:window.__toWorklet.length,pending:api.scheduleNow().pending.map((r)=>r.id).join('|'),
      row:$('queue').firstElementChild,navigations:navigations()};
    radio('pl').click();
    check(document.documentElement.lang==='pl'&&document.querySelector('#settings h2').textContent.includes('Ustawienia'),'actual Polish radio changes static interface text in place');
    check($('snacktext').textContent==='Dodano do kolejki: Random','a visible action message translates its meaning while keeping the raw track name');
    check(api.contextNow()===before.audio&&api.planNow().context===before.context&&api.editorNow()===before.editor&&api.scheduleNow()===before.schedule,'language change retains audio context, playback context, editor and schedule identities');
    check(api.actualNow()===before.track&&api.actualNow().data===before.data&&before.data.byteLength===64&&new Uint8Array(before.data)[0]===42,'language change preserves the actual held file buffer without detachment');
    check(window.__toWorklet.length===before.commands&&$('playpause').title==='Pauza','language selection sends no decoder or transport command');
    check(api.scheduleNow().pending.map((r)=>r.id).join('|')===before.pending&&!api.dirtyNow()&&before.row!==null&&$('queue').firstElementChild===before.row,'language selection preserves duplicate FIFO, clean draft and existing playlist rows');
    check($('title').textContent==='Nothing playing'&&$('playlistname').textContent==='Random','raw song and playlist names identical to translation keys remain literal');
    check([...$('fields').querySelectorAll('dt')].some((node)=>node.textContent==='Wykonawca')&&[...$('fields').querySelectorAll('dd')].some((node)=>node.textContent==='Search'),'live metadata field labels translate while their raw author stays literal');
    check(navigations()===before.navigations,'language selection does not request a reload that jsdom would only report without performing');
    now+=3000;send({type:'position',seconds:5});await settle();
    check((await window.__played.recent()).find((row)=>row.url===tracks[0].url)?.listened===true,'listening qualification spans a language change instead of restarting its five seconds');
    radio('en').click();check(document.documentElement.lang==='en'&&document.querySelector('#settings h2').textContent.includes('Settings'),'actual English radio reverses the same live labels');
    Object.defineProperty(window.navigator,'languages',{configurable:true,value:['pl-PL']});radio('system').click();
    check(document.documentElement.lang==='pl'&&window.localStorage.getItem('protracktor.language')==='system','System retains its own policy and resolves the browser language');
    Object.defineProperty(window.navigator,'languages',{configurable:true,value:['en-US']});window.dispatchEvent(new window.Event('languagechange'));
    check(document.documentElement.lang==='en','System follows a real languagechange event without a page reload');
    radio('pl').click();window.dispatchEvent(new window.Event('languagechange'));
    check(document.documentElement.lang==='pl','an explicit choice ignores later System-language events');
    radio('en').click();const set=window.Storage.prototype.setItem;
    try {
      window.Storage.prototype.setItem=function(key,value){if(key==='protracktor.language')throw new Error('locale write refused');return set.call(this,key,value);};
      radio('pl').click();
      check(document.documentElement.lang==='pl'&&radio('pl').checked&&window.localStorage.getItem('protracktor.language')==='en','refused language persistence keeps the session choice without claiming a saved change');
      check(!$('snackbar').hidden&&$('snacktext').textContent.includes('locale write refused'),'actual language write refusal is visible in the selected language');
    } finally {window.Storage.prototype.setItem=set;}
    radio('en').click();$('playpause').click();await settle();const paused=window.__toWorklet.length;
    radio('pl').click();radio('en').click();
    check($('playpause').title==='Play'&&window.__toWorklet.length===paused,'changing language while paused does not resume or reopen the track');
    check(navigations()===before.navigations,'round trips, System and failed saves never request browser navigation');
  } finally {
    radio('en').click();window.performance.now=oldClock;
    if(oldLanguages)Object.defineProperty(window.navigator,'languages',oldLanguages);else delete window.navigator.languages;
    api.showPanel(null);
  }
}
