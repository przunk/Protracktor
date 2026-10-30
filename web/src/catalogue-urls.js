// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later

export const MODLAND_FILE_BASE = 'https://modland.com/pub/modules/';

/** Match native Catalogue.encodePath; URL identities must survive import, cache and recovery. */
export function encodeCataloguePath(path) {
  return path.split('/').map(encodeSegment).join('/');
}

function encodeSegment(segment) {
  let out = '';
  for (const character of segment) {
    out += /[A-Za-z0-9.\-*_]/.test(character)
      ? character
      : [...new TextEncoder().encode(character)]
          .map((byte) => `%${byte.toString(16).toUpperCase().padStart(2, '0')}`).join('');
  }
  return out;
}

/** Request-only recovery for one moved author directory; invoke only after the original 404. */
export function legacyUnknownAuthorUrl(url) {
  if (typeof url !== 'string' || !url.startsWith(MODLAND_FILE_BASE) || url.includes('?') || url.includes('#')) return null;
  let parts;
  try { parts = url.slice(MODLAND_FILE_BASE.length).split('/').map(decodeURIComponent); } catch { return null; }
  if (parts.some((part) => part.includes('/'))) return null;
  let author = -1;
  for (let i = 1; i < parts.length - 1; i++) if (parts[i] === '- unknown') {
    if (author !== -1) return null;
    author = i;
  }
  if (author === -1) return null;
  parts[author] = '_unknown';
  return MODLAND_FILE_BASE + encodeCataloguePath(parts.join('/'));
}
