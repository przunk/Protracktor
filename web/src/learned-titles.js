// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later
import { trimQuery as trimText } from './query-text.js';

/** Native LearnedTitles: display copies only, with the original decoder filename retained. */
export function learnedDisplay(track, titles, fileName = track.file || track.name) {
  const name = trimText(titles.get(track.url) ?? '');
  return name ? Object.freeze({ ...track, name, file: fileName }) : track;
}
export function learnedSourceLine(track) {
  const file = track.file || track.name;
  if (track.name === file) return track.meta ?? '';
  const path = (track.meta ?? '').replace(/\/+$/, '');
  if (!trimText(path)) return file;
  return path === file || path.endsWith(`/${file}`) ? path : `${path}/${file}`;
}
