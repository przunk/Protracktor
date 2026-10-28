// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later
import assert from 'node:assert/strict';
import { JSDOM } from '../web/node_modules/jsdom/lib/api.js';
import { message, pluralMessage, useLanguage, translateStatic, resolveLanguage } from '../web/src/i18n.js';
import { uiMessage, joinUiText, renderUiValue } from '../web/src/ui-message.js';
import { setUiText, setUiAttribute, makeUiText, refreshUiText } from '../web/src/ui-text.js';
let checks = 0;
const test = (name, run) => {
  const dom = new JSDOM('<main><span id="label">Random</span><span id="raw"></span><button id="control" title="Search"><svg></svg></button></main>');
  const previous = globalThis.document; globalThis.document = dom.window.document;
  try { useLanguage('en'); run({ document: dom.window.document, window: dom.window, $: (id) => dom.window.document.getElementById(id) }); }
  catch (error) { console.error(`✗ ${name}`); throw error; }
  finally { dom.window.close(); if (previous) globalThis.document = previous; else delete globalThis.document; }
  checks++; console.log(`✓ ${name}`);
};
test('explicit UI meanings translate while identical raw user text stays literal', ({$,document}) => {
  setUiText($('label'), message('Random')); setUiText($('raw'), 'Random');
  useLanguage('pl'); refreshUiText(document.body);
  assert.equal($('label').textContent,'Losowo'); assert.equal($('raw').textContent,'Random');
  useLanguage('en'); refreshUiText(document.body); assert.equal($('label').textContent,'Random');
});
test('message arguments snapshot the action instead of a later playlist name', ({document}) => {
  const vars={name:'First playlist'}, value=message('Removed {name}',vars); vars.name='Second playlist';
  useLanguage('pl'); assert.equal(renderUiValue(value),'Usunięto First playlist');
  useLanguage('en'); assert.equal(String(value),'Removed First playlist');
});
test('raw placeholder values are stringified at action time without retaining mutable data', () => {
  const name=['First playlist'], value=message('Removed {name}',{name}); name.push('Second playlist');
  useLanguage('pl'); assert.equal(renderUiValue(value),'Usunięto First playlist');
});
test('composition snapshots branches and raw arguments but keeps nested message meanings', () => {
  let condition=true; const value=joinUiText('Raw Random · ',condition?message('Random'):message('Search'),' · ',message('Removed {name}',{name:message('Search')}));
  condition=false; useLanguage('pl'); assert.equal(renderUiValue(value),'Raw Random · Losowo · Usunięto Szukaj');
});
test('composition stringifies mutable raw parts at action time', () => {
  const parts=['First playlist'], value=joinUiText(message('Removed {name}',{name:'Random'}),' · ',parts);
  parts.push('Second playlist'); useLanguage('pl');
  assert.equal(renderUiValue(value),'Usunięto Random · First playlist');
});
test('plural snapshots keep native count semantics when the language changes', () => {
  for(const [n,polish] of [[1,'1 utwór'],[2,'2 utwory'],[5,'5 utworów'],[12,'12 utworów'],[22,'22 utwory']]) {
    const vars={count:n}, value=pluralMessage(n,'{count} tune','{count} tunes',vars); vars.count=999;
    useLanguage('pl'); assert.equal(renderUiValue(value),polish);
    useLanguage('en'); assert.equal(renderUiValue(value),`${n} ${n===1?'tune':'tunes'}`);
  }
});
test('replacing a bound fallback with identical raw text retires its meaning by node identity', ({$,document}) => {
  setUiText($('label'),message('Random')); const old=$('label').firstChild;
  $('label').textContent='Random'; assert.notEqual($('label').firstChild,old);
  useLanguage('pl'); refreshUiText(document.body); assert.equal($('label').textContent,'Random');
});
test('an explicit raw write on the same text node clears its previous binding', ({$,document}) => {
  const node=setUiText($('label'),message('Random')); setUiText(node,'Random');
  useLanguage('pl'); refreshUiText(document.body); assert.equal(node.textContent,'Random');
});
test('an externally changed text node cannot be overwritten by an older binding', ({$,document}) => {
  const node=setUiText($('label'),message('Random')); node.textContent='User title';
  useLanguage('pl'); refreshUiText(document.body); assert.equal(node.textContent,'User title');
});
test('removed rows are not visited and rebinding the active row replaces its old meaning', ({$,document}) => {
  const node=setUiText($('label'),message('Random')); $('label').remove();
  setUiText($('raw'),message('Random')); setUiText($('raw'),message('Search'));
  useLanguage('pl'); refreshUiText(document.body);
  assert.equal(node.textContent,'Random'); assert.equal($('raw').textContent,'Szukaj');
});
test('caption refresh retains its button icon focus and event ownership', ({$,document}) => {
  const control=$('control'), icon=control.firstChild; let clicks=0;
  const node=makeUiText(document,message('Search')); control.append(node); control.onclick=()=>clicks++; control.focus();
  useLanguage('pl'); refreshUiText(document.body);
  assert.equal(control.firstChild,icon); assert.equal(control.lastChild,node); assert.equal(document.activeElement,control);
  control.click(); assert.equal(clicks,1); assert.equal(node.textContent,'Szukaj');
});
test('accessible attributes translate and later explicit raw writes retire ownership', ({$,document}) => {
  setUiAttribute($('control'),'title',message('Search')); setUiAttribute($('control'),'aria-label',message('Random'));
  useLanguage('pl'); refreshUiText(document.body); assert.equal($('control').title,'Szukaj'); assert.equal($('control').getAttribute('aria-label'),'Losowo');
  setUiAttribute($('control'),'title','Search'); $('control').setAttribute('aria-label','Raw author');
  useLanguage('en'); refreshUiText(document.body); assert.equal($('control').title,'Search'); assert.equal($('control').getAttribute('aria-label'),'Raw author');
});
test('original English markup remains translatable through repeated round trips', ({$,document}) => {
  translateStatic(document.body); useLanguage('pl'); translateStatic(document.body);
  assert.equal($('label').textContent,'Losowo'); assert.equal($('control').title,'Szukaj');
  useLanguage('en'); translateStatic(document.body); assert.equal($('label').textContent,'Random'); assert.equal($('control').title,'Search');
  useLanguage('pl'); translateStatic(document.body); assert.equal($('label').textContent,'Losowo');
});
test('static capture cannot enlist later user rows or replaced user text into translations', ({$,document}) => {
  translateStatic(document.body); $('label').textContent='Random';
  const raw=document.createElement('span'); raw.textContent='Search'; document.body.append(raw);
  useLanguage('pl'); translateStatic(document.body); assert.equal($('label').textContent,'Random'); assert.equal(raw.textContent,'Search');
});
test('static markup capture preserves whitespace and ignores style and script contents', ({document}) => {
  const root=document.createElement('section'); root.innerHTML='\n  <span>  Random  </span><script>Random</script><style>Search</style>\n'; document.body.append(root);
  translateStatic(root); useLanguage('pl'); translateStatic(root);
  assert.equal(root.querySelector('span').textContent,'  Losowo  '); assert.equal(root.querySelector('script').textContent,'Random'); assert.equal(root.querySelector('style').textContent,'Search');
});
test('native language selection cases retain explicit choices and System fallback', () => {
  for(const [choice,preferred,expected] of [['system',['pl-PL'],'pl'],['en',['pl'],'en'],['pl',['en'],'pl'],['obsolete',['pl'],'pl'],['system',['de-DE'],'en']]) assert.equal(resolveLanguage(choice,preferred),expected);
  assert.throws(()=>uiMessage(null),TypeError);
});
assert(checks>0); console.log(`${checks} UI message and binding cases passed (host DOM only)`);
