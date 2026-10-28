// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later
import assert from 'node:assert/strict';
import { JSDOM } from '../web/node_modules/jsdom/lib/api.js';
import { PendingReorder } from '../web/src/queue-reorder.js';
import { moveItems } from '../web/src/list-reorder.js';

let checks = 0;
function test(name, run) {
  const dom = new JSDOM('<ol><li>Source</li><li><button>1</button></li><li><button>2</button></li><li><button>3</button></li><li>Source</li></ol>', { pretendToBeVisual: true });
  const { window } = dom, list = window.document.querySelector('ol');
  const original = [...list.children].slice(1, 4).map((node, i) => ({ id: i + 1, node }));
  let entries = [...original], generation = 1, frames = new Map(), nextFrame = 1;
  const moves = [], stops = [];
  window.requestAnimationFrame = (fn) => { const id = nextFrame++; frames.set(id, fn); return id; };
  window.cancelAnimationFrame = (id) => frames.delete(id);
  const frame = (time) => { const ready = [...frames]; frames.clear(); for (const [, fn] of ready) fn(time); };
  list.getBoundingClientRect = () => ({ top: 0, bottom: 240, height: 240 });
  Object.defineProperty(list, 'scrollHeight', { value: 900 });
  Object.defineProperty(list, 'clientHeight', { value: 240 });
  for (const entry of original) entry.node.getBoundingClientRect = () => ({ top: 80 + [...list.children].indexOf(entry.node) * 60 - 60 - list.scrollTop
    + (Number(/translateY\(([-\d.]+)px\)/.exec(entry.node.style.transform)?.[1]) || 0), height: 60 });
  const reorder = new PendingReorder({ list, rows: () => entries, generation: () => generation,
    move: (id, target) => { moves.push([id, target]); entries = moveItems(entries,
      entries.findIndex((row) => row.id === id), entries.findIndex((row) => row.id === target)); reorder.validate(); },
    onStop: (state) => { stops.push(state); for (const row of entries) list.insertBefore(row.node, list.lastElementChild); } });
  const disposers = original.map(({ node, id }) => reorder.bind(node.querySelector('button'), node, id));
  const event = (target, type, y, x = 0, pointer = 1) => {
    const e = new window.MouseEvent(type, { clientY: y, clientX: x, bubbles: true, cancelable: true, button: 0 });
    Object.defineProperty(e, 'pointerId', { value: pointer }); target.dispatchEvent(e); return e;
  };
  const grip = (id) => original[id - 1].node.querySelector('button');
  const start = (id, y) => event(grip(id), 'pointerdown', y);
  const move = (y) => event(window.document, 'pointermove', y);
  const end = (y) => event(window.document, 'pointerup', y);
  try { run({ window, list, original, moves, stops, reorder, grip, start, move, end, frame, frames, event,
    ids: () => entries.map((row) => row.id), change: (fn) => { entries = fn(entries); }, generation: () => generation++ }); }
  catch (error) { console.error(`✗ ${name}`); throw error; }
  finally { for (const dispose of disposers) dispose(); dom.window.close(); }
  checks++; console.log(`✓ ${name}`);
}
test('a full row crossing commits immediately before release, with the captured grip still connected', ({ start, move, end, moves, reorder, grip, ids }) => {
  start(1, 110); move(160); assert.equal(moves.length, 0);
  move(172); assert.deepEqual(moves, [[1, 2]]); assert.deepEqual(ids(), [2, 1, 3]);
  assert.ok(reorder.active && grip(1).isConnected); end(172); assert.equal(moves.length, 1);
});
test('residual offset retains native hysteresis instead of jumping back after a partial reverse', ({ start, move, end, moves, ids }) => {
  start(2, 170); move(104); assert.deepEqual(ids(), [2, 1, 3]);
  move(122); assert.deepEqual(ids(), [2, 1, 3]); assert.equal(moves.length, 1); end(122);
});
test('incremental moves survive multiple frames and keep the numeric viewport pinned', ({ start, move, end, frame, grip, list, moves, ids }) => {
  list.getBoundingClientRect = () => ({ top: 0, bottom: 1000, height: 1000 });
  start(1, 110); move(131); frame(0); move(203); frame(16); move(275);
  assert.deepEqual(ids(), [2, 3, 1]); assert.equal(moves.length, 2); assert.equal(list.scrollTop, 0);
  assert.ok(grip(1).isConnected); end(275);
});
test('the first pending request never visually crosses the source/current boundary', ({ start, move, end, original, moves }) => {
  start(1, 110); move(-200); assert.equal(original[0].node.getBoundingClientRect().top, 80); end(-200); assert.equal(moves.length, 0);
});
test('the last pending request never crosses into following source rows', ({ start, move, end, original, moves }) => {
  start(3, 230); move(900); assert.equal(original[2].node.getBoundingClientRect().top, 200); end(900); assert.equal(moves.length, 0);
});
test('membership changes cancel the gesture while retaining already committed moves', ({ start, move, end, change, reorder, moves }) => {
  start(1, 110); move(180); change((rows) => rows.slice(0, 2)); reorder.validate();
  assert.equal(reorder.active, false); end(180); assert.deepEqual(moves, [[1, 2]]);
});
for (const reason of ['generation', 'pointercancel', 'second pointer', 'capture loss']) test(`${reason} cancels without rolling back a live move`, ({ start, move, end, generation, reorder, moves, event, window, grip }) => {
  start(1, 110); move(180);
  if (reason === 'generation') { generation(); reorder.validate(); }
  if (reason === 'pointercancel') event(window.document, 'pointercancel', 180);
  if (reason === 'second pointer') event(window.document, 'pointerdown', 0, 0, 2);
  if (reason === 'capture loss') event(grip(1), 'lostpointercapture', 180);
  end(180); assert.equal(reorder.active, false); assert.deepEqual(moves, [[1, 2]]); assert.equal(reorder.preview.size, 0);
});
test('edge scrolling stays under the pointer and moves only within pending IDs', ({ list, start, move, frame, end, moves, original, frames }) => {
  list.getBoundingClientRect = () => ({ top: 0, bottom: 170, height: 170 });
  start(1, 110); move(165); frame(0); frame(32); frame(64);
  assert.ok(list.scrollTop > 0); assert.equal(Math.round(original[0].node.getBoundingClientRect().top), 135);
  assert.deepEqual(moves, [[1, 2]]); end(165); assert.equal(frames.size, 0);
});
test('a drag consumes its click while a later keyboard action remains available', ({ start, move, end, grip, window }) => {
  let clicks = 0; grip(1).onclick = () => clicks++;
  start(1, 110); move(180); end(180); grip(1).click(); assert.equal(clicks, 0);
  grip(1).dispatchEvent(new window.KeyboardEvent('keydown', { key: 'Enter' })); grip(1).click(); assert.equal(clicks, 1);
});
test('a horizontal grip gesture never queues, reorders or opens the fallback menu', ({ start, end, grip, event, window, moves }) => {
  let clicks = 0; grip(1).onclick = () => clicks++;
  start(1, 110); event(window.document, 'pointermove', 110, 180); end(110); grip(1).click();
  assert.equal(clicks, 0); assert.equal(moves.length, 0);
});
assert.ok(checks > 0); console.log(`${checks} live pending-grip checks passed (host DOM only)`);
