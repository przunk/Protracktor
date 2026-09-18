# What only a phone can prove

*Started 2026-09-15, because a build was handed over with a fix in it that no check in this
repository can reach.*

The suite covers what a machine can decide: 237 unit tests, 390 page checks, 15 engine checks, 8
server checks, and every one of them runs in a second on a desktop. **None of them has a phone.**
Four kinds of fault live entirely outside their reach, and each one has already shipped at least
once:

- **the process dying** — an exception out of a JNI frame or an audio callback is `std::terminate`,
  and there is no test host to observe it from;
- **the foreground service** — whether a notification survives the owner leaving the app, which is
  Android's decision and depends on the device;
- **audio in a real output** — glitching, sample rate, ducking under a notification;
- **anything about how it looks**, which is the whole of the UI.

So a handover build is not "tested" when the suite is green. This is what the owner checks, and it
is written down so it is the same list every time rather than whatever anybody remembers.

## Every build

**Play for twenty minutes without touching it.** Random, scope *Everything*, screen off, phone in a
pocket. This is the single most valuable test in the document because it is the only one that runs
the decoders against files nobody chose, and the library is 516,118 tunes of which a fair number are
damaged. **What you are looking for is the app still being there.**

A tune that cuts short and moves on is **not** a fault — since 2026-09-15 that is a contained
decoder failure behaving exactly as intended (`docs/STATUS.md` C42). The app vanishing is the fault.

**Leave the app while it plays.** Home button, then use the phone normally for ten minutes. The
player notification must stay up and the music must not stop. A process killed here is `C43`, and it
returned once after being called fixed.

**Open Now Playing on three files of different kinds.** A tracker module, a SID, and something
emulated — SNDH or NSF. Since `describe()` was guarded, a decoder that fails while reading its own
metadata shows an **empty information block** rather than killing the app. An empty block is worth
reporting with the file name; it is the visible end of a contained failure.

## When something was fixed, add its own check

A fix that cannot be told apart from the bug by looking is not finished. Each entry below says what
to do and **what distinguishes a fix from a coincidence**.

### Build 639 — 2026-09-16 — the fallback length, and the Spectrum in the browser

*In it: `docs/STATUS.md` C56 and `docs/BACKLOG.md` A32. Build 628's list below has not been reported
on yet and still stands.*

1. **A SID must now end on its own.** Play one with the HVSC database **not** downloaded — Settings
   → Storage says whether it is. Before this it played until you pressed next. *Fix looks like:* it
   stops after three minutes and the next tune starts. *Watch for the opposite fault:* a tune that
   ends the **instant** it starts means the setting read zero and took it literally, which is worse
   than the bug being fixed.
2. **The slider**, Settings → Playback → *When nothing knows the length*. Drag it end to end: the
   label must read whole minutes only, 3 to 10, never something like "3 minutes" sitting on a notch
   that stores 239 seconds. Leave it somewhere other than 3, force-stop the app, reopen: it must
   come back where you left it.
3. **It is not only SID.** A `.sndh` that sc68's database has no entry for behaves the same way.
   Nothing to do but know it, so that a tune stopping at your setting reads as correct rather than
   as a fault.
4. **Nothing else may have started stopping.** This is the risk the change carries: the fallback
   applies wherever a length is missing, so a format that reports none and deserves longer will now
   be cut off — and stopping looks normal, so nobody thinks to report it. If a tune ends at exactly
   your fallback and you expected more, that is the report I need, with the file name.
5. **The phone should be unchanged by A32.** ZXTune went into the *browser* engine; the patch
   touches sources Android compiles too, so play a few Spectrum files (`.pt3`, `.stc`, `.asc`) and
   confirm they are exactly as they were. The web player is where the new thing is: those formats
   now play there as well, which they never did.

### Build 628 — 2026-09-15 — the engine boundary and the SAP message

*In it: `docs/STATUS.md` C42 (the phone's half) and C55.*

1. **The twenty-minute Random run above is the main test of this build.** Before 628, an exception
   escaping a decoder ended the process immediately, with no dialog and nothing in the app to see.
   Sixteen JNI entry points and Oboe's audio callback are guarded now. *Fix looks like:* the app
   survives a session it would previously not have. *Coincidence looks like:* a twenty-minute run
   that happened not to meet a bad file — so length matters more than attention here.
2. **`scene register 5 menu.sap`** (Modland, Slight Atari Player / Yezus). **It will still refuse**,
   because the file on Modland is truncated by two bytes and no player can open it. What changed is
   the sentence: it must name the **Atari 8-bit decoder (ASAP)**. If it still says *"wrong file type
   for this emulator"*, the fix did not take.
3. **If anything does go wrong**, `adb logcat` carries a line containing the word `contained`. That
   line means the guard caught something and names the call it happened in, which is the difference
   between a report I can act on and one I cannot.

## What to send back

The file name, and the line from `logcat` if there is one. A screenshot for anything visual — those
live in `user/` and are gitignored, so they never end up in the repository.

**"It worked" is worth saying too.** Several fixes in `docs/STATUS.md` were closed on a build nobody
confirmed, and two of them came back.
