# Plan: more formats

Written 2026-09-01, ahead of the owner sending this as its own goal.

Each backend is its own risk and its own commit. The order below is by value per unit of pain, and
the first item is not a new format at all.

---

## How much is actually out there

Counted from Modland's index on 2026-09-01 — 515,057 files, of which **188,755 (37%) play today**.
Modland is not the whole world, but it is the largest single sample we have and the one we index.

| Backend | Modland files it would unlock | Largest formats |
| --- | --- | --- |
| game-music-emu | ~80,000 | Nintendo SPC 36,903 · Gameboy 23,775 · VGM 14,172 · NSF 5,015 |
| libsidplayfp | ~72,000 | HVSC 60,572 · Sidplayer 5,032 · RealSID 3,540 |
| the `*SF` family | ~71,000 | Nintendo DS 31,118 · Playstation 16,840 · PS2 6,969 · N64 6,463 |
| ASAP | 3,230 | Slight Atari Player |
| UADE | ~1,400 by extension, more by directory | spread across many small format directories |

Two things this table says that a list of format names does not:

- **game-music-emu and libsidplayfp together are worth about 150,000 files** — roughly doubling what
  the app can play — and they are two libraries, not ten.
- **UADE's count is small and its cost is the highest** (a whole emulated Amiga, plus the licensing
  question about its bundled replay binaries). It is last for good reasons, and the numbers are one
  of them.

## What every backend costs

The same five things, so they are stated once:

1. **Vendor it** through `scripts/fetch-native-deps.sh` — pinned version, SHA-256 verified.
2. **Verify the licence against the sources, not `COPYING`.** sc68's `COPYING` says GPL **2**, which
   would have invalidated our GPL-3 decision; the 51 source files say "or any later version". Had we
   read only `COPYING` we would have dropped the format that started this project.
3. **A `Backend` implementation** in `native/engine/engine.cpp` — `render`, `canSeek`, `seek`,
   `rewind`, `positionSeconds`, `durationSeconds`, `describe`, `preferredSampleRate`. Capabilities
   are *declared*: a CPU-emulating backend that cannot seek says so, and the UI and the media session
   both already honour that answer.
4. **Extensions in `SupportedFormats`**, and its prefix list where the format uses `fmt.title`.
5. **Re-index the catalogues.** The index deliberately keeps only entries whose filename a backend
   might handle, so a newly supported format is invisible in Modland until a re-index. This is the
   step that will be forgotten.

Probing order in `openBackend` matters: strict verifiers first, `libopenmpt` last, because its format
net is wide enough to make a claim on something another backend should have had.

---

## 0. ~~`.sndh` — half fixed, and the rest needs a newer sc68~~ — DONE 2026-09-03

**Done 2026-09-01:** sc68 wraps SNDH in a replay routine that lives on disk rather than inside the
tune. We shipped none, so every file loaded and played silence. The binaries are packaged now, and
the gate no longer trusts `api68_verify_mem`, which rejects ICE-packed SNDH that loads perfectly.

**Still broken, and measured rather than guessed.** Thirty random SNDH files from Modland, run
through the exact backend logic on the host:

| | | |
| --- | --- | --- |
| plays | 16 | 53% |
| loads, then renders silence | 5 | 17% |
| fails `api68_load_mem` | 9 | 30% |

**sc68 2.2.1 is from 2003 and its SNDH support is partial.** Roughly half. No amount of wiring on
our side changes that, and the owner has hit the failing half twice in a row.

### The fix is sc68 3.0.0b, and it is reachable

Its own `NEWS` says what we need:

> sndh support should be almost perfect. […] sc68 has a built-in database of all sndh files known to
> this day including information on track duration and hardware used

That database would also give SNDH the durations it currently lacks entirely.

**Fetching it is solved.** It lives only in SourceForge SVN (revision 713 as of 2026-09-01) — no git
mirror, the author's GitHub does not carry it, the snapshot zip 404s, and `svn` is not installed
here. But **SourceForge serves the SVN tree as plain HTTP directory listings**, and they walk. The
whole tree came down with nothing but `urllib`: 715 files, 391 MB.

Almost all of that is ballast: `sc68-fb2k` alone is 348 MB of foobar2000 SDK binaries. **The fetch
must exclude the plugin directories** (`sc68-fb2k`, `sc68-dshow`, `sc68-winamp`, `sc68-vlc`,
`sc68-gst`, `sc68-audacious`, `sc68-doc`). What we actually need is 71 C files:

| | |
| --- | --- |
| `libsc68/emu68` | 25 |
| `libsc68/io68` | 16 |
| `libsc68/src` | 4 |
| `file68/src` | 22 |
| `unice68` | 4 |

### What building it will take — established 2026-09-01, not guessed

There are **no autotools on this machine** (`autoconf`, `automake`, `libtool`, `pkg-config` all
missing), so `configure` cannot be run. The same treatment 2.2.1 got applies, and a trial compile
narrowed the work to three things:

1. **Package defines.** `PACKAGE_STRING`, `PACKAGE_NAME`, `PACKAGE_VERSION` and friends, which
   `libsc68.c` `#error`s about by name. Trivial.
2. **`HAVE_*` defines and `<stdint.h>`.** Without them every file fails on `uint32_t`. Trivial.
3. **`file68_features.h`**, which autoconf generates from `file68/sc68/file68_features.h.in`. Not
   trivial, but not research either: **the tree already contains a pre-generated one** at
   `sc68-msvc/sc68/file68_features.h`, which is a working starting point.

So: a focused session's work, not a research project. The order stays as written — fetch, build for
the **host**, re-run the same thirty files, and only integrate if it beats 16/30.

### What actually happened, 2026-09-03

Measured with `./scripts/probe-sc68.py`, which runs **both** libraries over one deterministically
picked corpus. The 16/30 baseline was measured on files nobody recorded, so this is an A/B on the
same bytes rather than a B against a remembered A:

| | 2.2.1 | 3.0.0b | 3.0.0b, `NDEBUG` |
| --- | --- | --- | --- |
| `.sndh` plays | 14/30 | 28/30 | **30/30** |
| `.sndh` silent | 7 | 0 | 0 |
| `.sndh` load failure | 9 | 0 | 0 |
| `.sndh` abort | 0 | **2** | 0 |
| `.sc68` plays | 10/10 | 10/10 | **10/10** |

**Integrated.** 47% → 100% on SNDH, nothing that worked before stopped working, and SNDH files now
have durations at all — 3.x carries a database of known tunes with their lengths, which 2.2.1 had
no equivalent of.

Four things the plan did not foresee:

- **The whole `api68_*` API is gone**, with no compatibility header. `Sc68Backend` was rewritten
  against `sc68_*`. This was a rewrite, not a version bump.
- **`trap68.h` is generated**, and by sc68's *own* 68000 assembler (`as68`, which is in the tree)
  from `libsc68/asm/trapfunc.s`. Running a host tool inside a cross-compile is possible and
  unpleasant; the 630 bytes are committed instead, the way libsidplayfp's configure output is.
- **Two files abort on an assertion** — an MFP timer mode 3.x does not implement, which its own
  code then handles by disabling the timer. Assertions are off for this target in every build, so
  a debug build cannot be taken down by an ordinary file from the library.
- **The licence changed**: 2.2.1 was GPL-2.0-or-later, 3.0.0b is GPL-**3**.0-or-later. Verified
  across all 78 licensed sources; `COPYING` agrees this time. It is compatible either way — the app
  is GPL-3.0-or-later — but it removes one thing that had to stay true.

### Why not write our own

Asked by the owner on 2026-09-01, and worth recording because the answer is not obvious.

It would mean a 68000 emulator, a YM2149, the MFP 68901 timers, and SNDH parsing. The CPU is the
*easy* part — Musashi is MIT-licensed and proven, so nobody needs to write one. The hard part is
timing: SNDH music is driven by MFP timer interrupts, and the whole genre depends on those being
cycle-accurate, plus the envelope and "SID voice" tricks composers abused on the YM.

Months of work, and the failure modes are subtle — wrong tempo, missing effects — rather than
obvious. It would also be writing sc68 again with a borrowed CPU. The author has already done it and
says his version is almost perfect; the sensible move is to take his.

### Meanwhile

The failure message now names which backend refused and what it said, instead of claiming the format
is unsupported. A file no backend claims and a file a backend claimed and then choked on are
different problems and were indistinguishable from outside — it took a host probe to tell them apart,
which is not a thing the owner can do.

## 1. ~~`libsidplayfp` — Commodore 64~~ — DONE 2026-09-02

Version 3.1.1, GPL-2.0-or-later, verified in the sources rather than in `COPYING` for the third time
running.

**The ROM question, answered with a number.** Thirty random Modland SIDs were played on the host
with **no ROMs supplied at all**: 30 played, 0 were silent, 0 failed, and 0 were BASIC-compatible.
The decision the owner was asked to make is therefore much smaller than it looked — it only matters
for tunes that call into KERNAL or BASIC, and none of a thirty-file sample did. It remains his
decision if one ever turns up.

**Three things worth knowing before the next version bump:**

- **ReSIDfp is no longer inside libsidplayfp.** 3.x split it into a separate `libresidfp` package, so
  the build here uses **SIDLite**, which ships inside. ReSIDfp is the better emulation; using it
  means vendoring a second project. Revisit if SIDLite proves wanting.
- **Three generated headers are hand-written** in `native/backends/sidplayfp/generated/`:
  `config.h`, `sl_defs.h` and `sidplayfp/sidversion.h`. Upstream produces them from `configure`, and
  cross-compiling `configure` three times to feed one CMake build is more moving parts than thirty
  defines. Compare them against freshly generated ones when the version changes.
- **`mix()` needs `initMixer()` called first**, or it dereferences a mixer that does not exist. No
  header says so. It cost a segfault and a read of `player.cpp` — and it is exactly the kind of
  thing the host probe exists to find, since finding it on a device would have meant a crash report
  and no stack worth reading.

## 2. ~~`game-music-emu` — the consoles~~ — DONE 2026-09-01

One library, many formats: NSF, GBS, SPC, VGM/VGZ, HES, AY, KSS. Modland lists 36,903 `.spc` and
14,167 `.vgz`.

- Latest release **0.6.5** (github.com/libgme/game-music-emu, checked 2026-09-01).
- LGPL-2.1-or-later, and it already builds with CMake — the least painful item on this list.
- It was the least painful, as expected, and the only vendored library here whose CMake works as-is.
Two things that were not obvious:

- The static target is `gme_static`, exported as `gme::gme` — not `gme`.
- Its targets export their include directory only for an *installed* package, so `gme.h` is not on
  the path when consumed from the build tree. One `target_include_directories` fixes it.

`USE_GME_SAP` is off: gme has its own SAP emulator, ASAP is the reference implementation, and the
binary should not carry two answers to one question. `GME_YM2612_EMU` is "Nuked" (LGPL) rather than
"MAME" (GPL) — both would be compatible, the weaker copyleft leaves more room.

**Subsong selection is now overdue.** Some of these files hold hundreds of tracks — one GBS reported
99, an HES reported 256 — and we play track 0 and nothing else.

## 3. ~~ASAP — Atari 8-bit~~ — DONE 2026-09-01

ASAP 8.0.0, from SourceForge (not GitHub — the earlier guess at `pfusik/asap` was wrong). GPL-2.0-or-
later, verified in `README` and 34 sources.

**The easiest vendored library in this project by a wide margin.** ASAP is written in Ć and shipped
as transpiled C: one 8,000-line `asap.c`, no configure step, no generated headers, no options. It
also seeks, which none of the other emulator backends do.

Twelve of twelve random Modland SAP files played on the host probe before a line of it went near the
app — the technique from §0, and it is now two for two at catching things early.

Two things worth remembering from it:

- **The engine now carries a filename alongside the bytes.** Several of ASAP's fourteen formats are
  told apart by extension rather than by any header, so nothing else could make that call.
- **`.fc` is claimed by two backends**: Atari Future Composer to ASAP, Amiga Future Composer to
  libopenmpt. Checked rather than assumed — ASAP refuses the Amiga files, so they fall through
  correctly. Content probing would settle it properly; until then, whichever loads it wins.

## 4. UADE — Amiga custom formats — MEASURED 2026-09-04, not integrated

TFMX, Hippel, Future Composer, David Whittaker, SidMon and a hundred others. Emulates a whole Amiga
to run the original replay routines.

**Last, and not only because it is the hardest.** UADE ships the original 68k replay binaries
extracted from commercial and shareware Amiga music programs. UADE's own code is GPL; those binaries
are not clearly licensed. That is a **distribution** question and it has to be answered before any
code is written, not after. If the answer is no, the formats can still be played by a user who
supplies their own — which is a different feature.

### What was built, and what it proved

`GOAL.md` round 6 item 1 says to prove a backend on the host before integrating it.
`./scripts/build-uade-probe.sh` does that from nothing: it fetches UADE 3.05 and the two libraries
of its author's that it needs (bencodetools, libzakalwe), builds them at pinned revisions, and
compiles `native/probe/uade/probe_uade.c` against the result. `./scripts/probe-uade.py` then measures
against the Modland index.

The probe asks the question R1 taught us to ask: **is the first buffer full**, not "is there audio
eventually". The player treats a short first render as end-of-tune, so a probe that loops until it
hears something measures a hope rather than a contract.

### What it plays — measured 2026-09-04

Two questions, and the first one is not "what can UADE play". libopenmpt already handles AHX, Future
Composer, Puma, TCB and a dozen other Amiga formats, so what matters is **what UADE adds to what we
already play**.

**Reach**, counted over all 515,509 files Modland lists rather than sampled:

| | files | |
| --- | ---: | --- |
| a backend we already have claims it | 314,340 | 61.0% |
| we cannot play it, and UADE's `eagleplayer.conf` claims the name | 29,127 | 5.7% |
| we cannot play it, and UADE does not claim it either | 172,042 | 33.4% |

**That 5.7% is an upper bound and a large part of it is a name collision**, which is the whole
reason the second pass renders instead of counting. Modland's `.psf` (Playstation Sound Format,
3,845 files) matches UADE's `psf` prefix for SoundFactory; Sidplayer's `.mus` (5,028) matches `mus`
for UFO. Neither is an Amiga file. `docs/ARCHITECTURE.md` §5 says extensions here are shared between
unrelated formats; this is that, quantified.

**Play** — 12 files from each of the 25 largest, actually rendered, first buffer checked:

```
OctaMED MMD0/1/2/3   48/48     Delitracker Custom  12/12     Sidplayer            0/12
TFMX                 12/12     Musicline Editor    12/12     IFF-SMUS             0/12
BP SoundMon 2/3      24/24     Art Of Noise        12/12     Playstation SF       0/12
SidMon 2             12/12     Delta Music 2       12/12     Stereo Sidplayer     0/12
Oktalyzer            11/12     Sonic Arranger      11/12     FAC SoundTracker     0/12
David Whittaker      11/12     YMST                 7/12     Spectrum             0/12
                                                             Hippel COSO / ST     0/12

Overall: 184/300 full, 114 unsupported, 2 silent.
```

Every 0/12 in the right-hand column is a name collision the render pass caught, except the Hippel
COSO pair — those are genuinely declared and genuinely do not load, in either naming convention
(tested both ways round; renaming changes nothing, and plain `.hip` Hippel plays fine as Modland
names it, so content detection is doing its job).

### What UADE is actually worth — and the finding that undercuts it

The formats UADE played cover **9,720 Modland files that nothing here plays today**. But
**5,557 of those 9,720 are already playable by libopenmpt**, which is in the APK — OctaMED
`.mmd0`…`.mmd3` (5,110) and Oktalyzer `.okta` (447), measured 6/6 each. Widening the sweep from 60
formats to 150 added two more of the same kind, Graoumf Tracker `.gtk` and MultiMedia Sound `.mms`,
for 5,652 in total; nothing else in Modland's unplayable third turns out to be free, and the rest
of it is console dumps (`.minigsf`, `.mini2sf`, `.minipsf` — 66,000 files) that no backend here
opens. libopenmpt identifies MED by
an "MMD" magic in the header and never looks at the filename. The app simply never offers them,
because `SupportedFormats.extensions` lists `med` and `okt` while Modland stores these as `.mmd1`
and `.okta`.

**So UADE's genuine, exclusive contribution is 4,163 files, not tens of thousands** —
TFMX, Musicline Editor, Delitracker Custom, Oktalyzer, Sonic Arranger, BP SoundMon, Art Of Noise,
David Whittaker, SidMon 2, Delta Music 2, YMST. That is a real body of music and it is the music
this project is closest to. It is also a fraction of what `docs/BACKLOG.md` A5 assumed when it
called this "the largest body of music left", and the estimate deserves correcting rather than
quietly inheriting.

### The process model — the real integration question

This is the difference from the other four backends, and it was worth finding out before writing
any Android code.

**libuade does not decode anything itself.** It creates a `socketpair`, forks, and `execlp`s a
separate `uadecore` executable, passing the descriptors as `-i` and `-o` arguments
(`src/frontends/common/unixsupport.c:222`). Rendered audio comes back over that socket as
`UADE_REPLY_DATA` messages. The emulator is a different process by construction.

**That is less fatal than it sounds, and the reason is upstream's own structure:**

- the transport is *already* a socketpair rather than anything process-specific;
- `src/main.c` is three lines — `return uadecore_main(argc, argv);` — so the entry point is already
  separated from the process;
- `uadecore_main` returns rather than looping forever, and the audio sink is the IPC socket, not a
  sound device.

So the in-process route is: create the socketpair, and start a **thread** on `uadecore_main` instead
of forking. The seam is one function.

**What that route costs, stated rather than discovered later:**

- **One instance per process, permanently.** The UAE-derived core is built on file-scope state —
  roughly 160 definitions across `newcpu.c`, `memory.c`, `custom.c`, `audio.c`, `cia.c` and
  `uade.c` — and `uadecore_ipc` is a single global. Two concurrent UADE tunes in one process is not
  a tuning problem, it is impossible without forking the emulator.
- **That constraint bites this app specifically.** `PlaybackController.probe` opens a second backend
  while the first is playing, and round 5 made folder scanning run concurrently with playback on
  purpose. Scanning a folder of TFMX while a TFMX tune plays is a real sequence, not a hypothetical.
  There is a way out worth noting: `uade_is_our_file_from_buffer` resolves through
  `uade_analyze_eagleplayer`, which is content analysis with **no emulator involved**, so
  identification during a scan need not contend with playback. Duration, title and subsong count
  would.
- **51 `exit()` calls in uadecore.** In a forked child that is a clean death the parent survives; on
  a thread inside the app it is the app disappearing.
- `uade_signal_initializations()` sets process-wide signal dispositions.

**The alternative is to keep upstream's fork+exec**, shipping `uadecore` inside the APK's
`lib/<abi>/` as a `lib*.so` — the one place Android still permits executing from. That keeps
instances independent and upstream unforked, at the cost of process lifecycle management.

**Neither is chosen here.** Item 1 was to measure, and this is the measurement.

### What this measurement recommends

**Not integrating UADE yet, and doing the cheap thing first.** Setting out the trade rather than
the conclusion, because the conclusion is the owner's:

- what UADE exclusively adds is **4,163 Modland files**, not the tens of thousands A5 assumed;
- it cannot ship without answering a licence question that is *worse* documented than sc68's, and
  unlike sc68 it has **no middle option** — without the replay binaries it plays 12 files in 300;
- it is the only backend here that is a second process, or else a single-instance-per-process
  emulator inside ours, which collides with scanning-while-playing;
- and **5,557 files of the 9,720 it appeared to win are already playable** by a backend in the APK,
  blocked by five missing lines in `SupportedFormats`.

The last point is the one worth acting on immediately: it is more files than UADE exclusively
offers, for a change that costs five strings and a re-index. That does not make UADE not worth
doing — TFMX and David Whittaker are exactly the music this project is for — but it does mean UADE
is a considered choice with a licence decision attached, rather than the obvious next step.

Everything needed to revisit it is committed: `./scripts/build-uade-probe.sh` rebuilds the whole
toolchain from nothing, and `./scripts/probe-uade.py` re-runs the measurement on the same seeded
corpus.

---

## Not planned

`.sc68` container files reference external replay binaries we do not ship, so they will not play even
though sc68 is present. Worth a line in the UI eventually; not worth shipping the replay set for.
