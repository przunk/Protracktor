// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later
/** Android CacheBudget rules; payload publication and I/O belong to the store. */
export const MUSIC_CACHE_CEILING = 512 * 1024 * 1024;
export const completedEntries = (entries) => entries.filter((entry) => !entry.name.endsWith('.part'));
export const totalCacheBytes = (entries) => completedEntries(entries).reduce((total, entry) => total + entry.sizeBytes, 0);
export function cacheEvictions(entries, inUse = new Set(), ceilingBytes = MUSIC_CACHE_CEILING) {
  const counted = completedEntries(entries);
  let total = totalCacheBytes(counted);
  const removed = [];
  const candidates = counted.filter((entry) => !inUse.has(entry.name)).sort((a, b) =>
    a.lastUsedMillis - b.lastUsedMillis || (a.name < b.name ? -1 : a.name > b.name ? 1 : 0));
  for (const entry of candidates) {
    if (total <= ceilingBytes) break;
    removed.push(entry.name); total -= entry.sizeBytes;
  }
  return removed;
}
export function freedCacheBytes(bytes) {
  return bytes <= 0 ? { kind: 'nothing' } : bytes < 1024 * 1024 ? { kind: 'under-megabyte' }
    : { kind: 'megabytes', count: Math.floor(bytes / (1024 * 1024)) };
}
