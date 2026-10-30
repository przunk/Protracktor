// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later
import assert from 'node:assert/strict';
import { PlaybackPreferences, storedPlaybackPreferences } from '../web/src/playback-preferences.js';
let count = 0;
const test = (name, run) => { try { run(); } catch (error) { console.error(`✗ ${name}`); throw error; } count++; console.log(`✓ ${name}`); };
// PlaylistSessionTest's default, saved true/ONE/true and compatible OFF restoration cases.
for (const [raw, expected, name] of [
  [{}, [false, 'off', false], 'absent state uses native defaults'],
  [{shuffle:'1',repeat:'one',playAllSubsongs:'1'}, [true,'one',true], 'saved native true One and play-all are restored'],
  [{shuffle:'0',repeat:'all',playAllSubsongs:'0'}, [false,'all',false], 'All remains distinct from One and Off'],
  [{shuffle:'true',repeat:'unknown',playAllSubsongs:'yes'}, [false,'off',false], 'unknown stored values are not treated as enabled'],
  [{shuffle:null,repeat:null,playAllSubsongs:null}, [false,'off',false], 'null storage uses compatible defaults'],
]) test(name, () => assert.deepEqual(Object.values(storedPlaybackPreferences(raw)), expected));
test('initial read never writes defaults and preserves the legacy subsong key', () => {
  const values=new Map([['protracktor.allsubsongs','1']]); let writes=0;
  const store=new PlaybackPreferences(() => ({getItem:(key)=>values.get(key),setItem:()=>writes++}));
  assert.deepEqual(store.read(),{value:{shuffle:false,repeat:'off',playAllSubsongs:true},failure:null}); assert.equal(writes,0);
});
test('a refused key does not discard another readable mode and retains its failure', () => {
  const failure=new Error('read denied');
  const store=new PlaybackPreferences(() => ({getItem:(key)=>{ if(key==='protracktor.shuffle') throw failure; return key==='protracktor.repeat'?'one':'1'; }}));
  assert.deepEqual(store.read(),{value:{shuffle:false,repeat:'one',playAllSubsongs:true},failure});
});
test('refusing storage access returns defaults and a failure without throwing out of startup', () => {
  const failure=new Error('access denied'), store=new PlaybackPreferences(()=>{throw failure;});
  assert.deepEqual(store.read(),{value:{shuffle:false,repeat:'off',playAllSubsongs:false},failure});
  assert.equal(store.write('shuffle',true),failure);
});
test('each valid action writes exactly its own key and round trips all three modes', () => {
  const values=new Map(),writes=[],store=new PlaybackPreferences(()=>({getItem:(k)=>values.get(k),setItem:(k,v)=>{writes.push([k,v]);values.set(k,v);}}));
  for(const [key,value] of [['shuffle',true],['repeat','one'],['playAllSubsongs',true],['repeat','all'],['repeat','off'],['shuffle',false]]) assert.equal(store.write(key,value),null);
  assert.deepEqual(writes,[['protracktor.shuffle','1'],['protracktor.repeat','one'],['protracktor.allsubsongs','1'],['protracktor.repeat','all'],['protracktor.repeat','off'],['protracktor.shuffle','0']]);
  assert.deepEqual(store.read().value,{shuffle:false,repeat:'off',playAllSubsongs:true});
});
test('write denial is returned while invalid caller values never touch storage', () => {
  const failure=new Error('quota'),writes=[],store=new PlaybackPreferences(()=>({setItem:(...args)=>{writes.push(args);throw failure;}}));
  assert.equal(store.write('repeat','all'),failure);
  for(const args of [['repeat','future'],['shuffle','1'],['playAllSubsongs',1],['unknown',true],['toString',true]]) assert.throws(()=>store.write(...args),TypeError);
  assert.equal(writes.length,1);
});
assert(count>0); console.log(`${count} playback preference cases passed`);
