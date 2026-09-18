// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later
//
// The online archives, indexed in the browser.
//
// **`docs/PLAN_WEB_LIBRARY.md` S3, and the shape came out of a measurement rather than a
// preference.** A record per track means 515,509 writes and 41 seconds on a slower machine; a
// record per **bucket** — one per format and author — means 43,715 writes, no index at
// all, and 2.6 seconds. The key *is* the lookup, which is the structure an index would have built.
//
// Two archives, Modland and ASMA. Both are stored in the same shape under their
// own prefix -- a group (Modland's format, ASMA's section), an author, the tunes -- so browsing,
// search and the dice treat them alike.

import { catalogue } from './store.js';
import { md5, parseSongLengths } from './songlengths.js';
import { searchTerms } from './rules.js';

const MODLAND = 'modland';
const ASMA = 'asma';
const INDEX_URL = 'https://modland.com/allmods.zip';
const ASMA_INDEX_URL = 'https://asma.atari.org/asmadb/asma.zip';

/** What each archive is called on screen, and where its files are served. */
const SOURCES = {
  [MODLAND]: { name: 'Modland', files: 'https://modland.com/pub/modules/' },
  // ASMA serves every file at its zip entry's own path (measured), which is what lets a
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
   * **So that searching reads 1,663 records instead of 43,715.** Building the shards costs 433 ms
   * and about 29 MB, against 473 GB offered — and it is what turns "find a tune
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
 * Nothing is indexed that this build cannot play. The browser build has no ZXTune, so `pt3`, `ym`
 * and the rest of the Spectrum's formats fall out here -- 26,559 of the rows the phone keeps.
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
 * **The engine and the list together**, which is the phone's rule as well: recording only the
 * decoders is half the truth, and five names added to the list leave every stored index missing
 * 5,558 files while it reports itself current. Order-independent; it only has to differ
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


/** The shard a title belongs to. Lower-cased and padded, so every title has exactly one. */
function shardOf(title) { return title.toLowerCase().slice(0, 2).padEnd(2, ' '); }

/**
 * Where the one member's compressed bytes begin and end.
 *
 * **Exactly, and that is the whole of `docs/STATUS.md` C33.** A zip is not a gzip: after the deflate
 * stream come a data descriptor, a central directory and an end record. Node's `DecompressionStream`
 * ignores those; **Firefox refuses them** — "unexpected input after the end of stream". Node will
 * not reproduce it, so this is one to check in a browser.
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
export function toRecords(text, source = MODLAND, isPlayable = () => true) {
  const key = keyFor(source);
  const buckets = new Map();
  const formats = new Map();
  let tracks = 0;
  let total = 0;

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
    // **Every row the archive lists, with the verdict beside it** (`docs/ROADMAP_FORMATS.md` step
    // 0). The index used to keep only what this build could open, which made it a function of the
    // format list -- so adding one format meant fetching the whole index again, on every device.
    const p = isPlayable(title) ? 1 : 0;
    held.push({ t: title, s: size, p });
    total++;
    if (p) {
      formats.set(format, (formats.get(format) ?? 0) + 1);
      tracks++;
    }
  }

  // **The buckets hold everything; everything derived from them holds only what plays.** That is
  // the whole shape of this: browsing, searching and the dice read the derived records and never
  // have to think about it, and a format added later is a local rebuild of these from buckets that
  // are already here (`refreshPlayable`) rather than a download.
  const records = [];
  const authorsByFormat = new Map();
  for (const [bucket, held] of buckets) {
    const slash = bucket.indexOf('/');
    const format = bucket.slice(0, slash);
    const author = bucket.slice(slash + 1);
    records.push({ key: key.tracks(format, author), tracks: held });
    const count = held.reduce((n, entry) => n + (entry.p ? 1 : 0), 0);
    // An author with nothing playable is not an author to offer. The bucket stays; the listing
    // does not mention it.
    if (count === 0) continue;
    let authors = authorsByFormat.get(format);
    if (!authors) { authors = []; authorsByFormat.set(format, authors); }
    authors.push({ name: author, count });
  }

  // Titles, sharded. Each entry carries what a hit needs to become playable and to say where it
  // came from -- there is no second lookup when somebody presses one.
  const shards = new Map();
  for (const [bucket, held] of buckets) {
    const slash = bucket.indexOf('/');
    const format = bucket.slice(0, slash);
    const author = bucket.slice(slash + 1);
    for (const { t, p } of held) {
      if (!p) continue;
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

  return { records, tracks, total, buckets: buckets.size, formats: formats.size };
}

/**
 * Fetches the index and stores it.
 *
 * [fingerprint] is what the engine reports for the decoders in this build. It is recorded and
 * checked for the same reason the phone records it: an index is filtered by what can be played, so
 * one built by an older set is missing files and **looks empty rather than out of date** -- which
 * can be 60,572 C64 tunes with nothing on screen to say why.
 */
export async function downloadModland({
  fingerprint = '', isPlayable = () => true, onProgress = null,
} = {}) {
  onProgress?.({ stage: 'fetching' });
  const response = await fetch(INDEX_URL);
  if (!response.ok) throw new Error(`HTTP ${response.status}`);
  const bytes = await response.arrayBuffer();

  onProgress?.({ stage: 'reading' });
  const text = await unzipOnly(bytes);

  onProgress?.({ stage: 'sorting' });
  const { records, tracks, total, buckets, formats } = toRecords(text, MODLAND, isPlayable);
  const key = keyFor(MODLAND);

  await catalogue.clear(`${MODLAND}:`);
  await catalogue.putAll(records, 2000, (done, count) => onProgress?.({ stage: 'storing', done, total: count }));
  await catalogue.putAll([{
    // **`complete`, the way the phone's `catalogues` row records it.** An index written before step
    // 0 holds only what an older build accepted, so a format added afterwards genuinely is missing
    // rows here and no local pass can supply them. One written since holds the archive.
    key: key.meta, tracks, total, buckets, formats, fingerprint, complete: true, at: Date.now(),
  }]);
  onProgress?.({ stage: 'done', tracks, total, buckets, formats });
  return { tracks, total, buckets, formats };
}

/**
 * ASMA's list, **read out of its archive without downloading the archive**.
 *
 * ASMA publishes one 20 MB zip and no separate index; the phone downloads the lot, which is what
 * makes it work offline. A browser needs only the list, and a zip keeps its list at the end: the
 * end record says where the central directory is, and the directory names every file and its
 * size. Two ranged requests -- the tail, then the directory -- come to 0.85 MB (measured: 6,780
 * entries, 6,335 of them `.sap`), and each tune is then fetched from its own
 * address when it plays.
 *
 * A server that ignores `Range` answers 200 with the whole file; the same reading works on that,
 * because the offsets are the file's own.
 *
 * **Never the suffix form, `bytes=-N`**, which fails with "Failed to fetch". Only a range with a
 * start is CORS-safelisted; any other makes the browser ask first, and asma.atari.org answers that
 * question without `Access-Control-Allow-Headers`, so the request is refused before it leaves. The
 * size comes from a HEAD instead, which needs no asking, and the tail is then an ordinary range. A
 * browser too old to safelist even that gets the whole archive, which is slow and always allowed.
 */
export async function downloadAsma({ fingerprint = '', isPlayable = () => true, onProgress = null } = {}) {
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
  const { records, tracks, total, buckets, formats } = toRecords(lines.join('\n'), ASMA, isPlayable);
  await catalogue.clear(`${ASMA}:`);
  await catalogue.putAll(records, 2000, (done, all) => onProgress?.({ stage: 'storing', done, total: all }));
  await catalogue.putAll([{
    key: keyFor(ASMA).meta, tracks, total, buckets, formats, fingerprint, complete: true, at: Date.now(),
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
  // **Every word, anywhere, in any order** (`rules.js` `searchTerms`, and the phone's
  // `SearchTerms`): `space ninja` finds `space_ninja`, which one substring never did.
  const words = searchTerms(query);
  if (words.join('').length < 2) return { hits: [], scanned: 0, capped: false };
  const found = [];
  let scanned = 0;
  for (const shard of await allTitleShards(source)) {
    for (const [title, format, author] of shard.entries) {
      scanned++;
      const haystack = title.toLowerCase();
      // A plain loop rather than `every`, because this runs half a million times a keystroke and
      // the first word that is missing is the answer.
      let all = true;
      for (const word of words) { if (!haystack.includes(word)) { all = false; break; } }
      if (!all) continue;
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
  const words = searchTerms(query);
  if (words.join('').length < 2) return [];
  const found = [];
  for (const { name: format } of await formats(source)) {
    for (const { name, count } of await authors(format, source)) {
      const haystack = name.toLowerCase();
      if (!words.every((word) => haystack.includes(word))) continue;
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
 * **Uniform over tunes, not over authors**, because what is being picked is a tune. The two differ
 * enough to matter: after the playable filter there are 32,212 buckets, median 2, the largest
 * 3,615, and 41% hold one tune. Drawing an author first would make a one-tune author as likely as
 * Bayliss with 1,298.
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
  // The bucket holds the folder as the archive has it; this is a list to play from. An index
  // written before step 0 has no `p` at all, and every row in it was playable by construction —
  // hence `!== 0` rather than a truth test.
  return (found?.tracks ?? []).filter(({ p }) => p !== 0).map(({ t, s }) => ({
    url: urlFor(format, author, t, source),
    name: t,
    file: t,
    meta: metaFor(source, format, author),
    size: s,
  }));
}

/**
 * Re-decides what a stored index offers, without fetching anything.
 *
 * **The page's half of `docs/ROADMAP_FORMATS.md` step 0**, and the phone's `refreshPlayable` is the
 * other. A stored index holds every row the archive lists, so a format this build has learnt since
 * the download is a question the stored rows can already answer. It used to mean fetching Modland's
 * 5.76 MB again, on every device, once per format added.
 *
 * The buckets are the truth and everything else is derived from them — the searchable title shards,
 * the author lists, the format counts — so this rewrites the derived records rather than trying to
 * patch them. That is also why the buckets carry `p` and the derived records do not need to.
 *
 * **Does nothing to an index written before step 0**, and cannot: those hold only the rows an older
 * build accepted, and no local pass can supply what was never downloaded. They say so
 * (`complete`), and Browse offers them the one download that ends this for good.
 *
 * @returns how many tunes are offered afterwards, or null when there was nothing to do.
 */
export async function refreshPlayable(source = MODLAND, isPlayable = () => true) {
  const key = keyFor(source);
  const held = await catalogue.get(key.meta);
  if (!held?.complete) return null;

  const buckets = await catalogue.byPrefix(`${source}:tracks:`);
  if (buckets.length === 0) return null;

  // Rebuilt from the buckets, exactly as `toRecords` derives them, so the two cannot drift: one
  // line of text becomes one bucket entry becomes one row of everything else.
  const records = [];
  const authorsByFormat = new Map();
  const formats = new Map();
  const shards = new Map();
  let tracks = 0;
  let total = 0;

  for (const bucket of buckets) {
    const [format, ...rest] = bucket.key.slice(`${source}:tracks:`.length).split("/");
    const author = rest.join('/');
    let count = 0;
    for (const entry of bucket.tracks ?? []) {
      entry.p = isPlayable(entry.t) ? 1 : 0;
      total++;
      if (!entry.p) continue;
      count++;
      tracks++;
      formats.set(format, (formats.get(format) ?? 0) + 1);
      const shard = shardOf(entry.t);
      let entries = shards.get(shard);
      if (!entries) { entries = []; shards.set(shard, entries); }
      entries.push([entry.t, format, author]);
    }
    records.push({ key: bucket.key, tracks: bucket.tracks });
    if (count === 0) continue;
    let authors = authorsByFormat.get(format);
    if (!authors) { authors = []; authorsByFormat.set(format, authors); }
    authors.push({ name: author, count });
  }

  const collator = new Intl.Collator(undefined, { sensitivity: 'base' });
  for (const [shard, entries] of shards) records.push({ key: key.titles(shard), entries });
  for (const [format, authors] of authorsByFormat) {
    authors.sort((a, b) => collator.compare(a.name, b.name));
    records.push({ key: key.authors(format), authors });
  }
  records.push({
    key: key.formats,
    formats: [...formats].map(([name, count]) => ({ name, count }))
      .sort((a, b) => collator.compare(a.name, b.name)),
  });

  // **The old derived records go first.** A format removed leaves a title shard and an author list
  // that this pass never rebuilds, and writing over the survivors would leave the rest standing --
  // tunes findable in search that Browse no longer lists.
  await catalogue.clear(`${source}:titles:`);
  await catalogue.clear(`${source}:authors:`);
  await catalogue.putAll(records, 2000);
  await catalogue.putAll([{ ...held, tracks, total, formats: formats.size, at: Date.now() }]);
  return tracks;
}

/**
 * Records which format list judged this index, so the judging is not repeated every time.
 *
 * Separate from `refreshPlayable` because the two answer to different things: that one rebuilds
 * what is offered, this one remembers that it was rebuilt.
 */
export async function stampIndex(source = MODLAND, fingerprint = '') {
  const held = await catalogue.get(keyFor(source).meta);
  if (!held) return;
  await catalogue.putAll([{ ...held, fingerprint }]);
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
 * against the same ones. `!!uu !! !!.it` is in there because Modland really contains it.
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


// --- HVSC's song lengths --------------------------------------------------------------------------
//
// The same lengths the phone uses (`docs/STATUS.md` C56). A SID carries no duration, so without
// them the page plays one until somebody presses next. The fallback length stays underneath: HVSC
// only knows about the C64, and every other format nobody has measured still needs an answer.
//
// Fetched from the same address the phone uses. **Check CORS with a GET, never a HEAD**: this
// server answers HEAD without its CORS filter, so a HEAD says there is no
// `Access-Control-Allow-Origin` when a GET sends one.

const SONG_LENGTHS_URL = 'https://hvsc.c64.org/download/C64Music/DOCUMENTS/Songlengths.md5';

/** Everything song lengths store lives under, so one `clear` takes all of it. */
const LENGTHS = 'lengths:';

/**
 * 61,157 tunes in 256 rows rather than 61,157 rows.
 *
 * Sharded on the first two characters of the MD5, which is as even a split as exists -- a hash is
 * uniform by construction, so every shard holds about 240 entries. One row per tune would be a
 * quarter of a million IndexedDB writes to store and a quarter of a million rows to delete; one row
 * per *tune* is also the wrong unit for reading, because a lookup wants one entry and would pay a
 * request for it either way.
 */
const shardOfMd5 = (key) => `${LENGTHS}${key.slice(0, 2)}`;

/** What the page knows about the stored database: how many tunes, and when it was fetched. */
export async function songLengthsMeta() { return catalogue.get(`${LENGTHS}meta`); }

/** Forgets them all. The storage section offers this, as the phone's does. */
export async function clearSongLengths() {
  await catalogue.clear(LENGTHS);
}

/**
 * Fetches HVSC's database and stores it.
 *
 * Progress is reported by bytes while fetching, because it is 5.2 MB of text and a page that says
 * nothing for ten seconds reads as a page that has stopped. Parsing is another 100 ms and storing a
 * few hundred, so only the fetch is worth counting.
 */
export async function downloadSongLengths({ onProgress = null } = {}) {
  onProgress?.({ stage: 'fetching', done: 0, total: 0 });
  const response = await fetch(SONG_LENGTHS_URL);
  if (!response.ok) throw new Error(`HTTP ${response.status}`);

  const total = Number(response.headers.get('content-length')) || 0;
  let text;
  if (response.body) {
    const reader = response.body.getReader();
    const chunks = [];
    let done = 0;
    for (;;) {
      const read = await reader.read();
      if (read.done) break;
      chunks.push(read.value);
      done += read.value.length;
      onProgress?.({ stage: 'fetching', done, total });
    }
    const joined = new Uint8Array(done);
    let at = 0;
    for (const chunk of chunks) { joined.set(chunk, at); at += chunk.length; }
    // **Latin-1, not UTF-8.** The file is a list of hex keys and digits, but the comment lines above
    // them carry composer names with accents in whatever eight-bit encoding HVSC has always used.
    // Decoding as UTF-8 throws on those bytes in strict mode and replaces them otherwise; either
    // way the comments are not read, and the entries must not be risked for them.
    text = new TextDecoder('latin1').decode(joined);
  } else {
    text = new TextDecoder('latin1').decode(new Uint8Array(await response.arrayBuffer()));
  }

  onProgress?.({ stage: 'reading' });
  const entries = parseSongLengths(text);
  if (entries.length === 0) throw new Error('the song length database was empty');

  const shards = new Map();
  for (const entry of entries) {
    const key = shardOfMd5(entry.md5);
    let held = shards.get(key);
    if (!held) { held = {}; shards.set(key, held); }
    held[entry.md5] = entry.seconds;
  }

  // **Replaced wholesale, not merged**, the same rule the phone keeps: HVSC publishes corrections
  // as well as additions, so a merge would keep a length its own publisher has withdrawn.
  await catalogue.clear(LENGTHS);
  const records = [...shards].map(([key, tunes]) => ({ key, tunes }));
  await catalogue.putAll(records, 64, (done, count) => onProgress?.({ stage: 'storing', done, total: count }));
  await catalogue.putAll([{ key: `${LENGTHS}meta`, tunes: entries.length, at: Date.now() }]);
  onProgress?.({ stage: 'done', tunes: entries.length });
  return { tunes: entries.length };
}

/**
 * Every subsong's length for these bytes, or null when HVSC has never heard of them.
 *
 * The key is the plain MD5 of the whole file — not the header hash libsidplayfp still exposes,
 * which older HVSC releases used and this one does not.
 */
export async function songLengthsFor(bytes) {
  if (!bytes || bytes.length === 0) return null;
  const key = md5(bytes instanceof Uint8Array ? bytes : new Uint8Array(bytes));
  const shard = await catalogue.get(shardOfMd5(key));
  const found = shard?.tunes?.[key];
  return Array.isArray(found) ? found : null;
}
