<p align="center">
  <img src="docs/images/protracktor-logo.png" alt="Protracktor logo" width="320">
</p>

# Protracktor

An Android player for the music formats of retro platforms — Amiga, Atari ST, Atari 8-bit,
Commodore 64, ZX Spectrum and the 8/16-bit consoles.

The design centres on keeping your place, multiple saved playlists, shuffle with a history, and
browsing without replacing what is playing. The active playback context and manual queue are
independent of the list currently on screen.

**Protracktor is in closed testing on Google Play.** This README describes the current development
code; a tagged release can have fewer features. Prepared store bundles, their versions, exact
source commits/tags and hashes are recorded in
[`store/play-console/releases.md`](store/play-console/releases.md) by `scripts/release.sh`.
Preparing a bundle or creating a tag does not establish upload or publication. Current release
and verification status is tracked in [`docs/STATUS.md`](docs/STATUS.md).

It plays tracker modules, Atari ST, Atari 8-bit, Commodore 64, the ZX Spectrum AY trackers, the
Amiga synth trackers, the Amiga custom replayers through UADE (TFMX, Hippel, Future Composer and
the rest — their replay routines downloaded on request) and seven console families, with playlists,
background playback and a media session. Local folders are scanned by opening every file rather than by trusting its name. Four
online archives: Modland and ASMA browse offline from a downloaded index, The Mod Archive searches
live, and UnExoticA provides indexed Amiga game soundtracks. Jools Wills (BuZz) approved the
proposed UnExoticA use; that permission and the agreed stable User-Agent are recorded on
2026-10-07 in [`docs/PLAN_UNEXOTICA.md`](docs/PLAN_UNEXOTICA.md). Its build-time switch remains
available for operational reversibility, not because permission is pending.
HVSC's database supplies the duration a SID cannot carry, and UADE's `songdb` the one an Amiga
tune cannot; what neither knows is measured while the tune plays.

Optional [volume normalization](docs/VOLUME_NORMALIZATION.md) reduces track-to-track loudness
differences during Android playback; it does not process exported audio.

There is also a **player for the browser** in `web/`, which plays the same archives and takes a
playlist handed to it by the phone. It is a second player rather than a second screen; see
`docs/WEB_SERVER.md` to run one.

See [`docs/STATUS.md`](docs/STATUS.md) for completed work, known defects and the evidence behind
verification claims. Host checks and reported phone results are recorded separately; neither a
successful build nor an overall trial acceptance proves every device scenario.

## Documentation

Start with Status for the current state and the implementation roadmap for open work. Dated
plans and reviews retain earlier decisions and verification evidence; they are not automatically
an up-to-date list of missing features or permission to implement them.

Historical implementation handoffs and review reports are available in
[Git history](https://github.com/przunk/protracktor/tree/2c35319cfa8b665398a36141248b45544bd68ebe/docs),
rather than the current documentation tree. Links to those records use a fixed revision; older
source comments may refer to their historical paths.

| File | Contents |
| --- | --- |
| [`docs/STATUS.md`](docs/STATUS.md) | what is done, known defects numbered **C1…Cn**, branch state |
| [`docs/IMPLEMENTATION_ROADMAP.md`](docs/IMPLEMENTATION_ROADMAP.md) | current inventory of unfinished work, deferred ideas and open decisions |
| [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md) | how it is built and **why that way** |
| [`docs/VOLUME_NORMALIZATION.md`](docs/VOLUME_NORMALIZATION.md) | playback loudness matching, measurement limits and verification |
| [`docs/OPEN_QUESTIONS.md`](docs/OPEN_QUESTIONS.md) | decisions deliberately left open, with options |
| [`docs/PLAN_FORMATS.md`](docs/PLAN_FORMATS.md) | decoder integration plans, decisions and historical implementation records |
| [`docs/PLAN_CATALOGUES.md`](docs/PLAN_CATALOGUES.md) | archive integration plans and decisions |
| [`docs/PLAN_UNEXOTICA.md`](docs/PLAN_UNEXOTICA.md) | UnExoticA integration, maintainer permission, client identity and operational switch |
| [`docs/PLAN_RANDOM.md`](docs/PLAN_RANDOM.md), [`docs/SPEC_RANDOM.md`](docs/SPEC_RANDOM.md) | the dice: what it does, and the rules both players follow |
| [`docs/PLAN_WEB.md`](docs/PLAN_WEB.md) | what a version in a browser would cost, and what it cannot carry |
| [`docs/PLAN_WEB_LIBRARY.md`](docs/PLAN_WEB_LIBRARY.md) | making the browser a second player rather than a second screen — and what that reverses |
| [`docs/PLAN_HANDOFF.md`](docs/PLAN_HANDOFF.md) | sending a playlist from the phone to that browser, without an account |
| [`docs/BACKLOG.md`](docs/BACKLOG.md) | implementation tasks numbered **A1…An**, including retained completion history |
| [`docs/WISHLIST.md`](docs/WISHLIST.md) | feature ideas and requests numbered **B1…Bn**, with their source, date and status |
| [`docs/WEB_SERVER.md`](docs/WEB_SERVER.md) | running the browser player, on a machine or a Raspberry Pi |
| [`docs/BUILD.md`](docs/BUILD.md) | how to build debug, release and a store bundle |
| [`store/privacy-policy.md`](store/privacy-policy.md) | the privacy policy, in both languages — the one published, and the one shown in the app |
| [`docs/PRIVACY.md`](docs/PRIVACY.md) | why that is the only copy, and what leaves the phone in short |
| [`docs/LICENSES.md`](docs/LICENSES.md) | every third-party component, its licence, and what that obliges us to do |
| [`docs/REQUIREMENTS.md`](docs/REQUIREMENTS.md) | what the app has to do, each requirement traced to the complaint it came from |
| [`docs/LAUNCH_READINESS.md`](docs/LAUNCH_READINESS.md) | what is left before the public launch, in Play Console and here, and where the web player goes |
| [`docs/PLAN_WEB_PARITY.md`](docs/PLAN_WEB_PARITY.md) | the browser brought level with the app, W1–W11 |
| [`docs/PLAN_ROUND_13.md`](docs/PLAN_ROUND_13.md) | the round after 0.7.0: titles, accent-blind search, the dock, folders cached ahead |
| [`docs/PLAN_SONGDB_LENGTHS.md`](docs/PLAN_SONGDB_LENGTHS.md) | where Amiga tunes get their length, and in what order the sources are asked |
| [`docs/PLAN_INSTRUMENT_NAMES.md`](docs/PLAN_INSTRUMENT_NAMES.md) | instrument and sample names in Now Playing, planned before any code |
| [`docs/ROADMAP_FORMATS.md`](docs/ROADMAP_FORMATS.md) | what we cannot play yet, in the order worth doing it, with what each costs |
| [`docs/PLAY_STORE.md`](docs/PLAY_STORE.md) | what Google Play asks for, answered |
| [`store/README.md`](store/README.md) | the Play listing in both languages, the console declarations, and the release gates still open |
| [`store/play-console/releases.md`](store/play-console/releases.md) | prepared store-bundle evidence: hash, versionCode, commit, tag and signer; preparation alone does not confirm publication |
| [`docs/rules/`](docs/rules) | the rules the phone and the browser share, as data both of them are tested against |
| [`docs/TESTING.md`](docs/TESTING.md) | what only a phone can prove, and what to check on a build before trusting it |

## Licence

GPL-3.0-or-later. See `LICENSE`.

This is not a preference. The decoders that make the app worth having are GPL, so the combined work
is GPL: `libsidplayfp` and ASAP are GPL-2-or-later, `sc68` is GPL-3-or-later, and ZXTune is LGPL-3.
Version 3 rather than 2 because `sc68` alone requires it, and because the Android stack we build on
(Jetpack Compose, AndroidX, Oboe) is Apache-2.0, which is incompatible with GPL-2 and compatible
with GPL-3. Every component, its licence and what it obliges us to do is in
[`docs/LICENSES.md`](docs/LICENSES.md); the reasoning is in `docs/ARCHITECTURE.md`.

UADE has been in the app since 0.7.0. Its upstream licence statements do not specify versions:
the emulator states GPL and libuade LGPL. `uadecore` runs as a separate program the app starts
and talks to over a socket; libuade is linked in. The decision to use this integration was made
on 2026-09-21; its licensing rationale and limitations are recorded in
[`docs/LICENSES.md`](docs/LICENSES.md). This records the project's integration decision, not a
new permission granted by upstream. UADE's replay routines are not in the APK; they are
downloaded when the user asks for them.

The app shows every component's licence text itself, under **Settings → Open-source licences**, and
a test fails when something compiled into the build has no entry there.
