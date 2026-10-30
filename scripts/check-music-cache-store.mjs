// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later
import assert from 'node:assert/strict';
import '../web/node_modules/fake-indexeddb/auto/index.mjs';
const {musicCache:cache,playlists,settings,played,catalogue,bookmarks,forget}=await import('../web/src/store.js');
let checks=0;const test=async(name,run)=>{try{await run();}catch(error){console.error('✗ '+name);throw error;}checks++;console.log('✓ IDB '+name);};
const content=async(url)=>[...new Uint8Array(await (await cache.read(url)).arrayBuffer())];
const byte=(value)=>new Blob([new Uint8Array([value])]);
await test('real Blob publication read and accounting',async()=>{
 await cache.write('https://test/A.mod',new Blob([new Uint8Array([1,2,3])]));assert.deepEqual(await content('https://test/A.mod'),[1,2,3]);
 assert.equal((await cache.snapshot())[0].sizeBytes,3);assert.match((await cache.snapshot())[0].name,/^[0-9a-f]{64}$/);
});
await test('write and eviction are one deterministic transaction with current protection',async()=>{
 await cache.clear();const now=Date.now;let stamp=10;Date.now=()=>stamp;
 try{
  await cache.write('A',byte(1));stamp=20;await cache.write('B',byte(2));stamp=30;
  await cache.write('C',byte(3),{ceiling:2,inUse:()=>['A']});
  assert.deepEqual((await cache.snapshot()).map((row)=>row.url).sort(),['A','C']);
 }finally{Date.now=now;}
});
await test('synchronous quota refusal preserves an older destination',async()=>{
 await cache.clear();await cache.write('old',byte(4));const put=IDBObjectStore.prototype.put;
 IDBObjectStore.prototype.put=function(...args){if(this.name==='remoteMusic')throw new DOMException('quota','QuotaExceededError');return put.apply(this,args);};
 try{await assert.rejects(cache.write('old',byte(9)),{name:'QuotaExceededError'});}finally{IDBObjectStore.prototype.put=put;}
 assert.deepEqual(await content('old'),[4]);
});
await test('failure during eviction rolls back the newly queued payload too',async()=>{
 const del=IDBObjectStore.prototype.delete;IDBObjectStore.prototype.delete=function(...args){if(this.name==='remoteMusic')throw new DOMException('evict','QuotaExceededError');return del.apply(this,args);};
 try{await assert.rejects(cache.write('new',byte(8),{ceiling:1}),{name:'QuotaExceededError'});}finally{IDBObjectStore.prototype.delete=del;}
 assert.equal(await cache.read('new'),null);assert.deepEqual(await content('old'),[4]);
});
await test('a retired publication ticket never commits and an empty replacement preserves old bytes',async()=>{
 let current=true;const storing=cache.write('late',byte(7),{canPublish:()=>current});current=false;
 assert.equal(await storing,false);assert.equal(await cache.read('late'),null);assert.equal(await cache.write('old',new Blob([])),false);assert.deepEqual(await content('old'),[4]);
});
await test('Clear deletes only fetched copies and late touches cannot restore them',async()=>{
 const open=indexedDB.open('protracktor',6);const archiveDb=await new Promise((resolve,reject)=>{open.onsuccess=()=>resolve(open.result);open.onerror=()=>reject(open.error);});
 await new Promise((resolve,reject)=>{const transaction=archiveDb.transaction('asmaArchives','readwrite');transaction.objectStore('asmaArchives').put({id:'asma',body:new Blob(['kept'])});transaction.oncomplete=resolve;transaction.onabort=()=>reject(transaction.error);});

 await playlists.save({id:'keep',name:'Mine',tracks:[]});await settings.set('keep',true);await catalogue.putAll([{key:'modland:meta',tracks:1}]);
 await played.record({url:'old',name:'Old'});await bookmarks.save({key:{catalogueId:'modland',format:'MOD',author:'A'},catalogueName:'Modland',savedAt:1});
 assert.equal(await cache.clear(),1);await cache.touch('old');assert.equal(await cache.read('old'),null);assert.equal((await playlists.get('keep')).name,'Mine');assert.equal(await settings.get('keep'),true);
 assert.equal((await catalogue.get('modland:meta')).tracks,1);assert.equal(await played.count(),1);assert.equal((await bookmarks.list()).length,1);
 const get=archiveDb.transaction('asmaArchives','readonly').objectStore('asmaArchives').get('asma');const archive=await new Promise((resolve,reject)=>{get.onsuccess=()=>resolve(get.result);get.onerror=()=>reject(get.error);});assert.equal(await archive.body.text(),'kept');archiveDb.close();
});
await test('metadata-shaped and empty payloads cannot establish retained availability',async()=>{
 const request=indexedDB.open('protracktor',6);const db=await new Promise((resolve,reject)=>{request.onsuccess=()=>resolve(request.result);request.onerror=()=>reject(request.error);});
 await new Promise((resolve,reject)=>{const transaction=db.transaction('remoteMusic','readwrite');const store=transaction.objectStore('remoteMusic');
  store.put({url:'flag-only',name:'flag',body:{size:900},lastUsedMillis:1});store.put({url:'empty-body',name:'empty',body:new Blob([]),lastUsedMillis:1});
  transaction.oncomplete=resolve;transaction.onabort=()=>reject(transaction.error);});db.close();
 assert.equal(await cache.read('flag-only'),null);assert.equal(await cache.read('empty-body'),null);
 assert.equal((await cache.snapshot()).length,0);await cache.enforce();assert.equal((await cache.snapshot()).length,0);
});
await test('eviction samples the latest protected set after publication awaits',async()=>{
 const now=Date.now;let stamp=10;Date.now=()=>stamp;
 try{
  await cache.write('A',byte(1));stamp=20;await cache.write('B',byte(2));stamp=30;
  const inUse=[];const storing=cache.write('C',byte(3),{ceiling:2,inUse:()=>inUse});inUse.push('A');await storing;
  assert.deepEqual((await cache.snapshot()).map((row)=>row.url).sort(),['A','C']);
 }finally{Date.now=now;}
});
assert.equal(checks,8);forget();console.log('8 actual fetched-music store cases passed (host IndexedDB)');
