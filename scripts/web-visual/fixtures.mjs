// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later
import { stereoFixture } from '../fixtures/stereo.mjs';

export const profiles = [
  { name: 'desktop-en-dark', width: 1440, height: 960, language: 'en', theme: 'dark', touch: false },
  { name: 'desktop-pl-light', width: 1280, height: 800, language: 'pl', theme: 'light', touch: false },
  { name: 'phone-en-dark', width: 412, height: 915, language: 'en', theme: 'dark', touch: true },
  { name: 'phone-pl-light', width: 360, height: 800, language: 'pl', theme: 'light', touch: true },
  { name: 'compact-en-light', width: 320, height: 640, language: 'en', theme: 'light', touch: true },
];

const rows = [
  ['Nintendo Sound Format', 'Visual Demo', 'five tunes.nsf', 'Five demo tunes'],
  ['Protracker', 'Visual Demo', 'evening.mod', 'Evening demo'],
  ['Protracker', 'Visual Demo', 'a very long filename to inspect narrow screens and text truncation.mod',
    'A long demonstration title to inspect truncation and action spacing'],
  ['Protracker', 'Visual Demo', 'zolc.mod', 'Zażółć gęślą jaźń'],
  ...Array.from({ length: 14 }, (_, n) => ['Protracker', 'Visual Demo', `demo ${n + 5}.mod`, `Demo ${n + 5}`]),
];
export const tracks = rows.map(([format, author, file, name]) => ({
  url: `https://modland.com/pub/modules/${[format, author, file].map(encodeURIComponent).join('/')}`,
  file, name, size: file.endsWith('.nsf') ? 384 : 2172,
}));
export const indexText = rows.map(([format, author, file]) => `2172\t${format}/${author}/${file}`).join('\n');

/** A real five-subsong NSF; returning CPU routines deliberately produce no sound. */
export function demoNsf() {
  const bytes = Buffer.alloc(384);
  bytes.write('NESM\x1a', 0, 'latin1');
  bytes[5] = 1; bytes[6] = 5; bytes[7] = 1;
  bytes.writeUInt16LE(0x8000, 8); bytes.writeUInt16LE(0x8000, 10); bytes.writeUInt16LE(0x8003, 12);
  bytes.write('Five demo tunes', 14); bytes.write('Visual Demo', 46); bytes.write('2026', 78);
  bytes.writeUInt16LE(16666, 110); bytes[128] = 0x60; bytes[131] = 0x60;
  return bytes;
}
export function demoMod() {
  const bytes = stereoFixture('opposite');
  bytes.fill(0, 0, 20); bytes.write('Visual demo module', 0, 'latin1');
  return bytes;
}

/** Runs inside a fresh browser context and uses the production IndexedDB owners. */
export async function seed({ tracks, indexText }) {
  const { playlists, settings, catalogue, played, bookmarks } = await import('/src/store.js');
  const { toRecords } = await import('/src/catalogue.js');
  const { PLATFORM_FINGERPRINT } = await import('/src/random-scope.js');
  await playlists.save({ id: 'visual-demo', name: 'Visual review · demo playlist', tracks, index: 0 });
  await playlists.save({ id: 'visual-empty', name: 'Empty demo playlist', tracks: [], index: 0 });
  await settings.set('active', 'visual-demo');
  await settings.set('recentSearches', ['demo', 'evening', 'visual']);
  const built = toRecords(indexText);
  await catalogue.replacePrefix('modland:', [...built.records, { key: 'modland:meta',
    tracks: built.tracks, total: built.total, buckets: built.buckets, formats: built.formats,
    platformCounts: built.platformCounts, platformFingerprint: PLATFORM_FINGERPRINT,
    complete: true, fingerprint: '', at: Date.now() }]);
  for (let n = 0; n < 8; n++) await played.record({ ...tracks[n], replayable: true,
    meta: `Modland / ${n ? 'Protracker' : 'Nintendo Sound Format'} / Visual Demo`,
    author: 'Visual Demo' });
  await bookmarks.save({ key: { catalogueId: 'modland', format: 'Protracker', author: 'Visual Demo' },
    catalogueName: 'Modland', savedAt: Date.now() });
}
