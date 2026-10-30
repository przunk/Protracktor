// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later

const KEYS = Object.freeze({ shuffle: 'protracktor.shuffle', repeat: 'protracktor.repeat',
  playAllSubsongs: 'protracktor.allsubsongs' });
const REPEATS = ['off', 'all', 'one'];

/** PlaylistSessionState/LibraryStore defaults; future or malformed stored values are harmless. */
export function storedPlaybackPreferences(raw = {}) {
  return Object.freeze({ shuffle: raw.shuffle === '1',
    repeat: REPEATS.includes(raw.repeat) ? raw.repeat : 'off', playAllSubsongs: raw.playAllSubsongs === '1' });
}

/** Read before either workspace order is created; initialization never writes defaults. */
export class PlaybackPreferences {
  constructor(storage) { this.storage = storage; }
  read() {
    const raw = {}; let failure = null;
    try {
      const storage = this.storage();
      for (const [name, key] of Object.entries(KEYS)) {
        try { raw[name] = storage.getItem(key); } catch (error) { failure ??= error; }
      }
    } catch (error) { failure = error; }
    return { value: storedPlaybackPreferences(raw), failure };
  }
  write(name, value) {
    if (!Object.hasOwn(KEYS, name)) throw new TypeError(`Unknown playback preference: ${name}`);
    if (name === 'repeat' ? !REPEATS.includes(value) : typeof value !== 'boolean') {
      throw new TypeError(`Invalid playback preference: ${name}`);
    }
    try {
      this.storage().setItem(KEYS[name], name === 'repeat' ? value : value ? '1' : '0');
      return null;
    } catch (error) { return error; }
  }
}
