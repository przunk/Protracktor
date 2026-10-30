// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later
import assert from 'node:assert/strict';
import { JSDOM } from '../web/node_modules/jsdom/lib/api.js';
import { bindRandomDice } from '../web/src/random-dice.js';
const dom = new JSDOM('<button><svg></svg>New list</button>');
const { window } = dom, button = window.document.querySelector('button');
let rolls = 0, reduced = false, enabled = true, animations = 0, cancelled = 0;
window.matchMedia = () => ({ matches: reduced });
button.querySelector('svg').animate = (keyframes, options) => {
  assert.deepEqual(keyframes, [{ transform: 'rotate(0deg)' }, { transform: 'rotate(360deg)' }]);
  assert.equal(options.duration, 500); animations++;
  return { cancel: () => cancelled++, finished: Promise.resolve() };
};
const dispose = bindRandomDice(button, { onReroll: () => rolls++, canReroll: () => enabled });
let checks = 0;
const test = (name, run) => { try { run(); } catch (error) { console.error(`✗ ${name}`); throw error; } checks++; console.log(`✓ ${name}`); };
const pointer = (type, time, { id = 1, x = 0, y = 0, kind = 'touch', primary = true, target = button } = {}) => {
  const event = new window.MouseEvent(type, { bubbles: true, cancelable: true, clientX: x, clientY: y });
  Object.defineProperties(event, { pointerId: { value: id }, pointerType: { value: kind }, isPrimary: { value: primary }, timeStamp: { value: time } });
  target.dispatchEvent(event); return event;
};
const touch = (time) => { pointer('pointerdown', time); pointer('pointerup', time + 30); };
const click = (detail = 1, time = 0, type = 'click') => {
  const event = new window.MouseEvent(type, { bubbles: true, cancelable: true, detail });
  Object.defineProperty(event, 'timeStamp', { value: time }); button.dispatchEvent(event);
};
test('single pointer click never rerolls the record', () => { click(); touch(100); click(1, 130); assert.equal(rolls, 0); });
test('double touch rerolls once and ignores the compatibility double-click', () => {
  touch(250); assert.equal(rolls, 1); click(1, 280); click(2, 280, 'dblclick'); assert.equal(rolls, 1); assert.equal(animations, 1);
});
test('a real mouse double-click rerolls through the same action', () => {
  pointer('pointerdown', 300, { kind: 'mouse' }); click(1, 300); click(2, 400); click(2, 400, 'dblclick');
  assert.equal(rolls, 2); assert.equal(cancelled, 1);
});
test('keyboard and accessibility single activation perform the equivalent action', () => {
  button.dispatchEvent(new window.KeyboardEvent('keydown', { key: 'Enter' })); click(0); button.click(); assert.equal(rolls, 4);
});
test('dragged, held and cancelled touches cannot become a reroll', () => {
  pointer('pointerdown', 1000); pointer('pointermove', 1010, { y: 8 }); pointer('pointerup', 1020); touch(1100);
  assert.equal(rolls, 4);
  pointer('pointerdown', 2000); pointer('pointerup', 2600); touch(2700); assert.equal(rolls, 4);
  pointer('pointerdown', 3000); pointer('pointercancel', 3010); pointer('pointerup', 3020); touch(3100); assert.equal(rolls, 4);
});
test('too slow or distant taps do not form a double-tap', () => {
  touch(4000); touch(4400); assert.equal(rolls, 4);
  pointer('pointerdown', 4550, { x: 150 }); pointer('pointerup', 4580, { x: 150 }); assert.equal(rolls, 4);
});
test('an additional pointer anywhere cancels the pending gesture', () => {
  touch(5000); pointer('pointerdown', 5100); pointer('pointerdown', 5110, { id: 2, primary: false, target: window.document.body });
  pointer('pointerup', 5120); assert.equal(rolls, 4);
});
test('reduced motion rerolls without starting another rotation', () => { reduced = true; const before = animations; button.click(); assert.equal(rolls, 5); assert.equal(animations, before); });
test('hidden-context guard and disposal disable all gesture paths', () => {
  enabled = false; button.click(); click(2, 7000, 'dblclick'); assert.equal(rolls, 5);
  enabled = true; dispose(); button.click(); click(2, 8000, 'dblclick'); assert.equal(rolls, 5);
});
window.close(); console.log(`${checks} Random dice cases passed.`);
