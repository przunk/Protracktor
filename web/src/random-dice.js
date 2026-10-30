// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later

/** Native RandomDice action: double pointer activation, single keyboard/accessibility activation. */
export function bindRandomDice(button, { onReroll, canReroll = () => true }) {
  const document = button.ownerDocument, view = document.defaultView;
  let touch = null, previous = null, lastPointer = '', lastTouchUp = -Infinity, animation = null;
  const reset = () => {
    touch = previous = null;
    document.removeEventListener('pointermove', move, true);
    document.removeEventListener('pointerup', up, true);
    document.removeEventListener('pointercancel', cancel, true);
    document.removeEventListener('pointerdown', additional, true);
  };
  const roll = () => {
    reset();
    if (!button.isConnected || !canReroll()) return;
    onReroll();
    animation?.cancel();
    const icon = button.querySelector('svg');
    if (!view.matchMedia?.('(prefers-reduced-motion: reduce)')?.matches && icon?.animate) {
      animation = icon.animate([{ transform: 'rotate(0deg)' }, { transform: 'rotate(360deg)' }], { duration: 500 });
      animation.finished.catch(() => {});
    }
  };
  const cancel = (event) => { if (!event || event.pointerId === touch?.id) reset(); };
  const additional = (event) => { if (event.pointerId !== touch?.id) reset(); };
  const move = (event) => {
    if (event.pointerId === touch?.id && Math.max(Math.abs(event.clientX - touch.x), Math.abs(event.clientY - touch.y)) >= 8) reset();
  };
  const up = (event) => {
    if (event.pointerId !== touch?.id) return;
    const current = touch, first = previous;
    move(event);
    if (!touch || event.timeStamp - current.time >= 500) { reset(); return; }
    lastTouchUp = event.timeStamp;
    reset();
    const gap = first ? current.time - first.time : Infinity;
    if (gap >= 40 && gap <= 300 && Math.hypot(current.x - first.x, current.y - first.y) <= 100) roll();
    else previous = { time: event.timeStamp, x: current.x, y: current.y };
  };
  const down = (event) => {
    lastPointer = event.pointerType || 'mouse';
    if (event.pointerType !== 'touch') return;
    if (event.isPrimary === false || touch || !canReroll()) { reset(); return; }
    touch = { id: event.pointerId, x: event.clientX, y: event.clientY, time: event.timeStamp };
    document.addEventListener('pointermove', move, true);
    document.addEventListener('pointerup', up, true);
    document.addEventListener('pointercancel', cancel, true);
    document.addEventListener('pointerdown', additional, true);
  };
  const doubleClick = (event) => {
    if (event.button > 0 || lastPointer === 'touch' && event.timeStamp - lastTouchUp < 600) return;
    event.preventDefault(); roll();
  };
  const click = (event) => {
    // Non-pointer activations have an empty pointerType; old MouseEvent implementations use detail=0.
    // https://www.w3.org/TR/pointerevents3/#the-click-auxclick-and-contextmenu-events
    if (event.detail === 0 && !event.pointerType) roll();
  };
  const keyboard = () => { reset(); lastPointer = ''; };
  const context = (event) => event.preventDefault();
  button.addEventListener('pointerdown', down);
  button.addEventListener('dblclick', doubleClick);
  button.addEventListener('click', click);
  button.addEventListener('keydown', keyboard);
  button.addEventListener('contextmenu', context);
  return () => {
    reset(); animation?.cancel();
    button.removeEventListener('pointerdown', down);
    button.removeEventListener('dblclick', doubleClick);
    button.removeEventListener('click', click);
    button.removeEventListener('keydown', keyboard);
    button.removeEventListener('contextmenu', context);
  };
}
