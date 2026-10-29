// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later

/** Stable StereoMix identifiers and widths, shared with the native parity cases. */
export const STEREO_WIDTHS = Object.freeze({ original: 100, narrow: 50, mono: 0 });
export function stereoMode(value) { return Object.hasOwn(STEREO_WIDTHS, value) ? value : 'original'; }
export function stereoWidth(value) { return STEREO_WIDTHS[stereoMode(value)]; }
const KEY = 'protracktor.stereoMix';

export class StereoMixPreferences {
  constructor(storage) { this.storage = storage; }
  read() {
    try { return { value: stereoMode(this.storage().getItem(KEY)), failure: null }; }
    catch (failure) { return { value: 'original', failure }; }
  }
  write(value) {
    if (!Object.hasOwn(STEREO_WIDTHS, value)) throw new TypeError(`Invalid stereo mix: ${value}`);
    try { this.storage().setItem(KEY, value); return null; }
    catch (failure) { return failure; }
  }
}
