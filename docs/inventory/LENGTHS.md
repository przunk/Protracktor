<!--
SPDX-FileCopyrightText: 2026 Przunk
SPDX-License-Identifier: GPL-3.0-or-later
-->

# Which of Modland's tunes have no length — B36 step 1, 2026-09-23

*`docs/WISHLIST.md` B36 asked, before building a length database: is the gap 5,000 NSFs, or 50,000
files in many formats? This is the answer, counted where it can be and sampled where it must be.*

Produced by `scripts/inventory-lengths.mjs --zxtune native/probe/zxtune/build/probe-zxtune` (seed 1:
20 files per format directory, 150 for NSF, 300 for SPC; Spectrum per tracker); every row is in
`lengths.tsv` beside this file. The files are cached under `~/.protracktor/inventory`, so a re-run
asks Modland for nothing it already gave.

**Two runs, 2026-09-23.** The first had 20 SPCs and no ZXTune; the second, below, 300 SPCs and ZXTune
through its probe. Changing a sample size changes which files every later directory draws, so the two
drew different files from the same directories -- and **the gap between them is the honest size of
the error**: NSF 113 of 150 without a length in one, 102 of 149 in the other.

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
counted as known. ZXTune is not in the page's engine either, so its formats were asked of the host
probe (`native/probe/zxtune`), which is built from its own ZXTune checkout with the AY-family
players; the app's ZXTune is `native/vendor/zxtune` -- close, and not proven identical.

## The answer

**About 8,900 of the 342,000 files measured have no length before they are played -- 2.6%. All but
a thousand are console music played by game-music-emu.**

| decoder | files | sampled, heard | no length | estimated without length |
| --- | ---: | ---: | ---: | ---: |
| game-music-emu | 57,818 | 529 | 163 | **7,909** |
| libsidplayfp | 64,697 | 37 | 4 | 787 |
| ASAP | 3,230 | 18 | 1 | 179 |
| UADE (songdb only) | 3,824 | 654 | 13 | 59 |
| libopenmpt | 177,063 | 1,097 | 0 | 0 |
| ZXTune (probe) | 26,496 | 220 | 0 | 0 |
| sc68 | 7,259 | 40 | 0 | 0 |
| HivelyTracker | 1,434 | 40 | 0 | 0 |

Where the 8,900 are:

| directory | files | heard | no length | estimate | first run | how firm |
| --- | ---: | ---: | ---: | ---: | ---: | --- |
| Nintendo SPC | 36,903 | 300 | 28 | **3,444** | 1,845 (1 of 20) | fair: one SPC in eleven has no ID666 length |
| Nintendo Sound Format | 5,015 | 149 | 102 | **3,433** | 3,778 | firm: two NSFs in three, or three in four |
| RealSID | 3,540 | 18 | 4 | 787 | 708 | fair: SIDs outside HVSC |
| Gameboy Sound System | 918 | 20 | 14 | 643 | 780 | firm: most |
| HES | 421 | 20 | 11 | 232 | 168 | fair |
| Slight Atari Player | 3,230 | 18 | 1 | 179 | 170 | weak, and small |
| KSS | 393 | 20 | 8 | 157 | 197 | fair |
| UADE formats, together | 3,824 | 654 | 13 | 59 | 120 | firm: songdb covers them |

**ZXTune: 0 of 220** sampled files without a length, across every Spectrum tracker and YM -- its
players compute the length from the module. Picatune2 (39 files) and Soundtracker Pro II (3) the probe
refused whole; they are listed with the refusals below.

### Found on the way: 3,803 files the page offers and cannot open

Fourteen directories where **not one sampled file opened** (twelve in the page's engine, two in the
ZXTune probe) -- counted apart
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

**One of 149 NSFs** in the second run loads below $8000 with the FDS chip -- the case of
`19 neunzehn.nsf` -- and none of 150 in the first. Rare: somewhere under 2%, a few dozen files, at
most around a hundred. C81 (b), teaching game-music-emu the FDS memory map, is for a handful.

## What it means for B36

- **The database is a console one.** NSF and SPC about 3,400 each, GBS 650, HES and KSS 400 together
  -- 7,900 files, all game-music-emu's. NSF, GBS, HES and KSS loop in the way step 2's loop detection
  is for; SPC is the SNES's sound chip, whose writes can be watched the same way.
- **Not SID, not Amiga, not Spectrum.** HVSC answers for 61,000 SIDs, songdb for the UADE formats,
  ZXTune for itself; what is left is RealSID's 800, a few hundred SAP and UADE files.
- **So step 2, the NSF pilot, is the right next step as planned**, and a success there carries to
  GBS, KSS and HES with the same hook, and then to SPC.
