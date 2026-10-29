// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later
import assert from 'node:assert/strict';
import fs from 'node:fs';
import { JSDOM } from '../web/node_modules/jsdom/lib/api.js';
import { LibraryDownloads, DOWNLOAD_STEPS } from '../web/src/library-downloads.js';
import { useLanguage } from '../web/src/i18n.js';
import { DownloadPickerView } from '../web/src/download-picker-view.js';
import { hasPolish } from '../web/src/i18n.js';
const dom=new JSDOM('<div id="picker"></div>',{pretendToBeVisual:true});globalThis.document=dom.window.document;
const root=document.getElementById('picker'),pause=()=>new Promise((done)=>setImmediate(done));
const until=async(fn)=>{for(let n=0;n<100;n++){if(fn())return;await pause();}throw new Error('Boundary not reached');};
let job,release,progress,wait=true,checks=0;const calls=[];
const check=(condition,name)=>{assert(condition,name);checks++;console.log('PASS: '+name);};
const runner=new LibraryDownloads(Object.fromEntries(DOWNLOAD_STEPS.map((id)=>[id,async(options)=>{calls.push(id);if(id==='songlengths'&&wait){progress=options.onProgress;await new Promise((resolve,reject)=>{release=resolve;options.signal.addEventListener('abort',()=>reject(new DOMException('Aborted','AbortError')),{once:true});});}return id;}] )));
const picker=new DownloadPickerView({root,runner,icon:(name)=>`<svg data-icon="${name}"></svg>`,onStart:(selected)=>{job=runner.start(selected);},onStop:()=>runner.stop(),onRetry:()=>{job=runner.retry();}});
try{
 const viewSource=fs.readFileSync('web/src/download-picker-view.js','utf8');
 const keys=[...viewSource.matchAll(/uiMessageText\('([^']+)'/g)].map((match)=>match[1]);
 for(const match of viewSource.matchAll(/uiPluralText\([^,]+, '[^']+', '([^']+)'/g))keys.push(match[1]);
 check(keys.length>15&&keys.every(hasPolish),'all picker messages and plural forms have Polish entries');
 picker.selectMissing({modland:true,asma:false,songmetadata:false});const inputs=[...root.querySelectorAll('input')];
 picker.action.focus();picker.action.dispatchEvent(new dom.window.KeyboardEvent('keydown',{key:'Tab',bubbles:true,cancelable:true}));
 check(document.activeElement===inputs[0],'Tab from the last available action returns to the first picker control');
 inputs[0].focus();inputs[0].dispatchEvent(new dom.window.KeyboardEvent('keydown',{key:'Tab',shiftKey:true,bubbles:true,cancelable:true}));
 check(document.activeElement===picker.action,'Shift-Tab from the first picker control returns to its last available action');
 check(inputs.length===3&&inputs.map((input)=>input.checked).join(',')==='false,true,true','default picks only missing native choices');
 check(picker.action.textContent==='Download (40 MB)','selected byte estimate includes only chosen WEB datasets');
 inputs[1].click();inputs[2].click();check(picker.action.disabled&&picker.note.textContent.includes('Select at least'),'empty choice stays visibly disabled with explanation');
 picker.selectMissing({});check(picker.action.textContent==='Download (47 MB)','all approved datasets cost the WEB credits-only total');
 picker.action.click();await until(()=>progress);check(runner.running&&inputs.every((input)=>input.disabled),'actual Download freezes checkboxes while the same picker reports progress');
 check(calls.join(',')==='modland,favourites,asma,songlengths','actual action follows selected native plan order');
 const button=picker.action,row=root.querySelector('[data-download-choice="songmetadata"]'),checkbox=inputs[2];button.focus();progress({stage:'fetching',done:1024*1024,total:2*1024*1024});
 check(document.activeElement===button&&picker.action===button&&row.querySelector('input')===checkbox,'progress retains button focus, row identity and checkbox identity');
 check(row.textContent.includes('1.0 of 2.0 MB')&&row.querySelector('.spinner'),'current step exposes byte progress beside a fixed spinner slot');
 const english=row.querySelector('strong').textContent;useLanguage('pl');picker.render();check(row.querySelector('strong').textContent!==english&&runner.running,'language refresh updates current picker without stopping downloads');useLanguage('en');picker.render();
 picker.action.click();await job;check(runner.state.phase==='stopped'&&!runner.running&&runner.busy.length===0,'actual Stop cancels its waiter and releases all run state');
 check(runner.state.rows.slice(0,3).every((item)=>item.status==='done')&&runner.retryable.join(',')==='songlengths,trackmetadata','completed datasets remain and only unfinished steps are offered');
 check(!picker.retry.hidden&&picker.retry.querySelector('svg')&&picker.retry.textContent.includes('2 unfinished'),'Retry has an icon, a label and unfinished count');
 wait=false;picker.retry.click();await job;check(calls.slice(4).join(',')==='songlengths,trackmetadata'&&runner.retryable.length===0&&picker.retry.hidden,'actual Retry executes only unfinished steps and disappears after success');
 check(picker.action===button&&inputs.every((input)=>!input.disabled),'completed run keeps stable controls and releases checkbox choices');
 console.log(checks+' DOM picker assertions pass; physical rendering unchecked.');
}finally{release?.();picker.unsubscribe();dom.window.close();delete globalThis.document;}
