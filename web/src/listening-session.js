// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later

/** Native ListeningSession: progressing time, or a verified natural end, never seek/wait time. */
export class ListeningSession {
  #candidate = null;
  #elapsed = 0;
  #lastTime = 0;
  #lastPosition = 0;
  #running = false;
  #completionEligible = true;
  constructor(nowMs) { this.nowMs = nowMs; }
  begin(trackId, historyGeneration, position, playing) {
    this.#candidate = Object.freeze({ trackId, historyGeneration });
    this.#elapsed = 0;
    this.#completionEligible = true;
    this.baseline(position, playing);
  }
  seeking(position) {
    this.#completionEligible = false;
    this.baseline(position, false);
  }
  baseline(position, playing) {
    this.#lastTime = this.nowMs();
    this.#lastPosition = position;
    this.#running = playing;
  }
  sample(position, playing, { completed = false } = {}) {
    const now = this.nowMs();
    if (this.#running) {
      const wall = Math.max(0, now - this.#lastTime) / 1000;
      const audio = Math.max(0, position - this.#lastPosition);
      this.#elapsed += Math.min(wall, audio);
    }
    this.#lastTime = now;
    this.#lastPosition = position;
    this.#running = playing;
    const finished = completed && this.#completionEligible && this.#elapsed > 0;
    if (this.#elapsed + 1e-9 >= 5 || finished) {
      const qualified = this.#candidate;
      this.#candidate = null;
      return qualified;
    }
    return null;
  }
  forget() {
    this.#candidate = null;
    this.#running = false;
    this.#elapsed = 0;
  }
}
