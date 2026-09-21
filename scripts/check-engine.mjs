// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later
//
// Checks on the engine's boundary: what it says when it refuses, and that it stays alive when a
// decoder throws.
//
// **Not a probe.** `scripts/probe-web.mjs` measures real files and prints what it found; this
// asserts, fails, and is part of `scripts/test-protracktor.sh`. The two faults it exists for both
// reached a listener:
//
//   C55  A `.sap` refused with "wrong file type for this emulator" -- game-music-emu's sentence
//        about a file ASAP had claimed by name and refused first. The reason shown belonged to a
//        decoder that had no business with the file.
//   C42  An exception out of a decoder crossing the boundary, which on the web wedges the worklet
//        until the tab is reloaded. On the phone the same throw ends the process.
//
// It runs against the WebAssembly build because that is the one that runs in node -- but the code
// under test is `native/engine/engine.cpp`, which is the same file Android compiles. Only the
// boundary differs, and that half is checked by reading it.
//
//   node scripts/check-engine.mjs

import fs from 'fs';
import path from 'path';

// An AudioWorklet defines these; node does not, and the module reads them while loading.
globalThis.sampleRate ??= 48000;
globalThis.currentFrame ??= 0;
globalThis.currentTime ??= 0;

const engineFile = path.resolve('web/vendor/engine.mjs');
if (!fs.existsSync(engineFile)) {
  console.error('no engine built at web/vendor/engine.mjs — run scripts/build-web-engine.sh');
  process.exit(2);
}

const M = await (await import(engineFile)).default();

let failed = 0;
const check = (what, ok, detail = '') => {
  if (ok) console.log(`✓ ${what}`);
  else {
    failed++;
    console.log(`✗ ${what}${detail ? `\n    ${detail}` : ''}`);
  }
};

const cstr = (s) => {
  const bytes = Buffer.from(s + '\0', 'utf8');
  const p = M._malloc(bytes.length);
  M.HEAPU8.set(bytes, p);
  return p;
};

/** Opens bytes under a name and returns { handle, error }. The handle must be closed by the caller. */
const open = (bytes, name) => {
  const buf = M._malloc(bytes.length);
  M.HEAPU8.set(bytes, buf);
  const namePtr = cstr(name);
  const handle = M._pt_open(buf, bytes.length, namePtr);
  M._free(buf);
  M._free(namePtr);
  return { handle, error: M.UTF8ToString(M._pt_last_error()) };
};

// --- which decoders this build actually has ----------------------------------------------------
//
// `pt_backends` is not a vanity string: `catalogue.js` reads it to decide what the page will offer
// to index, so a decoder silently dropping out of the build silently removes tunes from Browse.
// ZXTune is the one that has moved (`docs/BACKLOG.md` A32) and the one this guards.
{
  const fingerprint = M.UTF8ToString(M._pt_backends());
  const absent = fingerprint.split(';')
    .map((part) => part.split(':'))
    .filter(([, version]) => version === 'none')
    .map(([name]) => name);
  // UADE is the one decoder the browser can never have: it is a second process, and WebAssembly
  // has no `fork` (`docs/BACKLOG.md` A44). Anything else missing is a build that lost a decoder.
  check('the browser engine is missing exactly UADE', absent.join() === 'uade',
    `missing: ${absent.join(', ') || '(none)'} — full fingerprint: ${fingerprint}`);
  // Named individually, because "nothing missing" also passes on a build that lost a decoder in a
  // way the fingerprint does not describe.
  for (const decoder of ['openmpt', 'sc68', 'asap', 'gme', 'sidplayfp', 'minimp3']) {
    check(`${decoder} is in this build`, fingerprint.includes(`${decoder}:`), fingerprint);
  }
}

// --- the reason a refusal gives ----------------------------------------------------------------
//
// **A SAP that no decoder can load, built rather than stored.**
//
// The header is valid and complete -- ASAP parses it and finds a TYPE B tune -- and then the
// binary part claims a block two bytes longer than the file, so loading it into 6502 memory fails.
// That is exactly the damage in a real file: `scene register 5 menu.sap` on Modland is short by
// two bytes, and ASMA keeps no copy of it, because ASMA validates.
//
// The point of building it here is the *second* decoder. `gme_identify_header` knows `SAP\r\n` and
// game-music-emu is compiled without its Atari emulator, so it recognises this file and refuses it
// with "Wrong file type for this emulator" -- after ASAP has already refused. Both refusals are
// real; the question this checks is which one the listener is told about.
const malformedSap = (() => {
  const header = Buffer.from('SAP\r\nAUTHOR "check"\r\nNAME "malformed"\r\nTYPE B\r\nINIT 2000\r\nPLAYER 2003\r\n', 'latin1');
  // $FF $FF opens the binary part, then a block header: start address, last address. 256 bytes are
  // claimed and 254 supplied.
  const block = Buffer.from([0xff, 0xff, 0x00, 0x20, 0xff, 0x20]);
  return Buffer.concat([header, block, Buffer.alloc(254, 0xea)]);
})();

{
  const { handle, error } = open(malformedSap, 'malformed.sap');
  check('a malformed .sap is refused', handle === 0, `handle ${handle}`);
  if (handle) M._pt_close(handle);

  // The two halves of C55, stated separately so a failure says which one came back.
  check('the refusal names the decoder that claimed the file', /ASAP|Atari/i.test(error), `said: ${error}`);
  check(
    'the refusal is not a later decoder speaking over it',
    !/emulator|game-music-emu/i.test(error),
    `said: ${error}`,
  );
}

// The same file under a name ASAP does not claim reaches game-music-emu as the first claimant, and
// then game-music-emu's reason is the true one. The rule is "whoever claimed it first", not "never
// say game-music-emu".
{
  const { handle, error } = open(malformedSap, 'malformed.xyz');
  check('a file no name-claimant wants is still refused', handle === 0, `handle ${handle}`);
  check(
    'and then the first decoder to claim it is the one quoted',
    /game-music-emu/i.test(error),
    `said: ${error}`,
  );
  if (handle) M._pt_close(handle);
}

// A refusal must say something. An empty reason is a dialog with a blank space in it.
{
  const { handle, error } = open(Buffer.alloc(64, 0), 'empty.bin');
  check('refusing nothing-in-particular still gives a reason', handle === 0 && error.length > 0, `said: ${error}`);
  if (handle) M._pt_close(handle);
}


// --- a length nobody stated is not a length ------------------------------------------------------
//
// game-music-emu's `play_length` is documented in its own header as "Length if available, otherwise
// intro_length+loop_length*2 if available, **otherwise a default of 150000** (2.5 minutes)". Read as
// a measurement it makes every NSF, AY, KSS and GBS without a length claim to be exactly 2:30 --
// and the app drew a progress bar promising that over a tune that stops when it stops
// (`docs/STATUS.md` C67).
//
// A file the format gives nowhere to write a length: the header, and a page of 6502 that returns
// immediately. What it must report is nothing.
{
  const nsf = Buffer.alloc(128 + 256);
  nsf.write('NESM\x1a', 0, 'latin1');
  nsf[5] = 1;                       // version
  nsf[6] = 1;                       // one song
  nsf[7] = 1;                       // starting song
  nsf.writeUInt16LE(0x8000, 8);     // load address
  nsf.writeUInt16LE(0x8000, 10);    // init
  nsf.writeUInt16LE(0x8003, 12);    // play
  nsf.write('check tune', 14, 'latin1');
  nsf.writeUInt16LE(16666, 110);    // NTSC frame length, in microseconds
  nsf[128] = 0x60;                  // RTS, so init does nothing
  nsf[131] = 0x60;                  // RTS, so play does nothing

  const { handle, error } = open(nsf, 'no-length.nsf');
  check('an NSF opens', handle !== 0, `said: ${error}`);
  if (handle) {
    check(
      'and a file that states no length reports none, rather than the library default of 2:30',
      M._pt_duration(handle) === 0,
      `${M._pt_duration(handle)} s`,
    );
    M._pt_close(handle);
  }
}

// --- surviving a decoder that refuses ----------------------------------------------------------
//
// C42: what matters is not that a bad file fails but that the next good one still works. A throw
// that escapes leaves the module unusable and every tune after it fails too -- a page that plays
// nothing until the tab is reloaded.
//
// **A real module, built rather than stored.** A four-channel ProTracker file is 1084 bytes of
// header and 1024 of pattern, and writing it here keeps the check readable and the repository free
// of binary fixtures nobody can inspect.
const tinyModule = (() => {
  const header = Buffer.alloc(1084);
  header.write('check module', 0, 'latin1');            // title, 20 bytes
  // One sample, two bytes long, so that the file describes something rather than nothing.
  header.write('beep', 20, 'latin1');                    // sample 1 name, 22 bytes
  header.writeUInt16BE(1, 42);                           // its length, in words
  header[45] = 64;                                       // full volume
  header[950] = 1;                                       // song length: one position
  header[951] = 127;                                     // restart byte, as ProTracker writes it
  header[952] = 0;                                       // position 0 plays pattern 0
  header.write('M.K.', 1080, 'latin1');                  // four channels, 31 samples
  const pattern = Buffer.alloc(1024);
  // Row 0, channel 0: sample 1, period 428 (C-3), no effect.
  pattern[0] = 0x11;
  pattern[1] = 0xac;
  return Buffer.concat([header, pattern, Buffer.alloc(2)]);
})();

{
  // Proven first, so that a failure afterwards means the junk did it rather than the module being
  // wrong all along.
  const before = open(tinyModule, 'before.mod');
  check('the built module is one the engine can open', before.handle !== 0, `said: ${before.error}`);
  if (before.handle) M._pt_close(before.handle);

  const junk = [
    Buffer.alloc(16, 0),
    Buffer.from('SAP\r\n', 'latin1'),
    malformedSap.subarray(0, 40),
    malformedSap,
    Buffer.from('IMPM' + 'x'.repeat(200), 'latin1'),
    Buffer.from(crypto.getRandomValues(new Uint8Array(4096))),
  ];
  let opened = 0;
  for (const [i, bytes] of junk.entries()) {
    const { handle } = open(Buffer.from(bytes), `junk-${i}.mod`);
    if (handle) {
      opened++;
      M._pt_close(handle);
    }
  }
  check('a run of files it cannot open is refused, not crashed on', opened === 0, `${opened} opened`);

  const after = open(tinyModule, 'after.mod');
  check('and the next good file still opens afterwards', after.handle !== 0, `said: ${after.error}`);

  // --- every call the players make, on a handle that is real -----------------------------------
  //
  // The page asks for a description and a length as soon as it has a handle, and the phone polls a
  // position while the tune runs. None of these may throw out of the boundary, so all of them are
  // called here -- including a seek on a backend that may refuse to, and a subsong that does not
  // exist.
  if (after.handle) {
    const h = after.handle;
    const describe = M.UTF8ToString(M._pt_describe(h));
    check('a description comes back', describe.includes('check module'), `said: ${describe}`);
    check('a length comes back', Number.isFinite(M._pt_duration(h)));
    check('a position comes back', Number.isFinite(M._pt_position(h)));
    check('a subsong count comes back', M._pt_subsong_count(h) >= 1);

    const out = M._malloc(1024 * 2 * 4);
    const rendered = M._pt_render(h, 48000, 1024, out);
    check('audio comes back', rendered > 0, `${rendered} frames`);

    // **Asked for things it may not be able to do.** A subsong that is not there and a seek past
    // the end are what a mistimed tap actually sends, and neither may reach a decoder unguarded.
    M._pt_select_subsong(h, 99);
    M._pt_seek(h, 99999);
    M._pt_rewind(h);
    const stillRenders = M._pt_render(h, 48000, 1024, out);
    check('and it still renders after being asked for the impossible', stillRenders > 0, `${stillRenders} frames`);
    M._free(out);
    M._pt_close(h);
  }
}

console.log();
if (failed) {
  console.error(`${failed} engine check${failed === 1 ? '' : 's'} failed`);
  process.exit(1);
}
