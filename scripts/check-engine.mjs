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
    // Since 2026-09-22 (the owner's variant (a)) a tune that falls silent is measured, so this
    // silent one has a length of about a second -- where it really ends. What must never come back
    // is the library's invented 2:30.
    check(
      'and a file that states no length never reports the library default of 2:30',
      M._pt_duration(handle) !== 150 && M._pt_duration(handle) < 150,
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

// --- text read out of a file -------------------------------------------------------------------
//
// A47. A title is bytes from a fixed field, written in whatever the author's machine used, and
// every string leaves the engine as UTF-8 by one rule: valid UTF-8 stays as it is; text holding a
// byte from 0x80 to 0x9F is CP437 (those bytes are control characters in ISO-8859-1, and letters
// on a DOS machine); anything else is ISO-8859-1. The reported file, Zalza's "akes lekhorna.mod",
// holds 0x86 and 0x94 -- CP437's "å" and "ö" -- which libopenmpt turned into U+FFFD.
const titleOf = (describe) => (describe.split('\n').find((l) => l.startsWith('title\t')) ?? '').slice(6);
const lineOf = (describe, key) => (describe.split('\n').find((l) => l.startsWith(`${key}\t`)) ?? '').slice(key.length + 1);
const describeOf = (bytes, name) => {
  const { handle, error } = open(bytes, name);
  if (!handle) return `(did not open: ${error})`;
  const text = M.UTF8ToString(M._pt_describe(handle));
  M._pt_close(handle);
  return text;
};

{
  // Through libopenmpt, which decodes a MOD's text itself.
  const modWithTitle = (title) => {
    const bytes = Buffer.from(tinyModule);
    bytes.fill(0, 0, 20);
    title.copy(bytes, 0);
    return bytes;
  };
  const cp437 = describeOf(modWithTitle(Buffer.from([0x86, ...Buffer.from('kes lekh', 'latin1'), 0x94, ...Buffer.from('rna (za)', 'latin1')])), 'akes lekhorna.mod');
  check('a MOD title in CP437 reads as letters', titleOf(cp437) === 'åkes lekhörna (za)', `said: ${titleOf(cp437)}`);
  const latin1 = describeOf(modWithTitle(Buffer.from('J\xf6rg \xc5ke', 'latin1')), 'latin1.mod');
  check('a MOD title in ISO-8859-1 still reads as ISO-8859-1', titleOf(latin1) === 'Jörg Åke', `said: ${titleOf(latin1)}`);
}

{
  // Through the engine's own rule, for a decoder that hands out raw bytes: a PSID file, built
  // here -- a header, then two RTS instructions for init and play at $1000 and $1003.
  const sidWith = (name, author) => {
    const header = Buffer.alloc(0x7c);
    header.write('PSID', 0, 'latin1');
    header.writeUInt16BE(2, 4);          // version
    header.writeUInt16BE(0x7c, 6);       // data offset
    header.writeUInt16BE(0, 8);          // load address: the first two bytes of the data
    header.writeUInt16BE(0x1000, 0x0a);  // init
    header.writeUInt16BE(0x1003, 0x0c);  // play
    header.writeUInt16BE(1, 0x0e);       // songs
    header.writeUInt16BE(1, 0x10);       // start song
    name.copy(header, 0x16, 0, 32);
    author.copy(header, 0x36, 0, 32);
    const data = Buffer.from([0x00, 0x10, 0x60, 0xea, 0xea, 0x60]);
    return Buffer.concat([header, data]);
  };
  const sid = describeOf(sidWith(Buffer.from('lekh\x94rna', 'latin1'), Buffer.from('J\xf6rg', 'latin1')), 'check.sid');
  check('a SID title with a CP437 letter reads as that letter', titleOf(sid) === 'lekhörna', `said: ${titleOf(sid)}`);
  check('a SID author in ISO-8859-1 reads as ISO-8859-1', lineOf(sid, 'artist') === 'Jörg', `said: ${sid.split('\n').slice(0, 4).join(' | ')}`);
  const utf8 = describeOf(sidWith(Buffer.from('Łódź', 'utf8'), Buffer.from('x', 'latin1')), 'utf8.sid');
  check('a SID title already in UTF-8 is left as it is', titleOf(utf8) === 'Łódź', `said: ${titleOf(utf8)}`);
}

// --- a SID that is a BASIC program ---------------------------------------------------------------
//
// C80. An RSID with the BASIC flag is a program for the C64's BASIC interpreter, which needs the
// BASIC ROM this app does not carry. Opened anyway, it played silence -- `Prelfugueinfmaj_BASIC.sid`,
// and 590 others in HVSC. It has to be refused, and the refusal has to say why.
{
  const basicSid = (() => {
    const header = Buffer.alloc(0x7c);
    header.write('RSID', 0, 'latin1');
    header.writeUInt16BE(2, 4);          // version
    header.writeUInt16BE(0x7c, 6);       // data offset
    header.writeUInt16BE(0, 8);          // load address from the data, as RSID requires
    header.writeUInt16BE(0, 0x0a);       // init 0: BASIC's RUN starts it
    header.writeUInt16BE(0, 0x0c);       // play 0
    header.writeUInt16BE(1, 0x0e);       // songs
    header.writeUInt16BE(1, 0x10);       // start song
    header.write('basic check', 0x16, 'latin1');
    header.writeUInt16BE(0x02, 0x76);    // flags: C64 BASIC
    // A one-line BASIC program at $0801: 10 END.
    const data = Buffer.from([0x01, 0x08, 0x07, 0x08, 0x0a, 0x00, 0x80, 0x00, 0x00, 0x00]);
    return Buffer.concat([header, data]);
  })();
  const { handle, error } = open(basicSid, 'check_BASIC.sid');
  if (handle) M._pt_close(handle);
  check('a SID that needs the BASIC ROM is refused, not played as silence', handle === 0, 'it opened');
  check('and the refusal says it is BASIC', /BASIC/.test(error), `said: ${error}`);
}

// --- seeking a SID: running the machine there ------------------------------------------------------
//
// libsidplayfp cannot jump, so a seek renders silently to the place (`seekByRendering`): forward
// from where it is, backward from the start of the tune. The position it reports afterwards is the
// place asked for, to the buffer.
{
  const header = Buffer.alloc(0x7c);
  header.write('PSID', 0, 'latin1');
  header.writeUInt16BE(2, 4);
  header.writeUInt16BE(0x7c, 6);
  header.writeUInt16BE(0x1000, 0x0a);
  header.writeUInt16BE(0x1003, 0x0c);
  header.writeUInt16BE(1, 0x0e);
  header.writeUInt16BE(1, 0x10);
  header.write('seek check', 0x16, 'latin1');
  const sid = Buffer.concat([header, Buffer.from([0x00, 0x10, 0x60, 0xea, 0xea, 0x60])]);
  const { handle: h } = open(sid, 'seek.sid');
  check('the SID for seeking opens', h !== 0);
  if (h) {
    const describe = M.UTF8ToString(M._pt_describe(h));
    check('a SID says it can seek now', describe.includes('seekable\t1'), describe.split('\n').find((l) => l.startsWith('seekable')));
    const out = M._malloc(4096 * 8);
    M._pt_render(h, 44100, 4096, out);
    M._pt_seek(h, 30);
    const forward = M._pt_position(h);
    check('a seek forward arrives where it was asked', Math.abs(forward - 30) < 0.2, `at ${forward.toFixed(2)} s`);
    M._pt_seek(h, 5);
    const back = M._pt_position(h);
    check('and a seek back goes back, through the start', Math.abs(back - 5) < 0.2, `at ${back.toFixed(2)} s`);
    const started = Date.now();
    M._pt_seek(h, 99999);
    const far = M._pt_position(h);
    check('a seek past any tune is bounded, not an hour of rendering', far <= 20 * 60 + 1 && Date.now() - started < 120000,
      `at ${far.toFixed(0)} s after ${((Date.now() - started) / 1000).toFixed(1)} s`);
    M._free(out);
    M._pt_close(h);
  }
}

// --- an NSF's length: measured when it falls silent, "where it stops" when it loops ------------------
//
// The owner's variant (a), 2026-09-22. NSF states no length. A tune that falls silent is measured by
// playing a copy of it to its end; one that never does -- game music loops -- keeps no length, and
// the description says where game-music-emu will stop it (`ends_at`: its 2:30 default plus the fade).
{
  const nsf = (code) => {
    const header = Buffer.alloc(0x80);
    header.write('NESM\x1a', 0, 'latin1');
    header[5] = 1; header[6] = 1; header[7] = 1;               // version, songs, starting song
    header.writeUInt16LE(0x8000, 8);                           // load
    header.writeUInt16LE(0x8000, 10);                          // init
    header.writeUInt16LE(0x8020, 12);                          // play
    header.write('length check', 0x0e, 'latin1');
    header.writeUInt16LE(16666, 0x6e);                         // NTSC speed, 60 Hz
    const body = Buffer.alloc(0x40, 0xea);                     // NOPs
    code.copy(body, 0);
    body[0x20] = 0x60;                                          // play: RTS
    return Buffer.concat([header, body]);
  };
  // A square wave held on: enable pulse 1, constant volume, halt its length counter, set a period.
  const tone = nsf(Buffer.from([0xa9, 0x01, 0x8d, 0x15, 0x40, 0xa9, 0xbf, 0x8d, 0x00, 0x40,
                                0xa9, 0xfd, 0x8d, 0x02, 0x40, 0xa9, 0x00, 0x8d, 0x03, 0x40, 0x60]));
  const silent = nsf(Buffer.from([0x60]));
  const lineOf2 = (d, key) => (d.split('\n').find((l) => l.startsWith(`${key}\t`)) ?? '').slice(key.length + 1);
  const loops = open(tone, 'tone.nsf');
  check('the looping NSF opens', loops.handle !== 0, loops.error);
  if (loops.handle) {
    const d = M.UTF8ToString(M._pt_describe(loops.handle));
    check('a looping NSF has no length, and says where it will stop',
      M._pt_duration(loops.handle) === 0 && Math.abs(Number(lineOf2(d, 'ends_at')) - 158) < 0.5,
      `duration ${M._pt_duration(loops.handle)}, ends_at "${lineOf2(d, 'ends_at')}"`);
    M._pt_close(loops.handle);
  }
  const quiet = open(silent, 'silent.nsf');
  if (quiet.handle) {
    const d = M.UTF8ToString(M._pt_describe(quiet.handle));
    const seconds = M._pt_duration(quiet.handle);
    check('an NSF that falls silent is measured, and has a length of its own',
      seconds > 0 && seconds < 150 && !lineOf2(d, 'ends_at'),
      `duration ${seconds}, ends_at "${lineOf2(d, 'ends_at')}"`);
    M._pt_close(quiet.handle);
  }
}

console.log();
if (failed) {
  console.error(`${failed} engine check${failed === 1 ? '' : 's'} failed`);
  process.exit(1);
}
