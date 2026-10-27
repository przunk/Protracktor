// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later
import assert from 'node:assert/strict';
import { JSDOM } from '../web/node_modules/jsdom/lib/api.js';
import { SavedPlacesView } from '../web/src/saved-places-view.js';
import { useLanguage } from '../web/src/i18n.js';
const window = new JSDOM('<main><div id="header"></div><div id="tools"></div><ol></ol><p id="note"></p></main>').window;
globalThis.document = window.document;
let checks=0;
const test=(name,run)=>{try{run();}catch(error){console.error('✗ '+name);throw error;}checks++;console.log('✓ '+name);};
const key={catalogueId:'modland',format:'Protracker',author:'Artist'};
const place={key,catalogueName:'Modland',savedAt:1};
const calls=[];
const $=(selector)=>document.querySelector(selector);
const view=new SavedPlacesView({header:$('#header'),tools:$('#tools'),list:$('ol'),note:$('#note'),icon:(name)=>`<svg data-icon="${name}" aria-hidden="true"></svg>`,
 onToggle:(key,saved)=>calls.push(['toggle',key,saved]),onOpen:(key)=>calls.push(['open',key]),onRemove:(key)=>calls.push(['remove',key]),onPrepare:()=>calls.push(['catalogues']),onRetry:()=>calls.push(['refresh'])});
const empty={rows:[],loading:false,failed:false,writing:false,opening:null};
const available={...empty,rows:[{place,availability:'available'}]};
test('folder Bookmark keeps its label and exposes outlined/filled accessible state',()=>{
 view.render({state:empty,key,inList:false});const button=$('#header button');assert.equal(button.textContent,'Bookmark');assert.equal(button.getAttribute('aria-pressed'),'false');button.click();assert.deepEqual(calls.pop(),['toggle',key,false]);
 view.render({state:available,key,inList:false});assert.equal(button.textContent,'Bookmark');assert.equal(button.getAttribute('aria-pressed'),'true');assert.equal(button.querySelector('svg').dataset.icon,'bookmarkFilled');button.click();assert.deepEqual(calls.pop(),['toggle',key,true]);
});
test('saved row shows title/path and Open then Bookmark with actual callbacks',()=>{
 view.render({state:available,key:null,inList:true});const row=$('li');assert.equal(row.querySelector('.bname').textContent,'Artist');assert.equal(row.querySelector('.bmeta').textContent,'Modland › Protracker');
 const actions=[...row.querySelectorAll('.saved-actions button')];assert.deepEqual(actions.map((button)=>button.textContent),['Open','Bookmark']);actions[0].click();actions[1].click();assert.deepEqual(calls.splice(0),[['open',key],['remove',key]]);
});
test('busy updates keep the keyed row and explain why its controls are unavailable',()=>{
 const row=$('li');view.render({state:{...available,writing:true},key:null,inList:true});assert.equal($('li'),row);assert.ok([...row.querySelectorAll('.saved-actions button')].every((button)=>button.disabled));assert.ok($('#note').textContent.includes('Updating'));
});
test('missing index disables Open with a reason and offers explicit catalogues only',()=>{
 view.render({state:{...available,rows:[{place,availability:'index-missing'}]},key:null,inList:true});const row=$('li');assert.ok(row.querySelector('.saved-actions button').disabled);assert.ok(row.querySelector('.saved-reason').textContent.includes('index'));assert.equal(row.querySelectorAll('button')[2].hidden,false);row.querySelectorAll('button')[2].click();assert.deepEqual(calls.pop(),['catalogues']);
});
test('unavailable catalogue remains removable without a false download recovery',()=>{
 view.render({state:{...available,rows:[{place,availability:'catalogue-unavailable'}]},key:null,inList:true});assert.equal($('li').querySelectorAll('button')[2].hidden,true);assert.equal($('li').querySelectorAll('button')[1].disabled,false);
});
test('failed read offers Retry instead of a false empty state',()=>{
 view.render({state:{...empty,failed:true},key:null,inList:true});assert.ok($('#note').textContent.includes('Could not'));assert.equal($('li'),null);$('#tools button').click();assert.deepEqual(calls.pop(),['refresh']);
});
test('removing the last saved row produces an explanatory empty state',()=>{
 view.render({state:available,key:null,inList:true});view.render({state:empty,key:null,inList:true});assert.equal($('li'),null);assert.ok($('#note').textContent.includes('Open an author'));
});
test('Polish Bookmark and state description use native strings',()=>{
 useLanguage('pl');view.render({state:available,key,inList:false});assert.equal($('#header button').textContent,'Zakładka');assert.ok($('#header button').getAttribute('aria-label').includes('Dodano zakładkę'));useLanguage('en');
});
console.log(checks+' Saved places view cases passed.');
