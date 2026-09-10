// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later
//
// The online archives, indexed in the browser.
//
// **`docs/PLAN_WEB_LIBRARY.md` S3, and the shape came out of a measurement rather than a
// preference.** A record per track meant 515,509 writes and 41 seconds on the owner's slower
// machine; a record per **bucket** — one per format and author — means 43,715 writes, no index at
// all, and 2.6 seconds. The key *is* the lookup, which is the structure an index would have built.
//
// Modland only, so far. It is the one that matters and the only one whose numbers these are.

import { catalogue } from './store.js';

const MODLAND = 'modland';
const INDEX_URL = 'https://modland.com/allmods.zip';
const FILE_BASE = 'https://modland.com/pub/modules/';

const key = {
  meta: `${MODLAND}:meta`,
  formats: `${MODLAND}:formats`,
  authors: (format) => `${MODLAND}:authors:${format}`,
  tracks: (format, author) => `${MODLAND}:tracks:${format}/${author}`,
};

/**
 * Where the one member's compressed bytes begin and end.
 *
 * **Exactly, and that is the whole of `docs/STATUS.md` C33.** A zip is not a gzip: after the deflate
 * stream come a data descriptor, a central directory and an end record. Node's `DecompressionStream`
 * ignores those; **Firefox refuses them** — *"unexpected input after the end of stream"*, which is
 * what the owner got and what could not be reproduced here, because node had been asked instead.
 *
 * The compressed size is in the local header, unless bit 3 of the general-purpose flags says the
 * writer did not know it yet — then it is in the central directory, which is found from the end
 * record at the tail of the file. `allmods.zip` fills in the local header today; the fallback is
 * there because a zip writer is allowed not to and this one costs fifteen lines.
 */
export function memberBounds(bytes) {
  const view = new DataView(bytes);
  if (view.getUint32(0, true) !== 0x04034b50) throw new Error('not a zip');
  const start = 30 + view.getUint16(26, true) + view.getUint16(28, true);
  const flags = view.getUint16(6, true);
  let compressed = view.getUint32(18, true);

  if (compressed === 0 || (flags & 0x08) !== 0) {
    // The end-of-central-directory record, scanned back from the tail: its signature, then the
    // directory's own offset twelve bytes later. A comment may follow it, so it is a search rather
    // than a fixed position — bounded to the 64 KB a comment may be.
    let eocd = -1;
    for (let i = bytes.byteLength - 22; i >= Math.max(0, bytes.byteLength - 65558); i--) {
      if (view.getUint32(i, true) === 0x06054b50) { eocd = i; break; }
    }
    if (eocd < 0) throw new Error('no central directory');
    const directory = view.getUint32(eocd + 16, true);
    if (view.getUint32(directory, true) !== 0x02014b50) throw new Error('central directory is not there');
    compressed = view.getUint32(directory + 20, true);
  }
  if (!compressed) throw new Error('the archive does not say how long its member is');
  return { start, end: start + compressed };
}

/**
 * The one member of `allmods.zip`, decompressed.
 *
 * Fed exactly the member's bytes and nothing after them — see `memberBounds`. `DecompressionStream`
 * does the rest and never holds more than a chunk.
 */
async function unzipOnly(bytes) {
  const { start, end } = memberBounds(bytes);
  const stream = new Blob([bytes.slice(start, end)]).stream()
    .pipeThrough(new DecompressionStream('deflate-raw'));
  return new Response(stream).text();
}

/**
 * Turns the index into the records that will be stored.
 *
 * **All the buckets are held at once, and that was measured before it was chosen**: 55 MB, against
 * 218 for a row per track. The file is not grouped — 3,727 buckets come back after a gap — so
 * flushing on change would mean merging, and merging 3,727 records to save 55 MB is the wrong
 * trade on a machine that offered 473 GB.
 */
export function toRecords(text) {
  const buckets = new Map();
  const formats = new Map();
  let tracks = 0;

  for (const line of text.split('\n')) {
    const tab = line.indexOf('\t');
    if (tab <= 0) continue;
    const size = Number(line.slice(0, tab)) || 0;
    const path = line.slice(tab + 1);
    const parts = path.split('/');
    if (parts.length < 2) continue;
    // "Format/Author/title" is the convention; coop releases and unknown authors give deeper and
    // shallower paths. Taking the first segment as the format and everything between as the author
    // keeps both readable, which is what `Catalogue.Modland.parseIndex` decided and what a stored
    // path has to agree with.
    const format = parts[0];
    const author = parts.slice(1, -1).join('/');
    const title = parts[parts.length - 1];

    const bucket = `${format}/${author}`;
    let held = buckets.get(bucket);
    if (!held) { held = []; buckets.set(bucket, held); }
    held.push({ t: title, s: size });
    formats.set(format, (formats.get(format) ?? 0) + 1);
    tracks++;
  }

  const records = [];
  const authorsByFormat = new Map();
  for (const [bucket, held] of buckets) {
    const slash = bucket.indexOf('/');
    const format = bucket.slice(0, slash);
    const author = bucket.slice(slash + 1);
    records.push({ key: key.tracks(format, author), tracks: held });
    let authors = authorsByFormat.get(format);
    if (!authors) { authors = []; authorsByFormat.set(format, authors); }
    authors.push({ name: author, count: held.length });
  }

  const collator = new Intl.Collator(undefined, { sensitivity: 'base' });
  for (const [format, authors] of authorsByFormat) {
    authors.sort((a, b) => collator.compare(a.name, b.name));
    records.push({ key: key.authors(format), authors });
  }
  records.push({
    key: key.formats,
    formats: [...formats].map(([name, count]) => ({ name, count }))
      .sort((a, b) => collator.compare(a.name, b.name)),
  });

  return { records, tracks, buckets: buckets.size, formats: formats.size };
}

/**
 * Fetches the index and stores it.
 *
 * [fingerprint] is what the engine reports for the decoders in this build. It is recorded and
 * checked for the same reason the phone records it: an index is filtered by what can be played, so
 * one built by an older set is missing files and **looks empty rather than out of date**. The owner
 * lost 60,572 C64 tunes to exactly that once.
 */
export async function downloadModland({ fingerprint = '', keep = () => true, onProgress = null } = {}) {
  onProgress?.({ stage: 'fetching' });
  const response = await fetch(INDEX_URL);
  if (!response.ok) throw new Error(`HTTP ${response.status}`);
  const bytes = await response.arrayBuffer();

  onProgress?.({ stage: 'reading' });
  const text = await unzipOnly(bytes);

  onProgress?.({ stage: 'sorting' });
  const kept = keep === null ? text : text.split('\n')
    .filter((line) => { const tab = line.indexOf('\t'); return tab > 0 && keep(line.slice(line.lastIndexOf('/') + 1)); })
    .join('\n');
  const { records, tracks, buckets, formats } = toRecords(kept);

  await catalogue.clear(`${MODLAND}:`);
  await catalogue.putAll(records, 2000, (done, total) => onProgress?.({ stage: 'storing', done, total }));
  await catalogue.putAll([{ key: key.meta, tracks, buckets, formats, fingerprint, at: Date.now() }]);
  onProgress?.({ stage: 'done', tracks, buckets, formats });
  return { tracks, buckets, formats };
}

export async function meta() { return catalogue.get(key.meta); }
export async function formats() { return (await catalogue.get(key.formats))?.formats ?? []; }
export async function authors(format) { return (await catalogue.get(key.authors(format)))?.authors ?? []; }

/** One bucket, as tracks the queue understands. */
export async function tracksIn(format, author) {
  const found = await catalogue.get(key.tracks(format, author));
  return (found?.tracks ?? []).map(({ t, s }) => ({
    url: urlFor(format, author, t),
    name: t,
    file: t,
    meta: `Modland/${format}/${author}`,
    size: s,
  }));
}

/**
 * Modland's own address rule, segment by segment — **the same string the phone builds**.
 *
 * `Catalogue.Modland.urlFor` uses Java's `URLEncoder`, which escapes everything outside
 * `A-Za-z0-9.-*_` and then has its `+` turned into `%20`. `encodeURIComponent` keeps seven more
 * characters than that — `!`, `~`, `'`, `(`, `)` among them — so the two would have produced
 * different strings for the same file. Both work when fetched, and that is exactly why it would
 * have gone unnoticed until something compared them: a queue that thinks the phone's copy of a
 * track and its own are two different tracks.
 *
 * `docs/rules/queue-cases.tsv` has the cases, and `Modland` in `CatalogueTest` checks the other end
 * against the same ones. `!!uu !! !!.it` is in there because the owner played it.
 */
export function urlFor(format, author, title) {
  return FILE_BASE + [format, ...author.split('/'), title].map(encodeSegment).join('/');
}

function encodeSegment(segment) {
  let out = '';
  for (const character of segment) {
    out += /[A-Za-z0-9.\-*_]/.test(character)
      ? character
      : [...new TextEncoder().encode(character)]
          .map((byte) => `%${byte.toString(16).toUpperCase().padStart(2, '0')}`).join('');
  }
  return out;
}
