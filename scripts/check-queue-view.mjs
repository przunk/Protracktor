// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later
import assert from 'node:assert/strict';
import { JSDOM } from '../web/node_modules/jsdom/lib/api.js';
import { PlayOrder, PlaybackContext } from '../web/src/playback-context.js';
import { PlaybackSchedule, PlaybackPlan } from '../web/src/playback-schedule.js';
import { applyPlaybackStep } from '../web/src/playback-steps.js';
import { QueueView } from '../web/src/queue-view.js';

const dom = new JSDOM('<section><header></header><p class="empty"></p><p class="repeat"></p><ol></ol></section>', { pretendToBeVisual: true });
const { window } = dom, root = window.document.querySelector('section'), list = root.querySelector('ol');
let reduced = false, checks = 0;
const animations = [], scrolls = [];
window.matchMedia = () => ({ matches: reduced });
window.HTMLElement.prototype.scrollIntoView = function (options) { scrolls.push({ node: this, options }); };
window.HTMLElement.prototype.getBoundingClientRect = function () {
  const top = this.tagName === 'LI' ? [...list.children].filter((node) => !node._exit).indexOf(this) * 60 : 0;
  return { top, height: 60, width: 300 };
};
Object.defineProperty(window.HTMLElement.prototype, 'offsetTop', { get() { return this.getBoundingClientRect().top; } });
window.HTMLElement.prototype.animate = function (frames, options) {
  let finish;
  const finished = new Promise((resolve) => { finish = resolve; });
  const animation = { finished, cancel: finish, finish };
  animations.push({ node: this, frames, options, animation }); return animation;
};
const tracks = ['a', 'b', 'c'].map((name) => ({ name, url: `${name}.mod` }));
const context = new PlaybackContext({ source: { kind: 'playlist', id: 'view', name: 'View fixture' },
  order: new PlayOrder({ tracks }).startAt(0) });
let plan = new PlaybackPlan(context, new PlaybackSchedule().contextSelected(context).enqueue(tracks[1]).enqueue(tracks[2]));
const view = new QueueView({ root, list, source: root.querySelector('header'), empty: root.querySelector('.empty'),
  repeatNote: root.querySelector('.repeat'), icon: () => '<svg></svg>', sourceLabel: (source) => source.name,
  subtitle: (track) => track.url, onPlaySource() {}, onPlayManual() {}, onAdd() {}, onRemove() {}, onInfo() {}, onMove() {}, onMenu() {} });
const test = (name, run) => { run(); checks++; console.log(`✓ ${name}`); };
test('the initial projection never fades in the whole Queue list', () => {
  view.render(plan); assert.equal(animations.length, 0); assert.equal(list.children.length, 5);
  assert.equal(scrolls.length, 1); assert.equal(scrolls[0].options.block, 'nearest');
});
test('ordinary refresh retains node identities and never follows unchanged playback', () => {
  const nodes = [...list.children]; view.render(plan); assert.deepEqual([...list.children], nodes);
  assert.equal(scrolls.length, 1); assert.equal(animations.length, 0);
});
plan = applyPlaybackStep(plan, plan.schedule.next(plan.context)).plan;
view.render(plan);
const before = plan, current = list.querySelector('[aria-current="true"]');
test('manual projection has one current row and keeps its pending-only grip boundary', () => {
  assert.equal(current.dataset.kind, 'manual'); assert.equal(current.querySelector('.queue-grip'), null);
  assert.equal(list.querySelectorAll('[aria-current="true"]').length, 1);
});
plan = applyPlaybackStep(plan, plan.schedule.next(plan.context)).plan;
view.render(plan);
test('a consumed manual row alone fades out while the remaining rows move without opacity changes', () => {
  const fades = animations.filter((entry) => entry.frames.some((frame) => 'opacity' in frame));
  assert.equal(fades.length, 1); assert.equal(fades[0].node, current); assert.equal(fades[0].options.duration, 160);
  assert.equal(current.getAttribute('aria-hidden'), 'true'); assert.equal(current.inert, true);
  assert.equal(current.querySelector('.text').tabIndex, -1);
  assert.ok(animations.some((entry) => entry.options.duration === 220));
});
view.render(before);
await Promise.resolve(); await Promise.resolve();
test('restoring a departing occurrence cancels its obsolete animation removal', () => {
  assert.equal(view.nodes.get(current.dataset.key), current); assert.ok(current.isConnected);
  assert.equal(current.hasAttribute('aria-hidden'), false); assert.equal(current.inert, false);
});
test('hidden Queue updates do not scroll another view; reopening explicitly reveals the current row', () => {
  root.hidden = true; const count = scrolls.length;
  plan = applyPlaybackStep(before, before.schedule.next(before.context)).plan; view.render(plan);
  assert.equal(scrolls.length, count); root.hidden = false; view.revealCurrent();
  assert.equal(list.scrollTop, list.querySelector('[aria-current="true"]').offsetTop);
});
test('reduced-motion removal is immediate and never starts a new departure animation', () => {
  reduced = true; const count = animations.length;
  const removed = list.querySelector('[aria-current="true"]');
  plan = applyPlaybackStep(plan, plan.schedule.next(plan.context)).plan; view.render(plan);
  assert.equal(removed.isConnected, false); assert.equal(animations.length, count);
});
for (const node of view.nodes.values()) { node._disposeReorder?.(); node._disposeGesture(); }
dom.window.close(); assert.ok(checks > 0);
console.log(`${checks} keyed Queue presentation checks passed (host geometry/animation hooks only)`);
