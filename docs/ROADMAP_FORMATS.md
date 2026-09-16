# What we cannot play yet, in the order worth doing it

*Written 2026-09-16, when the owner asked for a roadmap over the gaps rather than another list of
them. `docs/PLAN_FORMATS.md` is the history and the measurements; this is what to do next and what
each thing costs.*

**The shape of the problem in one table.** Modland holds 516,107 rows and the app claims 342,169 of
them — 66%, the same on both players since ZXTune reached the browser on 2026-09-15. The missing
third is not evenly spread:

| | files | where it goes |
| --- | --- | --- |
| Amiga custom replayers | **~29,000** | step 2 — UADE |
| DefleMask `.dmf` | 1,807 | step 4 — its own decoder |
| FamiTracker `.ftm` | 1,874 | step 4 — its own decoder |
| ZX `.ay` | 1,202 | **step 5 — blocked by a licence, not by work** |
| Vortex Tracker II `.vt2` | *unclaimed* | **step 1 — the decoder is already compiled in** |
| `.med` (old Music Editor) | 132 | folds into step 2 |
| `.imf`, `.psm` stragglers | ~280 | step 4 |

**One cost applies to every step and is worth knowing before starting any of them.** Adding a name
to `SupportedFormats` changes its fingerprint, every stored index goes stale, and each user
re-downloads Modland's 40 MB (`docs/STATUS.md`, the `.vtx` entry). So **the additions want batching**
— pay it once for a group rather than once per format. That argues for doing step 1 *with* something
else rather than on its own, unless it is wanted immediately.

---

## Step 1 — Vortex Tracker II, `.vt2`

**The cheapest thing on this page by a wide margin, and it was missed.** ZXTune's
`protracker3_vortex.cpp` implements `CreateVortexTracker2Decoder()`, `players/aym/vortex.cpp`
implements the player, and **this project already compiles both** — the CMake globs
`src/formats/chiptune/aym/*.cpp` and `src/module/players/aym/*.cpp`, so the code is in the binary
today and nothing ever calls it.

What it needs: one `tryAym` line in `ZxTuneBackend`, the name in `worthTrying`, a row in
`SupportedFormats` and one in `web/src/formats.tsv`. Hours, not days.

**Measure before claiming it.** `.vt2` is a *text* format where ProTracker 3 is binary, so the one
thing to check is that a real Modland `.vt2` opens and renders rather than merely being claimed —
the `.psm`, `.ftc` and `.gtr` mistake was exactly this, three names claimed before the decoders were
wired and three files that said "Protracktor cannot play this yet" about decoders that existed.

## Step 2 — UADE, and it is the whole of the rest

**~29,000 files, an order of magnitude more than everything below it put together.** TFMX, Jochen
Hippel, Future Composer, David Whittaker, SidMon 2, Delta Music, YMST — the Amiga formats where the
tune is note data and the *player* is a separate 68000 program, which is why no ordinary decoder
reaches them and why one emulator reaches them all.

Most of the hard thinking is done and recorded:

- **The measurement.** `docs/PLAN_FORMATS.md` proved reach into directories holding 5,799 files in a
  sampled run, against a whole-index estimate of 29,127.
- **The licence.** Settled 2026-09-04 (`docs/LICENSES.md`): the replay binaries are **downloaded,
  not shipped**, from `zakalwe.fi/uade/download.html` — the page upstream publishes for the purpose
  — with GitLab as the fallback. Upstream confirmed it.
- **The third thing nobody expects.** A UADE backend needs the emulator, `players/`, **and a song
  database**: without `conf/song.conf`'s md5 overrides, Hippel and TFMX variants fail in a way that
  looks like the format not working. It is GPL-2-or-later and compatible. **`conf/songdb` beside it
  is CC BY-NC-SA and must not ship in a store app.**

What is genuinely open: **the process model.** UADE upstream forks and execs `uadecore`; Android
permits executing only from `lib/<abi>/`. `docs/PLAN_FORMATS.md` sets out that choice and does not
make it, and it is the first thing to decide when this is picked up.

**Do this before any of steps 3–5.** It is worth more than all of them together, and it takes
`.med`'s 132 with it.

## Step 3 — the browser question that comes with step 2

UADE is an Amiga emulator; whether it goes into the WebAssembly build is a size question the same
way ZXTune was, and ZXTune's answer is now known: **+21% over the wire for 26,537 tunes**, and the
"it does not build under Emscripten" that held it up for a week was eight lines nobody had tried.

So: **try the build early, not at the end.** If UADE compiles under Emscripten the two players stay
level, which `docs/SPEC_RANDOM.md` says they should. If it does not, the phone gains 29,000 tunes
the browser cannot play and that gap wants saying out loud in Browse, the way ZXTune's absence was.

## Step 4 — the ones that need a decoder of their own

Honest about the ratio: **~3,900 files between them, and each is a separate piece of work** with no
shared machinery. Worth doing only after step 2, and probably only if somebody wants the format.

| | files | what it would take |
| --- | --- | --- |
| FamiTracker `.ftm` | 1,874 | its own decoder; libopenmpt's FTM is *Face The Music* and 1 in 12 of Modland's open by accident |
| DefleMask `.dmf` | 1,807 | its own decoder; libopenmpt implements X-Tracker's `.dmf`, which is the other 366 |
| id Software `.imf` | 210 | an **OPL2** emulator, which this build does not have — `emu2413` in gme is OPLL, a different chip |
| Spectrum Pro Sound Maker `.psm` | ~70 | libopenmpt's PSM is Epic MegaGames MASI; these are a ZX format |

**The trap in all four is the shared extension**, and it has bitten this project twice. `.ftm`,
`.dmf`, `.psm` and `.imf` are each claimed today for the files that *do* work, so a new decoder has
to be tried **alongside** the existing one rather than instead of it, and `openBackend`'s
first-refusal-wins rule (`docs/STATUS.md` C55) decides which reason the owner is shown.

## Step 5 — `.ay`, which is not ours to fix

1,202 files, and **no amount of work here changes it.** The only AY-emulation decoder in reach is
ZXTune's `ayemul.cpp`, which uses `z80ex` — **GPL-2-only**, which cannot be combined with this
project's GPL-3. That is why it is the one file excluded from our ZXTune build by name.

The routes, and none is engineering:

1. A Z80 emulator under a compatible licence, wired into ZXTune's AY plugin. Somebody would have to
   want this enough to do it upstream.
2. `z80ex` relicensed, which is its authors' to do.
3. Leave it, and say so where a `.ay` is met.

**Option 3 today.** Recording it here so it stops being re-derived: this is a licence, not a to-do.

---

## What this does not cover

- **Other archives.** Every candidate but Modland and ASMA fails on CORS for the browser, which
  `docs/PLAN_CATALOGUES.md` measured on 2026-09-16. A format is worth more than an archive right
  now: step 2 alone is larger than anything a third catalogue would add.
- **UnExoticA**, which has its own document (`docs/PLAN_UNEXOTICA.md`) and overlaps step 2's
  formats heavily — worth re-reading *after* UADE, not before.
