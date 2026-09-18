// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later
//
// Checks that no icon's shape depends on where the pen sits after a `z`.
//
// **SVG says** the current point after a close returns to the *start* of the subpath that was
// closed. **Some renderers leave it at the last point drawn.** The two agree for every circle and
// every loop that ends where it began -- which is most of an icon set -- and disagree the moment a
// subpath ends somewhere else.
//
// `SkipPrevious` was `M6 6h2v12H6zm3.5 6l8.5 6V6z`: the bar ends at (6,18) and began at (6,6), so
// on a tester's phone the triangle after it landed twelve units down and drew a wedge. It was right
// on the owner's phone, on every emulator here, and in every browser. That is the worst kind of
// defect to hunt, which is why this walks the path rather than trusting the eye.
//
// It reads both players, because they draw the same marks from the same coordinates: the phone's
// `PlayerIcons.kt` and the page's own SVG.
//
//   node scripts/check-icons.mjs

import fs from 'fs';

const files = [
  'app/src/main/kotlin/com/przunk/protracktor/ui/PlayerIcons.kt',
  'web/src/app.js',
  'web/src/index.html',
];

const TOKEN = /([MmLlHhVvCcSsQqTtAaZz])|(-?\d*\.?\d+(?:e-?\d+)?)/g;
const ARGS = { M: 2, L: 2, T: 2, H: 1, V: 1, C: 6, S: 4, Q: 4, A: 7 };

/** Where each closed subpath began and where it ended, under the rule the spec states. */
function closes(d) {
  const toks = [...d.matchAll(TOKEN)].map((m) => m[1] ?? m[2]);
  let i = 0, cur = [0, 0], start = [0, 0], cmd = null;
  const out = [];
  while (i < toks.length) {
    if (/^[A-Za-z]$/.test(toks[i])) cmd = toks[i++];
    const nums = [];
    while (i < toks.length && !/^[A-Za-z]$/.test(toks[i])) nums.push(Number(toks[i++]));
    if (cmd === 'Z' || cmd === 'z') { out.push({ start, end: cur }); cur = start; continue; }
    const k = ARGS[cmd.toUpperCase()];
    for (let j = 0; j + k <= nums.length; j += k) {
      const a = nums.slice(j, j + k);
      const up = cmd.toUpperCase();
      const rel = cmd === cmd.toLowerCase();
      let x, y;
      if (up === 'H') { x = rel ? cur[0] + a[0] : a[0]; y = cur[1]; }
      else if (up === 'V') { x = cur[0]; y = rel ? cur[1] + a[0] : a[0]; }
      else if (up === 'M' || up === 'L' || up === 'T') { x = rel ? cur[0] + a[0] : a[0]; y = rel ? cur[1] + a[1] : a[1]; }
      else if (up === 'C') { x = rel ? cur[0] + a[4] : a[4]; y = rel ? cur[1] + a[5] : a[5]; }
      else if (up === 'S' || up === 'Q') { x = rel ? cur[0] + a[2] : a[2]; y = rel ? cur[1] + a[3] : a[3]; }
      else { x = rel ? cur[0] + a[5] : a[5]; y = rel ? cur[1] + a[6] : a[6]; }
      cur = [x, y];
      if (up === 'M') { start = cur; cmd = rel ? 'l' : 'L'; }
    }
  }
  return out;
}

/** The first `z` followed by a relative move where the two readings disagree, or null. */
function fragile(d) {
  for (const hit of d.matchAll(/[Zz]\s*m/g)) {
    const done = closes(d.slice(0, hit.index + 1));
    const last = done[done.length - 1];
    if (!last) continue;
    if (Math.abs(last.start[0] - last.end[0]) > 1e-9 || Math.abs(last.start[1] - last.end[1]) > 1e-9) {
      return last;
    }
  }
  return null;
}

let checked = 0;
let failed = 0;
for (const file of files) {
  // Paths are written in fragments joined by `+`, and one of the fragile ones hid its `z` at the
  // end of a fragment and its `m` at the start of the next.
  const joined = fs.readFileSync(file, 'utf8')
    .replace(/"\s*\+\s*\n?\s*"/g, '')
    .replace(/'\s*\+\s*\n?\s*'/g, '');
  for (const match of joined.matchAll(/["']([Mm][^"']{10,})["']/g)) {
    const d = match[1];
    if (!/[0-9]/.test(d) || !/[A-Za-z]\s*-?[\d.]/.test(d)) continue;
    checked++;
    const bad = fragile(d);
    if (bad) {
      failed++;
      console.log(`✗ ${file}: a subpath begins at (${bad.start}) and ends at (${bad.end}), and a`);
      console.log('  relative move follows its close — after `z`, move absolutely');
      console.log(`  ${d.slice(0, 96)}`);
    }
  }
}

if (failed > 0) {
  console.log(`\n❌ ${failed} of ${checked} icon paths depend on where the pen sits after \`z\``);
  process.exit(1);
}
console.log(`🎨 ${checked} icon paths checked`);
