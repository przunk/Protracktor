// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later
import assert from 'node:assert/strict';
import { JSDOM } from '../web/node_modules/jsdom/lib/api.js';
import { BrowseScroll } from '../web/src/browse-scroll.js';
import { BrowseScrollView } from '../web/src/browse-scroll-view.js';
let checks = 0;
const test = (name, run) => {
  try { run(); } catch (error) { console.error(`✗ ${name}`); throw error; }
  checks++; console.log(`✓ ${name}`);
};
function fixture() {
  const dom = new JSDOM('<ul></ul>'), viewport = dom.window.document.querySelector('ul'), model = new BrowseScroll();
  let visible = true;
  const view = new BrowseScrollView({ model, viewport, visible: () => visible });
  viewport.getBoundingClientRect = () => ({ top: 100 });
  const rows = (names) => {
    viewport.replaceChildren(...names.map((name, i) => {
      const row = dom.window.document.createElement('li'); row.dataset.browseKey = name;
      row.getBoundingClientRect = () => ({ top: 100 + i * 40 - viewport.scrollTop }); return row;
    }));
  };
  const render = (level, names, loading = false) => { const visit = view.begin(level); rows(names); view.finish(visit, { loading }); return visit; };
  return { dom, viewport, model, view, rows, render, visible: (value) => { visible = value; } };
}
test('a changed index restores the departed identity instead of its previous numeric position', () => {
  const f = fixture(); f.render('formats', ['a', 'b', 'c']); f.viewport.scrollTop = 75; f.view.remember('b');
  f.render('author', ['song']); f.render('formats', ['b', 'a', 'c']);
  assert.equal(f.viewport.scrollTop, 0); assert.equal(f.model.restoreFor('formats', ['b']).kind, 'nothing'); f.dom.window.close();
});
test('a missing departed row consumes its marker and retains the original level offset', () => {
  const f = fixture(); f.render('formats', ['a', 'b', 'c']); f.viewport.scrollTop = 75; f.view.remember('b');
  f.render('author', ['song']); f.render('formats', ['a', 'c']);
  assert.equal(f.viewport.scrollTop, 75); assert.equal(f.model.restoreFor('formats', ['b']).kind, 'nothing'); f.dom.window.close();
});
test('empty/loading intermediate lists cannot erase an offset or consume a pending return', () => {
  const f = fixture(); f.render('formats', ['a', 'b']); f.viewport.scrollTop = 90; f.view.remember('b');
  f.render('author', ['song']); f.render('formats', [], true); f.viewport.scrollTop = 0;
  f.render('formats', []); f.render('formats', ['a', 'c', 'b']);
  assert.equal(f.viewport.scrollTop, 80); assert.equal(f.model.position('formats'), 90); f.dom.window.close();
});
test('an obsolete completed render cannot restore or overwrite a newer visit', () => {
  const f = fixture(); f.render('old', ['a']); f.viewport.scrollTop = 90;
  const old = f.view.begin('slow'); f.render('new', ['b']); f.viewport.scrollTop = 30;
  f.view.finish(old); assert.equal(f.viewport.scrollTop, 30); assert.equal(f.view.level, 'new'); f.dom.window.close();
});
test('same-level refresh keeps the captured scroll offset after DOM replacement clamps it', () => {
  const f = fixture(); f.render('level', ['a', 'b']); f.viewport.scrollTop = 90;
  const visit = f.view.begin('level'); f.rows(['b', 'a']); f.viewport.scrollTop = 0; f.view.finish(visit);
  assert.equal(f.viewport.scrollTop, 90); f.dom.window.close();
});
test('hidden Queue updates defer restoration until the owning Browse level is visible', () => {
  const f = fixture(); f.render('level', ['a', 'b']); f.viewport.scrollTop = 90; f.visible(false);
  const visit = f.view.begin('level'); f.rows(['a', 'b']); f.viewport.scrollTop = 0; f.view.finish(visit);
  assert.equal(f.viewport.scrollTop, 0); f.visible(true); f.view.reveal('wrong'); assert.equal(f.viewport.scrollTop, 0);
  f.view.reveal('level'); assert.equal(f.viewport.scrollTop, 90); f.dom.window.close();
});
test('fresh entry invalidates old work and resets every offset and pending marker', () => {
  const f = fixture(); f.render('level', ['a']); f.viewport.scrollTop = 90; f.view.remember('a');
  const visit = f.view.begin('slow'); f.view.reset(); f.view.finish(visit);
  assert.equal(f.viewport.scrollTop, 0); assert.equal(f.model.position('level'), 0); assert.equal(f.view.level, null); f.dom.window.close();
});
console.log(`${checks} Browse restoration presentation checks passed (host geometry only)`);
