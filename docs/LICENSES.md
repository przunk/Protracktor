# Third-party licences

The application is GPL-3.0-or-later (`docs/ARCHITECTURE.md` §2). That obliges us to know exactly
what we link and under what terms.

**Nothing below is verified yet.** The "expected" column is what the component is believed to be
from prior knowledge; each row is confirmed against the actual `COPYING`/`LICENSE` file in the
source we vendor, at the moment we vendor it, and this table is corrected then. A row still marked
*unverified* must not be treated as fact.

## qrcode-generator 2.0.4 — MIT

*Added 2026-09-08, vendored as `web/lib/qrcode.js` for the page's pairing code.*

Read from the file's own header: "Licensed under the MIT license", Kazuhiko Arase, 2009. Compatible.
Committed rather than fetched at build time because the page must work with no network beyond the
archives it plays from, and because a script pulled from a CDN at run time is a third party in the
audio path's origin.

## ZXing core 3.5.3 — Apache-2.0

*Added 2026-09-08 for the pairing scanner (`docs/PLAN_HANDOFF.md` §3 H2).*

Read from the `LICENSE` file and the per-file headers, which agree: Apache License 2.0, ZXing
Authors. Compatible with GPL-3.0-or-later in one direction — Apache-2.0 code may be used in a
GPL-3 work, not the reverse — which is the direction this goes.

**Chosen over ML Kit's barcode scanner deliberately**, and for the same reason the decoders are
vendored source rather than services: ML Kit is proprietary and arrives through Google Play
services, so it is a dependency on a *service* and does not exist on a device without them. ZXing is
pure Java and reads a QR out of a byte array.

*Note on the name:* "QR Code" is a registered trademark of DENSO WAVE, which grants free use of the
standard. Nothing here needs a licence from them; recorded so the next reader does not have to
check.

## Our own files

Every source file this project writes carries two lines and no more:

```
// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later
```

They replaced a fourteen-line GPL notice repeated in 79 files. The short form does not stand alone —
the full licence text is in `LICENSE` at the root, which is what GPL-3 §4 asks for — and the
copyright line stays, because an identifier says which terms apply and not who holds them.

**Machine-readable is the point.** This project has twice had to answer "what is this actually
licensed under" about somebody else's code, and twice the answer was not in the file the repository
advertised. Our own files should not need a human to read a paragraph to find out.

**Not applied to `generated/` or `public/` directories** under `native/backends/`. Those stand in
for third-party build output — autoconf headers and installed public headers — and carry those
projects' terms rather than ours.

## Planned native components

| Component | Covers | Licence expected | Verified |
| --- | --- | --- | --- |
| libopenmpt 0.8.9 | MOD, XM, S3M, IT, MPTM and many others | BSD-3-Clause | **yes, 2026-08-31** |
| sc68 **3.0.0b** (SVN r713) | Atari ST SNDH, YM, `.sc68` | GPL-3.0-**or-later** | **yes, 2026-09-03** |
| libsidplayfp 3.1.1 | C64: PSID, RSID | GPL-2.0-**or-later** | **yes, 2026-09-02** |
| game-music-emu 0.6.5 | NSF, NSFE, GBS, SPC, VGM, VGZ, GYM, HES, AY, KSS | LGPL-2.1-**or-later** | **yes, 2026-09-01** |
| ASAP 8.0.0 | Atari 8-bit: SAP + 13 tracker formats | GPL-2.0-**or-later** | **yes, 2026-09-01** |
| HivelyTracker V1_9 | Amiga AHX and HVL | BSD-3-Clause | **yes, 2026-09-05** |
| ZXTune (c93e81d) | ZX Spectrum AY trackers | LGPL-3.0 | **yes, 2026-09-07** |
| audacious-uade-tools | metadata, downloaded not shipped | GPL-2.0-**or-later** | **yes, 2026-09-07** |
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

**sc68 3.0.0b**, checked on 2026-09-03 — SVN r713, fetched by `scripts/fetch-sc68-svn.py` against a
SHA-256 manifest rather than a released tarball, because there is no release. All **78** licensed
sources under `libsc68/`, `file68/` and `unice68/` say *"either version 3 of the License, or (at
your option) any later version"*, and `COPYING` carries the GPL v3 text, so the two agree. The
remaining 95 files are headers with no licence block and no competing claim.

**GPL-2-or-later became GPL-3-or-later with that upgrade.** It is compatible either way — this app
is GPL-3-or-later — but one thing that had to stay true no longer has to.

**sc68 2.2.1** is still fetched, and only so `scripts/probe-sc68.py` can re-run the comparison that
justified the change. Checked on 2026-09-01: `COPYING` carried the GPL **version 2** text, which on
its own would have invalidated our GPL-3 decision — but every one of its 51 licensed source files
said:

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

**HivelyTracker V1_9**, read from `LICENSE` in the tag tarball on 2026-09-05:

> BSD 3-Clause License
> Copyright (c) 2006-2018, Pete Gordon
> All rights reserved.

The three-clause text in full, with the usual non-endorsement clause. `hvl2wav/` — the three files
we build — carries no other notice, and no file in it claims different terms.

**The easiest licence question in this project.** Permissive, so it combines with GPL-3 without
argument; and unlike sc68 and UADE there are no replay binaries, because AHX and HVL are note data
played by code we compile ourselves. Its one obligation is attribution, which this file and
`app/src/main/res/` discharge.

**ZXTune**, revision `c93e81d`, read on 2026-09-07. `LICENSE.md` in the root is the LGPL-3 text, and
the sources we compile carry no per-file notice contradicting it — a doxygen `@file`/`@author` block
and nothing else. LGPL-3 combines with our GPL-3 without argument.

**The danger was `3rdparty/`, and this is the first time in the project that a bundled component had
to be actively avoided.** ZXTune vendors 34 third-party libraries, `z80ex` among them — *"Released
under GNU GPL v2"*, with no "or later", which cannot be combined with GPL-3. In ZXTune it is reached
from exactly two files: `src/devices/z80/z80.cpp`, which wraps it, and `src/module/players/aym/
ayemul.cpp`, the plugin for `.ay` — Z80 machine code rather than tracker data. **Neither is built.**
That is not a workaround; the component is not used, and `native/backends/zxtune/CMakeLists.txt`
says so where the exclusion lives.

Compiling the whole AY path with everything else absent and collecting what it asked for leaves one
third-party dependency: **`fmt`**, MIT.

**How this was found is the point.** The library measured first for these formats was **ayfly**, and
it plays them — 46 of 48 (`docs/PLAN_FORMATS.md` §7). It has no `LICENSE` or `COPYING` file at all,
its twelve player headers carry an author credit and no terms, and the same GPL-2-only `z80ex` sits
inside it referenced by five of its eight sources. Reading a repository's licence file, or trusting
GitHub's summary of it, would have got both libraries wrong in opposite directions.

**minimp3**, commit `ea99364`, checked 2026-09-10. `LICENSE` is the CC0 1.0 text and both headers
repeat the dedication in their own words:

> To the extent possible under law, the author(s) have dedicated all copyright and related and
> neighboring rights to this software to the public domain worldwide.

**A public domain dedication, so there is nothing to comply with** — not even attribution. It is
credited here and will be on the licences screen anyway, because a list of what this app is built
from that quietly omits the parts nobody obliges us to name is a worse list.

Pinned to a commit rather than a release, because there are no releases. GitHub generates these
tarballs rather than storing them, so the checksum can in principle move without the commit moving —
the same caveat HivelyTracker's tag tarball carries, and pinned anyway for the same reason.

**lhasa 0.6.0**, checked 2026-09-09 — fetched by `scripts/fetch-native-deps.sh` against a
SHA-256, like every other tarball here. `COPYING.md` is the ISC text:

> Copyright (c) 2011-2025, Simon Howard
> Permission to use, copy, modify, and/or distribute this software for any purpose with or without
> fee is hereby granted, provided that the above copyright notice and this permission notice appear
> in all copies.

**All 35 sources and headers under `lib/` repeat that grant per file**, so for once `COPYING` and
the sources agree — the first time in this project that checking both was uneventful. ISC is
permissive and combines with GPL-3 without argument; its one obligation is to reproduce the notice,
which is the same undischarged obligation libopenmpt's BSD leaves and which one licences screen will
settle for both.

**Why we fetch it rather than use the copy already on disk.** ZXTune vendors its own `3rdparty/lhasa`
— an older release plus a `zxtune.patch` that changes a header include and adds `extern "C"`. Taking
that copy would mean a second, forked lhasa arriving inside a sparse clone, with no pin of its own
and no checksum. Upstream is pinned and verified like everything else, and the forty lines of
difference live in one file of ours, `native/backends/zxtune/lha_zxtune.cpp`, which says so at its
head.

**It decodes no music.** It is here because two separate wishes need the same mechanism: ZXTune's
`.ym`/`.vtx` decoder reads an LHA-compressed stream, and every UnExoticA tune lives inside a `.lha`
archive.

**audacious-uade-tools**, checked 2026-09-07. `COPYING` is the GPL **version 2** text alone — the
fourth time in this project that reading it would have given the wrong answer — and the generator
scripts carry `SPDX-License-Identifier: GPL-2.0-or-later`. Compatible with GPL-3, and the check took
five seconds because the identifiers are there, which is the argument for the pass we made over our
own files the same day.

**Downloaded, not shipped.** The metadata table is fetched on request and stored in the user's
database, the same bargain as HVSC's song lengths: nothing of theirs is in the APK, the user chooses
to have it, and the storage screen can throw it away again.

## sc68's replay binaries — a question for the owner

**Raised 2026-09-01. Not a blocker today; not something to publish without an answer either.**

sc68 does not play SNDH from the tune alone. Each one names a small 68000 replay routine that ships
in sc68's replay directory, and sc68 opens it by path — which is why every SNDH in the owner's
library loaded and then played silence until those files were packaged. **99 of them are now in the
APK** (3.0.0b's `file68/data68/Replay/`, up from 2.2.1's 84 — part of why more SNDH files play).

They arrive inside sc68's own GPL tree and `AUTHORS` credits Benjamin Gerard as its programmer
with no separate statement about their origin. That is the whole of what is known. Some are plainly
his (`sndh_ice.bin` is sc68's own SNDH wrapper); others are named after commercial Atari games
(`alteredbeast.bin`, `cabal.bin`, `armalyte.bin`) and look very much like routines lifted from those
games, which is the **same** question already recorded above for UADE.

For a private build this is academic. Before anything reaches a store, the owner should decide.

### What each option actually costs, measured 2026-09-03

The question stopped being a judgement call once it was measured. Of the 99 binaries, exactly
**one** — `sndh_ice.bin` — is plainly sc68's own; the rest are named after commercial Atari ST games
(`alteredbeast`, `cabal`, `armalyte`) and after other people's players (`chipmon2`,
`bendaglish.deli`). `AUTHORS` says nothing about any of them.

Run through the real backend with the replay directory cut down to that one file:

| shipped | `.sndh` (30 files) | `.sc68` (10 files) |
| --- | --- | --- |
| all 99, 1.2 MB | **30 plays** | **10 plays** |
| `sndh_ice.bin` only, 4 KB | **30 plays** | 4 plays, 6 silent |

**Shipping only sc68's own replay costs nothing for SNDH.** That is the format this project was
started for and 5,484 files in Modland. What it costs is most of `.sc68`, which is 1,775 files.

So the three options are now:

1. **Ship all 99.** What every comparable player does — UADE and Deliplayer carry the same kind of
   extracted code — and the realistic worst case is a takedown rather than a lawsuit. "Everybody
   does it" is still not a licence.
2. **Ship `sndh_ice.bin` only.** SNDH is unaffected; `.sc68` largely stops working. Defensible
   without argument, and 4 KB instead of 1.2 MB.
3. **Ship `sndh_ice.bin` and offer the rest as a download.** The machinery exists — this is what
   already happens for the ASMA archive and the HVSC song lengths, and
   `docs/LICENSES.md` already records why downloading is different from distributing: the user
   fetches it from the project that publishes it, and we redistribute nothing.

**Recommended: 3.** It keeps the format the project exists for working out of the box, keeps
`.sc68` available to anyone who wants it, and moves the one genuinely doubtful 1.2 MB out of the
APK — which is where the doubt actually lives.

### Decided 2026-09-04: option 3, for sc68 and UADE alike

**And two letters, because the only genuinely clean route is to ask.** The drafts and the replies
stay on the owner's machine: `docs/letters/` is gitignored and was taken out of the history on
2026-09-05, because other people's replies are not ours to publish.

| | sent | to | status |
| --- | --- | --- | --- |
| UADE | **2026-09-04** | `heikki.orsila@iki.fi` | answered by **both maintainers**, exchange closed 2026-09-05 |
| sc68 | not yet | SourceForge ticket | the owner is holding off |

*The address stands because UADE publishes it*, in its own `AUTHORS` file, as the way to reach its
main author. Repeating a contact address a project gives out for the purpose is not the same as
exposing one — and it is the address the letter was sent to, which is the fact this row records.

**A third thing an integration would need, found on the way out.** UADE cannot identify several
Hippel and TFMX variants from their filenames, and the fix is a `song.conf` of md5 overrides
maintained alongside the Audacious plugin. Its licence is **GPL-2.0-or-later** — compatible with
ours — but the `conf/songdb` beside it is **CC BY-NC-SA 4.0**, non-commercial, and could not ship in
a store app. Take `conf/song.conf`; leave `conf/songdb`. GitHub's API labels the whole repository
"GPL-2.0"; the README is the accurate source (`docs/PLAN_FORMATS.md` §4).

**Neither the work nor the release waits on either.** `sndh_ice.bin` is sc68's own file in sc68's
own GPL tree and needs no permission, and UADE is not integrated at all. The letters improve what
this document can say; they do not gate anything.

**What actually reduces the risk, in order of how much it does:**

1. **Not shipping the files.** Copyright is about copying and distributing. In the APK we are the
   distributor, to every install. Fetched by the user's device from SourceForge or GitLab, the
   distributor is SourceForge or GitLab. That is a real difference, not a formal one, and it is
   where nearly all of the benefit comes from.
2. **Fetching from the project's own published location** — never a mirror we control, which would
   put us straight back to distributing.
3. **Not misrepresenting what they are.** The notice says plainly: other people's code, of
   unestablished status, extracted from programs of the 1980s and 90s.
4. **A notice the user reads before it downloads.** This is honesty and evidence of good faith.

**What a disclaimer does not do is transfer liability**, and it is worth writing down because it is
the intuitive and wrong answer. "The user accepted the terms" does not turn an infringing act into a
non-infringing one; if the copying were unlawful, a checkbox would not launder it. The safety comes
from item 1, not from consent.

**What remains, honestly:** inducement is a real doctrine, and an app that exists to fetch and run
unlicensed code does not answer it by declining to host the code. Our position is stronger than that
— these files have been published openly by their projects for twenty years and nobody has been
challenged — but that is an argument about *risk*, not about *cleanliness*. The realistic worst case
is a takedown request rather than litigation; most of the rights holders are individuals, many still
in the scene, whose interest is in the music being heard.

**None of the above is legal advice**, and the letters exist because the one route with no "but"
left at the end is a person saying yes.

### What the UADE enquiry settled, 2026-09-04

Both of UADE's maintainers answered, within a day. Three things in the reply change what this
document can say, and all three were checked against the tree afterwards.

**Their words are not reproduced here and their manner is not characterised**, which is the standing
rule for a private reply (`docs/PLAY_STORE.md`). Everything below either rests on a document UADE
publishes — quoted as such — or is our own measurement. The one fact that exists nowhere public is
marked where it appears.

**1. There is exactly one known objection, and it is specific.** In twenty years of shipping these
176 binaries, one complaint has been raised — by the Delitracker authors, about their own work.
*This is the fact with no public source; it came from the reply and is recorded because it is
load-bearing below.* An identifiable group about identifiable work, and nothing else in two
decades.

That is directly relevant to us and not only to UADE. sc68's replay set contains **eight
Delitracker-derived files** — `bendaglish.deli`, `hippel-coso_note.deli`, `mon_old.deli`,
`robhubbard.deli`, each in two forms — and our download fetches all 98. UADE has one,
`DaveLowe_Deli`. We do not distribute them and never did, but "the one family anybody has ever
objected to" is worth knowing by name rather than discovering later.

**2. `amigasrc/` is a stronger statement than `COPYING`**, and the reply is what sent us to look at
it. The tree is UADE's own and its README is public:

> All these files are certainly distributable, but some are not Open Source Initiative approved.

That is a much better position than `COPYING`'s "various different licenses and quite a many
different copyright holders" about `players/` — and both sentences are the project's own published
text, which is exactly what one should be quoting while reasoning about a project's licence.

Counted, and **the count is a name match, not an authority** — that matters, so it is said before
the number. `amigasrc/players/` holds 19 directories under `uade`, 98 under `wanted_team`, 7 under
`other` and 5 under `defect`. Matching those names against the 176 shipped binaries:

| | |
| --- | --- |
| exact name match | **99** |
| plausible but not exact | **35** |
| no candidate at all | **42** |

The middle row is why the first number cannot be quoted alone. `players/BenDaglish` and
`amigasrc/players/wanted_team/BennDaglish` are obviously the same player and differ by a letter;
`ArtOfNoise-4V` and `uade/artofnoise` are obviously related and do not match at all. A first pass
that only compared normalised names reported 99 and 77, and 77 was wrong.

So: **at most 42 of the 176 have no source in the tree**, probably fewer, and the doubtful remainder
is much smaller than `COPYING` alone suggests. Turning "probably" into a list means reading the
`EP_*.readme` files, which is work worth doing before anyone relies on it.

**The limit of that count is the important half**, and it was not lost on the people who answered:
reverse-engineering a routine is not the same as being given permission for it. A routine somebody
worked out from the bytes is not a routine somebody licensed. That is a general point and ours to
state; the reply is merely where we were reminded of it.

**3. The download route is invited, not merely tolerated.** UADE publishes the official binaries at
<https://zakalwe.fi/uade/download.html>, a page that exists for exactly this purpose, and the reply
confirmed they are there to be taken. Fetching them from GitLab instead is a matter between us and
GitLab.

So when UADE is integrated the download route is not merely defensible, it is the one upstream
offers. The trade between the two sources was pointed out in the same reply and is easy to verify
for oneself: zakalwe.fi is the project's own and can serve whatever shape is needed, GitLab is more
reliably up. **Prefer zakalwe.fi and fall back to GitLab** — taking what the authors offer, and
staying working when their server is not.

**What this does not settle**: the at most 42 binaries with no source in the tree, and whether the
Delitracker family should be left out of a download by choice rather than by necessity. Both are the
owner's, and neither blocks anything today.

### Built 2026-09-04

- **The APK ships one replay**, `sndh_ice.bin`, 424 bytes. It was 99 files and 1.2 MB; the generated
  asset directory is now 16 KB. SNDH is unaffected — 30 of 30 before and after.
- **The other 98 are fetched from sc68's own SourceForge**, at revision 713, the same one the
  vendored library was built from. A tune names a replay and the two have to agree about what that
  name means, so fetching HEAD would be fetching another library's data.
- **Ninety-eight requests, not an archive**, because sc68 publishes no archive of them: 3.0.0b
  exists only in SVN, whose HTTP interface serves a listing and the files under it. Slower than a
  zip, and the only route that keeps somebody else the publisher. Verified against the live server:
  the listing parses to exactly 98 names, none with a path separator in it, and sampled fetches
  return the right bytes.
- **Downloads live outside the tree an app update wipes.** `NativeData.ensureUnpacked` deletes and
  rebuilds its directory whenever the version changes — deliberately, so a stale replay cannot
  outlive an update — and downloaded files would have gone with it silently, leaving `.sc68` broken
  again after an update with nothing to explain why.
- **The notice comes before the download** and says what these are: other people's code of
  unestablished status, and that this app is not the one handing it out. Honesty, not a shield.
- **They can be deleted**, from the same storage section as everything else, with the consequence
  named: `.sc68` goes back to mostly silence and SNDH does not notice.

A `Sync` task replaced a `Copy` while doing this. `Copy` only ever adds, so narrowing the task from
99 files to one would have left the other 98 in the generated assets and shipped them anyway — and
the same would have happened to any file dropped upstream, silently and in the APK.

## UADE's replay binaries — the same question, with worse paperwork

**Measured 2026-09-04 while doing `GOAL.md` round 6 item 1.** UADE is not integrated; this is what
was found before deciding whether to integrate it, so that the decision is not made twice.

UADE works the way sc68 does and more so: it emulates an Amiga in order to run **the original 68000
replay routines**, and those routines ship as 176 binaries in its `players/` directory — 1,232 KB.
Without them UADE plays almost nothing, because for these formats the tune file is note data and the
player is a separate program.

**Upstream's own statement is the weakest of any dependency here.** `COPYING`:

> Files under players/ directory are licensed with various different licenses and quite a many
> different copyright holders. See inside the player binaries for more details.

So we looked inside. **118 of the 176 carry a copyright string**, and they name individuals and
companies rather than UADE:

```
AM-Composer            (c) 1989 by Marc Hawlitzeck
BeathovenSynthesizer   (c) 1987-91 by Thomas Lopatic
BenDaglish             (c) 1988-92 by Colin Dooley & Ben Daglish
Cinemaware             (c) 1990 by Cinemaware
DavidWhittaker         (c) 1987-94 by David Whittaker & Rob Hubbard
```

This is a stronger version of the sc68 question, not a different one. sc68's `AUTHORS` at least
credits one programmer and says nothing further; UADE states plainly that the directory is other
people's work under unknown terms, and the binaries agree.

**What this means for the decision the owner has deferred:** it is one decision, not two. Whatever
policy settles sc68's 99 binaries settles UADE's 176, because the facts are the same shape and
UADE's are better documented as third-party. Option 3 above — ship what is plainly ours, offer the
rest as a download — extends to UADE unchanged, and the machinery it needs already exists.

### What each option costs, measured 2026-09-04

The same corpus, run twice: once with UADE's `players/` directory as shipped, once with it empty.

| shipped | plays, of 300 files across 25 formats |
| --- | --- |
| all 176 binaries, 1,232 KB | **196** |
| none | **12** |

The first published run said 184. That was a probe defect, not a UADE limitation:
`UC_CONTENT_DETECTION` enables strict content-only identification and disabled the filename fallback
some players require. The corrected figure is from the same seeded 300 files. The empty-directory
result is unchanged: its twelve successes are self-contained Delitracker Custom executables and do
not depend on filename fallback or a binary from `players/`.

**UADE without its replay binaries is not a reduced player, it is not a player.** The twelve
survivors are all Delitracker Custom — `.cus` files, which *are* 68000 programs and carry their own
replay inside the tune. Everything else needs a binary from that directory.

**This is where UADE differs from sc68 in kind, not degree.** sc68 had a middle option:
`sndh_ice.bin` is plainly sc68's own, it is 4 KB, and keeping only it costs nothing for SNDH. UADE
has no equivalent — there is no "our own" replay to keep, because running other people's replays is
the entire technique. So the options are two, not three:

1. **Ship all 176.** 1,232 KB of other people's code, explicitly described by upstream as
   third-party under unknown terms, with 118 of them naming their authors on the way past.
2. **Ship none and offer them as a download**, the way the ASMA archive and the HVSC song lengths
   already work — the user fetches from the project that publishes them, and we redistribute
   nothing. Until they do, the app plays 12 files in 300.

There is no version of UADE worth shipping that does not answer this question first, which is
exactly why `docs/BACKLOG.md` A5 put the licence before the code.

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
