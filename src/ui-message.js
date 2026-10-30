// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later

const RENDER = Symbol.for('protracktor.ui-message');

/** An explicitly owned UI value; ordinary strings are never reverse-translated. */
export function uiMessage(render) {
  if (typeof render !== 'function') throw new TypeError('UI message requires a renderer');
  return Object.freeze({ [RENDER]: render, toString() { return render(); } });
}

export function isUiMessage(value) {
  return value !== null && typeof value === 'object' && typeof value[RENDER] === 'function';
}

export function renderUiValue(value) {
  if (isUiMessage(value)) return String(value[RENDER]());
  return String(value ?? '');
}

/** Snapshot composition and conditional choices without freezing their presentation language. */
export function joinUiText(...values) {
  const parts = Object.freeze(values.map((value) => isUiMessage(value) ? value : String(value)));
  return uiMessage(() => parts.map(renderUiValue).join(''));
}
