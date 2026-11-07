// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later
//
// Every startup case is a fresh page in a fresh process, so they share nothing and run several at
// once (2026-11-07): one after another they took about four and a half minutes of the gate.
// PROTRACKTOR_STARTUP_JOBS=1 restores the old order. Results are reported in the cases' own order.
import { spawn } from 'node:child_process';
import os from 'node:os';

const cases = ['normalization-on','normalization-off','normalization-malformed','normalization-denied','normalization-loading','stereo-narrow','stereo-mono','stereo-malformed','stereo-denied','stereo-loading','defaults','cache','asma','empty','malformed','one','all','legacy','denied','prefetch-always','prefetch-off','prefetch-denied','delayed','delayed-failure','fragment'];
const jobs = Math.max(1, Number(process.env.PROTRACKTOR_STARTUP_JOBS) || Math.min(6, Math.floor(os.cpus().length / 2) || 1));
const flags = process.env.PROTRACKTOR_PAGE_MODULES === '1' ? ['--experimental-vm-modules'] : [];

function run(name) {
  return new Promise((resolve) => {
    let stdout = '', stderr = '', done = false;
    const child = spawn(process.execPath, [...flags, 'scripts/check-page.mjs'],
      { env: { ...process.env, PROTRACKTOR_STARTUP_CASE: name } });
    // The same thirty seconds each case had alone; a busy machine is not a reason to wait for ever.
    const timer = setTimeout(() => { if (!done) child.kill('SIGKILL'); }, 30000);
    child.stdout.on('data', (chunk) => { stdout += chunk; });
    child.stderr.on('data', (chunk) => { stderr += chunk; });
    child.on('close', (status, signal) => { done = true; clearTimeout(timer); resolve({ name, status, signal, stdout, stderr }); });
    child.on('error', (error) => { done = true; clearTimeout(timer); resolve({ name, status: null, error, stdout, stderr }); });
  });
}

const results = new Array(cases.length);
let next = 0;
await Promise.all(Array.from({ length: Math.min(jobs, cases.length) }, async () => {
  while (next < cases.length) { const index = next++; results[index] = await run(cases[index]); }
}));

let count = 0;
for (const { name, status, signal, error, stdout, stderr } of results) {
  if (error || status !== 0) {
    console.error(stdout, stderr, error ?? '');
    throw new Error(`Fresh-page startup failed: ${name} (${status ?? signal})`);
  }
  const checks = (stdout.match(/^  ✓ /gm) ?? []).length;
  if (checks < 15) throw new Error(`Fresh-page startup performed too few checks: ${name}`);
  count++; console.log(`✓ fresh page ${name}: ${checks} actual bootstrap assertions`);
}
if (count !== cases.length) throw new Error('Not every fresh-page startup case reported');
console.log(`${count} fresh-page startup scenarios passed (${jobs} at a time)`);
