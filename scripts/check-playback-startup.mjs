// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later
import { spawnSync } from 'node:child_process';
let count=0;
for(const name of ['defaults','cache','empty','malformed','one','all','legacy','denied','delayed','delayed-failure','fragment']) {
  const flags=process.env.PROTRACKTOR_PAGE_MODULES==='1'?['--experimental-vm-modules']:[];
  const run=spawnSync(process.execPath,[...flags,'scripts/check-page.mjs'],{encoding:'utf8',timeout:30000,env:{...process.env,PROTRACKTOR_STARTUP_CASE:name}});
  if(run.error||run.status!==0) { console.error(run.stdout,run.stderr,run.error??''); throw new Error(`Fresh-page startup failed: ${name} (${run.status})`); }
  const checks=(run.stdout.match(/^  ✓ /gm)??[]).length;
  if(checks<15) throw new Error(`Fresh-page startup performed too few checks: ${name}`);
  count++; console.log(`✓ fresh page ${name}: ${checks} actual bootstrap assertions`);
}
console.log(`${count} fresh-page startup scenarios passed`);
