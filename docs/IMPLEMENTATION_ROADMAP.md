# Implementation roadmap

Updated: 2026-10-13. Owner-requested inventory of unfinished, deferred and undecided work.
This is a planning index, not an implementation order or permission to resume shelved work.
Completed tasks are excluded. Historical notes are reconciled where later records establish
completion; uncertain coverage remains explicitly marked for checking.

Maintain this index alongside [Wishlist](WISHLIST.md), [Backlog](BACKLOG.md),
[Status](STATUS.md) and [Open questions](OPEN_QUESTIONS.md).
Application changes still need a task branch, regression checks, full tests/lint and owner
device acceptance before merging. Documentation checks do not establish device behaviour.

## 1. Wishlist: features and ideas

| ID | Remaining work | State |
| --- | --- | --- |
| B47 | Bluetooth transport: Next, Previous, Play/Pause and Stop | Owner request, 2026-10-13. MediaSession callbacks already exist; verify receiver-button behaviour and address demonstrated gaps. |
| B46 | Recent queries offered when entering the search field | Real user request; discuss three entries versus a longer scrollable history, retention and reuse. Q15. |
| B45 | Active playback queue separate from the visible playlist, keeping music playing when returning from search/author browsing | Real user request; context transitions and transport/editing/restoration rules remain undecided. Q13. |
| B44 | Android Auto | Tester request; no selected scope or implementation schedule. |
| B40 | Remember measured SAP lengths for every subsong | Not scheduled; avoid repeatedly measuring the same subsong. |
| B38 | Phone pairing on public GitHub Pages | Deferred; the owner currently accepts no public-page pairing. |
| B37 | Google Cast / AirPlay | Not scheduled. The existing decision retains Cast as a possible future project and does not select AirPlay. |
| B36 | Own song-length database based on loop detection | Inventory completed; pilot on about 50 NSF games remains, with expansion conditional on reliable results. |
| B35 | Western / Central European encoding setting for old file text | Explicitly not to be implemented; retained as an option only. |
| B34 | Hide filename extensions/format prefixes | Shelved by the owner; do not implement or propose again without a renewed request. |
| B33 | Access Modland's `/incoming/`, absent from its normal index | Idea; no crawler or other implementation selected. |
| B31 | Drag local files into the web player | Not implemented; file lifetime and playlist ownership need decisions. |
| B30 | Read ASMA STIL titles, credits, comments and provenance | Not implemented; distinguish original composer from arranger. |
| B29 | Readable overview of open work in the documentation | This roadmap supplies the requested inventory. Placement/navigation and a manually maintained versus generated summary are not separately selected. |
| B28 | Supply artists for The Mod Archive tracks | Measure songdb coverage first; otherwise choose on-demand lookup or an explanation of missing data. |
| B25 | Swipe actions on a track row | Discuss gesture and action semantics before implementation. |
| B19 — indexes | Server-hosted periodically refreshed catalogue indexes | Infrastructure idea; no implementation selected. |
| B17 | Avoid unnecessary downloads after an accidental online-row tap | Decide between delay, cancellation and accepting current behaviour. |
| B19 — export | Export a playlist together with its music files | Discuss scope, size, import/storage and distribution terms. |
| B5 | Accounts and synchronised favourites/history between Android and web | The web player already exists; account/synchronisation scope remains an idea. |
| B6 | Stereo narrowing, mono mixing and possibly headphone crossfeed | Renewed real user request; defaults, strength, supported paths and listening checks remain undecided. Q14. |
| B7 | Full metadata for any supported file, including one not currently playing | Still open in the wishlist; establish the gap beyond existing Information/Now Playing before selecting work. |
| B9 | Optional scope or per-channel visualiser | Idea. |
| B11 | Per-format playback settings: interpolation, separation, SID model and filters | Idea; establish missing controls beyond existing backend capabilities. |
| B12 | Gapless/crossfade between subsongs | Idea. |

The source wishlist contains two different B19 entries. Descriptive suffixes distinguish them
without renumbering existing references. Bluetooth controls and Android Auto are different scopes;
B47 does not approve a car-browsing integration.

## 2. Defects and retained risks

Evidence and reproduction records live in [Status](STATUS.md), [Backlog](BACKLOG.md) and
[Architecture closeout](ARCHITECTURE_CLOSEOUT.md).

| Reference | Remaining problem | Evidence/state |
| --- | --- | --- |
| C102 | Connecting Bluetooth stops sound and stalls the app's progress while notification progress continues | Output-only recovery on `fix/bluetooth-output-recovery`, stacked on C103; 772 unit tests, 18 native checks and lint passed. Combined release trial 1.1.1 (1138) built. Owner confirmed speaker → Bluetooth playback and disconnect Pause on 2026-10-13; other phone checks and merge pending. |
| C96 | UADE crashes while cleaning up after rejecting a file | Scanning avoids some triggering paths; the teardown cause remains unfixed. |
| C36 | Keyboard/media-key Next or Previous on web can be swallowed after a long press ending without a click | Host sequence reproduced; touch event ordering needs device verification before a fix. |
| C26 | Reported SPC tempo difference | Deferred investigation; compare the same rip with an independent decoder. Cause not established. |
| A43 | More from this author opens an empty folder when the archive has no index | Open finding; guard the unavailable action or explain the missing prerequisite. |
| A45 | Author jump from a playlist does not identify the author as the Random jump does | Open finding; align the heading without introducing Random-only session behaviour. |
| C103 — snapshot ownership | A delayed write can pair the newly selected playlist ID with the previous queue | Owner-selected fix implemented on `fix/playlist-write-safety`; 768 tests and lint passed. Included in combined Bluetooth trial APK 1.1.1 (1138); playlist phone check and merge pending. |
| C103 — Save completion | Save completion can mark a newer, unsaved edit clean | Same separate fix; completion compares playlist identity and saved contents. Not yet accepted on a phone or merged. |
| Browse/navigation risk | An older catalogue, folder or search read can publish after newer navigation | Retained ordering risk; host-controlled sequences, not a phone-frequency measurement. |
| Back navigation risk | A captured snapshot can overwrite a newer reentrant parent-read update | Retained during extraction; separate fix required. |
| Read-ahead risk | An empty/already-held candidate list returns before cancelling an older fetch | Host sequence reproduced; stale bytes can subsequently enter the in-memory map. |
| Download cancellation notice | Stopped notice is suppressed because cancellation is checked inside NonCancellable cleanup | Production workflow exercised with controlled host ports; phone behaviour not independently checked. |
| Review round 5 R7 | Concurrent fetches for one URL share a temporary `.part` filename | Low-priority retained cache risk; [review record](review.md). |
| Review round 5 R8 | Cache eviction can remove another fetch's newly written file | Low-priority retained risk of a redundant download; [review record](review.md). |

These are not all independently reported user defects. Preserve each record's source category;
host race demonstrations do not establish how often the sequence occurs on a phone.

## 3. Technical work

- **A67:** remember files that were examined and are not music, avoiding repeated unsuccessful
  probing on later scans. Name-based skipping is already implemented.
- **A46 residuals:** recheck remaining onboarding/download proposals: a brief index explanation,
  clearer labelled download controls, metered-network information and measurable within-step
  progress. The download picker and offer on an empty playlist already exist.
- **A39:** MP3 cover thumbnails; parked.
- **A26:** reduce copying of large files over JNI when justified, and consider replacing the
  single-element error array. Neither is a demonstrated defect or selected priority.
- **Random query performance:** measure `ORDER BY RANDOM()` against a large index before choosing
  a different sampling strategy. [Review round 8 R7](review-round-8.md).
- **Architecture:** remaining controller responsibilities, Compose screens, web modules and native
  adapters are deferred, not an automatically resumed project. The bounded refactor was accepted;
  [closeout](ARCHITECTURE_CLOSEOUT.md) records what stayed in place.
- **Comments:** repository-wide shortening/correction remains deferred; only a bounded comment
  pass was completed. Historical unchecked stages in GOAL are not current authorization.

## 4. Formats and archives

See [Format plan](PLAN_FORMATS.md), [Catalogue plan](PLAN_CATALOGUES.md) and
[Web library plan](PLAN_WEB_LIBRARY.md). Historical headings may precede later implementations;
do not plan SAP, SID, AHX/HVL, ZXTune or UADE integration again merely from an old heading.

- **C81(b):** support NSF/FDS rips loading below `$8000`; the truthful unsupported-file error is
  implemented, not the FDS memory model.
- **C80:** play BASIC RSID; the explicit refusal is implemented, while the ROM decision remains.
- **C20 / A5:** old Music Editor MED is still a support gap. Later C88 work removes the false
  playability promise; do not treat that historical listing defect as newly unfixed.
- **A5 candidates:** 2SF, GSF, PSF, USF, SSF/DSF, MDX, FMP, Euphony and PMD. These are survey
  candidates, not an approved batch of new decoders.
- **A7 / HVSC:** browse the collection itself; SID song-length lookup already exists.
- **A7 / Aminet:** indexing and archive extraction.
- **Catalogue candidates:** investigate vgmrips, snesmusic and zxart if selected.
- **A7 / AMP (Amiga Music Preservation):** renewed owner TODO, 2026-10-13. Establish an approved
  access/index/download route before deciding integration. The 2026-09-04 investigation found no
  sanctioned machine-readable route; this request does not authorize crawling, scraping or contact
  with maintainers. Reuse A7, not a duplicate wishlist item.
- **Web relay:** UnExoticA and The Mod Archive need separate access/infrastructure decisions for
  browser integration; Android permission does not approve server-side relaying.
- **Web handoff:** optionally retain several From the phone playlists rather than only the latest;
  no decision selected.

## 5. Open decisions, verification and documentation

- **Q6:** restoration policy for formats requiring re-rendering from the beginning.
- **Q9:** whether to isolate decoders/engine in a separate Android process.
- **Q13–Q15:** queue/navigation, stereo/headphone controls and recent-search retention; covered
  by B45, B6 and B46 rather than separate implementation duplicates.
- **F-Droid:** maintainer suggestion retained for discussion; no distribution prepared.
- **Google Play:** additional real closed testing required before another production-access
  application. Owner-controlled upload/publication is separate from a prepared 1.1.0 artifact.
- **C3:** measure next-track latency against the owner's real library.
- **C92/C93:** complete physical-iPhone checks of corrected web layout/zoom behaviour.
- **iOS web audio:** verify background and screen-lock playback on actual devices.
- **PWA:** verify offline operation separately; installation and normal use were owner-confirmed.
- **B47 / C102:** verify Bluetooth buttons and remaining routing scenarios; basic speaker → Bluetooth
  playback and disconnect Pause were owner-confirmed on 2026-10-13. Build/device/receiver details
  and the remaining checklist outcomes are still missing.
- **UnExoticA:** server User-Agent whitelisting remains unconfirmed. Playback and catalogue caching
  have been owner-confirmed; older pending-test notes are not current blockers.
- **C86:** historical Pages deployment interruption was worked around; root cause unknown.
  The latest recorded deployment succeeded, so this is not a current failed deployment.
- **Documentation:** reconcile stale limitations, branch/pending headings and pre-1.1.0 preparation
  notes without losing historical evidence or inventing upload/publication results.
- **Web light palette:** matching it to the app's sampled colours is explicitly shelved.

No literal TODO/FIXME/XXX markers were found during the 2026-10-13 inventory search of first-party
`app/src`, `native/engine`, `native/backends`, `scripts` and `web/src`. This does not cover vendor
sources and is not a full code review or a claim that the implementation has no outstanding work.

## Selected safety work and suggested next session — 2026-10-13

1. The owner selected playlist-write safety (C103). Implementation is on its own branch with
   failing-before/passing-after regressions; full verification and phone acceptance are tracked
   in [Status](STATUS.md). No merge yet.
2. The owner subsequently selected C102. Output-only recovery is implemented on its own branch
   above C103; prepare the combined release APK and follow the phone checklist before merging.
3. For a subsequent user-facing feature, discuss/select B6 stereo narrowing/mono mixing before
   undertaking the larger queue-ownership change. Headphone processing remains optional.

Steps 1 and 2 are now owner-selected. The owner deferred the first APK until Bluetooth recovery
was included. The code-level disconnection mechanism still needs confirmation against the phone.

## Verification of the original inventory update

Documentation-only: checked consistent IDs, local file links, source/state wording and Git diff
whitespace. No application code changed, no tests/lint or APK build run, no new phone result,
push, merge or release implied.
