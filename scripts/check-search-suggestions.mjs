// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later
import assert from 'node:assert/strict';
import { JSDOM } from '../web/node_modules/jsdom/lib/api.js';
import { SearchSuggestions } from '../web/src/search-suggestions.js';
import { recentSearchSuggestions } from '../web/src/recent-searches.js';
import { useLanguage } from '../web/src/i18n.js';

let checks = 0;
function fixture() {
  const window = new JSDOM('<div id="root"><input id="query"><button></button><ul id="suggestions"></ul></div><button id="outside">Outside</button>',
    { pretendToBeVisual: true }).window;
  globalThis.document = window.document;
  const $ = (selector) => document.querySelector(selector);
  const calls = [], typed = [];
  let queries = ['delta one', 'DELTA two', 'delta three', 'delta four', 'other'];
  const view = new SearchSuggestions({ root: $('#root'), input: $('input'), submit: $('button'), list: $('ul'),
    suggestions: (text) => recentSearchSuggestions(queries, text), onInput: (text) => typed.push(text),
    onSubmit: (text) => calls.push(text) });
  view.setVisible(true);
  return { window, $, view, calls, typed, key: (key) => $('input').dispatchEvent(new window.KeyboardEvent('keydown', { key, bubbles: true, cancelable: true })),
    input: (text) => { $('input').value = text; $('input').dispatchEvent(new window.Event('input', { bubbles: true })); },
    loaded: (next) => { queries = next; view.refresh(); } };
}
function test(name, run) {
  const f = fixture();
  try { run(f); } catch (error) { console.error('✗ ' + name); throw error; }
  finally { f.window.close(); }
  checks++; console.log('✓ ' + name);
}
test('focusing an empty field shows only three text options without decorative controls', ({ $ }) => {
  $('input').focus(); assert.equal($('input').getAttribute('aria-expanded'), 'true');
  assert.equal($('ul').children.length, 3); assert.equal($('ul').querySelector('svg,button'), null);
  assert.equal($('ul').children[0].textContent, 'delta one');
});
test('typing filters before the cap and does not submit', ({ $, input, typed, calls }) => {
  $('input').focus(); input('  deLTa'); assert.deepEqual(typed, ['  deLTa']); assert.equal(calls.length, 0);
  assert.equal($('ul').children.length, 3); input('missing'); assert.equal($('ul').hidden, true);
});
test('clicking an option searches immediately and leaves focus and popup closed', ({ $, calls, loaded }) => {
  $('input').focus(); const row = $('ul').children[1];
  row.dispatchEvent(new document.defaultView.Event('pointerdown', { bubbles: true, cancelable: true }));
  row.click(); assert.deepEqual(calls, ['DELTA two']); assert.equal($('input').value, 'DELTA two');
  assert.notEqual(document.activeElement, $('input')); loaded(['DELTA two', 'delta one']);
  assert.equal($('ul').hidden, true); assert.equal($('input').getAttribute('aria-expanded'), 'false');
});
test('Enter submits the typed query exactly once without choosing the first option', ({ $, input, key, calls }) => {
  $('input').focus(); input('delta'); key('Enter'); assert.deepEqual(calls, ['delta']); assert.equal($('ul').hidden, true);
});
test('Arrow navigation exposes the active option and Enter chooses it', ({ $, key, calls }) => {
  $('input').focus(); key('ArrowDown'); key('ArrowDown');
  assert.equal($('input').getAttribute('aria-activedescendant'), 'suggestions-1');
  assert.equal($('ul').children[1].getAttribute('aria-selected'), 'true'); key('Enter'); assert.deepEqual(calls, ['DELTA two']);
});
test('Escape and Tab close without submitting or stealing focus', ({ $, key, calls }) => {
  $('input').focus(); key('Escape'); assert.equal($('ul').hidden, true); assert.equal(document.activeElement, $('input'));
  key('ArrowDown'); assert.equal($('ul').hidden, false); key('Tab'); assert.equal($('ul').hidden, true); assert.equal(calls.length, 0);
});
test('late reads do not open a field after blur, hiding or a submission', ({ $, view, loaded }) => {
  $('input').focus(); $('#outside').focus(); loaded(['late']); assert.equal($('ul').hidden, true);
  view.setVisible(false); loaded(['other']); assert.equal($('ul').hidden, true); assert.notEqual(document.activeElement, $('input'));
});
test('the integrated Search button submits without extra visible text', ({ $, input, calls }) => {
  input('typed'); $('button').click(); assert.deepEqual(calls, ['typed']); assert.equal($('button').textContent, '');
  assert.equal($('button').getAttribute('aria-label'), 'Search');
});
test('query text is literal rather than HTML', ({ $, loaded }) => {
  loaded(['<img src=x onerror=alert(1)>']); $('input').focus(); assert.equal($('ul').querySelector('img'), null);
  assert.equal($('ul').children[0].textContent, '<img src=x onerror=alert(1)>');
});
test('Polish accessible labels update without replacing the input or touching its query', ({ $, view, input }) => {
  input('delta'); const original = $('input'); useLanguage('pl'); view.refresh();
  assert.equal($('input'), original); assert.equal($('input').value, 'delta'); assert.equal($('button').getAttribute('aria-label'), 'Szukaj');
  assert.equal($('ul').getAttribute('aria-label'), 'Ostatnie wyszukiwania'); useLanguage('en');
});
test('the popup flips above an anchor near the keyboard and resizes through the real event', ({ $, window }) => {
  $('#root').getBoundingClientRect = () => ({ top: 700, bottom: 748 });
  $('input').focus(); window.dispatchEvent(new window.Event('resize'));
  assert.equal($('ul').style.bottom, '100%'); assert.equal($('ul').style.maxHeight, '144px');
  $('#root').getBoundingClientRect = () => ({ top: 10, bottom: 58 });
  window.dispatchEvent(new window.Event('resize')); assert.equal($('ul').style.top, '100%');
});
console.log(`${checks} search-suggestion event cases passed.`);
