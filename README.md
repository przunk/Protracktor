# Protracktor

An Android player for the music formats of retro platforms — Amiga, Atari ST, Atari 8-bit,
Commodore 64, ZX Spectrum and the 8/16-bit consoles.

Players for these formats exist. This one is written because the existing ones lose your place when
you leave the app, shuffle without a history, hold everything in a single playlist, and take
seconds to start a track that is measured in kilobytes.

**Status: 0.7.0 is in closed testing on Google Play** (versionCode 788, tag
[`v0.7.0`](https://github.com/przunk/protracktor/releases/tag/v0.7.0)). That tag is the exact source
the published build was made from, which is what the GPL below obliges. Every release, with the
hash of what was uploaded, is in
[`store/play-console/releases.md`](store/play-console/releases.md), which `scripts/release.sh` writes.

It plays tracker modules, Atari ST, Atari 8-bit, Commodore 64, the ZX Spectrum AY trackers, the
Amiga synth trackers, the Amiga custom replayers through UADE (TFMX, Hippel, Future Composer and
the rest — their replay routines downloaded on request) and seven console families, with playlists,
background playback and a media session. Local folders are scanned by opening every file rather than by trusting its name. Four
online archives: Modland and ASMA browse offline from a downloaded index, The Mod Archive searches
live, and UnExoticA is behind a switch until its maintainers answer a question
(`docs/PLAN_UNEXOTICA.md`). HVSC's database supplies the duration a SID cannot carry, and UADE's `songdb` the one an Amiga
tune cannot; what neither knows is measured while the tune plays.

There is also a **player for the browser** in `web/`, which plays the same archives and takes a
playlist handed to it by the phone. It is a second player rather than a second screen; see
`docs/WEB_SERVER.md` to run one.

See `docs/STATUS.md` for what is finished, what is known broken, and — the shortest list — what has
actually been confirmed on a device.

## Documentation

| File | Contents |
| --- | --- |
| [`docs/STATUS.md`](docs/STATUS.md) | what is done, known defects numbered **C1…Cn**, branch state |
| [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md) | how it is built and **why that way** |
| [`docs/OPEN_QUESTIONS.md`](docs/OPEN_QUESTIONS.md) | decisions deliberately left open, with options |
| [`docs/PLAN_FORMATS.md`](docs/PLAN_FORMATS.md) | plan for the decoders we do not have yet |
| [`docs/PLAN_CATALOGUES.md`](docs/PLAN_CATALOGUES.md) | plan for the online archives we do not have yet |
| [`docs/PLAN_UNEXOTICA.md`](docs/PLAN_UNEXOTICA.md) | the one catalogue built before its archive answered, and exactly how to remove it |
| [`docs/PLAN_RANDOM.md`](docs/PLAN_RANDOM.md), [`docs/SPEC_RANDOM.md`](docs/SPEC_RANDOM.md) | the dice: what it does, and the rules both players follow |
| [`docs/PLAN_WEB.md`](docs/PLAN_WEB.md) | what a version in a browser would cost, and what it cannot carry |
| [`docs/PLAN_WEB_LIBRARY.md`](docs/PLAN_WEB_LIBRARY.md) | making the browser a second player rather than a second screen — and what that reverses |
| [`docs/PLAN_HANDOFF.md`](docs/PLAN_HANDOFF.md) | sending a playlist from the phone to that browser, without an account |
| [`docs/BACKLOG.md`](docs/BACKLOG.md) | agreed work not yet started, numbered **A1…An** |
| [`docs/WISHLIST.md`](docs/WISHLIST.md) | ideas, numbered **B1…Bn**, with who raised them and when |
| [`docs/WEB_SERVER.md`](docs/WEB_SERVER.md) | running the browser player, on a machine or a Raspberry Pi |
| [`docs/BUILD.md`](docs/BUILD.md) | how to build debug, release and a store bundle |
| [`store/privacy-policy.md`](store/privacy-policy.md) | the privacy policy, in both languages — the one published, and the one shown in the app |
| [`docs/PRIVACY.md`](docs/PRIVACY.md) | why that is the only copy, and what leaves the phone in short |
| [`docs/LICENSES.md`](docs/LICENSES.md) | every third-party component, its licence, and what that obliges us to do |
| [`docs/REQUIREMENTS.md`](docs/REQUIREMENTS.md) | what the app has to do, each requirement traced to the complaint it came from |
| [`docs/PLAN_ROUND_13.md`](docs/PLAN_ROUND_13.md) | the round after 0.7.0: titles, accent-blind search, the dock, folders cached ahead |
| [`docs/PLAN_SONGDB_LENGTHS.md`](docs/PLAN_SONGDB_LENGTHS.md) | where Amiga tunes get their length, and in what order the sources are asked |
| [`docs/PLAN_INSTRUMENT_NAMES.md`](docs/PLAN_INSTRUMENT_NAMES.md) | instrument and sample names in Now Playing, planned before any code |
| [`docs/ROADMAP_FORMATS.md`](docs/ROADMAP_FORMATS.md) | what we cannot play yet, in the order worth doing it, with what each costs |
| [`docs/PLAY_STORE.md`](docs/PLAY_STORE.md) | what Google Play asks for, answered |
| [`store/README.md`](store/README.md) | the Play listing in both languages, the console declarations, and the release gates still open |
| [`store/play-console/releases.md`](store/play-console/releases.md) | every artifact handed to Play: hash, versionCode, commit, tag, signer |
| [`docs/rules/`](docs/rules) | the rules the phone and the browser share, as data both of them are tested against |
| [`docs/TESTING.md`](docs/TESTING.md) | what only a phone can prove, and what to check on a build before trusting it |
| `docs/review.md`, `docs/review-round-*.md` | what a pass over a finished round's own diff found, and what it checked and cleared |

## Licence

GPL-3.0-or-later. See `LICENSE`.

This is not a preference. The decoders that make the app worth having are GPL, so the combined work
is GPL: `libsidplayfp` and ASAP are GPL-2-or-later, `sc68` is GPL-3-or-later, and ZXTune is LGPL-3.
Version 3 rather than 2 because `sc68` alone requires it, and because the Android stack we build on
(Jetpack Compose, AndroidX, Oboe) is Apache-2.0, which is incompatible with GPL-2 and compatible
with GPL-3. Every component, its licence and what it obliges us to do is in
[`docs/LICENSES.md`](docs/LICENSES.md); the reasoning is in `docs/ARCHITECTURE.md`.

UADE is in the app since 0.7.0, and its licence needed reading rather than assuming: the emulator
states "GPL" with no version, libuade "LGPL" with none. `uadecore` runs as a separate program the
app starts and talks to over a socket; libuade is linked in. Why that is compatible with GPL-3, as
accepted by the owner on 2026-09-21, is in [`docs/LICENSES.md`](docs/LICENSES.md). UADE's replay
routines are not in the APK; they are downloaded when the user asks for them.

The app shows every component's licence text itself, under **Settings → Open-source licences**, and
a test fails when something compiled into the build has no entry there.
