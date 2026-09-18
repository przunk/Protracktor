# Plan: a web version

Written 2026-09-08, expanding `docs/WISHLIST.md` **B5** — *"the same music in a browser, sharing
state with the phone through an account"*, raised on 2026-09-01 and left as a thought.

**Nothing here is decided.** This document exists because B5 itself said the idea *"would change
decisions we are making today"*, and a sentence like that is worth either acting on or striking. It
turned out to be right, and the decisions it touches are listed in §11.

---

## 0. What was measured and what was only read

Following `AGENTS.md` §7, every claim below carries one of three labels. Nothing in this document is
**checked** in the strong sense, because nothing has been built.

| state | what it means here |
| --- | --- |
| **measured** | counted or requested from this machine on 2026-09-08, against `develop` at `b9924af` |
| **read** | found in somebody else's repository or documentation and believed, but not built or run here |
| **reasoned** | follows from what we already decided, and could be wrong the way an argument can be wrong |

**Reviewed the same day by a second session**, which found four things: the size question this
document said should be measured first (**§4** — measured, and the fear was unfounded), two false
positives in §3's portable bucket, the wrong measure of `PlaybackController`'s coupling, and the
preflight gap §6 had flagged in itself. Every one of its measurements was re-run here before being
folded in; the one number in its report that did not replicate was a file path, and the finding
behind it did. Its corrections are marked in place rather than silently applied, because a document
that hides having been wrong teaches nothing.

---

## 1. B5 said three things. One was wrong, one was too modest, one was the important one

### "the decoders … would need WebAssembly builds (libopenmpt already ships one; sc68 does not)"

**The sc68 half is wrong** (*read*). `webSC68` is an Emscripten port by Juergen Wothke, and it is
based on **sc68 3.0.0b** — the same version this project moved to on 2026-09-03 and the reason C1
was fixed. So the backend that looked like the blocker is the one with the closest match upstream.

The claim was written on 2026-09-01, two days before we had 3.0.0b ourselves, so it was reasonable
when written. It is recorded as a correction rather than quietly edited, per `AGENTS.md` §7.

### "the parts that would carry over unchanged are `PlayQueue`, and the schema in `SchemaSql`"

**True but far too modest** (*measured*). See §3. The number that matters is not the list of files
already free of Android — it is that `PlaybackController`, at 2,963 lines the largest file in the
project, touches Android in only six imports. What §3 also finds, after a review corrected the first
draft, is that the six imports are not where its coupling lives: it takes an injected `context` used
41 times and depends on ten project types that are themselves Android-bound.

### "syncing state to an account means a server, accounts, and somebody's data in someone else's hands — which is a different kind of project"

**Right, and it is the half nobody has thought about.** §8 is about what would actually sync, and
the answer is smaller and stranger than it looks.

---

## 2. Three products are hiding inside one wish

B5 is one sentence describing three things that share almost nothing but a name. Separating them is
most of the value of writing this down.

| | what it is | needs a server? | needs an account? |
| --- | --- | --- | --- |
| **W1** | a page you drop a file onto, and it plays | no | no |
| **W2** | W1 plus the catalogues — Modland, ASMA, HVSC, The Mod Archive — browsable and playable | **for one endpoint only** (§6) | no |
| **W3** | W2 plus favourites and history shared with the phone | yes | yes |

**They are also the right order**, and each one is worth having if the next never happens. W1 is a
weekend and answers the only question that can kill the whole idea — *do our decoders run in a
browser at an acceptable cost*. W2 is where it becomes the thing that was asked for. W3 is a
different kind of project with running costs, a privacy policy and somebody else's data in it, and
it should not be started to find out whether W1 works.

**Recommendation:** if any of this happens, W1 first, as a throwaway. It is the cheapest possible
answer to the expensive question, and the code is meant to be discarded.

---

## 3. What would carry over — counted, not estimated

*Measured* on 2026-09-08 against `develop` at `b9924af`. Method: a file counts as Android-bound if
it has a top-level `import android.` or `import androidx.`.

| | lines | share |
| --- | --- | --- |
| all Kotlin in `app/src/main` | 13,656 | |
| free of Android at file level | 2,472 | 18% |
| Compose UI (`ui/`, 23 files) | 5,357 | 39% |

**But a file-level import count answers a different question than the one that matters**, and the
first version of this section got that wrong in both directions. It was corrected on 2026-09-08 by a
review from a second session, whose measurements are folded in below and were re-run here.

| `player/PlaybackController.kt` | |
| --- | --- |
| lines | **2,963** |
| `android`/`androidx` imports | 6 |
| occurrences of the injected `context` | **41** |
| project types imported | 26 |
| …of those, defined in Android-bound files | **10** |

The first draft said "eleven Android expressions" and that number was measuring the wrong thing: it
counted uses of the six imported *types* and missed `context` itself, which is a constructor
parameter and appears 41 times. The six imports are also not the coupling that matters. The
coupling that matters is the ten project types — `CatalogueStore`, `HistoryStore`,
`LibraryIndexStore`, `LibraryStore`, `SongLengthStore`, `TrackMetadataStore`, `NativeData`,
`ModArchive`, `RemoteFiles`, `Sc68Replays` — every one of which is itself Android-bound.

**Portability is a property of the graph, not of a file.** "Domain wearing a phone's coat" is still
the right shape, but the coat has ten sleeves, and the work is extracting interfaces for ten
collaborators, not recompiling one file.

### Two false positives, both load-bearing

A file with no Android import is not thereby portable:

- **`engine/NativeEngine.kt`** — 179 lines, 15 `external fun`, no Android import, so the method
  counts it as carrying over. It is pure JNI: the one file in the project **guaranteed** to be
  rewritten.
- **`net/Catalogue.kt` (218) and `data/Md5.kt` (19)** are Android-free and `java.*`-bound —
  `java.util.zip.ZipInputStream`, `java.net.URLEncoder`/`URLDecoder`, `java.security.MessageDigest`.
  Under route A (§10) Kotlin/Wasm has no `java.*`, so these are rewrites. They are also, awkwardly,
  the catalogue parsing this section counts as carrying over and the MD5 identity §8 leans on.
  `Catalogue.kt` uses them fully qualified, with no `import` line, so a grep for imports misses them
  entirely.

**In the method's favour**, the same review checked all 66 files for fully-qualified `android.` use
without a top-level import: one hit, and it is a string literal. **False negatives are essentially
zero; the false positives are what bite.**

So the honest split, now that the false positives are taken out:

| | roughly | what it is |
| --- | --- | --- |
| **carries over** | ~5,000 lines | the domain, the queue, the navigation, the schema, the playback logic |
| **rewritten against browser APIs** | ~3,300 lines | the ten stores and fetchers, the JNI facade, the zip and MD5 helpers, the service, audio focus |
| **rewritten entirely** | ~5,400 lines | the Compose UI |

Still *reasoned* rather than measured — nobody has compiled any of it off Android. But the counts
underneath it are real, and they still say the thing worth saying: **`ARCHITECTURE` §7 worked.** The
rule "extract when the decision has been wrong before" was written for testability and it bought
most of a port for free. What it did not buy is the ten interfaces, and that is the actual estimate.

**The 21 test files come with the domain**, which is the part that makes a port checkable at all in
an environment with no emulator and now no phone either.

---

## 4. The decoders in a browser

*Read.* Nothing below was built here. Every entry is somebody else's Emscripten build, found by
search, and the version column is ours.

| backend | our version | licence | an existing web build | notes |
| --- | --- | --- | --- | --- |
| libopenmpt | 0.8.9 | BSD-3 | **upstream, first-party** | ships `libopenmpt.js`; the easy one, and 98% of what Modland hands us |
| ASAP | 8.0.0 | GPL-2.0+ | **first-party web page** | worth checking whether its source language transpiles to JS directly — if so there is no Emscripten step at all |
| sc68 | 3.0.0b | GPL-3.0+ | third-party (`webSC68`), **same version** | B5 said this did not exist; it does |
| libsidplayfp | 3.1.1 | GPL-2.0+ | third-party (`libsidplayfp-wasm`), and inside `chip-player-js` | reSIDfp is the expensive one per second of audio |
| game-music-emu | 0.6.5 | LGPL-2.1+ | inside `chip-player-js` | seven console families in one library |
| HivelyTracker | V1_9 | BSD-3 | **none found** | three C files, the smallest vendored decoder here; if the toolchain works at all this one works |
| ZXTune | c93e81d | LGPL-3.0 | **built and playing, 2026-09-15** | the largest C++ of the seven; eight patched lines, applied by the fetch script rather than committed — see §14 |

**The finding is that six of seven have a precedent and one does not.** ZXTune is also the newest
arrival — merged 2026-09-07 — and the only one where a web version would plausibly ship with a
format missing. That is survivable: it is 3,639 Modland files against 178,795 for libopenmpt.

### The size — answered, and the fear was unfounded

The first draft said this was the thing to measure before anything else. It has been measured
(*measured*, 2026-09-08, raised by the reviewing session and re-run here).

| | raw | over the wire |
| --- | --- | --- |
| `chiptune3@0.8.9` → `libopenmpt.worklet.js` — **libopenmpt 0.8.9, the version we ship** | 1,539,353 B (1.54 MB) | **528,040 B (516 KB), brotli** |
| our `libprotracktor_engine.so`, arm64, stripped — **all seven backends** | 3.87 MB | 3.87 MB in the APK |
| the whole APK, three ABIs | | 18 MB |

One backend of seven costs half a megabyte compressed; our seven together cost 3.87 MB as native
arm64. **A full wasm set is plausibly 1.5–2 MB compressed** — a normal web page, not an obstacle.
The draft's "a 30 MB download is not a web player" was a fear with nothing under it, and it is
recorded here rather than deleted because it was the stated reason to do this measurement first.

The measurement's caveats: it is one backend, not seven; wasm and arm64 do not compress alike; and
`chiptune3` may build libopenmpt with fewer formats enabled than we do. It bounds the answer well
enough to stop worrying, not well enough to quote as a total.

### And it decodes — measured the same day, in node, with no browser

*Measured.* The size answer says nothing about cost, so the same build was **run**. Loaded in plain
node with three globals stubbed (`sampleRate`, `currentFrame`, `currentTime`) and handed a real
Modland ProTracker module:

| | |
| --- | --- |
| opened | yes; title `ennio morricone`, duration 59.7 s, both read through the C API |
| decoded | 30 s of audio, peak amplitude 0.607 — **sound, not silence** |
| cost | 58 ms wall, **518× realtime** |

Even at a fiftieth of this machine's speed there is no dropout in that. CPU is not the risk for the
backend that covers 98% of Modland; reSIDfp and ZXTune are the expensive ones and neither is
measured yet.

**The more important finding is about method.** `AGENTS.md` §7's rule — build for the host, run real
files through it, count something — is how every backend in this project was decided, and it
**survives the move to the web**: a wasm decoder can be probed in node exactly as a native one is
probed on the host. What still needs a browser is glitching in a real audio thread, the page itself,
and whether a network permits any of it. That is a much smaller list than "everything", and it is
the difference between porting this project and starting a different one.

**Shape confirmed while measuring:** `chiptune3.worklet.js` is 13 KB and is the
`AudioWorkletProcessor`; it imports the 1.54 MB module and decodes inside `process()`. §5 reasoned
its way to that arrangement from `ARCHITECTURE` §4. Somebody's shipping build agreeing is worth more
than the argument.

**A separate question, and one this project has been careful about before:** sc68's replay binaries
(`docs/LICENSES.md`) and the SNDH/`.sc68` split apply unchanged in a browser. The app ships one of 99
and fetches the rest on request. In a browser the "fetch the rest" path lands in IndexedDB rather
than on disk, which is a storage question rather than a licensing one — but the licensing question
underneath it is identical and still unanswered.

---

## 5. The audio path — where `ARCHITECTURE` §4's argument comes back word for word

§4 says: *rendering and output both live in native code; JNI carries control and metadata only*,
because pushing PCM across the boundary *"crosses the JNI boundary every few milliseconds on the one
thread that must never be late"*.

**The browser poses exactly the same question and answers it with the same shape** (*reasoned*):

| Android | browser |
| --- | --- |
| Oboe callback on a real-time thread | `AudioWorkletProcessor.process()` on the audio thread |
| decoders in the same native library as the callback | decoder wasm instantiated **inside the worklet** |
| JNI carries control only | `postMessage` carries control only |

**And this is not only an argument — somebody already ships it.** The `chiptune3` build measured in
§4 is named `libopenmpt.worklet.js`: the decoder is instantiated **inside the AudioWorklet**, which
is the exact shape this section reasons its way to. That is independent corroboration rather than a
second opinion, and it is worth more than the argument that produced it.

`engine.cpp` is **1,868 lines, of which about 64 mention Oboe, JNI or `android/log`** (*measured*).
The backend registry, the probing, the subsong handling and the mixing are portable C++. That is the
single most encouraging number in this document: the native side was written to a boundary and the
boundary is in the right place.

**The catch, and it is a real one.** Instantiating wasm inside an AudioWorklet and sharing buffers
with it wants `SharedArrayBuffer`, which requires the page to be cross-origin isolated — `COOP` and
`COEP` headers on every response. That is a hosting requirement, not a code change, and it interacts
badly with embedding third-party anything. Without it the fallback is a ring buffer filled from the
main thread, **which is precisely the design §4 rejected on Android** — and it would be rejected for
the same reason and produce the same class of glitch.

So: **cross-origin isolation is not a detail, it is a precondition.** If the hosting cannot provide
those headers, W1 is a toy and W2 should not be attempted.

> **Corrected 2026-09-08: this was too strong, and the premise under it was never checked.**
> *Measured*: the published libopenmpt worklet build contains **zero** references to
> `SharedArrayBuffer`, `pthread`, `Atomics`, `Worker` or `crossOriginIsolated`, in either file. The
> decoder is instantiated single-threaded *inside* the worklet and writes straight into
> `process()`'s output buffers — there is no memory to share, so there is nothing to isolate for.
> `COOP`/`COEP` become necessary only if decoding moves to a worker and its buffers are shared with
> the audio thread, which is a design choice rather than a requirement.
>
> What survives: `AudioWorklet` needs a **secure context**. `http://localhost` is one, so
> development needs no certificate and no headers at all; reaching the page from another machine
> needs HTTPS, which is a tunnel or a host — not a header, and not a precondition on the hosting's
> configurability.
>
> The reasoning above was sound and the conclusion was wrong, which is the more useful kind of
> mistake to leave standing.

---

## 6. Where the music comes from — and the surprise

This was expected to be the wall. **It is not** (*measured* — one range `GET` per host with an
`Origin:` header, from this machine, 2026-09-08).

| host | what we use it for | `Access-Control-Allow-Origin` |
| --- | --- | --- |
| `modland.com` | the 40 MB index **and every module file** | `*` |
| `asma.atari.org` | the 20 MB ASMA archive | `*` |
| `www.hvsc.c64.org` | 5.2 MB of song lengths | `*` |
| `api.modarchive.org` | per-module download | `*` — but **no range support**, see below |
| `raw.githubusercontent.com` | the songdb metadata table (14.8 MB) | `*` |
| **`modarchive.org/index.php`** | **search** | **absent** |
| **`svn.code.sf.net`** | **sc68 replay binaries** | **absent** |

Five of seven are open to a browser directly, including both large archives and every music file.
**Two need a proxy, and they are the two smallest jobs**: a search that returns a page we already
know how to parse, and a directory of 99 small binaries that could equally be mirrored once into the
repository or into object storage and never proxied at all.

**Why this matters more than it looks.** The Mod Archive integration is a *scraper*
(`docs/PLAN_CATALOGUES.md`) — it reads `index.php`'s HTML because there were no API keys. A scraper
cannot run in a browser against a host that does not opt in. So W2's server is not "infrastructure
for a web player"; it is one function that fetches a URL and returns the body, and the honest way to
describe its cost is that it is small but it is not zero, and it never goes away.

### The preflight, and one correction — both 2026-09-08

The first draft flagged that it had tested only a simple range `GET`, and that a request carrying
custom headers preflights separately. The reviewing session closed that gap; re-run here.

`OPTIONS` to `modland.com` with `Access-Control-Request-Headers: range` answers **204**, with:

```
Access-Control-Allow-Origin:    *
Access-Control-Allow-Headers:   DNT,User-Agent,X-Requested-With,If-Modified-Since,
                                Cache-Control,Content-Type,Range
Access-Control-Expose-Headers:  Content-Length,Content-Range
Access-Control-Max-Age:         1728000
```

**That is a stronger result than the table above claims.** `Range` is allowed *and* `Content-Range`
is exposed, so the **ranged** read the app's prepare-ahead depends on (`ARCHITECTURE` §6) works from
a browser — not merely a whole-file `GET`.

**One correction to the table.** `api.modarchive.org` does **not** support ranges: a range request
answers `200` with the whole 757,586-byte module, not `206`. The first draft's row was measured
against `downloads.php` with no `moduleid`, which returns a 16-byte error body — and a 16-byte body
answers a range request happily. The `Access-Control-Allow-Origin: *` is real on the genuine
download and the row stands; the implied range support did not exist. **A one-line error page will
answer almost any question you ask it, which is a good reason to measure against a real response.**

**The caveat that remains.** A header measured on one day is not a promise: any of these hosts can
drop it without telling anyone, and then a working web player breaks with nothing on our side having
changed. That is a risk the Android app does not carry at all.

---

## 7. The local library mostly does not cross

`ARCHITECTURE` §18 — *a folder is scanned by opening every file with a real decoder, not by reading
its name* — is one of this project's better decisions and it is the one that travels worst.

*Read*, 2026-09-08: `showDirectoryPicker()` is Chromium-only. **Firefox and Safari implement only
the Origin Private File System**, a sandbox with no view of the user's disk, on desktop and on iOS
alike.

| | Chrome / Edge | Firefox | Safari |
| --- | --- | --- | --- |
| pick a folder and remember it | yes | **no** | **no** |
| open individual files a user chose | yes | yes | yes |
| a private sandbox we can fill ourselves | yes | yes | yes |

So a web version has a **local library** on one browser family and a **file you dropped on the page**
everywhere else. That is not a defect to fix; it is a scope decision to take deliberately, and it
argues for the catalogues (W2) being the point of the web version rather than an afterthought to it.
On the phone the library is the product and the catalogues are the extra. In a browser it is the
other way round.

---

## 8. What can actually sync — the part worth thinking hardest about

B5 asks for favourites and history shared between the phone and the browser. **Not all of it can be
shared, and the reason is structural rather than technical.**

> **Corrected 2026-09-08, and the correction narrows this section rather than adding to it.**
> Everything below is a property of **asynchronous sync** — where the browser reads a row while the
> phone is absent — and it was written here as a property of *local files*. It is not. Under a live
> pairing (`docs/PLAN_HANDOFF.md`) the phone is present at the moment of transfer and can simply
> send those rows as bytes; Modland's median module is 20 KB. So "a playlist that mixes both kinds
> cannot arrive intact" is true of an account and false of a pairing, which makes the accountless
> design the *more* capable one here. Read this section as being about W3.

`ARCHITECTURE` §8 says a playlist refers to index rows, and those rows come from two kinds of source:
a local folder and a remote catalogue. §11 says **a remote track's identity is its URL**. A local
track's identity is a document URI granted by the storage access framework on one phone.

| what | can it sync | why |
| --- | --- | --- |
| **favourites of catalogue tracks** | **yes** | a URL means the same thing on both machines |
| **history of catalogue tracks** | **yes**, and easily — it is append-only with timestamps, so two devices merge by concatenating | |
| **settings** (theme, language, mix) | yes, and trivially | |
| **favourites and history of local files** | **no** | the browser cannot open the phone's storage, and the identity is a grant to one app on one device |
| **a playlist** | **partly, and that is the trap** | a playlist may mix both kinds; the synced copy would silently lose rows |

**The playlist finding is the important one.** A playlist that mixes a local file with a Modland
track cannot arrive intact in a browser, and the failure mode is the worst kind: it looks like it
worked and the list is shorter. `AGENTS.md` §7 — *a press that does nothing and says nothing is a
defect* — has an obvious extension here, which is that **a sync that drops rows silently is worse
than one that refuses**.

Three ways out, in increasing order of honesty and cost:

1. **Sync only what has a URL.** The web sees a filtered playlist and says so, per list, in words.
2. **Sync the whole playlist and mark the unreachable rows** — present, greyed, labelled *"on your
   phone"*. Nothing is lost, nothing is pretended.
3. **Give local files a portable identity** — content hash rather than URI. `data/Md5.kt` already
   exists, the scan already opens every file, and the songdb table is *already keyed by MD5*
   (2026-09-07). A local file the browser also has — because the user pointed both at the same music
   — would then match.

**Recommendation: (2), with (3) as the thing that makes it good later.** (2) is a rendering rule and
costs almost nothing; (3) is genuinely interesting and would also give the phone a way to recognise
the same tune arriving from two sources, which is a problem this project already has and has not
solved. But (3) is not a sync feature and should not be justified as one.

**And a fourth option that deserves saying out loud: no accounts at all.** Favourites and history
could travel as an exported file — we already write and read M3U with our own identifiers in
comments (**A25**). It syncs nothing automatically and it costs no server, no privacy policy, no
password reset and nobody's data in anybody's hands. It is not what B5 asked for. It is a tenth of
the work, and it is available in W1.

---

## 9. Licensing — no new problem, one new obligation

*Reasoned*, from `docs/LICENSES.md`.

The combined decoder set is already GPL-3.0-or-later and that does not change by being compiled to
wasm. What changes is **what counts as distribution**: serving a wasm bundle to a browser is
conveying it, so the page must carry the same written offer of source the APK does. That is an
obligation on the *front end*, and it means the entire JavaScript or Kotlin front end linked into
that bundle is GPL-3.0-or-later too.

**A server that never links to the decoders is separate work** and is not forced open by GPL-3. If
W3 ever happens, whoever writes it should decide deliberately whether they *want* it open — AGPL
would be a choice, not a consequence.

**One thing to check rather than assume**: LGPL-2.1 (game-music-emu) and LGPL-3.0 (ZXTune) grant the
user the right to relink. In a statically linked wasm blob that right is satisfied the same way it
is in our APK today, which is by shipping the sources and the build. Whatever answer `LICENSES.md`
settled on for the APK carries over unchanged; it should be restated there rather than re-derived.

---

## 10. Three routes, and which one to recommend

| | what it is | the case for | the case against |
| --- | --- | --- | --- |
| **A. Kotlin Multiplatform** | share the domain, compile it to Kotlin/Wasm, rewrite the stores, drive the decoder wasm through JS glue | the ~5,000 lines of §3 and their 21 test files are literally reused; one bug fixed once | Kotlin/Wasm's web target is **Beta** (*read*, 2026); Kotlin/Wasm and Emscripten are two separate wasm modules that must talk through JavaScript, and the audio worklet is on the far side of that glue — the exact boundary §5 says must not have glue in it |
| **B. A separate web app** | TypeScript, decoders via Emscripten, domain reimplemented | no toolchain risk; the audio worklet owns the decoder directly; a small W1 is genuinely a weekend | the domain is written twice and drifts, which `AGENTS.md` §8 names as the thing that rots quietly — a rule changed on the phone and not in the browser will not be noticed by anyone until a user sees it |
| **C. Neither yet — do the server first** | build **B19** (hosted catalogue indexes) and let the phone use it; the browser client comes later against an interface that already exists | it is useful to the phone **today** — a 40 MB index download is the app's largest single cost, and B19 exists as a wish for that reason alone; it also settles §6's two proxy endpoints as a side effect | it is not a web player, and if what is wanted is a web player this does not produce one |

**Recommendation: W1 as a throwaway on route B, then decide.**

The reasoning: the question that determines everything is *what does the decoder set cost in a
browser*, and that question is answered identically under A and B. **Half of it is now answered
without building anything** — §4 bounds the weight at plausibly 1.5–2 MB compressed. What is left is
the half a byte count cannot reach: whether seven backends decode inside an AudioWorklet without
dropouts on a mid-range phone's browser, and what cross-origin isolation costs to arrange. W1 exists
to answer that, and it is a smaller W1 than it was when the size was still unknown.

B answers it in the fewest moving parts, and route A's real risk — Kotlin/Wasm talking to Emscripten
across JS at the audio boundary — is a thing to measure before committing 5,000 lines of shared code
to it, not after. §3's ten Android-bound collaborators sharpen that: route A's advantage is only
real once those ten have interfaces, and extracting them is work the phone gets nothing from.

Route C is the one to take **if the honest answer is that this is not going to happen soon**, because
it is the only one of the three that pays for itself without a web version existing.

---

## 11. What this changes about decisions being made today

B5's claim that it *"would change decisions we are making today"* is the reason this document was
written. Concretely, and independent of whether any web version is ever built:

1. **Keep pulling logic out of `PlaybackController`.** §3 shows why: the file is 2,963 lines with
   only six Android imports, which means almost all of it is domain wearing a phone's coat. §3's
   correction sharpens rather than weakens this: the coat is ten Android-bound collaborators, and
   every one given an interface is one the port does not have to fight.
   `ARCHITECTURE` §7's rule already says to extract *when a decision has been wrong before*; this is
   a second, weaker reason that costs nothing to honour.
2. **Keep the JNI surface narrow.** It is fifteen functions and about 64 of `engine.cpp`'s 1,868
   lines. Every control that goes through it instead of around it is one that a wasm build inherits
   for free.
3. **Do not add a catalogue that only works by scraping**, without noticing that it has just become
   the only one needing a server. §6 makes The Mod Archive the sole exception today; a second one
   should be a decision rather than a discovery.
4. **The content-hash identity (§8 option 3) is worth wanting for its own sake.** The songdb table
   is already keyed by MD5 and `Md5.kt` already exists. If a reason to key the index by content ever
   comes up on the phone, this is a second reason.
5. **`docs/PRIVACY.md` currently says the app keeps everything local**, and that is true and worth
   keeping true. W3 would make it false. That is not an argument against W3 — it is an argument for
   W3 being a separate, named decision rather than something that arrives with a feature.

---

## 12. What has to be decided before any of this is work

Not resolved here, deliberately — `AGENTS.md` §6 says to write the question with its options rather
than invent an answer.

- **Is this W1, W2 or W3?** They are three projects (§2). Answering this is most of the planning.
- **Is a web version a *product* or a *demo*?** A page that plays a file you drop on it, linked from
  the README, costs a weekend and is a good advertisement. A second product to keep in step with the
  phone forever is a different commitment and the one the merge rules would have to cover.
- **Route A or B (§10)** — and this should be decided *after* W1 has produced a byte count.
- **If W3: whose account system?** B5 said Google or Cloudflare. B19 already assumes Cloudflare for
  hosted indexes, which is an argument for one answer rather than two.
- **Is a proxied scrape still a user's own client?** Raised by the reviewing session on 2026-09-08,
  and it is the sharpest question here. §6 leaves the proxy carrying exactly one thing — the Mod
  Archive search — but that is the case where every browser's request arrives at their server **from
  our address rather than the user's**. The same shape was reasoned about for UnExoticA and
  concluded that a user's own client is not a crawler; that argument is sound and it **stops working
  the moment we are in the middle**. It should be a stated question with an answer, not something
  discovered after the traffic exists.

---

## 13. The implementation plan

Written 2026-09-08 after what was asked for was one. Seven stages, each with a **deliverable**, a
**way it is checked**, **what it needs from a device**, and **what would make us stop**. The order is not
decoration: every stage is useful if the next never happens, and the two that could kill the idea
come before the two that cost the most.

Stated once so it is not rediscovered as a surprise: **no accounts anywhere in this plan**, ZXTune
is out of the first set, Compose Multiplatform is not used (`PLAN_HANDOFF.md` §5a), and `assetlinks.json`
waits for a domain that does not exist yet.

### S0 — Split the engine. Android only, no web code at all

`engine.cpp` is 1,868 lines of which the Android-bound part is one class and one block: the Oboe
`Player` at 1497 and the `extern "C"` JNI functions from 1737. They move to `player_oboe.cpp`; the
shared file keeps the backends, the registry and the dispatch.

- **Deliverable:** identical APK behaviour, one file fewer concern.
- **Checked by:** the 184 unit tests, a release build, and **a real device** — this is the
  heart of playback and nothing else in this plan touches the phone.
- **Needs a device:** one test round.
- **Stop if:** the split cannot be made without changing behaviour. Then the web port is a fork of
  the engine rather than a second target, and that is a different and worse project.

### S1 — Emscripten, and one decoder end to end

`scripts/fetch-emsdk.sh` and `scripts/build-web-engine.sh` producing `web/vendor/engine.js` + `.wasm`
from **our** `engine.cpp` with libopenmpt only, plus `player_wasm.cpp` — the worklet-side counterpart
of the Oboe class.

- **Deliverable:** our own engine, in wasm, with a byte count.
- **Checked by:** `scripts/probe-web.mjs` in node — the host-probe rule (`AGENTS.md` §7) applied to
  wasm. Real Modland files in, per-file open/decode/peak/realtime out. Proven possible today against
  somebody else's build: 518× realtime, peak 0.607.
- **Needs a device:** nothing.
- **Stop if:** our `engine.cpp` needs forking to compile under `emcc`. Report and stop; do not fork.

### S2 — The rest of the backends, one at a time, each with a number

game-music-emu, libsidplayfp, ASAP, HivelyTracker, sc68. Each added, probed in node against real
files from the archives, and recorded the way `PLAN_FORMATS.md` records a native backend.

- **Deliverable:** a coverage table and a total byte count.
- **Checked by:** the same probe. reSIDfp and sc68 are the ones whose realtime factor is worth
  watching; libopenmpt's 518× says nothing about them.
- **Needs a device:** nothing.
- **Stop if:** the total goes past a few megabytes compressed, or a backend cannot reach realtime with
  margin. Either is a reason to ship without it, not to abandon the stage.
- **ZXTune is not in this list.** No precedent exists, it is the largest C++ here and it fought the
  native build. It is 3,639 Modland files of 516,107; the web version can say so.

### S3 — The page, on localhost

A static page: title, transport, position, queue. An `AudioWorkletProcessor` importing the engine.
Plays a list of Modland URLs it is given. No browsing, no search, no settings — `PLAN_HANDOFF.md`
§5a says why.

- **Deliverable:** `web/src`, `scripts/serve-web.sh`, and a URL to open.
- **Checked by:** **a person, on a real browser.** Does it play, does it glitch, does seeking work. `http://localhost`
  is a secure context, so no certificate and no headers are needed.
- **Needs a device:** the first real test round of the whole idea.
- **Stop if:** it glitches and the cause is the worklet rather than a bug. That is the one failure
  no measurement here can predict.

### S4 — H1, the link. The first time the wish actually works

Phone: "send this queue to the browser" builds a URL with the queue compressed into the fragment and
hands it to the share sheet. Page: reads the fragment, builds the queue, plays.

- **Deliverable:** a playlist from the phone, playing on a desk.
- **Checked by:** a person, end to end, from phone to browser.
- **Needs a decision:** one round, and whether copy-paste per handoff is tolerable.
- **Measured already:** 50 tracks compress to 1,992 characters — inside the limit that is safe
  everywhere. No server is involved in the handoff at all.

### S5 — H2, the relay and the typed code

Only if S4's copy-paste grates. `web/relay/`: rooms, one-directional, holding nothing. The browser
shows an eight-digit code, the phone takes it. Requires HTTPS from outside, so it arrives with the
hosting conversation rather than before it.

- **Stop if:** the network at the other end blocks the relay. H1 survives that; H2 does not, which is the third
  reason H1 comes first.

### S6 — Everything else, deliberately not planned

Browsing catalogues in the browser, the local library through the File System Access API, favourites
and history syncing, WebRTC. Each is a decision (`PLAN_WEB.md` §12), not a next step.

### What only a person can answer

Four things, and nothing else: **the S0 device test**, **whether S3 glitches**, **whether S4 works
end to end**, and eventually **whether the network at the other end permits any of it** — the last being the one
question that can invalidate the whole thing and the one nobody else can answer.

Everything before S3 needs no browser, no host, no network and no account, because the host-probe
rule survives the move to the web (§4).

## 14. S1, done 2026-09-08: our engine runs in a browser's runtime

*Measured*, on this machine, from **our** sources — not somebody else's build.

### It links, and the stop condition never came near

§13 S1 said to stop if `engine.cpp` needed forking to compile under Emscripten. It did not need a
line. Six of the seven backends link, and `scripts/probe-web.mjs` decodes real Modland files through
them in node.

| | |
| --- | --- |
| `engine.wasm` | 2.60 MB raw, **0.74 MB brotli**, 0.97 MB gzip |
| `engine.mjs` | 0.12 MB raw, 0.02 MB brotli |
| **over the wire** | **0.76 MB** |
| for comparison | our arm64 `.so`, all seven backends: 3.87 MB |

**0.76 MB for six decoders.** §4's estimate of 1.5–2 MB was pessimistic by half, and the "30 MB is
not a web player" fear is now twice-buried.

### What it plays, first run, 16 real files from Modland

```
12 played, 2 silent, 2 refused
```

| backend | result |
| --- | --- |
| libopenmpt | `.mod` 489–639×, `.it` 791–1287× realtime |
| HivelyTracker | `.ahx` 1280–1599×, `.hvl` 495–556× |
| sc68 | `.sndh` 201–221×, subsongs counted |
| libsidplayfp | `.sid` **27–38×** — the slowest by an order of magnitude, as expected: reSIDfp |
| game-music-emu | `.kss` opened and **silent** |
| ASAP | not in this set |

**27× realtime for a SID is the number to keep.** It is comfortable and it is 50 times closer to the
edge than libopenmpt, so it is the one that decides whether a slow laptop under a worklet is fine.

### Two things to check on the phone before believing either

Both are behaviour differences, not build failures, and **neither is claimed as a web problem until
the same file is tried on Android**:

1. **`.ym` is refused**, and the message says libopenmpt refused it — meaning sc68 did not claim it,
   though `SupportedFormats` lists `ym` for sc68.
2. **`.kss` opens and renders silence.** `GmeBackend` deliberately opens at the first track with
   sound in it, so either that logic is not doing its job here or these two files are silent on the
   phone too.

The probe caught both on its first run, which is the argument for having written it before writing
the page.

### S2, the same day: 184 files, 20 formats

*Measured.* Sampled at random from Modland's index, sizes between 2 KB and 400 KB, run through the
wasm engine in node.

```
166 played, 8 silent, 10 refused, of 184
```

**And both failure groups have a known cause that is not the web build.**

| | |
| --- | --- |
| **8 silent** | every one a `.sc68`, and this is the documented behaviour: those files need the replay routines the app deliberately does not ship and fetches on request (`docs/LICENSES.md`). Nothing fetched them here. **`.sndh` played 15 of 15**, because SNDH carries its own code — which is the SNDH/`.sc68` split, confirmed rather than assumed |
| **10 refused** | every one a `.med`, all with `MED\x04` at offset zero. libopenmpt's loader requires `MMD` (`Load_med.cpp:865`): these are the older Amiga *Music Editor* format, which it does not load at all. Not a wasm difference — the same files are refused on the phone |

So of the files a backend here can actually play, **166 of 166 played**, across six decoders.

| format | files | slowest |
| --- | --- | --- |
| `.sid` | 20 | **23× realtime** |
| `.spc` | 8 | 86× |
| `.sndh` | 15 | 94× |
| `.sap` | 8 | 140× |
| `.it` | 10 | 178× |
| `.xm` / `.mod` / `.s3m` | 50 | 286× / 323× / 449× |
| `.ahx` / `.hvl` | 21 | 1533× / 594× |
| `.nsf` / `.gbs` / `.kss` | 20 | 1105× / 921× / 517× |

**23× realtime for a SID is the floor of the whole set**, and it is the number that decides whether
a slow laptop is safe. Everything else has at least 86×.

### Three obstacles, and each lied about its cause

- **`zlib.h` not found** in exactly one of libopenmpt's 353 files. Emscripten has zlib as a *port*,
  and the flag has to be on the compile line, not only the link line — otherwise one file fails and
  it reads as a broken source rather than a missing dependency.
- **`FileNotFoundError: cache.lock`** from inside the compiler. Emscripten builds a port on first
  use and 353 parallel compilations all ask at once. `embuilder build zlib` first, serially.
- **`undefined symbol: gme_play`** at the link. `native/backends/gme` adds the vendored tree with
  `EXCLUDE_FROM_ALL`; on Android the engine target pulls it in by linking `gme::gme`, and here there
  was no engine target to pull it. Nothing had failed — nobody had asked for it.

### S3, and the defect a measurement could not have found

The page played on a real machine at the first attempt that got past the worklet's missing
globals, and the answer to the question S3 exists for — *does it glitch* — was no, including the
SID. Then something that mattered more:

> It sounds slightly faster than it should — of `Crazy_Comets.sid`, a tune the listener knows well.

That was right, and the cause is exact. **Six of the seven backends emulate a machine with a fixed
clock and produce 44,100 samples a second whatever rate they are asked for.** A browser's default
`AudioContext` runs at 48,000, and there is no resampler between an `AudioWorkletProcessor` and the
output — so those samples played **8.84% fast**, about a semitone and a half sharp.

**Android never had this**, because `player_oboe.cpp` asks `preferredSampleRate()` and hands it to
Oboe, which resamples. The page was written as though that path did not exist. The context is opened
at 44,100 now, which is the same answer by the only route a page has.

**No measurement here would have caught it.** The probe reports how *fast* a decoder runs, never at
what pitch, and it was asking for 48,000 itself — so its own SID figures were 9% out and it had no
way to notice. It asks each file for its preferred rate now (SID: 29× realtime, not 23×), and the
page compares the two and says so in words if they ever differ again, rather than transposing the
music silently.

**The general lesson is worth more than the fix.** Every check in this project is a number the
machine can produce: does it open, is it above silence, how many times realtime. *Pitch* is not in
that set, and an ear that knew the tune found in one listen what a hundred and eighty files could
not.

### S4, done the same evening: the queue crosses

The phone packs the playlist into a URL fragment, appends the page's address and hands it to the
share sheet. **No server is involved in the handoff at all**, and a fragment never reaches one, so
the page's own host does not learn what is on the list.

Three defects came out of the first two attempts, and each is worth keeping:

1. **A link opened in a tab that already had the page did nothing.** Only the fragment changed, so
   the browser fired `hashchange` rather than reloading, and the page read the fragment once at
   start-up. It appeared to work only after a manual reload.
2. **Every Mod Archive row was called `downloads.php`.** That archive addresses a file as
   `downloads.php?moduleid=123#tune.mod` — the path is a script, the query is a number, and the
   filename is only in the fragment. **Not cosmetic:** four backends identify a format by its
   extension and were being handed a name with none.
3. **And the phone's title did not travel.** The browser showed `lotus3_4.mod` where the phone said
   `L3_CD4-SpaceNinja`, because only the address was sent. The link carries `address<tab>title` now,
   and only when the title adds something — a Modland row whose title is its filename sends nothing
   extra, which is most of a real queue, so the 2,000-character measurement still holds.

**All three were found by using it, none by the tests.** The suite proves the packing round-trips;
it cannot know that a queue is something a person reads before pressing anything.

### ZXTune is in — 2026-09-15, and it was eight lines

*Written here as "out" from 2026-09-08 until 2026-09-15. What follows replaces that, and the old
reasoning is kept in the first paragraph because it is the part that was wrong.*

**What this section used to say**, and what everything downstream repeated for a week: ZXTune does
not build under Emscripten, because `lexic_analysis.cpp` initialises a `const auto*` from a
`std::string_view::const_iterator` — a raw pointer in the NDK's libc++ and not in Emscripten's —
and it was the *first* file, so probably not the last. Patching it would mean forking a library
`ARCHITECTURE` §3 says we do not fork.

**The diagnosis was right and the conclusion was not, and nobody had run the build.** Doing it
produced **eight lines in five files**, all the same idiom, and then it linked and played. The
"probably not the last" was correct — it was six more — and "probably too many to patch" was the
part that was never measured.

| | |
| --- | --- |
| lines patched | **8**, in 5 files |
| what each one does | lets an iterator keep its own type, or asks the view for a pointer it needed anyway |
| behaviour changed | **none** — no logic is touched and the Android build compiles the same sources |
| upstream | has the same code today (checked 2026-09-15), so there is no newer revision to take instead |
| engine size | **2.65 MB → 3.21 MB** over the wire, +21% |

Emscripten's libc++ runs at ABI version 2, where `_LIBCPP_ABI_USE_WRAP_ITER_IN_STD_STRING_VIEW`
makes that iterator a `__wrap_iter` **on purpose**, to stop code relying on the implementation
detail. Switching the ABI version back was considered and rejected: it would apply to one target in
a link whose other objects — and the prebuilt sysroot — are built at version 2, which is an ABI
mismatch rather than a fix.

**The patch lives in `scripts/fetch-zxtune.py`, not in the tree.** A fetch deletes and re-clones the
whole directory, so an edit made by hand would vanish the next time anybody ran that script and the
symptom would be a build that worked yesterday. Each entry carries the number of times it expects to
match, and a mismatch is a loud failure — which caught a real mistake while this was being written:
one of the seven patterns occurs **twice** in `encoding.cpp`, and a blind replace had quietly done
both.

**What it buys: 26,537 Modland tunes** that played on the phone and not in the browser — `pt3`
7,376, `pt2` 6,284, `ym` 4,977, `stc` 3,639 and the rest of the Spectrum's formats. Verified by
playing one of each family through the built engine: `.pt3`, `.asc` and `.stp` open and render
audible audio at ~380× realtime.

**Nothing in the page changed**, which is the part worth noticing. `absentDecoders()` reads what the
engine says rather than a list somebody maintains, so the decoder appearing in the fingerprint is
the whole of the integration. `scripts/check-engine.mjs` now asserts the fingerprint reports nothing
missing, so this cannot quietly go back.

## Sources for the *read* claims

Listed so the next person can re-check them rather than trust this file, per `AGENTS.md` §7.

- webSC68 — `github.com/wothke/sc68-2.2.1` (Emscripten port; the hosted player states sc68 3.0.0b)
- `libsidplayfp-wasm` — `github.com/chrisgleissner/libsidplayfp-wasm`
- `chip-player-js` — `github.com/mmontag/chip-player-js` (game-music-emu, libsidplayfp and others in
  one Emscripten build; the source of the time-to-audio figure)
- ASAP web — `asap.sourceforge.net/web.html`, and webASAP at `wothke.ch/webASAP`
- File System Access API support — MDN, `developer.mozilla.org/en-US/docs/Web/API/File_System_API`
- Kotlin/Wasm status — `kotlinlang.org/docs/wasm-overview.html`
- `chiptune3@0.8.9` — npm, listed via `data.jsdelivr.com/v1/packages/npm/chiptune3@0.8.9` and
  fetched from `cdn.jsdelivr.net/npm/chiptune3@0.8.9/libopenmpt.worklet.js`. The sizes in §4 are
  *measured*, not read; this is here so the artefact can be found again.
