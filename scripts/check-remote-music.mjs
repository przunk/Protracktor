// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later
// Native shared-transfer/cancellation rules through the production coordinator.
import assert from 'node:assert/strict';
import { RemoteMusic } from '../web/src/remote-music.js';
const deferred=()=>{let resolve,reject;const promise=new Promise((done,fail)=>{resolve=done;reject=fail;});return {promise,resolve,reject};};
const until=async(predicate)=>{for(let i=0;i<50;i++){if(predicate())return;await Promise.resolve();}throw new Error('Draft boundary not reached');};
const cache=()=>{
 const rows=new Map(),listeners=new Set();let writes=0;
 return {rows,get writes(){return writes;},subscribe(fn){listeners.add(fn);return()=>listeners.delete(fn);},enforce:async()=>{},
  read:async(url)=>rows.get(url)??null,touch:async()=>{},snapshot:async()=>[...rows].map(([url,blob])=>({url,sizeBytes:blob.size})),
  async write(url,body,{canPublish}){if(!canPublish())return false;rows.set(url,body);writes++;for(const fn of listeners)fn();return true;},
  async clear(){const bytes=[...rows.values()].reduce((n,blob)=>n+blob.size,0);rows.clear();for(const fn of listeners)fn();return bytes;}};
};
let checks=0;
const test=async(name,run)=>{try{await run();}catch(error){console.error('✗ '+name);throw error;}checks++;console.log('✓ '+name);};
await test('shared transfer survives one cancelled waiter and gives independent transferable results',async()=>{
 const disk=cache(),body=deferred();let requests=0;
 const music=new RemoteMusic({cache:disk,fetch:async()=>{requests++;return {ok:true,arrayBuffer:()=>body.promise};}});await music.ready;
 const signal=new AbortController(),a=music.bytes('https://example.test/coordinator.mod',{signal:signal.signal}),b=music.bytes('https://example.test/coordinator.mod'),c=music.bytes('https://example.test/coordinator.mod');
 const rejected=assert.rejects(a,{name:'AbortError'});await until(()=>requests===1);signal.abort();await rejected;
 body.resolve(new Uint8Array([42,7]).buffer);const [one,two]=await Promise.all([b,c]);
 assert.equal(requests,1);assert.notEqual(one,two);structuredClone(one,{transfer:[one]});assert.equal(one.byteLength,0);assert.equal(new Uint8Array(two)[0],42);
 await music.refresh();assert.equal(music.has('https://example.test/coordinator.mod'),true);assert.deepEqual([...new Uint8Array(await music.bytes('https://example.test/coordinator.mod'))],[42,7]);assert.equal(requests,1);
});
await test('Clear retires an old publication while a fresh request can refill the same URL',async()=>{
 const disk=cache(),old=deferred();let requests=0;
 const music=new RemoteMusic({cache:disk,fetch:async()=>({ok:true,arrayBuffer:()=>++requests===1?old.promise:Promise.resolve(new Uint8Array([2]).buffer)})});await music.ready;
 const first=music.bytes('https://example.test/coordinator.mod');await until(()=>requests===1);assert.equal(await music.clear(),0);
 assert.equal(new Uint8Array(await music.bytes('https://example.test/coordinator.mod'))[0],2);old.resolve(new Uint8Array([1]).buffer);assert.equal(new Uint8Array(await first)[0],1);
 assert.equal(new Uint8Array(await music.bytes('https://example.test/coordinator.mod'))[0],2);assert.equal(disk.writes,1);
});
await test('quota failure still returns fetched bytes without an offline claim',async()=>{
 const disk=cache(),failures=[];disk.write=async()=>{throw new DOMException('quota','QuotaExceededError');};
 const music=new RemoteMusic({cache:disk,failure:(e)=>failures.push(e.name),fetch:async()=>({ok:true,arrayBuffer:async()=>new Uint8Array([3]).buffer})});await music.ready;
 assert.equal(new Uint8Array(await music.bytes('https://example.test/coordinator.mod'))[0],3);assert.deepEqual(failures,['QuotaExceededError']);assert.equal(music.has('https://example.test/coordinator.mod'),false);
});
await test('a refused LRU touch does not withhold an existing readable offline copy',async()=>{
 const disk=cache(),failures=[];disk.rows.set('https://example.test/coordinator.mod',new Blob([new Uint8Array([4])]));disk.touch=async()=>{throw new DOMException('touch','QuotaExceededError');};
 const music=new RemoteMusic({cache:disk,failure:(e)=>failures.push(e.name),fetch:()=>{throw new Error('Network must not run');}});await music.ready;
 assert.equal(new Uint8Array(await music.bytes('https://example.test/coordinator.mod'))[0],4);assert.deepEqual(failures,['QuotaExceededError']);
});
await test('a database AbortError is a cache miss unless the requesting caller was cancelled',async()=>{
 const disk=cache(),failures=[];disk.read=async()=>{throw new DOMException('database','AbortError');};
 const music=new RemoteMusic({cache:disk,failure:(e)=>failures.push(e.name),fetch:async()=>({ok:true,arrayBuffer:async()=>new Uint8Array([5]).buffer})});await music.ready;
 assert.equal(new Uint8Array(await music.bytes('https://example.test/coordinator.mod'))[0],5);assert.deepEqual(failures,['AbortError']);
});
await test('failed Clear preserves data and does not poison future playback waits',async()=>{
 const disk=cache();disk.rows.set('https://example.test/coordinator.mod',new Blob([new Uint8Array([6])]));disk.clear=async()=>{throw new Error('clear refused');};
 const music=new RemoteMusic({cache:disk,fetch:()=>{throw new Error('Network must not run');}});await music.ready;
 await assert.rejects(music.clear(),/clear refused/);assert.equal(new Uint8Array(await music.bytes('https://example.test/coordinator.mod'))[0],6);
});
await test('a late old availability snapshot cannot undo a newer cache state',async()=>{
 const disk=cache(),old=deferred(),music=new RemoteMusic({cache:disk});await music.ready;
 const snapshot=disk.snapshot;disk.snapshot=()=>old.promise;const stale=music.refresh();disk.snapshot=snapshot;
 disk.rows.set('new',new Blob(['new']));await music.refresh();old.resolve([{url:'old',sizeBytes:3}]);await stale;
 assert.equal(music.has('new'),true);assert.equal(music.has('old'),false);assert.equal(music.state.bytes,3);
});
await test('empty HTTP payloads do not create a retained-copy flag',async()=>{
 const disk=cache(),music=new RemoteMusic({cache:disk,fetch:async()=>({ok:true,arrayBuffer:async()=>new ArrayBuffer(0)})});await music.ready;
 assert.equal((await music.bytes('https://example.test/coordinator.mod')).byteLength,0);assert.equal(disk.writes,0);assert.equal(music.has('https://example.test/coordinator.mod'),false);
});
await test('a publication refusal stays visible through readable metadata and clears after a real successful write',async()=>{
 const disk=cache(),write=disk.write;disk.write=async()=>{throw new DOMException('quota','QuotaExceededError');};
 const music=new RemoteMusic({cache:disk,fetch:async()=>({ok:true,arrayBuffer:async()=>new Uint8Array([7]).buffer})});await music.ready;
 await music.bytes('https://example.test/first.mod');assert.equal(music.state.failed?.name,'QuotaExceededError');await music.refresh();assert.equal(music.state.failed?.name,'QuotaExceededError');
 disk.write=write;await music.bytes('https://example.test/second.mod');await music.refresh();assert.equal(music.state.failed,null);assert.equal(music.has('https://example.test/second.mod'),true);
});
await test('explicit storage retry uses the same enforcement and snapshot path as startup',async()=>{
 const disk=cache();let attempts=0;disk.enforce=async()=>{if(++attempts===1)throw new Error('maintenance refused');};
 const music=new RemoteMusic({cache:disk});await music.ready;assert.equal(music.state.failed?.message,'maintenance refused');
 await music.retry();assert.equal(attempts,2);assert.equal(music.state.failed,null);assert.equal(music.state.loading,false);
});
await test('a retired initial maintenance failure cannot replace a newer successful retry',async()=>{
 const disk=cache(),held=deferred();let attempts=0;disk.enforce=()=>++attempts===1?held.promise:Promise.resolve();
 const music=new RemoteMusic({cache:disk});await music.retry();held.reject(new Error('old maintenance'));await music.ready;
 assert.equal(music.state.failed,null);assert.equal(music.state.loading,false);
});
await test('relative data and local identities never silently enter persistent fetched storage',async()=>{
 const disk=cache(),music=new RemoteMusic({cache:disk,fetch:async()=>({ok:true,arrayBuffer:async()=>new Uint8Array([8]).buffer})});await music.ready;
 for(const url of ['relative.mod','data:audio/x-mod;base64,AQID','file:///private.mod']) {
  assert.equal((await music.bytes(url)).byteLength,1);assert.equal(music.has(url),false);
 }
 assert.equal(disk.writes,0);
});
await test('failure to construct a retained Blob does not withhold the fetched decoder bytes',async()=>{
 const response=globalThis.Response,disk=cache(),failures=[];
 const music=new RemoteMusic({cache:disk,failure:(error)=>failures.push(error.message),fetch:async()=>({ok:true,arrayBuffer:async()=>new Uint8Array([6]).buffer})});await music.ready;
 globalThis.Response=class {blob(){return Promise.reject(new Error('blob refused'));}};
 try{assert.equal(new Uint8Array(await music.bytes('https://example.test/blob.mod'))[0],6);}finally{globalThis.Response=response;}
 assert.deepEqual(failures,['blob refused']);assert.equal(music.has('https://example.test/blob.mod'),false);
});
assert.equal(checks,13);console.log('13 remote-music coordinator cases passed (network/storage ports are host fixtures)');
