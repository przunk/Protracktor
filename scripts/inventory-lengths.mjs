// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later
//
// How many of Modland's tunes have no length from any source -- `docs/WISHLIST.md` B36, step 1.
//
// **Counted where it can be, sampled where it must be, and said which is which.** The index gives
// exact counts per format directory. Whether a file has a length depends on its decoder, and for
// half of them on the file itself, so those are sampled: a seeded random handful per directory is
// fetched and asked, the way the app asks -- the engine the page runs (the phone's own backends,
// built to WebAssembly), then HVSC by the full MD5 for a SID, then songdb by the first twelve
// characters for everything else (`LengthSource`). UADE is the one decoder the browser's engine
// cannot have, so a UADE format is asked of songdb only; what UADE measures by playing a tune once
// (A50) is counted separately, as "learnt by playing", not as known.
//
// Everything fetched is cached under ~/.protracktor/inventory, so a second run asks Modland for
// nothing it has already given, and the fetching is three at a time.
//
//   node scripts/inventory-lengths.mjs [--sample N] [--seed N] [--nsf N] [--spc N] [--only Format,Format]
//                                      [--zxtune native/probe/zxtune/build/probe-zxtune]
//
// **ZXTune through its probe.** The page's engine has no ZXTune, so its formats are asked of the
// host probe (`scripts/build-zxtune-probe.sh`), when one is given: it opens a file with ZXTune's own
// players and prints the length they compute. The probe is built from its own checkout of ZXTune,
// not from `native/vendor/zxtune`, and carries the AY-family players -- a file it refuses is said
// to be refused by the probe, not by the app. Modland files every Spectrum tracker under one
// directory, so Spectrum is sampled per tracker, one level down.
//
// Writes docs/inventory/lengths.tsv (one row per format directory) and prints the totals.

import fs from 'fs';
import os from 'os';
import path from 'path';
import zlib from 'zlib';
import crypto from 'crypto';
import { execFileSync } from 'child_process';

globalThis.sampleRate ??= 48000;
globalThis.currentFrame ??= 0;
globalThis.currentTime ??= 0;

const args = Object.fromEntries(process.argv.slice(2).reduce((pairs, a, i, all) => {
  if (a.startsWith('--')) pairs.push([a.slice(2), all[i + 1] && !all[i + 1].startsWith('--') ? all[i + 1] : 'yes']);
  return pairs;
}, []));
const SAMPLE = Number(args.sample ?? 20);
const NSF_SAMPLE = Number(args.nsf ?? 150);
const SPC_SAMPLE = Number(args.spc ?? 300);
const ZXTUNE = args.zxtune ? path.resolve(args.zxtune) : null;
const SEED = Number(args.seed ?? 1);
const ONLY = args.only ? new Set(args.only.split(',')) : null;

const CACHE = path.join(os.homedir(), '.protracktor', 'inventory');
fs.mkdirSync(path.join(CACHE, 'files'), { recursive: true });

const INDEX_URL = 'https://modland.com/allmods.zip';
const FILES = 'https://modland.com/pub/modules/';
const HVSC_URL = 'https://www.hvsc.c64.org/download/C64Music/DOCUMENTS/Songlengths.md5';
const SONGDB_URL = 'https://raw.githubusercontent.com/mvtiaine/audacious-uade-tools/1bad3e8/tsv/pretty/md5/songlengths.tsv';

/** A URL's bytes, from the cache when they are there. Null when the server would not give them. */
async function fetchCached(url, name = crypto.createHash('sha256').update(url).digest('hex')) {
  const file = path.join(CACHE, name.includes('/') ? name : path.join('files', name));
  if (fs.existsSync(file)) return fs.readFileSync(file);
  for (let attempt = 0; attempt < 3; attempt++) {
    try {
      const response = await fetch(url);
      if (response.status === 404) return null;
      if (!response.ok) throw new Error(`HTTP ${response.status}`);
      const bytes = Buffer.from(await response.arrayBuffer());
      fs.mkdirSync(path.dirname(file), { recursive: true });
      fs.writeFileSync(file, bytes);
      return bytes;
    } catch (error) {
      if (attempt === 2) { console.warn(`  ! ${url}: ${error.message}`); return null; }
      await new Promise((r) => setTimeout(r, 1500 * (attempt + 1)));
    }
  }
  return null;
}

/** The first member of a zip, inflated -- Modland's index is one file, `allmods.txt`. */
function firstMember(zip) {
  if (zip.readUInt32LE(0) !== 0x04034b50) throw new Error('not a zip');
  const method = zip.readUInt16LE(8);
  const compressed = zip.readUInt32LE(18);
  const start = 30 + zip.readUInt16LE(26) + zip.readUInt16LE(28);
  const body = zip.subarray(start, compressed ? start + compressed : undefined);
  return method === 0 ? body : zlib.inflateRawSync(body);
}

// --- what the app plays, by name: `web/src/formats.tsv`, the list both runtimes index by ------------
const table = { extensions: new Map(), prefixes: new Map() };
for (const raw of fs.readFileSync('web/src/formats.tsv', 'utf8').split('\n')) {
  const line = raw.trim();
  if (!line || line.startsWith('#')) continue;
  const [kind, name, decoders] = line.split('\t');
  if (kind === 'directory') continue;   // C88's refused directories: judged here by what opens
  if (name && decoders) (kind === 'prefix' ? table.prefixes : table.extensions).set(name, decoders.split(','));
}
/** The decoders for a file name, or null: the extension, or failing that the part before the first dot. */
function decodersOf(file) {
  const lower = file.toLowerCase();
  const dot = lower.lastIndexOf('.');
  const extension = dot >= 0 ? lower.slice(dot + 1) : '';
  if (table.extensions.has(extension)) return table.extensions.get(extension);
  const first = lower.indexOf('.');
  const prefix = first > 0 ? lower.slice(0, first) : '';
  return table.prefixes.get(prefix) ?? null;
}

// --- a seeded shuffle, so a run can be repeated and a number can be checked ----------------------
function rng(seed) {
  let s = seed >>> 0 || 1;
  return () => { s ^= s << 13; s >>>= 0; s ^= s >> 17; s ^= s << 5; s >>>= 0; return s / 4294967296; };
}
function sample(list, n, random) {
  const copy = list.slice();
  for (let i = copy.length - 1; i > 0; i--) {
    const j = Math.floor(random() * (i + 1));
    [copy[i], copy[j]] = [copy[j], copy[i]];
  }
  return copy.slice(0, n);
}

// --- the databases -----------------------------------------------------------------------------------
console.log('Reading the databases…');
const hvsc = new Map();
for (const line of (await fetchCached(HVSC_URL, 'Songlengths.md5'))?.toString('latin1').split('\n') ?? []) {
  const m = line.trim().match(/^([0-9a-fA-F]{32})=(.+)$/);
  if (m) hvsc.set(m[1].toLowerCase(), m[2].trim().split(/\s+/));
}
const LENGTH_ENDS = new Set(['p', 'p+s', 'p+v', 's', 'l', 'l+s', 'l+v', 'r', 'v']);
const songdb = new Map();
for (const line of (await fetchCached(SONGDB_URL, 'songlengths.tsv'))?.toString('utf8').split('\n') ?? []) {
  const [key, , subsongs] = line.split('\t');
  if (!key || !subsongs) continue;
  // Known when the first subsong listed is a length the app would use (`SongDbLengths.End.isLength`).
  const [millis, end] = subsongs.trim().split(' ')[0].split(',');
  songdb.set(key.trim().toLowerCase(), Number(millis) > 0 && LENGTH_ENDS.has(end));
}
console.log(`  HVSC: ${hvsc.size.toLocaleString()} files, songdb: ${songdb.size.toLocaleString()} files`);

// --- the engine the page runs: the phone's backends but UADE, built to WebAssembly -----------------
const engineFile = path.resolve('web/vendor/engine.mjs');
if (!fs.existsSync(engineFile)) { console.error('no engine: run scripts/build-web-engine.sh'); process.exit(2); }
const M = await (await import(engineFile)).default();
const fingerprint = M.UTF8ToString(M._pt_backends());
const absent = new Set(fingerprint.split(';').map((p) => p.split(':')).filter(([, v]) => v === 'none').map(([n]) => n));
/**
 * Whether this engine can answer for [decoder]. HivelyTracker is in every build and never in the
 * fingerprint; any other decoder must be named there and not as `none`. ZXTune is missing from the
 * page's build, so its formats are **not measured here** and said so, not counted as unknown.
 */
const here = (decoder) => decoder === 'hively' || (fingerprint.includes(`${decoder}:`) && !absent.has(decoder))
  || (decoder === 'zxtune' && ZXTUNE !== null);

/** What the ZXTune probe says of a file, in the shape `ask` answers. */
function askZxtune(bytes, name) {
  const file = path.join(os.tmpdir(), `inventory-${process.pid}-${name.replace(/[^\w.-]/g, '_')}`);
  fs.writeFileSync(file, bytes);
  try {
    const out = execFileSync(ZXTUNE, [file], { encoding: 'utf8', timeout: 60_000, stdio: ['ignore', 'pipe', 'ignore'] });
    const verdict = out.split('\n').reverse().find((l) => l.startsWith('VERDICT')) ?? '';
    if (/VERDICT reject/.test(verdict)) return { opened: false, error: verdict };
    const len = Number((verdict.match(/len=(\d+)s/) ?? [])[1] ?? 0);
    return { opened: true, seconds: len, endsAt: false };
  } catch (error) {
    return { opened: false, error: String(error.message).slice(0, 120) };
  } finally {
    fs.rmSync(file, { force: true });
  }
}

/** What the engine says of a file: opened or not, its length, and whether it ends by itself. */
function ask(bytes, name) {
  const buf = M._malloc(bytes.length);
  M.HEAPU8.set(bytes, buf);
  const nameBytes = Buffer.from(name + '\0', 'utf8');
  const namePtr = M._malloc(nameBytes.length);
  M.HEAPU8.set(nameBytes, namePtr);
  const handle = M._pt_open(buf, bytes.length, namePtr);
  M._free(buf);
  M._free(namePtr);
  if (!handle) return { opened: false, error: M.UTF8ToString(M._pt_last_error()) };
  const seconds = M._pt_duration(handle);
  const describe = M.UTF8ToString(M._pt_describe(handle));
  M._pt_close(handle);
  return { opened: true, seconds, endsAt: /(^|\n)ends_at\t/.test(describe) };
}

// --- the index ---------------------------------------------------------------------------------------
console.log('Reading Modland\'s index…');
const text = firstMember(await fetchCached(INDEX_URL, 'allmods.zip')).toString('latin1');
const byFormat = new Map();
let rows = 0;
for (const line of text.split('\n')) {
  const tab = line.indexOf('\t');
  if (tab <= 0) continue;
  const file = line.slice(tab + 1).trim();
  const parts = file.split('/');
  if (parts.length < 2) continue;
  rows++;
  const decoders = decodersOf(parts[parts.length - 1]);
  if (!decoders) continue;
  // Spectrum holds every ZX tracker under one directory; each tracker is its own format.
  const format = parts[0] === 'Spectrum' && parts.length > 2 ? `Spectrum/${parts[1]}` : parts[0];
  if (ONLY && !ONLY.has(format) && !ONLY.has(parts[0])) continue;
  if (!byFormat.has(format)) byFormat.set(format, { files: [], decoders: new Map() });
  const group = byFormat.get(format);
  group.files.push(file);
  const decoder = decoders[0];
  group.decoders.set(decoder, (group.decoders.get(decoder) ?? 0) + 1);
}
const playable = [...byFormat.values()].reduce((n, g) => n + g.files.length, 0);
console.log(`  ${rows.toLocaleString()} rows, ${playable.toLocaleString()} the app plays, in ${byFormat.size} format directories`);

// --- per directory: sample, fetch, ask ------------------------------------------------------------
const random = rng(SEED);
const results = [];
let fds = { nsf: 0, low: 0 };
const formats = [...byFormat.entries()].sort((a, b) => b[1].files.length - a[1].files.length);
let done = 0;
for (const [format, group] of formats) {
  done++;
  const decoder = [...group.decoders.entries()].sort((a, b) => b[1] - a[1])[0][0];
  if (decoder !== 'uade' && !here(decoder)) {
    results.push({ format, decoder, files: group.files.length, unmeasured: true });
    process.stdout.write(`\r  ${done}/${formats.length} ${format.slice(0, 40).padEnd(40)}`);
    continue;
  }
  const n = format === 'Nintendo Sound Format' ? NSF_SAMPLE : format === 'Nintendo SPC' ? SPC_SAMPLE : SAMPLE;
  const picks = sample(group.files, n, random);
  const tally = { asked: 0, file: 0, hvsc: 0, songdb: 0, none: 0, refused: 0, missing: 0 };
  const lengths = [];
  const queue = picks.slice();
  const worker = async () => {
    for (let pick = queue.shift(); pick; pick = queue.shift()) {
      const url = FILES + pick.split('/').map(encodeURIComponent).join('/');
      const bytes = await fetchCached(url);
      if (!bytes) { tally.missing++; continue; }
      tally.asked++;
      const md5 = crypto.createHash('md5').update(bytes).digest('hex');
      if (format === 'Nintendo Sound Format' && bytes.length >= 128 && bytes.toString('latin1', 0, 5) === 'NESM\x1a') {
        fds.nsf++;
        const load = bytes.readUInt16LE(8);
        if ((bytes[123] & 0x04) && load && load < 0x8000) fds.low++;
      }
      // The app's order (`LengthSource`): the file through its backend, then HVSC, then songdb.
      const answer = decoder === 'uade' ? null
        : decoder === 'zxtune' ? askZxtune(bytes, pick.split('/').pop()) : ask(bytes, pick.split('/').pop());
      if (answer && !answer.opened) { tally.refused++; continue; }
      if (answer && answer.seconds > 0 && !answer.endsAt) { tally.file++; lengths.push(Math.round(answer.seconds)); continue; }
      if (hvsc.has(md5)) { tally.hvsc++; continue; }
      if (songdb.get(md5.slice(0, 12))) { tally.songdb++; continue; }
      tally.none++;
    }
  };
  await Promise.all([worker(), worker(), worker()]);
  const heard = tally.asked - tally.refused;
  const knownShare = heard > 0 ? (tally.file + tally.hvsc + tally.songdb) / heard : 0;
  // The commonest length the engine gave, and how often: a decoder that reports a default when it
  // does not know would show here as one number far too often -- a length that is not one.
  const counts = new Map();
  for (const l of lengths) counts.set(l, (counts.get(l) ?? 0) + 1);
  const [common, times] = [...counts.entries()].sort((a, b) => b[1] - a[1])[0] ?? [0, 0];
  results.push({ format, decoder, files: group.files.length, ...tally, knownShare, common, times });
  process.stdout.write(`\r  ${done}/${formats.length} ${format.slice(0, 40).padEnd(40)}`);
}
process.stdout.write('\n');

// --- written down ------------------------------------------------------------------------------------
fs.mkdirSync('docs/inventory', { recursive: true });
const header = ['format', 'decoder', 'files', 'sampled', 'refused', 'from_file', 'from_hvsc', 'from_songdb', 'no_length', 'est_files_without_length', 'commonest_file_length_s', 'times'];
const lines = [header.join('\t')];
let estimate = 0;
const byDecoder = new Map();
const unmeasured = new Map();
const refusedWhole = new Map();
for (const r of results) {
  if (r.unmeasured) {
    unmeasured.set(r.decoder, (unmeasured.get(r.decoder) ?? 0) + r.files);
    lines.push([r.format, r.decoder, r.files, 'not measured here', '', '', '', '', '', '', '', ''].join('\t'));
    continue;
  }
  const heard = r.asked - r.refused;
  // **A directory this engine opened nothing of is not a directory with no lengths.** The first run
  // counted it so, and put 3,800 files -- Deflemask, FamiTracker, SidMon 1 -- among the unknown when
  // not one had been heard. Refused is its own answer, listed apart.
  if (heard === 0) {
    refusedWhole.set(r.decoder, (refusedWhole.get(r.decoder) ?? 0) + r.files);
    lines.push([r.format, r.decoder, r.files, r.asked, r.refused, r.file, r.hvsc, r.songdb, r.none, 'all refused', '', ''].join('\t'));
    continue;
  }
  const without = Math.round(r.files * (r.none / heard));
  estimate += without;
  const d = byDecoder.get(r.decoder) ?? { files: 0, without: 0, heard: 0, none: 0 };
  d.files += r.files; d.without += without; d.heard += heard; d.none += r.none;
  byDecoder.set(r.decoder, d);
  lines.push([r.format, r.decoder, r.files, r.asked, r.refused, r.file, r.hvsc, r.songdb, r.none, without, r.common, r.times].join('\t'));
}
fs.writeFileSync('docs/inventory/lengths.tsv',
  `# B36 step 1: Modland's tunes with no length from any source. Seed ${SEED}, ${SAMPLE} per directory (${NSF_SAMPLE} for NSF), ` +
  `index and databases as fetched ${new Date().toISOString().slice(0, 10)}. Engine: ${fingerprint}` +
  (ZXTUNE ? `; ZXTune via ${path.relative(process.cwd(), ZXTUNE)}` : '') + `; SPC ${SPC_SAMPLE}\n` + lines.join('\n') + '\n');

console.log('\nBy decoder (estimated files with no length before playing):');
for (const [decoder, d] of [...byDecoder.entries()].sort((a, b) => b[1].without - a[1].without)) {
  console.log(`  ${decoder.padEnd(10)} ${String(d.without).padStart(8)} of ${String(d.files).padStart(8)}   (${d.none} of ${d.heard} sampled)`);
}
const measuredFiles = playable - [...unmeasured.values(), ...refusedWhole.values()].reduce((a, b) => a + b, 0);
console.log(`\n  total      ${String(estimate).padStart(8)} of ${String(measuredFiles).padStart(8)} measured`);
for (const [decoder, files] of unmeasured) console.log(`  not measured here: ${decoder}, ${files.toLocaleString()} files (not in this engine)`);
for (const [decoder, files] of refusedWhole) console.log(`  every sampled file refused: ${decoder}, ${files.toLocaleString()} files (directories this engine opened nothing of)`);
console.log(`\nNSF sampled: ${fds.nsf}; Famicom Disk System loading below $8000: ${fds.low}`);
console.log('Written: docs/inventory/lengths.tsv');
