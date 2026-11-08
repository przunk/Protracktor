// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later

/**
 * What sharing several tunes sends (B59, 2026-11-09) -- the phone's `SelectionShare`, rule for
 * rule: the links one per line in selection order, a row with no address counted, and names unique
 * within one batch so two `intro.mod` do not overwrite each other.
 */
export function selectionLinks(entries) {
  const lines = entries.map((entry) => entry.url).filter(Boolean);
  return { text: lines.join('\n'), shared: lines.length, without: entries.length - lines.length };
}

export function uniqueNames(names) {
  const taken = new Set();
  return names.map((wanted) => {
    const name = wanted || 'tune';
    if (!taken.has(name.toLowerCase())) { taken.add(name.toLowerCase()); return name; }
    const dot = name.lastIndexOf('.') > 0 ? name.lastIndexOf('.') : name.length;
    for (let n = 2; ; n++) {
      const candidate = `${name.slice(0, dot)} (${n})${name.slice(dot)}`;
      if (!taken.has(candidate.toLowerCase())) { taken.add(candidate.toLowerCase()); return candidate; }
    }
  });
}
