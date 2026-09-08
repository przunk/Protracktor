// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later
//
// The host probe, for the WebAssembly engine (docs/PLAN_WEB.md §13 S1).
//
// **The same rule as every native backend in this project** (AGENTS.md §7): build it, run real
// files through it, count something. There is no browser here and there does not need to be -- a
// wasm decoder can be opened, rendered and measured in node, and what a browser adds is glitching
// in a real audio thread, which no amount of this would have found anyway.
//
//   node scripts/probe-web.mjs <file|dir> [...]
//
// Reports, per file: whether it opened, which decoder claimed it, the duration, whether the audio
// is audible rather than silent, and how far ahead of realtime it decodes.

import fs from 'fs';
import path from 'path';

// An AudioWorklet defines these; node does not, and the module reads them while loading.
globalThis.sampleRate ??= 48000;
globalThis.currentFrame ??= 0;
globalThis.currentTime ??= 0;

const SAMPLE_RATE = 48000;
const BLOCK = 4096;
const MAX_SECONDS = 20;

const args = process.argv.slice(2);
if (args.length === 0) {
  console.error('usage: node scripts/probe-web.mjs <file|dir> [...]');
  process.exit(2);
}

const engine = await import(path.resolve('web/vendor/engine.mjs'));
const M = await engine.default();

const cstr = (s) => {
  const bytes = Buffer.from(s + '\0', 'utf8');
  const p = M._malloc(bytes.length);
  M.HEAPU8.set(bytes, p);
  return p;
};

console.log('backends:', M.UTF8ToString(M._pt_backends()));
console.log();

const files = [];
for (const a of args) {
  const st = fs.statSync(a);
  if (st.isDirectory()) {
    for (const n of fs.readdirSync(a)) {
      const p = path.join(a, n);
      if (fs.statSync(p).isFile()) files.push(p);
    }
  } else files.push(a);
}

let played = 0, silent = 0, refused = 0;
const slowest = [];

for (const file of files.sort()) {
  const bytes = fs.readFileSync(file);
  const name = path.basename(file);
  const buf = M._malloc(bytes.length);
  M.HEAPU8.set(bytes, buf);
  const namePtr = cstr(name);
  const h = M._pt_open(buf, bytes.length, namePtr);
  M._free(buf);
  M._free(namePtr);

  if (!h) {
    refused++;
    console.log(`❌ ${name}\n   ${M.UTF8ToString(M._pt_last_error())}`);
    continue;
  }

  // `describe` is a tab-separated block of key/value lines, which is right for the app and
  // wrong for one line of a report.
  const describe = M.UTF8ToString(M._pt_describe(h)).replace(/[\t\n]+/g, ' ').trim();
  const duration = M._pt_duration(h);
  const subsongs = M._pt_subsong_count(h);

  const out = M._malloc(BLOCK * 2 * 4);
  let frames = 0, peak = 0;
  const t0 = process.hrtime.bigint();
  for (;;) {
    const n = M._pt_render(h, SAMPLE_RATE, BLOCK, out);
    if (n <= 0) break;
    frames += n;
    const view = new Float32Array(M.HEAPF32.buffer, out, n * 2);
    for (let i = 0; i < view.length; i += 53) {
      const v = Math.abs(view[i]);
      if (v > peak) peak = v;
    }
    if (frames >= SAMPLE_RATE * MAX_SECONDS) break;
  }
  const ms = Number(process.hrtime.bigint() - t0) / 1e6;
  M._free(out);

  const seconds = frames / SAMPLE_RATE;
  const speed = ms > 0 ? seconds / (ms / 1000) : Infinity;
  slowest.push([speed, name]);
  // Silence is the failure this catches that "it opened" does not: a decoder that loads a file and
  // renders zeros looks like success from every direction except the only one that matters.
  if (peak < 0.001) {
    silent++;
    // **Silence has two causes and they need different answers.** `GmeBackend` opens KSS and HES at
    // the first *audible* track because track 0 is routinely an empty slot, and it gives up after
    // 300 ms of searching -- a budget set against a phone emulating at fifty times realtime. If a
    // later subsong has sound, the file is fine and the budget ran out; if none does, the file is
    // silent and nothing here is wrong. Walking them says which, and only for files that failed.
    let audibleAt = -1;
    const count = M._pt_subsong_count(h);
    const probe = M._malloc(BLOCK * 2 * 4);
    for (let s = 1; s < Math.min(count, 256) && audibleAt < 0; s++) {
      if (!M._pt_select_subsong(h, s)) continue;
      let p2 = 0;
      for (let block = 0; block < 12; block++) {
        const n = M._pt_render(h, SAMPLE_RATE, BLOCK, probe);
        if (n <= 0) break;
        const v2 = new Float32Array(M.HEAPF32.buffer, probe, n * 2);
        for (let i = 0; i < v2.length; i += 53) { const v = Math.abs(v2[i]); if (v > p2) p2 = v; }
        if (p2 > 0.001) break;
      }
      if (p2 > 0.001) audibleAt = s;
    }
    M._free(probe);
    console.log(`🔇 ${name}  opened, decoded ${seconds.toFixed(1)}s, SILENT` +
      (audibleAt >= 0 ? `  — but subsong ${audibleAt} of ${count} has sound` : `  — and all ${count} subsongs are silent`));
  }
  else { played++; console.log(`✅ ${name}  ${describe.slice(0, 44)}  ${duration.toFixed(0)}s${subsongs > 1 ? ` ×${subsongs}` : ''}  peak ${peak.toFixed(2)}  ${speed.toFixed(0)}× realtime`); }
  M._pt_close(h);
}

slowest.sort((a, b) => a[0] - b[0]);
console.log(`\n${played} played, ${silent} silent, ${refused} refused, of ${files.length}`);
if (slowest.length) {
  console.log('slowest:', slowest.slice(0, 3).map(([s, n]) => `${n} ${s.toFixed(0)}×`).join(', '));
}
