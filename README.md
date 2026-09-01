# Protracktor

An Android player for the music formats of retro platforms — Amiga, Atari ST, Atari 8-bit,
Commodore 64, ZX Spectrum and the 8/16-bit consoles.

Players for these formats exist. This one is written because the existing ones lose your place when
you leave the app, shuffle without a history, hold everything in a single playlist, and take
seconds to start a track that is measured in kilobytes.

**Status: it plays.** Tracker modules, Atari ST, Atari 8-bit and seven console families, with
playlists, offline browsing of Modland, background playback and a media session. See
`docs/STATUS.md` for what is finished, what is known broken, and what has not been confirmed on a
device.

## Documentation

| File | Contents |
| --- | --- |
| `docs/STATUS.md` | what is done, known defects numbered **C1…Cn**, branch state |
| `docs/ARCHITECTURE.md` | how it is built and **why that way** |
| `docs/OPEN_QUESTIONS.md` | decisions deliberately left open, with options |
| `docs/PLAN_FORMATS.md` | plan for the decoders we do not have yet |
| `docs/PLAN_CATALOGUES.md` | plan for the online archives we do not have yet |
| `docs/BACKLOG.md` | agreed work not yet started, numbered **A1…An** |
| `docs/WISHLIST.md` | ideas, numbered **B1…Bn**, with who raised them and when |
| `docs/BUILD.md` | how to build debug, release and a store bundle |
| `docs/LICENSES.md` | every third-party component, its licence, and what that obliges us to do |

## Licence

GPL-3.0-or-later. See `LICENSE`.

This is not a preference. The decoders that make the app worth having — `libsidplayfp`, UADE,
`sc68`, ASAP — are GPL, so the combined work is GPL. Version 3 rather than 2 because the Android
stack we build on (Jetpack Compose, Media3, Room, Oboe) is Apache-2.0, which is incompatible with
GPL-2 and compatible with GPL-3. The reasoning is recorded in `docs/ARCHITECTURE.md`.
