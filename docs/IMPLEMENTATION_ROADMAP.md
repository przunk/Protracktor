# Implementation roadmap

Updated: 2026-10-20. Owner-requested inventory of unfinished, deferred and undecided work.
This is a planning index, not an implementation order or permission to resume shelved work.
The owner-approved unpublished version-history correction restores release baseline 1.1.0;
release 1.2.0 now has a prepared tagged AAB (Play publication unconfirmed). Historical IDs/SHAs below refer to the archived original graph,
not newly built artifacts. [Commit map and validation](VERSION_HISTORY_REWRITE.md).
Completed tasks are excluded. Historical notes are reconciled where later records establish
completion; uncertain coverage remains explicitly marked for checking.

Maintain this index alongside [Wishlist](WISHLIST.md), [Backlog](BACKLOG.md),
[Status](STATUS.md) and [Open questions](OPEN_QUESTIONS.md).
Application changes still need a task branch, regression checks, full tests/lint and owner
device acceptance before merging. Documentation checks do not establish device behaviour.

## 1. Wishlist: features and ideas

B48 notification heart and B46 recent searches were accepted by the owner and merged in order
on 2026-10-14 (`61886e7`, `7111c47`), so they are no longer open inventory entries. Individual
device checklist outcomes were not separately supplied; [remaining checks](TESTING.md) are retained.
Dated implementation checkpoints below describe the earlier, unaccepted builds.
B49 live History search was accepted by the owner and merged on 2026-10-15 (`ef8cd39`);
it is no longer open. [Verification and acceptance](PLAN_HISTORY_SEARCH.md).
B45 active playback queue was accepted and merged on 2026-10-19 (`7cab210`), after normal-version
cleanup and a fresh 956-test full gate/lint. It is no longer open inventory work. Further colleague
feedback is now captured in the [context and manual queue analysis](QUEUE_CONTEXT_ANALYSIS.md).
The extension is implemented on an unmerged trial branch, separate from the accepted baseline;
phone acceptance is pending and AMP stays deferred.
[Baseline evidence](QUEUE_EXPERIMENT.md).

| ID | Remaining work | State |
| --- | --- | --- |
| B45 follow-up / Q13 | Unified list with one-shot manual inserts and editable session context | Implemented in separate stages on the same unmerged branch; source 613b154 passed 1028 tests/full gates and lint, APK 1.2.0 (1245) ready. Phone acceptance pending. Prior two-tab APK 1239/evidence remain historical. AMP and compact dock-label C112 deferred. [Contract/review/handoff](QUEUE_UNIFIED_LIST.md). |
| B47 | Bluetooth transport: Next, Previous, Play/Pause and Stop | Android headset buttons appear to work in owner testing; remaining report concerns WWW buttons (C104). Deferred by the owner on 2026-10-13; do not reimplement Android transport. |
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
| B25 | Swipe actions on a track row | Third iteration selected: right enqueue on all track lists; unified list also left-removes either origin with Undo. [Contract/evidence](QUEUE_UNIFIED_LIST.md), phone acceptance pending. No reorder or source-storage deletion. |
| B19 — indexes | Server-hosted periodically refreshed catalogue indexes | Infrastructure idea; no implementation selected. |
| B17 | Avoid unnecessary downloads after an accidental online-row tap | Decide between delay, cancellation and accepting current behaviour. |
| B19 — export | Export a playlist together with its music files | Discuss scope, size, import/storage and distribution terms. |
| B5 | Accounts and synchronised favourites/history between Android and web | The web player already exists; account/synchronisation scope remains an idea. |
| B6 | Stereo narrowing, mono mixing and possibly headphone crossfeed | Original / Narrow / Mono accepted and merged at `f4d304c`, 2026-10-13; [plan](PLAN_STEREO_MIX.md). Original default and 50% Narrow retained. Individual remaining device checks and longer-term usefulness are open; crossfeed and web remain outside the trial. Q14. |
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
| C111 | Add music and Online catalogues content padding changes between views | Owner/user report, 2026-10-19; visible alignment/spacing mismatch. Measurements and cause not established; not fixed or included in queue work. [Report](STATUS.md#c111-add-music-and-online-catalogues-have-inconsistent-padding). |
| ~~C110~~ | ~~Browse reopens playing History instead of the main Browse view~~ | Owner accepted and requested merge, 2026-10-19; merged `7e7f242`, tree equals accepted branch. 963 tests, lint without errors; APK 1.2.0 (1231) remains the artifact. [Evidence](PR_C110_BROWSE_ROOT_NAVIGATION.md). |
| C109 | Modland Same Game SPC entry reports a download/connection failure; other tracks also affected | Owner report, 2026-10-18; exact file path, other entries and build need confirmation. Code inspection confirms generic reporting also covers HTTP failures; actual cause not established. Separate from C26 and B45. |
| C112 | Dock subsong-position label crowds the Now Playing text beside Queue | Owner/user report, 2026-10-20; explicitly deferred from the unified-list iteration. Compact presentation needs later discussion. |
| C96 | UADE crashes while cleaning up after rejecting a file | Scanning avoids some triggering paths; the teardown cause remains unfixed. |
| C104 | Headset/media buttons do not control the WWW player | Owner report, 2026-10-13; Android buttons appear to work. Browser/device/build/buttons and mechanism unknown; deferred, tracked with B47. |
| C36 | Keyboard/media-key Next or Previous on web can be swallowed after a long press ending without a click | Host sequence reproduced; touch event ordering needs device verification before a fix. |
| C26 | Reported SPC tempo difference | Deferred investigation; compare the same rip with an independent decoder. Cause not established. |
| A43 | More from this author opens an empty folder when the archive has no index | Open finding; guard the unavailable action or explain the missing prerequisite. |
| A45 | Author jump from a playlist does not identify the author as the Random jump does | Open finding; align the heading without introducing Random-only session behaviour. |
| Browse/navigation risk | An older catalogue or folder read can publish after newer navigation | Visit/route ownership merged at `e9a7a98`, 2026-10-18; full gate passed (929 tests, lint zero errors), APK 1.2.0 (1214) ready. Owner authorized integration before phone acceptance, which remains pending. Search ownership was already fixed by C106. [Stability report](PR_BROWSE_DOWNLOAD_STABILITY.md). |
| Back navigation risk | A captured snapshot can overwrite a newer reentrant parent-read update | Parent-first publication merged at `e9a7a98`; full gate passed, phone acceptance pending. |
| Read-ahead risk | An empty/already-held candidate list returns before cancelling an older fetch | Retirement and late-completion guards merged at `e9a7a98`; full gate passed, phone acceptance pending. |
| Download cancellation notice | Stopped notice is suppressed because cancellation is checked inside NonCancellable cleanup | Production workflow exercised with controlled host ports; phone behaviour not independently checked. |
| Review round 5 R7 | Historical shared `.part` filename finding | Current SharedFetches already joins one-URL transfers. Unique staging files and failure cleanup merged at `e9a7a98`; not a newly reproduced duplicate transfer. [Reassessment](review.md). |
| Review round 5 R8 | An old eviction snapshot can remove a concurrent replacement | Controlled disk reproduction fails on the old algorithm; transactional cache correction passed the full gate and merged at `e9a7a98`, phone acceptance pending. [Report](PR_BROWSE_DOWNLOAD_STABILITY.md). |

C108 protection was approved for integration and merged at `8f3ef56`, 2026-10-15, and is no longer
open. Schema remains 23; older destructive binaries are not repaired by this merge.
[Verification and remaining phone evidence limits](PR_C108_PRESERVE_NEWER_DATABASE.md).

Completed C105/C106/C107 left the open inventory after owner acceptance on 2026-10-15: C105 merged
at `557acbb`, then C106/C107 at `d74d339`. Their defect history and verification remain in
[Status](STATUS.md) and their PR records; other catalogue/folder navigation risks remain open.

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
- **Q13–Q16:** queue/navigation, stereo/headphone controls, recent-search retention and notification
  heart semantics; covered by B45, B6, B46 and B48 rather than separate implementation duplicates.
- **F-Droid:** maintainer suggestion retained for discussion; no distribution prepared.
- **Google Play:** additional real closed testing required before another production-access
  application. Owner-controlled upload/publication is separate from a prepared 1.1.0 artifact.
- **C3:** measure next-track latency against the owner's real library.
- **C92/C93:** complete physical-iPhone checks of corrected web layout/zoom behaviour.
- **iOS web audio:** verify background and screen-lock playback on actual devices.
- **PWA:** verify offline operation separately; installation and normal use were owner-confirmed.
- **B47 / C102:** verify Bluetooth buttons and remaining routing scenarios. The owner confirmed
  connection/disconnection while paused, sound handoff during playback and Pause on disconnection
  on 2026-10-13, then approved the merge. Build/device/receiver details and remaining checklist
  outcomes are still missing. Individual C103 playlist phone results were not supplied either.
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

1. ~~Implement playlist-write safety (C103): snapshot ownership and guarded Save completion.~~
   Completed and merged with owner approval at `f4c9bf1` on 2026-10-13. Regression/mutation checks
   and the full gate passed; individual playlist phone results remain unreported in [Status](STATUS.md).
2. ~~Implement output-only Bluetooth recovery (C102) and prepare the combined release trial.~~
   Trial 1.1.1 (1138) delivered; four routing states owner-confirmed, accepted and merged at `69c42a2`
   on 2026-10-13. Joint gate: 772 unit tests, 18 native checks, lint 0 errors/68 warnings.
3. ~~Implement B6's three-mode Android Settings trial on `feature/stereo-mix-modes`.~~
   Host gate passed: 782 unit tests, 30 native checks, lint 0 errors/69 warnings. Release APK
   1.2.0 (1146), source `ad6e3cd`, was offered. Positive owner feedback and merge approval followed;
   merged at `f4d304c` on 2026-10-13. Individual remaining device checks are not separately reported.
   Headphone crossfeed and the larger queue-ownership change remain unselected.

Steps 1–3 are merged, not remaining implementation work. The owner deferred the first APK until
Bluetooth recovery was included. The device error trace and additional phone scenarios remain
unconfirmed; merging does not establish store release/publication or long-term behaviour.

**Implementation checkpoint, 2026-10-13:** B48 and B46 are implemented on separate stacked task
branches with owner approval. Their combined forced host gate/lint passed; one minified release
APK 1.3.0 (1154), source `1452309`, was offered. Owner feedback requires notification slot and
search-field presentation corrections. Corrected gate passed 835 tests/112 suites, lint 0 errors/72
warnings; replacement **1.3.0 (1158)** APK, clean source `f289863`, is ready. Phone acceptance
remains pending.
Merge only afterward.
WWW media buttons/B47/C104,
UADE/C96 and queue/B45 are explicitly deferred.

## Verification of the original inventory update

Documentation-only: checked consistent IDs, local file links, source/state wording and Git diff
whitespace. No application code changed, no tests/lint or APK build run, no new phone result,
push, merge or release implied.
