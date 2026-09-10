# UnExoticA — Amiga game soundtracks, behind a switch

*Built 2026-09-09, on the owner's instruction and before the archive's maintainers have answered
the letter in `docs/letters/2026-09-08-exotica-unexotica.md`. That order is deliberate and this
document exists to make it reversible.*

## Why it exists

The owner went looking for an Amiga game's soundtrack on 2026-09-07, found only the SNES rip under
Nintendo SPC, and asked where the Amiga one lives. Modland is organised by musician and is mostly
demoscene — that is the shape of the archive rather than an omission, and it leaves out the whole
category of music most people actually remember. UnExoticA is the archive for that, and it is
indexed by game.

## Why it is built before the answer

`files.exotica.org.uk/robots.txt` is a blanket `Disallow: /`. Our reading, which has not changed, is
that robots.txt governs crawlers rather than a user's own client fetching a file they asked for. But
it is a volunteer archive's bandwidth and a published app would point every user at it, so the
question was asked rather than assumed.

The owner's decision on 2026-09-09: *"czekam na maila ale możemy przed publikacją używać już wersji,
która spełni moje osobiste marzenia… W razie czego wyłączymy funkcję lub ją usuniemy. Proponuję
dodać jako zależność/funkcję łatwousuwalną."* Nothing is published, no user but him is pointed at
the archive, and the feature comes out if the answer is no.

**What that requires of the code is the only interesting constraint here**, and it is why this
document is not just a note in `docs/PLAN_CATALOGUES.md`.

## Turning it off

`UnExoticA.ENABLED` — one `const val` in `app/src/main/kotlin/com/przunk/protracktor/net/UnExoticA.kt`.

Setting it to `false` removes the catalogue from `Catalogue.all`, which is where everything else
reads it from: the browse screen, `byId`, `owning`, search, Random and the storage screen. Rows
already in the database are deleted at the next start-up by `CatalogueStore.pruneUnknownCatalogues()`
— that call exists for this and is the difference between *off* and *hidden*. Cached `.lha` files
age out of the ordinary download cache like anything else.

## Removing it entirely

Four files and three lines:

| | |
|---|---|
| `app/.../net/UnExoticA.kt` | the catalogue and the `Lha` wrapper — delete |
| `app/.../test/net/UnExoticATest.kt` | delete |
| `native/engine/lha_jni.cpp` | the whole native surface — delete, and its name from `native/engine/CMakeLists.txt` |
| `native/lhasa/lha_extract.{h,cpp}` | the archive reader — delete, and its `target_sources` line |
| `Catalogue.all` | drop the conditional |
| `PlaybackController.loadBytes` | drop the first branch, and `loadFromUnExoticA` with it |

**`native/lhasa` itself stays.** lhasa is not this feature's dependency alone: ZXTune's `.ym` and
`.vtx` decoders read an LHA-compressed stream through it, which is 5,840 Modland files
(`docs/PLAN_FORMATS.md` §8). Those two uses share the library and nothing else — the YM path uses
the raw decoder, this one uses the archive reader — so removing either leaves the other whole.

`RemoteFiles`'s verification-gate retry also stays: it is written against the behaviour, not against
this host, and any archive that puts a "verifying your browser" page in front of a download would
otherwise store that page as if it were a module.

## The three things that make it a different shape

**The index is somebody else's.** ExoticA publish no machine-readable list. The track listing comes
from `mvtiaine/audacious-uade-tools` — `songdb/sources/site/unexotica.tsv`, 1.7 MB, GPL-2.0-or-later
(`docs/reference/songdb.md`) — so the app never walks their directory tree. It is a TSV of two row
shapes sharing one file: eleven columns name a file, four state how long a later subsong is. Column
6 is the module format, **column 8 the uncompressed size**, column 11 the path. Column 3 is a song
length in milliseconds and looks exactly as much like a size, which is what the test is for.

**The unit of download is a game.** Every tune lives inside a `.lha` addressed as
`Game/Composer/Title.lha/Title/mod.name`. Playing one fetches the archive and unpacks one member;
the archive lands in the ordinary file cache, so the rest of that soundtrack costs nothing and ages
out under the same budget as everything else. Measured: `Total_Recall.lha` is 315 KB for six tunes,
`Lotus.lha` 357 KB for six.

**The address is a query, not a path.** `files.exotica.org.uk` serves through
`?file=exotica/<percent-encoded path>`, slashes included. `docs/PLAN_CATALOGUES.md` recorded a
directory-shaped rule for a day and it was wrong — every plausible form of it answers 404. The real
one was read off the download link on the site's own wiki page.

And one behaviour that is not a shape but bit immediately: **a cold client gets a doorman.** The
first request answers 200 with a few hundred bytes of *"Verifying your browser…"*, a `Set-Cookie`
and a script that reloads. A plain fetch stores that as if it were a module and the failure arrives
later, as a decoder refusing a file that downloaded fine. `RemoteFiles.download` now retries once
when a response set a cookie, is `text/html`, and is under two kilobytes — three conditions no index
and no module can meet at once.

## What it is worth, measured 2026-09-09

8,713 tunes with paths. **4,338 pass our extension filter — 49.8%**, across **924 game archives**.
Median tune 53 KB.

| kept | | dropped | |
|---|---|---|---|
| Protracker | 2,243 | Sierra AGI | 745 |
| Protracker compatible | 774 | Sonix Music Driver | 303 |
| Soundtracker II (15 instr.) | 307 | P40A–P41A | 234 |
| OctaMED MMD0 | 230 | P50A–P61A | 217 |
| Startrekker (4ch) | 209 | CustomPlay | 204 |
| SoundFX | 146 | TFMX | 167 |
| OctaMED MMD1 | 136 | TFMX Pro | 148 |

**The half we do not cover is almost exactly UADE's territory**, and this is a second argument for
revisiting it rather than an argument against the catalogue. It has one visible consequence worth
saying plainly: **Turrican is not there.** Chris Hülsbeck wrote in TFMX and nothing in this build
decodes it, so the most famous Amiga game soundtrack of all is indexed by ExoticA and absent from
ours. So are Shadow of the Beast's Sierra AGI relatives and every David Whittaker `dw.` tune.

## Browsing

Catalogue → format → author → tracks is the model the store and the browse screen already have, and
the mapping chosen is **format = `Game` or `Demo`** (the archive's own top-level split, and its only
two values) and **author = the game**, prettified from the `.lha` name: `Total_Recall` → `Total
Recall`.

The composer loses its slot, and that is the trade. The archive is organised around games, the
owner's question was about a game, and "more from this author" then means "the rest of this
soundtrack", which is the useful reading here. The composer is still in every stored path, and most
of these modules carry their author internally where the decoder can read it.

## Verified end to end, 2026-09-09

Against two real archives rather than a fixture:

- `Game/Whittaker_David/Total_Recall.lha` — 315,478 bytes, six members, `Total_Recall/dw.ingame_2`
  extracts to **33,014 bytes, exactly the size the index states**.
- `Game/Southern_Shaun/Lotus.lha` — 356,778 bytes, `Lotus/mod.track 1` extracts to 75,236 bytes
  with `M.K.` at offset 1080 and the title `track 1`. A valid ProTracker module.

Both archives store members as `Lotus\mod.track 1`, with a backslash, while the index names them
with a forward slash. `lhaExtract` normalises both and matches case-insensitively, which is not
defensiveness — it is the difference between the feature working and not.

## Still open

- **Nothing has been played on the phone yet.** The extraction is verified on the host and the
  build is green; the first real listen is the owner's.
- **The index and the archives can drift.** songdb holds a snapshot, not ExoticA's live tree. A
  member the index names and the archive does not hold is logged as
  `UnExoticA: <member> is not in <url>`; if that appears at all, this is why.
- **No attribution in the app.** The letter promises UnExoticA is credited by name wherever its
  tracks appear. `displayName` does that in the browse list, and the licences screen that
  `docs/LICENSES.md` has been waiting for is still not written.
- **The reply.** If it is no, the section above says exactly what to delete.
