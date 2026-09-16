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
| Vortex Tracker II `.vt2` | **12** | **step 5 — measured 2026-09-17, and it is neither free nor worth it** |
| `.med` (old Music Editor) | 132 | folds into step 2 |
| `.imf`, `.psm` stragglers | ~280 | step 4 |

**~~One cost applies to every step~~ — paid off 2026-09-17, and it was step 0.** Adding a name to
`SupportedFormats` used to change its fingerprint, make every stored index stale, and cost each user
a re-download of Modland's 40 MB — a toll the four steps below would have charged four times. An
index holds the whole archive now and what this build can open is decided where it is read, so a
format added later is a question the stored rows already answer: **one statement, no network.** The
steps below no longer want batching for that reason, and none of them has to mention it again.

---

## Step 0 — the index stops depending on what we can play — **done 2026-09-17**

`SupportedFormats` used to decide what a downloaded index **kept**, which made the index a function
of the decoder set. Both players store every row the archive lists now, with the verdict beside it,
and every screen asks for the playable part.

| | |
| --- | --- |
| a format added later | **one `UPDATE`, 228 ms over 516,107 rows** — or 1.7 s with the partial indexes to maintain — and no network |
| what it used to cost | Modland's 40 MB, per device, per format |
| phone database | 83.3 MB → **112.8 MB** at Modland's size: +29.5 MB for 172,036 more rows |
| a folder in Browse | **faster**, 0.1 ms: the browse index is partial over `playable = 1`, so it covers exactly the rows anything reads |

**The one thing a recompute cannot do is conjure rows that were never downloaded**, so an index
built before this is marked `complete = 0` and asks for one last refresh. After that there is not
another. The migration's `playable DEFAULT 1` is what keeps such an index working meanwhile: it
holds only playable rows by construction, and defaulting to 0 would have emptied Browse.

The class of defect it removes is worth as much as the toll. The fingerprint existed because an
index built by an older decoder set is missing files **and looks current** — the owner lost 60,572
C64 tunes to exactly that. An index that holds everything cannot be wrong about what this build
plays; it can only be out of date about the archive, which is a different and much more visible
thing.

## Step 1 — ~~Vortex Tracker II, `.vt2`~~ — **struck out 2026-09-17, and both halves of the case for it were wrong**

*This said: the cheapest thing on the page, the decoder is already compiled in, hours not days. It
was written without a count and without trying it. Kept in full, because the mistake is instructive
and the next person will be tempted the same way.*

**The decoder is more than compiled in — it is already wired.** `engine.cpp` has called
`Module::ProTracker3::CreateFactory(FC::ProTracker3::VortexTracker2::CreateDecoder())` all along,
right beside the ordinary PT3 decoder. The only thing missing was the **name**: `.vt2` is not in
`ZxTuneBackend::worthTrying`, so such a file never reaches ZXTune.

**And adding the name would not have helped.** Renamed to `.pt3`, so that the existing claim carries
it, a real Modland `.vt2` is still refused — and so are the other seven sampled from seven
different authors. The host probe says where: the header parses, `ParseBody` stops after **374
bytes of 38,221**, and `CheckIsSubset` then throws because the order list names patterns the body
never read. These files put a blank line between sections *and* a blank line after the header, and
ZXTune's text parser ends the body at the first of them. Collapsing them moves the failure rather
than fixing it, so there is more than one difference.

**Twelve files.** That is the whole of `.vt2` in Modland's 516,118 — counted from `allmods.zip`, not
estimated. The step was placed first on the strength of "free", and it is neither free nor worth a
day of somebody's ZXTune parser archaeology. It lives at step 5 now, beside the other thing that is
not ours to fix.

**The lesson, which is the reason this is not simply deleted:** the roadmap ordered a step by how
cheap it looked and never counted what it was worth. `.vt2` went first ahead of 29,000 files. One
`grep` over the index — ninety seconds — would have said twelve.

**What was kept from the attempt.** `native/probe/zxtune/probe_zxtune.cpp` now prints the decoder's
own exception under `PROBE_ZXTUNE_WHY=1`, because "reject:load" is every refusal wearing one face
and the question is always which decoder objected to what. That is how the 374 bytes were found.

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

## Step 5 — the two that are not worth it, for different reasons

### `.ay` — not ours to fix

1,202 files — Modland's `AY Emul` **directory**, whose members are not named `.ay`, so counting by
extension says zero. And **no amount of work here changes it.** The only AY-emulation decoder in reach is
ZXTune's `ayemul.cpp`, which uses `z80ex` — **GPL-2-only**, which cannot be combined with this
project's GPL-3. That is why it is the one file excluded from our ZXTune build by name.

The routes, and none is engineering:

1. A Z80 emulator under a compatible licence, wired into ZXTune's AY plugin. Somebody would have to
   want this enough to do it upstream.
2. `z80ex` relicensed, which is its authors' to do.
3. Leave it, and say so where a `.ay` is met.

**Option 3 today.** Recording it here so it stops being re-derived: this is a licence, not a to-do.

### `.vt2` — twelve files behind a parser disagreement

The whole of step 1 above, moved here. ZXTune's decoder is already wired and refuses every one of
Modland's twelve; the failure is inside its text parser and would be a day of somebody's time. If
it is ever done, do it for the parser's sake rather than for the twelve.

---

## What this does not cover

- **Other archives.** Every candidate but Modland and ASMA fails on CORS for the browser, which
  `docs/PLAN_CATALOGUES.md` measured on 2026-09-16. A format is worth more than an archive right
  now: step 2 alone is larger than anything a third catalogue would add.
- **UnExoticA**, which has its own document (`docs/PLAN_UNEXOTICA.md`) and overlaps step 2's
  formats heavily — worth re-reading *after* UADE, not before.
