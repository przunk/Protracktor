# Plan: a web version

Written 2026-09-08, expanding `docs/WISHLIST.md` **B5** — *"the same music in a browser, sharing
state with the phone through an account"*, raised by the owner on 2026-09-01 and left as a thought.

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
project, touches Android in six imports and eleven expressions.

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
browser at an acceptable cost*. W2 is where it becomes the thing the owner described. W3 is a
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

**But the file-level measure understates the answer, badly, in one place.**

| file | lines | `android`/`androidx` imports | Android expressions |
| --- | --- | --- | --- |
| `player/PlaybackController.kt` | **2,963** | 6 | 11 |

Six imports — `Context`, `Intent`, `Uri`, `Process`, `SystemClock`, `DateUtils` — and eleven uses of
them across nearly three thousand lines. Four of the six have a one-line browser equivalent
(`SystemClock` → `performance.now()`, `DateUtils` → a formatter we already half own in
`ReleaseYear`). `Context` and `Uri` are the two that carry real weight, and they carry it into the
stores, not into the logic.

So the honest split is not 18/82. It is closer to:

| | roughly | what it is |
| --- | --- | --- |
| **carries over** | ~5,400 lines | the domain, the queue, the navigation, the schema, the catalogue parsing, the playback logic |
| **rewritten against browser APIs** | ~2,900 lines | the stores (`data/`), the fetchers (`net/`), the service, audio focus |
| **rewritten entirely** | ~5,400 lines | the Compose UI |

This is *reasoned* rather than measured — nobody has tried to compile any of it off Android — but
the two counts underneath it are real, and they say something worth saying plainly: **`ARCHITECTURE`
§7 worked.** The rule "extract when the decision has been wrong before" was written for testability
and it bought portability for free. That is an argument for keeping to it, independent of whether a
web version ever exists.

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
| ZXTune | c93e81d | LGPL-3.0 | **work in progress at best** | the weakest link: the largest C++ of the seven, and the one we deliberately do not fork (`ARCHITECTURE` §3) |

**The finding is that six of seven have a precedent and one does not.** ZXTune is also the newest
arrival — merged 2026-09-07 — and the only one where a web version would plausibly ship with a
format missing. That is survivable: it is 3,639 Modland files against 178,795 for libopenmpt.

**What none of this tells us is the size.** `chip-player-js` bundles a comparable set and reports
time-to-audio under 500 ms, which is encouraging and is not a byte count. Our APK carries three ABIs
and is 18 MB; one wasm ABI should be far less, but *nobody has measured it and it should be measured
before anything else*, because a 30 MB download is not a web player.

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

---

## 6. Where the music comes from — and the surprise

This was expected to be the wall. **It is not** (*measured* — one range `GET` per host with an
`Origin:` header, from this machine, 2026-09-08).

| host | what we use it for | `Access-Control-Allow-Origin` |
| --- | --- | --- |
| `modland.com` | the 40 MB index **and every module file** | `*` |
| `asma.atari.org` | the 20 MB ASMA archive | `*` |
| `www.hvsc.c64.org` | 5.2 MB of song lengths | `*` |
| `api.modarchive.org` | per-module download | `*` |
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

**Two caveats, stated so nobody trusts this further than it goes.** A permissive header on a simple
range `GET` is what a browser needs for a simple `GET`; a request carrying custom headers preflights
separately and was not tested. And a header measured once on one day is not a promise: any of these
hosts can drop it without telling anyone, and then a working web player breaks with nothing on our
side having changed. That is a risk the Android app does not carry at all.

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
| **A. Kotlin Multiplatform** | share the domain, compile it to Kotlin/Wasm, rewrite the stores, drive the decoder wasm through JS glue | the ~5,400 lines of §3 and their 21 test files are literally reused; one bug fixed once | Kotlin/Wasm's web target is **Beta** (*read*, 2026); Kotlin/Wasm and Emscripten are two separate wasm modules that must talk through JavaScript, and the audio worklet is on the far side of that glue — the exact boundary §5 says must not have glue in it |
| **B. A separate web app** | TypeScript, decoders via Emscripten, domain reimplemented | no toolchain risk; the audio worklet owns the decoder directly; a small W1 is genuinely a weekend | the domain is written twice and drifts, which `AGENTS.md` §8 names as the thing that rots quietly — a rule changed on the phone and not in the browser will not be noticed by anyone until a user sees it |
| **C. Neither yet — do the server first** | build **B19** (hosted catalogue indexes) and let the phone use it; the browser client comes later against an interface that already exists | it is useful to the phone **today** — a 40 MB index download is the app's largest single cost, and B19 exists as a wish for that reason alone; it also settles §6's two proxy endpoints as a side effect | it is not a web player, and if the owner wants a web player this does not produce one |

**Recommendation: W1 as a throwaway on route B, then decide.**

The reasoning: the question that determines everything is *what does the decoder set weigh and cost
in a browser*, and that question is answered identically under A and B. B answers it in the smallest
number of moving parts, and route A's real risk — Kotlin/Wasm talking to Emscripten across JS at the
audio boundary — is a thing to measure before committing 5,400 lines of shared code to it, not
after.

Route C is the one to take **if the honest answer is that this is not going to happen soon**, because
it is the only one of the three that pays for itself without a web version existing.

---

## 11. What this changes about decisions being made today

B5's claim that it *"would change decisions we are making today"* is the reason this document was
written. Concretely, and independent of whether any web version is ever built:

1. **Keep pulling logic out of `PlaybackController`.** §3 shows why: the file is 2,963 lines with
   eleven Android expressions in it, which means almost all of it is domain wearing a phone's coat.
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

---

## Sources for the *read* claims

Listed so the next person can re-check them rather than trust this file, per `AGENTS.md` §7.

- webSC68 — `github.com/wothke/sc68-2.2.1` (Emscripten port; the hosted player states sc68 3.0.0b)
- `libsidplayfp-wasm` — `github.com/chrisgleissner/libsidplayfp-wasm`
- `chip-player-js` — `github.com/mmontag/chip-player-js` (game-music-emu, libsidplayfp and others in
  one Emscripten build; the source of the time-to-audio figure)
- ASAP web — `asap.sourceforge.net/web.html`, and webASAP at `wothke.ch/webASAP`
- File System Access API support — MDN, `developer.mozilla.org/en-US/docs/Web/API/File_System_API`
- Kotlin/Wasm status — `kotlinlang.org/docs/wasm-overview.html`
