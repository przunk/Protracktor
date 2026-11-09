// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later
import assert from 'node:assert/strict';
import { selectionLinks, uniqueNames } from '../web/src/selection-share.js';

let checks = 0;
const test = (name, run) => { try { run(); } catch (error) { console.error(`✗ ${name}`); throw error; } checks++; console.log(`✓ ${name}`); };
test('links one per line in selection order; a row from the phone is counted', () => {
  const links = selectionLinks([{ url: 'https://modland.com/a.mod' }, { local: true }, { url: 'https://modland.com/b.mod' }]);
  assert.deepEqual(links, { text: 'https://modland.com/a.mod\nhttps://modland.com/b.mod', shared: 2, without: 1 });
});
test('names are unique within one batch, the phone\'s rule', () => {
  assert.deepEqual(uniqueNames(['intro.mod', 'intro.mod', 'song.xm', 'intro.mod', 'Intro (2).mod']),
    ['intro.mod', 'intro (2).mod', 'song.xm', 'intro (3).mod', 'Intro (2) (2).mod']);
  assert.deepEqual(uniqueNames(['', 'tune']), ['tune', 'tune (2)']);
});
assert(checks > 0); console.log(`${checks} selection share checks passed`);
