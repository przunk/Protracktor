// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later
import fs from 'node:fs/promises';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { execFileSync } from 'node:child_process';
import { createHash } from 'node:crypto';

const packages = [
  ['pool/main/n/nspr/libnspr4_4.35-1.1build1_amd64.deb', 'e579e72d091f6c7a13f5a756c31065b15aae5b81840d61b069355aa2283c07b4'],
  ['pool/main/n/nss/libnss3_3.98-1build1_amd64.deb', '88247fe0db5cd4c273b7dd026d9ded4ff9ba828b62437d12a2f1c2abc29468d2'],
  ['pool/main/a/alsa-lib/libasound2t64_1.2.11-1ubuntu0.3_amd64.deb', '5d17bfb5683eb99f1be951fe48e1be5bf3c52391913b6b74a8aecf9b4f5e779f'],
];
const sha = bytes => createHash('sha256').update(bytes).digest('hex');

export function browserEnv(root) {
  return { ...process.env, PLAYWRIGHT_BROWSERS_PATH: path.join(root, 'user/web-visual/browsers'),
    LD_LIBRARY_PATH: [path.join(root, 'user/web-visual/linux-libs/root/usr/lib/x86_64-linux-gnu'),
      process.env.LD_LIBRARY_PATH].filter(Boolean).join(path.delimiter) };
}

/** Ubuntu's debs are unpacked as private files; no dpkg registration or system writes. */
async function install(root) {
  if (process.platform !== 'linux' || process.arch !== 'x64') return;
  const registry = JSON.parse(await fs.readFile(new URL('./node_modules/playwright-core/browsers.json', import.meta.url)));
  const revision = registry.browsers.find(browser => browser.name === 'chromium-headless-shell').revision;
  const executable = path.join(root, `user/web-visual/browsers/chromium_headless_shell-${revision}/chrome-headless-shell-linux64/chrome-headless-shell`);
  const missing = () => execFileSync('ldd', [executable], { encoding: 'utf8', env: browserEnv(root) }).includes('not found');
  if (!missing()) { console.log('Chromium shared libraries are available.'); return; }
  const release = await fs.readFile('/etc/os-release', 'utf8');
  if (!/^ID=ubuntu$/m.test(release) || !/^VERSION_ID="24\.04"$/m.test(release)) {
    throw new Error('Missing Chromium libraries. Automatic project-local extraction supports Ubuntu 24.04 x64 only; no system packages were installed.');
  }
  const dir = path.join(root, 'user/web-visual/linux-libs');
  await fs.mkdir(path.join(dir, 'packages'), { recursive: true });
  const records = [];
  for (const [filename, expected] of packages) {
    const url = `https://archive.ubuntu.com/ubuntu/${filename}`, target = path.join(dir, 'packages', path.basename(filename));
    let bytes = await fs.readFile(target).catch(() => null);
    if (!bytes || sha(bytes) !== expected) {
      const response = await fetch(url, { signal: AbortSignal.timeout(60000) });
      if (!response.ok) throw new Error(`${url}: HTTP ${response.status}`);
      bytes = Buffer.from(await response.arrayBuffer());
      if (sha(bytes) !== expected) throw new Error(`Library checksum mismatch: ${filename}`);
      await fs.writeFile(target, bytes);
    }
    execFileSync('dpkg-deb', ['-x', target, path.join(dir, 'root')]);
    records.push({ url, sha256: expected });
  }
  await fs.writeFile(path.join(dir, 'receipt.json'), JSON.stringify(records, null, 2) + '\n');
  if (missing()) throw new Error('Chromium still has missing shared libraries; inspect ldd output. No system packages were installed.');
  console.log('Chromium libraries unpacked under user/web-visual/linux-libs/.');
}

if (process.argv[1] && path.resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '../..');
  await install(root);
}
