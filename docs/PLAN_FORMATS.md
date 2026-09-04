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
| `.ahx` | 0/12 | 1,389 | **libopenmpt has no AHX loader at all** — removed from the list |
| `.hvl` | 0/12 | 44 | same; HivelyTracker, no loader — removed |
| `.ftm` | 1/12 | 1,874 | libopenmpt's FTM is **Face The Music**; Modland's are **FamiTracker** |
| `.imf` | 3/12 | 210 | libopenmpt's IMF is **Imago Orpheus**; Modland's are **id Software AdLib** |
| `.psm` | 6/12 | 141 | libopenmpt's PSM is **Epic MegaGames MASI**; half of Modland's are **Spectrum Pro Sound Maker** |
| `.med` | 1/12 | 140 | magic `MED\x04` — **Music Editor**, the older Amiga format, not MMD |

**Five of those six are one extension standing for two unrelated formats**, which is precisely what
`docs/ARCHITECTURE.md` §5 says extensions do in this world. It is now measured rather than asserted:
2,365 Modland files are filed under a name whose owner is a different program.

**Nothing more was removed.** Each of those extensions genuinely loads the format libopenmpt
implements; the failures are files that merely share the name, and dropping the extension would
throw away the real ones with them. `.ahx` and `.hvl` were different — **no** loader exists, so the
entries were pure loss, and they come back the day UADE lands.

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
format. Heikki Orsila caught this by playing `amberstar (03).hipc` with stock `uade123`; removing
the option made the exact file render here too with player `Hippel-COSO`.

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
reference set Heikki pointed at: `git clone git://zakalwe.fi/chip`, which he describes as UADE's
test-case repository, with an example of every format it supports.

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
`UC_CONTENT_DETECTION`, a second confident wrong theory is the thing most worth avoiding — the
observation is reported to UADE's maintainer and the explanation left to him.

**What follows from it for us:** `zakalwe.fi/chip` is the reference set now, not Modland. Modland
remains what the app *browses*, but a format measured only against it is a format measured against
one ripper's habits.

### And then Matti Tiainen answered, and this section was wrong too

Within the hour, UADE's other maintainer replied. The Hippel and TFMX families are a mess, he said —
not the formats themselves but the way they are distributed, with different collections and sites
using prefix and suffix conventions that contradict each other. And he pointed at the fix he already
maintains: a `song.conf` of **md5 overrides** at
<https://github.com/mvtiaine/audacious-uade/blob/master/conf/song.conf>, which he said covers the
Modland files in question.

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

Heikki added it for the record. RMC was designed as a single-file format that states exactly what a
song is and what it needs: a torrent-like container holding an optional player, the song itself, and
metadata including subsong durations. He describes it as solving many of these compatibility
problems. — `git clone git://zakalwe.fi/rmc-chip`.

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

**The licence half moved on 2026-09-04**, after UADE's maintainer answered: the replay binaries are
to be downloaded rather than shipped, from <https://zakalwe.fi/uade/download.html> which he offers
for exactly that, with GitLab as the fallback when his server is not up. `docs/LICENSES.md` has
what he said and what it settles. That removes the objection that made this item's cost look
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

## Not planned

`.sc68` container files reference external replay binaries we do not ship, so they will not play even
though sc68 is present. Worth a line in the UI eventually; not worth shipping the replay set for.
