# Third-party licences

The application is GPL-3.0-or-later (`docs/ARCHITECTURE.md` §2). That obliges us to know exactly
what we link and under what terms.

**Nothing below is verified yet.** The "expected" column is what the component is believed to be
from prior knowledge; each row is confirmed against the actual `COPYING`/`LICENSE` file in the
source we vendor, at the moment we vendor it, and this table is corrected then. A row still marked
*unverified* must not be treated as fact.

## Planned native components

| Component | Covers | Licence expected | Verified |
| --- | --- | --- | --- |
| libopenmpt 0.8.9 | MOD, XM, S3M, IT, MPTM and many others | BSD-3-Clause | **yes, 2026-08-31** |
| sc68 2.2.1 | Atari ST SNDH, YM | GPL-2.0-**or-later** | **yes, 2026-09-01** |
| libsidplayfp 3.1.1 | C64: PSID, RSID | GPL-2.0-**or-later** | **yes, 2026-09-02** |
| game-music-emu 0.6.5 | NSF, NSFE, GBS, SPC, VGM, VGZ, GYM, HES, AY, KSS | LGPL-2.1-**or-later** | **yes, 2026-09-01** |
| ASAP 8.0.0 | Atari 8-bit: SAP + 13 tracker formats | GPL-2.0-**or-later** | **yes, 2026-09-01** |
| UADE | Amiga custom replayers (TFMX, Hippel, FC, …) | GPL-2.0-**or-later** | no |
| Oboe 1.10.0 | audio output | Apache-2.0 | no |

**The "or later" matters.** A dependency that turns out to be GPL-2-**only** cannot be combined
with our Apache-2.0 UI stack under GPL-3, and invalidates the licence decision. If that is found,
stop and raise it with the owner — do not work around it.

**UADE needs separate care.** It ships the original 68k replayer binaries extracted from
commercial and shareware Amiga music programs. UADE's own code is GPL; the status of those
binaries is not clean, and that is a distribution question, not a linking one. To be settled before
UADE is integrated, not after.

## Verified

**libopenmpt 0.8.9**, read from `LICENSE` in the source tarball on 2026-08-31:

> Copyright (c) 2004-2026, OpenMPT Project Developers and Contributors
> Copyright (c) 1997-2003, Olivier Lapicque
> All rights reserved.
> Redistribution and use in source and binary forms, with or without modification, are permitted
> provided that the following conditions are met: […]

Three-clause BSD — permissive, compatible with GPL-3, and it obliges us to reproduce that notice in
anything we distribute. That obligation is not yet discharged: the app has no licences screen.
Recorded here so it is not forgotten before a release.

Sources are fetched by `scripts/fetch-native-deps.sh`, pinned to an exact version and verified
against a SHA-256 recorded in that script. They are not committed.

**sc68 2.2.1**, checked on 2026-09-01. `COPYING` carries the GPL **version 2** text, which on its
own would have invalidated our GPL-3 decision — but every one of the 51 licensed source files says:

> under the terms of the GNU General Public License as published by the Free Software Foundation;
> either version 2 of the License, or (at your option) any later version.

"Or later" is what matters, and it is unanimous across the tree. Reading only `COPYING` would have
given the wrong answer here, which is why the rule is to check the sources.

**ASAP 8.0.0**, checked on 2026-09-01. `COPYING` is the GPL **version 2** text again, and again the
sources settle it — `README` and 34 files say:

> either version 2 of the License, or (at your option) any later version

Compatible with GPL-3. Second time in this project that reading only `COPYING` would have given the
wrong answer.

**libsidplayfp 3.1.1**, checked on 2026-09-02. `COPYING` is the GPL **version 2** text — the third
time in this project — and the sources again say "either version 2 of the License, or (at your
option) any later version". Compatible with GPL-3.

**No Commodore ROMs are shipped and none are needed for the files tested.** KERNAL, BASIC and
CHARGEN are Commodore's and cannot be distributed. Thirty random Modland SIDs all played without
them and none was BASIC-compatible, so the question the owner was asked to decide turns out to be
much smaller than it looked. It is still his to decide if a tune ever needs them.

## sc68's replay binaries — a question for the owner

**Raised 2026-09-01. Not a blocker today; not something to publish without an answer either.**

sc68 does not play SNDH from the tune alone. Each one names a small 68000 replay routine that ships
in sc68's `data/Replay/` directory, and sc68 opens it by path — which is why every SNDH in the
owner's library loaded and then played silence until those files were packaged. 85 of them are now
in the APK, 902 KB.

They arrive inside sc68's own GPL tarball and `AUTHORS` credits Benjamin Gerard as its programmer
with no separate statement about their origin. That is the whole of what is known. Some are plainly
his (`sndh_ice.bin` is sc68's own SNDH wrapper); others are named after commercial Atari games
(`alteredbeast.bin`, `cabal.bin`, `armalyte.bin`) and look very much like routines lifted from those
games, which is the **same** question already recorded above for UADE.

For a private build this is academic. Before the repository goes public or anything reaches a store,
the owner should decide: ship them, ship only the ones sc68 clearly authored, or have the user supply
their own. Shipping them silently is the one option that should not happen by default, which is why
this is written down rather than left in a commit message.

## Data the app downloads, and does not ship

Distinct from the components above, and the distinction is the whole point: **none of this is in the
repository or in the APK.** The user asks for it, their device fetches it from the project that
publishes it, and it stays on their device. Nothing here is redistributed by us, so nothing here
places a condition on our distribution.

| Data | From | Size | What it is |
| --- | --- | --- | --- |
| Modland index | `modland.com` | ~40 MB | a list of what the archive holds |
| ASMA | `asma.atari.org` | 20 MB | the Atari 8-bit collection itself, 6,335 `.sap` files |
| HVSC song lengths | `hvsc.c64.org` | 5.2 MB | hand-timed durations for 61,157 SID tunes |

Worth a second look **if that ever changes** — if it becomes tempting to bundle any of it to save
the user a download, the terms of the collection publishing it become our problem, and each of these
three is published under its own conditions that nobody here has read. Today none of them apply.

## Android / JVM dependencies

Checked against `gradle/libs.versions.toml` on 2026-09-02, and it is a short list — **Apache-2.0
throughout**, so nothing here constrains the GPL-3 combined work.

| Dependency | Licence |
| --- | --- |
| `androidx.core:core-ktx`, `androidx.lifecycle:*`, `androidx.activity:activity-compose` | Apache-2.0 |
| `androidx.compose:*` (BOM 2024.12.01), `androidx.compose.material3:material3` | Apache-2.0 |
| `com.google.oboe:oboe` 1.10.0 | Apache-2.0 |
| `junit:junit` 4.13.2 — test only | EPL-1.0 |
| `org.xerial:sqlite-jdbc` 3.50.3.0 — test only | Apache-2.0 |

Two names that earlier drafts of this file and `README.md` listed are **not dependencies and never
were**: Media3 and Room. The media session is built on the platform's own `android.media.session`,
and storage is hand-written SQLite for the reason in `docs/ARCHITECTURE.md` §9. The GPL-3 argument
does not depend on either — Compose and Oboe carry it — but a licence file naming components that
are not there is a licence file nobody should trust.
