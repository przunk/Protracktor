// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later
import fs from 'node:fs';
import { PLATFORM_DATA } from '../web/src/platforms.js';
import { platformProjection, platformModule } from './generate-web-platforms.mjs';
import { PLATFORMS, platformForFormat, platformForFileName, platformForCatalogueRow } from '../web/src/random-scope.js';
import assert from 'node:assert/strict';
import { storedRandomScope, parseModlandFavourites, RandomFilterState } from '../web/src/random-scope.js';
let checks = 0;
const test = async (name, run) => { try { await run(); } catch (error) { console.error(`✗ ${name}`); throw error; } checks++; console.log(`✓ ${name}`); };
const deferred = () => { let resolve; const promise = new Promise((done) => { resolve = done; }); return { promise, resolve }; };
const counts = (amiga = 42, favouriteCount = 2, favouritesListed = 3) => ({ platformCounts: { amiga }, favouriteCount, favouritesListed });
const known = async () => { const state = new RandomFilterState(); await state.refresh({ active: true, key: 'A' }, async () => counts()); return state; };
await test('native stored scopes retain named and unknown nonempty platform IDs', () => {
  for (const scope of ['everything','favourites','platform:amiga','platform:future','platform: ']) assert.equal(storedRandomScope(scope),scope);
  for (const value of [null,undefined,{},true,23,'','other','platform:']) assert.equal(storedRandomScope(value),'everything');
});
const full = (path) => ['md5','1','300','player','uade','MOD','0','123','crc','samplecrc',path].join('\t');
await test('only native eleven-column favourites rows carry paths', () => {
  assert.deepEqual(parseModlandFavourites([full('Protracker/Artist/title.mod'),'hash\t2\t100\tloop',full('Spectrum/Pro Tracker 3/Author/abc.pt3')].join('\n')),
    ['Protracker/Artist/title.mod','Spectrum/Pro Tracker 3/Author/abc.pt3']);
});
await test('short subsong and blank rows are never mistaken for favourites', () => {
  assert.deepEqual(parseModlandFavourites(['','   ','one\ttwo','hash\t1\t100\tplayer',full('  '),full('')].join('\n')),[]);
});
await test('native line handling retains duplicate and nonblank original paths', () => {
  assert.deepEqual(parseModlandFavourites(full(' x ')+'\r\n'+full(' x ')+'\r'+full('z')),[' x ',' x ','z']);
});
await test('cold availability is checking, not a zero/missing favourites claim', async () => {
  const state = new RandomFilterState(), read=deferred();
  const job=state.refresh({active:true,key:'A'},()=>read.promise);
  assert.deepEqual(state.state,{availability:null,loading:true,failed:false});
  read.resolve(counts()); await job;
  assert.equal(state.state.availability.platformCounts.amiga,42); assert.equal(state.state.loading,false);
});
await test('Random preloading and sheet reopening do not recount the same resources', async () => {
  const state = new RandomFilterState(); let reads=0;
  const load=async()=>{reads++;return counts();};
  await state.refresh({active:true,key:'A'},load); await state.refresh({active:true,key:'A'},load); assert.equal(reads,1);
});
await test('inactive Random does not query availability', async () => {
  const state=new RandomFilterState();let reads=0;
  await state.refresh({active:false,key:'A'},async()=>{reads++;return counts();});assert.equal(reads,0); assert.equal(state.state.loading,false);
});
await test('warm reentry keeps known chips usable during delayed refresh', async () => {
  const state=await known(), read=deferred();await state.refresh({active:false,key:'A'},()=>assert.fail());
  const pending=state.refresh({active:true,key:'A'},()=>read.promise);
  assert.equal(state.state.availability.platformCounts.amiga,42);assert.equal(state.state.loading,true);read.resolve(counts(23));await pending;
  assert.equal(state.state.availability.platformCounts.amiga,23);
});
await test('download state changes retain verified availability while current resources refresh', async () => {
  const state=await known(),read=deferred();const pending=state.refresh({active:true,key:'A-downloading'},()=>read.promise);
  assert.equal(state.state.availability.platformCounts.amiga,42);read.resolve(counts(23));await pending;
});
await test('deletion blocks counts before a delayed replacement read', async () => {
  const state=await known();await state.refresh({active:true,blocked:true,key:'deleting'},()=>assert.fail());
  assert.deepEqual(state.state,{availability:null,loading:true,failed:false});
  await state.refresh({active:true,key:'B'},async()=>counts(0,0,3));assert.equal(state.state.availability.favouriteCount,0);
});
await test('resources changed while inactive cannot reuse old enabled chips', async () => {
  const state=await known();await state.refresh({active:false,key:'B'},()=>assert.fail());assert.equal(state.state.availability,null);
});
await test('refresh failure retains known counts and a forced Retry can recover', async () => {
  const state=await known();await state.refresh({active:true,key:'B'},async()=>{throw Error('store');});
  assert.equal(state.state.availability.platformCounts.amiga,42);assert.equal(state.state.failed,true);
  await state.refresh({active:true,key:'B',force:true},async()=>counts(7));assert.equal(state.state.availability.platformCounts.amiga,7);assert.equal(state.state.failed,false);
});
await test('an obsolete noncancellable read cannot replace newer resources', async () => {
  const state=new RandomFilterState(),old=deferred();const pending=state.refresh({active:true,key:'A'},()=>old.promise);
  await state.refresh({active:true,key:'B'},async()=>counts(20));old.resolve(counts(1));await pending;assert.equal(state.state.availability.platformCounts.amiga,20);
});
await test('first-read failure offers Retry without pretending a favourites list is absent', async () => {
  const state=new RandomFilterState();await state.refresh({active:true,key:'A'},async()=>{throw Error('read');});
  assert.deepEqual(state.state,{availability:null,loading:false,failed:true});
});
await test('database preparation blocks reads and its completion refreshes once', async () => {
  const state=new RandomFilterState();let reads=0;const load=async()=>{reads++;return counts();};
  await state.refresh({active:true,blocked:true,key:'A'},load);assert.equal(reads,0);
  await state.refresh({active:true,key:'A'},load);assert.equal(reads,1);assert.equal(state.state.loading,false);
});
await test('closing while reading cannot publish into the next opening', async () => {
  const state=new RandomFilterState(),old=deferred();const pending=state.refresh({active:true,key:'A'},()=>old.promise);
  await state.refresh({active:false,key:'A'},()=>assert.fail());await state.refresh({active:true,key:'B'},async()=>counts(90));old.resolve(counts(10));await pending;
  assert.equal(state.state.availability.platformCounts.amiga,90);
});
await test('accepted counters are immutable snapshots independent of the loader', async () => {
  const state=new RandomFilterState(),data=counts();await state.refresh({active:true,key:'A'},async()=>data);
  data.platformCounts.amiga=0;assert.equal(state.state.availability.platformCounts.amiga,42);assert.throws(()=>{state.state.availability.platformCounts.amiga=0;},TypeError);
});
await test('all native platform declarations and their chip order are exactly projected', () => {
  const native=fs.readFileSync('app/src/main/kotlin/com/przunk/protracktor/player/Platforms.kt','utf8');
  assert.deepEqual(PLATFORM_DATA,platformProjection(native));
  assert.equal(fs.readFileSync('web/src/platforms.js','utf8'),platformModule(platformProjection(native)));
  assert.deepEqual(PLATFORMS.map((p)=>p.id),['amiga','pc','c64','nintendo','sega','atari-st','atari-8bit','spectrum','pc98','sharp','sony','msx','pc-engine']);
});
await test('every supported native name has one platform and no invented name', () => {
  const text=fs.readFileSync('web/src/formats.tsv','utf8');
  const names=new Set(text.split('\n').filter((line)=>/^(extension|prefix)\t/.test(line)).map((line)=>line.split('\t')[1]));
  const all=PLATFORMS.flatMap((p)=>p.names);
  assert.equal(new Set(all).size,all.length);assert.deepEqual(new Set(all),names);
});
await test('catalogue formats are unique and match native case/whitespace rules', () => {
  const formats=PLATFORMS.flatMap((p)=>p.catalogueFormats);assert.equal(formats.length,new Set(formats).size);
  for (const [format,id] of [['Protracker','amiga'],['  protracker ','amiga'],['HVSC','c64'],['SNDH','atari-st'],['FM Sound Driver (FMP)','pc98']]) assert.equal(platformForFormat(format)?.id,id);
  assert.equal(platformForFormat('Organya'),null);
});
await test('native filename classification accepts both conventions and leaves unknown files unclassified', () => {
  for (const [name,id] of [['4mat-elysium.mod','amiga'],['mod.elysium','amiga'],['cruisin.ahx','amiga'],['commando.sid','c64'],['zelda.spc','nintendo']]) assert.equal(platformForFileName(name)?.id,id);
  assert.equal(platformForFileName('README'),null);assert.equal(platformForFileName('notes.txt'),null);
});
await test('C89 whole-archive classification precedes directory and filename ambiguity', () => {
  for (const [source,format,name,id] of [['asma','Composers','Bonio.sap','atari-8bit'],['asma','Games','whatever.xyz','atari-8bit'],['unexotica','Turrican','mdat.title','amiga'],['modland','Spectrum','tune.psm','spectrum'],['modland','Slight Atari Player','a.sap','atari-8bit'],['modland','Some New Folder','a.sap','atari-8bit']])
    assert.equal(platformForCatalogueRow(source,format,name)?.id,id);
  assert.equal(platformForCatalogueRow('modland','Some New Folder','readme'),null);
});
await test('checked WEB tables reject mutation and native data drift changes their projection', () => {
  assert.throws(()=>PLATFORMS[0].names.push('new'),TypeError);
  const native=fs.readFileSync('app/src/main/kotlin/com/przunk/protracktor/player/Platforms.kt','utf8');
  assert.notDeepEqual(PLATFORM_DATA,platformProjection(native.replace('name = "Amiga"','name = "Changed"')));
});
await test('a replacement or closed filter owner cancels cooperative availability reads', async () => {
  const state=new RandomFilterState();let signal;
  const pending=state.refresh({active:true,key:'A'},async(readSignal)=>{signal=readSignal;await new Promise((resolve)=>readSignal.addEventListener('abort',resolve,{once:true}));return counts(1);});
  await state.refresh({active:false,key:'B'},()=>assert.fail());assert.equal(signal.aborted,true);await pending;
  assert.equal(state.state.availability,null);assert.equal(state.state.failed,false);
});
console.log(`${checks} Random-scope cases passed.`);
