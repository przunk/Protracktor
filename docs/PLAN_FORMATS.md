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

## 0. `.sndh` — half fixed, and the rest needs a newer sc68

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

## 1. `libsidplayfp` — Commodore 64

The largest single body of music after trackers, and Modland alone lists **60,633** `.sid` files.

- Latest release **v3.1.1** (github.com/libsidplayfp/libsidplayfp, checked 2026-09-01).
- GPL-2.0-or-later — compatible, but verify against the sources.
- Builds with autotools; expect the same treatment sc68 got: a CMakeLists of ours listing the
  sources, with the configure-generated defines supplied by hand.
- **It needs ROM images** (KERNAL, BASIC, CHARGEN) for some tunes. reSIDfp plays much without them,
  but not everything. Those ROMs are Commodore's and **cannot be shipped**. Decide early: play what
  works without them, or let the user supply their own. This is a question for the owner, not a
  detail to settle in code.
- Cannot seek. Duration needs HVSC's `Songlengths.md5` (5.2 MB, fetchable — verified 2026-08-31),
  which ties this item to the catalogue work.

## 2. `game-music-emu` — the consoles

One library, many formats: NSF, GBS, SPC, VGM/VGZ, HES, AY, KSS. Modland lists 36,903 `.spc` and
14,167 `.vgz`.

- Latest release **0.6.5** (github.com/libgme/game-music-emu, checked 2026-09-01).
- LGPL-2.1-or-later, and it already builds with CMake — the least painful item on this list.
- Best value per hour of work. Do it before ASAP unless the owner wants Atari 8-bit sooner.

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

## 4. UADE — Amiga custom formats

TFMX, Hippel, Future Composer, David Whittaker, SidMon and a hundred others. Emulates a whole Amiga
to run the original replay routines.

**Last, and not only because it is the hardest.** UADE ships the original 68k replay binaries
extracted from commercial and shareware Amiga music programs. UADE's own code is GPL; those binaries
are not clearly licensed. That is a **distribution** question and it has to be answered before any
code is written, not after. If the answer is no, the formats can still be played by a user who
supplies their own — which is a different feature.

---

## Not planned

`.sc68` container files reference external replay binaries we do not ship, so they will not play even
though sc68 is present. Worth a line in the UI eventually; not worth shipping the replay set for.
