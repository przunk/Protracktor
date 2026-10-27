// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later

// Android references: PlayQueue, ActiveQueue and PlaylistSessionState.
// No DOM, storage or decoder ownership: callers execute the returned transport steps.

// Transport-only copies retain a pending save's immutable content snapshot.
const frozen = (values) => Object.isFrozen(values) ? values : Object.freeze([...values]);
const trackId = (track) => track.url ?? track.id;
const sameTracks = (a, b) => a.length === b.length && a.every((track, i) => trackId(track) === trackId(b[i]));
const checkedIndex = (tracks, index) => {
  if (!Number.isInteger(index) || index < 0 || index >= tracks.length) throw new RangeError(`No track at ${index}`);
  return index;
};

function dealt(tracks, shuffle, first, random) {
  const indices = tracks.map((_, index) => index);
  if (!shuffle) return indices;
  for (let i = indices.length - 1; i > 0; i--) {
    const j = Math.floor(random() * (i + 1));
    [indices[i], indices[j]] = [indices[j], indices[i]];
  }
  return first == null ? indices : [first, ...indices.filter((index) => index !== first)];
}

/** Immutable finite order with a cursor into actual shuffle history. */
export class PlayOrder {
  constructor({ tracks = [], shuffle = false, repeat = 'off', indices, history = [], cursor = -1,
    shuffleFrom = null, random = Math.random } = {}) {
    if (!['off', 'all', 'one'].includes(repeat)) throw new TypeError(`Unknown repeat mode: ${repeat}`);
    this.tracks = frozen(tracks);
    this.shuffle = shuffle;
    this.repeat = repeat;
    this.indices = frozen(indices ?? dealt(tracks, shuffle, shuffleFrom, random));
    this.history = frozen(history);
    this.cursor = cursor;
    this.shuffleFrom = shuffleFrom;
    this.random = random;
    Object.freeze(this);
  }

  copy(values) { return new PlayOrder({ ...this, ...values }); }
  get currentIndex() { return this.history[this.cursor] ?? null; }
  get current() { return this.tracks[this.currentIndex] ?? null; }
  get maxTraversalSteps() { return this.history.length + this.tracks.length + 1; }
  get hasNext() {
    return this.tracks.length > 0 && (this.currentIndex == null
      || this.shuffle && this.cursor < this.history.length - 1
      || this.indices.indexOf(this.currentIndex) + 1 < this.indices.length || this.repeat === 'all');
  }
  get hasPrevious() { return this.previousOrder() !== null; }

  appended(index) {
    checkedIndex(this.tracks, index);
    return this.copy({ history: [...this.history.slice(0, this.cursor + 1), index], cursor: this.cursor + 1 });
  }

  startAt(index) {
    checkedIndex(this.tracks, index);
    return this.shuffle
      ? this.copy({ history: [index], cursor: 0, shuffleFrom: index,
        indices: dealt(this.tracks, true, index, this.random) })
      : this.appended(index);
  }

  jumpTo(index) { return this.appended(index); }
  withoutPosition() { return this.copy({ history: [], cursor: -1 }); }
  withRepeat(repeat) { return this.copy({ repeat }); }

  withShuffle(shuffle) {
    return shuffle === this.shuffle ? this : this.copy({ shuffle, shuffleFrom: this.currentIndex,
      indices: dealt(this.tracks, shuffle, this.currentIndex, this.random) });
  }

  withTracks(tracks) {
    const byId = new Map(tracks.map((track, index) => [trackId(track), index]));
    const history = [];
    let cursor = -1;
    this.history.forEach((oldIndex, position) => {
      const index = byId.get(trackId(this.tracks[oldIndex] ?? {}));
      if (index == null) return;
      history.push(index);
      if (position <= this.cursor) cursor = history.length - 1;
    });
    const unchanged = sameTracks(this.tracks, tracks);
    const shuffleFrom = unchanged ? this.shuffleFrom : history[cursor] ?? null;
    return this.copy({ tracks, history, cursor, shuffleFrom,
      indices: unchanged ? this.indices : dealt(tracks, this.shuffle, shuffleFrom, this.random) });
  }

  advanced() {
    if (!this.tracks.length) return null;
    if (this.currentIndex == null) return this.copy({ history: [this.indices[0]], cursor: 0 });
    if (this.shuffle && this.cursor < this.history.length - 1) return this.copy({ cursor: this.cursor + 1 });
    const next = this.indices.indexOf(this.currentIndex) + 1;
    if (next < this.indices.length) return this.appended(this.indices[next]);
    if (this.repeat !== 'all') return null;
    const lap = this.shuffle ? this.copy({ shuffleFrom: null,
      indices: dealt(this.tracks, true, null, this.random) }) : this;
    return lap.appended(lap.indices[0]);
  }

  previousOrder() {
    if (this.shuffle) return this.cursor > 0 ? this.copy({ cursor: this.cursor - 1 }) : null;
    if (this.currentIndex == null) return null;
    const at = this.indices.indexOf(this.currentIndex);
    if (at > 0) return this.appended(this.indices[at - 1]);
    return this.repeat === 'all' && this.indices.length ? this.appended(this.indices.at(-1)) : null;
  }

  next() { return this.advanced() ?? this; }
  previous() { return this.previousOrder() ?? this; }
  ended() { return this.repeat === 'one' && this.current ? this : this.advanced(); }
}

const sourceKinds = new Set(['none', 'playlist', 'collection', 'random', 'external']);
const step = (kind, context) => Object.freeze(context ? { kind, context } : { kind });

/** A deliberate playback source; selecting a different editor does not replace it. */
export class PlaybackContext {
  constructor({ source = { kind: 'none' }, order = new PlayOrder(), entryIds,
    nextEntryId = order.tracks.length, omitted = [], route = null } = {}) {
    if (!sourceKinds.has(source.kind)) throw new TypeError(`Unknown playback source: ${source.kind}`);
    this.source = Object.freeze({ ...source });
    this.order = order;
    this.route = route;
    this.entryIds = frozen(entryIds ?? order.tracks.map((_, index) => index));
    this.nextEntryId = nextEntryId;
    this.omitted = frozen(omitted);
    if (this.entryIds.length !== order.tracks.length || new Set(this.entryIds).size !== this.entryIds.length)
      throw new TypeError('Playback occurrence identities must match the track list');
    Object.freeze(this);
  }

  copy(values) { return new PlaybackContext({ ...this, ...values }); }
  get current() { return this.order.current; }
  get currentEntryId() { return this.entryIds[this.order.currentIndex] ?? null; }
  get currentAvailable() { return this.current !== null && !this.omitted.includes(this.currentEntryId); }
  get fromHistory() { return this.source.kind === 'collection' && this.source.origin === 'history'; }
  belongsTo(id) { return this.source.kind === 'playlist' && this.source.id === id; }
  get playbackIndices() {
    return this.source.kind === 'random' ? this.order.tracks.map((_, index) => index) : this.order.indices;
  }
  get visibleIndices() { return this.playbackIndices.filter((index) => !this.omitted.includes(this.entryIds[index])); }

  select(index) { return this.copy({ order: this.order.startAt(index) }); }
  jump(index) { return this.copy({ order: this.order.jumpTo(index) }); }
  omit(id) { return !this.entryIds.includes(id) || this.omitted.includes(id) ? this : this.copy({ omitted: [...this.omitted, id] }); }
  restore(id) { return this.copy({ omitted: this.omitted.filter((omitted) => omitted !== id) }); }

  withOrder(order) {
    if (order === this.order) return this;
    if (sameTracks(this.order.tracks, order.tracks)) return this.copy({ order });
    const old = new Map();
    this.order.tracks.forEach((track, index) => {
      const id = trackId(track);
      if (!old.has(id)) old.set(id, []);
      old.get(id).push(this.entryIds[index]);
    });
    let nextEntryId = this.nextEntryId;
    const entryIds = order.tracks.map((track) => old.get(trackId(track))?.shift() ?? nextEntryId++);
    return this.copy({ order, entryIds, nextEntryId, omitted: this.omitted.filter((id) => entryIds.includes(id)) });
  }

  finiteMove(forward) {
    if (!this.visibleIndices.length) return null;
    let order = this.order;
    for (let tried = 0; tried < this.order.maxTraversalSteps; tried++) {
      order = forward ? order.advanced() : order.previousOrder();
      if (!order) return null;
      if (!this.omitted.includes(this.entryIds[order.currentIndex])) return this.copy({ order });
    }
    return null;
  }

  finiteAvailable(forward, accepts = () => true) {
    if (!this.visibleIndices.some(accepts)) return false;
    const order = this.order;
    const available = (index) => !this.omitted.includes(this.entryIds[index]) && accepts(index);
    if (order.currentIndex == null) return forward;
    if (order.shuffle) {
      if (!forward) return order.history.slice(0, order.cursor).some(available);
      if (order.history.slice(order.cursor + 1).some(available)) return true;
    }
    const at = order.indices.indexOf(order.currentIndex);
    return (forward ? order.indices.slice(at + 1) : order.indices.slice(0, at)).some(available)
      || order.repeat === 'all';
  }

  get hasNext() {
    switch (this.source.kind) {
      case 'random': return true;
      case 'none': case 'external': return false;
      case 'playlist': case 'collection': return this.finiteAvailable(true);
    }
  }

  get hasPrevious() {
    switch (this.source.kind) {
      case 'random': return this.order.tracks.some((_, index) => index < this.order.currentIndex && !this.omitted.includes(this.entryIds[index]));
      case 'none': case 'external': return false;
      case 'playlist': case 'collection': return this.finiteAvailable(false);
    }
  }

  next() {
    switch (this.source.kind) {
      case 'random': return step('random-next');
      case 'none': case 'external': return step('unchanged');
      case 'playlist': case 'collection': {
        const next = this.finiteMove(true);
        return next ? step('play', next) : step('unchanged');
      }
    }
  }

  previous() {
    switch (this.source.kind) {
      case 'random': return step(this.hasPrevious ? 'random-previous' : 'unchanged');
      case 'none': case 'external': return step('unchanged');
      case 'playlist': case 'collection': {
        const previous = this.finiteMove(false);
        return previous ? step('play', previous) : step('unchanged');
      }
    }
  }

  ended() {
    if (this.order.repeat === 'one' && this.currentAvailable) return step('replay');
    switch (this.source.kind) {
      case 'random': return step('random-next');
      case 'none': case 'external': return step('stop');
      case 'playlist': case 'collection': {
        const next = this.finiteMove(true);
        return next ? step('play', next) : step('stop');
      }
    }
  }
}

/** Selected playlist state and active transport have separate lifetimes. */
export class PlaybackWorkspace {
  constructor({ editor = { id: null, name: '', order: new PlayOrder(), dirty: false, revision: 0 },
    context = new PlaybackContext() } = {}) {
    this.editor = Object.freeze({ ...editor });
    this.context = context;
    Object.freeze(this);
  }

  copy(values) { return new PlaybackWorkspace({ ...this, ...values }); }

  selectPlaylist({ id, name = '', tracks = [] }, { preservePlayback = false } = {}) {
    const owns = this.context.belongsTo(id);
    const order = owns
      ? preservePlayback ? this.context.order : this.context.order.withTracks(tracks)
      : new PlayOrder({ tracks, repeat: this.context.order.repeat, shuffle: this.context.order.shuffle,
        random: this.context.order.random });
    return this.copy({ editor: { id, name, order, dirty: owns && preservePlayback && !sameTracks(order.tracks, tracks),
      revision: this.editor.revision + 1 }, context: owns && !preservePlayback ? this.context.withOrder(order) : this.context });
  }

  activate(context) {
    return this.copy({ context, editor: context.belongsTo(this.editor.id)
      ? { ...this.editor, order: context.order } : this.editor });
  }

  playPlaylist(index) {
    const order = this.editor.order.startAt(index);
    return this.activate(new PlaybackContext({ source: { kind: 'playlist', id: this.editor.id, name: this.editor.name }, order }));
  }

  playCollection(tracks, index, source) {
    const order = new PlayOrder({ tracks, repeat: this.context.order.repeat, shuffle: this.context.order.shuffle,
      random: this.context.order.random }).startAt(index);
    return this.activate(new PlaybackContext({ source, order }));
  }

  editTracks(tracks, { dirty = true } = {}) {
    const order = this.editor.order.withTracks(tracks);
    return this.copy({ editor: { ...this.editor, order, dirty, revision: this.editor.revision + 1 },
      context: this.context.belongsTo(this.editor.id) ? this.context.withOrder(order) : this.context });
  }

  saveSnapshot() {
    return Object.freeze({ playlistId: this.editor.id, tracks: this.editor.order.tracks, revision: this.editor.revision });
  }

  markSaved(snapshot) {
    return snapshot.playlistId === this.editor.id && snapshot.revision === this.editor.revision && snapshot.tracks === this.editor.order.tracks
      ? this.copy({ editor: { ...this.editor, dirty: false } }) : this;
  }
}
