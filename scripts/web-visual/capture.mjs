// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later
import assert from 'node:assert/strict';
import fs from 'node:fs/promises';
import path from 'node:path';
import os from 'node:os';
import net from 'node:net';
import { spawn, execFileSync } from 'node:child_process';
import { once } from 'node:events';
import { createHash } from 'node:crypto';
import { fileURLToPath } from 'node:url';
import { chromium } from 'playwright';
import { profiles, tracks, indexText, seed, demoNsf, demoMod } from './fixtures.mjs';
import { browserEnv } from './linux-libs.mjs';

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '../..');
const git = (...args) => execFileSync('git', args, { cwd: root, encoding: 'utf8' }).trim();
const digest = data => createHash('sha256').update(data).digest('hex');
const escape = value => String(value).replace(/[&<>"']/g, c => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' })[c]);

function options(args) {
  const result = { profiles: [] };
  while (args.length) {
    const arg = args.shift();
    if (arg === '--profile') {
      const name = args.shift();
      if (!profiles.some(profile => profile.name === name)) throw new Error(`Unknown profile: ${name}`);
      result.profiles.push(name);
    } else if (arg === '--list') result.list = true;
    else throw new Error(`Unknown option: ${arg}. Use --list or --profile NAME.`);
  }
  return result;
}

async function inputs() {
  const names = new Set(git('ls-files', '--', 'web', 'scripts/web-visual', 'scripts/capture-web.sh',
    'scripts/setup-web-visual.sh', 'scripts/serve-web.mjs', 'scripts/fixtures/stereo.mjs').split('\n').filter(Boolean));
  for (const name of await fs.readdir(path.join(root, 'scripts/web-visual'))) {
    if (name.endsWith('.mjs') || name.endsWith('.json')) names.add(`scripts/web-visual/${name}`);
  }
  for (const name of ['scripts/capture-web.sh', 'scripts/setup-web-visual.sh', 'web/vendor/engine.mjs',
    'web/vendor/engine.wasm']) names.add(name);
  const vendorFiles = async dir => {
    for (const entry of await fs.readdir(path.join(root, dir), { withFileTypes: true })) {
      const name = `${dir}/${entry.name}`;
      if (entry.isDirectory()) await vendorFiles(name);
      else if (entry.isFile()) names.add(name);
    }
  };
  await vendorFiles('web/vendor');
  return Object.fromEntries(await Promise.all([...names].sort().map(async name => [name, digest(await fs.readFile(path.join(root, name)))])));
}

async function server(output) {
  const reservation = net.createServer();
  reservation.listen(0, '127.0.0.1'); await once(reservation, 'listening');
  const port = reservation.address().port;
  await new Promise(resolve => reservation.close(resolve));
  const dir = await fs.mkdtemp(path.join(os.tmpdir(), 'protracktor-web-visual-'));
  await fs.writeFile(path.join(dir, 'server.json'), JSON.stringify({ port, host: `http://127.0.0.1:${port}`, bind: '127.0.0.1' }));
  const child = spawn(process.execPath, [path.join(root, 'scripts/serve-web.mjs'), String(port)],
    { cwd: dir, stdio: ['ignore', 'pipe', 'pipe'] });
  let log = '', failure = null;
  child.stdout.on('data', chunk => { log += chunk; });
  child.stderr.on('data', chunk => { log += chunk; });
  child.on('error', error => { failure = error; });
  const stop = async () => {
    if (child.exitCode === null && child.signalCode === null) {
      const ended = once(child, 'exit'); child.kill('SIGTERM'); await ended;
    }
    await fs.writeFile(path.join(output, 'server.log'), log);
    await fs.rm(dir, { recursive: true, force: true });
  };
  try {
    for (let n = 0; n < 100; n++) {
      if (failure || child.exitCode !== null || child.signalCode !== null) throw failure ?? new Error(`Server exited: ${log}`);
      try { if ((await fetch(`http://127.0.0.1:${port}/src/`, { signal: AbortSignal.timeout(1000) })).ok) return { base: `http://127.0.0.1:${port}`, stop }; }
      catch { /* The new child may not be listening yet. */ }
      await new Promise(resolve => setTimeout(resolve, 50));
    }
    throw new Error(`Server did not start: ${log}`);
  } catch (error) { await stop(); throw error; }
}

async function diagnostics(page) {
  return page.evaluate(() => {
    const shown = element => {
      const rect = element.getBoundingClientRect(), style = getComputedStyle(element);
      return !element.closest('[hidden]') && style.visibility !== 'hidden' && rect.width > 0 && rect.height > 0
        && rect.bottom > 0 && rect.top < innerHeight;
    };
    const label = element => element.id || element.getAttribute('aria-label') || element.textContent.trim().slice(0, 70);
    return {
      viewport: { width: innerWidth, height: innerHeight, scale: devicePixelRatio },
      horizontalOverflow: document.documentElement.scrollWidth > innerWidth + 1,
      fontFamily: getComputedStyle(document.body).fontFamily,
      inspectedStyles: ['#random-back', '#random-dice', '#search-scopes .chip', '#search-scopes .chip svg'].flatMap(selector =>
        [...document.querySelectorAll(selector)].filter(shown).map(element => {
          const style = getComputedStyle(element), rect = element.getBoundingClientRect();
          return { selector, text: label(element), width: rect.width, height: rect.height,
            color: style.color, fill: style.fill, background: style.backgroundColor,
            borderRadius: style.borderRadius, fontSize: style.fontSize };
        })),
      controls: [...document.querySelectorAll('button,input,[role="button"]')].filter(shown).map(element => {
        const r = element.getBoundingClientRect();
        return { label: label(element), disabled: !!element.disabled, x: r.x, y: r.y, width: r.width, height: r.height,
          outsideViewport: r.left < -1 || r.right > innerWidth + 1 || r.bottom > innerHeight + 1 };
      }),
      dock: ['nowcard', 'title', 'sub', 'dock-tune', 'nowkeep', 'showqueue', 'playpause'].map(id => {
        const node = document.getElementById(id), r = node?.getBoundingClientRect();
        return { id, shown: !!node && shown(node), text: node?.textContent.trim(),
          rect: r ? { x: r.x, y: r.y, width: r.width, height: r.height } : null };
      }),
    };
  });
}

async function captureProfile(browser, base, output, profile, report) {
  const context = await browser.newContext({ viewport: { width: profile.width, height: profile.height },
    deviceScaleFactor: 1, isMobile: profile.touch, hasTouch: profile.touch,
    locale: profile.language === 'pl' ? 'pl-PL' : 'en-US', timezoneId: 'Europe/Warsaw',
    colorScheme: profile.theme, reducedMotion: 'reduce', serviceWorkers: 'block' });
  const errors = [], blocked = [], fulfilled = [];
  const page = await context.newPage();
  page.setDefaultTimeout(15000);
  page.on('pageerror', error => errors.push(error.stack ?? String(error)));
  const mod = demoMod(), nsf = demoNsf();
  await context.route('**/*', async route => {
    const url = new URL(route.request().url());
    if (url.origin === base || url.protocol === 'data:') return route.continue();
    if (url.hostname === 'modland.com' && url.pathname.startsWith('/pub/modules/')) {
      fulfilled.push(url.href);
      return route.fulfill({ status: 200, contentType: 'application/octet-stream',
        headers: { 'access-control-allow-origin': '*' }, body: url.pathname.endsWith('.nsf') ? nsf : mod });
    }
    blocked.push(url.href);
    return route.abort('blockedbyclient');
  });
  await context.addInitScript(({ language, theme }) => {
    localStorage.setItem('protracktor.language', language);
    localStorage.setItem('protracktor.theme', theme);
    localStorage.setItem('protracktor.cacheAhead', 'off');
  }, profile);
  const dir = path.join(output, profile.name);
  await fs.mkdir(dir);
  const entry = { ...profile, screenshots: [], errors, blocked, fulfilled };
  report.profiles.push(entry);
  const take = async name => {
    await page.evaluate(() => document.fonts.ready);
    // Let Chromium's transient touch highlight expire without changing the page's CSS.
    if (profile.touch) await page.waitForTimeout(350);
    await page.evaluate(() => new Promise(resolve => requestAnimationFrame(() => requestAnimationFrame(resolve))));
    const file = `${profile.name}/${String(entry.screenshots.length + 1).padStart(2, '0')}-${name}.png`;
    await page.screenshot({ path: path.join(output, file), animations: 'disabled', caret: 'hide' });
    const info = await diagnostics(page);
    entry.screenshots.push({ name, file, sha256: digest(await fs.readFile(path.join(output, file))), ...info });
    console.log(`${profile.name}: ${name}`);
  };
  const click = selector => profile.touch ? page.locator(selector).tap() : page.locator(selector).click();
  const close = async id => {
    if (await page.locator(`#${id}`).isVisible()) await click(`#${id} [data-close]`);
  };
  const browse = async title => {
    if (await page.locator('#playback-panel').isVisible()) await click('#queue-close');
    if (await page.locator('#browse').isVisible()) await click('#browseclose');
    if (await page.locator('#randomhead').isVisible()) await click('#random-leave');
    await click('#tab-browse');
    await page.locator('#browse').waitFor({ state: 'visible' });
    if (title) await page.locator('#browselist .bname', { hasText: title }).click();
  };
  try {
    // Pairing deliberately long-polls, so network-idle is not an application-ready signal.
    await page.goto(`${base}/src/`, { waitUntil: 'domcontentloaded' });
    await page.waitForFunction(() => document.querySelector('#qr').children.length > 0);
    await close('pair');
    await page.locator('#emptyplaylist').waitFor({ state: 'visible' });
    await take('empty-playlist');
    await page.evaluate(seed, { tracks, indexText });
    await page.reload({ waitUntil: 'domcontentloaded' });
    await page.locator('#queue .track').first().waitFor();
    await close('pair');
    await take('playlist');
    await click('#queue .track:first-child .text');
    await page.locator('#dock-tune').waitFor({ state: 'visible' });
    await page.waitForFunction(() => document.querySelector('#dock-tune').textContent.includes('5'));
    await click('#playpause');
    await take('dock-five-tunes');
    await click('#nowcard');
    await page.locator('#nowplaying').waitFor({ state: 'visible' });
    await take('now-playing');
    await click('#nowcard');
    for (let n = 1; n <= 2; n++) {
      await page.locator('#queue .track').nth(n).locator('.rowmenu').click();
      await page.locator('#menu button').filter({ hasText: /Add to queue|Dodaj do kolejki/ }).click();
    }
    await click('#showqueue');
    await page.locator('#playback-list').waitFor({ state: 'visible' });
    await take('queue');
    await click('#queue-close');
    await browse(); await take('browse');
    await browse(/Online catalogues|Katalogi online/); await take('online-catalogues');
    await page.locator('#browselist .bname', { hasText: /^Modland$/ }).click();
    await page.locator('#browselist .bname', { hasText: /^Protracker$/ }).click();
    await page.locator('#browselist .bname', { hasText: /^Visual Demo$/ }).click();
    await take('author-folder');
    await browse(/^History$|^Historia$/); await take('history');
    await page.locator('#historysearch').fill('demo');
    await page.waitForTimeout(350); await take('history-search');
    await browse(/^Search$|^Szukaj$/);
    await click('#browsesearch');
    await page.locator('#search-suggestions').waitFor({ state: 'visible' });
    await take('search-suggestions');
    await page.locator('#browsesearch').fill('demo');
    await click('#browsesubmit');
    await page.locator('#browselist .bname').first().waitFor();
    await take('search-results');
    await browse(/Saved places|Zapisane miejsca/); await take('saved-places');
    await browse(/^Random$|^Losowo$/);
    await page.locator('#randomhead').waitFor({ state: 'visible' });
    await page.waitForFunction(() => !document.querySelector('#dock-tune').hidden || !document.querySelector('#seek').disabled);
    await page.waitForFunction(() => /^(Pause|Pauza)$/.test(document.querySelector('#playpause').title));
    await click('#playpause');
    await take('random');
    await click('#random-filter'); await take('random-filter');
    await click('#tab-settings');
    await page.locator('#settings').waitFor({ state: 'visible' });
    await take('settings-top');
    await page.locator('input[name="language"][value="en"]').scrollIntoViewIfNeeded();
    await take('settings-language-theme');
    await page.locator('#open-source').scrollIntoViewIfNeeded();
    await take('settings-bottom');
    assert.equal(errors.length, 0, `Browser page errors: ${errors.join('\n')}`);
    assert.equal(blocked.length, 0, `Unexpected external requests: ${blocked.join('\n')}`);
  } catch (error) {
    entry.failure = error.stack ?? String(error);
    await take('failure').catch(() => {});
    throw error;
  } finally { await context.close(); }
}

async function gallery(output, report) {
  const body = report.profiles.map(profile => `<section><h2>${escape(profile.name)} · ${profile.width} × ${profile.height}</h2>
    ${profile.failure ? `<pre>${escape(profile.failure)}</pre>` : ''}<div class="shots">${profile.screenshots.map(shot => `<figure><a href="${escape(shot.file)}"><img loading="lazy" src="${escape(shot.file)}"></a><figcaption>${escape(shot.name)}${shot.horizontalOverflow ? ' · HORIZONTAL OVERFLOW' : ''}</figcaption></figure>`).join('')}</div></section>`).join('');
  await fs.writeFile(path.join(output, 'index.html'), `<!doctype html><html lang="en"><meta charset="utf-8"><title>Protracktor visual review</title>
    <style>body{font:16px system-ui;background:#17202a;color:#eaf0f6;margin:24px}h1,h2{margin:24px 0 12px}.shots{display:grid;grid-template-columns:repeat(auto-fit,minmax(300px,1fr));gap:18px}figure{margin:0}img{display:block;width:100%;border:1px solid #526276}figcaption{padding:8px}pre{white-space:pre-wrap}a{color:inherit}</style>
    <h1>Protracktor · actual Chromium renders</h1><p>Source ${escape(report.source)}${report.dirty ? ' · dirty' : ''} · ${escape(report.browser)} · ${escape(report.at)}</p>
    <p>Demonstration data and intercepted module downloads; real production UI, WASM and AudioWorklet. Headless output is muted. Phone layouts emulate viewport/touch; no physical device, Bluetooth or audible verification.</p>${body}</html>`);
  await fs.writeFile(path.join(output, 'report.json'), JSON.stringify(report, null, 2) + '\n');
}

async function overviews(browser, output, report) {
  const context = await browser.newContext({ deviceScaleFactor: 1 });
  try {
    const page = await context.newPage();
    for (const profile of report.profiles) {
      const shots = profile.screenshots.filter(shot => shot.name !== 'failure');
      profile.overviews = [];
      const width = profile.touch ? profile.width : 640;
      await page.setViewportSize({ width: width * 2 + 48, height: 900 });
      for (let at = 0; at < shots.length; at += 6) {
        const selected = shots.slice(at, at + 6), number = at / 6 + 1;
        const filename = `${profile.name}-overview-${number}`;
        const html = `<!doctype html><html lang="en"><meta charset="utf-8"><style>body{font:15px system-ui;background:#e7e9ed;color:#182028;margin:16px}.grid{display:grid;grid-template-columns:repeat(2,${width}px);gap:16px}figure{margin:0}img{display:block;width:100%}figcaption{padding:6px 0}</style><h2>${escape(profile.name)} · ${profile.width} × ${profile.height}</h2><div class="grid">${selected.map(shot => `<figure><figcaption>${escape(shot.name)}</figcaption><img src="${escape(shot.file)}"></figure>`).join('')}</div></html>`;
        await fs.writeFile(path.join(output, `${filename}.html`), html);
        await page.goto(`file://${path.join(output, `${filename}.html`)}`);
        await page.waitForFunction(() => [...document.images].every(img => img.complete && img.naturalWidth > 0));
        await page.screenshot({ path: path.join(output, `${filename}.png`), fullPage: true });
        profile.overviews.push(`${filename}.png`);
      }
    }
  } finally { await context.close(); }
}

const selected = options(process.argv.slice(2));
if (selected.list) { console.log(profiles.map(profile => `${profile.name}: ${profile.width}x${profile.height}`).join('\n')); process.exit(0); }
const source = git('rev-parse', 'HEAD'), dirty = git('status', '--porcelain').length > 0;
const stamp = new Date().toISOString().replace(/[:.]/g, '-');
const output = path.join(root, 'dist', `web-visual-${source.slice(0, 7)}${dirty ? '-dirty' : ''}-${stamp}`);
await fs.mkdir(output, { recursive: true });
const report = { at: new Date().toISOString(), source, dirty, inputHashes: await inputs(),
  fixture: 'Generated NSF with five silent subsongs and ProTracker tones, synthetic catalogue/History/bookmarks',
  serviceWorkers: 'blocked for an isolated current-source rendering run; not a PWA acceptance test', profiles: [], status: 'running' };
let running, browser, failed = false;
try {
  running = await server(output);
  browser = await chromium.launch({ headless: true, env: browserEnv(root) });
  report.browser = `Chromium ${browser.version()}`;
  report.base = running.base;
  for (const profile of profiles.filter(profile => !selected.profiles.length || selected.profiles.includes(profile.name))) {
    try { await captureProfile(browser, running.base, output, profile, report); }
    catch (error) { failed = true; console.error(`${profile.name}: ${error.message}`); }
  }
  assert(report.profiles.length > 0, 'Zero profiles is not a successful capture');
  assert.deepEqual(await inputs(), report.inputHashes, 'Source/tooling changed during capture');
  report.status = failed ? 'failed' : 'captured; visual assessment pending';
} catch (error) { failed = true; report.status = 'failed'; report.failure = error.stack ?? String(error); console.error(error.message); }
finally {
  try {
    await gallery(output, report);
    if (browser) await overviews(browser, output, report);
  } catch (error) {
    failed = true; report.status = 'failed'; report.galleryFailure = error.stack ?? String(error);
    console.error(error.message);
  } finally {
    try { await browser?.close(); }
    finally {
      await running?.stop();
      await fs.writeFile(path.join(output, 'report.json'), JSON.stringify(report, null, 2) + '\n');
    }
  }
}
console.log(`Visual report: ${output}/index.html`);
if (failed) process.exitCode = 1;
