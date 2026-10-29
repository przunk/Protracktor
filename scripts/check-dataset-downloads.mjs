// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later
import assert from 'node:assert/strict';
import { inflateRawSync } from 'node:zlib';
import '../web/node_modules/fake-indexeddb/auto/index.mjs';
import { zipFixture } from './fixtures/zip.mjs';
import { catalogue, playlists, settings, forget } from '../web/src/store.js';
import * as archive from '../web/src/catalogue.js';
import { waitForDownload } from '../web/src/dataset-download.js';
import { downloadFavourites } from '../web/src/random-catalogue.js';
const originalFetch=globalThis.fetch,originalPut=IDBObjectStore.prototype.put,NativeDecompress=globalThis.DecompressionStream;
// Node20 lacks raw deflate; the browser entry still uses its real standard stream API.
globalThis.DecompressionStream=class {constructor(format){if(format!=='deflate-raw')return new NativeDecompress(format);const parts=[];return new TransformStream({transform(chunk){parts.push(Buffer.from(chunk));},flush(controller){controller.enqueue(inflateRawSync(Buffer.concat(parts)));}});}};
const cases=[
 {id:'lengths:',run:archive.downloadSongLengths,url:'https://www.hvsc.de/download/C64Music/DOCUMENTS/Songlengths.md5',body:'abcdef123456abcdef123456abcdef12=1:02 0:03\n',newKey:'lengths:ab',count:'tunes'},
 {id:'songdb:',run:archive.downloadSongMetadata,url:'https://raw.githubusercontent.com/mvtiaine/audacious-uade-tools/1bad3e8/tsv/pretty/md5/metadata.tsv',body:'abcdef123456\tNew\tGroup\tAlbum\t1999\n',newKey:'songdb:ab',count:'tunes'},
 {id:'modland:',run:archive.downloadModland,url:'https://modland.com/allmods.zip',body:zipFixture([{name:'allmods.txt',body:'100\tProtracker/Writer/one.mod\n200\tFuture/Unknown/two.fut\n'}],{method:8}),newKey:'modland:tracks:Protracker/Writer',count:'tracks'},
 {id:'modland-favourites:',run:downloadFavourites,url:'https://raw.githubusercontent.com/mvtiaine/audacious-uade-tools/master/songdb/sources/site/modland_favourites.tsv',body:'\t\t\t\t\t\t\t\t\t\tProtracker/Writer/one.mod\n',newKey:'modland-favourites:paths',count:'listed'},
];
const old=(c)=>[{key:c.id+'old',value:42},{key:c.id+'meta',[c.count]:7,at:1}];
const seed=async(c)=>catalogue.replacePrefix(c.id,old(c));
let passed=0;
const test=async(name,run)=>{try{await run();passed++;console.log('PASS: '+name);}catch(error){console.error('FAIL: '+name);throw error;}};
try{
 await playlists.save({id:'keep',name:'Kept',tracks:[{url:'https://example.test/kept.mod',name:'Kept'}],index:0});await settings.set('keep',{value:5});
 for(const c of cases){
  await test(c.id+' uses exact approved source and commits complete rows with honest progress',async()=>{
   await seed(c);const events=[],urls=[];globalThis.fetch=async(url)=>{urls.push(url);return new Response(c.body);};
   await c.run({onProgress:(e)=>events.push(e)});assert.deepEqual(urls,[c.url]);assert(await catalogue.get(c.newKey));assert.equal(await catalogue.get(c.id+'old'),undefined);assert.equal(events.at(-1).stage,'done');assert(events.some((e)=>e.stage==='fetching'));assert(events.some((e)=>e.stage==='storing'));
  });
  await test(c.id+' refused parsing leaves its previous dataset installed',async()=>{
   await seed(c);globalThis.fetch=async()=>new Response('This is not a valid catalogue or metadata payload.');
   await assert.rejects(c.run());for(const row of old(c))assert.deepEqual(await catalogue.get(row.key),row);
  });
  await test(c.id+' quota refusal preserves all old rows and metadata',async()=>{
   await seed(c);globalThis.fetch=async()=>new Response(c.body);IDBObjectStore.prototype.put=function(value,...args){if(this.name==='catalogue'&&value.key===c.newKey)throw new DOMException('full','QuotaExceededError');return originalPut.call(this,value,...args);};
   try{await assert.rejects(c.run(),{name:'QuotaExceededError'});}finally{IDBObjectStore.prototype.put=originalPut;}
   for(const row of old(c))assert.deepEqual(await catalogue.get(row.key),row);assert.equal(await catalogue.get(c.newKey),undefined);
  });
  await test(c.id+' Stop from storing progress rejects before atomic publication',async()=>{
   await seed(c);globalThis.fetch=async()=>new Response(c.body);const controller=new AbortController();
   await assert.rejects(c.run({signal:controller.signal,onProgress:(e)=>{if(e.stage==='storing')controller.abort();}}),{name:'AbortError'});
   for(const row of old(c))assert.deepEqual(await catalogue.get(row.key),row);
  });
  await test(c.id+' a late request cannot overwrite a newer dataset snapshot',async()=>{
   await seed(c);let release;globalThis.fetch=()=>new Promise((resolve)=>{release=()=>resolve(new Response(c.body));});
   const job=c.run();while(!release)await new Promise((done)=>setImmediate(done));await catalogue.replacePrefix(c.id,[{key:c.id+'meta',[c.count]:99,at:99}]);release();await assert.rejects(job,{name:'AbortError'});
   assert.equal((await catalogue.get(c.id+'meta'))[c.count],99);assert.equal(await catalogue.get(c.newKey),undefined);
  });
  await test(c.id+' Stop cancels a blocked response and ignores its eventual body',async()=>{
   await seed(c);let release,signal;globalThis.fetch=(url,options)=>{signal=options.signal;return new Promise((resolve)=>{release=()=>resolve(new Response(c.body));});};const controller=new AbortController();
   const job=c.run({signal:controller.signal});while(!release)await new Promise((done)=>setImmediate(done));controller.abort();await assert.rejects(job,{name:'AbortError'});assert.equal(signal.aborted,true);release();await new Promise((done)=>setImmediate(done));for(const row of old(c))assert.deepEqual(await catalogue.get(row.key),row);
  });
 }
 await test('a cancelled engine waiter leaves a shared foreground waiter independent',async()=>{
  let release; const shared=new Promise((resolve)=>{release=resolve;}),controller=new AbortController();
  const own=waitForDownload(shared,controller.signal),foreground=waitForDownload(shared);controller.abort();await assert.rejects(own,{name:'AbortError'});release(42);assert.equal(await foreground,42);
 });
 await test('completed, already-cancelled and refused setup waits settle once and retain failures',async()=>{
  const complete=new AbortController();assert.equal(await waitForDownload(Promise.resolve(7),complete.signal),7);complete.abort();
  const aborted=new AbortController();aborted.abort();await assert.rejects(waitForDownload(Promise.reject(new Error('obsolete')),aborted.signal),{name:'AbortError'});
  await assert.rejects(waitForDownload(Promise.reject(new Error('engine did not start'))),/engine did not start/);
 });
 assert.equal((await playlists.get('keep')).name,'Kept');assert.deepEqual(await settings.get('keep'),{value:5});
 console.log(passed+' production-path dataset cases pass; documents/settings retained.');
}finally{globalThis.fetch=originalFetch;IDBObjectStore.prototype.put=originalPut;globalThis.DecompressionStream=NativeDecompress;forget();}
