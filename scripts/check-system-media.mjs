// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later
import assert from 'node:assert/strict';
import { SystemMedia } from '../web/src/system-media.js';

let checks = 0;
async function test(name, run) {
  await run();
  checks++;
  console.log(`✓ ${name}`);
}
function fixture({ unsupported = [], failMetadata = false, failPosition = false, failState = false } = {}) {
  const handlers = {}, calls = [], positions = [], errors = [], registrations = [];
  let metadata = null, playbackState = 'none';
  const state = {
    active: true, playing: true, canPlay: true, canPause: true, canNext: true,
    canPrevious: true, canSeek: true, seekLimit: 180, duration: 120, position: 4,
    metadata: { title: 'A', artist: 'Author · Platform · 1995', album: 'Archive/Author', artwork: [] },
  };
  const session = {
    setActionHandler(name, callback) {
      registrations.push(name);
      if (unsupported.includes(name)) throw Error(name);
      handlers[name] = callback;
    },
    setPositionState(value) {
      if (failPosition) throw Error('position');
      positions.push(value);
    },
    get metadata() { return metadata; },
    set metadata(value) {
      if (failMetadata) throw Error('metadata');
      metadata = value;
    },
    get playbackState() { return playbackState; },
    set playbackState(value) {
      if (failState) throw Error('state');
      playbackState = value;
    },
  };
  const commands = Object.fromEntries(['play', 'pause', 'stop', 'next', 'previous', 'seek']
    .map(name => [name, (...args) => calls.push([name, ...args])]));
  const bridge = new SystemMedia({
    navigator: { mediaSession: session },
    Metadata: class { constructor(data) { Object.assign(this, data); } },
    read: () => state, commands, onError: operation => errors.push(operation),
  });
  return { bridge, state, session, handlers, calls, positions, errors, registrations, commands };
}
await test('all eight supported actions register independently with the current capabilities', () => {
  const f = fixture();
  f.bridge.publish();
  assert.equal(Object.values(f.handlers).filter(v=>typeof v==='function').length,8);
});
await test('metadata reuses the complete shared summary and does not mutate input', () => {
  const f = fixture(), before=JSON.stringify(f.state);
  f.bridge.publish();
  assert.deepEqual({...f.session.metadata},f.state.metadata);
  assert.equal(JSON.stringify(f.state),before);
});
await test('Play Pause Stop Next and Previous target explicit commands', async () => {
  const f = fixture();
  f.bridge.publish();
  for(const action of ['play','pause','stop','nexttrack','previoustrack'])await f.handlers[action]();
  assert.deepEqual(f.calls,[['play'],['pause'],['stop'],['next'],['previous']]);
});
await test('an old handler checks current capability rather than a disabled DOM button', async () => {
  const f = fixture();
  f.bridge.publish();
  const next=f.handlers.nexttrack;
  f.state.canNext=false;
  await next();
  assert.equal(f.calls.length,0);
  f.bridge.publish();
  assert.equal(f.handlers.nexttrack,null);
  f.state.canNext=true;
  f.bridge.publish();
  await f.handlers.nexttrack();
  assert.deepEqual(f.calls,[['next']]);
});
await test('an unsupported action cannot prevent later actions or metadata', async () => {
  const f=fixture({unsupported:['seekbackward','pause']});
  f.bridge.publish();
  assert.equal(typeof f.handlers.stop,'function');
  assert.equal(typeof f.handlers.seekforward,'function');
  await f.handlers.nexttrack();
  assert.deepEqual(f.calls,[['next']]);
  assert.equal(f.session.metadata.title,'A');
  const attempts=f.registrations.length;
  f.bridge.publish();
  assert.equal(f.registrations.length,attempts);
});
await test('metadata failure leaves all actions and position working', async () => {
  const f=fixture({failMetadata:true});
  f.bridge.publish();
  await f.handlers.play();
  assert.deepEqual(f.calls,[['play']]);
  assert.equal(f.positions.at(-1).position,4);
  assert(f.errors.includes('metadata'));
});
await test('position rejection does not prevent metadata playback state or commands', async () => {
  const f=fixture({failPosition:true});
  f.bridge.publish();
  await f.handlers.pause();
  assert.equal(f.session.metadata.title,'A');
  assert.equal(f.session.playbackState,'playing');
  assert.deepEqual(f.calls,[['pause']]);
});
await test('state rejection does not prevent metadata position or commands', async () => {
  const f=fixture({failState:true});
  f.bridge.publish();
  await f.handlers.nexttrack();
  assert.equal(f.positions.at(-1).duration,120);
  assert.equal(f.session.metadata.title,'A');
  assert.deepEqual(f.calls,[['next']]);
});
await test('seek targets are finite and clamped to the same page limit', async () => {
  const f = fixture();
  f.bridge.publish();
  await f.handlers.seekto({seekTime:-5});
  await f.handlers.seekto({seekTime:999});
  assert.deepEqual(f.calls,[['seek',0],['seek',180]]);
});
await test('relative seeks use current position default10 and optional nonnegative offset', async () => {
  const f = fixture();
  f.bridge.publish();
  await f.handlers.seekforward({});
  f.state.position=150;
  await f.handlers.seekbackward({seekOffset:17});
  await f.handlers.seekforward({seekOffset:50});
  assert.deepEqual(f.calls,[['seek',14],['seek',133],['seek',180]]);
});
await test('invalid seek values and unknown actions never issue a command', async () => {
  const f = fixture();
  f.bridge.publish();
  for(const value of [NaN,Infinity,-Infinity,'2',null,undefined])await f.handlers.seekto({seekTime:value});
  for(const value of [NaN,Infinity,-1,'2',null])await f.handlers.seekforward({seekOffset:value});
  await f.bridge.invoke('unknown',{seekTime:5});
  assert.equal(f.calls.length,0);
});
await test('unseekable current transport retires every seek action', async () => {
  const f = fixture();
  f.bridge.publish();
  const old=f.handlers.seekto;
  f.state.canSeek=false;
  f.bridge.publish();
  assert.equal(f.handlers.seekto,null);
  assert.equal(f.handlers.seekbackward,null);
  await old({seekTime:10});
  assert.equal(f.calls.length,0);
});
await test('position publishes known actual duration and clamps both ends', () => {
  const f = fixture();
  f.state.position=999;
  f.bridge.publish();
  assert.deepEqual(f.positions.at(-1),{duration:120,playbackRate:1,position:120});
  f.state.position=-1;
  f.bridge.publish();
  assert.equal(f.positions.at(-1).position,0);
});
await test('pause uses a valid nonzero position rate and declared paused state', () => {
  const f = fixture();
  f.state.playing=false;
  f.bridge.publish();
  assert.equal(f.session.playbackState,'paused');
  assert.equal(f.positions.at(-1).playbackRate,1);
});
await test('unknown or invalid duration clears stale position without fabricating a fallback', () => {
  for (const duration of [0, -1, NaN, Infinity, undefined]) {
    const f = fixture();
    f.bridge.publish();
    f.state.duration = duration;
    f.bridge.publish();
    assert.deepEqual(f.positions.at(-1), {});
  }
});
await test('loading or Stop clears old metadata and position without losing transport commands', async () => {
  const f = fixture();
  f.bridge.publish();
  f.state.active=false;
  f.bridge.publish();
  assert.equal(f.session.metadata,null);
  assert.equal(f.session.playbackState,'none');
  assert.deepEqual(f.positions.at(-1),{});
  await f.handlers.play();
  assert.deepEqual(f.calls,[['play']]);
});
await test('identical facts do not rebuild metadata handlers or position repeatedly', () => {
  const f = fixture();
  f.bridge.publish();
  const metadata=f.session.metadata,registrations=f.registrations.length,positions=f.positions.length;
  f.bridge.publish();
  assert.equal(f.session.metadata,metadata);
  assert.equal(f.registrations.length,registrations);
  assert.equal(f.positions.length,positions);
});
await test('metadata replacement follows the new tune without replacing live command closures', async () => {
  const f = fixture();
  f.bridge.publish();
  const play=f.handlers.play;
  f.state.metadata={...f.state.metadata,title:'Second subsong'};
  f.bridge.publish();
  assert.equal(f.session.metadata.title,'Second subsong');
  assert.equal(f.handlers.play,play);
});
await test('absent API or refused API getter leaves page controls independent', () => {
  const read=()=>({}),commands={};
  assert.doesNotThrow(()=>new SystemMedia({navigator:{},read,commands}).publish());
  const navigator = { get mediaSession() { throw Error('refused'); } };
  assert.doesNotThrow(()=>new SystemMedia({navigator,read,commands}).publish());
});
await test('missing constructor and position method do not remove supported actions', async () => {
  const f = fixture();
  f.bridge.Metadata=undefined;
  delete f.session.setPositionState;
  f.bridge.publish();
  await f.handlers.stop();
  assert.deepEqual(f.calls,[['stop']]);
  assert.equal(f.session.metadata,null);
});
await test('a refused position method getter cannot escape into playback', () => {
  const f = fixture();
  Object.defineProperty(f.session, 'setPositionState', { get() { throw Error('getter'); } });
  assert.doesNotThrow(()=>f.bridge.publish());
  assert.equal(f.session.metadata.title,'A');
});
await test('sync and async command errors are contained and diagnostics are bounded', async () => {
  const f = fixture();
  f.commands.play = () => { throw Error('play'); };
  f.commands.pause = async () => { throw Error('pause'); };
  f.bridge.publish();
  await f.handlers.play();
  await f.handlers.play();
  await f.handlers.pause();
  assert.deepEqual(f.errors,['command:play','command:pause']);
});
await test('diagnostic failure cannot prevent the other supported platform actions', async () => {
  const f=fixture({unsupported:['seekforward']});
  f.bridge.onError = () => { throw Error('diagnostic'); };
  assert.doesNotThrow(()=>f.bridge.publish());
  await f.handlers.stop();
  assert.deepEqual(f.calls,[['stop']]);
});
await test('pause and resume rebase the browser position timestamp even if the last decoder position is unchanged', () => {
  const f = fixture();
  let now = 0, last, reportedAt;
  f.session.setPositionState = value => { last = value; reportedAt = now; };
  f.bridge.publish();
  now = 5;
  f.state.playing = false;
  f.bridge.publish();
  now = 65;
  f.state.playing = true;
  f.bridge.publish();
  const estimatedPosition = last.position + (f.session.playbackState === 'playing' ? now - reportedAt : 0);
  assert.equal(estimatedPosition, 4, 'paused wall-clock time must not become playback progress');
});
await test('a restored state setter rebases position after a temporary platform refusal', () => {
  const f = fixture();
  let now = 0, reportedAt, last, refused = true, declared = 'none';
  Object.defineProperty(f.session, 'playbackState', { configurable: true,
    get() { return declared; },
    set(value) {
      if (refused) throw Error('refused');
      declared = value;
    },
  });
  f.session.setPositionState = value => { last = value; reportedAt = now; };
  f.bridge.publish();
  now = 65;
  refused = false;
  f.bridge.publish();
  assert.equal(last.position + now - reportedAt, 4, 'recovered state must not reuse a stale timestamp');
});
await test('a restart revision rebases the same position without replacing metadata or actions', () => {
  const f = fixture();
  f.state.positionRevision = 1;
  f.bridge.publish();
  const before = f.positions.length, metadata = f.session.metadata, actions = f.registrations.length;
  f.state.positionRevision++;
  f.bridge.publish();
  assert.equal(f.positions.length, before + 1);
  assert.equal(f.session.metadata, metadata);
  assert.equal(f.registrations.length, actions);
});
await test('a rejected new metadata object clears the preceding title and a later valid title recovers', () => {
  const f = fixture();
  f.bridge.publish();
  const Metadata = f.bridge.Metadata, original = f.state.metadata;
  f.bridge.Metadata = class { constructor() { throw Error('metadata rejected'); } };
  f.state.metadata = { ...original, title: 'Second tune' };
  f.bridge.publish();
  assert.equal(f.session.metadata, null, 'a rejected update must not keep the previous tune on screen');
  f.bridge.Metadata = Metadata;
  f.state.metadata = original;
  f.bridge.publish();
  assert.equal(f.session.metadata.title, original.title, 'clearing must invalidate the old metadata cache too');
});
assert.equal(checks,27);
console.log(`${checks} system media cases passed`);
