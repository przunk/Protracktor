// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later
import assert from 'node:assert/strict';
import fs from 'node:fs/promises';
import path from 'node:path';
import net from 'node:net';
import { spawn, execFileSync } from 'node:child_process';
import { once } from 'node:events';
import { fileURLToPath } from 'node:url';
import { createHash } from 'node:crypto';
import { chromium } from 'playwright';
import { browserEnv } from './linux-libs.mjs';
import { stereoFixture } from '../fixtures/stereo.mjs';

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '../..');
if (process.argv.length > 2) throw new Error('Usage: ./scripts/check-web-normalization.sh');
const output = path.join(root, 'user/web-normalization-check');
await fs.mkdir(output, { recursive: true });
const reservation = net.createServer();
reservation.listen(0, '127.0.0.1'); await once(reservation, 'listening');
const port = reservation.address().port;
await new Promise(resolve => reservation.close(resolve));
const base = `http://127.0.0.1:${port}`;
const serverDirectory = await fs.mkdtemp(path.join(output, 'server-'));
await fs.writeFile(path.join(serverDirectory, 'server.json'), JSON.stringify({ port, bind: '127.0.0.1', host: base }));
const server = spawn(process.execPath, [path.join(root, 'scripts/serve-web.mjs'), String(port)],
  { cwd: serverDirectory, stdio: ['ignore', 'pipe', 'pipe'] });
let serverLog = '', browser, serverFailure;
server.stdout.on('data', data => { serverLog += data; });
server.stderr.on('data', data => { serverLog += data; });
server.on('error', error => { serverFailure = error; });
const report = { source: execFileSync('git', ['rev-parse', 'HEAD'], { cwd: root, encoding: 'utf8' }).trim(),
  dirty: execFileSync('git', ['status', '--porcelain'], { cwd: root, encoding: 'utf8' }).trim().length > 0,
  fixture: 'Generated long quiet ProTracker tones; real Worker, worklet, WASM and browser timers',
  audibleDevice: 'unchecked; browser output muted', cases: [] };
try {
  let ready = false;
  for (let n = 0; n < 100; n++) {
    if (serverFailure) throw serverFailure;
    if (server.exitCode !== null) throw new Error(`Server exited: ${serverLog}`);
    try { if ((await fetch(`${base}/src/manifest.webmanifest`, { signal: AbortSignal.timeout(1000) })).ok) { ready = true; break; } } catch { /* Starting. */ }
    await new Promise(resolve => setTimeout(resolve, 50));
  }
  assert(ready, 'Production server did not start');
  browser = await chromium.launch({ headless: true, env: browserEnv(root), args: ['--mute-audio'] });
  report.browser = browser.version();
  const bytes = stereoFixture('correlated'); bytes[45] = 8; bytes[950] = 128;
  for (const width of [100, 50]) {
    const context = await browser.newContext({ serviceWorkers: 'block' });
    try {
      const page = await context.newPage(), errors = [];
      page.on('pageerror', error => { errors.push(error.message); });
      await page.route('**/*', route => new URL(route.request().url()).origin === base ? route.continue() : route.abort());
      await page.goto(`${base}/src/manifest.webmanifest`);
      await page.evaluate(() => { document.body.innerHTML = '<button id="start">Start audio test</button>'; });
      await page.click('#start');
      const cases = await page.evaluate(async ({ base, bytes, width }) => {
        const { PlaybackNormalization } = await import(`${base}/src/playback-normalization.js`);
        const wasmBinary = await (await fetch(`${base}/vendor/engine.wasm`)).arrayBuffer();
        const audio = new AudioContext({ sampleRate: 48000 });
        const prepared = new Map(), opened = new Set(), results = [];
        let node, player, ready = false, failure = null, failed = true;
        const wait = async predicate => {
          const deadline = performance.now() + 10000;
          while (!predicate()) {
            if (failure) throw new Error(failure);
            if (performance.now() > deadline) throw new Error('Normalization handshake timed out');
            await new Promise(resolve => setTimeout(resolve, 10));
          }
        };
        try {
          await audio.resume();
          await audio.audioWorklet.addModule(`${base}/src/processor.js`);
          node = new AudioWorkletNode(audio, 'protracktor', {
            numberOfInputs: 0, outputChannelCount: [2],
            processorOptions: { wasmBinary, stereoWidth: width, normalizationEnabled: true },
          });
          node.onprocessorerror = () => { failure = 'Production audio worklet failed'; };
          player = new PlaybackNormalization({ enabled: true, width, send: message => {
            if (message.type === 'prepared') prepared.set(message.requestId, message);
            node.port.postMessage(message);
          } });
          player.setEngine(wasmBinary);
          node.port.onmessage = ({ data }) => {
            switch (data.type) {
              case 'ready': ready = true; break;
              case 'decoded': void player.decoded(data).catch(error => { failure = error.message; }); break;
              case 'opened': player.opened(data.requestId); opened.add(data.requestId); break;
              case 'closed': player.closed(data.requestId); break;
              case 'loudness': void player.measured(data); break;
              case 'failed': failure = data.reason; break;
            }
          };
          const analyser = audio.createAnalyser(); analyser.fftSize = 2048;
          const mute = audio.createGain(); mute.gain.value = 0;
          node.connect(analyser); analyser.connect(mute); mute.connect(audio.destination);
          const level = async () => {
            let sum = 0;
            const samples = new Float32Array(analyser.fftSize);
            for (let n = 0; n < 8; n++) {
              analyser.getFloatTimeDomainData(samples);
              for (const value of samples) sum += value * value;
              await new Promise(resolve => setTimeout(resolve, 20));
            }
            return Math.sqrt(sum / (8 * samples.length));
          };
          await wait(() => ready);
          for (const [requestId, name, warm] of [[1, 'quiet-one.mod', false], [2, 'quiet-two.mod', false], [3, 'quiet-one.mod', true]]) {
            const buffer = Uint8Array.from(bytes).buffer;
            player.register(requestId, buffer, name);
            node.port.postMessage({ type: 'open', requestId, name, bytes: buffer }, [buffer]);
            await wait(() => opened.has(requestId));
            const profile = prepared.get(requestId)?.profile;
            if (!profile || profile.seconds < .4) throw new Error(`Request ${requestId} started without a prepared profile`);
            await new Promise(resolve => setTimeout(resolve, 400));
            const early = await level();
            await new Promise(resolve => setTimeout(resolve, 3100));
            const late = await level();
            if (!(early > .0001 && late > .0001)) throw new Error('The actual audio graph produced no measurable PCM');
            const differenceDb = 20 * Math.log10(late / early);
            if (Math.abs(differenceDb) > .5) throw new Error(`Gain changed by ${differenceDb.toFixed(2)} dB after startup`);
            results.push({ width, requestId, warm, profile, earlyRms: early, lateRms: late, differenceDb });
          }
          if (Math.abs(20 * Math.log10(results[2].earlyRms / results[0].earlyRms)) > .5)
            throw new Error('Warm replay starts at a different level than cold playback');
          failed = false;
          return results;
        } finally {
          try {
            node?.port.postMessage({ type: 'close' });
            player?.setEnabled(false);
          } catch (error) {
            // Preserve an earlier failed guard; cleanup must still pass on a successful run.
            if (!failed) throw error;
          } finally { await audio.close(); }
        }
      }, { base, bytes: [...bytes], width });
      assert.equal(errors.length, 0, errors.join('\n'));
      assert.equal(cases.length, 3);
      report.cases.push(...cases);
      console.log(`✓ width ${width}: two cold tracks and warm replay have prepared profiles and stable real PCM`);
    } finally { await context.close(); }
  }
  assert.equal(report.cases.length, 6);
  report.wasmSha256 = createHash('sha256').update(await fs.readFile(path.join(root, 'web/vendor/engine.wasm'))).digest('hex');
  report.status = 'passed';
} catch (error) {
  report.status = 'failed'; report.error = error.message; throw error;
} finally {
  await browser?.close();
  if (server.pid && server.exitCode === null && server.signalCode === null) {
    const exited = once(server, 'exit'); server.kill('SIGTERM'); await exited;
  }
  await fs.writeFile(path.join(output, 'report.json'), JSON.stringify(report, null, 2) + '\n');
  await fs.writeFile(path.join(output, 'server.log'), serverLog);
  await fs.rm(serverDirectory, { recursive: true, force: true });
}
console.log(`6 browser normalization cases passed; report: ${path.join(output, 'report.json')}`);
