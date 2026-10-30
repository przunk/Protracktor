// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later
import { settings } from './store.js';
import { loudnessKeyValid, mergedLoudnessRecords, recordProfile } from './loudness-profiles.js';

export const LOUDNESS_CACHE_KEY = 'loudnessProfiles.v1';

/** Derived statistics share the settings store, not the lifetime of History or fetched music. */
export class LoudnessProfiles {
  constructor(store = settings) { this.store = store; }
  async read(key) {
    if (!loudnessKeyValid(key)) return null;
    try {
      const rows = await this.store.get(LOUDNESS_CACHE_KEY);
      return Array.isArray(rows) ? recordProfile(rows.find((row) => row?.key === key)) : null;
    } catch { return null; }
  }
  async write(key, profile) {
    try {
      return await this.store.update(LOUDNESS_CACHE_KEY,
        (previous) => mergedLoudnessRecords(previous, key, profile) ?? undefined);
    } catch { return false; }
  }
}
