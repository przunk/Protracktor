# GPL source and third-party notices

This is an engineering checklist, not legal advice. The shipped application declares
GPL-3.0-or-later. Publishing an AAB conveys object code and therefore makes the corresponding-source
and notice obligations part of the release, not a task for later.

## Required before distribution

1. Make `https://github.com/przunk/protracktor` public, or provide another no-charge public source
   location, before recipients can obtain the binary.
2. Tag the exact commit used for the Play release and keep the tag immutable.
3. Publish a source archive for that tag containing the application source, build scripts,
   generated configuration sources and the complete corresponding source for shipped native code.
   A fetch script alone is fragile if an upstream archive disappears; retain the verified source
   inputs for each release.
4. Include a clear source link in the store listing and in an in-app legal/about surface.
5. Generate third-party notices from the exact artifacts used by the release, not from expected
   licence names. Make those notices accessible to recipients.
6. Preserve copyright and licence texts required by libopenmpt, sc68, libsidplayfp, ASAP,
   game-music-emu, Oboe, AndroidX, Compose and the Kotlin runtime as applicable.

GPLv3 section 6 permits network distribution of object code when equivalent access to the
Corresponding Source is offered without further charge. The exact release source, not merely the
latest branch, is what a recipient must be able to retrieve.

## Blocking questions in the present tree

- **sc68 replay binaries:** `docs/LICENSES.md` records that their origin and redistributability are
  unresolved. They are packaged into the APK today. Do not publish them silently.
- **Oboe 1.10.0:** expected Apache-2.0, but `docs/LICENSES.md` still marks verification against the
  actual artifact incomplete.
- **Notices UI:** the app has no legal/about surface. A notice file in the repository is not an
  accessible in-app notice.
- **UADE:** not currently shipped, so it is not a blocker for this release. Its replay-binary issue
  becomes a release blocker if UADE is later integrated.

Primary source: [GNU GPL version 3, section 6](https://www.gnu.org/licenses/gpl-3.0.html#section6).
