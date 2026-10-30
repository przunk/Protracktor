// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later
import assert from 'node:assert/strict';
import {zipFixture} from './fixtures/zip.mjs';
import '../web/node_modules/fake-indexeddb/auto/index.mjs';
import { catalogue, playlists, played, settings } from '../web/src/store.js';
import * as archive from '../web/src/catalogue.js';
import { randomAvailability, scopedRandomTable, downloadFavourites, favouritesMeta } from '../web/src/random-catalogue.js';
import { PLATFORM_FINGERPRINT } from '../web/src/random-scope.js';
let checks = 0;
const test = async (name, run) => { try { await run(); } catch (error) { console.error(`✗ ${name}`); throw error; } checks++; console.log(`✓ ${name}`); };
const fixture = (lines, source = 'modland', playable = () => true, summary = true) => {
  const built = archive.toRecords(lines, source, playable);
  return [...built.records, { key: `${source}:meta`, tracks: built.tracks, total: built.total, complete: true,
    fingerprint: lines, ...(summary ? { platformCounts: built.platformCounts, platformFingerprint: PLATFORM_FINGERPRINT } : {}) }];
};
const oldFetch=globalThis.fetch;
const zip=Buffer.from(zipFixture([{name:'asma/Games/New/new.sap',body:'SAP'}]));
const zipResponse=async()=>({ok:true,status:200,headers:{get:()=>String(zip.length)},arrayBuffer:async()=>zip.buffer.slice(zip.byteOffset,zip.byteOffset+zip.byteLength)});

await test('a failed real ASMA index update retains the previous index and user data', async () => {
  await catalogue.putAll(fixture('1\tGames/Old/kept.sap','asma'));
  await playlists.save({id:'kept',name:'Kept',tracks:[{url:'https://example.test/kept.mod',name:'Kept'}]});
  await played.record({url:'https://example.test/kept.mod',name:'Kept'});await settings.set('randomScope','favourites');
  const put=IDBObjectStore.prototype.put;let writes=0;
  globalThis.fetch=zipResponse;
  IDBObjectStore.prototype.put=function(record,...rest){
    if(this.name==='catalogue'&&String(record?.key).startsWith('asma:')&&++writes===2) throw new DOMException('Quota test','QuotaExceededError');
    return put.call(this,record,...rest);
  };
  try { await assert.rejects(archive.downloadAsma({isPlayable:(name)=>name.endsWith('.sap')}), /Quota test/); }
  finally { IDBObjectStore.prototype.put=put;globalThis.fetch=oldFetch; }
  assert.equal((await catalogue.get('asma:meta'))?.fingerprint,'1\tGames/Old/kept.sap');
  assert.equal((await archive.tracksIn('Games','Old','asma'))[0]?.name,'kept.sap');
  assert.equal((await playlists.get('kept')).name,'Kept');assert.equal(await played.count(),1);assert.equal(await settings.get('randomScope'),'favourites');
});
await test('a successful real ASMA replacement publishes one complete dataset with native platform counts', async () => {
  const changes=[];const off=catalogue.subscribe((change)=>changes.push(change));globalThis.fetch=zipResponse;
  try { await archive.downloadAsma({isPlayable:(name)=>name.endsWith('.sap')}); }
  finally { off();globalThis.fetch=oldFetch; }
  const meta=await archive.meta('asma');assert.deepEqual(meta.platformCounts,{'atari-8bit':1});assert.equal(meta.platformFingerprint,PLATFORM_FINGERPRINT);
  assert.equal((await archive.tracksIn('Games','Old','asma')).length,0);assert.equal((await archive.tracksIn('Games','New','asma'))[0].name,'new.sap');
  assert.equal(changes.length,1);assert.deepEqual(changes[0].prefixes,['asma:']);
});
await test('a delayed playability refresh cannot replace a newer complete index', async () => {
  await catalogue.replacePrefix('modland:',fixture('1\tProtracker/Old/old.mod','modland'));
  const read=catalogue.byPrefix;let entered,release;
  const waiting=new Promise((resolve)=>{entered=resolve;}),hold=new Promise((resolve)=>{release=resolve;});
  catalogue.byPrefix=async(prefix)=>{const rows=await read(prefix);if(prefix==='modland:tracks:'){entered();await hold;}return rows;};
  try {
    const pending=archive.refreshPlayable('modland',()=>true);await waiting;
    await catalogue.replacePrefix('modland:',fixture('1\tFasttracker 2/New/new.xm','modland'));
    release();await pending;
    assert.equal((await archive.tracksIn('Fasttracker 2','New','modland'))[0]?.name,'new.xm');
    assert.equal((await archive.tracksIn('Protracker','Old','modland')).length,0);
  } finally {release?.();catalogue.byPrefix=read;}
});
await test('atomic replacement rejects records outside its dataset without deleting prior records', async () => {
  await catalogue.putAll([{key:'test:old',value:1},{key:'other:kept',value:2}]);
  await assert.rejects(catalogue.replacePrefix('test:',[{key:'other:bad',value:3}]),/outside its prefix/);
  assert.equal((await catalogue.get('test:old')).value,1);assert.equal((await catalogue.get('other:kept')).value,2);
});
await test('cancelled or uncloneable replacements roll back all queued writes', async () => {
  const controller=new AbortController();controller.abort();
  await assert.rejects(catalogue.replacePrefix('test:',[{key:'test:new'}],{signal:controller.signal}),{name:'AbortError'});
  await assert.rejects(catalogue.replacePrefix('test:',[{key:'test:new'},{key:'test:bad',uncopyable:()=>{}}]));
  assert.equal((await catalogue.get('test:old')).value,1);assert.equal(await catalogue.get('test:new'),undefined);
});
await test('bounded catalogue visits cover every matching bucket once and can cancel between batches', async () => {
  await catalogue.putAll(Array.from({length:7},(_,i)=>({key:`scan:${i}`,i})));const found=[];
  await catalogue.visitPrefix('scan:',(record)=>found.push(record.i),{batch:2});assert.deepEqual(found,[0,1,2,3,4,5,6]);
  const controller=new AbortController();let visited=0;
  await assert.rejects(catalogue.visitPrefix('scan:',()=>{visited++;controller.abort();},{batch:2,signal:controller.signal}),{name:'AbortError'});assert.equal(visited,2);
  await assert.rejects(catalogue.visitPrefix('scan:',()=>{},{batch:0}),RangeError);
});
const lines=['1\tProtracker/A/a.mod','1\tProtracker/A/not-playable.mod','1\tFasttracker 2/PC/a.xm',
  '1\tSpectrum/Pro Tracker 3/Author/ambiguous.psm','1\tOdd/Author/a.sid','1\tOdd/Author/a.mod', '1\tOdd/Author/readme'].join('\n');
await test('stored and legacy summaries count actual playable platforms with native precedence', async () => {
  await catalogue.replacePrefix('modland:',fixture(lines,'modland',(name)=>name!=='not-playable.mod'));
  const expected={amiga:2,pc:1,spectrum:1,c64:1,'atari-8bit':1};
  assert.deepEqual((await randomAvailability()).platformCounts,expected);
  const meta=await archive.meta('modland');delete meta.platformCounts;delete meta.platformFingerprint;await catalogue.putAll([meta]);
  assert.deepEqual((await randomAvailability()).platformCounts,expected);
});
await test('platform pools preserve directory precedence, filename fallback and whole ASMA coverage', async () => {
  const amiga=await scopedRandomTable('platform:amiga');assert.equal(amiga.total,2);
  assert.deepEqual(new Set((await archive.sampleRandom(amiga,10,()=>0)).map((track)=>track.url)),new Set([archive.urlFor('Protracker','A','a.mod'),archive.urlFor('Odd','Author','a.mod')]));
  const spectrum=await scopedRandomTable('platform:spectrum');assert.equal(spectrum.total,1);assert.equal((await archive.drawTrack(spectrum,()=>0)).name,'ambiguous.psm');
  const atari=await scopedRandomTable('platform:atari-8bit');assert.equal(atari.total,1);assert.equal((await archive.drawTrack(atari,()=>0)).name,'new.sap');
  assert.equal((await scopedRandomTable('platform:unknown')).total,0);
});
const row=(path)=>['hash','1','200','player','uade','mod','0','100','crc','samplecrc',path].join('\t');
const list=[row('Protracker/A/a.mod'),row('Protracker/A/a.mod'),row('Spectrum/Pro Tracker 3/Author/ambiguous.psm'),row('Protracker/A/not-playable.mod'),row('Protracker/Gone/missing.mod'),'hash\t2\t100\tloop'].join('\n');
await test('curated Favourites joins exact Modland paths and excludes missing or unplayable files', async () => {
  globalThis.fetch=async()=>({ok:true,arrayBuffer:async()=>new TextEncoder().encode(list).buffer});
  try { assert.equal(await downloadFavourites(),4); }finally{globalThis.fetch=oldFetch;}
  assert.equal((await favouritesMeta()).listed,4);
  const available=await randomAvailability();assert.equal(available.favouritesListed,4);assert.equal(available.favouriteCount,2);
  const pool=await scopedRandomTable('favourites');assert.equal(pool.total,2);
  assert.deepEqual(new Set((await archive.sampleRandom(pool,10,()=>0)).map((track)=>track.name)),new Set(['a.mod','ambiguous.psm']));
});
await test('failed favourites refresh preserves its retained list and current playable join', async () => {
  globalThis.fetch=async()=>({ok:true,arrayBuffer:async()=>new TextEncoder().encode('<html>not a list</html>').buffer});
  try { await assert.rejects(downloadFavourites(),/no favourite paths/); }finally{globalThis.fetch=oldFetch;}
  assert.equal((await favouritesMeta()).listed,4);assert.equal((await randomAvailability()).favouriteCount,2);
});
await test('cancelled table construction stops after its suspended read before querying another archive', async () => {
  const get=catalogue.get,controller=new AbortController();let entered,release,asmaReads=0;
  const started=new Promise((resolve)=>{entered=resolve;}),hold=new Promise((resolve)=>{release=resolve;});
  catalogue.get=async(key)=>{const result=await get(key);if(key==='modland:formats'){entered();await hold;}if(key==='asma:formats')asmaReads++;return result;};
  try {const pending=archive.buildRandomTable({signal:controller.signal});await started;controller.abort();release();await assert.rejects(pending,{name:'AbortError'});assert.equal(asmaReads,0);}
  finally {release?.();catalogue.get=get;}
});
await test('removing an index retains the curated list and distinguishes listed from currently playable', async () => {
  await catalogue.clear('modland:');const state=await randomAvailability();assert.equal(state.favouritesListed,4);assert.equal(state.favouriteCount,0);
  assert.deepEqual(state.platformCounts,{'atari-8bit':1});
});
console.log(`${checks} Random catalogue/storage cases passed.`);
