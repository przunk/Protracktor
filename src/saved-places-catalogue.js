// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later
import { catalogue } from './store.js';
import * as archive from './catalogue.js';
import { savedPlaceReason } from './saved-places.js';

const check = (signal) => { if (signal?.aborted) throw new DOMException('Aborted', 'AbortError'); };
const revision = () => JSON.stringify(archive.sources().map((source) => [source, catalogue.revisionOf(`${source}:`)]));

/** Local index reads only. A changed dataset cannot publish an obsolete availability/destination. */
async function currentRead(signal, read) {
  for (let attempt = 0; attempt < 2; attempt++) {
    check(signal); const before = revision();
    const found = await read(); check(signal);
    if (revision() === before) return found;
  }
  throw new Error('The catalogue changed while reading a saved place');
}

export function savedPlaceRows(places, signal) {
  return currentRead(signal, async () => {
    const metadata = new Map(), groups = new Map(), rows = [];
    for (const place of places) {
      check(signal);
      const key = place.key;
      if (!metadata.has(key.catalogueId)) metadata.set(key.catalogueId,
        archive.sources().includes(key.catalogueId) ? await archive.meta(key.catalogueId) : null);
      check(signal);
      let availability = savedPlaceReason(key, metadata.get(key.catalogueId), archive.sources().includes(key.catalogueId));
      if (!availability) {
        const id = JSON.stringify([key.catalogueId, key.format]);
        if (!groups.has(id)) groups.set(id, await archive.authors(key.format, key.catalogueId));
        availability = groups.get(id).some((group) => group.name === key.author && group.count > 0) ? 'available' : 'group-missing';
      }
      rows.push({ place, availability });
    }
    return rows;
  });
}

export function resolveSavedPlace(key, signal) {
  return currentRead(signal, async () => {
    const supported = archive.sources().includes(key.catalogueId);
    const meta = supported ? await archive.meta(key.catalogueId) : null;
    const reason = savedPlaceReason(key, meta, supported);
    if (reason) return { reason };
    check(signal);
    const tracks = await archive.tracksIn(key.format, key.author, key.catalogueId);
    return tracks.length ? { meta, tracks } : { reason: 'group-missing' };
  });
}
