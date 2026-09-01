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

### ASMA — Atari 8-bit

The reference collection of `.sap`. Natural pair with ASAP.

- `https://asma.atari.org/` responds; `asma.zip` does **not** (404). The distribution URL and its
  shape are unknown — find them first.
- ASMA ships an `.stil`-style info database, which would give durations and credits the way HVSC
  does for SID.
- **Blocked on ASAP** (`docs/PLAN_FORMATS.md` §3).

### HVSC — Commodore 64

- Not an index-plus-fetch archive: it is distributed as one large collection, and the whole thing is
  a plausible download rather than a per-track fetch. That is a **different shape** from Modland and
  the code does not currently express it — expect a second kind of `Catalogue`, not another parser.
- `Songlengths.md5` (5.2 MB) fetches directly — verified 2026-08-31. Worth having on its own, before
  any browsing, because it is what gives SID tunes a duration.
- **Blocked on `libsidplayfp`.**

### The Mod Archive

- `https://api.modarchive.org/` responds (2026-09-01). It has a documented XML API, and it wants an
  **API key**, which is a question for the owner: a key in a public GPL repository is a key that is
  no longer private.
- No single index file. This is a paged, queried archive — browsing would be online rather than
  offline, which breaks the property that makes Modland pleasant. Consider search-only integration.
- Formats are mostly trackers, so it is **playable today** — the only candidate here that is.

### AMP (amp.dascene.net)

- Responds (2026-09-01). Heavy on Amiga custom formats, which is exactly what we cannot play.
- Index format unknown; historically a web interface rather than a published index.
- **Blocked on UADE**, which has its own licensing question.

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
