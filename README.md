# Protracktor

An Android player for the music formats of retro platforms — Amiga, Atari ST, Atari 8-bit,
Commodore 64, ZX Spectrum and the 8/16-bit consoles.

Players for these formats exist. This one is written because the existing ones lose your place when
you leave the app, shuffle without a history, hold everything in a single playlist, and take
seconds to start a track that is measured in kilobytes.

**Status: it plays.** Tracker modules, Atari ST, Atari 8-bit, Commodore 64 and seven console
families, with playlists, background playback and a media session. Local folders are scanned by
opening files rather than by trusting their names; Modland and ASMA browse offline, The Mod Archive
searches live. See `docs/STATUS.md` for what is finished, what is known broken, and — the shortest
list — what has actually been confirmed on a device.

## Documentation

| File | Contents |
| --- | --- |
| [`GOAL.md`](GOAL.md) | the work queued for an unattended run, with its rules |
| [`docs/STATUS.md`](docs/STATUS.md) | what is done, known defects numbered **C1…Cn**, branch state |
| [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md) | how it is built and **why that way** |
| [`docs/OPEN_QUESTIONS.md`](docs/OPEN_QUESTIONS.md) | decisions deliberately left open, with options |
| [`docs/PLAN_FORMATS.md`](docs/PLAN_FORMATS.md) | plan for the decoders we do not have yet |
| [`docs/PLAN_CATALOGUES.md`](docs/PLAN_CATALOGUES.md) | plan for the online archives we do not have yet |
| [`docs/PLAN_UNEXOTICA.md`](docs/PLAN_UNEXOTICA.md) | the one catalogue built before its archive answered, and exactly how to remove it |
| [`docs/PLAN_WEB.md`](docs/PLAN_WEB.md) | what a version in a browser would cost, and what it cannot carry |
| [`docs/PLAN_WEB_LIBRARY.md`](docs/PLAN_WEB_LIBRARY.md) | making the browser a second player rather than a second screen — and what that reverses |
| [`docs/PLAN_HANDOFF.md`](docs/PLAN_HANDOFF.md) | sending a playlist from the phone to that browser, without an account |
| [`docs/BACKLOG.md`](docs/BACKLOG.md) | agreed work not yet started, numbered **A1…An** |
| [`docs/WISHLIST.md`](docs/WISHLIST.md) | ideas, numbered **B1…Bn**, with who raised them and when |
| [`docs/BUILD.md`](docs/BUILD.md) | how to build debug, release and a store bundle |
| [`docs/LICENSES.md`](docs/LICENSES.md) | every third-party component, its licence, and what that obliges us to do |
| [`docs/ROADMAP_FORMATS.md`](docs/ROADMAP_FORMATS.md) | what we cannot play yet, in the order worth doing it, with what each costs |
| [`store/README.md`](store/README.md) | the Play listing in both languages, the console declarations, and the release gates still open |
| [`docs/TESTING.md`](docs/TESTING.md) | what only a phone can prove, and what to check on a build before trusting it |
| `docs/review-round-*.md` | what a pass over a finished round's own diff found, and what it checked and cleared |

## Licence

GPL-3.0-or-later. See `LICENSE`.

This is not a preference. The decoders that make the app worth having — `libsidplayfp`, UADE,
`sc68`, ASAP — are GPL, so the combined work is GPL. Version 3 rather than 2 because the Android
stack we build on (Jetpack Compose, AndroidX, Oboe) is Apache-2.0, which is incompatible with
GPL-2 and compatible with GPL-3. The reasoning is recorded in `docs/ARCHITECTURE.md`.
