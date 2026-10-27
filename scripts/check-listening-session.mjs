// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later
import assert from 'node:assert/strict';
import { ListeningSession } from '../web/src/listening-session.js';
let checks=0;
const test=(name,run)=>{let time=0;const session=new ListeningSession(()=>time);try { run(session,(value)=>{time=value;}); } catch (error) { console.error(`✗ ${name}`); throw error; } checks++;console.log(`✓ ${name}`);};
const result=(trackId,historyGeneration=0)=>({trackId,historyGeneration});
test('five progressing seconds qualify exactly once',(s,time)=>{s.begin('a',7,0,true);time(4999);assert.equal(s.sample(4.999,true),null);time(5000);assert.deepEqual(s.sample(5,true),result('a',7));time(9000);assert.equal(s.sample(9,true),null);});
test('three second natural completion qualifies exactly once',(s,time)=>{s.begin('short',7,0,true);time(3000);assert.deepEqual(s.sample(3,false,{completed:true}),result('short',7));assert.equal(s.sample(3,false,{completed:true}),null);});
test('completion between polls still qualifies a very short track',(s,time)=>{s.begin('short',0,0,true);time(200);assert.deepEqual(s.sample(.08,false,{completed:true}),result('short'));});
test('end handler qualifies after final stopped poll without another tick',(s,time)=>{s.begin('short',0,0,true);time(3000);assert.equal(s.sample(3,false),null);assert.deepEqual(s.sample(3,false,{completed:true}),result('short'));});
test('known duration completion qualifies even while decoder plays',(s,time)=>{s.begin('short',0,0,true);time(3000);assert.equal(s.sample(3,true),null);assert.deepEqual(s.sample(3,true,{completed:true}),result('short'));});
test('pause or skip interrupts short playback without qualification',(s,time)=>{s.begin('short',0,0,true);time(3000);assert.equal(s.sample(3,false),null);s.begin('next',0,0,true);time(3100);assert.equal(s.sample(.1,true),null);});
test('seeking to end cannot qualify, but five earned seconds still can',(s,time)=>{s.begin('seek',0,0,true);time(1000);s.sample(1,true);s.seeking(1);time(2000);s.baseline(99,true);time(3000);assert.equal(s.sample(100,false,{completed:true}),null);s.baseline(100,true);time(6000);assert.deepEqual(s.sample(103,true),result('seek'));});
test('restart restores completion eligibility after a sought occurrence',(s,time)=>{s.begin('short',0,0,true);s.seeking(0);s.begin('short',0,0,true);time(3000);assert.deepEqual(s.sample(3,false,{completed:true}),result('short'));});
test('pause resume preserves short natural completion eligibility',(s,time)=>{s.begin('short',0,0,true);time(1000);s.sample(1,false);time(60000);s.baseline(1,true);time(62000);assert.deepEqual(s.sample(3,false,{completed:true}),result('short'));});
test('failed start or zero audio completion never qualifies',(s,time)=>{s.begin('failed',0,0,false);time(3000);assert.equal(s.sample(3,false,{completed:true}),null);s.begin('empty',0,0,true);time(4000);assert.equal(s.sample(0,false,{completed:true}),null);});
test('clear cancels completion until a new occurrence',(s,time)=>{s.begin('short',0,0,true);time(1000);s.sample(1,true);s.forget();s.baseline(1,true);time(3000);assert.equal(s.sample(3,false,{completed:true}),null);s.begin('short',1,0,true);time(6000);assert.deepEqual(s.sample(3,false,{completed:true}),result('short',1));});
test('paused time and a seek jump earn nothing',(s,time)=>{s.begin('a',0,0,true);time(2000);assert.equal(s.sample(2,false),null);time(60000);s.baseline(100,true);time(62999);assert.equal(s.sample(102.999,true),null);time(63000);assert.deepEqual(s.sample(103,true),result('a'));});
test('short or stalled audio cannot qualify on a delayed poll',(s,time)=>{s.begin('short',0,0,true);time(60000);assert.equal(s.sample(4.9,false),null);s.begin('failed',0,0,false);time(120000);assert.equal(s.sample(0,false),null);});
test('another occurrence resets time even for the same file',(s,time)=>{s.begin('a',0,0,true);time(4000);assert.equal(s.sample(4,true),null);s.begin('a',0,0,true);time(6000);assert.equal(s.sample(2,true),null);});
test('clear cancels current occurrence until new begin',(s,time)=>{s.begin('a',0,0,true);time(4000);s.sample(4,true);s.forget();s.baseline(4,true);time(14000);assert.equal(s.sample(14,true),null);s.begin('b',1,0,true);time(19000);assert.deepEqual(s.sample(5,true),result('b',1));});
test('subsong reset preserves file-level earned time',(s,time)=>{s.begin('file',0,0,true);time(3000);s.sample(3,true);s.baseline(0,true);time(5000);assert.deepEqual(s.sample(2,true),result('file'));});
console.log(`${checks} listening cases passed.`);
