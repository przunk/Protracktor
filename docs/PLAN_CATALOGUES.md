# Plan: more online catalogues

Written 2026-09-01. Modland is wired up and verified end to end; this is about the rest.

---

## What Modland established

The shape works and is worth restating, because every catalogue below is judged against it:

- One downloadable index, parsed once into `catalogue_tracks`.
- **Browsing then needs no network.** The network is next touched when an uncached track is played.
- A track's identity is its URL; the cache keys on it.
- Only entries a backend might handle are indexed.

Adding a catalogue means writing `parseIndex` and `urlFor` on a new `Catalogue` subclass. Everything
above it — browsing, search, random, caching — is already generic.

**The blocker is rarely the parser.** It is usually that we cannot play what the archive holds.
Indexing an archive of SIDs before `libsidplayfp` exists produces a browsable list of files that fail
to open, which is worse than not offering it.

---

## The candidates

Ordered by what they unlock, with what was actually checked on 2026-09-01.

### ~~ASMA — Atari 8-bit~~ — DONE 2026-09-02

The reference collection of `.sap`. Natural pair with ASAP.

- Found: `https://asma.atari.org/asmadb/asma.zip`. Not an index — **the whole collection**, 20 MB
  holding 6,335 `.sap` files. `asma/Docs/Asma.txt` looked like it might be a metadata index and is
  four lines of version banner.
- That made it the second kind of catalogue predicted below for HVSC; `docs/ARCHITECTURE.md` §13
  records the shape.
- ASMA ships an `.stil`-style info database, which would give durations and credits the way HVSC
  does for SID.
- ~~Blocked on ASAP~~ — ASAP landed 2026-09-01, which is what unblocked this.

### HVSC — Commodore 64 — song lengths DONE 2026-09-02, browsing not built

- Not an index-plus-fetch archive: it is distributed as one large collection, and the whole thing is
  a plausible download rather than a per-track fetch. That is a **different shape** from Modland and
  the code does not currently express it — expect a second kind of `Catalogue`, not another parser.
- `Songlengths.md5` (5.2 MB) fetches directly — re-checked 2026-09-02, still 5,205,150 bytes at the
  address recorded in `docs/ARCHITECTURE.md`. **Done**: downloaded on demand from the online screen,
  parsed and stored, and looked up whenever a backend reports no duration of its own.
- The key is the **plain MD5 of the whole file**, not the header hash that libsidplayfp still
  exposes as `SidTune::createMD5`. Older HVSC releases used the latter; this one does not. Checked
  rather than assumed — three tunes fetched from the collection, all three found by file MD5.
- ~~Blocked on `libsidplayfp`~~ — landed 2026-09-02.
- Browsing the collection itself is **still not built**, and is a bigger job than the lengths were:
  ASMA's archive shape (`docs/ARCHITECTURE.md` §13) is the model, but HVSC is an order of magnitude
  larger and a whole-collection download is a different proposition at that size.

### The Mod Archive — search-only integration DONE 2026-09-02

- `https://api.modarchive.org/` responds (2026-09-01). The official XML API requires an API key
  that is no longer issued via automatic self-service (requires contacting staff).
- Instead of waiting for API keys, **integrated via live web search parser** (2026-09-02):
  - Queries `https://modarchive.org/index.php?request=search&query=...` directly.
  - Direct downloads via `https://api.modarchive.org/downloads.php?moduleid=...` require no key
    or session, and are cached on-demand by `RemoteFiles`.
  - Introduced `Catalogue.isOnlineOnly`: The Mod Archive participates in Search domain filters
    without requiring a multi-megabyte local offline index download.
  - Generates shareable web URLs pointing to `https://modarchive.org/index.php?request=view_by_moduleid&query=...`.
- Supported tracker formats (MOD, XM, S3M, IT) are **playable today** via libopenmpt.
- **Future direction:** `docs/WISHLIST.md` B19 envisions server-hosted periodic index dumps to allow
  offline browsing in addition to live search.

### AMP (amp.dascene.net) — investigated 2026-09-04, and the answer is no

`GOAL.md` round 6 item 2 said: if AMP publishes nothing machine-readable, stop and write that down
rather than scraping a website into a fragile parser. It publishes nothing machine-readable, and
there is a second reason that matters more.

**What was checked:** `/api`, `/list.php`, `/downloads/`, `/sitemap.xml` — all 404. `/download.php`
is a page about donating, not a data endpoint. There is no bulk archive the way ASMA publishes one
and no tab-separated index the way Modland does. The site is a PHP front end over a database, and
the only route to a module is `downmod.php?index=N`.

**And `robots.txt` asks us not to take it:**

```
User-Agent: *
Disallow: /downmod.php
Disallow: /downmod.php?
Disallow: /downmod.php*
Disallow: /modules/
```

The download endpoint and the module directory are exactly what an index would have to walk and
what the app would then fetch from. That is the site telling automated clients to stay off its
music, and it is not ours to reinterpret because our automated client happens to have a person
behind it. It changes this from "hard to parse" to "asked not to", which is a different kind of no.

**So AMP is not blocked on UADE.** It was recorded that way, and after 2026-09-04 that is no longer
the binding reason: even with every Amiga format playing, the sanctioned way in does not exist. If
AMP is ever wanted, the move is to ask them — they have a forum and a contact page — not to write a
parser.

### Aminet

- `https://aminet.net/mods/` returns 404 — the path is wrong, not the site. Aminet publishes a
  machine-readable `INDEX` per directory, which is promising.
- Holds archived `.lha` files rather than bare modules, so this one needs **archive extraction**
  before it needs anything else. libopenmpt unpacks some containers itself; `.lha` is not among them.

---

## Work that is shared, and should come first

Three things every additional catalogue needs, none of which exists yet:

1. **Cache eviction.** Nothing is ever deleted today (`docs/OPEN_QUESTIONS.md` Q5). One archive is
   fine; several are not.
2. **Re-index without losing the user's place.** `replaceIndex` currently deletes and rewrites a
   catalogue's rows wholesale. That is correct but blunt, and it will be run every time a backend is
   added.
3. **Progress that means something.** The index download shows an indeterminate bar. For Modland's
   half-million rows on a phone that is a long time to say nothing specific. Insert progress is
   countable; show it.

---

## Recommendation

Do **nothing** here until `docs/PLAN_FORMATS.md` items 1 and 2 are done. Every catalogue except The
Mod Archive is blocked on a decoder, and The Mod Archive is the one whose shape fits our design
worst. The best next move for online music is not another archive — it is being able to play more of
the one we already have.

**2026-09-04 made that recommendation literal rather than rhetorical.** Five names added to
`SupportedFormats` gave Modland 5,557 more playable files than adding any archive would have, at a
fraction of the cost, and AMP — the archive this section was holding a place for — turns out not to
be available on any terms we should take. Playing more of what we have is not a stopgap until the
next catalogue; it is the better move on the numbers.
