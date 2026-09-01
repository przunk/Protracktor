# Plan: more formats

Written 2026-09-01, ahead of the owner sending this as its own goal.

Each backend is its own risk and its own commit. The order below is by value per unit of pain, and
the first item is not a new format at all.

---

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

## 0. Fix `.sndh` — before adding anything

**This is a defect, not a missing format.** sc68 is built in and `file68.c` recognises SNDH:
`read_header` returns `-'sndh'` and `SC68file_verify` turns that into success, so `api68_verify_mem`
should accept these files. Something between there and `NativeEngine.open` returning null is wrong.

Adding backends on top of a broken one means debugging two things at once.

**How to chase it**, cheapest first:

1. Get an SNDH file onto the build machine. There is none here, and `sndh.net` did not resolve on
   2026-08-31 — recheck, or ask the owner for one, or take one from Modland's Atari ST section.
2. Build sc68 **for the host** (it already builds with plain CMake; only the NDK toolchain file is
   Android-specific) and call `api68_verify_mem` and `api68_load_mem` on the bytes directly. That
   separates "sc68 refuses it" from "our wrapper is wrong" in one run, on this machine, without a
   device.
3. Likely suspects if sc68 accepts it: our `recognises()` return-code reading, `api68_play(api, 1)`
   track numbering, or `api68_process` frame counting.

**Done when:** an SNDH plays on the owner's device.

---

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

## 3. ASAP — Atari 8-bit (`.sap`)

The owner has `.sap` files and they do not play.

- **Source location unverified.** It is not at `github.com/pfusik/asap` (404 on 2026-09-01). Find it
  before planning further; it has historically lived on SourceForge, and there is an official
  Android port whose build may be worth reading.
- GPL-2.0-or-later, expected. Verify.
- Written in Ć and transpiled to C, so the vendored artefact is generated C — check what the release
  tarball actually contains before assuming a source build.
- Cannot seek. ASMA (see `docs/PLAN_CATALOGUES.md`) is its natural catalogue.

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
