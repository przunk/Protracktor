// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later

// Kotlin Char.isWhitespace includes separator controls, but not the BOM trimmed by JS trim().
const QUERY_SPACE = '[\\u0009-\\u000d\\u001c-\\u0020\\u00a0\\u1680\\u2000-\\u200a\\u2028\\u2029\\u202f\\u205f\\u3000]';
const LEADING_SPACE = new RegExp(`^${QUERY_SPACE}+`, 'u');
const TRAILING_SPACE = new RegExp(`${QUERY_SPACE}+$`, 'u');
export const trimQueryStart = (value) => value.replace(LEADING_SPACE, '');
export const trimQuery = (value) => trimQueryStart(value).replace(TRAILING_SPACE, '');

// Java ignoreCase uses single-character upper/lower mappings, not expanding SS or ligatures.
function foldCharacter(character) {
  const upper = character.toUpperCase();
  const simple = [...upper].length === 1 ? upper : character;
  return simple === '\u0130' ? 'i' : simple.toLowerCase();
}

export function sameQueryCase(first, second) {
  if (first.length !== second.length) return false;
  const a = [...first], b = [...second];
  return a.length === b.length && a.every((character, i) => character === b[i]
    || foldCharacter(character) === foldCharacter(b[i]));
}

