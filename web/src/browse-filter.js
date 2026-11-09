// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later
//
// Browse's Search, as the phone has it (2026-11-09): the list on screen narrowed to what matches,
// by the playlist's rule -- an archive's formats, a format's authors, an author's tunes. One query
// per level while Browse is open, so narrowing the authors, opening one and coming back finds them
// still narrowed; leaving Browse forgets them all. Browse → Search is still where a whole
// catalogue is searched.
import { searchMatches } from './rules.js';
import { playlistMatches } from './playlist-search.js';

/** A Browse path with a list to narrow: an archive, a format or an author, not a destination. */
export function filterablePath(path) {
  return path.length > 0 && !['catalogues', 'search', 'history', 'saved'].includes(path[0]);
}

export class BrowseFilters {
  #queries = new Map();
  /** The query at `level`, or null while Search is closed there. */
  queryAt(level) { return this.#queries.has(level) ? this.#queries.get(level) : null; }
  open(level) { if (!this.#queries.has(level)) this.#queries.set(level, ''); }
  change(level, query) { this.#queries.set(level, query); }
  close(level) { this.#queries.delete(level); }
  clear() { this.#queries.clear(); }
}

/** A tune by what it shows (a learned title included), as a playlist row; any other row by its name. */
export function browseRowMatches(node, query) {
  const row = node._selectionRow;
  if (row) return playlistMatches(row.shown(), query);
  return searchMatches(query, node.querySelector('.bname')?.textContent ?? '');
}
