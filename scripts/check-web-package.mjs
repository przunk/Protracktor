// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later
import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';
import os from 'node:os';
import { execFileSync, spawnSync } from 'node:child_process';
import { createHash } from 'node:crypto';
import { fileURLToPath } from 'node:url';

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const fixture = fs.mkdtempSync(path.join(os.tmpdir(), 'protracktor-package-'));
const run = (command, args, cwd = fixture) => execFileSync(command, args, { cwd, encoding: 'utf8', stdio: ['ignore', 'pipe', 'pipe'] });
const write = (file, text) => {
  const target = path.join(fixture, file); fs.mkdirSync(path.dirname(target), { recursive: true }); fs.writeFileSync(target, text);
};
const copy = file => write(file, fs.readFileSync(path.join(root, file)));
const digest = file => createHash('sha256').update(fs.readFileSync(file)).digest('hex');
const unpack = archive => {
  const target = fs.mkdtempSync(path.join(fixture, 'unpacked-'));
  run('tar', ['-xzf', archive, '-C', target]);
  return path.join(target, 'protracktor-web');
};
const packageSite = () => {
  const output = run('bash', ['scripts/package-web.sh']);
  const archives = fs.readdirSync(path.join(fixture, 'dist')).filter(file => file.endsWith('.tar.gz'))
    .map(file => path.join(fixture, 'dist', file));
  assert(archives.length > 0, 'the packager produced an archive');
  const archive = archives.find(file => output.includes(file))
    ?? archives.find(file => output.includes(path.basename(file)));
  assert(archive, output);
  return { archive, output, hash: digest(archive) };
};

try {
  for (const file of ['scripts/package-web.sh', 'scripts/stage-web-legal.mjs', 'scripts/stage-web-pwa.mjs',
    'scripts/web-service-worker.js', 'scripts/serve-web.mjs', 'web/src/rules.js', 'web/src/query-text.js',
    'web/notices/qrcode-MIT.txt', 'web/lib/qrcode.js', 'LICENSE']) copy(file);
  write('web/package.json', '{"type":"module"}\n');
  write('web/src/index.html', '<!doctype html><title>Packaged player fixture</title>\n');
  write('web/vendor/engine.mjs', 'export default function Engine() {}\n');
  write('web/vendor/engine.wasm', Buffer.from([0, 97, 115, 109, 1, 0, 0, 0]));
  write('app/notices/components.tsv', 'qrcode\tQR generator\t2.0.4\tMIT\tweb/notices/qrcode-MIT.txt\tweb\n');
  write('app/notices/sources.tsv', '# Fixture music sources\n');
  write('store/privacy-policy.md', '# Fixture privacy policy\n');
  write('docs/WEB_SERVER.md', '# Packaged server instructions\n');
  write('.gitignore', 'web/vendor/\nweb/sw.js\nweb/node_modules/\nweb/*.tar.gz\ndist/\nunpacked-*/\n');
  write('web/node_modules/unused/fixture.js', 'must not be shipped');
  write('web/previous.tar.gz', 'must not be nested');
  write('server.json', '{"privateFixture":true}\n');
  run('git', ['init', '-q']);
  run('git', ['add', '.']);
  run('git', ['-c', 'user.name=Package fixture', '-c', 'user.email=fixture@example.invalid',
    '-c', 'commit.gpgSign=false', '-c', 'core.hooksPath=/dev/null', 'commit', '-qm', 'Fixture source']);
  const source = run('git', ['rev-parse', 'HEAD']).trim();
  const first = packageSite(), firstHash = first.hash;
  const file = path.join(fixture, 'web/src/index.html');
  fs.utimesSync(file, 123456789, 123456789); fs.chmodSync(file, 0o600);
  const second = packageSite();
  assert.equal(second.hash, firstHash, 'same source bytes must package identically despite mtime/mode and staging time');
  assert.notEqual(first.archive, second.archive, 'two builds must not overwrite the first archive');
  console.log('  ✓ two actual packages have identical bytes and distinct source-labelled filenames');

  const files = run('tar', ['-tzf', second.archive]).trim().split('\n');
  for (const required of ['run.sh', 'scripts/serve-web.mjs', 'scripts/stage-web-pwa.mjs', 'scripts/web-service-worker.js',
    'web/src/index.html', 'web/vendor/engine.wasm', 'web/vendor/legal/build.json', 'web/vendor/legal/privacy-policy.md',
    'web/vendor/notices/qrcode/qrcode-MIT.txt', 'web/sw.js', 'README.md', 'LICENSE'])
    assert(files.includes(`protracktor-web/${required}`), required);
  assert(files.every(name => name.startsWith('protracktor-web/') && !name.includes('/../')
    && !/node_modules|\.tar\.gz|server\.json/.test(name)));
  const site = unpack(second.archive), metadata = JSON.parse(fs.readFileSync(path.join(site, 'web/vendor/legal/build.json')));
  assert.equal(metadata.commit, source); assert.equal(metadata.dirty, false);
  assert.equal(fs.statSync(path.join(site, 'run.sh')).mode & 0o777, 0o755);
  assert.equal(fs.statSync(path.join(site, 'web/src/index.html')).mode & 0o777, 0o644);
  assert(second.archive.includes(run('git', ['rev-parse', '--short', 'HEAD']).trim()));
  assert(second.output.trim().endsWith(`📦 ${second.archive} (${fs.statSync(second.archive).size} bytes)`), second.output);
  run('node', ['--check', 'scripts/serve-web.mjs'], site);
  console.log('  ✓ runtime files, executable launcher, legal/source records, modes and final artifact report match');

  fs.appendFileSync(file, '<!-- changed fixture -->\n');
  const changed = packageSite(), dirty = unpack(changed.archive);
  assert(changed.archive.includes('-dirty-'));
  assert.notEqual(changed.hash, firstHash);
  assert.deepEqual(JSON.parse(fs.readFileSync(path.join(dirty, 'web/vendor/legal/build.json'))), { commit: source, dirty: true });
  assert.notEqual(fs.readFileSync(path.join(site, 'web/sw.js'), 'utf8'), fs.readFileSync(path.join(dirty, 'web/sw.js'), 'utf8'));
  console.log('  ✓ changed source is marked dirty in filename and metadata and changes the PWA revision');

  fs.appendFileSync(path.join(fixture, 'scripts/stage-web-legal.mjs'),
    "\nfs.writeFileSync(path.join(out, 'legal', 'build.json'), JSON.stringify({commit:'wrong',dirty:true}));\n");
  const mismatch = spawnSync('bash', ['scripts/package-web.sh'], { cwd: fixture, encoding: 'utf8' });
  assert.notEqual(mismatch.status, 0); assert.match(mismatch.stderr, /source identity does not match/);
  console.log('  ✓ mismatched staged source metadata refuses packaging');
  fs.rmSync(path.join(fixture, 'web/vendor/engine.wasm'));
  const refused = spawnSync('bash', ['scripts/package-web.sh'], { cwd: fixture, encoding: 'utf8' });
  assert.notEqual(refused.status, 0); assert.match(refused.stdout, /no engine/);
  console.log('  ✓ missing engine refuses packaging instead of announcing an unusable artifact');
} finally {
  fs.rmSync(fixture, { recursive: true, force: true });
}
console.log('✅ WEB packaging checks passed');
