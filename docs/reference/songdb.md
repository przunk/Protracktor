# audacious-uade-tools: a database of what these files are

*Surveyed 2026-09-07, on the owner's pointer.*

`mvtiaine/audacious-uade-tools` publishes TSVs keyed by **the first 48 bits of a file's MD5**, built
from around 400 archives. **476,919 unique hashes** with song lengths and module infos; **380,000**
with metadata — author, album, publisher, year.

**GPL-2.0-or-later**, read from `SPDX-License-Identifier` lines in the scripts rather than from
`COPYING`, which is the version 2 text alone. Compatible with our GPL-3. That check took five
seconds and is the argument for the SPDX pass we did the same day.

The repository is 606 MB; the files that matter are about 30 MB and would be downloaded on request,
the way HVSC's song lengths already are.

## What it is worth, honestly

**The song lengths are mostly redundant, and that is worth saying first.** 177,421 of the 340,404
Modland files this app claims have a length in the database — but 165,000 of those are MOD, XM, IT
and S3M, and **libopenmpt already states a duration for every one of them**. The real gap is
narrower: SIDs outside HVSC, and the game-music-emu files that state no length at all.

**The year is the find.** 67,601 of the files we claim would gain a release year — **19.9%** —
and the formats that gain most are the ones that *cannot carry a year at all*: 40,161 ProTracker,
11,733 Fasttracker 2, 5,920 Impulsetracker. `docs/WISHLIST.md` B20 records that plain `.mod` and
`.xm` have nowhere in the file to put a date, and that getting one "means an external database keyed
by something like the file's hash — the same shape as HVSC's song lengths". This is that database,
and it already exists.

**`modinfos.tsv` names the real format per hash**, which is the answer to a class of bug this
project keeps meeting: `.dmf` is 1,807 DefleMask and 366 X-Tracker, `.ftm` is 1,779 FamiTracker and
95 Face The Music, `.psm` is 90 MASI and 51 Pro Sound Maker. Today the app finds out by trying and
failing. A hash lookup would let it know before it offers the file.

**And it carries index sources for about fifty archives**, UnExoticA among them — which is how this
was found.

## UnExoticA, measured from it

8,713 tunes with paths. **Our format list covers 4,479 — 51.4%.** The half we do not cover is
almost exactly UADE's territory: Sierra AGI (745), Sonix Music Driver (301), the P40A/P50A/P60A
packers (451), CustomPlay (204), TFMX (314), David Whittaker (119), Sonic Arranger (116), Richard
Joseph (105).

**Do not read the `player` column as a verdict.** It says `uade` for 8,688 of 8,713 — including
ProTracker, which we play through libopenmpt. It records what *that* project uses, and that project
is UADE tooling. The format column is the signal.

**Every file lives inside a `.lha` archive**, addressed as `Game/Author/Title.lha/Title/mod.name`.
So the unit of download is a game's whole soundtrack, which is closer to ASMA's model than to
Modland's — and it needs LHA extraction, which this app does not have. ZXTune vendors `lhasa` and we
do not build it.

## Modland's favourites, taken 2026-09-08

`songdb/sources/site/modland_favourites.tsv`, 142 KB. **Not keyed by hash like the rest** — its
eleventh column is Modland's own path, which is the same string our catalogue index holds, so it
joins directly.

**Two row shapes, and only one names a file.** 1,163 rows: 991 of eleven columns ending in a path,
172 of four columns saying how long one subsong is and whether it is `player`, `loop`, `silence` or
`nosound`. Taking the last field of every row yields 172 entries called "loop".

Measured against `allmods.txt` the same day: **891 of the 991 paths still exist** in Modland, and
**835 survive the extension filter an index is built through**. The rest are formats no backend here
claims — `.aon`, `.dw`, `.dm2`, `.hip`, `.cus`, six or fewer each.

By directory, of the 835: ProTracker 562, Fasttracker 2 198, AHX 46, Impulsetracker 11,
Screamtracker 3 7. **It is an Amiga list**, which is why the Random scope does not offer it
combined with a platform (`docs/WISHLIST.md` B27).

## What this does not settle

Whether to depend on a third-party dataset that moves. It is the same bargain as HVSC — fetched on
request, verifiable, replaceable — but it is a second one, and each adds a thing that can go stale
without anybody noticing.
