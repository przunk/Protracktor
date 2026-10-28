// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later
import assert from 'node:assert/strict';
import fs from 'node:fs';
import '../web/node_modules/fake-indexeddb/auto/index.mjs';
import { zipFixture } from './fixtures/zip.mjs';
import { zipDirectory, readZipMember, zipCrc32, validateZip } from '../web/src/zip-archive.js';
import { asmaArchive, catalogue, playlists, settings, played, musicCache, forget } from '../web/src/store.js';
import { downloadAsma, fileUrlFor, asmaFileUrl, toRecords } from '../web/src/catalogue.js';
import { OfflineAsma } from '../web/src/offline-asma.js';
let count = 0;
const test = async (name, run) => { try { await run(); } catch (error) { console.error(`✗ ${name}`); throw error; } count++; console.log(`✓ ${name}`); };
const text = (bytes) => new TextDecoder().decode(bytes);
const input = fs.readFileSync('docs/rules/asma-archive-cases.tsv', 'utf8');
const lines = input.split('\n').filter((line) => line && !line.startsWith('#'));
const expectedCount = Number(/^# cases: (\d+)$/m.exec(input)?.[1]);
assert(expectedCount > 0); assert.equal(lines.length - 1, expectedCount);
assert.equal(lines[0], 'path\tbody\tformat\tauthor\ttitle\tsize');
const cases = lines.slice(1).map((line) => { const [path, body, format, author, title, size] = line.split('\t'); return { path, body, format, author: author === '-' ? '' : author, title, size: Number(size) }; });
const files = cases.map((row) => ({ name: row.path, body: row.body }));
const bytes = zipFixture(files), body = new Blob([bytes]), entries = zipDirectory(bytes);
const record = { id: 'asma', schema: 1, body, entries, tracks: 5, total: 6, at: 1 };
const records = [{ key: 'asma:meta', tracks: 5, total: 6 }];
const install = async () => asmaArchive.replace(record, records, { ticket: await asmaArchive.ticket() });
await test('native ASMA paths, contents and index buckets match all shared cases', async () => {
  assert.equal(entries.length, expectedCount);
  const built = toRecords(entries.map((entry) => `${entry.size}\t${entry.name.slice(5)}`).join('\n'), 'asma', (name) => name.endsWith('.sap'));
  assert.equal(built.total, 6); assert.equal(built.tracks, 5);
  for (const row of cases) {
    const member = entries.find((entry) => entry.name === row.path); assert.equal(member.size, row.size);
    assert.equal(text(await readZipMember(body, member)), row.body);
    assert(built.records.find((entry) => entry.key === `asma:tracks:${row.format}/${row.author}`)?.tracks.some((track) => track.t === row.title));
    assert.equal(asmaFileUrl(`asma://${row.path}`), fileUrlFor(row.path.slice(5), 'asma'));
    assert.equal(asmaFileUrl(`asma://${row.path.slice(5)}`), fileUrlFor(row.path.slice(5), 'asma'));
  }
});
await test('stored and deflated members work with or without descriptors and an end comment', async () => {
  assert.equal(zipCrc32(new TextEncoder().encode('123456789')), 0xcbf43926);
  for (const method of [0, 8]) for (const descriptor of [true, false]) {
    const data = zipFixture([{name:'asma/',body:''}, ...files], { method, descriptor, comment: 'comment PK\x05\x06' });
    const members = zipDirectory(data); assert.equal(members.length, expectedCount);
    await validateZip(new Blob([data]), members);
  }
});
await test('malformed bounds, counts, encryption, methods and ambiguous paths are refused', async () => {
  for (const name of ['../bad.sap','asma/../bad.sap','asma//bad.sap','asma/./bad.sap','/asma/a.sap','asma\\bad.sap']) assert.throws(() => zipDirectory(zipFixture([{name}])));
  assert.throws(() => zipDirectory(zipFixture([{name:'asma/a.sap'},{name:'asma/a.sap'}])), /duplicate/);
  assert.throws(() => zipDirectory(bytes.slice(0, -3)));
  for (const change of [
    (view, end) => view.setUint16(end + 10, 0, true),
    (view, end) => view.setUint16(end + 4, 1, true),
    (view, end) => view.setUint32(end + 16, 0xffffffff, true),
    (view, end) => view.setUint16(view.getUint32(end + 16, true) + 8, 1, true),
    (view, end) => view.setUint16(view.getUint32(end + 16, true) + 10, 12, true),
    (view, end) => view.setUint32(view.getUint32(end + 16, true) + 42, 0xfffffffe, true),
  ]) { const bad = bytes.slice(0); change(new DataView(bad), bad.byteLength - 22); assert.throws(() => zipDirectory(bad)); }
});
await test('CRC, expanded-size and corrupt stored-manifest guards reject damaged payloads', async () => {
  const data = zipFixture([{name:'asma/a.sap',body:'SAP'}],{method:0}), member = zipDirectory(data)[0]; new Uint8Array(data)[member.start] ^= 1;
  await assert.rejects(readZipMember(new Blob([data]), member), /checksum/);
  await assert.rejects(readZipMember(body, {...entries[0], size: entries[0].size - 1}), /declared size|checksum/);
  await assert.rejects(readZipMember(body, {...entries[0], size: 1024 ** 4}), /bounds/);
});
await test('actual storage installs archive and index as one change while retaining documents', async () => {
  await playlists.save({id:'keep',name:'Kept',tracks:[{url:'original'}]}); await settings.set('keep','value'); await played.record({url:'original'});
  await catalogue.putAll([{key:'modland:meta',tracks:7}]); const changes=[]; const off=catalogue.subscribe((change)=>changes.push(change));
  try { assert.equal(await install(),true); } finally {off();}
  assert.equal((await asmaArchive.read()).body.size,bytes.byteLength); assert.equal((await catalogue.get('asma:meta')).tracks,5);
  assert.equal(changes.length,1);assert.deepEqual(changes[0].prefixes,['asma:']);
  assert.equal((await playlists.get('keep')).name,'Kept');assert.equal(await settings.get('keep'),'value');assert.equal(await played.count(),1);assert.equal((await catalogue.get('modland:meta')).tracks,7);
});
await test('quota at archive/control publication rolls back index, payload and revision', async () => {
  const before=await asmaArchive.ticket(), put=IDBObjectStore.prototype.put;
  for(const id of ['asma','asma-control']) {
    IDBObjectStore.prototype.put=function(...args){if(this.name==='asmaArchives'&&args[0].id===id)throw new DOMException('quota','QuotaExceededError');return put.apply(this,args);};
    try {await assert.rejects(asmaArchive.replace({...record,at:99},[{key:'asma:meta',tracks:99}],{ticket:before}),{name:'QuotaExceededError'});}finally{IDBObjectStore.prototype.put=put;}
    assert.equal(await asmaArchive.ticket(),before);assert.equal((await asmaArchive.read()).at,1);assert.equal((await catalogue.get('asma:meta')).tracks,5);
  }
});
await test('invalid dataset records and cancelled transactions preserve the old installation', async () => {
  const ticket=await asmaArchive.ticket();const controller=new AbortController();controller.abort();
  await assert.rejects(asmaArchive.replace(record,records,{ticket,signal:controller.signal}),{name:'AbortError'});
  await assert.rejects(asmaArchive.replace(record,[{key:'modland:bad'}],{ticket}),/outside/);
  await assert.rejects(asmaArchive.replace({...record,id:'other'},records,{ticket}),/payload/);
  assert.equal((await asmaArchive.read()).at,1);assert.equal((await catalogue.get('asma:meta')).tracks,5);
});
await test('cache Clear retains the archive, while archive deletion removes its index alone', async () => {
  await musicCache.write('https://example.test/a.mod',new Blob(['music']));await musicCache.clear();assert.equal((await asmaArchive.read()).body.size,body.size);
  const oldTicket=await asmaArchive.ticket();await asmaArchive.remove();assert.equal(await asmaArchive.read(),null);assert.equal(await catalogue.get('asma:meta'),undefined);
  assert.equal(await asmaArchive.replace(record,records,{ticket:oldTicket}),false);
  assert.equal((await playlists.get('keep')).name,'Kept');assert.equal(await played.count(),1);assert.equal((await catalogue.get('modland:meta')).tracks,7);
});
await test('another production connection prevents a stale post-delete publication and observes bytes', async () => {
  await install();const other=await import('../web/src/store.js?asma-second');const ticket=await asmaArchive.ticket();
  try {assert.equal((await other.asmaArchive.read()).body.size,body.size);await other.asmaArchive.remove();assert.equal(await asmaArchive.replace(record,records,{ticket}),false);}finally{other.forget();}
  assert.equal(await asmaArchive.read(),null);
});
await test('the real downloader retains the whole ZIP, reports progress and returns native counts', async () => {
  const fetch=globalThis.fetch,progress=[];let calls=0;
  globalThis.fetch=async(url,options)=>{calls++;assert.equal(url,'https://asma.atari.org/asmadb/asma.zip');assert(!options.headers?.Range);return new Response(bytes,{headers:{'content-length':String(bytes.byteLength)}});};
  try {const result=await downloadAsma({isPlayable:(name)=>name.endsWith('.sap'),onProgress:(event)=>progress.push(event)});assert.equal(result.tracks,5);}finally{globalThis.fetch=fetch;}
  assert.equal(calls,1);assert(progress.some((event)=>event.stage==='verifying'));assert.equal(progress.at(-1).stage,'done');assert.equal((await asmaArchive.read()).body.size,body.size);
});
await test('corrupt or cancelled downloads leave the previous real index and archive unchanged', async () => {
  const old=await asmaArchive.ticket(),fetch=globalThis.fetch;
  try {
    globalThis.fetch=async()=>new Response(new Uint8Array([1,2,3]));await assert.rejects(downloadAsma());assert.equal(await asmaArchive.ticket(),old);
    const controller=new AbortController();globalThis.fetch=async()=>new Response(bytes);
    await assert.rejects(downloadAsma({signal:controller.signal,onProgress:(event)=>{if(event.stage==='verifying')controller.abort();}}),{name:'AbortError'});
    assert.equal(await asmaArchive.ticket(),old);assert.equal((await asmaArchive.read()).body.size,body.size);
  }finally{globalThis.fetch=fetch;}
});
await test('a delayed real download cannot restore a catalogue deleted during fetch', async () => {
  const fetch=globalThis.fetch;let release,entered;const waiting=new Promise((resolve)=>entered=resolve);
  globalThis.fetch=()=>{entered();return new Promise((resolve)=>release=()=>resolve(new Response(bytes)));};
  try {const job=downloadAsma();await waiting;await asmaArchive.remove();release();await assert.rejects(job,{name:'AbortError'});assert.equal(await asmaArchive.read(),null);assert.equal(await catalogue.get('asma:meta'),undefined);}finally{globalThis.fetch=fetch;}
});
await test('a fresh offline coordinator reads exact immutable payloads and sees cross-tab deletion', async () => {
  await install();const coordinator=new OfflineAsma({storage:asmaArchive,urlForPath:(path)=>fileUrlFor(path.slice(5),'asma'),changed:(notify)=>catalogue.subscribe(notify)});
  await coordinator.ready;const url=fileUrlFor(cases[0].path.slice(5),'asma');assert.equal(coordinator.has(url),true);assert.equal(text(await coordinator.bytes(url)),'SAP');
  const first=await coordinator.bytes(url);structuredClone(first,{transfer:[first]});assert.equal(text(await coordinator.bytes(url)),'SAP');
  const other=await import('../web/src/store.js?asma-deleting');try {await other.asmaArchive.remove();assert.equal(await coordinator.bytes(url),null);assert.equal(coordinator.has(url),false);}finally{other.forget();}
});
await test('a damaged stored manifest cannot establish archive availability', async () => {
  const request=indexedDB.open('protracktor',6);const db=await new Promise((resolve,reject)=>{request.onsuccess=()=>resolve(request.result);request.onerror=()=>reject(request.error);});
  await new Promise((resolve,reject)=>{const transaction=db.transaction('asmaArchives','readwrite');transaction.objectStore('asmaArchives').put({id:'asma',schema:1,body:{size:999},entries});transaction.oncomplete=resolve;transaction.onabort=()=>reject(transaction.error);});db.close();
  await assert.rejects(asmaArchive.read(),/unreadable/);await asmaArchive.remove();
});
await test('a retired initial archive read cannot restore presence after a newer empty snapshot', async () => {
  let release, calls = 0; const delayed = new Promise((resolve) => { release = resolve; });
  const coordinator = new OfflineAsma({ storage: { read: () => ++calls === 1 ? delayed : Promise.resolve(null) },
    urlForPath: (path) => fileUrlFor(path.slice(5), 'asma'), changed: () => {} });
  await coordinator.refresh(); release({ ...record, revision: 1 }); await coordinator.ready;
  assert.equal(coordinator.state.bytes, 0); assert.equal(coordinator.has(fileUrlFor(cases[0].path.slice(5), 'asma')), false);
});
await test('a known corrupt member stays unavailable across metadata refresh until a new archive replaces it', async () => {
  const data = zipFixture([{ name: 'asma/Games/bad.sap', body: 'SAP' }], { method: 0 }), members = zipDirectory(data);
  new Uint8Array(data)[members[0].start] ^= 1;
  let stored = { ...record, body: new Blob([data]), entries: members, revision: 1 };
  const coordinator = new OfflineAsma({ storage: { read: async () => stored }, urlForPath: (path) => fileUrlFor(path.slice(5), 'asma'), changed: () => {} });
  await coordinator.ready; const url = fileUrlFor('Games/bad.sap', 'asma');
  assert.equal(await coordinator.bytes(url), null); assert(coordinator.state.failed); assert.equal(coordinator.has(url), false);
  await coordinator.refresh(); assert(coordinator.state.failed); assert.equal(coordinator.has(url), false);
  const repaired = zipFixture([{ name: 'asma/Games/bad.sap', body: 'SAP' }], { method: 0 });
  stored = { ...stored, body: new Blob([repaired]), entries: zipDirectory(repaired), revision: 2 };
  await coordinator.refresh(); assert.equal(coordinator.state.failed, null); assert.equal(coordinator.has(url), true); assert.equal(text(await coordinator.bytes(url)), 'SAP');
});
assert.equal(count,16);forget();console.log('16 full-archive/production-storage cases passed; browser CORS/audio remain unchecked.');
