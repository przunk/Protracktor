// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later
import assert from 'node:assert/strict';
import { JSDOM } from '../web/node_modules/jsdom/lib/api.js';
import { TrackSelection } from '../web/src/track-selection.js';
import { BrowseSelectionView } from '../web/src/browse-selection-view.js';
import { useLanguage } from '../web/src/i18n.js';
const dom = new JSDOM('<header><span></span><button><svg></svg><span></span></button></header><ol></ol>', { pretendToBeVisual: true });
const { document } = dom.window; globalThis.document = document; let checks = 0, changed = 0, qualified = false, disposed = 0, view;
const test = (name, run) => { try { run(); } catch (error) { console.error('✗ ' + name); throw error; } checks++; console.log('✓ ' + name); };
const leading = () => { const slot = document.createElement('span'); slot.className = 'track-leading'; slot.textContent = qualified ? '✓' : ''; return slot; };
const model = new TrackSelection({ changed: () => view.render() });
view = new BrowseSelectionView({ model, header: document.querySelector('header'), count: document.querySelector('header > span'),
  all: document.querySelector('button'), leading, changed: () => changed++ });
let display = 'Current title';
const rows = [0, 1, 2].map((i) => {
  const node = document.createElement('li'), text = document.createElement('div'), add = document.createElement('button'), more = document.createElement('button');
  text.tabIndex = 0; node.append(leading(), text, add, more); document.querySelector('ol').append(node);
  const track = { url: String(i), name: `Selection ${i}` }, owner = 'history';
  node.onclick = () => model.toggle(owner, track.url);
  return { node, text, add, more, track, owner, shown: () => ({ name: display }), dispose: () => disposed++ };
});
view.sync('history', rows);
test('the header and leading slots exist before selection and rows retain their physical identity', () => {
  assert.equal(view.header.hidden, false); assert.equal(view.all.hidden, true); assert.equal(view.count.textContent, '3 tracks');
  model.start('history', '2'); assert.deepEqual([...document.querySelector('ol').children], rows.map((row) => row.node));
  assert.equal(view.count.textContent, '1 selected'); assert.equal(rows.every((row) => row.add.hidden && row.more.hidden), true);
});
test('the actual checkbox click toggles exactly once without bubbling into the row action', () => {
  rows[0].node.querySelector('input').click(); assert.equal(model.count, 2); assert.equal(model.has('0'), true);
  rows[0].node.querySelector('input').click(); assert.equal(model.count, 1); assert.equal(model.has('0'), false);
});
test('the actual Select all/none button follows native counts and restores current leading status', () => {
  view.all.click(); assert.equal(model.count, 3); assert.equal(view.all.querySelector('span').textContent, 'Select none');
  qualified = true; view.all.click(); assert.equal(model.count, 0); assert.equal(view.all.hidden, true);
  assert.equal(rows.every((row) => row.node.querySelector('.track-leading').textContent === '✓'), true);
});
test('the last deselected checkbox transfers focus to the stable row text', () => {
  model.start('history', '0'); const input = rows[0].node.querySelector('input'); input.focus(); input.click();
  assert.equal(model.active, false); assert.equal(document.activeElement, rows[0].text);
});
test('same-owner refresh prunes removed rows, disposes obsolete gestures and keeps remaining selection', () => {
  model.start('history', '0'); model.toggle('history', '2'); rows[2].node.remove();
  view.sync('history', rows.slice(0, 2)); assert.equal(disposed, 1); assert.equal(model.count, 1); assert.equal(model.has('0'), true);
});
test('language/title refresh updates checkbox descriptions without replacing the focused checkbox', () => {
  const input = rows[0].node.querySelector('input'); input.focus(); display = 'Nowa nazwa'; useLanguage('pl'); view.render();
  assert.equal(rows[0].node.querySelector('input'), input); assert.equal(document.activeElement, input);
  assert.equal(input.getAttribute('aria-label'), 'Zaznacz Nowa nazwa'); assert.equal(view.all.querySelector('span').textContent, 'Zaznacz wszystkie');
});
test('changing owner retires selection and restores any still-connected former rows', () => {
  view.sync('new-route', []); assert.equal(model.active, false); assert.equal(view.header.hidden, true);
  assert.equal(rows[0].node.querySelector('input'), null); assert.equal(rows[0].add.hidden, false); assert.ok(changed > 0);
});
useLanguage('en'); dom.window.close(); assert.ok(checks > 0); console.log(`${checks} Browse selection presentation checks passed (host DOM only)`);
