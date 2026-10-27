// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later
import assert from 'node:assert/strict';
import { JSDOM } from '../web/node_modules/jsdom/lib/api.js';
import { PendingReorder } from '../web/src/queue-reorder.js';

const dom = new JSDOM('<ol><li>Source</li><li><button>1</button></li><li><button>2</button></li><li><button>3</button></li><li>Source</li></ol>', { pretendToBeVisual: true });
const { window } = dom, list = window.document.querySelector('ol');
let entries = [...list.children].slice(1, 4).map((node, i) => ({ id: i + 1, node }));
let generation = 1, moves = [], frames = new Map(), nextFrame = 1, checks = 0;
const test = (name, run) => {
  try { run(); } catch (error) { console.error(`✗ ${name}`); throw error; }
  checks++; console.log(`✓ ${name}`);
};
window.requestAnimationFrame = (fn) => { const id = nextFrame++; frames.set(id, fn); return id; };
window.cancelAnimationFrame = (id) => frames.delete(id);
const frame = (time) => { const ready = [...frames]; frames.clear(); for (const [, fn] of ready) fn(time); };
list.getBoundingClientRect = () => ({ top: 0, bottom: 240, height: 240 });
Object.defineProperty(list, 'scrollHeight', { value: 900 });
Object.defineProperty(list, 'clientHeight', { value: 240 });
for (const [i, entry] of entries.entries()) entry.node.getBoundingClientRect = () => ({ top: 80 + i * 60 - list.scrollTop
  + (Number(/translateY\(([-\d.]+)px\)/.exec(entry.node.style.transform)?.[1]) || 0), height: 60 });
const reorder = new PendingReorder({ list, rows: () => entries, generation: () => generation,
  move: (id, target) => moves.push([id, target]) });
const disposers = entries.map(({ node, id }) => reorder.bind(node.querySelector('button'), node, id));
const event = (target, type, y, x = 0) => {
  const e = new window.MouseEvent(type, { clientY: y, clientX: x, bubbles: true, cancelable: true, button: 0 });
  Object.defineProperty(e, 'pointerId', { value: 1 }); target.dispatchEvent(e); return e;
};
const start = (index, y) => event(entries[index].node.querySelector('button'), 'pointerdown', y);
const move = (y) => event(window.document, 'pointermove', y);
const end = (y) => event(window.document, 'pointerup', y);
test('the real grip waits for a row height before changing a pending slot, as Android does', () => {
  start(0, 110); move(160); end(160); assert.equal(moves.length, 0);
  start(0, 110); move(172); end(172); assert.deepEqual(moves.pop(), [1, 2]);
});
test('the first pending request never visually crosses the source/current boundary', () => {
  start(0, 110); move(-200); assert.equal(entries[0].node.getBoundingClientRect().top, 80);
  end(-200); assert.equal(moves.length, 0);
});
test('the last pending request never visually crosses into the following source rows', () => {
  start(2, 230); move(900); assert.equal(entries[2].node.getBoundingClientRect().top, 200);
  end(900); assert.equal(moves.length, 0);
});
test('any pending membership change cancels the active drag', () => {
  start(0, 110); move(180); entries = [...entries, { id: 4, node: entries[2].node }];
  reorder.validate(); assert.equal(reorder.active, false); end(180); assert.equal(moves.length, 0); entries = entries.slice(0, 3);
});
test('generation changes and pointer cancellation never commit a preview', () => {
  start(0, 110); move(180); generation++; reorder.validate(); end(180);
  start(0, 110); move(180); event(window.document, 'pointercancel', 180); end(180);
  assert.equal(moves.length, 0); assert.equal(reorder.preview.size, 0);
});
test('edge scrolling stays under the pointer and commits within pending IDs', () => {
  list.getBoundingClientRect = () => ({ top: 0, bottom: 170, height: 170 });
  start(0, 110); move(165); frame(0); frame(32); frame(64);
  assert.ok(list.scrollTop > 0); assert.equal(Math.round(entries[0].node.getBoundingClientRect().top), 135);
  end(165); assert.deepEqual(moves.pop(), [1, 2]); assert.equal(frames.size, 0);
});
test('a drag consumes its click while a later keyboard action remains available', () => {
  let clicks = 0; const grip = entries[0].node.querySelector('button'); grip.onclick = () => clicks++;
  list.scrollTop = 0; start(0, 110); move(180); end(180); grip.click(); assert.equal(clicks, 0);
  grip.dispatchEvent(new window.KeyboardEvent('keydown', { key: 'Enter' })); grip.click(); assert.equal(clicks, 1);
});
test('a horizontal grip gesture never queues, reorders or opens its fallback menu', () => {
  let clicks = 0; const grip = entries[0].node.querySelector('button'); grip.onclick = () => clicks++;
  start(0, 110); event(window.document, 'pointermove', 110, 180); end(110); grip.click();
  assert.equal(clicks, 0);
});
test('a second pointer anywhere and capture loss cancel the grip without committing', () => {
  moves = []; start(0, 110); move(180);
  const second = new window.MouseEvent('pointerdown', { bubbles: true });
  Object.defineProperty(second, 'pointerId', { value: 2 });
  window.document.dispatchEvent(second); end(180); assert.equal(reorder.active, false); assert.equal(moves.length, 0);
  start(0, 110); move(180);
  event(entries[0].node.querySelector('button'), 'lostpointercapture', 180); end(180);
  assert.equal(reorder.active, false); assert.equal(moves.length, 0);
});
for (const dispose of disposers) dispose();
dom.window.close();
assert.ok(checks > 0); console.log(`${checks} physical pending-grip checks passed (host DOM only)`);
