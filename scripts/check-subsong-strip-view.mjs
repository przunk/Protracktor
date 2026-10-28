// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later
import assert from 'node:assert/strict';
import { JSDOM } from '../web/node_modules/jsdom/lib/api.js';
import { SubsongStripView } from '../web/src/subsong-strip-view.js';
let checks = 0;
const test = (name, run) => {
  try { run(); } catch (error) { console.error(`✗ ${name}`); throw error; }
  checks++; console.log(`✓ ${name}`);
};
function fixture() {
  const dom = new JSDOM('<div id="strip"></div>'), strip = dom.window.document.getElementById('strip');
  const callbacks = new Map(), cancelled = [], moves = [], selected = [];
  let next = 0, visible = true, left = 0;
  Object.defineProperty(strip, 'scrollLeft', { get: () => left, set: (value) => { left = value; moves.push(value); } });
  strip.getBoundingClientRect = () => ({ left: 0, right: 330, width: 330 });
  const view = new SubsongStripView({ strip, visible: () => visible, onSelect: (index) => selected.push(index),
    requestFrame: (fn) => { callbacks.set(++next, fn); return next; }, cancelFrame: (id) => cancelled.push(id) });
  const geometry = () => {
    [...strip.children].forEach((node, index) => { node.getBoundingClientRect = () => ({ left: index * 42 - left, right: index * 42 - left + 36 }); });
  };
  const render = (data) => { view.render({ key: 'first', count: 256, current: 68, ...data }); geometry(); return next; };
  return { dom, strip, view, moves, selected, cancelled, render, flush: (id = next) => callbacks.get(id)?.(), visible: (value) => { visible = value; } };
}
test('native initial current and visible advance retain nodes focus and offset', () => {
  const f = fixture(); f.render(); f.flush(); assert.equal(f.strip.scrollLeft, 68 * 42);
  const chip = f.strip.children[69]; chip.focus(); const moves = f.moves.length;
  f.render({ current: 69 }); f.flush();
  assert.equal(f.strip.children[69], chip); assert.equal(f.dom.window.document.activeElement, chip);
  assert.equal(chip.getAttribute('aria-pressed'), 'true'); assert.equal(f.moves.length, moves); f.dom.window.close();
});
test('native distant browsing remains while file and count changes reset opening', () => {
  const f = fixture(); f.render(); f.flush(); f.strip.scrollLeft = 248 * 42;
  f.render({ current: 69 }); f.flush(); assert.equal(f.strip.scrollLeft, 248 * 42);
  f.render({ key: 'second', current: 5 }); f.flush(); assert.equal(f.strip.scrollLeft, 5 * 42);
  f.render({ key: 'second', count: 5, current: 0 }); f.flush(); assert.equal(f.strip.scrollLeft, 0); f.dom.window.close();
});
test('a partly visible previous chip counts as watching while a nonvisible current is followed', () => {
  const f = fixture(); f.render(); f.flush(); f.strip.scrollLeft = 68 * 42 + 20;
  f.render({ current: 81 }); f.flush(); assert.equal(f.strip.scrollLeft, 81 * 42); f.dom.window.close();
});
test('obsolete deferred work cannot scroll a newer strip before its own frame', () => {
  const f = fixture(); const old = f.render(); f.render({ key: 'second', current: 5 });
  f.strip.scrollLeft = 999; const moves = f.moves.length;
  f.flush(old); assert.equal(f.strip.scrollLeft, 999); assert.equal(f.moves.length, moves);
  f.flush(); assert.equal(f.strip.scrollLeft, 5 * 42); assert.ok(f.cancelled.includes(old)); f.dom.window.close();
});
test('hidden updates defer first reveal and closing invalidates a previously scheduled frame', () => {
  const f = fixture(); f.visible(false); f.render(); f.flush(); assert.equal(f.strip.scrollLeft, 0);
  f.visible(true); f.view.show(); f.flush(); assert.equal(f.strip.scrollLeft, 68 * 42);
  const old = f.render({ current: 90 }); f.view.hide(); f.visible(false); f.flush(old);
  assert.equal(f.strip.scrollLeft, 68 * 42);
  f.visible(true); f.view.show(); f.flush(); assert.equal(f.strip.scrollLeft, 90 * 42); f.dom.window.close();
});
test('actual chip actions ignore current hidden and retired owners without speculative selection', () => {
  const f = fixture(); f.render(); f.flush(); const old = f.strip.children[69];
  f.strip.children[68].click(); assert.deepEqual(f.selected, []);
  old.click(); assert.deepEqual(f.selected, [69]); assert.equal(old.getAttribute('aria-pressed'), 'false');
  f.visible(false); old.click(); assert.deepEqual(f.selected, [69]);
  f.visible(true); f.render({ key: 'second' }); old.click(); assert.deepEqual(f.selected, [69]);
  f.render({ current: -1 }); assert.equal(f.strip.children.length, 0);
  f.render({ count: 1, current: 0 }); assert.equal(f.strip.children.length, 0); f.dom.window.close();
});
console.log(`${checks} subsong presentation cases passed (host DOM/geometry only)`);
