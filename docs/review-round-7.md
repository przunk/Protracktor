<!-- SPDX-FileCopyrightText: 2026 Przunk -->
<!-- SPDX-License-Identifier: GPL-3.0-or-later -->

# Review round 7 — 2026-09-07

**Scope: everything written today.** 112 files, 2,732 insertions — the ZXTune backend and its build,
the songdb metadata table with a schema migration, four failure messages where there had been one,
a retry path, and an SPDX pass over 92 files. Rounds 5 and 6 each found five defects and most were
in code written that same day; the rule that earned its place is **suspect this session's own work
first**, and it earned it again.

Eight findings, all fixed. Seven are in code written today.

## R1 — the metadata import peaks near 150 MB, and only on a phone

`downloadTrackMetadata` read the file into a `String`, parsed it into a `List<Entry>`, then wrote
the list. Counted rather than guessed:

| | |
| --- | --- |
| 380,282 `Entry` objects | ~15 MB of headers |
| 1,901,410 `String` objects | ~76 MB of headers |
| their characters, the source `String`, the downloaded bytes | ~60 MB |
| **peak** | **~150 MB** |

That is an out-of-memory crash on a modest device, during a 15 MB download, on the one code path
nobody had run. **Every test here runs on the JVM against a handful of rows**, so nothing would
have caught it before the owner did — and HVSC's database, which this was modelled on, is a sixth
the size and got away with it.

`SongDbMetadata.parse` now returns a lazy `Sequence`, and `TrackMetadataStore.replaceAllFrom` parses
straight into the insert. One row is alive at a time.

## R2 — a backend saying "0" blocked a year the database had

`merged` filled a field from the songdb table when the decoder's own was **blank**. sc68 emits
`year` for every tune and writes `0` or `unknown` when it does not know — not blank. So the files
most likely to benefit from a lookup were the ones that refused it.

`ReleaseYear` already knew what counts as a year. `merged` now asks it, so the two cannot drift, and
a test pins the shared rule.

## R3 — `std::tolower` on a possibly negative `char`

`ZxTuneBackend::worthTrying` lower-cased an extension with `std::tolower(c)` where `c` is a plain
`char`. A negative value there is undefined, and a filename can carry one. Widened through
`unsigned char`, as every other byte-classifying call in the file already was.

## R4 — the retry outlived the user moving on

This is the one worth remembering, because it is the same surprise the code was written to remove.
`pendingRetry` remembers what to try again after a failed open. It was cleared on success and never
otherwise — so: a search result fails, the user switches playlist, presses play, and gets **the
abandoned track instead of the playlist**. Exactly the substitution the owner had reported that
morning, arriving from the other side.

Cleared in `stopPlayback`, which is what every "the user moved on" path already calls — switching
playlist, deleting one, returning from a detour, removing the playing track, and shutdown.

## R5 — every track was hashed twice

Two databases key on the MD5 of the file, and each store computed its own. Neither was wrong;
together they hashed several megabytes twice per track. `Md5.of` computes it once and both lookups
take it.

## R6 — a fresh checkout could not build the ZX Spectrum backend

`fetch-zxtune.py` was never wired into `fetch-native-deps.sh`. Anybody following the documented
setup — including this project on another machine — would fetch five libraries, miss the sixth, and
meet `Run ./scripts/fetch-zxtune.py first` from CMake. It works here only because the script had
been run by hand while the backend was being written.

sc68 has the same shape, being an SVN checkout rather than a tarball, and *is* wired in. This is
that line, for the same reason.

## R7 — two wrappers nobody calls

`SongLengthStore.secondsFor` and `TrackMetadataStore.forBytes` hashed the file themselves. R5 moved
the hashing to the caller, which left both as convenience methods with no callers. Removed rather
than kept for a caller that no longer exists — the same reasoning already recorded in this
controller about `addToPlaylistAndSay`.

## R8 — a source list that stops matching the sources

`file(GLOB)` is read once, at configure time. Moving the pin in `fetch-zxtune.py` and re-fetching
would leave the build compiling yesterday's list of files, with no error to say so.
`CONFIGURE_DEPENDS` makes the glob a build-time dependency.

**And the fix had a bug of its own**, caught by building rather than by reading: `CONFIGURE_DEPENDS`
goes *after* the variable name, and putting it first makes CMake take it as the variable and
`ZX_SOURCES` as a glob pattern. The Kotlin tests are green either way; only a native build knows.

## Not a finding, and recorded so it does not become one

An automated comparison of `SupportedFormats`' ZX Spectrum names, `ZxTuneBackend::worthTrying`, and
the factories the backend actually tries reported a total mismatch. **The extraction was broken, not
the code**: all three lists hold the same thirteen names. Worth writing down because the same
mismatch was real this morning — four formats were claimed and not implemented — and a false alarm
about a bug that recently existed is exactly the kind of thing that gets acted on twice.
