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

## 0b. libopenmpt — measured 2026-09-04, having carried the library unmeasured

Four backends had numbers and the one holding **sixty-one per cent of everything the app claims**
did not. `./scripts/probe-openmpt.py` asks the same contract as the others — is the *first* buffer
full, and is there sound in it — across every extension the app hands it, sampled from Modland and
weighted by what the archive actually holds.

**390 of 457 sampled, which is 98.0% of the 178,795 files those extensions cover.**

The four that matter are perfect: `.mod` 12/12 (85,023 files), `.xm` 12/12 (44,260), `.it` 12/12
(25,570), `.s3m` 12/12 (11,215). So is everything in the Amiga group that libopenmpt actually
implements — `.dbm`, `.sfx`, `.smod`, `.symmod`, `.digi`, `.dsym`, `.fc`, `.gmc`, `.puma`, `.tcb`,
and the `.mmd0`/`.mmd1` added that morning.

### What the two per cent is, and why almost none of it is a defect

| | played | Modland | what the failures actually are |
| --- | --- | --- | --- |
| `.ahx` | 0/12 | 1,389 | **libopenmpt has no AHX loader at all** — removed, and back in §6 |
| `.hvl` | 0/12 | 44 | same; no loader — removed, and back in §6 |
| `.ftm` | 1/12 | 1,874 | libopenmpt's FTM is **Face The Music**; Modland's are **FamiTracker** |
| `.imf` | 3/12 | 210 | libopenmpt's IMF is **Imago Orpheus**; Modland's are **id Software AdLib** |
| `.psm` | 6/12 | 141 | libopenmpt's PSM is **Epic MegaGames MASI**; half of Modland's are **Spectrum Pro Sound Maker** |
| `.med` | 1/12 | 140 | magic `MED\x04` — **Music Editor**, the older Amiga format, not MMD |
| `.dmf` | 4/16 | 2,186 | **1,807 are DefleMask**, 366 X-Tracker; libopenmpt implements the second |

`.dmf` joined this table on 2026-09-07, after the owner found a DefleMask file that would not open.
Measured the same way: **1,807 of Modland's 2,186 are DefleMask and 366 are X-Tracker**, and
libopenmpt implements the second. The extension stays claimed for the reason `.ftm` and `.psm` do —
dropping it would throw away the 366 that work — and DefleMask would need a decoder of its own,
which is a format question rather than a defect.

**Five of those six are one extension standing for two unrelated formats**, which is precisely what
`docs/ARCHITECTURE.md` §5 says extensions do in this world. It is now measured rather than asserted:
2,365 Modland files are filed under a name whose owner is a different program.

**Nothing more was removed.** Each of those extensions genuinely loads the format libopenmpt
implements; the failures are files that merely share the name, and dropping the extension would
throw away the real ones with them. `.ahx` and `.hvl` were different — **no** loader existed, so the
entries were pure loss. They came back the following day through HivelyTracker (§6), not UADE.

**The real answer is `docs/BACKLOG.md` A6**, probing content instead of trusting names. A local
folder is already scanned that way. A catalogue index is a list of filenames on somebody else's
server, so it cannot be — and this table is the price of that, quantified: about 1.3% of what the
app offers from Modland is a name collision. That is the trade, and it is a good one.

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

~~**Subsong selection is now overdue.**~~ Done in round 6's A2. Some of these files hold hundreds of
tracks — one GBS reported 99, an HES reported 256.

### Measured 2026-09-04, three years late

This backend went in on build notes and **not one number**, while sc68 has 30 of 30, libsidplayfp
30 of 30 and ASAP 12 of 12. It is also the part of the app nobody has ever listened to, so it was
heading into a store listing on trust. `./scripts/build-gme-probe.sh` and `./scripts/probe-gme.py`
measure it, asking `GmeBackend`'s contract rather than hoping: is the **first** buffer full (R1), and
does the track ever end.

| | before | after | Modland |
| --- | --- | --- | --- |
| `.spc` | 20/20 | 20/20 | 36,903 |
| `.vgz` | 19/20 | 19/20 | 14,167 |
| `.nsf` | 19/20 | **20/20** | 5,015 |
| `.gbs` | 20/20 | 20/20 | 918 |
| `.hes` | 13/20 | **20/20** | 421 |
| `.kss` | 9/20 | **17/20** | 393 |
| `.gym` | 0/20 | — removed | 265 |
| **total** | **100/141** | **116/141** | |

**What "before" was failing at is the finding.** HES and KSS routinely hold nothing at track 0 —
they are sound *banks* as much as albums, and track 0 is often an empty slot or an effect. Every one
of twenty HES files had music in it; seven of them were silent where the app opened them. So a third
of the format looked broken while the music sat one track along.

`GmeBackend::openAtSomethingAudible` now starts at the first track with sound in it, entered only
when track 0 was silent, so a file that begins with music never pays for it. It reports where it
opened through `describe()`, because the subsong strip has to agree with what is audible.

**Bounded by time, which is the second version.** The first stopped after twelve tracks and the
owner found the counter-example within minutes: `aleste 2.kss` has 256 tracks, 82 of them audible,
and **the first is number 47**. Twelve was a guess dressed as a limit. A 300 ms wall-clock budget
makes no guess about how fast the phone is — a quick device searches further, a slow one stops
sooner and behaves exactly as it did before — and it bounds the quantity that actually matters,
which is the wait before sound. That took `.kss` from 15/20 to 17/20.

**What is still wrong and is gme's, not ours:** `gme_track_count` returns a flat **256** for KSS and
HES whatever the file holds. `aleste 2.kss` really has 82 tunes; the subsong strip shows 256 chips
and most lead to silence. Finding the truth means rendering all 256, which is minutes, not
milliseconds — so it is recorded here rather than guessed at. The formats affected are 814 Modland
files between them.

**`.gym` is removed from `SupportedFormats`.** All forty sampled Modland GYM files are packed, and
game-music-emu refuses packed GYM unconditionally — `"Packed GYM file not supported"` is in
`Gym_Emu.cpp` with no build option behind it. Listing the extension only indexed 265 files that
could not open. (First measured wrongly: a hand-written header check read `loop_start` where
`packed` lives and reported 39 of 40 *un*packed. The probe was right and the shortcut was not.)

**What still fails, and is not worth chasing:** one `.vgz` using the YM2413 chip this build does not
include, one `.vgm` that renders silence, and three `.kss` with nothing audible within the budget.
Five files in a hundred and twenty, across formats worth 58,000.

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

### What it plays — measured and corrected 2026-09-04

Two questions, and the first one is not "what can UADE play". libopenmpt already handles Future
Composer, Puma, TCB and a dozen other Amiga formats, so what matters is **what UADE adds to what we
already play**. AHX was once included in that list by assumption; the measured correction below
explains why it is not.

**Reach in the current application**, counted over all 515,509 files Modland lists rather than
sampled:

| | files | |
| --- | ---: | --- |
| a backend we already have claims it | 318,275 | 61.7% |
| we cannot play it, and UADE's `eagleplayer.conf` claims the name | 24,916 | 4.8% |
| we cannot play it, and UADE does not claim it either | 172,318 | 33.4% |

**That 4.8% is an upper bound and a large part of it is a name collision**, which is the whole
reason the second pass renders instead of counting. Modland's `.psf` (Playstation Sound Format,
3,845 files) matches UADE's `psf` prefix for SoundFactory; Sidplayer's `.mus` (5,028) matches `mus`
for UFO. Neither is an Amiga file. `docs/ARCHITECTURE.md` §5 says extensions here are shared between
unrelated formats; this is that, quantified.

**Play in the current application** — 12 files from each of the 25 largest remaining directories,
actually rendered, first buffer checked:

```
AHX                  12/12     Delitracker Custom  12/12     Sidplayer             0/12
TFMX                 12/12     Musicline Editor    12/12     IFF-SMUS              0/12
BP SoundMon 2/3      24/24     Art Of Noise        12/12     Playstation SF        0/12
SidMon 2             12/12     Delta Music 2       12/12     Stereo Sidplayer      0/12
Digital Mugician     12/12     MusicMaker V8       12/12     FAC SoundTracker      0/12
Sonic Arranger       11/12     David Whittaker     11/12     Spectrum              0/12
YMST                 11/12     Hippel COSO         10/12     Hippel ST COSO        0/12
                                                             SCC-Musixx            0/12
                                                             GoatTracker           0/12
                                                             Richard Joseph        0/12

Overall: 175/300 full, 122 unsupported, 3 silent.
```

The successful format directories in this current run contain **5,799 Modland files that nothing
in the application claims today**. This is evidence of reach, not a claim that every file in those
directories plays: the render pass is deliberately sampled.

**Why the previous result was wrong.** The original probe set `UC_CONTENT_DETECTION`, believing the
name enabled additional detection. UADE exposes that option in `uade123` as strict content-only
detection. It therefore rejected the filename match whenever the bytes alone did not identify the
format. **Upstream caught this** — the reply pointed at `amberstar (03).hipc` played with stock
`uade123`, which anyone can repeat; removing the option made the exact file render here too, with
player `Hippel-COSO`.

The old result can be compared exactly rather than guessed. `scripts/probe-uade.py
--supported-formats-revision 95da64a` restores the supported-format list used to choose the original
top 25, and seed 68 consequently selects the same 300 files. The rerun is **196/300 full, 101
unsupported and 3 silent**, not 184/300. Hippel COSO changes from the false 0/12 to **11 full and 1
silent first buffer**; Hippel ST COSO remains 0/12. The old parser also accidentally counted
commented-out `"snd"` as an extension. Correcting that changes the whole-index historical reach from
314,340 / 29,127 / 172,042 to **314,317 / 29,128 / 172,064**, but the one newly reachable SND file
is outside the top 25 and does not change the 300-file play corpus.

### Hippel ST COSO is Modland's problem, not UADE's — established 2026-09-05

The one directory still at 0/12 after the probe was corrected, and the answer came from the
reference set upstream pointed at: `git clone git://zakalwe.fi/chip`, UADE's own test-case
repository, holding an example of every format it supports.

| | accepted |
| --- | --- |
| `jochen_hippel_coso/SOC.*` from `zakalwe.fi/chip` | **66 of 67** (the exception is `SOC.samp`) |
| Modland `Hippel ST COSO`, seeded sample | **0 of 12** |

**Renaming does not explain it.** The reference set uses the Amiga `SOC.name` convention and Modland
uses `name.soc`; the Modland files were re-tested under both and failed under both. That was the
obvious theory and it is wrong.

**The files differ in size by about a factor of seven** for what appear to be the same tunes —
GhostBattle world2 is 3,806 bytes from Modland and 30,012 from the Wanted Team rip; the sampled
means are 3,732 against 26,796. Both carry the same `COSO` magic and the same `TFMX` marker at
offset 0x20, so they are the same format rather than two different ones.

**No diagnosis beyond that is offered, deliberately.** A header-offset theory was tried and
discarded within the hour: Modland's files appear "truncated" by that measure and so do reference
files that play perfectly, which means the measure was reading the format wrong. After
`UC_CONTENT_DETECTION`, a second confident wrong theory is the thing most worth avoiding — so the
observation went upstream and the explanation was left to the people who know the format.

**What follows from it for us:** `zakalwe.fi/chip` is the reference set now, not Modland. Modland
remains what the app *browses*, but a format measured only against it is a format measured against
one ripper's habits.

### And then upstream answered, and this section was wrong too

Within the hour, UADE's other maintainer replied, and the answer was that the mess is in the
*distribution* of the Hippel and TFMX families rather than in the formats: different collections and
sites use prefix and suffix conventions that contradict each other. The fix already exists and is
public — a `song.conf` of **md5 overrides** at
<https://github.com/mvtiaine/audacious-uade/blob/master/conf/song.conf>, maintained alongside the
Audacious plugin, and said to cover the Modland files in question.

It does. Dropped into UADE's base directory, where `uade_load_initial_song_conf` looks for it:

| | before | after |
| --- | --- | --- |
| Modland Hippel ST COSO, the same 12 files | 0/12 | **11/12** |
| the historical 300-file corpus | 196/300 | **206/300** |

All twelve of the sampled files are named in that table by md5. Hippel COSO goes to 11/12, David
Whittaker to 12/12.

**So "Modland's rips are broken" was wrong**, and it was the second confident diagnosis in two days
to be corrected by the people who maintain the thing. The files are fine; UADE cannot tell from
their names which player to use, and the table says so per file. The size difference recorded above
is real and was a red herring — the Wanted Team rips simply carry more with them.

**What this changes for an integration, and it is not small.** A UADE backend needs three things
from upstream, not two: the emulator, `players/`, **and a song database**. Without the third,
formats fail in a way that looks like the format not working — which is exactly how it looked here
for a day.

**The licence of that table is fine, and the trap next to it is not.** `audacious-uade`'s README:
*"The project as a whole is licensed under GPL-2.0-or-later"* — compatible with our GPL-3. But the
same README says *"Songdb (`conf/songdb`) is licensed under CC BY-NC-SA 4.0"*, which is
**non-commercial** and could not ship in a store app. `conf/song.conf` is the one we want and
`conf/songdb` is the one to keep away from. GitHub's own API reports the repository as plain
"GPL-2.0"; the README is the accurate source.

### RMC — the format that would make most of this moot

Raised by upstream for the record, and worth keeping. RMC is a single-file format that states
exactly what a song is and what it needs: a torrent-like container holding an optional player, the
song itself, and metadata including subsong durations — which is most of the compatibility trouble
above, solved at the source. — `git clone git://zakalwe.fi/rmc-chip`.

**libuade already implements it**, which is what makes this worth writing down rather than filing
away: `uade_is_rmc`, `uade_rmc_get_subsongs` (a dictionary of subsong number to length in
milliseconds) and `uade_rmc_get_song_length`. And `uade_get_time_position` documents the difference
in one line — *"Function returns a negative value for non-rmc songs. Time is always returned with
RMC files."*

That is three of this project's standing problems in one container: which player a file needs, how
long each subsong is, and how many subsongs there really are. Compare what we do instead — HVSC's
md5 table for SID lengths (`docs/ARCHITECTURE.md` §14), game-music-emu reporting a flat 256 tracks
for KSS and HES whatever the file holds, and a timed search for the first audible track. Worth a
serious look before any UADE work starts, and possibly worth a look regardless of UADE.

**Nothing was reported upstream.** The owner closed the correspondence on 2026-09-05 and the draft
sits unsent in `docs/letters/`. Both maintainers volunteered all of the above without being asked
twice, which is the part worth remembering.

### What UADE is actually worth — and the finding that undercuts it

In the corrected historical top-25 run, formats with a successful sample cover **9,813 Modland
files**. **5,558 of those are already playable by libopenmpt**, which is in the APK — OctaMED
`.mmd0`…`.mmd3` (5,111) and Oktalyzer `.okta` (447), measured 6/6 each. Widening the sweep from 60
formats to 150 added two more of the same kind, Graoumf Tracker `.gtk` and MultiMedia Sound `.mms`,
for 5,653 in total; nothing else in Modland's unplayable third turns out to be free, and the rest
of it is console dumps (`.minigsf`, `.mini2sf`, `.minipsf` — 66,000 files) that no backend here
opens. libopenmpt identifies MED by
an "MMD" magic in the header and never looks at the filename. The app simply never offers them,
because `SupportedFormats.extensions` lists `med` and `okt` while Modland stores these as `.mmd1`
and `.okta`.

Another part of the earlier correction remains valid: libopenmpt does **not** play AHX or HVL — no
loader exists in its source, neither name appears in its format table, and 0 of 12 AHX plus 0 of 6
HVL files opened. Those 1,433 Modland files had been counted as "already ours" merely because the
extension list claimed them. The old text then turned that correction into an exact 5,596-file
exclusive total. The corrected probe shows why that precision was unjustified: Hippel COSO and
successful directories newly entering the current top 25 were absent from that arithmetic. The
5,799-file sampled-directory lower bound above is the defensible figure.

**The current top-25 run proves successful reach into directories holding 5,799 files**, including
TFMX, Musicline Editor, Delitracker Custom, Sonic Arranger, BP SoundMon, Art Of Noise, David
Whittaker, SidMon 2, Delta Music 2, YMST and Hippel COSO. It is a lower bound on useful reach, not an
exact count of playable files: twelve samples cannot certify a whole directory, while smaller
successful directories outside the top 25 are omitted. That is still a real body of music and the
music this project is closest to, but it is a fraction of the tens of thousands assumed in
`docs/BACKLOG.md` A5.

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

**The licence half moved on 2026-09-04**, after UADE's maintainers answered: the replay binaries
are to be downloaded rather than shipped, from <https://zakalwe.fi/uade/download.html>, the page
upstream publishes for exactly that, with GitLab as the fallback when that server is not up.
`docs/LICENSES.md` has what the exchange settled. That removes the objection that made this item's cost look
open-ended — what remains is the emulator, the process model and at least the 5,799 files covered
by successful directories in the current top-25 sample.

### What this measurement recommends

**Not integrating UADE yet, and doing the cheap thing first.** Setting out the trade rather than
the conclusion, because the conclusion is the owner's:

- the current top-25 sample finds successful formats covering **5,799 Modland files** that nothing
  in the app claims — a measured lower bound, not the tens of thousands A5 assumed;
- it cannot ship without answering a licence question that is *worse* documented than sc68's, and
  unlike sc68 it has **no middle option** — without the replay binaries it plays 12 files in 300;
- it is the only backend here that is a second process, or else a single-instance-per-process
  emulator inside ours, which collides with scanning-while-playing;
- and **5,558 files in the historical top-25 set were already playable** by a backend in the APK,
  blocked by five missing lines in `SupportedFormats`.

The last point was worth acting on immediately and already has been: almost as many files as the
current sampled UADE lower bound became visible for five strings and a re-index. That does not make
UADE not worth doing — TFMX and David Whittaker are exactly the music this project is for — but it
does mean UADE is a considered choice with a licence decision attached, rather than the obvious
next step.

Everything needed to revisit it is committed: `./scripts/build-uade-probe.sh` rebuilds the whole
toolchain from nothing, and `./scripts/probe-uade.py` re-runs the measurement on the same seeded
corpus.

---

## 5. Modizer as a map — surveyed 2026-09-05

<https://github.com/yoyofr/modizer>, an iOS chiptune player, raised by the owner as worth looking at.
It bundles **48 decoder libraries** and its README links each one to its own upstream. That map is
the value here, and it lines up almost exactly with what we measured as unplayable.

**The full list is copied to `docs/reference/modizer-libraries.md`** — all 50 entries with their
homes, split into what we already have, the 39 we do not, and the iOS furniture that is not a
decoder at all. Kept because reconstructing it means reading somebody else's Xcode project. The
table below is the part that matters; that file is the part that would be tedious to find twice.

**Take nothing from the repository itself.** It has **no licence file**, which means all rights
reserved — an aggregator's convenience copy, not something to vendor from. Every library in it has
its own home and its own terms, and those are where to go.

### What it covers that we do not

Matched against the Modland counts in §0b and §2, largest first:

| unplayable here | files | what Modizer uses |
| --- | ---: | --- |
| Nintendo DS `.mini2sf` | 31,117 | [VIO2SF](https://bitbucket.org/kode54/vio2sf/), [in_xsf](https://github.com/CyberBotX/in_xsf) |
| Gameboy `.minigsf` | 23,775 | [PlayGSF](https://github.com/yshui/playgsf) |
| Spectrum `.pt3`/`.pt2` | 22,631 | [PT3Player](https://github.com/Volutar/pt3player), [ZXTune](https://bitbucket.org/zxtune/zxtune/) |
| Playstation `.minipsf` | 16,840 | [Highly Experimental](https://gitlab.com/kode54/highly_experimental/) |
| FMP `.ovi`/`.opi` | 12,801 | [FMPmini](https://github.com/myon98/98fmplayer) |
| MDX | 7,467 | [mdxmini](https://github.com/gzaffin/mdxmini) |
| Ultra64 `.miniusf` | 6,462 | [LazyUSF](https://github.com/derselbst/lazyusf) |
| Saturn / Dreamcast | 10,081 | [Highly Theoretical](https://gitlab.com/kode54/highly_theoretical/) |
| Euphony `.eup` | 3,067 | [Eupmini](https://github.com/gzaffin/eupmini) |
| PMD | 1,510 | [Pmdmini](https://github.com/mistydemeo/pmdmini) |
| **AHX / HVL** | **1,433** | [HivelyTracker](https://github.com/pete-gordon/hivelytracker) |

**AHX is the one to notice.** It is 1,433 files we removed from `SupportedFormats` on 2026-09-04
because nothing here plays them, and the fix is a small standalone library rather than the whole of
UADE. That makes it the cheapest single win on this page — and it shrinks UADE's exclusive
contribution again, from 5,596 to about 4,160.

The `*SF` family alone — DS, Gameboy, Playstation, Ultra64, Saturn, Dreamcast — is roughly **95,000
files**, which is more than everything the app currently plays from Modland outside the trackers.
They are also the least like this project: emulator cores for machines nobody would call a chiptune
platform, and `docs/PLAN_FORMATS.md`'s own opening argument was that volume is not the only measure.

### The other half: it integrates catalogues we do not

Its description names **vgmrips, snesmusic and zxart** alongside Modland, HVSC and ASMA. Those are
three archives `docs/PLAN_CATALOGUES.md` has never looked at, and at least two of them are exactly
the platforms `docs/WISHLIST.md` B23 would want to offer.

### What to do with this

**Nothing yet, and deliberately.** This is a survey, not a plan: it says which library to fetch when
a format is wanted, so that question never has to be researched again. Each one still needs the five
things `docs/PLAN_FORMATS.md` opens with — a licence checked against its sources, a host probe, a
measurement, and a re-index — and the licences here are genuinely varied (kode54's GitLab projects,
ZXTune's Mercurial-era tree, a Bitbucket repository).

**If one is picked first, AHX.** Smallest library, a format we already know we are missing by name,
and the only entry on this page whose absence is currently a lie in the extension list rather than a
gap in ambition.

*Picked, and done the same day — §6.* It took one afternoon, which is the argument for this survey
in miniature: the research had already been done, so the work was building and measuring.

## 6. ~~AHX and HivelyTracker~~ — DONE 2026-09-05

`.ahx` and `.hvl` were removed from `SupportedFormats.kt` on 2026-09-04 because §0b found libopenmpt
has no loader for either. That was the right removal and it left the only gap in the format table
that is **our own doing**: 1,433 Modland files the app used to list and now does not, missing not
because the format is out of scope but because nothing we vendor could open it.

HivelyTracker closes it, and it is the cheapest backend on this page by a wide margin.

### What it is

`hvl2wav/` inside `pete-gordon/hivelytracker` is a standalone replayer: **`replay.c`, `replay.h`,
`types.h`**, about 70 KB, plus a command-line example that renders to a WAV. No build system to
configure, no second process, no replay binaries, no emulated machine. Compare the four before it:
sc68 needed its own SVN fetcher and 99 replay binaries, UADE needed two support libraries and a
`uadecore` it forks and talks to over pipes, gme was "the only vendored library that ships a working
CMake", ASAP was a transpiled single file — this is smaller than ASAP.

**BSD 3-Clause, Copyright (c) 2006-2018 Pete Gordon.** The licence question that cost a day each on
sc68 and UADE does not arise: permissive, no replay-binary distribution problem, compatible with our
GPL-3 without argument.

`hvl_reset(buf, len, …)` loads **from memory** and dispatches AHX (`THX`) and HVL itself, so the
header gate the backend needs already exists upstream. That matters more than it sounds: the app
never has a path, only bytes from SAF, and two of the five existing backends needed work to accept
that.

### The measurement

`./scripts/build-hively-probe.sh` then `./scripts/probe-hively.py`, 40 files of each name sampled
from Modland:

**80 of 80 loaded from a buffer and were audible. 80 of 80 reached a song end.**

That is the first backend to come back clean on both halves. Lengths run **4s to 307s, median 97s**,
which is a real duration from the replayer itself — `.ahx` and `.hvl` would show a seek bar without
needing `SongLengths` at all. None of the 80 had more than one subsong; AHX supports them
(`ht_SubsongNr`, byte 13 of the header) but Modland's do not use them, so the subsong path is
present and untested rather than absent.

Modland holds **1,389 `.ahx` and 44 `.hvl`**, all filed as suffixes — no prefix convention here, so
the mistake that made UADE's reach look like 807 files instead of 29,127 does not repeat.

### The one portability finding, and why the probe is built twice

Upstream's `types.h` says `typedef unsigned long uint32`. That is 32 bits on the Amiga this replayer
comes from and **64 bits on arm64 and x86_64** — two of the app's three ABIs. The third,
**armeabi-v7a, is 32**. So the two widths are not hypothetical; they are targets we ship, and a
replayer that behaved differently between them would be broken on exactly the older phones nobody
here tests.

The probe is therefore built twice, once with upstream's typedefs and once with fixed-width ones
forced in through `-include`, and every file is run through both. **They agree on every file about
what plays, how many subsongs it has, whether it ends and how long it is.** 16 of 80 render a
slightly different peak sample — the two widths do not wrap identically, most likely the noise
generator — so arm64 and armeabi-v7a will not be sample-identical. That is a difference in the last
bits of a chiptune's noise channel, not a difference in what plays.

The first attempt at this comparison measured nothing at all: `replay.c` says `#include "types.h"`,
and the quoted form searches the including file's own directory first, so an `-I` override reached
the probe and not the replayer. Two translation units then read the same struct at different
offsets, which looks exactly like a portability bug and is not one — it reported subsong counts of
5, 11 and 17 for files whose header byte says 1. The header byte is what settled it.

### What integration actually needed

`HivelyBackend` in `native/engine/engine.cpp`, and three things the probe had turned up:

- **The replayer emits one PAL frame per call.** `hvl_DecodeFrame` fills exactly one 1/50s buffer
  and chooses that size itself; every other backend renders however many frames it is asked for. So
  this is the only backend with a ring buffer. The size is `rate/50/multiplier*multiplier`, which is
  **880, not 882**, when the speed multiplier is four — taking the obvious 882 would have emitted
  two stale samples every frame, fifty times a second.
- **The loader never checks the file's length.** It walks the header computing sizes, and
  `strncpy(ht_Name, &buf[(buf[4]<<8)|buf[5]], 128)` takes its offset straight from the header. A
  truncated file reads past the end. Restating its walk in the backend would be a second copy of a
  parser, so instead the bytes go in inside a zeroed allocation with **a mebibyte of slack** — and
  that number is arithmetic, not a guess: every count the loader reads is a byte or a twelve-bit
  field, so the worst walk any header can ask for is under 960 KB. Zeros also terminate both walks
  early, since a zero row is not `0x3f` and a zero instrument has no program.
- **`replay.c` prints to stdout** on its error paths, including a bare `"Invalid file."`. The
  backend gates the two header magics itself so the library is never asked a question it would
  answer that way.

**The length comes free, and that is the nice part.** `hvl_play_irq` is the sequencer without the
mixer — about 1ms to run a tune to its end where full decoding takes 30 — so `HivelyBackend`
measures the duration at load, unconditionally. Sequencer-only and full-render lengths were checked
against each other on the host and agreed on every file. AHX and HVL therefore arrive with a real
duration and a working seek bar, which sc68 and libsidplayfp still do not have; seeking re-starts
and decodes forward, which is the only way in and costs about 30ms per minute skipped.

Not measured, because Modland has none: **subsongs**. The path is written and the arithmetic is
upstream's (`ht_SubsongNr` is the count of *extras*, so a plain file reports one tune), but nothing
in 80 files exercised it.

## 7. ZX Spectrum — measured 2026-09-07, **blocked on a licence**

`./scripts/probe-platforms.py` says ZX Spectrum holds **23,891 Modland files and this build plays
58**. It is the largest dark platform in the archive and the only large one that is a chiptune in
the same sense as the rest of the app: `.pt3`, `.pt2`, `.stc`, `.asc` and `.sqt` are trackers
driving an AY-3-8912, not console emulators. The `*SF` family is bigger — about 95,000 files across
PlayStation, DS, GBA and N64 — and is emulator cores, which is a different kind of program to put
inside this one.

### The formats work, and the number is not close

`./scripts/build-ayfly-probe.sh` then `./scripts/probe-ayfly.py`, against **ayfly** — a ~900 KB AY
replay library whose `players/` covers every name Modland files under Spectrum:

**46 of 48 sampled files loaded from a buffer and were audible — 99.6% weighted by what Modland
actually holds, of 21,639 files.** The two failures are both `.psc`, a format worth 239 files.

Every one of them **states its own length**, median 130s. So these files would arrive with a
duration and a working seek bar, the way AHX did, with nothing stored and no song-length database.

It also renders into a buffer of whatever size it is asked for — `ay_rendersongbuffer`, interleaved
16-bit stereo — so unlike HivelyTracker it needs no ring buffer. Technically this is the easiest
integration since ASAP.

### And it cannot be shipped

**`docs/LICENSES.md` says to stop here and raise it rather than work around it, so that is what this
is.** Three findings, in increasing order of seriousness:

- **The repository carries no `LICENSE` or `COPYING` file at all.** Not in the root, not in `src`,
  and no licence statement in `README`, `configure.ac` or `ChangeLog`. GitHub's API reports no
  licence for it.
- **The twelve player headers carry no notice** — and they are precisely the files we need. Of the
  27 sources in `libayfly`, 15 carry GPL-2.0-**or-later**, which would be fine; the other 12 are
  `players/PT3Play.h`, `PT2Play.h`, `STCPlay.h`, `ASCPlay.h`, `SQTPlay.h` and their siblings, and
  they carry an author credit and nothing else. `PT3Play.h` opens with *"Pro tracker 3.x player was
  written by S.V. Bulba"* and no terms.
- **`z80ex` says "Released under GNU GPL v2"** with no "or later". **GPL-2-only is incompatible with
  our GPL-3**, and it is not separable: five of the eight library sources reference it, including
  `ay.cpp` and `formats.cpp`, not only the `.ay` path one might hope to drop.

This is the case `docs/LICENSES.md` warns about in as many words — "a dependency that turns out to be
GPL-2-**only** cannot be combined with our Apache-2.0 UI stack under GPL-3, and invalidates the
licence decision". It is the fourth time in this project that reading `COPYING` alone would have
given the wrong answer, and the first time the answer is no.

### What to do instead

**ZXTune** — named in `docs/reference/modizer-libraries.md`, LGPL-3.0 by GitHub's reading, covering
every one of these formats and many more. It is a 182 MB C++ project with its own build system,
which is why it was not tried first; the measurement above is the argument for paying that cost,
because it says the formats themselves are worth 21,639 files at 99.6%.

### ZXTune checked, 2026-09-07: the licence is clean and the size is not the problem

**Licence.** `LICENSE.md` in the root is the LGPL-3 text, and the sources we would build carry no
per-file notice contradicting it — a doxygen `@file`/`@author` block and nothing else. LGPL-3
combines with our GPL-3 without argument.

**`3rdparty/` is where the danger was, and it is avoidable.** It bundles 34 components, `z80ex`
among them — the same GPL-2-only Z80 emulator that blocked ayfly. In ZXTune it is reached from
exactly two files: `src/devices/z80/z80.cpp`, which wraps it, and `src/module/players/aym/ayemul.cpp`,
which is the plugin for `.ay` — Z80 machine code rather than tracker data. **Leave that one file out
and z80ex never enters the build.** It costs Modland's "AY Emul" directory, 1,202 files of 23,891,
and none of the tracker formats. That is not working around a licence; it is not using the
component.

Compiling the whole AY path with everything else missing, and collecting what it asked for, the
answer is **one** third-party dependency: **`fmt`**, MIT. (`src/sound/impl/resampler.cpp` reaches
for `lazyusf2`, an N64 emulator, and is not needed — Oboe does our rate conversion.)

**Size, which is the owner's question.** The repository is 182 MB and almost none of it would ship:

| | |
| --- | --- |
| `3rdparty/` | 280 MB — **none of it built** except `fmt` |
| checked-in HVSC song-length databases under `src/core` | ~60 MB of `.md5` files, not code |
| **the AY source set we would compile** | **1.9 MB of C++ in ~235 files** |

For scale, the libopenmpt and game-music-emu sources this project already compiles are **36 MB**,
and the release APK is 14 MB. ZXTune's AY support is about 5% of the source already going through
the compiler. It is not a 100 MB proposition.

**And it compiles.** `-I src -I include -I .` under **C++20**, which is already this project's
standard — `-std=c++17` fails on `std::to_address` and a `concept` declaration, which is the sort of
thing that would otherwise be discovered halfway through a build.

### Measured 2026-09-07: 72 of 72

`./scripts/build-zxtune-probe.sh` then `./scripts/probe-zxtune.py`, twelve files of each format
sampled from Modland:

**72 of 72 loaded from a buffer and were audible — 100% weighted by what Modland holds, of 20,521
files.** ayfly, which cannot be shipped, scored 46 of 48 on the same corpus.

**Every one states its own length**, median 149s. So `.pt3`, `.pt2`, `.stc`, `.asc`, `.sqt` and
`.stp` would arrive with a duration and a working seek bar, with nothing stored — the third backend
to manage that, after AHX and the trackers.

### What it cost, and what the next person should know

The formats were never the difficulty. Three days of this page were spent on libraries that play
things; this one was spent on a build.

**The dependency closure has to be discovered by compiling.** A sweep of `src/module` pulls in the
PlayStation, DS, GBA and N64 players, each wanting a different part of `3rdparty`; `src/sound`
pulls in ALSA and OSS; `src/binary` pulls in zlib and lhasa; `src/l10n` wants Boost. None of that is
on the path from the bytes of a `.pt3` to samples, and the only way to learn which files are is to
build and read the linker. The answer is **90 sources**, listed in the build script.

**Three things were wrong in the probe and each looked like the library failing:**

- **Two shapes of factory.** ProTracker3 hands back a full `Module::Factory` that produces a holder;
  every other AY plugin hands back an `AYM::Factory` that produces a *chiptune*, and
  `AYM::CreateHolder` is the step between. The plugin layer that normally does this drags in the
  whole plugin registry, so the probe does it by hand — and the backend will have to as well.
- **`binary/format/full` versus `lite`.** Both define `Binary::CreateFormat`; `lite` drops the
  pattern syntax the chiptune decoders' format strings use. Building neither is one undefined symbol
  at the end of a ninety-file link.
- **The renderer needs the module's own properties.** `CreateRenderer` was given a fresh empty
  parameter container, and the AY renderer reads its frequency table from parameters —
  `aym_parameters.cpp` says *"frequency table is mandatory!!!"* and throws. It throws at the first
  `Render`, not at construction, so all 48 files aborted with `exit-6` and it looked like a decoder
  that could not decode. The table is something the *file* chose and the chiptune had already put
  there; `holder->GetModuleProperties()` is what carries it.

**And one thing about the build itself:** a single `g++` over ninety sources recompiles all of them
every time an include path turns out to be wrong, which on a library this size is most of an
afternoon. The script compiles one object per source, in parallel, and keeps them.

### Integrated 2026-09-07 — and the phone found four things the host could not

`ZxTuneBackend`, ZXTune's 91 sources building for all three ABIs, and thirteen names back in
`SupportedFormats`: `pt3`, `pt2`, `pt1`, `stc`, `st1`, `st3`, `asc`, `as0`, `sqt`, `stp`, `psm`,
`ftc`, `gtr`.

**The release APK goes from 14.2 MB to 17 MB.** That is the answer to the question that opened this
section: ZXTune costs **2.8 MB across three ABIs**, not the 100 MB its 182 MB repository suggests.

**Every remaining obstacle was clang against GCC**, which is exactly what a host probe cannot show:

- **`std::char_traits<uint16_t>` does not exist in libc++.** ZXTune's `encoding.cpp` uses
  `basic_string_view<uint16_t>`; libstdc++ still has a generic primary template and libc++ has only
  the character types the standard names. The file was dropped for one round and the link refused —
  `sanitize.cpp` calls `ToAutoUtf8`, and sanitising is what cleans a title before it is shown. A
  `char_traits` shim is force-included into that one translation unit, and
  `native/backends/zxtune/uint16_char_traits.h` says plainly what that costs.
- **`fmt`'s compile-time format checking does not survive this clang.** The fix is
  `-DFMT_CONSTEVAL=`, and the *first* attempt used `FMT_USE_CONSTEVAL=0`, which `core.h` consults
  only when `FMT_CONSTEVAL` is undefined — so it changed nothing and produced the identical error a
  second time.
- **Two vendored libraries ship a `types.h`.** ZXTune's defines `uint_t` and `int_t`, which its
  other headers use and none of them includes; HivelyTracker's is on the include path too and won.
  The error surfaced sixty lines away, inside a third library's header, saying a type did not exist.
  `#include <include/types.h>` is the disambiguation and the path is load-bearing.
- **`RangeChecker` lives in `src/tools/src`, not `src/tools`**, which holds only headers. One glob
  short, four undefined symbols.

**What the app does with them**, beyond playing: ZXTune states a duration for every one of these
files, so they arrive with a working seek bar; `Platforms` claims the names, so the ZX Spectrum chip
in the search filter stops being greyed out; and changing `SupportedFormats` changes
`backendsFingerprint`, so every index built before today is stale and will be rebuilt.

`.ay` stays with game-music-emu. ZXTune's own reader for it is `ayemul`, the one plugin whose
licence cannot be taken.

### The owner ran it, and found the list promising more than the code delivered

`SupportedFormats` and the backend's `worthTrying` claimed **thirteen** names. `ZxTuneBackend`
implemented **ten**. So `.psm`, `.ftc` and `.gtr` were offered, taken, refused — and the app said
*"is a format Protracktor cannot play yet"*, which is the message for a missing decoder and was true
only because a list of factories was three lines short. `.pt1` was a fourth instance, found a moment
later by the probe rather than by him.

**The probe now measures what `SupportedFormats` promises**, which is the only guard that crosses
the language boundary: the format list is Kotlin and the decoders are C++, so no unit test can
compare them. With all thirteen: **73 of 80, 99.5% weighted by what Modland holds.**

The seven failures are honest and worth reading. Six are `.psm` files from Modland's *Epic Megagames
MASI* directory, which ZXTune's Pro Sound Maker decoder correctly refuses — in the app those fall
through to libopenmpt and play, which is a fallback the probe does not have. One `.ftc` did not load.

### And a second thing he found, which was not about ZX Spectrum at all

`Could not read ice.pt2` — then it played on the second attempt. That message comes from the path
where the file never arrived, `bytes == null`, before any decoder sees it: a transient fetch failure
from Modland.

**The two messages send you to opposite places and look alike from an armchair.** "Could not read"
is the network; "cannot play yet" is a missing decoder. Both of us spent a few minutes looking at
the wrong one. It is the same shape as C15, where a swallowed failure and an honest absence were
reported identically — worth a defect of its own rather than a note here.

### `.stc` — some files, not the platform, and not our doing

The owner reported `.stc` not playing, then narrowed it himself: `#######.stc` and `(letsgo).stc`
play, `&SFTDEMO.stc` does not, and the message is *"Spectrum is a format Protracktor cannot play
yet"* — the decoder refusing, not the network.

**Reproduced on this machine with the same files**, which moves it out of Android entirely.
`&SFTDEMO.stc` and `Info1.stc` are refused; the other two play. All four have plausible Sound
Tracker headers — a tempo byte and ascending section pointers — so they are not packed files wearing
the wrong name.

**Measured: 76 of 80 sampled `.stc` play, 95%.** Across Modland's 3,639 that is roughly 180 files
ZXTune's Sound Tracker decoder will not take.

All three variants are already tried — `Ver1` compiled, `Ver1` uncompiled, and `Ver3`, which is every
one ZXTune's own plugins use for this extension. So ZXTune refuses these files too; this is not a
gap in the integration, and closing it would mean changing somebody else's decoder.

Three causes ruled out along the way, each worth writing down so nobody pays for them twice:

- **Not the buffer's lifetime.** `Binary::CreateContainer(View)` copies into a `Dump` rather than
  referencing the caller's bytes. Worth checking because the probe keeps its bytes alive to the end
  of `main` and `openBackend` does not.
- **Not `char` signedness.** Plain `char` is signed on x86-64 and unsigned on ARM, the classic
  reason a decoder works on a desktop and fails on a phone.
  `./scripts/build-zxtune-probe.sh` now takes `PROBE_CHAR_FLAGS=-funsigned-char` and builds a second
  binary beside the first: **0 of 25 differ.** The switch stays, because the next "works here" will
  want it.
- **Not another backend taking the name.** ASAP's extension list has no `stc`, and game-music-emu
  identifies by header.

**Nothing else has been heard on a phone.**

### Two things the probe found that were not about the formats

- **`ay_startsong` dereferences the song's audio player without checking it for null**, while
  `ay_songstarted` three lines away does check. Rendering into a buffer needs no player, so the
  first run segfaulted on all 32 files. It is not needed at all: `ay_rendersongbuffer` drives the
  chip directly.
- **`ay_getsonglength` returns fiftieths of a second**, which `ayfly.h` says on the field and
  nothing repeats. Read as milliseconds it made every tune about six seconds long — plausible
  enough to be believed, and wrong. The median is 130 seconds.

## 8. ~~`.ym` and `.vtx` — the LHA half of C20~~ — DONE 2026-09-09

*`docs/STATUS.md` C20, found by the wasm probe on 2026-09-08: 4,961 Modland `.ym` files were in
every index and no backend could open one. The entry offered two ways out and did not choose. The
owner chose the second — "druga droga warta pracy" — on 2026-09-09.*

**The diagnosis in C20 was one clause too pessimistic.** It said sc68 has no YM loader and Modland's
YM files are LHA-packed, both true, and concluded that nothing here could open one packed or not.
That last part was wrong: **ZXTune has a YM decoder** and we were not building it.
`native/backends/zxtune/CMakeLists.txt` had excluded `formats/chiptune/aym/ym_vtx.cpp` since the
backend was written, with the reason recorded honestly at the exclusion — *"VTX is LHA-compressed
and would pull in `3rdparty/lhasa` for 879 files"*. What that note could not know is that the same
dependency was about to be wanted for a second, larger reason, at which point 879 stopped being the
number to weigh it against.

**One dependency, two wishes.** UnExoticA — the Amiga game-soundtrack archive
(`docs/PLAN_CATALOGUES.md`) — keeps every tune inside a `.lha`, so it needs LHA extraction before it
needs anything else. Vendoring `lhasa` once serves both. It is ISC, all 35 sources verified per file
(`docs/LICENSES.md`), and it is the only library in `native/vendor/` that decodes no music.

**Upstream lhasa, not ZXTune's.** ZXTune bundles an older lhasa plus a patch, and its own
`binary/compression/src/lha.cpp` includes it by a path that exists only inside its tree; its patched
`lha_decoder_for_name` also returns a non-const pointer where upstream returns a const one, so that
file does not compile against the real library. `native/backends/zxtune/lha_zxtune.cpp` implements
the same two-function interface against upstream in forty lines. Vendoring a second, forked copy of
a library to avoid writing forty lines is not the cheaper bargain.

### Measured 2026-09-09, `./scripts/probe-zxtune.py --files 20`

| | sampled | played |
|---|---|---|
| `.ym` | 20 of 4,961 | **20** |
| `.vtx` | 20 of 879 | **20** |

Every one loaded from a buffer, was audible in the first eight seconds, and stated a duration — so
they arrive with a seek bar and need no song-length database. Overall for the whole AY set:
223 of 240, about **98.4% of 26,678 Modland files**.

**`.vtx` was added to `SupportedFormats` and `.ym` moved.** `.ym` had been sitting in the Atari ST
group since the start, on the assumption sc68 handled it; nothing did. `.vtx` is a new name and so
changes `SupportedFormats.fingerprint`, which marks every stored index stale — the owner
re-downloads Modland's 40 MB. C20 argued against paying that price to *remove* dead rows; paying it
for 879 files that play is the other side of the same bargain.

**`.med` is still open.** The other half of C20 — 132 Modland files in the older Amiga *Music
Editor* format, which libopenmpt's loader rejects because it wants `MMD` at offset zero. Nothing
here decodes it and lhasa does not help.

## Not planned

`.sc68` container files reference external replay binaries we do not ship, so they will not play even
though sc68 is present. Worth a line in the UI eventually; not worth shipping the replay set for.
