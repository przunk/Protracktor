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
// Two archives: Modland, and since 2026-09-11 ASMA. Both are stored in the same shape under their
// own prefix -- a group (Modland's format, ASMA's section), an author, the tunes -- so browsing,
// search and the dice treat them alike.

import { catalogue } from './store.js';

const MODLAND = 'modland';
const ASMA = 'asma';
const INDEX_URL = 'https://modland.com/allmods.zip';
const ASMA_INDEX_URL = 'https://asma.atari.org/asmadb/asma.zip';

/** What each archive is called on screen, and where its files are served. */
const SOURCES = {
  [MODLAND]: { name: 'Modland', files: 'https://modland.com/pub/modules/' },
  // ASMA serves every file at its zip entry's own path (measured 2026-09-11), which is what lets a
  // browser play it without the 20 MB archive -- `docs/rules/queue-cases.tsv` [asmaUrl].
  [ASMA]: { name: 'ASMA', files: 'https://asma.atari.org/asma/' },
};

/** Every archive this page can hold, in the order they are listed. */
export function sources() { return Object.keys(SOURCES); }

export function sourceName(source) { return SOURCES[source]?.name ?? source; }

const keyFor = (source) => ({
  meta: `${source}:meta`,
  formats: `${source}:formats`,
  authors: (format) => `${source}:authors:${format}`,
  tracks: (format, author) => `${source}:tracks:${format}/${author}`,
  /**
   * Titles, sharded by their first two characters.
   *
   * **So that searching reads 1,663 records instead of 43,715.** Measured 2026-09-10: building the
   * shards costs 433 ms and about 29 MB, against 473 GB offered — and it is what turns "find a tune
   * by name" from reading the whole index into reading a twenty-sixth of it.
   */
  titles: (two) => `${source}:titles:${two}`,
});

/**
 * `web/src/formats.tsv`, read into two maps of name to decoders, and one of name to machine.
 *
 * The file is the list the phone indexes by too (`SupportedFormatsFileTest` holds the two
 * together), so a page that keeps what this says keeps what the phone keeps -- minus whatever the
 * engine in hand cannot open.
 */
export function parseFormats(text) {
  const extensions = new Map();
  const prefixes = new Map();
  const platforms = new Map();
  for (const raw of text.split('\n')) {
    const line = raw.trim();
    if (!line || line.startsWith('#')) continue;
    const [kind, name, decoders, platform] = line.split('\t');
    if (!name || !decoders) continue;
    (kind === 'prefix' ? prefixes : extensions).set(name, decoders.split(','));
    if (platform && platform !== '-') platforms.set(name, platform);
  }
  return { extensions, prefixes, platforms };
}

/**
 * The machine a file belongs to, or null -- `Platforms.forFileName` on the phone, rule for rule: the
 * extension, or failing that the part before the first dot, both asked of one table of names.
 */
export function platformOf(table, fileName) {
  const name = String(fileName ?? '').toLowerCase();
  if (!table?.platforms || !name.includes('.')) return null;
  return table.platforms.get(name.slice(name.lastIndexOf('.') + 1))
    ?? table.platforms.get(name.slice(0, name.indexOf('.')))
    ?? null;
}

/**
 * The decoders the engine says it does not have.
 *
 * **Only what it says.** `pt_backends` appends `zxtune:none` when that decoder is compiled out and
 * mentions nothing else as missing -- HivelyTracker is in every build and never named. So absence is
 * read from the fingerprint and never inferred from a name not being there.
 */
export function absentDecoders(engineFingerprint) {
  const absent = new Set();
  for (const part of String(engineFingerprint ?? '').split(';')) {
    const [name, version] = part.split(':');
    if (name && version === 'none') absent.add(name);
  }
  return absent;
}

/**
 * Which listed name a file is filed under, the phone's way: its extension, or failing that an
 * Amiga-style prefix (`mod.title`). Mirrors `SupportedFormats.inCatalogueIndex` exactly.
 */
function listedAs(table, fileName) {
  const name = String(fileName).toLowerCase();
  const dot = name.lastIndexOf('.');
  const extension = dot >= 0 ? name.slice(dot + 1) : '';
  if (extension && table.extensions.has(extension)) return table.extensions.get(extension);
  const first = name.indexOf('.');
  const prefix = first > 0 ? name.slice(0, first) : '';
  if (prefix && table.prefixes.has(prefix)) return table.prefixes.get(prefix);
  return null;
}

/** Whether the phone would index a name. The page's own question is [playable]. */
export function onPhone(table) {
  return (fileName) => listedAs(table, fileName) !== null;
}

/**
 * Whether **this** engine can open a name: listed, and at least one of its decoders present.
 *
 * *"Nie indeksujmy utworów, których nie zagramy"* (owner, 2026-09-10). The browser build has no
 * ZXTune, so `pt3`, `ym` and the rest of the Spectrum's formats fall out here -- 26,559 of the rows
 * the phone keeps.
 */
export function playable(table, absent) {
  return (fileName) => {
    const decoders = listedAs(table, fileName);
    return decoders !== null && decoders.some((decoder) => !absent.has(decoder));
  };
}

/**
 * What an index records about the set that filtered it.
 *
 * **The engine and the list together**, which is the phone's lesson: recording only the decoders
 * was half the truth, and on 2026-09-04 five names added to the list left every stored index
 * missing 5,558 files while it reported itself current. Order-independent; it only has to differ
 * when either half does.
 */
export function indexFingerprint(engineFingerprint, table) {
  const names = [...table.extensions].map(([n, d]) => `e:${n}=${d.join('+')}`)
    .concat([...table.prefixes].map(([n, d]) => `p:${n}=${d.join('+')}`))
    .sort().join(',');
  let hash = 0;
  for (let i = 0; i < names.length; i++) hash = (Math.imul(hash, 31) + names.charCodeAt(i)) | 0;
  return `${engineFingerprint}|names:${(hash >>> 0).toString(16).padStart(8, '0')}`;
}

/**
 * The index lines worth keeping, and a count of what was left out and why.
 *
 * [total] is every tune Modland lists; [phoneOnly] is the part the phone keeps and this engine
 * cannot open. Both are what lets Browse say plainly how much of Modland is here and why the rest
 * is not -- the owner's condition for leaving anything out at all.
 */
export function filterIndex(text, keep, phone = () => true) {
  const kept = [];
  let total = 0;
  let phoneOnly = 0;
  for (const line of text.split('\n')) {
    const tab = line.indexOf('\t');
    if (tab <= 0) continue;
    total++;
    const fileName = line.slice(line.lastIndexOf('/') + 1);
    if (keep(fileName)) kept.push(line);
    else if (phone(fileName)) phoneOnly++;
  }
  return { text: kept.join('\n'), total, phoneOnly };
}

/** The shard a title belongs to. Lower-cased and padded, so every title has exactly one. */
function shardOf(title) { return title.toLowerCase().slice(0, 2).padEnd(2, ' '); }

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
export function toRecords(text, source = MODLAND) {
  const key = keyFor(source);
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

  // Titles, sharded. Each entry carries what a hit needs to become playable and to say where it
  // came from -- there is no second lookup when somebody presses one.
  const shards = new Map();
  for (const [bucket, held] of buckets) {
    const slash = bucket.indexOf('/');
    const format = bucket.slice(0, slash);
    const author = bucket.slice(slash + 1);
    for (const { t } of held) {
      const shard = shardOf(t);
      let entries = shards.get(shard);
      if (!entries) { entries = []; shards.set(shard, entries); }
      entries.push([t, format, author]);
    }
  }
  for (const [shard, entries] of shards) records.push({ key: key.titles(shard), entries });

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
export async function downloadModland({
  fingerprint = '', keep = () => true, phone = () => true, onProgress = null,
} = {}) {
  onProgress?.({ stage: 'fetching' });
  const response = await fetch(INDEX_URL);
  if (!response.ok) throw new Error(`HTTP ${response.status}`);
  const bytes = await response.arrayBuffer();

  onProgress?.({ stage: 'reading' });
  const text = await unzipOnly(bytes);

  onProgress?.({ stage: 'sorting' });
  const { text: kept, total, phoneOnly } = filterIndex(text, keep, phone);
  const { records, tracks, buckets, formats } = toRecords(kept);
  const key = keyFor(MODLAND);

  await catalogue.clear(`${MODLAND}:`);
  await catalogue.putAll(records, 2000, (done, total) => onProgress?.({ stage: 'storing', done, total }));
  await catalogue.putAll([{
    key: key.meta, tracks, total, phoneOnly, buckets, formats, fingerprint, at: Date.now(),
  }]);
  onProgress?.({ stage: 'done', tracks, total, phoneOnly, buckets, formats });
  return { tracks, total, phoneOnly, buckets, formats };
}

/**
 * ASMA's list, **read out of its archive without downloading the archive**.
 *
 * ASMA publishes one 20 MB zip and no separate index; the phone downloads the lot, which is what
 * makes it work offline. A browser needs only the list, and a zip keeps its list at the end: the
 * end record says where the central directory is, and the directory names every file and its
 * size. Two ranged requests -- the tail, then the directory -- come to 0.85 MB (measured
 * 2026-09-11: 6,780 entries, 6,335 of them `.sap`), and each tune is then fetched from its own
 * address when it plays.
 *
 * A server that ignores `Range` answers 200 with the whole file; the same reading works on that,
 * because the offsets are the file's own.
 *
 * **Never the suffix form, `bytes=-N`** (owner, 2026-09-11: "Failed to fetch"). Only a range with
 * a start is CORS-safelisted; any other makes the browser ask first, and asma.atari.org answers that
 * question without `Access-Control-Allow-Headers`, so the request was refused before it left. The
 * size comes from a HEAD instead, which needs no asking, and the tail is then an ordinary range. A
 * browser too old to safelist even that gets the whole archive, which is slow and always allowed.
 */
export async function downloadAsma({ fingerprint = '', keep = () => true, onProgress = null } = {}) {
  onProgress?.({ stage: 'fetching the list' });
  // The end record is 22 bytes and may be followed by a comment of up to 64 KB.
  let tail;
  try {
    const head = await fetch(ASMA_INDEX_URL, { method: 'HEAD' });
    const length = Number(head.headers.get('content-length'));
    if (!head.ok || !length) throw new Error('no size');
    tail = await ranged(ASMA_INDEX_URL, `bytes=${Math.max(0, length - 65557)}-${length - 1}`);
    tail.start = Math.max(0, length - 65557);
  } catch {
    onProgress?.({ stage: 'fetching the whole archive (20 MB), this browser will not ask for part of it' });
    const response = await fetch(ASMA_INDEX_URL);
    if (!response.ok) throw new Error(`HTTP ${response.status}`);
    tail = { bytes: await response.arrayBuffer(), whole: true };
  }
  const view = new DataView(tail.bytes);
  let end = -1;
  for (let i = tail.bytes.byteLength - 22; i >= 0; i--) {
    if (view.getUint32(i, true) === 0x06054b50) { end = i; break; }
  }
  if (end < 0) throw new Error('no end record in the archive');
  const size = view.getUint32(end + 12, true);
  const offset = view.getUint32(end + 16, true);
  // Already in hand when the tail reached back far enough -- or was the whole file.
  const from = tail.whole ? 0 : tail.start;
  const directory = offset >= from
    ? new DataView(tail.bytes, offset - from, size)
    : new DataView((await ranged(ASMA_INDEX_URL, `bytes=${offset}-${offset + size - 1}`)).bytes);

  onProgress?.({ stage: 'reading' });
  const names = new TextDecoder();
  const lines = [];
  for (let at = 0; at + 46 <= directory.byteLength && directory.getUint32(at, true) === 0x02014b50;) {
    const bytes = directory.getUint32(at + 24, true);
    const nameLength = directory.getUint16(at + 28, true);
    const extra = directory.getUint16(at + 30, true);
    const comment = directory.getUint16(at + 32, true);
    const name = names.decode(new Uint8Array(directory.buffer, directory.byteOffset + at + 46, nameLength));
    at += 46 + nameLength + extra + comment;
    // `asma/<section>/<author>/<title>.sap`; directories end in a slash and hold nothing.
    if (!name.startsWith('asma/') || name.endsWith('/')) continue;
    lines.push(`${bytes}\t${name.slice('asma/'.length)}`);
  }

  onProgress?.({ stage: 'sorting' });
  const { text: kept, total } = filterIndex(lines.join('\n'), keep);
  const { records, tracks, buckets, formats } = toRecords(kept, ASMA);
  await catalogue.clear(`${ASMA}:`);
  await catalogue.putAll(records, 2000, (done, all) => onProgress?.({ stage: 'storing', done, total: all }));
  await catalogue.putAll([{
    key: keyFor(ASMA).meta, tracks, total, phoneOnly: 0, buckets, formats, fingerprint, at: Date.now(),
  }]);
  onProgress?.({ stage: 'done', tracks, total, buckets, formats });
  return { tracks, total, buckets, formats };
}

/** One ranged request, and whether the server ignored the range and sent everything. */
async function ranged(url, range) {
  const response = await fetch(url, { headers: { Range: range } });
  if (!response.ok) throw new Error(`HTTP ${response.status}`);
  return { bytes: await response.arrayBuffer(), whole: response.status === 200, start: 0 };
}

/**
 * Tunes whose title contains [query].
 *
 * **Every shard is read, and that is the design rather than a shortcut.** A first-two-characters
 * index would answer a prefix instantly and would not find "elysium" inside "the elysium remix",
 * which is what a person typing a name expects. Reading 1,663 records to get a substring search is
 * the trade the sharding was for; reading 43,715 was the alternative.
 *
 * Capped, because a query of one letter matches tens of thousands and nobody reads those.
 */
export async function searchTitles(query, limit = 200, source = MODLAND) {
  const needle = query.trim().toLowerCase();
  if (needle.length < 2) return { hits: [], scanned: 0, capped: false };
  const found = [];
  let scanned = 0;
  for (const shard of await allTitleShards(source)) {
    for (const [title, format, author] of shard.entries) {
      scanned++;
      if (!title.toLowerCase().includes(needle)) continue;
      if (found.length < limit) {
        found.push({ url: urlFor(format, author, title, source), name: title, file: title,
                     meta: metaFor(source, format, author) });
      } else {
        return { hits: found, scanned, capped: true };
      }
    }
  }
  return { hits: found, scanned, capped: false };
}

/** Authors whose name contains [query], with the format they are filed under. */
export async function searchAuthors(query, limit = 100, source = MODLAND) {
  const needle = query.trim().toLowerCase();
  if (needle.length < 2) return [];
  const found = [];
  for (const { name: format } of await formats(source)) {
    for (const { name, count } of await authors(format, source)) {
      if (!name.toLowerCase().includes(needle)) continue;
      found.push({ source, format, author: name, count });
      if (found.length >= limit) return found;
    }
  }
  return found;
}

/**
 * Every title shard, asked for by prefix.
 *
 * By prefix rather than by walking an alphabet: Modland's titles begin with brackets, exclamation
 * marks, digits, Cyrillic and things nobody would think to list, and a guessed alphabet loses tunes
 * silently.
 */
function allTitleShards(source = MODLAND) { return catalogue.byPrefix(`${source}:titles:`); }

export async function meta(source = MODLAND) { return catalogue.get(keyFor(source).meta); }
export async function formats(source = MODLAND) { return (await catalogue.get(keyFor(source).formats))?.formats ?? []; }
export async function authors(format, source = MODLAND) {
  return (await catalogue.get(keyFor(source).authors(format)))?.authors ?? [];
}

/**
 * Every tune this browser holds, as a running count over the buckets it is filed in.
 *
 * **Uniform over tunes, not over authors** -- the owner's decision, 2026-09-10: *"mnie interesują
 * utwory, nie autorzy"*. The two differ enough to matter: after item 1's filter there are 32,212
 * buckets, median 2, the largest 3,615, and 41% hold one tune. Drawing an author first would make a
 * one-tune author as likely as Bayliss with 1,298.
 *
 * Built from the author lists, which already carry each bucket's count, so it costs one read per
 * format and none per bucket. Held by the caller for the session; a roll is then a random number
 * and a binary search.
 */
export async function buildRandomTable() {
  const entries = [];
  let total = 0;
  // Every archive held, one pool: a tune from ASMA is as likely as any tune from Modland.
  for (const source of sources()) {
    for (const { name: format } of await formats(source)) {
      for (const { name: author, count } of await authors(format, source)) {
        if (!count) continue;
        total += count;
        entries.push({ source, format, author, end: total });
      }
    }
  }
  return { entries, total };
}

/**
 * One tune, every tune in [table] equally likely.
 *
 * One random number does both jobs: it picks the bucket by where it falls in the running count, and
 * its distance past the bucket's start picks the tune inside it.
 */
export async function drawTrack(table, random = Math.random) {
  if (!table?.total) return null;
  const r = Math.min(table.total - 1, Math.floor(random() * table.total));
  let lo = 0;
  let hi = table.entries.length - 1;
  while (lo < hi) {
    const mid = (lo + hi) >> 1;
    if (table.entries[mid].end > r) hi = mid; else lo = mid + 1;
  }
  const bucket = table.entries[lo];
  const start = lo ? table.entries[lo - 1].end : 0;
  const tracks = await tracksIn(bucket.format, bucket.author, bucket.source);
  return tracks[r - start] ?? tracks[0] ?? null;
}

/** One bucket, as tracks the queue understands. */
export async function tracksIn(format, author, source = MODLAND) {
  const found = await catalogue.get(keyFor(source).tracks(format, author));
  return (found?.tracks ?? []).map(({ t, s }) => ({
    url: urlFor(format, author, t, source),
    name: t,
    file: t,
    meta: metaFor(source, format, author),
    size: s,
  }));
}

/** Where a tune lives, as the row's second line says it: `Modland/Protracker/4-Mat`. */
function metaFor(source, format, author) {
  return [sourceName(source), format, author].filter(Boolean).join('/');
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
export function urlFor(format, author, title, source = MODLAND) {
  // An empty author is a file filed straight under its group (ASMA's `Games/Title.sap`), not an
  // empty folder: splitting "" gives one empty segment, and the address a double slash.
  return SOURCES[source].files
    + [format, ...(author ? author.split('/') : []), title].map(encodeSegment).join('/');
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
