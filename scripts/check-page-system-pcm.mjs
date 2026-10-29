// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later
import assert from 'node:assert/strict';
const until = async condition => {
  const deadline = Date.now() + 15000;
  while (Date.now() < deadline) {
    if (await condition()) return true;
    await new Promise(resolve => setTimeout(resolve, 5));
  }
  return false;
};
export async function verify({window,check}) {
 const api=window.__api, bridge=window.__loudnessBridge, handlers=window.__mediaHandlers, $=id=>window.document.getElementById(id);
 const track=file=>({url:`https://example.test/loudness-bridge/${file}`,file,name:file});
 const tracks=['source-a.mod','source-b.mod','source-c.mod'].map(track);
 const live=()=>bridge.processor?.handle && !bridge.processor.awaitingPreparation && bridge.processor.playing
  && $('playpause').title==='Pause' && bridge.commands.findLast(m=>m.type==='open')?.requestId===bridge.replies.findLast(m=>m.type==='opened')?.requestId;
 try {
  await api.storeNow.save({id:'system-pcm',name:'System PCM',tracks,index:0});
  await api.choosePlaylist('system-pcm');
  $('queue').querySelector('li.track').click();
  window.document.body.click();
  assert(await until(live));
  for (const normalized of [false, true]) {
   if ($('settings-normalization').checked !== normalized) $('settings-normalization').click();
   assert(await until(() => bridge.processor.normalizationEnabled === normalized));
   const fresh = track(`held-source-${normalized}.mod`);
   await api.storeNow.save({ id: 'held-source', name: 'Held source', tracks: [fresh], index: 0 });
   await api.choosePlaylist('held-source');
   check(bridge.callback().some(value => Math.abs(value) > .001), 'viewing another playlist keeps the old source sounding');
   const fetch = window.fetch;
   let release;
   window.fetch = (url, options) => String(url) === fresh.url
    ? new Promise(resolve => { release = () => resolve(fetch(url, options)); }) : fetch(url, options);
   const count = await window.__played.count();
   try {
    $('queue').querySelector('li.track').click();
    assert(await until(() => release));
    check(bridge.processor.handle === 0 && bridge.callback().every(value => value === 0),
     `an explicit source selection closes old PCM before its held fetch (${normalized ? 'normalized' : 'original'})`);
    check(await window.__played.count() === count, 'a selected file awaiting bytes does not enter History');
    await handlers.pause();
    const deliver = release; release = null; deliver();
    await new Promise(resolve => setTimeout(resolve, 25));
    check(bridge.processor.handle === 0 && bridge.callback().every(value => value === 0)
     && await window.__played.count() === count, 'Pause rejects the late selected fetch without reopening PCM or History');
   } finally { window.fetch = fetch; release?.(); }
   await handlers.play();
   assert(await until(() => live() && api.actualNow().url === fresh.url));
   check(bridge.callback().some(value => Math.abs(value) > .001), 'explicit Play after cancellation opens the selected source normally');
  }
  if ($('settings-normalization').checked) $('settings-normalization').click();
  await api.choosePlaylist('system-pcm');
  $('queue').querySelector('li.track').click();
  assert(await until(() => live() && api.actualNow().url === tracks[0].url));
  const handle=bridge.processor.handle,engine=bridge.processor.engine;
  check(bridge.callback().some(v=>Math.abs(v)>.001),'actual system fixture renders real WASM PCM');
  const before=engine._pt_position(handle),opens=bridge.commands.filter(m=>m.type==='open').length;
  await handlers.play();
  await handlers.play();
  check(bridge.processor.handle===handle&&bridge.commands.filter(m=>m.type==='open').length===opens&&bridge.callback().some(v=>Math.abs(v)>.001),
   'repeated system Play keeps one real handle rendering without reopen');
  await handlers.pause();
  await handlers.pause();
  assert(await until(()=>!bridge.processor.playing));
  const paused=engine._pt_position(handle);
  check(paused>before&&bridge.callback().every(v=>v===0)&&engine._pt_position(handle)===paused,'repeated system Pause silences real callbacks without moving the decoder');
  await handlers.play();
  assert(await until(live));
  check(bridge.processor.handle===handle&&bridge.callback().some(v=>Math.abs(v)>.001),'system Play resumes PCM in the original handle');
  if (handlers.seekto) {
   await handlers.seekto({seekTime:1});
   assert(await until(()=>bridge.replies.findLast(m=>m.type==='seeked')));
   const landed=bridge.replies.findLast(m=>m.type==='seeked').seconds;
   check(engine._pt_position(handle)===landed&&window.__mediaPositions.at(-1).position===landed,'system seek acknowledges the actual native position through the shared page path');
   }
  await api.enqueueTrack(tracks[1]);
  await api.enqueueTrack(tracks[1]);
  const ids=api.scheduleNow().pending.map(r=>r.id);
  await handlers.nexttrack();
  assert(await until(()=>live()&&api.actualNow().url===tracks[1].url&&api.scheduleNow().pending.length===1));
  check(api.scheduleNow().pending[0].id===ids[1]&&bridge.callback().some(v=>Math.abs(v)>.001),'system Next renders the first duplicate manual request and keeps the second');
  const manualOpenCount=bridge.commands.filter(m=>m.type==='open').length;
  await handlers.nexttrack();
  assert(await until(()=>live()&&api.scheduleNow().pending.length===0&&bridge.commands.filter(m=>m.type==='open').length===manualOpenCount+1));
  check(api.actualNow().url===tracks[1].url&&bridge.callback().some(v=>Math.abs(v)>.001),'the second identical manual request plays separately rather than disappearing');
  await handlers.previoustrack();
  assert(await until(()=>live()&&api.actualNow().url===tracks[0].url));
  check(bridge.callback().some(v=>Math.abs(v)>.001),'Previous returns to the actual source after manual playback');
  const stoppedHandle=bridge.processor.handle,stoppedPosition=engine._pt_position(stoppedHandle),source=api.planNow().context;
  await handlers.stop();
  assert(await until(()=>!bridge.processor.playing));
  check(bridge.processor.handle===stoppedHandle&&bridge.callback().every(v=>v===0)&&engine._pt_position(stoppedHandle)===stoppedPosition
   &&api.planNow().context===source&&window.navigator.mediaSession.metadata===null,'system Stop retains real decoder position and source while silencing PCM and dismissing metadata');
  await handlers.play();
  assert(await until(live));
  check(bridge.processor.handle===stoppedHandle&&bridge.callback().some(v=>Math.abs(v)>.001),'Play after Stop resumes the same real PCM stream');
  const subsongTrack = track('system-subsongs.nsf');
  await api.storeNow.save({ id: 'system-subsongs', name: 'System subsongs', tracks: [subsongTrack], index: 0 });
  await api.choosePlaylist('system-subsongs');
  $('queue').querySelector('li.track').click();
  assert(await until(() => live() && api.actualNow().url === subsongTrack.url));
  api.showPanel('nowplaying');
  await handlers.pause();
  assert(await until(() => !bridge.processor.playing));
  $('subsongs').children[0].click();
  assert(await until(() => live() && engine._pt_current_subsong(bridge.processor.handle) === 0));
  check(window.navigator.mediaSession.playbackState === 'playing', 'choosing an actual tune chip from Pause restarts the real decoder and system state together');
  $('subsongs').children[1].click();
  assert(await until(() => live() && engine._pt_current_subsong(bridge.processor.handle) === 1));
  check(bridge.callback().some(value => Math.abs(value) > .001), 'the next actual tune chip renders the intended audible native subsong');
  $('settings-normalization').click();
  bridge.hold=true;
  const fresh=track('system-cancelled.mod');
  await api.storeNow.save({id:'system-cancelled',name:'System cancelled',tracks:[fresh],index:0});
  await api.choosePlaylist('system-cancelled');
  const count=await window.__played.count();
  $('queue').querySelector('li.track').click();
  assert(await until(()=>bridge.held.length&&bridge.processor.awaitingPreparation));
  await handlers.pause();
  assert(await until(()=>bridge.processor.handle===0));
  bridge.hold=false;
  for(const release of bridge.held.splice(0))release();
  await new Promise(r=>setTimeout(r,25));
  check(bridge.processor.handle===0&&bridge.callback().every(v=>v===0)&&await window.__played.count()===count,'system Pause cancels real private startup and its late analysis without PCM or History');
  await handlers.play();
  assert(await until(live));
  check(bridge.callback().some(v=>Math.abs(v)>.001),'an explicit system Play can recover after the cancelled private startup');
 } finally {
  await bridge.cleanup();
 }
}
