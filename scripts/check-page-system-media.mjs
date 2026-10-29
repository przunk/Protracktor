// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later

export async function verify({ window, check }) {
  const api = window.__api, $ = id => window.document.getElementById(id), handlers = window.__mediaHandlers;
  const until = async condition => {
    for (let n = 0; n < 400; n++) {
      if (condition()) return true;
      await new Promise(resolve => setTimeout(resolve, 5));
    }
    return false;
  };
  const opens=()=>window.__toWorklet.filter(m=>m.type==='open');
  const messages=(type)=>window.__toWorklet.filter(m=>m.type===type);
  const tracks=['a.mod','b.mod','c.mod'].map(file=>({url:`https://example.test/system-media/${file}`,file,name:file,meta:'Modland/Protracker/Tester'}));
  await api.storeNow.save({id:'system-source',name:'System source',tracks,index:0});
  await api.choosePlaylist('system-source');
  $('queue').querySelector('li.track').click();
  window.document.body.click();
  check(await until(()=>opens().length===1),'actual row establishes one pending source');
  check(typeof handlers.play==='function'&&typeof handlers.pause==='function'&&typeof handlers.stop==='function',
    'system intents are registered before the decoder opens and independently of metadata');
  if(!handlers.play||!handlers.pause||!handlers.stop)return;
  await handlers.play();
  await handlers.play();
  check(opens().length===1 && api.actualNow().url===tracks[0].url,'repeated system Play leaves pending startup intact');
  const send=(m)=>api.onWorklet({...m,requestId:opens().at(-1).requestId});
  send({type:'opened',describe:'title\tSystem fixture\ncomposer\tComposer\nyear\t1995',duration:120,rate:44100,canSeek:true,subsongs:3,current:0});
  await until(()=>$('playpause').title==='Pause');
  const beforePlay=messages('play').length;
  await handlers.play();
  await handlers.play();
  check($('playpause').title==='Pause'&&messages('play').length===beforePlay,'Play is idempotent while playing rather than toggling twice');
  await handlers.pause();
  await handlers.pause();
  check($('playpause').title==='Play','repeated Pause stays paused');
  api.showPanel('nowplaying');
  const pickerCount = messages('subsong').length;
  $('subsongs').children[1].click();
  check(messages('subsong').length === pickerCount + 1, 'the actual tune chip reaches the shared transport');
  send({ type: 'subsong', index: 1, requestedIndex: 1, selected: true, playing: true,
    previousSeconds: 0, duration: 120, describe: 'title\tSystem fixture\ncomposer\tComposer\nyear\t1995' });
  check($('playpause').title === 'Pause', 'choosing a tune while paused publishes its actual restarted playback');
  await handlers.play();
  check($('playpause').title==='Pause'&&opens().length===1,'Play resumes the same loaded handle without reopening');
  const session=window.navigator.mediaSession;
  check(session.metadata?.title==='System fixture'&&session.metadata.artist===$('sub').textContent,'system title and subtitle reuse the actual dock summary');
  check(session.metadata?.artwork?.[0]?.sizes==='512x512','system artwork is a local format cover with the Native size');
  send({type:'position',seconds:4});
  check(window.__mediaPositions.at(-1)?.position===4&&window.__mediaPositions.at(-1)?.duration===120,'the actual position message updates finite platform position');
  const audio = api.contextNow(), beforeStateEvents = window.__toWorklet.length;
  for (const state of ['suspended', 'interrupted']) {
    audio.state = state;
    audio.dispatchEvent(new window.Event('statechange'));
    check(session.playbackState === 'paused', `a ${state} AudioContext updates system state without a worklet message`);
  }
  audio.state = 'running';
  audio.dispatchEvent(new window.Event('statechange'));
  check(session.playbackState === 'playing' && window.__toWorklet.length === beforeStateEvents,
    'a browser state event only publishes presentation and never issues transport commands');
  audio.state = 'suspended';
  audio.dispatchEvent(new window.Event('statechange'));
  const normalResume = audio.resume;
  let explicitResumes = 0;
  audio.resume = async () => { explicitResumes++;
  audio.state = 'running';
  audio.dispatchEvent(new window.Event('statechange'));
  };
  await handlers.play();
  check(explicitResumes === 1 && session.playbackState === 'playing', 'explicit system Play can resume a suspended context whose logical transport was playing');
  audio.resume = normalResume;
  audio.state = 'running';
  audio.dispatchEvent(new window.Event('statechange'));
  const sliderValue = Object.getOwnPropertyDescriptor(window.HTMLInputElement.prototype, 'value');
  // Model a coarse slider: platform seek precision must not be recovered from its presentation.
  Object.defineProperty($('seek'), 'value', { configurable: true,
    get() { return String(Math.round(Number(sliderValue.get.call(this)))); },
    set(value) { sliderValue.set.call(this, value); },
  });
  await handlers.seekto({seekTime:50});
  check(messages('seek').at(-1)?.seconds===50,'platform seek enters the page seek path');
  const seekCount=messages('seek').length;
  await handlers.seekforward({seekOffset:20});
  await handlers.seekbackward({seekOffset:10});
  check(messages('seek').length===seekCount,'seeks during a seek coalesce instead of flooding the decoder');
  send({type:'seeked',seconds:50});
  check(messages('seek').at(-1)?.seconds===60,'the last platform seek wins against a pending seek');
  send({type:'seeked',seconds:60});
  check(window.__mediaPositions.at(-1)?.position===60,'position publishes the acknowledged final seek');
  await handlers.seekto({seekTime:NaN});
  await handlers.seekforward({seekOffset:-1});
  check(messages('seek').length===seekCount+1,'invalid system seeks do not reach the decoder');
  delete $('seek').value;
  await handlers.seekto({ seekTime: 5 });
  await handlers.seekto({ seekTime: 9 });
  const oldSeekCount = messages('seek').length;
  $('subsongs').children[2].click();
  check($('seek').disabled && handlers.seekto === null, 'a tune transition suspends seeking until its acknowledgement');
  send({ type: 'seeked', seconds: 5 });
  check(messages('seek').length === oldSeekCount, 'an old seek acknowledgement cannot apply its queued target to a new tune');
  send({ type: 'subsong', index: 2, requestedIndex: 2, selected: true, playing: true,
    previousSeconds: 5, duration: 120, describe: 'title\tSystem fixture\ncomposer\tComposer\nyear\t1995' });
  check(!$('seek').disabled && window.__mediaPositions.at(-1).position === 0, 'the acknowledged tune restores seeking from its own position');
  const beforeLateEnd = opens().length, beforeRewind = messages('rewind').length;
  await handlers.pause();
  send({ type: 'ended', seconds: 120 });
  check(opens().length === beforeLateEnd && $('playpause').title === 'Play', 'a late decoder end cannot advance the source after Pause');
  await handlers.play();
  check(messages('rewind').length === beforeRewind + 1, 'Play rewinds a decoder that finished just before Pause took effect');
  send({ type: 'position', seconds: 0 });
  await handlers.pause();
  send({ type: 'position', seconds: 120 });
  check(opens().length === beforeLateEnd && $('playpause').title === 'Play', 'a late clock-limit message cannot advance after Pause');
  await handlers.play();
  send({ type: 'position', seconds: 0 });
  await handlers.pause();
  const current=api.contextNow(), resume=current.resume, beforeResume=messages('play').length;
  let release, resumes=0;
  current.state='suspended';
  current.resume = () => {
    resumes++;
    return new Promise(done => {
      release = () => { current.state = 'running'; done(); };
    });
  };
  const pending=handlers.play();
  await handlers.play();
  await handlers.pause();
  release();
  await pending;
  check(resumes===1&&messages('play').length===beforeResume&&$('playpause').title==='Play','Pause beats a delayed resume and repeated Play does not duplicate it');
  current.resume=resume;
  await handlers.play();
  send({type:'position',seconds:17});
  await api.enqueueTrack(tracks[1]);
  await api.enqueueTrack(tracks[1]);
  const retained={source:api.planNow().context,pending:api.scheduleNow().pending.map(r=>r.id).join('|'),opens:opens().length};
  await handlers.stop();
  check(session.metadata===null&&session.playbackState==='none'&&$('playpause').title==='Play','Stop pauses and dismisses system presentation');
  check(api.planNow().context===retained.source&&api.scheduleNow().pending.map(r=>r.id).join('|')===retained.pending,'Stop retains actual source and duplicate manual requests');
  await handlers.play();
  check(opens().length===retained.opens&&session.metadata?.title==='System fixture','Play after Stop restores presentation without refetching');
  await api.storeNow.save({id:'system-editor',name:'Other editor',tracks:[tracks[2]],index:0});
  await api.choosePlaylist('system-editor');
  check(session.metadata?.title==='System fixture'&&api.actualNow().url===tracks[0].url,'another visible playlist does not replace system source metadata');
  await handlers.nexttrack();
  check(await until(()=>api.actualNow().url===tracks[1].url&&opens().length>retained.opens),'system Next follows the pending manual FIFO');
  check(session.metadata===null&&Object.keys(window.__mediaPositions.at(-1)).length===0,'replacement clears old metadata and position before decode');
  const beforeCancel=opens().at(-1).requestId;
  await handlers.pause();
  send({type:'opened',requestId:beforeCancel,describe:'title\tCancelled',duration:1,rate:44100});
  check($('playpause').title==='Play'&&session.metadata===null,'Pause cancels pending decoder ownership and rejects its late opened message');
  // The pointer-hold suppression state is deliberately left waiting for its synthetic click.
  api.clearPlaybackQueue();
  await api.choosePlaylist('system-source');
  $('queue').querySelector('li.track').click();
  await until(()=>api.actualNow().url===tracks[0].url && opens().at(-1).requestId!==beforeCancel);
  send({type:'opened',describe:'title\tA',duration:120,rate:44100,canSeek:true,subsongs:3,current:0});
  $('settings-allsubsongs').click();
  if(!$('settings-allsubsongs').checked)$('settings-allsubsongs').click();
  const acknowledge = (command, extra = {}) => send({ type: 'subsong', requestedIndex: command.index,
    index: command.index, selectionId: command.selectionId, selected: true, playing: true,
    duration: 120, previousSeconds: 0, describe: 'title\tA', ...extra });
  await handlers.nexttrack();
  await handlers.nexttrack();
  await handlers.previoustrack();
  const rapid = messages('subsong').slice(-3);
  check(rapid.map(command => command.index).join() === '1,2,1', 'rapid Next/Previous walks the requested tune rather than the last acknowledgement');
  acknowledge(rapid[0], { describe: 'title\tStale first tune' });
  acknowledge(rapid[1], { describe: 'title\tStale second tune' });
  check($('seek').disabled && session.metadata.title === 'A', 'old acknowledgements cannot publish a tune even when its index matches the latest request');
  acknowledge(rapid[2]);
  check(!$('seek').disabled && session.metadata.title === 'A', 'only the latest tune acknowledgement restores the shared controls');
  await handlers.previoustrack();
  acknowledge(messages('subsong').at(-1));
  await handlers.pause();
  api.showPanel('nowplaying');
  $('subsongs').children[2].click();
  const refused = messages('subsong').at(-1);
  acknowledge(refused, { index: 0, selected: false, playing: false });
  check($('playpause').title === 'Play' && !$('seek').disabled && $('error').textContent.length > 0,
    'a refused tune selection preserves Pause, restores seeking and explains the refusal');
  await handlers.play();
  const beforeSubsong=messages('subsong').length;
  await handlers.nexttrack();
  check(messages('subsong').length===beforeSubsong+1&&messages('subsong').at(-1).index===1,'system short Next walks subsongs when the shared setting is enabled');
  send({type:'subsong',describe:'title\tA second tune\ncomposer\tOther composer\nyear\t1996',duration:24,index:1,previousSeconds:0});
  check(session.metadata?.title==='A second tune'&&session.metadata.artist===$('sub').textContent&&window.__mediaPositions.at(-1).duration===24,'a subsong refreshes the shared system title summary and duration');
  await handlers.previoustrack();
  check(messages('subsong').at(-1).index===0,'system Previous uses the same short subsong semantics');
  await handlers.pause();
  send({type:'subsong',describe:'title\tA',duration:120,index:0,previousSeconds:0});
  check($('playpause').title==='Play'&&session.playbackState==='paused','a late subsong acknowledgement cannot undo a later Pause');
  await handlers.play();
  $('settings-allsubsongs').click();
  $('next').dispatchEvent(new window.Event('pointerdown',{bubbles:true}));
  await new Promise(done=>setTimeout(done,540));
  $('next').dispatchEvent(new window.Event('pointerup',{bubbles:true}));
  check(await until(()=>api.actualNow().url===tracks[1].url),'an actual held Next advances one file');
  const holdOpens=opens().length;
  send({type:'opened',describe:'title\tB',duration:120,rate:44100,canSeek:true,subsongs:1,current:0});
  await handlers.nexttrack();
  check(await until(()=>api.actualNow().url===tracks[2].url&&opens().length===holdOpens+1),'the next system media action bypasses the pointer hold click-suppression flag');
  // A generic first gesture may unlock a context, but cannot reverse an explicit Pause.
  current.state='suspended';
  let unlock;
  current.resume = () => new Promise(done => {
    unlock = () => { current.state = 'running'; done(); };
  });
  send({type:'opened',describe:'title\tC',duration:120,rate:44100,canSeek:true,subsongs:1,current:0});
  window.document.body.click();
  const beforeUnlock=messages('play').length;
  await handlers.pause();
  unlock();
  await new Promise(done=>setTimeout(done,10));
  check(messages('play').length===beforeUnlock&&$('playpause').title==='Play','Pause beats a delayed first-touch unlock without resuming sound');
  current.resume=resume;
  check(window.__browserErrors.length===0,'the actual page path has no unhandled browser errors');
}
