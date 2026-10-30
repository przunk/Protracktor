// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later
import { makeUiText } from './ui-text.js';

export const swipeStartDistance = (minimum, slop) => Math.max(minimum, slop * 2);
export function swipeIntent(x, y, slop, start) {
  if (Math.abs(y) >= slop && Math.abs(x) <= Math.abs(y) * 2) return 'vertical';
  if (Math.abs(x) >= start && Math.abs(x) > Math.abs(y) * 2) return 'horizontal';
  return 'undecided';
}

/** Native QueueSwipeIntent thresholds in logical pixels; CSS pan-y preserves browser scrolling. */
export function bindTrackGestures(row, { onAdd, onRemove, onLongPress, canSwipe = () => true,
  canLongPress = () => true, canAdd = () => true, canRemove = () => true, icon, addLabel, removeLabel, slop = 8 } = {}) {
  const document = row.ownerDocument;
  const view = document.defaultView;
  let surface = row.querySelector(':scope > .swipe-surface');
  if (!surface) {
    surface = document.createElement('div');
    surface.className = 'swipe-surface';
    surface.append(...row.childNodes);
    row.append(surface);
  }
  row.classList.add('swipe-row');
  const reveal = (direction, label, glyph) => {
    if (!label) return null;
    const element = document.createElement('div');
    element.className = `swipe-reveal ${direction}`;
    element.setAttribute('aria-hidden', 'true');
    element.innerHTML = icon(glyph);
    element.append(makeUiText(document, label));
    row.prepend(element);
    return element;
  };
  const add = onAdd ? reveal('add', addLabel, 'add') : null;
  const remove = onRemove ? reveal('remove', removeLabel, 'remove') : null;
  let pressed = null, timer = null, swallowClick = false, swallowContextMenu = false;
  const reset = () => {
    surface.style.transform = '';
    row.classList.remove('swiping');
    if (add) add.hidden = true;
    if (remove) remove.hidden = true;
  };
  const clearTimer = () => { view.clearTimeout(timer); timer = null; };
  const release = () => {
    const before = pressed;
    pressed = null;
    clearTimer();
    document.removeEventListener('pointermove', move, true);
    document.removeEventListener('pointerup', up, true);
    document.removeEventListener('pointercancel', cancel, true);
    document.removeEventListener('pointerdown', additionalPointer, true);
    if (before && row.hasPointerCapture?.(before.id)) row.releasePointerCapture(before.id);
    reset();
    return before;
  };
  const cancel = (event) => { if (!event || event.pointerId === pressed?.id) release(); };
  const additionalPointer = (event) => {
    if (pressed && event.pointerId !== pressed.id) { swallowClick = true; release(); }
  };
  const longPress = () => {
    if (!pressed || pressed.long || pressed.moved || !row.isConnected || !onLongPress || !canLongPress()) return;
    clearTimer();
    pressed.long = true;
    swallowClick = swallowContextMenu = true;
    onLongPress();
  };
  const move = (event) => {
    if (!pressed || event.pointerId !== pressed.id) return;
    if (!row.isConnected || !canSwipe()) { release(); return; }
    const x = event.clientX - pressed.x, y = event.clientY - pressed.y;
    if (Math.max(Math.abs(x), Math.abs(y)) >= slop) { clearTimer(); pressed.moved = true; swallowClick = true; }
    if (pressed.long) return;
    if (pressed.intent === 'undecided') pressed.intent = swipeIntent(x, y, slop, swipeStartDistance(24, slop));
    if (pressed.intent === 'vertical') { swallowClick = true; return; }
    if (pressed.intent !== 'horizontal') return;
    swallowClick = true;
    event.preventDefault();
    if (!row.hasPointerCapture?.(event.pointerId)) {
      try { row.setPointerCapture?.(event.pointerId); } catch { /* document listeners also cover mouse fallback */ }
    }
    pressed.dx = onRemove && canRemove() && x < 0 || onAdd && canAdd() && x > 0 ? x : 0;
    surface.style.transform = `translateX(${pressed.dx}px)`;
    row.classList.add('swiping');
    if (add) add.hidden = pressed.dx <= 0;
    if (remove) remove.hidden = pressed.dx >= 0;
  };
  const up = (event) => {
    if (!pressed || event.pointerId !== pressed.id) return;
    if (Number.isFinite(event.clientX) && Number.isFinite(event.clientY)) move(event);
    if (!pressed) return;
    const action = pressed;
    const connected = row.isConnected;
    const threshold = Math.max(56, row.getBoundingClientRect().width * 0.30);
    release();
    if (action.intent !== 'horizontal' || !connected || !canSwipe() || Math.abs(action.dx) < threshold) return;
    event.preventDefault();
    if (action.dx > 0 && canAdd()) onAdd?.(); else if (action.dx < 0 && canRemove()) onRemove?.();
  };
  const down = (event) => {
    if (event.isPrimary === false) { swallowClick = true; release(); return; }
    if (pressed) return;
    swallowClick = swallowContextMenu = false;
    if (event.button > 0 || event.target.closest('button,a,input,select,textarea,summary,[data-no-gesture]')) return;
    pressed = { id: event.pointerId, type: event.pointerType, x: event.clientX, y: event.clientY,
      dx: 0, intent: 'undecided', long: false, moved: false };
    document.addEventListener('pointermove', move, { capture: true, passive: false });
    document.addEventListener('pointerup', up, true);
    document.addEventListener('pointercancel', cancel, true);
    document.addEventListener('pointerdown', additionalPointer, true);
    if (onLongPress && canLongPress()) timer = view.setTimeout(longPress, 500);
  };
  // preventDefault(pointerup) cannot suppress click; capture can retarget it after the row moves.
  // https://www.w3.org/TR/pointerevents3/#the-click-auxclick-and-contextmenu-events
  const click = (event) => {
    if (!swallowClick) return;
    swallowClick = false;
    event.preventDefault();
    event.stopImmediatePropagation();
  };
  const contextMenu = (event) => {
    if (pressed?.type === 'touch') longPress();
    if (!swallowContextMenu && !(pressed?.moved && swallowClick)) return;
    event.preventDefault(); event.stopImmediatePropagation();
  };
  const keyboard = () => { swallowClick = swallowContextMenu = false; clearTimer(); };
  row.addEventListener('pointerdown', down);
  row.addEventListener('lostpointercapture', cancel);
  row.addEventListener('click', click, true);
  row.addEventListener('keydown', keyboard, true);
  row.addEventListener('contextmenu', contextMenu, true);
  reset();
  return () => {
    release();
    row.removeEventListener('pointerdown', down);
    row.removeEventListener('lostpointercapture', cancel);
    row.removeEventListener('click', click, true);
    row.removeEventListener('keydown', keyboard, true);
    row.removeEventListener('contextmenu', contextMenu, true);
  };
}
