// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later
import { PlayOrder } from './playback-context.js';

export function moveItems(items, from, to) {
  if (from === to || !Number.isInteger(from) || !Number.isInteger(to)
    || from < 0 || to < 0 || from >= items.length || to >= items.length) return items;
  const next = [...items]; next.splice(to, 0, next.splice(from, 1)[0]);
  return Object.freeze(next);
}

/** Native draft move, with a positional permutation preserving legacy duplicate occurrences. */
export function moveEditorTrack(workspace, from, to) {
  const before = workspace.editor.order;
  const positions = before.tracks.map((_, index) => index), moved = moveItems(positions, from, to);
  if (moved === positions) return workspace;
  const inverse = new Map(moved.map((old, index) => [old, index]));
  const history = [];
  let cursor = -1;
  before.history.forEach((old, position) => {
    if (!inverse.has(old)) return;
    history.push(inverse.get(old));
    if (position <= before.cursor) cursor = history.length - 1;
  });
  const order = new PlayOrder({ tracks: moved.map((old) => before.tracks[old]),
    repeat: before.repeat, shuffle: before.shuffle, history, cursor,
    shuffleFrom: history[cursor] ?? null, random: before.random });
  const owns = workspace.context.belongsTo(workspace.editor.id);
  const context = owns ? workspace.context.copy({ order,
    entryIds: moved.map((old) => workspace.context.entryIds[old]) }) : workspace.context;
  return workspace.copy({ editor: { ...workspace.editor, order, dirty: true,
    revision: workspace.editor.revision + 1 }, context });
}
