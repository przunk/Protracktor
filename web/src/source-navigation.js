// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later

export function hasSourceView(source) {
  switch (source.kind) {
    case 'playlist': case 'random': return true;
    case 'collection': return source.origin !== 'link';
    case 'none': case 'external': return false;
    default: throw new TypeError(`Unknown source kind: ${source.kind}`);
  }
}

/** Display names do not identify a playlist, folder route or remembered Random session. */
export function sameSourceView(a, b) {
  if (!a || !b || a.kind !== b.kind) return false;
  switch (a.kind) {
    case 'none': case 'external': return true;
    case 'playlist': return a.id === b.id;
    case 'random': return a.session === b.session;
    case 'collection': return a.origin === b.origin && a.path === b.path && a.query === b.query
      && a.scope === b.scope && a.visit === b.visit;
    default: throw new TypeError(`Unknown source kind: ${a.kind}`);
  }
}

export function sourceDestination(source, { playlistIds = [], randomSession = 0, snapshot = null } = {}) {
  switch (source.kind) {
    case 'none': case 'external': return null;
    case 'playlist': return new Set(playlistIds).has(source.id) ? 'playlist' : null;
    case 'random': return source.session > 0 && source.session === randomSession ? 'random' : null;
    case 'collection': return source.origin !== 'link' && snapshot && sameSourceView(source, snapshot.source) ? 'browse' : null;
    default: throw new TypeError(`Unknown source kind: ${source.kind}`);
  }
}

/** Route fields are the only snapshot data allowed to replace live Browse state. */
export function captureSourceRoute(source, { path = [], query = '', searchScope = null, historyPage = 0,
  historyQuery = '', historyTotal = 0, historyRows = null, returnToSavedPlaces = false, results = [], title = '', note = '' } = {}) {
  return Object.freeze({ source: Object.freeze({ ...source }), path: Object.freeze([...path]), query,
    searchScope, historyPage, historyQuery, historyTotal, returnToSavedPlaces, historyRows: historyRows && Object.freeze([...historyRows]), results: Object.freeze([...results]), title, note });
}

export function restoreSourceRoute(snapshot, current) {
  const { path, query, searchScope, historyPage, historyQuery, historyTotal, historyRows, returnToSavedPlaces, results, title, note } = snapshot;
  return Object.freeze({ ...current, path, query, searchScope, historyPage, historyQuery, historyTotal, historyRows, returnToSavedPlaces, results, title, note,
    navigationVisit: (current.navigationVisit ?? 0) + 1, loading: false, jumpedTo: null, detour: null });
}
