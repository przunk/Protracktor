#!/usr/bin/env node
// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later

import fs from 'node:fs';
import path from 'node:path';
import crypto from 'node:crypto';
import { fileURLToPath } from 'node:url';

const scriptDir = path.dirname(fileURLToPath(import.meta.url));
const template = fs.readFileSync(path.join(scriptDir, 'web-service-worker.js'), 'utf8');

/** Only shipped application files; never sweep vendor diagnostics or local downloads into cache. */
function filesBelow(root, directory) {
  return fs.readdirSync(path.join(root, directory), { withFileTypes: true }).flatMap((entry) => {
    const name = `${directory}/${entry.name}`;
    if (entry.isDirectory()) return filesBelow(root, name);
    return entry.isFile() ? [name] : [];
  });
}

/** Shared by the static-site packager and the local server, without a separate build tool. */
export function buildServiceWorker(root) {
  const files = [
    ...filesBelow(root, 'src'), ...filesBelow(root, 'lib'),
    'vendor/engine.mjs', 'vendor/engine.wasm',
    ...filesBelow(root, 'vendor/legal'), ...filesBelow(root, 'vendor/notices'),
  ].sort();
  const hash = crypto.createHash('sha256').update(template);
  for (const file of files) hash.update(file).update('\0').update(fs.readFileSync(path.join(root, file)));
  const revision = hash.digest('hex');
  const source = template.replace('__PWA_REVISION__', JSON.stringify(revision))
    .replace('__PWA_FILES__', JSON.stringify(files));
  return { source, revision, files };
}

if (process.argv[1] && path.resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  const root = path.resolve(scriptDir, '..', 'web');
  const { source, files } = buildServiceWorker(root);
  fs.writeFileSync(path.join(root, 'sw.js'), source);
  console.log(`📦 offline startup: ${files.length} application files`);
}
