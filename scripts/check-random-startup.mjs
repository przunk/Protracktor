// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later
import fs from 'node:fs';
import path from 'node:path';
import { pathToFileURL } from 'node:url';
import { execFileSync } from 'node:child_process';
const root=process.cwd();
const page=fs.readFileSync('scripts/check-page.mjs','utf8');
const marker='const $ = (id) => window.document.getElementById(id);';
let prefix=page.slice(0,page.indexOf(marker)+marker.length)
  .replace("'./../web/node_modules/jsdom/lib/api.js'",JSON.stringify(pathToFileURL(path.join(root,'web/node_modules/jsdom/lib/api.js')).href))
  .replace("'./../web/node_modules/fake-indexeddb/auto/index.mjs'",JSON.stringify(pathToFileURL(path.join(root,'web/node_modules/fake-indexeddb/auto/index.mjs')).href));
const temporary=fs.mkdtempSync(path.join(root,'user/web-parity-verification/w05-startup-'));
try {
  for(const held of [false,true]) {
    let source=prefix;
    if(held) {
      const original="const storeSource = fs.readFileSync('web/src/store.js', 'utf8').replace(/^export /gm, '');";
      const override="\nconst originalScopeGet = settings.get.bind(settings); settings.get = (key, fallback) => key === 'randomScope' ? new Promise((resolve) => { window.__releaseScopeRead = () => resolve('platform:pc'); }) : originalScopeGet(key, fallback);\n";
      if(!source.includes(original)) throw Error('The production page harness store loader changed');
      source=source.replace(original,original.slice(0,-1)+' + '+JSON.stringify(override)+';');
    } else {
      source=source.replace("console.log('page:');","await (await import(pathToSettings)).settings.set('randomScope','platform:pc');\nconsole.log('page:');")
        .replace("const failures = [];",`const pathToSettings = ${JSON.stringify(pathToFileURL(path.join(root,'web/src/store.js')).href)};\nconst failures = [];`);
    }
    source+=held?`
window.__api.showPanel(null);
const opening=window.__api.openRandom();
$('random-filter').click();
$('randomfilter').querySelector('[data-scope="everything"]').click();
window.__releaseScopeRead(); await opening;
await new Promise((resolve)=>setTimeout(resolve,80));
check(window.__api.randomScopeNow()==='everything','an explicit Everything choice wins over a delayed stored platform, even before scope changed');
check(await (await import(${JSON.stringify(pathToFileURL(path.join(root,'web/src/store.js')).href)})).settings.get('randomScope')==='everything','that explicit Everything choice is persisted rather than lost to the initial read');
process.exit(failures.length?1:0);
`:`
check(window.__api.randomScopeNow()==='platform:pc','startup restores the actual persisted Random scope through the production store');
check(window.__toWorklet.length===0,'restoring the Random scope never starts audio');
process.exit(failures.length?1:0);
`;
    const file=path.join(temporary,held?'held.mjs':'restored.mjs');fs.writeFileSync(file,source);
    process.stdout.write(execFileSync(process.execPath,[file],{cwd:root,timeout:90000,encoding:'utf8'}));
  }
} finally { fs.rmSync(temporary,{recursive:true,force:true}); }
console.log('4 Random startup/ownership cases passed.');
