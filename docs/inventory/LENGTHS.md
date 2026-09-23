<!--
SPDX-FileCopyrightText: 2026 Przunk
SPDX-License-Identifier: GPL-3.0-or-later
-->

# Which of Modland's tunes have no length — B36 step 1, 2026-09-23

*`docs/WISHLIST.md` B36 asked, before building a length database: is the gap 5,000 NSFs, or 50,000
files in many formats? This is the answer, counted where it can be and sampled where it must be.*

Produced by `scripts/inventory-lengths.mjs` (seed 1: 20 files per format directory, 150 for NSF);
every row is in `lengths.tsv` beside this file. Re-run it and the same files are drawn and read from
the cache under `~/.protracktor/inventory` (535 MB after the run), so Modland is not asked twice.

## How a file was judged

The app's own order (`LengthSource`), asked of each sampled file:

1. **the file, through its decoder** -- the engine the page runs, which is the phone's backends
   built to WebAssembly: libopenmpt, sc68, ASAP, game-music-emu, libsidplayfp, HivelyTracker. For
   game-music-emu this includes the measurement of a tune that falls silent (variant (a), 2026-09-22);
2. **HVSC**, by the file's full MD5;
3. **songdb**, by the first twelve characters of it, when its first subsong's end code is a length
   the app uses (`SongDbLengths.End.isLength`).

A file none of these gives a length is **no length before playing**. UADE is not in the page's engine,
so a UADE format is asked of songdb only -- what UADE measures by playing a tune once (A50) is not
counted as known. ZXTune is not in the page's engine either, and its formats were **not measured**.

## The answer

**About 7,800 of the 315,000 files measured have no length before they are played -- 2.5%. Nearly
all of them are console music played by game-music-emu.**

| decoder | files | sampled, heard | no length | estimated without length |
| --- | ---: | ---: | ---: | ---: |
| game-music-emu | 57,818 | 250 | 149 | **6,768** |
| libsidplayfp | 64,697 | 34 | 3 | 708 |
| ASAP | 3,230 | 19 | 1 | 170 |
| UADE (songdb only) | 3,824 | 654 | 17 | 120 |
| libopenmpt | 177,012 | 1,082 | 0 | 0 |
| sc68 | 7,259 | 40 | 0 | 0 |
| HivelyTracker | 1,434 | 40 | 0 | 0 |

Where the 7,800 are:

| directory | files | heard | no length | estimate | how firm |
| --- | ---: | ---: | ---: | ---: | --- |
| Nintendo Sound Format | 5,015 | 150 | 113 | **3,778** | firm: three in four NSFs |
| Nintendo SPC | 36,903 | 20 | 1 | 1,845 | **weak**: one file of twenty, times 37,000 |
| Gameboy Sound System | 918 | 20 | 17 | 780 | firm: nearly all |
| RealSID | 3,540 | 15 | 3 | 708 | fair: SIDs outside HVSC |
| KSS | 393 | 20 | 10 | 197 | fair |
| Slight Atari Player | 3,230 | 19 | 1 | 170 | weak, and small |
| HES | 421 | 20 | 8 | 168 | fair |
| UADE formats, together | 3,824 | 654 | 17 | 120 | firm: songdb covers them |

The SPC figure is one file in twenty multiplied out, and could be a tenth of it or several times it;
SPC normally states its length in its ID666 tag, which is why nineteen of twenty had one.

### Not measured here

- **ZXTune: 26,589 files** -- Spectrum (21,581), YM (4,962) and a handful of others. ZXTune is not
  in the page's engine. Its backend reports the length ZXTune computes from the module, so most are
  probably known, but that is read from the code and **not measured**; the phone's own probe
  (`native/probe/zxtune`) would answer it.

### Found on the way: 3,803 files the page offers and cannot open

Twelve directories where **not one sampled file opened** in the page's engine -- counted apart
rather than as "no length", because a file that does not open has no length question to answer.
The big four, and why (asked of one file each, 2026-09-23):

| directory | files | the name says | libopenmpt reads that name as |
| --- | ---: | --- | --- |
| Deflemask | 1,807 | `.dmf` | X-Tracker's DMF |
| FamiTracker | 1,779 | `.ftm` | Face The Music, an Amiga format |
| Music Editor | 132 | `.med` | OctaMED's MMD; this older MED it refuses |
| SidMon 1 | 61 | `.sid` | a C64 SID, for libsidplayfp -- it is an Amiga format |

The same kind of collision as A51's `.gtk`, at a thousand times the scale: the index decides by
name, the name belongs to another format, and the file is offered and refused. Whether the phone,
which also has UADE, plays any of these is **not checked**. Recorded as `docs/STATUS.md` C88.

### Also answered: C81's Famicom Disk System NSFs

**None of the 150 NSFs sampled** loads below $8000 with the FDS chip -- the case of
`19 neunzehn.nsf`. They are rare: with none in 150, fewer than about 2% (at most around a hundred
files) is what the sample allows. C81 (b), teaching game-music-emu the FDS memory map, is for a
handful of files.

## What it means for B36

- **The database is a console one.** About 5,000 files outside SPC -- NSF, GBS, KSS, HES -- and all
  four are game-music-emu formats whose music loops, which is exactly what step 2's loop detection
  is for. NSF alone is half the gap.
- **Not SID, not Amiga.** HVSC answers for 61,000 SIDs and songdb for the UADE formats; what is left
  there is RealSID's 700 and a hundred UADE files.
- **So step 2, the NSF pilot, is the right next step as planned**, and a success there carries to
  GBS, KSS and HES with the same hook.
