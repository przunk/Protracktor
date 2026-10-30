// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later
const KEY = 'protracktor.volumeNormalization';

/** The fixed native policy is opt-in; reading never writes a default or starts audio. */
export class NormalizationPreferences {
  constructor(storage) { this.storage = storage; }
  read() {
    try { return { value: this.storage().getItem(KEY) === '1', failure: null }; }
    catch (failure) { return { value: false, failure }; }
  }
  write(enabled) {
    if (typeof enabled !== 'boolean') throw new TypeError('Normalization must be On or Off');
    try { this.storage().setItem(KEY, enabled ? '1' : '0'); return null; }
    catch (failure) { return failure; }
  }
}
