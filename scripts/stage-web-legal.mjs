#!/usr/bin/env node
// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later
//
// Puts what the page must show about itself beside its engine (`docs/PLAN_WEB_PARITY.md` W4): the
// licence table and every file its `web` rows name, under `web/vendor/notices/<id>/`, and the
// privacy policy under `web/vendor/legal/`.
//
// **Serving the engine is conveying it.** libopenmpt's and HivelyTracker's BSD, {fmt}'s, zlib's and
// Emscripten's MIT ask for their notices to travel with the binary; the page carries a wasm binary,
// so it carries them too. The files are copied from where they came with the code, so the text a
// reader sees is the text that came with it -- as the app's build does for the APK.
//
// Run by `build-web-engine.sh`, after it has written the engine, and by `package-web.sh`, so an
// archive can never go out with notices older than its engine. Fails on a missing file: a notice
// the page names and cannot show is a notice it does not carry.
import fs from 'fs';
import path from 'path';
import { fileURLToPath } from 'url';
import { parseNotices } from '../web/src/rules.js';

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const out = path.join(root, 'web', 'vendor');
const tablePath = path.join(root, 'app', 'notices', 'components.tsv');
const table = fs.readFileSync(tablePath, 'utf8');

fs.rmSync(path.join(out, 'notices'), { recursive: true, force: true });
fs.mkdirSync(path.join(out, 'notices'), { recursive: true });
fs.copyFileSync(tablePath, path.join(out, 'notices', 'components.tsv'));

let copied = 0;
const missing = [];
for (const line of table.split('\n')) {
  if (!line.trim() || line.startsWith('#')) continue;
  const cells = line.split('\t');
  if (!(cells[5] ?? '').split(',').map((b) => b.trim()).includes('web')) continue;
  const id = cells[0].trim();
  for (const source of cells[4].split(',').map((f) => f.trim()).filter(Boolean)) {
    const from = path.join(root, source);
    if (!fs.existsSync(from)) { missing.push(source); continue; }
    const dir = path.join(out, 'notices', id);
    fs.mkdirSync(dir, { recursive: true });
    fs.copyFileSync(from, path.join(dir, path.basename(source)));
    copied++;
  }
}
if (missing.length) {
  console.error(`❌ licence files named for the page and not there:\n   ${missing.join('\n   ')}`);
  console.error('   The decoders\' files arrive with ./scripts/fetch-native-deps.sh.');
  process.exit(1);
}

fs.mkdirSync(path.join(out, 'legal'), { recursive: true });
fs.copyFileSync(path.join(root, 'store', 'privacy-policy.md'), path.join(out, 'legal', 'privacy-policy.md'));

// **Which source this page is.** The GPL's offer of source is for the version conveyed, so the
// page's Source code link points at the commit it was built from, when there is one to name.
let commit = '';
try {
  commit = (await import('child_process')).execSync('git rev-parse HEAD', { cwd: root }).toString().trim();
} catch { /* not a checkout: the link falls back to the repository */ }
fs.writeFileSync(path.join(out, 'legal', 'build.json'), JSON.stringify({ commit }) + '\n');

console.log(`📜 ${parseNotices(table, 'web').length} components, ${copied} licence files and the privacy policy beside the engine`);
