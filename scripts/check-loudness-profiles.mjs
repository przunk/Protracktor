// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later
import assert from 'node:assert/strict';
import fs from 'node:fs';
import { webcrypto } from 'node:crypto';
import { loudnessProfile, nativeLoudnessProfile, measuredSubsong, improvesLoudness, encodeLoudness,
  decodeLoudness, loudnessRecord, recordProfile, mergedLoudnessRecords, loudnessContentKey, loudnessVariantKey } from '../web/src/loudness-profiles.js';
let count = 0;
async function test(name, run) { await run(); count++; console.log(`✓ ${name}`); }
function cases(name, header) {
  const text = fs.readFileSync(new URL(name, import.meta.url), 'utf8');
  const rows = text.split('\n').filter((line) => line && !line.startsWith('#'));
  assert.equal(rows.shift(), header); assert.equal(rows.length, Number(text.match(/^# cases: (\d+)$/m)?.[1]));
  assert(rows.length > 0); return rows.map((line) => line.split('\t'));
}
for (const [name, seconds, lufs, peak, complete, usable] of cases('../docs/rules/loudness-profile-cases.tsv', 'case\tseconds\tlufs\tpeak\tcomplete\tusable')) {
  await test(`Native profile validation: ${name}`, () => {
    const values = [seconds, lufs, peak, complete].map(Number), profile = nativeLoudnessProfile(values);
    assert.equal(!!profile, usable === '1');
    assert.equal(!!loudnessProfile({ seconds: values[0], lufs: values[1], peak: values[2], complete: values[3] === 1 }), usable === '1');
    if (profile) assert(Object.isFrozen(profile));
  });
}
for (const [name, seconds, lufs, peak, complete, payload] of cases('../docs/rules/loudness-encoding-cases.tsv', 'case\tseconds\tlufs\tpeak\tcomplete\tpayload')) {
  await test(`Native binary and CRC: ${name}`, () => {
    const profile = { seconds: Number(seconds), lufs: Number(lufs), peak: Number(peak), complete: complete === '1' };
    const bytes = encodeLoudness(profile);
    assert.equal(Buffer.from(bytes).toString('hex'), payload); assert.deepEqual(decodeLoudness(bytes), profile);
    const damaged = bytes.slice(); damaged[19] ^= 1; assert.equal(decodeLoudness(damaged), null);
    assert.equal(decodeLoudness(bytes.subarray(0, 36)), null);
  });
}
for (const [name, file, data, revision, companionText, subsong, width, base, key] of cases('../docs/rules/loudness-identity-cases.tsv', 'case\tname\tbytes\trevision\tcompanions\tsubsong\twidth\tbase\tkey')) {
  await test(`Native content and variant identity: ${name}`, async () => {
    const companions = companionText === '-' ? [] : companionText.split(';').map((row) => { const [n, b] = row.split('='); return [n, Buffer.from(b, 'hex')]; });
    assert.equal(await loudnessContentKey(Buffer.from(data, 'hex'), file, companions, revision, webcrypto), base);
    assert.equal(await loudnessVariantKey(base, Number(subsong), Number(width), webcrypto), key);
  });
}
const p = Object.freeze({ seconds: 20, lufs: -23, peak: .4, complete: false });
const key = (n) => n.toString(16).padStart(64, '0');
await test('complete beats partial and equal completeness prefers a longer observation only', () => {
  assert(!improvesLoudness(p, { ...p, seconds: 2 })); assert(!improvesLoudness(p, { ...p, lufs: -10 }));
  assert(improvesLoudness(p, { ...p, seconds: 2, complete: true }));
  assert(!improvesLoudness({ ...p, complete: true }, { ...p, seconds: 100 }));
  assert(improvesLoudness(p, { ...p, seconds: 21 }));
});
await test('cache has exactly512 writes, no read/inferior promotion, and immutable records', () => {
  let records = [];
  for (let n = 0; n < 513; n++) records = mergedLoudnessRecords(records, key(n), p);
  assert.equal(records.length, 512); assert(!records.some((row) => row.key === key(0)));
  assert.equal(mergedLoudnessRecords(records, key(1), p), null);
  records = mergedLoudnessRecords(records, key(1), { ...p, complete: true, seconds: 2 });
  assert.equal(records.at(-1).key, key(1)); assert(Object.isFrozen(records) && records.every(Object.isFrozen));
  records = mergedLoudnessRecords(records, key(513), p);
  assert(!records.some((row) => row.key === key(2))); assert(records.some((row) => row.key === key(1)));
});
await test('corrupt and invalid cache rows miss without mutating prior snapshots', () => {
  const original = loudnessRecord(key(1), p), before = JSON.stringify(original);
  const damaged = { ...original, payload: original.payload.slice(0, 20) + 'ff' + original.payload.slice(22) };
  assert.equal(recordProfile(damaged), null); assert.equal(recordProfile({ key: '../escape', payload: original.payload }), null);
  const next = mergedLoudnessRecords([original, damaged, null], key(2), p);
  assert.equal(next.length, 2); assert.equal(JSON.stringify(original), before);
  assert.equal(mergedLoudnessRecords(next, '../escape', p), null);
});
await test('invalid Native subsong facts never become a profile identity', () => {
  for (const n of [null, -1, 1.1, NaN, Infinity, 65536]) assert.equal(measuredSubsong(n), null);
  for (const n of [0, 7, 65535]) assert.equal(measuredSubsong(n), n);
});
await test('unavailable hashing refuses reusable identity without weakening keys', async () => {
  await assert.rejects(loudnessContentKey(Uint8Array.of(1), 'a.mod', [], 'decoder', {}));
  await assert.rejects(loudnessVariantKey('../escape', 0, 100, webcrypto));
  await assert.rejects(loudnessVariantKey(key(0), 0, 25, webcrypto));
});
console.log(`${count} shared loudness profile/cache/identity cases passed`);
