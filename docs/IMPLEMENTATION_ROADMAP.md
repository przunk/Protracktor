# Implementation roadmap

Updated: 2026-10-22. Inventory of unfinished, deferred and undecided work.
This is a planning index, not an implementation order or permission to resume shelved work.
The unpublished version-history correction restored baseline 1.1.0 before release 1.2.0.
The latest prepared tagged AAB is **1.3.0 (1269)**; upload and Play publication are unconfirmed.
[Current release record](RELEASE_NOTES_1.3.0.md). Pre-correction historical IDs/SHAs below refer to
the archived original graph, not newly built artifacts. [Commit map and validation](VERSION_HISTORY_REWRITE.md).
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
The manual-queue extension was subsequently accepted after phone testing and merged on
2026-10-21 as **3bc5a98**, including C113–C117 and Clear/reorder/Info/gesture work. Its source
1a1503f passed 1096 tests/full gates/lint; APK 1.2.0 (1261) remains the artifact. B45 follow-up
is no longer unfinished inventory work. [Integration record](QUEUE_BRANCH_REVIEW.md#acceptance-and-integration--2026-10-21).
Granular device checks remain recorded separately; AMP stays deferred. C112's compact dock trial
was accepted and merged as **e90c817**, 2026-10-22. C109/C111 were accepted and merged on
2026-10-21; C119's deeper Modland path correction was accepted and merged as **0a7bcc0**,
2026-10-22. C118 still records the open History freshness issue below.
[Baseline evidence](QUEUE_EXPERIMENT.md).

| ID | Remaining work | State |
| --- | --- | --- |
| B52 | Normalize perceived loudness between tracks | Selected 2026-10-22 as the next task after Queue return-to-source navigation. Listening feedback: quiet-to-loud track transitions are unpleasant. Algorithm, clipping protection, dynamics, backend coverage and measurement/cache strategy need analysis; not implemented or started as part of navigation. [Wishlist](WISHLIST.md#b52-consistent-perceived-loudness-between-tracks--next-task-recorded-2026-10-22). |
| B45 — return to source | Open the active context's originating view from Queue | Selected 2026-10-22. Separate unmerged trial on `feature/queue-source-navigation`; all source types, playback independence, dirty-draft protection and fixed normal-size header geometry. Source b225ff9 passed 1147 host tests/full gate/lint; release APK 1.3.0 (1290) is ready for phone acceptance, not part of the previously accepted manual-queue baseline. [Contract and artifact](QUEUE_SOURCE_NAVIGATION.md). |
| B51 | Save a selected subsong as a playlist entry instead of the whole multi-subsong file | Request recorded 2026-10-21; useful for files reporting hundreds of subsongs. Explicit selection, not automatic expansion. Entry identity, playback/queue behaviour and persistence/export need design; no implementation selected. [Wishlist](WISHLIST.md#b51-save-an-individual-subsong-in-a-playlist--todo-recorded-2026-10-21). |
| B50 | Animated multi-page tutorial on first launch and/or from Settings | User request recorded 2026-10-21; TODO, analysis only. Safe animated previews recommended; startup, dismissal and upgrade choices remain open. Shares index guidance with A46; no implementation authorized. [Analysis](TUTORIAL_ANALYSIS.md). |
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
| ~~C119~~ | ~~Nested Modland legacy unknown-author folders are outside C109's recovery rule~~ | Accepted after satisfactory phone playback and merged **0a7bcc0**, 2026-10-22. Exact-directory recovery at variable depth without ID/cache changes; failing-before-fix regressions, real non-silent host decode, forced 1119-test full gate and fresh lint passed. APK **1.3.0 (1285)**, source **657f4da**, unchanged. Detailed phone scenarios not individually reported. [Report](STATUS.md#c119-nested-modland-legacy-unknown-author-paths-still-fail-to-download). |
| C118 | Open History does not reflect newly recorded playback until leaving and reopening it | User report, 2026-10-21. Open, not implemented or independently reproduced. Reconcile live updates with A56's stable History ordering and no self-recording policy. [Report](STATUS.md#c118-history-does-not-show-newly-recorded-plays-while-the-view-remains-open). |
| ~~C116~~ | ~~Queue briefly flashes after Next scrolls below the viewport~~ | Accepted and merged **3bc5a98**, 2026-10-21. Surviving-row opacity regression failed before correction; disappearance/placement retained. Overall trial accepted; granular phone results not enumerated. [Evidence](QUEUE_AUTO_FOLLOW.md). |
| ~~C117~~ | ~~Omitted repeated shuffle history can prematurely disable Next/Previous~~ | Accepted and merged **3bc5a98**, 2026-10-21. Code-review finding with both direction regressions failing before correction; no separate phone reproduction claimed. [Audit](QUEUE_BRANCH_REVIEW.md). |
| ~~C113~~ | ~~Clear leaves the standalone manual list indented~~ | Accepted and merged **3bc5a98**, 2026-10-21; conditional content inset, failing layout probe. [Evidence](QUEUE_ROW_REGRESSIONS.md). |
| ~~C114~~ | ~~Current manual row highlight/hit area excludes its inset~~ | Accepted and merged **3bc5a98**, 2026-10-21; full-width paint/hit area, failing pixel/hold probes. [Evidence](QUEUE_ROW_REGRESSIONS.md). |
| ~~C115~~ | ~~Playlist grip loses ownership, row layering and viewport tracking~~ | Accepted and merged **3bc5a98**, 2026-10-21; stable subtree, outer layer, viewport pin and batched moves with negative probes. [Evidence](QUEUE_ROW_REGRESSIONS.md). |
| ~~C111~~ | ~~Add music and Online catalogues content padding changes between views~~ | Accepted and merged **03dfc1d**, 2026-10-21, after the 1106-test full gate/lint and shared APK trial. Slight residual movement is accepted; not a pixel-identical layout claim. [Report](STATUS.md#c111-add-music-and-online-catalogues-have-inconsistent-padding). |
| ~~C110~~ | ~~Browse reopens playing History instead of the main Browse view~~ | Owner accepted and requested merge, 2026-10-19; merged `7e7f242`, tree equals accepted branch. 963 tests, lint without errors; APK 1.2.0 (1231) remains the artifact. [Evidence](https://github.com/przunk/protracktor/blob/2c35319cfa8b665398a36141248b45544bd68ebe/docs/PR_C110_BROWSE_ROOT_NAVIGATION.md). |
| ~~C109~~ | ~~Legacy Modland unknown-author paths fail to download (YM and Same Game SPC)~~ | Provisionally resolved after satisfactory phone playback; merged **122368f**, 2026-10-21. Saved identities are retained; 36 focused checks and the combined 1106-test full gate/lint passed. Longer-term coverage remains open. [Analysis](MODLAND_LEGACY_DOWNLOADS.md). Separate from C26 and B45. |
| ~~C112~~ | ~~Dock subsong-position label crowds the Now Playing text beside Queue~~ | Accepted after positive phone testing and merged **e90c817**, 2026-10-22. APK **1.3.0 (1281), 0b9169b** remains unchanged; 60 dp card/original text insets, 1113 tests/full gates/lint passed. Detailed accessibility/checklist outcomes not separately reported. [Decision and evidence](STATUS.md#c112-dock-subsong-count-crowds-now-playing-text). |
| C96 | UADE crashes while cleaning up after rejecting a file | Scanning avoids some triggering paths; the teardown cause remains unfixed. |
| C104 | Headset/media buttons do not control the WWW player | Owner report, 2026-10-13; Android buttons appear to work. Browser/device/build/buttons and mechanism unknown; deferred, tracked with B47. |
| C36 | Keyboard/media-key Next or Previous on web can be swallowed after a long press ending without a click | Host sequence reproduced; touch event ordering needs device verification before a fix. |
| C26 | Reported SPC tempo difference | Deferred investigation; compare the same rip with an independent decoder. Cause not established. |
| A43 | More from this author opens an empty folder when the archive has no index | Open finding; guard the unavailable action or explain the missing prerequisite. |
| A45 | Author jump from a playlist does not identify the author as the Random jump does | Open finding; align the heading without introducing Random-only session behaviour. |
| Browse/navigation risk | An older catalogue or folder read can publish after newer navigation | Visit/route ownership merged at `e9a7a98`, 2026-10-18; full gate passed (929 tests, lint zero errors), APK 1.2.0 (1214) ready. Owner authorized integration before phone acceptance, which remains pending. Search ownership was already fixed by C106. [Stability report](https://github.com/przunk/protracktor/blob/2c35319cfa8b665398a36141248b45544bd68ebe/docs/PR_BROWSE_DOWNLOAD_STABILITY.md). |
| Back navigation risk | A captured snapshot can overwrite a newer reentrant parent-read update | Parent-first publication merged at `e9a7a98`; full gate passed, phone acceptance pending. |
| Read-ahead risk | An empty/already-held candidate list returns before cancelling an older fetch | Retirement and late-completion guards merged at `e9a7a98`; full gate passed, phone acceptance pending. |
| Download cancellation notice | Stopped notice is suppressed because cancellation is checked inside NonCancellable cleanup | Production workflow exercised with controlled host ports; phone behaviour not independently checked. |
| Review round 5 R7 | Historical shared `.part` filename finding | Current SharedFetches already joins one-URL transfers. Unique staging files and failure cleanup merged at `e9a7a98`; not a newly reproduced duplicate transfer. [Reassessment](https://github.com/przunk/protracktor/blob/2c35319cfa8b665398a36141248b45544bd68ebe/docs/review.md). |
| Review round 5 R8 | An old eviction snapshot can remove a concurrent replacement | Controlled disk reproduction fails on the old algorithm; transactional cache correction passed the full gate and merged at `e9a7a98`, phone acceptance pending. [Report](https://github.com/przunk/protracktor/blob/2c35319cfa8b665398a36141248b45544bd68ebe/docs/PR_BROWSE_DOWNLOAD_STABILITY.md). |

C108 protection was approved for integration and merged at `8f3ef56`, 2026-10-15, and is no longer
open. Schema remains 23; older destructive binaries are not repaired by this merge.
[Verification and remaining phone evidence limits](https://github.com/przunk/protracktor/blob/2c35319cfa8b665398a36141248b45544bd68ebe/docs/PR_C108_PRESERVE_NEWER_DATABASE.md).

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
  progress. The download picker and offer on an empty playlist already exist. B50's broader
  [tutorial analysis](TUTORIAL_ANALYSIS.md) shares the index explanation, not the remaining
  download implementation work.
- **A39:** MP3 cover thumbnails; parked.
- **A26:** reduce copying of large files over JNI when justified, and consider replacing the
  single-element error array. Neither is a demonstrated defect or selected priority.
- **Random query performance:** measure `ORDER BY RANDOM()` against a large index before choosing
  a different sampling strategy. [Review round 8 R7](https://github.com/przunk/protracktor/blob/2c35319cfa8b665398a36141248b45544bd68ebe/docs/review-round-8.md).
- **Retained review observations, 2026-10-14:** reassess against current code before selecting
  implementation. These are not newly reported phone defects:

  - Measure repeated notification/heart projection on progress ticks with a large playlist before
    introducing caches or changing update policy.
  - Check the composite author/platform/year `ARTIST` value on a real MediaSession receiver;
    do not silently reverse the accepted notification presentation.
  - The audio-host harness checks ordered recovery but does not establish concurrent Oboe
    callback/shutdown lifetime safety. Additional synchronized tests and phone stress are options,
    not evidence of an existing memory fault.
  - The APK wrapper's signature check can warn and continue after verification failure. The
    warning-only path is still present on 2026-10-21; invalid/unsigned/wrong-certificate artifact
    tests and enforcement remain separate work. Previously handed-off APKs were independently
    verified; their signatures are not invalidated by this tooling observation.

  [Original R3–R6 analysis and evidence limits](https://github.com/przunk/protracktor/blob/2c35319cfa8b665398a36141248b45544bd68ebe/docs/review-2026-10-14.md).
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
