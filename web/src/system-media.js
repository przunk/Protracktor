// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later

const ACTIONS = ['play', 'pause', 'stop', 'nexttrack', 'previoustrack', 'seekto', 'seekbackward', 'seekforward'];
const clamp = (value, limit) => Math.min(limit, Math.max(0, value));
const finite = (value) => typeof value === 'number' && Number.isFinite(value);

/** Browser presentation is optional; none of its failures may interrupt the actual transport. */
export class SystemMedia {
  constructor({ navigator, Metadata, read, commands, onError = () => {} }) {
    this.read = read; this.commands = commands; this.Metadata = Metadata; this.onError = onError;
    this.registered = new Map(); this.unsupported = new Set(); this.reported = new Set();
    try { this.session = navigator?.mediaSession ?? null; }
    catch (error) { this.failed('session', error); this.session = null; }
  }

  failed(operation, error) {
    if (this.reported.has(operation)) return;
    this.reported.add(operation);
    try { this.onError(operation, error); } catch { /* Diagnostics are optional too. */ }
  }

  allowed(action, state) {
    switch (action) {
      case 'play': return !!state.canPlay;
      case 'pause': case 'stop': return !!state.canPause;
      case 'nexttrack': return !!state.canNext;
      case 'previoustrack': return !!state.canPrevious;
      case 'seekto': case 'seekbackward': case 'seekforward':
        return !!state.canSeek && finite(state.seekLimit) && state.seekLimit > 0;
      default: return false;
    }
  }

  invoke(action, details = {}) {
    const state = this.read();
    if (!this.allowed(action, state)) return;
    try {
      let result;
      switch (action) {
        case 'play': result = this.commands.play(); break;
        case 'pause': result = this.commands.pause(); break;
        case 'stop': result = this.commands.stop(); break;
        case 'nexttrack': result = this.commands.next(); break;
        case 'previoustrack': result = this.commands.previous(); break;
        default: {
          let target = details.seekTime;
          if (action !== 'seekto') {
            const offset = details.seekOffset === undefined ? 10 : details.seekOffset;
            if (!finite(offset) || offset < 0 || !finite(state.position)) return;
            target = state.position + (action === 'seekbackward' ? -offset : offset);
          }
          if (!finite(target)) return;
          result = this.commands.seek(clamp(target, state.seekLimit));
        }
      }
      return Promise.resolve(result).catch((error) => this.failed(`command:${action}`, error));
    } catch (error) { this.failed(`command:${action}`, error); }
  }

  publish() {
    const session = this.session;
    if (!session) return;
    const state = this.read();
    // Register independently: an unsupported seek or rejected artwork must not disable Play.
    for (const action of ACTIONS) {
      const enabled = this.allowed(action, state);
      if (this.unsupported.has(action) || this.registered.get(action) === enabled) continue;
      try {
        session.setActionHandler(action, enabled ? (details) => this.invoke(action, details) : null);
        this.registered.set(action, enabled);
      } catch (error) { this.unsupported.add(action); this.failed(`action:${action}`, error); }
    }
    const metadata = state.active ? state.metadata : null;
    const metadataKey = JSON.stringify(metadata);
    if (this.metadataKey !== metadataKey) {
      try {
        session.metadata = metadata && this.Metadata ? new this.Metadata(metadata) : null;
        this.metadataKey = metadataKey;
      } catch (error) {
        this.failed('metadata', error);
        this.metadataKey = undefined;
        try { session.metadata = null; } catch { /* The platform can refuse clearing too. */ }
      }
    }
    const playbackState = state.active ? state.playing ? 'playing' : 'paused' : 'none';
    if (this.playbackState !== playbackState) {
      try {
        session.playbackState = playbackState; this.playbackState = playbackState;
        this.positionKey = null;
      }
      catch (error) { this.failed('playbackState', error); }
    }
    let setPosition;
    try { setPosition = session.setPositionState; }
    catch (error) { this.failed('position', error); return; }
    if (typeof setPosition !== 'function') return;
    const hasPosition = state.active && finite(state.duration) && state.duration > 0 && finite(state.position);
    const position = hasPosition
      ? { duration: state.duration, playbackRate: 1, position: clamp(state.position, state.duration) } : {};
    // The UA extrapolates from this timestamp; resume/restart must rebase even at the same position.
    const positionKey = JSON.stringify([state.positionRevision ?? null, playbackState, position]);
    if (this.positionKey === positionKey) return;
    try { setPosition.call(session, position); this.positionKey = positionKey; }
    catch (error) { this.failed('position', error); }
  }
}
