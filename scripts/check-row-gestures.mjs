// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later
import assert from 'node:assert/strict';
import { JSDOM } from '../web/node_modules/jsdom/lib/api.js';
import { swipeIntent, swipeStartDistance, bindTrackGestures } from '../web/src/row-gestures.js';

let checks = 0;
const test = (name, run) => {
  try { run(); } catch (error) { console.error(`✗ ${name}`); throw error; }
  checks++; console.log(`✓ ${name}`);
};
test('Android swipe minimum respects density and larger platform slop', () => {
  assert.equal(swipeStartDistance(48, 16), 48); assert.equal(swipeStartDistance(48, 40), 80);
});
test('Android meaningful diagonals and ratio ties belong to vertical scrolling', () => {
  for (const x of [-60, -32, 32, 60]) for (const sign of [-1, 1]) assert.equal(swipeIntent(x, sign * 40, 16, 48), 'vertical');
  assert.equal(swipeIntent(48, 24, 16, 48), 'vertical');
});
test('Android horizontal intent crosses both distance and dominance requirements', () => {
  for (const sign of [-1, 1]) {
    assert.equal(swipeIntent(sign * 47, 0, 16, 48), 'undecided');
    assert.equal(swipeIntent(sign * 47, 12, 16, 48), 'undecided');
    assert.equal(swipeIntent(sign * 48, 23, 16, 48), 'horizontal');
  }
  assert.equal(swipeIntent(0, 15, 16, 48), 'undecided');
  assert.equal(swipeIntent(2, -16, 16, 48), 'vertical');
});
const dom = new JSDOM('<ol><li><span>A track</span><button>Menu</button></li></ol>', { pretendToBeVisual: true });
const { window } = dom;
const row = window.document.querySelector('li');
row.getBoundingClientRect = () => ({ width: 300 });
let adds = 0, removes = 0, plays = 0, information = 0, enabled = true, captures = 0;
row.onclick = () => plays++;
const held = new Set();
row.setPointerCapture = (id) => { captures++; held.add(id); };
row.releasePointerCapture = (id) => held.delete(id);
row.hasPointerCapture = (id) => held.has(id);
const dispose = bindTrackGestures(row, { onAdd: () => adds++, onRemove: () => removes++,
  onLongPress: () => information++, canSwipe: () => enabled, icon: () => '<svg></svg>', addLabel: 'Add to queue', removeLabel: 'Remove' });
const pointer = (type, x = 0, y = 0, target = row) => {
  const event = new window.MouseEvent(type, { bubbles: true, cancelable: true, clientX: x, clientY: y, button: 0 });
  Object.defineProperty(event, 'pointerId', { value: 17 });
  target.dispatchEvent(event);
  return event;
};
test('a real right drag commits once and suppresses its follow-up click', () => {
  pointer('pointerdown'); assert.equal(captures, 0);
  pointer('pointermove', 110, 8); assert.equal(captures, 1);
  assert.equal(row.querySelector('.swipe-reveal.add').hidden, false);
  pointer('pointerup', 110, 8); row.click();
  assert.equal(adds, 1); assert.equal(plays, 0); assert.equal(held.size, 0);
});
test('a real left drag exposes removal and commits once', () => {
  pointer('pointerdown'); pointer('pointermove', -110, 5);
  assert.equal(row.querySelector('.swipe-reveal.remove').hidden, false);
  pointer('pointerup', -110, 5); row.click(); assert.equal(removes, 1); assert.equal(plays, 0);
});
test('partial and reversed drags return to rest without a queue action', () => {
  pointer('pointerdown'); pointer('pointermove', 89, 4); pointer('pointerup', 89, 4); row.click();
  pointer('pointerdown'); pointer('pointermove', 120, 4); pointer('pointermove', 10, 4); pointer('pointerup', 10, 4); row.click();
  assert.equal(adds, 1); assert.equal(removes, 1); assert.equal(row.querySelector('.swipe-surface').style.transform, '');
});
test('vertical intent is never prevented or converted to a queue swipe', () => {
  pointer('pointerdown');
  assert.equal(pointer('pointermove', 40, 60).defaultPrevented, false);
  assert.equal(pointer('pointermove', 160, 65).defaultPrevented, false);
  pointer('pointerup', 160, 65); row.click(); assert.equal(adds, 1); assert.equal(plays, 0);
});
test('selection disables the swipe and cancellation cannot commit it', () => {
  enabled = false; pointer('pointerdown'); pointer('pointermove', 120, 5); pointer('pointerup', 120, 5);
  enabled = true; pointer('pointerdown'); pointer('pointermove', 120, 5); pointer('pointercancel');
  pointer('pointerup', 120, 5); assert.equal(adds, 1);
});
test('row action buttons keep their own pointer gesture', () => {
  const button = row.querySelector('button');
  pointer('pointerdown', 0, 0, button); pointer('pointermove', 130, 0, button); pointer('pointerup', 130, 0, button);
  assert.equal(adds, 1);
});
pointer('pointerdown');
await new Promise((resolve) => window.setTimeout(resolve, 520));
pointer('pointerup'); row.click();
test('the production 500ms long press opens Information without playing the row', () => {
  assert.equal(information, 1); assert.equal(plays, 0);
});
test('a keyboard action after a held gesture is never swallowed', () => {
  row.dispatchEvent(new window.KeyboardEvent('keydown', { key: 'Enter', bubbles: true })); row.click(); assert.equal(plays, 1);
});
test('movement beyond touch slop cannot become a tap before horizontal activation', () => {
  pointer('pointerdown'); pointer('pointermove', 12, 0); pointer('pointerup', 12, 0); row.click();
  assert.equal(plays, 1); assert.equal(adds, 1);
});
test('the final release position can cancel a horizontal preview without an extra move event', () => {
  pointer('pointerdown'); pointer('pointermove', 110, 0); pointer('pointerup', 5, 0); row.click();
  assert.equal(adds, 1); assert.equal(plays, 1);
});
test('a second finger outside the pressed row cancels its swipe', () => {
  pointer('pointerdown'); pointer('pointermove', 110, 0);
  const second = new window.MouseEvent('pointerdown', { bubbles: true, cancelable: true });
  Object.defineProperty(second, 'pointerId', { value: 18 });
  Object.defineProperty(second, 'isPrimary', { value: false });
  window.document.querySelector('ol').dispatchEvent(second);
  pointer('pointerup', 110, 0); row.click();
  assert.equal(adds, 1); assert.equal(plays, 1);
});
let nativeMenus = 0;
row.oncontextmenu = () => nativeMenus++;
pointer('pointerdown');
await new Promise((resolve) => window.setTimeout(resolve, 520));
pointer('pointerup');
test('handled long press suppresses the browser context menu without stealing a later button tap', () => {
  const menu = new window.MouseEvent('contextmenu', { bubbles: true, cancelable: true });
  row.dispatchEvent(menu);
  assert.equal(menu.defaultPrevented, true); assert.equal(nativeMenus, 0);
  let tapped = 0; const button = row.querySelector('button');
  button.onclick = (event) => { event.stopPropagation(); tapped++; };
  pointer('pointerdown', 0, 0, button); pointer('pointerup', 0, 0, button); button.click();
  assert.equal(tapped, 1);
});
test('a detached row cannot commit an in-flight swipe', () => {
  pointer('pointerdown'); pointer('pointermove', 110, 4); row.remove();
  pointer('pointerup', 110, 4, window.document); assert.equal(adds, 1);
});
dispose();
dom.window.close();
assert.ok(checks > 0);
console.log(`${checks} swipe-intent/physical-event checks passed (host DOM only)`);
