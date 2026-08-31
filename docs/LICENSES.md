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
| libopenmpt | MOD, XM, S3M, IT, MPTM and many others | BSD-3-Clause | no |
| sc68 / libsc68 | Atari ST SNDH, YM | GPL-3.0-or-later | no |
| libsidplayfp | C64 SID | GPL-2.0-**or-later** | no |
| game-music-emu | NSF, GBS, SPC, VGM, HES, AY, KSS | LGPL-2.1-or-later | no |
| ASAP | Atari 8-bit SAP | GPL-2.0-**or-later** | no |
| UADE | Amiga custom replayers (TFMX, Hippel, FC, …) | GPL-2.0-**or-later** | no |
| Oboe | audio output | Apache-2.0 | no |

**The "or later" matters.** A dependency that turns out to be GPL-2-**only** cannot be combined
with our Apache-2.0 UI stack under GPL-3, and invalidates the licence decision. If that is found,
stop and raise it with the owner — do not work around it.

**UADE needs separate care.** It ships the original 68k replayer binaries extracted from
commercial and shareware Amiga music programs. UADE's own code is GPL; the status of those
binaries is not clean, and that is a distribution question, not a linking one. To be settled before
UADE is integrated, not after.

## Android / JVM dependencies

Filled in when the Gradle project exists. Expected to be Apache-2.0 throughout (AndroidX, Compose,
Media3, Room, Kotlin).
