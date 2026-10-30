// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later
import assert from 'node:assert/strict';
import fs from 'node:fs';
import { MUSIC_CACHE_CEILING, cacheEvictions, totalCacheBytes, freedCacheBytes } from '../web/src/cache-budget.js';
function rows(file, header) {
  const lines = fs.readFileSync(`docs/rules/${file}.tsv`, 'utf8').trim().split('\n');
  const count = Number(lines.find((line) => line.startsWith('# cases: ')).split(': ')[1]);
  const data = lines.filter((line) => line && !line.startsWith('#'));
  assert.equal(data.shift(), header); assert(count > 0); assert.equal(data.length, count, 'A shared case is missing');
  return data.map((line) => line.split('\t'));
}
const names = (raw) => raw === '-' ? [] : raw.split('|').map(decodeURIComponent);
const budgets = rows('cache-budget-cases', 'case\tentries\tinUse\tceiling\tevicted\ttotal');
assert.equal(new Set(budgets.map((row) => row[0])).size, budgets.length);
for (const [name, input, used, limit, removed, total] of budgets) {
  const entries = input === '-' ? [] : input.split('|').map((row) => {
    const [name,size,stamp] = row.split(','); return { name: decodeURIComponent(name), sizeBytes: Number(size), lastUsedMillis: Number(stamp) };
  });
  assert.equal(totalCacheBytes(entries), Number(total), name);
  assert.deepEqual(cacheEvictions(entries, new Set(names(used)), limit === 'default' ? MUSIC_CACHE_CEILING : Number(limit)), names(removed), name);
  console.log(`✓ shared cache budget: ${name}`);
}
const freed = rows('cache-freed-cases', 'bytes\tkind\tcount');
for (const [bytes,kind,count] of freed) {
  assert.deepEqual(freedCacheBytes(Number(bytes)), kind === 'megabytes' ? { kind, count: Number(count) } : { kind });
  console.log(`✓ shared freed quantity: ${bytes}`);
}
assert.equal(MUSIC_CACHE_CEILING, 512 * 1024 * 1024);
console.log(`${budgets.length} budget and ${freed.length} deletion cases shared with native CacheBudget`);
