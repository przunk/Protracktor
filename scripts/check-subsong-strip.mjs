// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later
import assert from 'node:assert/strict';
import { followSubsong, selectSubsong } from '../web/src/subsong-strip.js';

const follow = [
  ['opening at arbitrary current', true, false, false, true],
  ['opening realigns even an already visible current', true, false, true, true],
  ['advance past the watched region', false, true, false, true],
  ['advance inside the watched region', false, true, true, false],
  ['advance while reading a distant region', false, false, false, false],
  ['music reaches the distant visible region', false, false, true, false],
];
for (const [label, opening, previousVisible, currentVisible, expected] of follow) {
  assert.equal(followSubsong({ opening, previousVisible, currentVisible }), expected, label); console.log(`✓ native follow: ${label}`);
}
const select = [
  [69, 68, 256, true], [68, 68, 256, false], [-1, 68, 256, false], [256, 68, 256, false],
  [0, 255, 256, true], [255, 0, 256, true], [0, 0, 1, false], [NaN, 0, 3, false], [1.5, 0, 3, false],
];
for (const [index, current, count, expected] of select) {
  assert.equal(selectSubsong(index, current, count), expected, `select ${index} from ${current} of ${count}`);
  console.log(`✓ native controller select: ${index} from ${current} of ${count}`);
}
console.log(`${follow.length + select.length} native-derived subsong rules passed`);
