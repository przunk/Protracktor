// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later
// Upgrade/refusal through the actual production database opener.
import assert from 'node:assert/strict';
import '../web/node_modules/fake-indexeddb/auto/index.mjs';
import {IDBFactory} from '../web/node_modules/fake-indexeddb/build/esm/index.js';
const result=(request)=>new Promise((resolve,reject)=>{request.onsuccess=()=>resolve(request.result);request.onerror=()=>reject(request.error);});
let generation=0;
const oldPlace={key:{catalogueId:'modland',format:'Protracker',author:'A'},catalogueName:'Modland',savedAt:1};
async function seed(version){
 const request=indexedDB.open('protracktor',version);request.onupgradeneeded=()=>{
  const db=request.result;
  for(const [name,key] of [['playlists','id'],['settings','key'],['catalogue','key'],['played','url'],['bookmarks','key']])db.createObjectStore(name,{keyPath:key});
  request.transaction.objectStore('played').createIndex('playedAt','playedAt');
  request.transaction.objectStore('playlists').put({id:'keep',name:'Mine',tracks:[{url:'original'}],index:0});
  request.transaction.objectStore('settings').put({key:'keep',value:'kept'});
  request.transaction.objectStore('catalogue').put({key:'modland:meta',tracks:7});
  request.transaction.objectStore('played').put({url:'original',name:'Original',playedAt:100,playCount:3,listened:false});
  request.transaction.objectStore('bookmarks').put({key:'["modland","Protracker","A"]',place:oldPlace});
 };return result(request);
}
const store=()=>import(`../web/src/store.js?cache-migration=${++generation}`);
for(const [name,run] of [
 ['v5 upgrade preserves raw documents and creates empty independent stores',async()=>{
  globalThis.indexedDB=new IDBFactory();(await seed(5)).close();const api=await store();
  assert.equal((await api.playlists.get('keep')).name,'Mine');assert.equal(await api.settings.get('keep'),'kept');
  assert.equal((await api.catalogue.get('modland:meta')).tracks,7);assert.equal((await api.played.recent())[0].listened,false);assert.deepEqual(await api.bookmarks.list(),[oldPlace]);assert.deepEqual(await api.musicCache.snapshot(),[]);
  const db=await result(indexedDB.open('protracktor'));assert.equal(db.version,6);assert.equal(db.objectStoreNames.contains('asmaArchives'),true);
  const transaction=db.transaction('asmaArchives','readonly');assert.equal(await result(transaction.objectStore('asmaArchives').count()),0);db.close();api.forget();
 }],
 ['a newer schema refuses downgrade and retains existing user data',async()=>{
  globalThis.indexedDB=new IDBFactory();(await seed(7)).close();const api=await store();await assert.rejects(api.playlists.get('keep'),{name:'VersionError'});
  const db=await result(indexedDB.open('protracktor'));assert.equal(db.version,7);assert.equal((await result(db.transaction('playlists','readonly').objectStore('playlists').get('keep'))).name,'Mine');db.close();api.forget();
 }],
 ['blocked upgrade explains failure and a later retry retains old records',async()=>{
  globalThis.indexedDB=new IDBFactory();const old=await seed(5);old.onversionchange=()=>{};const api=await store();
  await assert.rejects(api.musicCache.snapshot(),/another tab/);old.close();
  assert.equal((await api.playlists.get('keep')).name,'Mine');assert.deepEqual(await api.bookmarks.list(),[oldPlace]);assert.deepEqual(await api.musicCache.snapshot(),[]);api.forget();
 }],
]){await run();console.log('✓ migration '+name);}
console.log('3 production browser migration/refusal cases passed (host IndexedDB)');
