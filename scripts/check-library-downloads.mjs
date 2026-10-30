// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later
import assert from 'node:assert/strict';
import fs from 'node:fs';
import { LibraryDownloads, downloadSteps, downloadMegabytes, missingDownloads, DOWNLOAD_STEPS } from '../web/src/library-downloads.js';
const deferred = () => { let resolve, reject; const promise = new Promise((yes, no) => { resolve = yes; reject = no; }); return { promise, resolve, reject }; };
const tick = () => new Promise((done) => setImmediate(done));
const until = async (f) => { for (let n = 0; n < 50; n++) { if (f()) return; await tick(); } throw new Error('Boundary not reached'); };
const operations = (run = async () => {}) => Object.fromEntries(DOWNLOAD_STEPS.map((id) => [id, (options) => run(id, options)]));
let count = 0;
const test = async (name, run) => { try { await run(); } catch (error) { console.error('FAIL: '+name); throw error; } count++; console.log('PASS: '+name); };
await test('shared native plans, choice order, approved source filtering and explicit credits-only size', async () => {
  const lines = fs.readFileSync('docs/rules/download-plan-cases.tsv', 'utf8').trim().split('\n');
  const expected = Number(lines.find((row) => row.startsWith('# cases: ')).slice(9));
  const rows = lines.filter((row) => !row.startsWith('#')); assert.equal(rows.shift(), 'case\tselected\texpected'); assert.equal(rows.length, expected); assert(expected > 0);
  const ids = (value) => value === '-' ? [] : value.split(',');
  for (const line of rows) { const [name, selected, wanted] = line.split('\t'); assert.deepEqual(downloadSteps(ids(selected)), ids(wanted), name); }
  assert.deepEqual(downloadSteps(['unexotica']), []); // Approved WEB scope is narrower than native.
  assert.equal(downloadMegabytes(['modland', 'asma', 'songmetadata']), 47); assert.equal(downloadMegabytes(['songmetadata']),20); assert.equal(downloadMegabytes([]),0);
  assert.deepEqual(missingDownloads({modland:true,asma:false,songmetadata:false}),['asma','songmetadata']);
});
await test('partial failure preserves previous successes, continues independent steps and retries only unfinished ones', async () => {
  const calls = []; let fail = true;
  const runner = new LibraryDownloads(operations(async (id) => { calls.push(id); if (id === 'favourites' && fail) throw new Error('offline'); return id; }));
  const first = await runner.start(['songmetadata','modland']);
  assert.deepEqual(calls,['modland','favourites','songlengths','trackmetadata']);
  assert.equal(first.phase,'finished'); assert.deepEqual(first.rows.map((r)=>r.status),['done','failed','done','done']); assert.deepEqual(runner.retryable,['favourites']);
  fail=false; await runner.retry(); assert.deepEqual(calls.slice(4),['favourites']); assert.deepEqual(runner.retryable,[]); assert.equal(runner.retry(),null);
});
await test('Stop retains committed data, cancels the current step and prevents later starts until explicit Retry', async () => {
  const calls=[], gate=deferred(); let stopped, delay=true;
  const runner=new LibraryDownloads(operations(async(id,{signal})=> {calls.push(id);if(id==='songlengths' && delay){stopped=signal;await gate.promise;if(signal.aborted)throw new DOMException('Aborted','AbortError');}return id;}));
  const job=runner.start(['asma','songmetadata']);await until(()=>stopped);assert.equal(runner.stop(),true);assert.equal(stopped.aborted,true);gate.resolve();
  const result=await job;assert.equal(result.phase,'stopped');assert.deepEqual(calls,['asma','songlengths']);assert.deepEqual(runner.busy,[]);assert.deepEqual(runner.retryable,['songlengths','trackmetadata']);
  delay=false;await runner.retry();assert.deepEqual(calls.slice(2),['songlengths','trackmetadata']);
});
await test('a commit completed just before Stop still counts as retained', async()=>{
 const gate=deferred();let entered=false;
 const runner=new LibraryDownloads(operations(async(id)=>{entered=true;await gate.promise;return {committed:true};}));
 const job=runner.start(['asma','songmetadata']);await until(()=>entered);runner.stop();gate.resolve();const result=await job;
 assert.equal(result.rows[0].status,'done');assert.deepEqual(runner.retryable,['songlengths','trackmetadata']);
});
await test('an already busy individual step is not duplicated or counted as failure by a grouped run', async()=>{
 const gate=deferred(),calls=[];const runner=new LibraryDownloads(operations(async(id)=>{calls.push(id);if(id==='asma')await gate.promise;}));
 const individual=runner.executeStep('asma');const group=await runner.start(['asma','songmetadata']);
 assert.deepEqual(calls,['asma','songlengths','trackmetadata']);assert.equal(group.rows[0].status,'busy');assert.deepEqual(runner.retryable,[]);
 gate.resolve();assert.equal((await individual).status,'done');await tick();assert.deepEqual(runner.busy,[]);assert.equal(runner.state.rows[0].status,'done');assert.equal(runner.retry(),null);
});
await test('duplicate grouped/individual calls do not steal locks and late progress cannot describe a retry', async()=>{
 const gate=deferred();let progress;const updates=[];
 const runner=new LibraryDownloads(operations(async(id,o)=>{progress=o.onProgress;await gate.promise;}),{changed:(state)=>updates.push(state)});
 const first=runner.start(['asma']);await until(()=>progress);assert.equal(runner.start(['asma']),null);assert.equal((await runner.executeStep('asma')).status,'busy');
 runner.stop();progress({stage:'late',done:99});assert(!updates.some((s)=>s.rows.some((r)=>r.progress?.stage==='late')));gate.resolve();await first;assert.deepEqual(runner.busy,[]);
});
await test('empty/unknown choices never create a run or publish busy state',async()=>{
 const states=[];const runner=new LibraryDownloads(operations(),{changed:(s)=>states.push(s)});assert.equal(runner.start([]),null);assert.equal(runner.start(['unknown']),null);assert.deepEqual(states,[]);assert.equal(runner.stop(),false);
});
await test('state snapshots remain immutable and progress is copied away from the producer',async()=>{
 const gate=deferred();let progress;const runner=new LibraryDownloads(operations(async(id,o)=>{progress=o.onProgress;await gate.promise;}));
 const job=runner.start(['asma']);const original=runner.state;await until(()=>progress);const source={stage:'fetching',done:3};progress(source);source.done=100;assert.equal(runner.state.rows[0].progress.done,3);assert.equal(original.rows[0].status,'pending');assert(Object.isFrozen(runner.state.rows[0]));gate.resolve();await job;
});
await test('a synchronous observer can Stop before any transfer begins',async()=>{
 const calls=[];let runner;runner=new LibraryDownloads(operations(async(id)=>calls.push(id)),{changed:(s)=>{if(s.phase==='running')runner.stop();}});
 const result=await runner.start(['modland']);assert.equal(result.phase,'stopped');assert.deepEqual(calls,[]);assert.deepEqual(runner.retryable,['modland','favourites']);
});
await test('a completion observer can start a new run without the previous finalizer erasing ownership',async()=>{
 const gate=deferred();let runner,next,entered=false;
 runner=new LibraryDownloads(operations(async(id)=>{if(id==='songlengths'){entered=true;await gate.promise;}}),{changed:(s)=>{if(s.phase==='finished'&&s.rows.length===1)next=runner.start(['songmetadata']);}});
 const first=await runner.start(['asma']);assert.equal(first.rows[0].id,'asma');await until(()=>entered);assert.equal(runner.running,true);gate.resolve();await next;assert.equal(runner.state.rows[0].id,'songlengths');assert.equal(runner.running,false);
});
await test('missing and unknown implementations fail visibly rather than succeeding with no work',async()=>{
 assert.throws(()=>new LibraryDownloads({}),/Missing/);const runner=new LibraryDownloads(operations());await assert.rejects(runner.executeStep('players'),/Unknown/);
});
await test('an independently failed busy step becomes retryable instead of being silently lost',async()=>{
 const gate=deferred();let fail=true;const runner=new LibraryDownloads(operations(async(id)=>{if(id==='asma'){await gate.promise;if(fail)throw new Error('offline');}}));
 const individual=runner.executeStep('asma');await runner.start(['asma']);assert.equal(runner.state.rows[0].status,'busy');assert.deepEqual(runner.retryable,[]);
 gate.resolve();await individual;await tick();assert.equal(runner.state.rows[0].status,'failed');assert.deepEqual(runner.retryable,['asma']);fail=false;await runner.retry();assert.deepEqual(runner.retryable,[]);
});
await test('Stop never cancels another action that owns a skipped step',async()=>{
 const own=new AbortController(),gate=deferred();let groupSignal;
 const runner=new LibraryDownloads(operations(async(id,{signal})=>{if(id==='asma')await gate.promise;else{groupSignal=signal;await new Promise((resolve,reject)=>signal.addEventListener('abort',()=>reject(new DOMException('Aborted','AbortError')),{once:true}));}}));
 const individual=runner.executeStep('asma',{signal:own.signal});const job=runner.start(['asma','songmetadata']);await until(()=>groupSignal);runner.stop();await job;
 assert.equal(groupSignal.aborted,true);assert.equal(own.signal.aborted,false);gate.resolve();await individual;await tick();assert.equal(runner.state.phase,'stopped');assert.equal(runner.state.rows[0].status,'done');assert.deepEqual(runner.retryable,['songlengths','trackmetadata']);
});
await test('a late independent result cannot overwrite a newer grouped run',async()=>{
 const old=deferred(),current=deferred();let entered=false;
 const runner=new LibraryDownloads(operations(async(id)=>{if(id==='asma')await old.promise;if(id==='songlengths'){entered=true;await current.promise;}}));
 const individual=runner.executeStep('asma');await runner.start(['asma']);const newer=runner.start(['songmetadata']);await until(()=>entered);
 const before=runner.state;old.resolve();await individual;await tick();assert.equal(runner.state,before);assert.equal(runner.running,true);assert.deepEqual(runner.state.rows.map((row)=>row.id),['songlengths','trackmetadata']);
 current.resolve();await newer;assert(runner.state.rows.every((row)=>row.status==='done'));
});
console.log(count+' production grouped-download cases pass');
