// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later
import assert from 'node:assert/strict';
import { learnedDisplay, learnedSourceLine } from '../web/src/learned-titles.js';
let checks=0;
const test=(name,run)=>{try{run();}catch(error){console.error('✗ '+name);throw error;}checks++;console.log('✓ '+name);};
test('display changes names without changing order identity or duplicate occurrences',()=>{
 const a={url:'a',name:'a.mod',meta:'Modland / Author',size:42,author:'Author'},z={url:'z',name:'z.mod',meta:'Modland / Author',size:80};
 const rows=[a,z,a],titles=new Map([['a','Zulu'],['z','Alpha']]);const shown=rows.map((row)=>learnedDisplay(row,titles));
 assert.deepEqual(shown.map((row)=>row.url),['a','z','a']);assert.deepEqual(shown.map((row)=>row.name),['Zulu','Alpha','Zulu']);
 assert.deepEqual(shown.map((row)=>row.file),['a.mod','z.mod','a.mod']);assert.equal(learnedSourceLine(shown[0]),'Modland / Author/a.mod');
 assert.deepEqual(shown.map((row)=>row.author),rows.map((row)=>row.author));assert.deepEqual(shown.map((row)=>row.meta),rows.map((row)=>row.meta));
 assert.equal(shown[0].size,42);assert.equal(a.name,'a.mod');assert.equal(a.meta,'Modland / Author');
});
test('missing blank and unrelated titles retain the original object',()=>{
 const track={url:'a',name:'a.mod',meta:'Source'};
 for(const titles of [new Map(),new Map([['a','  ']]),new Map([['other','Title']])])assert.equal(learnedDisplay(track,titles),track);
});
test('source label includes the filename once and never rewrites escaped identity',()=>{
 const track={url:'id:escaped%2Fpart',name:'file.mod',file:'file.mod',meta:'Source/file.mod'};
 const shown=learnedDisplay(track,new Map([[track.url,'  Decoded  ']]));assert.equal(shown.name,'Decoded');assert.equal(shown.meta,track.meta);assert.equal(shown.url,track.url);
 assert.deepEqual(learnedDisplay(shown,new Map([[track.url,'Decoded']])),shown);
 assert.equal(learnedSourceLine(shown),'Source/file.mod');assert.equal(learnedSourceLine({...shown,meta:''}),'file.mod');
});
test('the production filename boundary can supply an original name independent of the label',()=>{
 const track={url:'https://example.test/file.mod',name:'Link title'};
 const shown=learnedDisplay(track,new Map([[track.url,'Decoded']]),'file.mod');assert.equal(shown.file,'file.mod');assert.equal(track.file,undefined);
});
test('trailing path separators whitespace and already-complete paths match native rules',()=>{
 const track={url:'a',name:'Title',file:'file.mod',meta:'Source///'};
 assert.equal(learnedSourceLine(track),'Source/file.mod');assert.equal(learnedSourceLine({...track,meta:'  '}),'file.mod');
 assert.equal(learnedSourceLine({...track,meta:'file.mod'}),'file.mod');assert.equal(learnedSourceLine({...track,name:'file.mod'}),'Source///');
});
console.log(checks+' learned-title cases passed.');
