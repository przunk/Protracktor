# Status

Updated: 2026-10-21 — C109/C111 candidates on separate stacked branches; shared test APK 1.3.0 (1273), source d370ea2, phone checks pending; prepared AAB 1.3.0 (1269), tag v1.3.0/master d456fa7; manual queue and C113–C117 accepted and merged as 3bc5a98; C112 deferred; schema 23.
The signed AAB is prepared; upload to Play and publication are not confirmed.
GitHub tags v1.1.0/v1.2.0 were verified remotely on 2026-10-16.
versionCode is the source commit count; later documentation commits do not relabel the AAB.
The last recorded Google response requested additional closed testing; later production access is not confirmed.
Historical dated checks below describe their own builds; they are not current test counts.

## C109/C111 — shared candidate ready for phone checks

`fix/modland-download-failures` contains C109 at **91ba854**. The agreed separate C111 branch
`fix/browse-padding` starts there; clean combined application source is **d370ea2**. Neither is
merged. Develop **44a9992**, master/tag **d456fa7** and the AMP branch remain unchanged.

**Checked on the host:** the forced full gate passed in **631 s**, **1106 tests / 144 suites**,
zero failures/errors/skips, all companion checks passed. Lint executed a fresh analysis:
**0 errors / 72 warnings**. The failing-before-fix HTTP/layout regressions and C109 status-guard
mutation are recorded in the individual reports below. Both real downloaded examples produced
non-silent PCM in the host engine; this does not establish Android playback.

Minified, test-signed release APK built in **163 s**:
[protracktor-release-1.3.0-1273-d370ea2-20261021-222612.apk](../dist/protracktor-release-1.3.0-1273-d370ea2-20261021-222612.apk),
**11,915,667 bytes**, SHA-256
`0934f1ed25bb81e2833f6555fdf67f015778eb4d39500cc00d0234398ca59125`.
The agent independently checked APK v2 signature, package/name/code, embedded source revision,
absence of the debuggable flag, ZIP integrity/alignment, minSdk 29/targetSdk 36 and all three
engine ABIs. R8 executed and its mapping was retained. Public test-certificate SHA-256:
`f97af50c2d869a2591dd82d24555894a2cc95927afb6c0a1fcd4bd197221e47e`.
This is a device-test artifact, not a Play upload. Logs, reports, mapping and artifact inspection
are retained in ignored `user/c109-c111-verification/`.

**Checked partially:** host HTTP/decoder/layout behaviour and artifact properties.
**Unchecked:** phone playback, visual alignment, accessibility and regression acceptance.
[Phone checklist](TESTING.md#c109c111-download-and-browse-alignment--phone-checks-pending).
VersionName stays 1.3.0 and schema 23; no push, merge, tag movement or store release. Later
documentation commits do not relabel the source SHA or versionCode of this APK.

## 1.3.0 — AAB prepared, upload/publication unconfirmed

Release preparation completed on 2026-10-21. Signed bundle
`dist/protracktor-1.3.0-1269.aab`, **22,154,124 bytes**, SHA-256
`3f63164fb1256160696fd374615e7bb21d6be41f029997177c2860eccafec8db`.
Tag `v1.3.0` and master identify `d456fa7`; the script recorded artifact evidence in `9ca624f`.
[Release scope, verification and remaining steps](RELEASE_NOTES_1.3.0.md).

The agent inspected existing release-run logs/XML: **1096 tests / 142 suites**, zero failures,
errors or skips; unit stage **365.3 s**. Lint succeeded using its up-to-date report, with **0
errors / 72 warnings**, not a newly executed analysis. Artifact hash, embedded revision, packaged
engine ABIs and public upload-certificate identity match the release records. JAR verification
reports the same warning categories retained for 1.2.0; neither this nor the tag establishes Play
acceptance. No new build/signing, test run or push was performed by the agent. Queue acceptance
below refers to the earlier APK trial, not installation of a Play-generated 1.3.0 package.

## Audited queue — accepted and merged, 2026-10-21

The trial is accepted after positive phone testing and integration is approved. Merged
`feature/manual-playback-queue` (`9287df5`) into develop as **3bc5a98**, without conflicts;
the merge tree exactly equals the accepted branch. Application/build sources still equal
the verified APK source **1a1503f**; subsequent changes are documentation only. No new APK or
test rerun for this merge. [Acceptance and integration record](QUEUE_BRANCH_REVIEW.md#acceptance-and-integration--2026-10-21).
Overall phone acceptance does not supply individual outcomes for every retained checklist.

C116 corrects whole-list appearance flashes after automatic following. The [complete branch
audit](QUEUE_BRANCH_REVIEW.md) also corrected C117's omitted-history traversal bound and EN/PL
Help wording, adding an Info-through-progress-recomposition guard. All 22 changed production
Kotlin files and both resource files were inspected; no further actionable defect was identified.
Restored focused verification passed **218 tests / 14 suites**, zero failures/errors/skips.
Clean source **1a1503f** passed the forced complete gate in **551 s**: **1096 tests / 142 suites**,
all companion checks and lint **0 errors / 72 unchanged warnings** (records match APK 1258).

Minified test-signed release APK built in **97.9 s**:
[protracktor-release-1.2.0-1261-1a1503f-20261021-005631.apk](../dist/protracktor-release-1.2.0-1261-1a1503f-20261021-005631.apk),
**11,915,043 bytes**. Package/code, v2 test certificate, absence of debuggable flag, executed
R8/nonempty mapping, minSdk 29/targetSdk 36 and all three engine ABIs checked. SHA-256:
`0317cccb20526608a6c8f0cec324254a335ace4a0ad151b34ae1c99d7b5d6e5b`.
[Evidence](QUEUE_BRANCH_REVIEW.md#final-verification-and-apk);
[phone checklist](TESTING.md#queue-follow-and-branch-audit--phone-checks-pending).
**Checked:** forced host gates/artifact properties and overall phone trial acceptance (feedback
reported, not run by the agent). **Unchecked individually:** detailed accessibility, notification,
Bluetooth, gesture/timing measurements and long-term scenarios without separate results.
No push/tag; AMP and master unchanged. C111/C112 remain excluded. Older handoffs below retain
their original evidence; neither integration nor documentation relabels the APK source SHA/code.

## ~~C116 Queue flashes when Next follows below the viewport~~ — merged, 2026-10-21

Phone report after the APK 1258 handoff: Next near the bottom triggers automatic downward
scrolling and a brief whole-list flash. Installed build ID was not separately supplied.
[Investigation and verification](QUEUE_AUTO_FOLLOW.md). The corrected real-Dock pixel test
reproduces a lazy-item animator reset at the final scroll snap. Queue appearance fading is
disabled; disappearance/placement remain animated. Restored verification passed 18 host tests /
4 suites, zero failures/errors/skips. The later [complete branch audit](QUEUE_BRANCH_REVIEW.md)
passed 218 focused tests / 14 suites, including rapid Next, Info during progress updates,
departure/placement, Clear, playlist/manual grips and swipe regressions. Complete gates and
APK 1261 passed as recorded above. Included in the overall accepted integration as **3bc5a98**;
individual checklist outcomes were not separately reported.

## ~~C117 Omitted shuffle history can prematurely disable transport~~ — merged, 2026-10-21

Code-review finding, not a user-reported incident. Repeated in-list selections create more
history entries than source rows. The old omission walk stopped after one list length and
could miss a surviving earlier/later destination. Both pure-model reproductions failed before
correction. PlayQueue now supplies a bounded budget covering history plus a fresh lap;
218-test focused verification passed. [Audit](QUEUE_BRANCH_REVIEW.md). Full gates/APK 1261 passed
as recorded above; included in the overall accepted integration **3bc5a98**. This specific
code-review reproduction has host proof, not a separately reported phone result. No source
order, saved playlist, shuffle or manual FIFO policy change.

## ~~C113–C115 Queue presentation and playlist dragging~~ — merged, 2026-10-21

Included in the accepted candidate merged as **3bc5a98**. The report and pre-acceptance handoff
evidence below retain their original dates/counts; individual phone checks were not enumerated.

Owner phone report after the Clear handoff: clearing adds unwanted left indentation; manual
playing-row highlighting omits that margin; playlist dragging can hide below neighbours, pin a
moved first row to the top or lose pointer ownership. Installed build ID was not separately
reported; latest supplied APK was 1255. [Reports, mechanisms and correction plan](QUEUE_ROW_REGRESSIONS.md).
C113/C114 are implemented and passed 20 host tests / 4 suites; Clear alignment, full-width
highlight and inset-hold regressions failed before correction. C115 is implemented; grip,
viewport, drawing-layer and same-frame stale-index negative probes failed as intended. Final
restored combined verification passed 147 host tests / 14 suites, zero failures/errors/skips.
Clean source **aca3cd6** passed the forced complete gate in **545 s**: **1090 tests / 141 suites**,
zero failures/errors/skips, all companion checks and lint **0 errors / 72 unchanged warnings**
(ID/severity/message/file records match APK 1255).

Minified test-signed release APK built in **104.1 s**:
[protracktor-release-1.2.0-1258-aca3cd6-20261021-001730.apk](../dist/protracktor-release-1.2.0-1258-aca3cd6-20261021-001730.apk),
**11,915,735 bytes**. Package/code, v2 public test certificate, absence of debuggable flag,
executed R8/nonempty mapping, minSdk 29/targetSdk 36 and all three engine ABIs checked. SHA-256:
`f7d0396c6080b908900a536b081a9d5d13b34dd48b2e444bf4fab5ed2777904f`.
[Evidence](QUEUE_ROW_REGRESSIONS.md#final-verification-and-apk);
[phone checklist](TESTING.md#queue-row-and-playlist-drag-corrections--phone-checks-pending).
**Checked partially:** host gestures/layout/rendering and artifact properties. **Unchecked:**
device dragging/viewport, visual comfort, audio, notifications, Bluetooth, accessibility and
long-term use. No merge/push/tag; develop, AMP and master unchanged. C111/C112 remain excluded.
APK 1255 below is historical; documentation-only closeout does not change APK source SHA/code.

## Clear queue — previous handoff, 2026-10-20

Owner approved clearing source continuation and waiting requests while retaining only the actual
current track as a one-shot. Saved playlists remain untouched. [Contract](QUEUE_CLEAR.md).
Domain/controller and UI are implemented. Final combined verification passed 137 host tests / 12 suites;
negative probes caught disconnected Clear, a stuck Random spinner and an actually clipped label.
Review also corrected Undo overriding live Repeat/Shuffle on playlist relinking; its regression
failed before the correction. Another failing regression covered explicit Play after Undo restores
Random before its first pick; that path now draws or explains the lack of candidates. The two
earlier full gates were deliberately interrupted for those additions, not passed.
Clean source **1aec1aa** passed the forced complete gate in **521 s**: **1080 tests / 139 suites**,
zero failures/errors/skips, all companion checks and lint **0 errors / 72 unchanged warnings**
(ID/severity/message/file records compared with APK 1252).

Minified test-signed release APK built in **107.9 s**:
[protracktor-release-1.2.0-1255-1aec1aa-20261020-232555.apk](../dist/protracktor-release-1.2.0-1255-1aec1aa-20261020-232555.apk),
**11,913,319 bytes**. Package/code, v2 public test certificate, absence of debuggable flag,
executed R8/nonempty mapping, minSdk 29/targetSdk 36 and all three engine ABIs checked. SHA-256:
`6fb98c1bfbcaa1df0de966b2299e45a11970b2d388e2068ef7e27f04a97e2606`.
[Evidence](QUEUE_CLEAR.md#final-verification-and-apk);
[phone checklist](TESTING.md#clear-queue--phone-checks-pending).
**Checked partially:** host behaviour and artifact properties. **Unchecked:** device audio,
loading/Random timing, UI/accessibility, notifications, Bluetooth and long-term use. No merge/push/
tag; develop, AMP and master unchanged. C111/C112 remain excluded. APK 1252 below is historical;
documentation-only closeout does not change this APK's source SHA/code.

## Queue swipes steal vertical scrolling — previous handoff, 2026-10-20

Owner report: attempting up/down scrolling can accidentally move the row sideways. Latest
supplied APK was 1250; installed ID not separately reported. The horizontal detector claims
small sideways motion before the scrolling parent. [Scroll-first intent fix](QUEUE_SWIPE_INTENT.md)
is implemented on the same feature branch. Original wobble and abandoned-click regressions
failed before correction; lowering dominance to 1:1 failed the real-list diagonal test.
Restored combined verification passed **98 tests / 9 suites**, zero failures/errors/skips.
Clean source **f6a5ee4** passed the forced full gate in **531 s**: **1056 tests / 137 suites**,
zero failures/errors/skips, all companion checks and lint **0 errors / 72 unchanged warnings**
(issue ID/message/file records compared with APK 1250).

Minified test-signed release APK built in **96.3 s**:
[protracktor-release-1.2.0-1252-f6a5ee4-20261020-223105.apk](../dist/protracktor-release-1.2.0-1252-f6a5ee4-20261020-223105.apk),
**11,907,283 bytes**. Package/code, v2 public test certificate, absence of debuggable flag, executed
R8/nonempty mapping, minSdk 29/targetSdk 36 and all three engine ABIs checked. SHA-256:
`c228e109f54247ffb2957bd44d0da0a307149c561bc4f8e5dc095b4235795024`.
[Evidence](QUEUE_SWIPE_INTENT.md#final-verification-and-apk);
[phone checklist](TESTING.md#scroll-first-row-gestures--phone-checks-pending).
**Checked partially:** host gestures/regression and artifact properties. **Unchecked:** device
gesture comfort, flick/diagonal behaviour, accessibility and playback/service/Bluetooth scenarios.
No merge/push/tag. Develop, AMP and master are unchanged; C111/C112 remain excluded. APK 1250 below
is historical; documentation-only closeout does not change this APK's source SHA/code.
Clear queue was discussion-only at this handoff; the later owner approval is recorded above.

## Queue presentation feedback — previous handoff, 2026-10-20

The owner reports abrupt disappearance of consumed manual rows, missing Info-hold vibration and
an inelegant persistent gesture hint. Installed build ID was not separately supplied; latest
handoff was APK 1248. UI-only animations, explicit Info haptics and on-demand labelled Help are
implemented on the existing feature branch. [Scope and evidence](QUEUE_PRESENTATION_POLISH.md).
Restored focused verification passed 78 tests/7 suites; placement/fade/Help negative probes
failed as intended, and the missing-haptic regression failed before the change. An initial full
gate stopped on an inconsistent single-frame pixel test; the refined sequence test passed its
negative probe and a forced presentation run. Final clean source **6043902** passed the complete
forced gate in **521 s**: **1043 tests / 136 suites**, zero failures/errors/skips, all companion
checks and lint **0 errors / 72 unchanged warnings**, compared by issue/message/file.

Minified test-signed release APK built in **86.6 s**:
[protracktor-release-1.2.0-1250-6043902-20261020-194651.apk](../dist/protracktor-release-1.2.0-1250-6043902-20261020-194651.apk),
**11,907,759 bytes**. Package/code, v2 public test signature, absence of debuggable flag, R8 mapping,
minSdk 29/targetSdk 36 and all three engine ABIs checked. SHA-256:
`809da54fa82bd4d57aae4be5aecfe6d63b269b9061ea49373509cfebb62a999d`.
[Artifact/evidence](QUEUE_PRESENTATION_POLISH.md#final-verification-and-apk);
[phone checklist](TESTING.md#queue-presentation-polish--phone-checks-pending).
**Checked partially:** host behaviour and artifact properties. **Unchecked:** device animation
feel/layout, actual vibration, TalkBack and playback/service/Bluetooth behaviour. No merge/push/tag;
develop, AMP and master are unchanged. C111/C112 and AMP remain outside this task. APK 1248 below
is historical; a later documentation-only commit does not relabel this APK.

## Manual-block reorder and long-press Info — previous handoff, 2026-10-20

Owner selected Info on row long-press and a drag grip only for waiting manual requests inside
their indented block. Current/source rows remain fixed; saved source data and transport stay
unchanged. Continue on the same feature branch in separate commits, without merging.
[Contract/evidence](QUEUE_MANUAL_REORDER.md). Scheduler/controller stage passed 43 focused tests;
UI and bounded/edge-scrolling grips are implemented. The restored combined run passed 79 tests /
7 suites, with failing negative probes for drag, edge scroll and Info. A grip/swipe collision and
semantic grouping were corrected. Clean source `0082064` passed the forced full gate in **507 s**:
**1037 tests / 135 suites**, zero failures/errors/skips, all companion checks and lint
(**0 errors / 72 unchanged warnings**, compared by issue/message/file).

Minified test-signed release APK, built in **123.8 s**:
[protracktor-release-1.2.0-1248-0082064-20261020-185705.apk](../dist/protracktor-release-1.2.0-1248-0082064-20261020-185705.apk),
**11,887,299 bytes**. Package/code, v2 public test signature, absence of debuggable flag, R8 mapping,
minSdk 29/targetSdk 36 and all three engine ABIs checked. SHA-256:
`f3bee93a044085672cb6da0718e16d837a281eb1a8daef5d9c5734fa911ddb72`.
[Report/evidence](QUEUE_MANUAL_REORDER.md#final-verification-and-apk);
[phone checklist](TESTING.md#manual-block-reorder-and-long-press-info--phone-checks-pending).
Checked partially on host; all new phone behaviour is unchecked. APK 1245 below is the previous
handoff, not verification of this extension. No merge/push/tag; develop, AMP and master unchanged.

## Unified queue — third iteration ready for phone testing, unmerged, 2026-10-20

After testing/discussion with app users, the owner selected one combined list, one-shot manual
inserts, context-only Previous, marked duplicate occurrences, session-only source removal,
bidirectional gestures and the existing dock visible below the panel. Continue in separate stage
commits on `feature/manual-playback-queue`; no merge is authorized. Source-switch precedence is
provisionally selected-track-now, retained requests next, then the new source continuation.
[Current contract and evidence](QUEUE_UNIFIED_LIST.md) supersedes the two-tab/mixed-history rules.

Domain/controller stage passed 144 focused tests. Shared gestures and the nonmodal unified panel
are implemented; 31 UI/navigation tests passed, with negative gesture/dock probes. Final review
corrected paused removal, equal Random prune identities, linked-draft ID recycling and manual
consumption on empty Random. The restored final focused run passed 125 tests / 7 suites.
Final source `613b154` passed the forced full gate in **511 s**: **1028 tests / 134 suites**,
zero failures/errors/skips, all companion stages and lint (**0 errors / 72 unchanged warnings**).
The final review also corrected source highlights after pruning a sounding Random occurrence;
its regression failed before correction and the restored focused run passed 58 tests / 4 suites.

Minified test-signed release APK built in **142.8 s**:
[protracktor-release-1.2.0-1245-613b154-20261020-182237.apk](../dist/protracktor-release-1.2.0-1245-613b154-20261020-182237.apk),
**11,878,271 bytes**. Package/code, v2 public test certificate, absence of the debuggable flag,
R8 mapping, minSdk 29/targetSdk 36 and all three engine ABIs checked. Not an AAB/store release.
SHA-256: `08ae9164d7ec925944470f8da21ca1b50ccffba8c2c2711c3e5d2056cfb74b0c`.
[Evidence/review](QUEUE_UNIFIED_LIST.md#final-verification-and-handoff);
[phone checklist](TESTING.md#unified-queue--third-iteration-phone-checks-pending).
Previous APK 1239/results below are historical only. Later documentation commits do not relabel
the source SHA/code of this artifact. Develop, AMP and master are unchanged; no merge/push/tag.
All new phone behaviour is unchecked. AMP, C111 and C112 are outside the implementation scope.

## C112 Dock subsong count crowds Now Playing text

Owner/user feedback, 2026-10-20: the subsong-position label occupies too much of the compact dock
beside the Queue action, reducing room for Now Playing. The latest supplied APK was 1.2.0 (1239);
the installed build ID and exact layout measurements were not separately supplied. **Open and
explicitly deferred** by the owner. Record a future compact presentation discussion; do not bundle
it into the unified-list implementation. Phone report only, no agent-run visual confirmation.

## C111 Add music and Online catalogues have inconsistent padding

User report, 2026-10-19: switching between the Add music (Browse root) and Online catalogues
views visibly changes content padding. Expected: consistent alignment and spacing through that
transition. Exact edges/measurements and installed build ID were not independently supplied;
the latest handoff was C110 APK 1.2.0 (1231). This was not included in the manual queue work.

**Candidate, 2026-10-21:** `fix/browse-padding` starts at C109's `91ba854`, as agreed for one shared
test APK. The common outer insets are unchanged. Root rows had 28 dp icons, while online Material
rows used their 24 dp defaults: text began at 60 versus 56 dp from the content edge. The agent's
rendered Compose regression measured the 4 dp difference and failed before correction (one
failure; the other test was skipped by fail-fast). A shared 28 dp leading-icon size now aligns
missing, indexed and online-only catalogues, Song metadata and Replay routines. Both layout tests
passed after correction, including 1.3x text and downloaded-state changes with nonzero outer insets.
Root's fixed 78 dp rows and online rows' adaptive heights, wrapping and actions are retained.

**Checked partially:** host-rendered geometry, not phone appearance or full visual acceptance.
The combined source passed the 1106-test full gate and lint; shared release APK **1.3.0 (1273)**
is recorded above. Device checks and merge approval remain pending.
[Phone checklist](TESTING.md#c109c111-download-and-browse-alignment--phone-checks-pending).

## ~~C110 Browse restores the playing collection instead of its root~~ — accepted and merged, 2026-10-19

The owner accepted the correction and requested integration. Merged as `7e7f242`; its complete
tree equals branch checkpoint `57a9d65`. No repeat build/gate is claimed for the merge; application
sources equal tested source `fa09e7a`. Individual checklist/device/build details were not separately
reported. The original handoff evidence below remains historical; no push or store release.

Owner report, 2026-10-19: play History, Back to Browse, Back to the playlist, press Browse.
Observed: History reopens. Expected: the main Browse view opens, with music/context unchanged.
Exact device and installed build ID were not independently supplied; the last handoff was the
normal-code Queue APK 1.2.0 (1228). The owner reported that the app started after that handoff;
this is launch evidence, not a completed detailed regression matrix.

**Mechanism:** the former `ProtracktorApp.openBrowse` first called
`returnToSession()`. That restored the snapshot saved by `playFromResults()` whenever
`searchMode` is true; that property covers every `QueueSource.Collection`, not just Search.
The same restore path covers History, Search, online authors and local folders. Random has a
different path; its earlier Back defect is not assumed to be the same mechanism.

**At the original handoff, before the acceptance above:** the actual shell regression
failed before the correction and passed afterwards with preset playback, not decoded audio.
Generic Browse now opens ROOT independently of its playing source. Explicit destination events
and one-level Back remain separate. Clean source `fa09e7a` passed the forced full gate in **498 s**:
**963 tests / 129 suites**, zero failures/errors/skips; all companion stages passed. Lint:
**0 errors / 72 unchanged warnings**. Minified release APK built in **90.8 s**:
`dist/protracktor-release-1.2.0-1231-fa09e7a-20261019-231016.apk`, **11,831,347 bytes**.
Package/code, v2 test signature, absence of the debuggable flag, R8 and three engine ABIs verified.
Phone behaviour remains **unchecked**; no merge/push. Develop, AMP and master are untouched.
[Implementation, hashes and evidence](https://github.com/przunk/protracktor/blob/2c35319cfa8b665398a36141248b45544bd68ebe/docs/PR_C110_BROWSE_ROOT_NAVIGATION.md);
[complete queue discussion](QUEUE_CONTEXT_ANALYSIS.md). No manual queue or AMP change is included.

## Manual queue contract audited — implementation authorized, 2026-10-19

The owner requested an analysis audit and implementation on a separate branch, delegating open
choices to the agent without blocking questions. [The revised contract](QUEUE_CONTEXT_ANALYSIS.md)
records gaps/inconsistencies, a full transition table, provisional choices and questions for later
evaluation. FIFO/preserved forward traversal, current-versus-context identity, subsongs, repeat,
failure/retry, source edits, lifetime, two tabs and pending swipe/Remove/Undo are now explicit.
Unified view, reordering/Clear, persistence, web queue and AMP remain excluded. This checkpoint
changes documentation only; implementation starts separately from accepted develop. No new
application verification, feature APK or feature merge is claimed by this audit.

Implementation has started on `feature/manual-playback-queue` from `d6d3297`. The first checkpoint
adds a pure scheduler with a failing FIFO mutation probe. The next checkpoint integrates the
controller/actual-file projection and passes 93 focused tests, including real-controller failure
and stale-retry cases; negative Random-anchor and manual-failure probes detect broken rules.
UI wiring now includes two tabs, shared row actions, quiet context markers and pending swipe/Remove/
Undo inside the modal. The final focused run passed 63 tests / 6 suites, zero failures/errors/skips,
after correcting an existing reactive Random test's snapshot/clock synchronization. Idle/no-source and
paused-resume boundaries are explicit. [Stage evidence](MANUAL_QUEUE_IMPLEMENTATION.md).
The first clean-source forced gate passed **1011 tests / 133 suites**, zero failures/errors/skips,
all companion stages and lint (**0 errors / 72 warnings**) in 525 s. Minified test-signed APK
**1.2.0 (1238)** from `783d8fa` built and verified; it is an **intermediate artifact**, not the test
candidate. The owner requested a requirements/all-commits audit after it and a corrected,
reverified APK. [Second iteration](MANUAL_QUEUE_SECOND_ITERATION.md) records five corrected findings:
one production History policy, visible Undo restoration checks, cached title projection with full
keys, accurate ownership documentation and awaited test-fixture teardown.

**Second iteration completed:** clean source `3d27868` passed the **forced** full gate in **481 s**:
**1013 tests / 133 suites**, zero failures/errors/skips, all companion stages; lint **0 errors /
72 warnings** (81.9 s). Two negative probes fail under old History policy/incomplete title keys;
the restored corrective focused run passed 89 tests. Minified release APK built in **98.8 s**:
`dist/protracktor-release-1.2.0-1239-3d27868-20261020-001402.apk`, **11,881,447 bytes**.
Package/code, v2 public test signature, absent debuggable flag, R8 mapping and all three engine
ABIs checked. Exact hashes/evidence in the second-iteration report. This, not the first APK, is
the candidate for owner testing. App behaviour is checked partially on the host; all phone
behaviour is **unchecked**. Unmerged; develop, AMP and master unchanged. No push/tag/store release.

## B45 accepted and merged — normal versioning restored, 2026-10-19

The owner confirmed that Queue behaves as intended and authorized merging it into develop before
the next iteration. The subsequently supplied colleague/owner feedback is recorded in
[the follow-up analysis](QUEUE_CONTEXT_ANALYSIS.md); it does not reopen the accepted baseline.
AMP work/integration is deferred until after that iteration, with its branch untouched.

Pre-merge cleanup restores commit-derived versionCode for every build and removes the two unused
search-cover strings. VersionName remains 1.2.0 until a release procedure. Existing code-1221 APKs
remain historical; installing a new higher-code APK ends ordinary replacement by the older AMP APK.
Do not uninstall or clear data to bypass the version check. Schema 23 and retained AMP data guards
are unchanged. No playback or UI behaviour is modified by this cleanup.

**Checked:** clean source `069b238` passed the forced full gate in **623 s**, **956 tests / 128
suites**, zero failures/errors/skips; unit stage **423.8 s**, lint **97.0 s**, **0 errors / 72
warnings** (the retired heading warning is gone). All companion stages passed. The normal debug
manifest reports 1.2.0/code 1227, matching that source's commit count.

Merged as `7cab210`; its entire tree equals the tested branch checkpoint. Release APK built from
that clean merge in **98 s**:
`dist/protracktor-release-1.2.0-1228-7cab210-20261019-220200.apk`, **11,829,987 bytes**.
APK v2 signature, package/name/code, absence of the debuggable flag, R8 execution/nonempty mapping
and three engine ABIs were verified. Code **1228** equals the merge's commit count, including on
the test-signing path; versionName remains **1.2.0**, not a new store release. Exact hashes and
verification evidence are in [the report](QUEUE_EXPERIMENT.md).

The general positive phone report is owner acceptance, not a separately reported result for every
device checklist item. The owner later reported startup after the normal-code handoff, without
independently supplying its installed build ID or the detailed checklist. Further work starts
on a new task branch from develop, not on the integrated experiment branch.
AMP remains `8e92002`, master `794d8f9`; no push, tag movement or store release occurred.
Subsequent documentation commits do not relabel this artifact or its tested source.

## B45 follow-up — four owner-reported UI refinements, 2026-10-19

The owner tried the initial QUEUE build and reported a promising overall result, not merge approval.
Queue is now a separate far-right dock action outside the expandable player card. Both Random
Back paths return to Browse; the system handler follows visible-screen priority so an author or
Settings over remembered Random closes first. The direct playlist shortcut remains unchanged.
The dice hint names its double-tap target in both languages. Each fresh Queue sheet starts at the
current row, at the top where possible; later playback changes retain shared list following.

**Checked:** clean source `bc32bea`, forced full gate **535 s**, **956 tests / 128 suites**, zero
failures/errors/skips; all companion checks passed. Lint: **0 errors / 73 unchanged warnings**.
Minified release APK built in **107 s**:
`dist/protracktor-release-1.2.0-1221-bc32bea-20261019-002459.apk`, **11,830,331 bytes**.
APK v2 signature, package/name/code, absence of the debuggable flag, R8 mapping and three engine
ABIs were verified. Public test certificate matches the AMP reference. Regression tests detect the
old card boundary/initial scroll and a deliberately incorrect Back priority; eight focused cases
passed before the full gate. Four updated documents have 78 valid local links.

Phone acceptance of these refinements remains **unchecked**. Version name 1.2.0/test code 1221,
schema 23 and the AMP permission hold are unchanged. No merge or push authorized. The previous
build below remains historical evidence; [new artifact hashes and test details](QUEUE_EXPERIMENT.md).

## B45 QUEUE experiment — unmerged, ready for owner phone testing, 2026-10-18

Owner-selected isolated `feature/playback-queue-experiment` from develop `9ca86ea`.
One source-aware queue owns transport; playlist selection and navigation do not interrupt music.
The read-only queue sheet opens fully expanded. Random keeps its record when reopened, first entry
starts as before, and double-tapping the animated dice starts a new list; single tap has no action.
The experiment may be rejected; no merge, push or release is authorized.

The owner approved code 1221 only for this branch's test APKs to alternate with the existing AMP
APK. Schema remains 23. Native SQLite tests confirm AMP index/playlist/History/bookmark retention,
with retained AMP rows excluded from discovery; this branch does not implement AMP playback.
**Checked:** clean source `da75839`, forced full gate **510 s**, **953 tests / 128 suites**,
zero failures/errors/skips; all companion checks passed. Lint: **0 errors / 73 warnings** (one
new unused legacy search-cover heading). Minified, non-debuggable, test-signed APK:
`dist/protracktor-release-1.2.0-1221-da75839-20261018-235148.apk`, **11,828,663 bytes**.
Its package/name/code/certificate match the reference AMP APK; APK v2, three engine ABIs, packaged
source/privacy assets and R8 mapping were verified. Exact hashes and failed/reproduced/corrected
intermediate checkpoints are recorded in the experiment report.

**Unchecked:** all phone behaviour and actual AMP -> QUEUE -> AMP interchange. Temporary queues
remain memory-only; process death restores the selected playlist paused, as before. No merge,
push, tag change or store release occurred. Later documentation does not relabel this APK.
Remove the temporary version override and unused legacy heading before an accepted merge.
[Full contract, decisions and evidence](QUEUE_EXPERIMENT.md); [device checklist](TESTING.md).

## C109 — Same Game SPC cannot be played — owner report, 2026-10-18

The owner could not play the Modland entry shown as
`/modland/nintendo SPC/- unknown/Same Game (same game (22).spc)` during listening tests.
Keep this as the reported display path, not a confirmed archive URL or filename.
Expected: selecting the entry starts playback. The owner clarified that the app reported a
download failure with a connection suggestion, and that other tracks have shown this behaviour
too; those additional identities were initially unspecified. Exact installed build and device remain unspecified.
SPC is already handled by the existing console decoder; this report does not establish a
format-wide failure, an AMP regression or a queue bug.

**Initial inspection, 2026-10-18:** non-2xx responses and connection exceptions both reached
`open_failed_not_fetched`, before decoder opening. No HTTP response had yet been measured.

**2026-10-21 user follow-up:** `YM/- unknown/conflict europe.ym` has the same phone symptom.
The agent confirmed both legacy paths return **HTTP 404**, while the current official index
and server use `_unknown`: YM returns **200 / 5,333 bytes**, SPC **200 / 66,048 bytes**.
Both downloaded files opened and rendered non-silent PCM in the host WASM probe. The mismatch
explains the supplied examples; the dates of the archive change and the phone's stored index
were not measured. No format-wide or connection-wide failure is established.

**Candidate on `fix/modland-download-failures`:** one request-only recovery after an exact
Modland legacy-author 404, retaining saved IDs and cache keys. Before-fix regressions failed
for both examples; **36 focused checks passed** after correction. The separate stacked C111
candidate passed the **1106-test full gate and lint**; shared APK **1.3.0 (1273)** is recorded
above. Phone behaviour and merge approval are pending.
[Mechanism, bounded scope and verification](MODLAND_LEGACY_DOWNLOADS.md).
This is separate from the B45 queue discussion and from the deferred SPC tempo report C26.

## Browse/download stability — merged 2026-10-18; phone testing pending

Owner-selected bounded correctness pass on `fix/browse-download-stability`, from develop `59ba578`.
Merged into develop at `e9a7a98` on the owner's explicit instruction before phone acceptance.
Four stages cover navigation visit ownership, Back publication order, read-ahead
retirement and disk-cache transactions. These are retained review findings, not new phone reports.
The initial five correctness regressions failed before the fixes; 47 navigation-focused checks
passed. A clean serial cache reproduction executes three checks with two expected assertion
failures on the old extracted algorithm. Combined serial focused checks: **65 passed** (110.1 s).
**Final clean source `f32e6cc`:** forced full gate passed in **468 s**, **929 tests / 124 suites**,
zero failures/errors/skips; lint **zero errors / 72 existing warnings**. All project companion
checks passed. Minified, test-signed release APK **1.2.0 (1214)**:
`dist/protracktor-release-1.2.0-1214-f32e6cc-20261016-003937.apk`, 11,830,407 bytes;
APK signature, package/version and three ABIs verified. SHA-256 and public test-certificate identity
are in the report. The earlier overlapping diagnostics had result-file errors and are not counted
as successful verification; two interrupted preliminary full runs are also excluded.
Phone behaviour remains **unchecked**. Subsequent handoff documentation does not change the APK's
fixed versionCode/source. **Checked integration:** the merge tree equals branch `1af5a72`; app,
native, web and scripts match verified source `f32e6cc`. This is a Git comparison, not a fresh
929-test run or a phone check. No new APK, push, master/tag movement, store release or migration.
[Plan](PLAN_BROWSE_STABILITY.md), [analysis and verification](https://github.com/przunk/protracktor/blob/2c35319cfa8b665398a36141248b45544bd68ebe/docs/PR_BROWSE_DOWNLOAD_STABILITY.md).

## Release 1.2.0 — AAB prepared 2026-10-15, verified 2026-10-16

The owner completed `release.sh 1.2.0`. Master and annotated `v1.2.0` point to release commit
`794d8f9` (1207 commits); develop includes the script-generated evidence commit `53c0424`.
Prepared artifact: `dist/protracktor-1.2.0-1207.aab`, **22,033,917 bytes**. Its SHA-256 matches
the owner's release output and the immutable [release ledger](../store/play-console/releases.md).
The bundle's embedded source revision also matches the full tagged commit; the public upload
certificate fingerprint matches the ledger and 1.1.0. No private key/keystore was read.

**Checked:** artifact size/hash, embedded revision, tag/branch relationship, public signer and
JAR signature verification. `jarsigner` reports verification with warnings; details and scope
are recorded in [the full 1.2.0 notes](RELEASE_NOTES_1.2.0.md). The existing owner-run release logs
show executed unit tests (320.5 s) and successful lint (98.1 s); XML confirms **910 tests**,
zero failures/errors/skips, and lint zero errors/**72 warnings**. Runner/release-flow logs confirm
fourteen checks and eleven disposable Git scenarios. These are inspected release-run results,
not tests rerun by the agent or device testing of this AAB.

This release includes stereo mix modes, the notification playlist heart, recent-search suggestions,
live History search, Bluetooth output recovery, playlist/search ordering safeguards, History entry
scroll correction and newer-database preservation. The full notes distinguish user feedback from
code-review findings and retain open device/Compose limitations. PL/EN Play notes are at the tag.
**Unconfirmed:** upload/acceptance in Play Console, publication and a phone trial of this
exact store artifact. The preparation evidence does not establish any of those states. No rebuild,
tag movement, additional release, upload or push was performed for this documentation closeout.
Follow-up 2026-10-16: a public `git ls-remote --tags` read confirms both v1.1.0 and v1.2.0,
including their peeled source revisions, match local tags. Tags are separate from GitHub Releases.

## Concise check output — owner approved and merged, 2026-10-15

After an owner-run successful `--check` (910 tests, 556 s), the owner requested short terminal
output again. `fix/concise-check-output` changes presentation only: stage start/result and
30-second elapsed notices remain visible; successful child output stays in complete stage logs.
Failure/timeout prints a bounded tail (40 lines from the last 8 KiB), with the complete log path.
No application/build configuration, test isolation, force policy, deadline or cleanup changes.
**Checked before correction:** all three console-noise regressions fail on the original helper.
**Checked focused:** all fourteen runner/entry-point checks pass, including 2,000-line success
and failure fixtures, retained Unicode/full logs, timeout, late Ctrl+C and process cleanup.
All eleven disposable release scenarios, Bash syntax and whitespace checks also pass. A direct
preview shows two console notices and retains 2,000 child lines in the complete log. Application,
build configuration, native/web and release-script sources match develop `e750591`. No new
application test/lint run or APK is claimed for this output-only tooling change; the earlier
910-test gates remain historical evidence, not a new execution. Owner-approved merge `420caed`
has the same complete tracked tree as checked branch `6d1311b`; verified by Git tree comparison,
not another test run. The following closeout changes documentation only. No release, APK or push;
master, tags and remote refs are unchanged.
[Report](https://github.com/przunk/protracktor/blob/2c35319cfa8b665398a36141248b45544bd68ebe/docs/PR_RELEASE_TEST_RUNNER.md).

## Release test runner — owner approved and merged, 2026-10-15

The owner paused release preparation after two Settings Compose idle failures during the ordinary
`release.sh 1.2.0 --check` path. At report time the scripts had no test deadline or live Gradle
progress; the earlier successful 910-test gate used temporary worker isolation absent from that path.
The original `fix/release-test-runner` merge introduced class isolation, live test output, elapsed messages,
per-stage deadlines (unit default 20 minutes), scoped process cleanup and preserved failure codes.
Release validation forces test execution; typo options and false/empty JUnit success are refused.
Generated Kotlin session files are ignored without deletion. No application/engine/UI changes.
**Checked before correction:** the real script ignores a one-second limit in a stuck-Gradle
fixture; its external five-second watchdog fails the single regression and cleans up its own
processes. Subsequent lint-status and cleanup-window cancellation regressions also fail before
correction. **Checked final gate:** ordinary `test-protracktor.sh --really`, no external init script,
**547 s, 910 tests/121 suites**, zero failures/errors/skips; lint zero errors/**72 warnings**
(unchanged). Twelve runner checks, eleven disposable Git release scenarios, PL/EN/store,
581 page, 49 engine, eight SAP, browser upgrade, 30 native audio, 140 icon paths, PWA and eleven
local-server checks pass. Three injected faults in a supervisor copy are rejected. A real offline single-use
Gradle hang terminates at 15 s with 124 and its PID is gone; unrelated process sentinel survives.
App/build inputs match the checked source; final cancellation-only refinement is covered by the
same run's twelve runner checks. Evidence and exact source chronology are in the linked report.
The underlying shared-worker Compose idle failure is **not diagnosed**; isolation is containment.
The owner authorized integration; merged into develop at `6736cd0`. Its complete tracked tree
matches approved branch `6ec0a3a`, and script/build SHA-256 checks match the verified sources.
This is a Git tree/checksum verification, not a fresh execution of the 547-second gate. The
following closeout is documentation-only. No application APK/AAB, phone test, release or push was
performed; tooling verification does not establish app/device behaviour. Master remains at
`v1.1.0` (`da4569b`); all tags and remote refs are unchanged, and `v1.2.0` is absent.
[Analysis and verification](https://github.com/przunk/protracktor/blob/2c35319cfa8b665398a36141248b45544bd68ebe/docs/PR_RELEASE_TEST_RUNNER.md).

## Release-version history reconciliation — completed, 2026-10-15

The owner approved correcting unpublished test-version bumps and the project AGENTS version rule.
Scope: 63 commits after immutable `v1.1.0`, original develop `16bd60d`. Candidate `eca4c8d`
normalizes only `app/build.gradle.kts`'s versionName to 1.1.0. All 63 tree/metadata/ordered-parent
pairs pass verification; 55 hashes change, eight remain identical. The graph retains 1193 commits.
Three negative checks reject an unremoved trial version, unrelated README edit and reordered merge
parents. Current application source differs only in versionName; schema, signing and runtime code
are unchanged.
**Checked full gate:** clean candidate `912122c`, forced run in 485 s: **910 tests/121 suites**,
zero failures/errors/skips; lint zero errors/72 warnings (unchanged). PL/EN resources/store,
581 page checks, 49 engine checks, eight SAP checks, browser upgrade, 30 native audio checks,
140 icon paths, PWA and 11 local server checks passed. No exclusions or toolchain changes.
Backup recovery/import/fsck also passed with original count/tag/develop; all saved artifact hashes
match, and a second direct GitHub read confirms the unchanged publication boundary.
Evidence: ignored `dist/version-cleanup/`. No new APK or release is prepared here.
The original graph is retained in an archive ref and a verified Git bundle. Guarded ref promotion
succeeded at `f0ca1a9`: nine local refs moved, one affected identity ref remained unchanged.
Develop and completed scoped task branches now use the rewritten graph. Master, tags, remote refs
and other worktrees are untouched. The rewrite itself retained 1193 commits; the three ordinary
audit/documentation commits bring the final count to 1196. Application/build source still matches
tested `912122c`; later commits are documentation-only. No new APK/AAB, push or release.
[Procedure and commit map](VERSION_HISTORY_REWRITE.md).

All dated APK, test and integration records below retain their original numbers and SHA values.
They describe the original built/tested history, preserved in the archive. Do not interpret those
old hashes as the rewritten branch tips or rename an APK to match rewritten source. Use the map.

## B49 live History search — owner accepted and merged, 2026-10-15

The owner reported satisfactory operation after the 1.4.0 trial handoff and authorized merging.
Merged at `ef8cd39`; its tracked tree matches accepted branch `1820c9b`, and application/build
sources match offered APK `e4f5ccc` and full-gate implementation `27bd9fa`. Verified by Git tree
comparison, not a new test/lint run. No rebuild was needed for unchanged application sources.
Device/Android, exact installed build and individual checklist outcomes were not separately
supplied. This is overall owner acceptance, not agent-run phone testing or measured phone latency.
Earlier pending checkpoints below are superseded by this acceptance. Master/tags/store are
unchanged; nothing was pushed or published. C108 remains a separate task.

### Pre-acceptance verification record

Owner selected `feature/history-live-search` from develop `5954c86`: live literal substring
filtering across all History by title/author/filename, case-insensitive, with 200 ms debounce,
paging/counts and unchanged playback. Trial version 1.4.0; schema stays 23. A bounded cursor scan
supports Unicode case matching without an upgrade. Input stays mounted while a query-keyed result
subtree resets scroll/follow; page switches preserve positions. No-match, empty and failed reads
have distinct feedback, with labelled retry. Playback/History recording/general Search are unchanged.
**Checked:** storage/schema/qualification gate 44 tests; storage mutation fails all six new checks.
Initial integration had 16 UI class-load failures out of 34 (`ClassFormatError`); changing keyed
early exits to `when` fixes class loading without JDK/dependency changes. Retry passed 34 checks.
Generation/field-callback mutation fails two of nine checks; restored files match SHA-256 sums.
Final restored focused gate: **35 tests in six suites**, zero failures/errors/skips, 109 s, including
actual EN/PL History UI and ordinary Search. Host/native SQLite read 10,000 rows/5,000 matches in
168 ms in the initial storage run; phone latency is **not checked**.
**Checked full gate:** forced run on `27bd9fa` passed in 457 s: **897 tests in 119 suites**, zero
failures/errors/skips. Lint: zero errors/72 warnings (unchanged). PL/EN resource/store checks,
581 page checks, 49 WASM engine checks, eight SAP checks, browser database upgrade, 30 native
audio checks, 140 icon paths, PWA and 11 loopback server checks passed. Existing JDK and temporary
per-class test isolation; no exclusions or repository build-setting changes.
**Checked artifact:** clean source `e4f5ccc`, **1.4.0 (1185)**, built in 161 s:
`dist/protracktor-release-1.4.0-1185-e4f5ccc-20261015-201306.apk`, 11,817,367 bytes.
SHA-256: `5170532e9363d331c5523b72812536d6f48ced2bc380106868e61a361e01518a`.
R8 executed; manifest is non-debuggable, minSdk 29/targetSdk 36; all three existing ABIs remain.
Independent APK v2 verification confirms unchanged test certificate SHA-256
`f97af50c2d869a2591dd82d24555894a2cc95927afb6c0a1fcd4bd197221e47e`.
New History resources remain in the APK. App/native/scripts/web/build sources match full-gate
commit `27bd9fa`; later checkpoints are docs-only. Evidence: ignored `dist/b49-*`.
**Not checked:** phone behaviour, timing and owner acceptance. Branch is unmerged; develop/master,
tags and store are unchanged. No push or AAB build. Earlier APKs are preserved.
[Plan](PLAN_HISTORY_SEARCH.md). Play release is a later owner-driven step, not started here.

## ~~C108 — destructive database downgrade~~ — owner approved and merged, 2026-10-15

After the 1.4.1 trial handoff the owner authorized integration. Merged at `8f3ef56`; its tracked
tree matches approved branch `83468f4`. App/native/scripts/web/build sources match offered APK
`2fe0311` and full-gate implementation `a7bd098`. Checked by Git tree/source comparison, not a new
test/lint run. The recorded gate remains 910 tests/121 suites, lint zero errors/72 warnings.
No APK rebuild was needed for unchanged application sources. Device/Android, exact installed
build and individual scenario results were not separately supplied. Integration approval is not
agent-run phone testing or evidence of exercising the newer-schema refusal on a phone.
Earlier pending checkpoints below are historical and superseded for integration approval only.
Master/tags/store/schema are unchanged; no push, publication or AAB build.

### Pre-integration report and verification

Recorded 2026-10-15 from `ProtracktorDatabase.onDowngrade`: the original helper dropped/recreated user tables.
This contradicts the workspace's data-preserving downgrade policy. No phone reproduction, affected
device count or current data loss is claimed. B49 changes no schema and does not alter or trigger
this path. Not independent user feedback.
After B49 integration the owner selected a separate correction on `fix/preserve-newer-database`
from develop `f41a5fa`. Real-helper native SQLite reproduction, after isolating the process
singleton per synthetic application directory: both checks fail on the old implementation,
zero errors/skips, 94 s. An earlier attempt failed compiling a test expression (73 s); the first
executable run failed one of two checks but the other used a stale singleton context, so it is not
data-preservation evidence. The corrected fixture demonstrates both missing refusal and a changed
database version breaking compatible reopen. No owner data or older APK was used.
Implementation now refuses downgrade and adds read-only startup/service preflight with PL/EN
feedback. Focused gate passed **51 checks in four suites**, zero failures/errors/skips, 125 s:
seven native real-file/helper, seven actual entry/UI/service, three preparation and 34 existing
schema checks. A test import first prevented compilation (45 s); the first executable gate failed
two of 51 checks (136 s): the WAL fixture used an execution API for a result-returning PRAGMA, and
the retry test waited on a non-Compose counter instead of the rendered result. Correcting those
test paths passed without further production changes. Controlled no-op-downgrade/disconnected-Retry
mutation fails all three selected checks (163 s); both files restored and SHA-256 verified.
Trial version 1.4.1; the restored full gate follows.
First forced full run on `9e61064`: 910 tests/121 suites, one failure, zero errors/skips,
298 s for Gradle. The numeric-resource convention test rejected ungrouped schema-number
placeholders; corrected both EN/PL strings. All 909 other checks, including restored C108, passed.
Lint/auxiliary checks were not reached on that first attempt.
**Checked restored full gate:** corrected source `a7bd098`, 463 s, **910 tests/121 suites**,
zero failures/errors/skips; lint zero errors/72 warnings (unchanged). PL/EN resources/store,
581 page checks, 49 WASM engine checks, eight SAP checks, browser database upgrade, 30 native
audio checks, 140 icon paths, PWA and 11 loopback server checks passed. Same JDK and temporary
per-class isolation, no exclusions or repository build-setting changes. Evidence: ignored
`dist/c108-full-gate*`.
**Checked artifact:** clean source `2fe0311`, **1.4.1 (1190)**, 151 s:
`dist/protracktor-release-1.4.1-1190-2fe0311-20261015-213009.apk`, 11,823,499 bytes.
SHA-256: `5322d6bf815a130305b51eefc9122df572faeb90027310a2b32fdbc1713213f3`.
R8 executed; non-debuggable manifest, minSdk 29/targetSdk 36 and all three existing ABIs checked.
Independent APK v2 verification confirms unchanged test certificate SHA-256
`f97af50c2d869a2591dd82d24555894a2cc95927afb6c0a1fcd4bd197221e47e`.
All five compatibility resources remain. App/native/scripts/web/build sources match full-gate
`a7bd098`; later checkpoints are docs-only. Evidence: ignored `dist/c108-release*` / `dist/c108-apk-*`.
**Not checked:** normal operation and refusal UI/timing on a phone; owner acceptance remains pending.
Native SQLite/Activity/service checks are host evidence only. Branch unmerged; develop remains at
the accepted B49 closeout `f41a5fa`. Master/tags/store untouched, no push/AAB. Schema stays 23;
no reset or migration. Previous APKs preserved. [Analysis and verification](https://github.com/przunk/protracktor/blob/2c35319cfa8b665398a36141248b45544bd68ebe/docs/PR_C108_PRESERVE_NEWER_DATABASE.md).

## C105–C107 — owner accepted and merged, 2026-10-15

The owner gave overall acceptance and authorized merging after the combined 1.3.3 trial handoff.
C105 was merged first at `557acbb`, then C106/C107 at `d74d339`. The resulting tracked tree matches
the accepted branch `96a7298`; app/native/scripts also match the offered clean-source APK
`ffdbd87` and full-gate implementation `ccf85a0`. This was checked with Git tree/source comparison,
not a new test/lint run. The recorded gate remains 876 tests/116 suites, lint 0 errors/72 warnings.
No new APK was needed for tree-identical integration and this documentation-only closeout.

Device/Android, exact installed build and individual checklist outcomes were not separately
supplied. This is overall owner acceptance, not agent-run phone testing or confirmation of every
scenario. Dated pending checkpoints below are historical and superseded by this acceptance.
Master, tags, schema and store state are unchanged; nothing was pushed. B49 History search is
tracked separately in [Wishlist](WISHLIST.md#b49-search-in-history--owner-accepted-and-merged-2026-10-15).

## ~~C107 History entry scroll~~ — pre-acceptance handoff, 2026-10-15

The owner observed History opening below the top and requested that every new entry start at
the top. Exact installed build/device were not separately supplied. History resets the data page
to zero but reuses the Browse session's cached `history/0` list position; the scroll code predates
C106. Scope: reset per new visit, retain page positions within that visit and leave other domains
unchanged. The owner explicitly selected the existing `fix/search-request-ownership` branch.
**Checked before correction:** all three real Browse Compose regressions execute and fail by
assertion at the missing top row after reopening, zero errors/skips, 229 s cold Gradle run.
Paging assertions before the new visit pass. The cache-only correction then passed 67 focused
checks, but a fourth UI check exposed a simultaneous playing-key change overriding the new visit
(one of four failed). The final correction keys the History subtree by an in-memory visit counter,
resetting its scroll cache and treating the playing row as arrival rather than a track change.
Other Browse domains retain their cache; page/row/loading updates within the visit do not reset it.
**Checked final focused run:** 68 tests in six suites, zero failures/errors/skips, 119 s.
The trial declares 1.3.3; schema/versionCode policy are unchanged.
**Checked full gate:** forced run on `ccf85a0` passed in 464 s: **876 tests in 116 suites**, zero
failures/errors/skips. Lint: zero errors/72 warnings (unchanged). PL/EN resource/store checks,
581 page checks, 49 WASM engine checks, eight SAP checks, browser database upgrade, 30 native
audio checks, 140 icon paths, PWA and 11 loopback server checks passed. Existing JDK, temporary
per-class isolation (one worker/1 GiB heap), no exclusions or repository build-setting changes.
Evidence: ignored `dist/c107-full-gate*`.
**Checked artifact:** clean source `ffdbd87`, **1.3.3 (1178)**, built in 262 s:
`dist/protracktor-release-1.3.3-1178-ffdbd87-20261015-191612.apk`, 11,807,383 bytes.
SHA-256: `0ce11599c8378f5444958d80159dac146a4056eb0fd34f447a90281917207612`.
R8 executed; non-debuggable manifest, minSdk 29/targetSdk 36 and the three existing ABIs checked.
Independent APK v2 verification confirms the unchanged test certificate SHA-256
`f97af50c2d869a2591dd82d24555894a2cc95927afb6c0a1fcd4bd197221e47e`.
Build/artifact evidence: ignored `dist/c107-release*` / `dist/c107-apk-*`. App/native/scripts match
the full-gate source `ccf85a0`; later handoff is docs-only. This combined trial includes C105/C106
and supersedes 1.3.2 for current testing without deleting or altering previous APKs/branches.
No phone retest, merge or push is claimed. [Analysis](https://github.com/przunk/protracktor/blob/2c35319cfa8b665398a36141248b45544bd68ebe/docs/PR_C107_HISTORY_ENTRY_SCROLL.md).

## ~~C106 search request ownership~~ — pre-acceptance handoff, 2026-10-15

**Source:** the 2026-10-14 code review R2, not independent user feedback or a new B46 regression.
The owner selected repair on `fix/search-request-ownership`, explicitly stacked from C105
`ad9ec54` while its existing APK is being tested. C105 is unchanged and remains unmerged.
The inherited defect lets an older search publish into newer Browse contexts, deliver obsolete
live-source notices or leave loading stuck after cancellation/failure. The trial declares 1.3.2,
without changing schema or versionCode calculation. Request generation, owner and input/domain
guards now control rows, live notices, failures and loading cleanup. Navigation and input edits
retire ownership before cancellation; typing/filter changes still require explicit submission.

**Checked before correction:** 36 workflow checks executed, 14 assertion failures, zero errors/skips
(ten added and four adjusted characterizations). Non-cancellable controlled reads demonstrate why
cancelling a job alone is insufficient. The cold run completed in 176 s; bootstrap/cache failure
before that run is not counted as test evidence. Initial focused checks passed 101 tests in four
suites (36 workflow, 11 search, six autocomplete UI, 48 C105 session), zero failures/errors/skips.
Three more workflow checks cover identical-input resubmission, no-op edits and snapshot restoration.
Bypassing ownership and the no-op-query guard fails 12/39 checks; restored sources match saved
SHA-256 sums. Restored focused run passed **104 tests in four suites**, zero failures/errors/skips
(39 workflow, 11 search, six autocomplete UI, 48 C105 session), in 117 s.
**Checked full gate:** forced run on `cd387d7` passed in 446 s: **872 unit tests in 115 suites**,
zero failures/errors/skips. Lint: zero errors/72 warnings (unchanged). PL/EN resource/store checks,
581 page checks, 49 WASM engine checks, eight SAP checks, browser database upgrade, 30 native
audio checks, 140 icon paths, PWA and 11 loopback server checks passed. Existing JDK and temporary
per-class isolation (one worker/1 GiB heap); no exclusions or repository build-setting changes.
Evidence: ignored `dist/c106-full-gate*`.
**Checked artifact:** clean source `a4b0a6f`, **1.3.2 (1174)**, built in 808 s after rebuilding the
three native ABIs: `dist/protracktor-release-1.3.2-1174-a4b0a6f-20261015-011618.apk`,
11,806,903 bytes. SHA-256:
`e6b5636038528bdea3572b676d25951f21a6996c6aa867a74bf504afbd2f2dfd`.
R8 executed; non-debuggable manifest, minSdk 29/targetSdk 36 and all three existing ABIs checked.
Independent APK v2 verification confirms the unchanged test certificate SHA-256:
`f97af50c2d869a2591dd82d24555894a2cc95927afb6c0a1fcd4bd197221e47e`.
Build/artifact evidence: ignored `dist/c106-release*` and `dist/c106-apk-*`. Application/native/script
sources match the full-gate commit `cd387d7`; later handoff changes documentation only.
Phone behaviour is **not checked**; nothing merged or pushed.
[Analysis](https://github.com/przunk/protracktor/blob/2c35319cfa8b665398a36141248b45544bd68ebe/docs/PR_C106_SEARCH_REQUEST_OWNERSHIP.md). Other catalogue/folder navigation races remain
separate; this is not a general Browse rewrite.

## ~~C105 latest playlist activation~~ — pre-acceptance handoff, 2026-10-14

`fix/latest-playlist-activation` starts from develop `ad18da6`; develop remains 1.3.0/schema 23.
The trial declares 1.3.1, without changing versionCode calculation or schema. Request generations
guard suspended settings/track/list completions and their follow-up effects. Destination data is
read before non-suspending publication, which keeps persistence ownership aligned with visible
rows. New track-write captures remain gated while loading, preserving C103; previously captured
writes keep their owners. Settings writes are serialized separately.

**Checked negative evidence:** all six new regression checks fail before correction (47 session
checks, six assertion failures). Controlled removal of latest-request/published-write protections
fails five checks out of 48, including the added immediate-destination-save check. Restored sources
match their recorded SHA-256 sums. The first implementation run had one historical early-ID
expectation failure out of 65 checks; that ownership expectation was updated without weakening
the row-write assertions. Restored focused checks passed: 66 tests in three suites, zero
failures/errors/skips (48 session, 10 heart, eight SDK presentation), 101 s Gradle run.
**Checked full gate:** forced run completed in 358 s: 859 unit tests in 115 suites, zero
failures/errors/skips. Lint passed with zero errors/72 warnings (unchanged). PL/EN resource/store
checks, 581 page checks, 49 WASM engine checks, eight SAP checks,
browser database upgrade, 30 native audio checks, 140 icon paths, PWA and 11 loopback server
checks passed. Existing JDK and temporary per-class isolation (one worker/1 GiB heap), no exclusions
or repository build-setting changes. Evidence: ignored `dist/c105-full-gate*`.
**Checked artifact:** clean source `07300b1`, **1.3.1 (1170)**, built in 66 s:
`dist/protracktor-release-1.3.1-1170-07300b1-20261014-005316.apk`, 11,802,642 bytes.
SHA-256: `f7257c3125cdd6903762072ce1c6889b580da4bf6e9c7d1f94500b8625ef0aab`.
R8 executed; non-debuggable manifest, minSdk 29/targetSdk 36 and three existing ABIs checked.
APK v2 signature verified independently; expected test certificate SHA-256:
`f97af50c2d869a2591dd82d24555894a2cc95927afb6c0a1fcd4bd197221e47e`.
Build/artifact evidence: ignored `dist/c105-release*` and `dist/c105-apk-*`.
Application/native/script sources match the full-gate source `6c89157`; later handoff is docs-only.
Phone behaviour is **not checked**; no merge/push or master/tag/store change.
[Analysis and phone checklist](https://github.com/przunk/protracktor/blob/2c35319cfa8b665398a36141248b45544bd68ebe/docs/PR_C105_LATEST_PLAYLIST_ACTIVATION.md).

## Earlier session code review — pre-fix checkpoint, 2026-10-14

[Review and evidence](https://github.com/przunk/protracktor/blob/2c35319cfa8b665398a36141248b45544bd68ebe/docs/review-2026-10-14.md) covers the 2026-10-13/14 session, including C103, C102,
B6, B48 and B46. One additional pre-existing playlist activation defect is recorded as C105 below;
the known stale-search/navigation race also remains. Performance, external ARTIST metadata,
concurrent native-test coverage and fail-open APK verification are observations, not invented
device regressions. No fixes or new APK were made. Sources still match the accepted trial.

### C105 — overlapping playlist switches can disagree with the persistence target

**Recorded:** 2026-10-14. **Source:** code review and controlled host diagnostic, not user feedback.
Suspend activation 2's track read, finish activation 3, then finish 2. Actual result: persistence
target 3, visible playlist 2 with its rows. A temporary correctness test executed once and failed
its latest-selection assertion; source/XML/log retained in ignored `dist/daily-review-20261014/`.
The switch method is unchanged from baseline `79244e0`, so this is not a new C103/B48 regression.

Each activation publishes independently without a latest-request guard. C103 prevents the mismatched
queue from being written into the other playlist, but subsequent snapshots can be refused until
activation restores agreement. Normal Save has no rejection feedback on this path; notification
Save reports failure. No wrong-playlist write, data loss, phone frequency or phone reproduction is
claimed. **Owner selected correction, 2026-10-14:** implementation on
`fix/latest-playlist-activation`, based on develop `ad18da6`. Before correction, all six new
controlled-store regressions fail by assertion (47 session checks, six failures, zero errors/skips).
This is the pre-fix checkpoint; the current branch implementation is recorded above. No phone
result or merge is claimed. [Analysis](https://github.com/przunk/protracktor/blob/2c35319cfa8b665398a36141248b45544bd68ebe/docs/PR_C105_LATEST_PLAYLIST_ACTIVATION.md).

## B48 and B46 — owner accepted and merged, 2026-10-14

The owner reported no objections and authorized both merges. B48 was merged first at `61886e7`,
then B46 at `7111c47`. The resulting tracked tree matches the accepted stacked branch `4c0a28b`;
app/web/scripts/Gradle sources match the offered clean-source APK `ea04525` below. No new APK or
test/lint run was needed for tree-identical integration and documentation closeout. The recorded
852-test gate applies to those unchanged sources, not a newly executed merged-head run.

This is overall owner acceptance, not a claim that every checklist was reported individually.
Device/Android and exact installed build were not separately supplied. Master, tags, schema and
store state are unchanged; nothing was pushed. Earlier pending/unmerged entries below are dated
checkpoints, superseded by this acceptance. [Remaining checks](TESTING.md).

## Stable B48 save control and shared Now Playing presentation — pre-acceptance evidence, 2026-10-14

The owner reported that the 1.3.0 (1158) trial removes the heart during saving and moves Next/seek
geometry under a second tap. The owner approved retaining the heart, a bold title, the dock's
shared subtitle policy and a flat P glyph. The search field was reported as acceptable; search
code remains unchanged, without inferring acceptance of every B46 persistence scenario.

B48 `6b4efca` is integrated into the approved stacked B46 branch without rewriting history.
`PlaylistHeartSave` remains the overlap guard; action identity/position is retained throughout
saving. `NowPlayingText` serves both UI and native presentation; original artist metadata, audio,
service lifecycle and artwork palette remain unchanged. The title has a bold span; session display
subtitle and ARTIST display fallback carry the shared summary. The small P is white on transparent.
[Contract and evidence](PLAN_NOTIFICATION_HEART.md). No dependency/schema/toolchain change.

**Checked:** 88 focused host checks passed in seven suites, zero failures/errors/skips. The new
pending regression failed 2/16 before correction. Shared-year mutation failed 6/17, including the
actual dock and both SDK projections; pending-action/wrong-icon mutations failed 6/24. Sources
were restored and SHA-256 compared. Targeted icon generation is repeatable; a headless disposable
full-generation fixture matched all 26 current assets. Live SystemUI geometry, double taps and
final text/icon appearance are **not checked** until owner phone retest.

**Checked combined gate:** forced run completed in 364 s: 852 unit tests in 115 suites, zero
failures/errors/skips; lint 0 errors/72 warnings (unchanged). All existing PL/EN, store metadata,
581 page, 49 WASM engine, 8 SAP, browser database upgrade, 30 native audio, 140 icon-path, PWA and
11 loopback server checks passed. Same temporary per-class worker isolation, one worker/1 GiB
heap and existing JDK, without exclusions or repository build-setting changes. Evidence:
ignored `dist/notification-search-verification/full-gate-stable-heart*`.
The replacement minified APK is ready below. Develop/master, tags and store are unchanged.

**Checked artifact:** **1.3.0 (1162)**, clean source `ea04525`, built in 51 s:
`dist/protracktor-release-1.3.0-1162-ea04525-20261014-001502.apk`, 11,801,606 bytes. SHA-256:
`3b169b620fd9a7df385d78130e0e5376818fbd0f59757ef26407cee3fefab054`.
R8 executed; non-debuggable manifest, minSdk 29/targetSdk 36 and all three ABIs verified. APK v2
test certificate SHA-256 `f97af50c2d869a2591dd82d24555894a2cc95927afb6c0a1fcd4bd197221e47e`
matches previous trials. The flat P, both hearts and Saving label survive resource shrinking;
the removed search heading remains absent. These are host/artifact checks, not live SystemUI
execution. [Phone retest](TESTING.md). Subsequent handoff is documentation-only; app/native/search
sources are unchanged. Earlier APKs are superseded; no merge, push, AAB or store release.

## Earlier B48+B46 placement/search correction APK — superseded by save-state feedback, 2026-10-13

The owner reported three presentation problems after the combined APK handoff: the notification
heart fills the missing Previous slot and moves when Previous becomes available; the integrated
search magnifier became a labelled button; and suggestions look like icon buttons under a heading
instead of ordinary text autocomplete. Device/Android and installed build were not separately
supplied. These are real owner observations, not fabricated third-party feedback or agent phone tests.

B48 `029c943` reserves both transport slots through the production MediaSession factory without
enabling unavailable skip commands. It remains on `feature/notification-playlist-heart`; the
approved stacked B46 branch integrates that correction without rewriting either branch's history.
71 focused host checks passed; removing reservations fails all four new factory checks. The host
records `setExtras`, not actual controller/SystemUI rendering. B46 restores the integrated magnifier
and plain text anchored suggestions. Storage/search semantics, schema and WWW are unchanged.
The corrected search path passes 44 focused host checks; four of six UI checks failed before the
presentation change. The forced combined gate passed in 346 s: 835 unit tests in 112 suites,
zero failures/errors/skips, lint 0 errors/72 warnings, plus all existing web/native/resource/store
and loopback checks listed below. It used the same temporary per-class worker isolation and JDK,
without exclusions or repository build-setting changes. Evidence is in ignored
`dist/notification-search-verification/full-gate-presentation*`. A replacement minified APK is
ready below. Nothing has been merged into develop/master or pushed. Phone acceptance remains pending.

**Checked replacement artifact:** **1.3.0 (1158)**, clean source `f289863`, built in 56 s:
`dist/protracktor-release-1.3.0-1158-f289863-20261013-232128.apk`, 11,800,374 bytes. SHA-256:
`e15e147fdac8a31f54c6b61ebdef24a1522780ec3401c20e1ced0115e3aa041b`.
R8 executed; manifest is non-debuggable, minSdk 29/targetSdk 36, three existing ABIs. APK v2
signature verified; test certificate SHA-256
`f97af50c2d869a2591dd82d24555894a2cc95927afb6c0a1fcd4bd197221e47e`, matching earlier trials.
Both reservation wire keys survive in DEX; heart/magnifier resources remain and the removed
suggestion-heading resource is absent. These are artifact/host checks, not SystemUI or keyboard
execution on a phone. [Corrected phone checklist](TESTING.md). Subsequent handoff is documentation
only; no rebuild is needed for it. Earlier APKs below are superseded. No AAB/store release.

## Earlier B48+B46 combined APK — superseded by presentation feedback, 2026-10-13

After the first combined gate/APK, code inspection found new heart labels/feedback missing the
saved in-app locale. Corrected B48 `d8dfb89` now passes 67 focused checks; B46 was rebased onto it.
The `9583569` APK and earlier 825-test gate predate that correction and are not final trial
evidence. The fresh forced combined gate/lint passed; the final clean-source APK is recorded below.
No phone result,
merge, push or change to develop/master.

**Checked:** the final forced combined gate passed in 331 s: 829 unit tests in 111 suites, zero
failures/errors/skips; lint 0 errors and 72 warnings (three new KTX style suggestions). PL/EN strings,
store metadata, 581 page checks, 49 existing WASM engine checks, 8 SAP checks, browser database
upgrade, 30 native audio checks, 140 icon paths, PWA and 11 loopback server checks also passed.
Two earlier shared-worker attempts were stopped after Compose idle timeouts (Settings, then
Search teardown); Settings passed separately without source changes. The successful gate used
temporary per-class worker isolation and a 1 GiB heap on the existing JDK, without exclusions or
repository build-setting changes. The timeout mechanism is not established. Evidence is in ignored
`dist/notification-search-verification/`; [details](PLAN_RECENT_SEARCHES.md).
Both features remain unmerged. A clean-source combined release APK is ready; phone behaviour is
**not checked**. Master, tags, schema and store publication are unchanged.

**Checked artifact:** minified release APK built in 35 s from clean source `1452309`:
`dist/protracktor-release-1.3.0-1154-1452309-20261013-224501.apk`, **1.3.0 (1154)**,
11,775,822 bytes. SHA-256:
`2325fc69f406c1f59cfe12f4e6483a3b35bff887ffa0dd9bf5ed920b6bf01469`.
R8 executed; the APK is non-debuggable, minSdk 29/targetSdk 36, with all three existing ABIs.
APK v2 signature verified; test certificate SHA-256
`f97af50c2d869a2591dd82d24555894a2cc95927afb6c0a1fcd4bd197221e47e`, matching the accepted
stereo/Bluetooth trial. Both heart drawables and all new labelled-action/search resources survive
resource shrinking. Artifact checks are not Android execution; this is sideload testing, not an
AAB or store release. The handoff commit changes documentation only. App/native sources match
the final full-gate source tree; no rebuild was needed for documentation/commit-message corrections.

## Recent searches — B46, stacked implementation; phone acceptance pending, 2026-10-13

`feature/recent-search-suggestions` starts from corrected B48 `d8dfb89` with explicit owner
approval for stacking; its earlier local unpublished base `ceeaa57` was amended before handoff.
It declares 1.3.0 for the combined B48+B46 trial; develop remains 1.2.0, schema 23 unchanged.
Android keeps 20 unique submitted queries separately from playback History, offers up to three
case-insensitive prefix matches and immediately submits a selected query in the current scope.
Typing only filters suggestions. [Contract and evidence](PLAN_RECENT_SEARCHES.md).

**Checked:** 42 focused host tests passed (8 pure, 4 preference, 26 workflow, 4 real Browse/Compose).
Deliberate matching/submission/recording/persistence mutations failed 11/42; all four sources were
restored and SHA-256 compared. The final restored full gate/lint passed as recorded above;
the combined release APK is recorded above. Real phone UI and both feature checklists are **not checked**.
No merge or push.

## Notification heart — B48, implemented on task branch; phone acceptance pending, 2026-10-13

`feature/notification-playlist-heart`, based on develop `debb00d`. The owner selected add-only to
the active playlist and immediate saving of all working edits, including previous unsaved changes.
Outline/filled membership controls are built for notification actions and session custom actions.
URI payloads bind the displayed track and playlist; stale/missing/loading targets and overlapping
requests are rejected. Save uses C103's serialized snapshots and confirms completion; a failed
save retains the working addition/dirty state and gives feedback. Normal additions, playback,
schema and WWW are unchanged. [Contract and evidence](PLAN_NOTIFICATION_HEART.md).

**Language closeout:** the saved in-app locale was missing from raw service/application resources.
Actual app-selection regression checks failed on SDK 29/33 before correction (2/14). New heart
labels and feedback now use `AppLocale.wrap`; all 67 focused checks passed afterward (10 domain,
16 action/locale, 41 session). Bypassing feedback localisation failed 2/16 adapter checks;
the source was restored, SHA-256 compared and 16 passed again. Existing transport/channel labels
are unchanged. The combined gate passed after rebasing B46; the earlier `9583569` APK is superseded.

**Checked:** 63 focused host tests passed (10 domain, 12 SDK 29/33 action checks, 41 session checks).
Deliberate guard/payload/completion mutations failed 7/63; all three sources were restored and
SHA-256 compared, then 63 passed again. Real service/SystemUI/lock-screen/phone behaviour is
**not checked**. Full tests/lint passed on the approved stacked B46 branch as recorded above;
the combined APK is recorded above. No merge, push or store release. The initial 63-test checkpoint
preceded the four added locale checks; the current focused total is 67.

## Stereo mix trial — B6, owner-accepted and merged, 2026-10-13

Owner selected three Android Settings modes: Original, Narrow and Mono. Work originated on
`feature/stereo-mix-modes` and merged into develop at `f4d304c` after owner approval;
[trial contract](PLAN_STEREO_MIX.md) records the boundaries and assumptions.
Develop now declares version 1.2.0/schema 23. Original is the unchanged default,
Narrow uses 50% width and Mono averages both channels. Existing preferences retain the global choice.
The shared Android output applies it before ducking, including MP3. Live transitions ramp over
up to approximately 10 ms without resetting the decoder; a newly opened track receives the latest mode
before starting. Paused playback stays paused. Web and exported/shared audio are unchanged.

**Checked:** the production Player/JNI harness passes 30 checks, also with ASan/UBSan outside the
sandbox. Its earlier regression checkpoint failed 8/29 before PCM processing. Bypassing processing
failed 9/30 checks; removing the ramp failed 2/30. Ten focused Kotlin/Android-host/Compose checks
cover stable mode mapping, preferences and real Settings clicks in PL/EN. Disconnecting Settings
failed 2/3 UI tests; mutating persistence/width mapping failed 4/7 focused checks. All mutated sources
were restored and checksums compared. These are host checks, not phone execution.

**Checked:** forced full gate completed in 205 s: 782 unit tests across 106 suites, zero
failures/errors/skips; lint 0 errors and 69 warnings. Also passed: PL/EN strings, store metadata,
581 page checks, 49 existing WASM engine checks, 8 SAP checks, browser database upgrade, 30 native
audio checks, 140 icon paths, PWA and 11 loopback server checks. An earlier full run passed all
782 unit tests but lint rejected a literal percent in the new translated description; both
resources now explicitly disable formatting. The first rerun was interrupted after the host test
worker reported OutOfMemoryError. The successful rerun used a temporary init script limiting that
worker to 1 GiB, with the existing JDK and no app/build configuration or test exclusions changed.

**Checked:** minified release APK built from clean source `ad6e3cd` in 106 s:
`dist/protracktor-release-1.2.0-1146-ad6e3cd-20261013-213208.apk`, **1.2.0 (1146)**,
11,760,006 bytes. SHA-256:
`dbbdc1ed91f11c53d69cc4b988f57fb670b1544c1ede7a52c58094474e613487`.
R8 executed; the APK is non-debuggable, minSdk 29/targetSdk 36, three ABIs. APK v2 signature
verified; test certificate SHA-256 `f97af50c2d869a2591dd82d24555894a2cc95927afb6c0a1fcd4bd197221e47e`,
matching the previous accepted Bluetooth trial. The width setter's JNI export exists in all three
packaged libraries and its native name survives in minified DEX. These are artifact checks, not
execution on Android. The subsequent handoff changes documentation only, not the APK sources.

**Owner acceptance, 2026-10-13:** positive feedback followed the APK handoff and the owner explicitly
authorized merging. Installed build, device/headphones, preferred mode and individual checklist
results were not separately supplied; this is not evidence that every phone scenario passed.
Remaining device scenarios are **not checked** individually; see the
[phone checklist](TESTING.md#b6-stereo-mix--owner-accepted-remaining-device-checks).
The merge tree exactly matched the approved branch; application sources still match APK source
`ad6e3cd`. Tests/lint and APK were not rerun for the merge or documentation-only closeout.
Master, tags and store publication are unchanged; no push, new APK or schema change.

## Selected next work and deferred WWW headset report — 2026-10-13

B48 notification hearts and B46 recent searches are selected for implementation. The owner approved
two stacked task branches and one combined release APK for testing, with no merge before acceptance.
B48 saves the selected playlist immediately, including existing unsaved edits; B46 remembers 20
queries and shows at most three prefix matches, submitting a selected query in the current scope.
No implementation, tests or new APK are established by this requirements checkpoint.
C96 UADE teardown is deferred beyond today; B45 queue ownership needs a separate discussion.

### C104 — WWW headset/media buttons do not control playback; owner report, deferred

The owner reports Android headset buttons appear to work, but buttons do not work for the WWW
player. Exact browser/device/headset, installed/site build, affected buttons and reproduction steps
were not supplied. This is a real owner observation, not an agent reproduction or a diagnosis.
Web already registers play/pause/next/previous handlers in `web/src/app.js`; their presence does
not prove browser/device delivery. Cause and fix remain unverified. Track the deferred work with
B47, separately from Android routing C102 and Android Auto B44. No code change or device retest.

## Notification heart request — recorded 2026-10-13; implementation selected

The owner relayed post-test user feedback requesting direct playlist addition from the player
notification. Recorded as B48, with destination and existing-membership semantics in Q16 and the
[feedback index](TESTING.md#feedback-record). The owner selected add-only membership indication and
immediate saving of the selected playlist's entire working contents after B6 was accepted.
Requirements recorded only at this checkpoint: no code change, test/lint run, APK or release.
Reporter/build/device details and Play closed-test participation were not supplied or inferred.

## Bluetooth output recovery — C102, owner-accepted and merged, 2026-10-13

Work originated on `fix/bluetooth-output-recovery`, stacked on C103 at `b134b3e`.
Following owner approval, C103 merged first at `f4c9bf1`, then C102 at `69c42a2`.
Develop now declares 1.1.1/schema 23; master, tags and store publication are unchanged.
The original headphone report is real owner testing; the agent has not reproduced Bluetooth
on a phone. No installed-build/device details or logcat confirmation of ErrorDisconnected exist.

The native Player detects output disconnection and reopens only the output, preserving its
decoder, position, subsong and gain. A stream-owned shared error callback publishes an atomic
result without referring to Player or closing/reopening from the error thread. The existing
200 ms control poll owns recovery; Pause/track destruction cannot be overridden by a late callback.
Other output errors or a failed reopen pause playback with the existing localized error notice,
instead of silently displaying Playing or treating the failure as a track ending.

**Checked:** four host checks against unchanged production executed and three failed (`72af15e`).
The completed production Player/JNI, compiled with a fake Oboe device and decoder, passes 18 host
checks, also with ASan/UBSan. Controlled native mutations failed 11/18 checks; the mutated JNI-result
mapping failed all four Kotlin tests. Both mutated sources were restored and checksums compared.
The sandbox sanitizer attempt could not complete LeakSanitizer under ptrace; the outside-sandbox
run completed successfully. No decoder cache or hardware service is used by these tests.

**Checked:** forced full gate, 772 unit tests across 103 suites (zero failures/errors/skips),
18 native audio-host checks; lint completed with 0 errors and 68 warnings. PL/EN strings, store
metadata, 581 page checks, 49 existing WASM engine checks, 8 SAP checks, browser database upgrade,
137 icon paths, PWA and 11 loopback server checks also passed. Gate duration: 270 s.
**Checked:** combined release APK built from clean source `d1d8964` in 661 s:
`dist/protracktor-release-1.1.1-1138-d1d8964-20261013-203532.apk`, **1.1.1 (1138)**,
11,751,338 bytes. SHA-256:
`4077ab501bb11726eb70da1bd3dc688236891b2c946ff0340c4049827bfc7988`.
R8 executed; the APK is non-debuggable, minSdk 29/targetSdk 36, three ABIs. APK v2 signature
verified; test certificate SHA-256 `f97af50c2d869a2591dd82d24555894a2cc95927afb6c0a1fcd4bd197221e47e`,
matching trial APK 1123. The recovery JNI export exists in all three packaged engine libraries,
and the minified DEX retains its native method name. These are artifact checks, not device execution.
The APK script selects only test signing and names the artifact with variant/version/commit SHA
and dirty marker where needed. The copy/name helper was exercised on an actual existing APK in a
disposable clean/dirty Git fixture. That build stage did not access an upload key, build an AAB,
create a tag, push, merge or release to the store.
**Checked on phone — owner follow-up, 2026-10-13:** connecting or disconnecting Bluetooth while
paused leaves playback silent; connecting during playback transfers sound to headphones without
manual restart; disconnecting during playback pauses. The owner accepted these results and
authorized merging the combined work. Installed build/device details were not separately supplied.
**Not checked:** car receiver, route timing, position/subsong continuity, progress-bar agreement,
seek, a Pause racing with connection, focus/EOF/error cases and long-term behaviour. No individual
C103 phone-check results were supplied. The merge tree exactly matched the approved branch;
application sources still match tested APK source `d1d8964`. Tests/lint and APK were not rerun for
the merges and documentation-only closeout. [Analysis and handoff](https://github.com/przunk/protracktor/blob/2c35319cfa8b665398a36141248b45544bd68ebe/docs/PR_C102_BLUETOOTH_OUTPUT_RECOVERY.md).

## Playlist-write safety — C103, merged with owner approval, 2026-10-13

Owner-selected work on `fix/playlist-write-safety`, based on `develop` at `01e5bc1`.
The two session-write races recorded during the refactor are addressed without changing the
schema, version, settings debounce, decoder ownership or playlist activation order.
Writes capture the playlist and detached track list together; session replacements are serialized.
Save clears dirty state only when the same playlist still contains the saved tracks. Revert and
Delete invalidate obsolete requests, including requests captured while those operations suspend.
Automatic additions use the same session writer instead of reading the target/queue later.

**Checked:** the unchanged implementation failed 11 of 29 regression-checkpoint tests; three
additional lifecycle tests failed before their guards. The completed session suite passed all
38 tests. Deliberately disabling snapshot/serialization/completion/lifecycle guards failed 13
of those 38 tests; both production sources were restored and their SHA-256 values compared.
**Checked:** forced full gate (`scripts/test-protracktor.sh --really`), 768 unit tests with zero
failures/errors/skips; lint completed with 0 errors and 68 warnings. Also passed: PL/EN strings,
store metadata, 581 page checks, 49 existing WASM engine checks, 8 SAP checks, browser database
upgrade, 137 icon paths, PWA checks and 11 loopback server checks. Duration: 310 s.
Logs/XML and lint report: ignored `dist/playlist-write-safety/`.
**Not checked:** real phone/SQLite timing or long-term behaviour.
No standalone APK was built: C103 is included in the minified combined trial recorded above.
Merged at `f4c9bf1` with the owner's approval of the combined work. The Bluetooth checklist results
do not independently confirm playlist behaviour; no per-scenario C103 phone result was supplied.
[Analysis and handoff](https://github.com/przunk/protracktor/blob/2c35319cfa8b665398a36141248b45544bd68ebe/docs/PR_C103_PLAYLIST_WRITE_SAFETY.md).

## Implementation roadmap and Bluetooth report — recorded 2026-10-13

The owner requested a saved inventory of unfinished, deferred and undecided work:
[Implementation roadmap](IMPLEMENTATION_ROADMAP.md). It includes existing backlog/review risks
without treating historical completed tasks as new work. AMP is a renewed A7 exploration TODO,
not a second catalogue item; an approved access route remains the prerequisite. B47 records
verification of Bluetooth transport callbacks that already exist, rather than claiming absent
support. C102 was subsequently confirmed by the owner on headphones; the installed build/device
and cause remain unknown. These requests were supplied by the owner; no separate
tester identity or Play-test participation is inferred.

Documentation only: content/IDs, local links and diff whitespace are checked. No implementation,
application test/lint run, APK build or new device result is established by this record.

## New real user requests — recorded 2026-10-13; discussion pending

The owner relayed three genuine user requests: continuous playback when returning from search
or author browsing to the playlist, stereo mixing for listening in both ears (with a possible
headphone mode), and recent queries offered from the search field. Queue continuity is B45,
recent searches B46, and stereo mixing extends existing B6. The active-queue approach was
discussed with the first reporter; its transition rules and implementation are deferred.
These propose new behaviour; no navigation regression or audio-output fault is diagnosed here.

The wishlist preserves each request and its source/date; Q13–Q15 preserve unresolved choices.
The [feedback index](TESTING.md#feedback-record) links them for future analysis, implementation
and user retesting. No reporter identity or Play closed-test participation is inferred. This
documentation-only update does not implement, test or schedule these features, or amend the
1.1.0 release notes. The build already declares 1.1.0 and its AAB evidence exists separately
in `store/play-console/releases.md`; this record does not establish upload/publication status.

## Next release description and PWA feedback — 2026-10-11

After confirming PWA installation and normal operation, the owner proposed an Android release
and requested that its description include all addressed user feedback. Prepared
[the full changes since 1.0.0](RELEASE_NOTES_NEXT.md) and replaced the PL/EN Play release-note
drafts with feedback-led summaries within the repository's 500-character limit. Unimplemented
Android Auto/F-Droid ideas and deferred refactoring are explicitly excluded from delivered work.
Version 1.1.0 is recommended, not yet selected or written to the build. No release script, test
suite, APK/AAB build, tag, master change or push was performed for this documentation update.

The earlier web deployment was checked separately: `gh-pages` at `3b7a9ee` publishes source
`d5dbe4a`; GitHub Actions reported success, both branches matched their remote references, and
ten sampled public files returned HTTP 200 and matched the published tree. The owner's later
PWA report confirms installation and normal use, not a separate offline or iOS test.

## Owner acceptance and merge — 2026-10-11

The owner reported that the combined changes work and authorised merging. B43 with C100,
B42 and C101 were integrated into local `develop` in that order by fast-forward, ending at
`77dea5b` before this documentation update. This retains both features and accepts the fixes.
The confirmation is an overall phone-test result, not an itemised claim that every checklist
case, device or language was tested. Earlier pending-trial notes below are historical.

Verification: the merged tracked tree outside `docs/` is identical to `cbb6f3e`, the source of
the supplied 1123 APK that passed 749 tests and lint. No application edits, additional test run
or APK build were needed for the fast-forwards. The owner explicitly requested no APK build.
VersionName, schema and `master` are unchanged; no push, tag, AAB or publication.
At that point two remote commits were still absent (`44ff9ff`, `ad42749`, Web/PWA PR #3).
They were subsequently integrated at the owner's request, as recorded below.

## GitHub PR #3 integrated — 2026-10-11

Fetched `origin` and merged `origin/develop` at `ad42749` into local `develop`, preserving
the accepted B43/C100, B42 and C101 stack. PR #3 adds Web/PWA installation and offline startup
(`44ff9ff`); the merge had no conflicts. Android application sources are unchanged.
Verified on the merged tree: PWA policy/manifest tests, page checks, all 137 icon paths,
11 loopback server checks, the web database upgrade check, and shell syntax for the changed
packaging, publication and test scripts. No Android test/lint rerun, APK build, web packaging,
browser/device installation test, push or deployment was performed for this integration.
The earlier PWA contributor evidence is retained below and is not a new test run by this agent.

**Current combined trial APK (C100 + C101 + B42/B43):**
`dist/protracktor-release-1.0.0-1123-cbb6f3e-20261011-174025.apk`, **1.0.0 (1123)**,
clean source `cbb6f3e`, built in 195 s; 11,745,002 bytes. SHA-256:
`fd138fe8185f710985de8b0a325352f69db945ad0a37ad2f21d9be890c473cb2`.
Minified/non-debuggable, minSdk 29/targetSdk 36, three ABIs. APK v2 signature verified with the
same test certificate as 1120; schema 23 unchanged. Full gate: 749 tests and lint passed.
Owner phone acceptance and local merge are recorded above; no push, tag, AAB or publication.
The handoff commit changes documentation only. [Evidence and test checklist](https://github.com/przunk/protracktor/blob/2c35319cfa8b665398a36141248b45544bd68ebe/docs/PR_C101_LEARNED_TRACK_TITLES.md).

**Branch organisation, owner decision 2026-10-11:** C100 is part of B43 on
`feature/played-before` (`3cde2ec`). `feature/saved-places` (`cb79b1b`) is rebased on it;
`fix/learned-track-titles` remains separate above B42 (C101 code: `4b26bdb`). The former
`fix/short-track-listened` branch is retired. Original tips are retained under
`refs/archive/20261011/` for traceability of the APKs and verification runs.
Git comparison confirms the combined source tree outside `docs/` is identical to the tested
`cbb6f3e` source, and the B42+C100 tree matches the original `0f70540` outside `docs/`.
Only commit order and documentation changed; no new test run or APK build was performed for
this reorganisation. The existing 1123 APK remains the combined trial candidate. Standalone B43
with C100 was not separately rebuilt/tested and must not replace an installed schema-23 trial.
At reorganisation time `develop` and `master` were unchanged; acceptance was still pending.
The later owner acceptance and local merge are recorded above.

## User trial feedback — 2026-10-11

On 2026-10-08 the owner reported satisfactory use so far, explicitly withholding acceptance.
B42/B43 were still experiments without merge approval at that point. The following reports were relayed as user
feedback on 2026-10-11; the exact installed build and example file were not supplied (the latest
handed-over candidate was 1120). No reporter identity or closed-test membership is inferred.

### ~~C100. Tracks shorter than five seconds never receive the listened marker~~ — accepted and merged, 2026-10-11

Reproduction: play a previously unqualified three-second track through its end, then inspect its
Browse/Random marker. Observed: no mark; five seconds of playback does mark a longer track.
Requested: five active seconds **or natural completion**. This revises B43's original strict
threshold; it is not a regression from an earlier completion rule. `ListeningSession.sample`
previously checked accumulated time without a completion input. At the owner's request on
2026-10-11, C100 belongs directly to `feature/played-before`; C101 remains a separate correction.
B42 is stacked above B43. C100 changes no schema. On the original combined
B42/B43 trial at `0f70540`, the forced full gate passed in 270 s: **741 tests**, zero failures/errors/skips,
lint without errors and all companion checks. The old rule failed four new tests; an isolated
seek-guard mutation failed its intended test. [Evidence and phone checklist](https://github.com/przunk/protracktor/blob/2c35319cfa8b665398a36141248b45544bd68ebe/docs/PR_C100_SHORT_TRACK_LISTENED.md).
The owner subsequently accepted the combined trial and authorised the merge (see above).
The combined 1123 release above includes the separately authorised C101.

### ~~C101. Random/Browse retain filenames while History shows decoded titles~~ — accepted and merged, 2026-10-11

The user reports inconsistent names and asks whether improving cached entries would disturb
ordering. `adoptTitleFrom` returns a decoded title to History and updates playlist entries only;
Random's record and Browse catalogue rows retain their source labels. `CatalogueStore.tracks`
orders by catalogue title, so overwriting that stored field would change later Browse ordering.
Recommended: a display-only learned title, filename/path still available, unchanged catalogue sort
key and Random order. Downloading into cache alone does not extract decoder metadata. The owner
approved this presentation as a separate correction on 2026-10-11, and explicitly approved using
History as the source: restart and file-cache eviction retain names, Clear History removes the
overlay. Implementation is on `fix/learned-track-titles`, now above `feature/saved-places`
with C100 inherited from `feature/played-before`, without schema changes.
One batched History read supplies markers and titles; only rendered copies change, never catalogue
sort fields or the Random record. Two UI tests failed before wiring and passed afterwards.
The forced full gate passed in **309 s: 749 tests**, zero failures/errors/skips, lint without
errors and all companion checks. The owner subsequently accepted the combined trial and authorised the merge.
See [the handoff](https://github.com/przunk/protracktor/blob/2c35319cfa8b665398a36141248b45544bd68ebe/docs/PR_C101_LEARNED_TRACK_TITLES.md) and
[Q12](OPEN_QUESTIONS.md#q12--learned-titles-in-random-and-browse).

## B42 Saved places — accepted and merged, 2026-10-11

The dated implementation and handoff records below precede the acceptance recorded above.

**Follow-up feedback, 2026-10-07 — host verified, phone trial pending:** the owner requested snackbar Undo after
bookmark removal in Saved places and reported unequal controls. A host layout test reproduced
112 px Open versus 134 px Bookmark widths at the test density. The pair now shares the wider
intrinsic width. Bookmark removal captures its exact saved record and exposes a one-shot Undo
on that notice; restoration preserves the original save time and does not undo playlist edits.
Schema 23 and B43 are unchanged. Full gate passed in **190 s: 731 tests**, zero failures/errors/skips,
lint without errors and all companion checks. Tests executed rather than returning a cached result.
Two deliberate undo mutations broke four tests; the code was then restored. Two native-render tests produced eight
previews; EN-light and PL-dark list layouts were inspected. Previous checks/APKs below do not
verify this follow-up. Phone behaviour remains unverified.

**Previous bookmark trial APK:** `dist/protracktor-release-1.0.0-1120-3c7ae8e-20261007-224320.apk`,
**1.0.0 (1120)**, clean source `3c7ae8e`, built in 118 s; 11,742,546 bytes.
SHA-256: `1c587ca3013d7c29e8105e127c2627db05427c291f91fafed052c0bff24d2383`.
Minified/non-debuggable, minSdk 29/targetSdk 36, three ABIs; verified v2 test signature matches
1118. Evidence: `dist/b42-undo-verification/`. The handoff commit changes documentation only.
No merge or publication.

**Presentation refinement, host verified:** the owner requested outlined/filled Bookmark controls
with a stable label, plus compact name/path rows with Open and Bookmark on the right.
Schema 23 and B43 are unchanged. Full gate: **724 tests**, zero failures/errors/skips, lint without
errors, all companion checks passed (137 icon paths); 211 s. Tests executed, not served from cache.
Four updated UI tests failed against the old presentation; all eight interaction tests now pass.
Native previews cover EN/PL and both themes. UI fixture lifecycle/shared-SDK failures and the
successful API-29 isolation are recorded in [the PR handoff](https://github.com/przunk/protracktor/blob/2c35319cfa8b665398a36141248b45544bd68ebe/docs/PR_B42_SAVED_PLACES.md).
The revised UI is not yet verified on the phone.

**Previous presentation APK:** `dist/protracktor-release-1.0.0-1118-84fa8a8-20261007-222557.apk`,
**1.0.0 (1118)**, clean source `84fa8a8`, built in 98 s; 11,740,154 bytes.
SHA-256: `5f9e373fbe4e282eee41ba9b0d99262fee49e37fb227905683f0a88ce3fd968f`.
Minified/non-debuggable, minSdk 29/targetSdk 36, three ABIs; v2 test signature matches 1116.
Evidence: `dist/b42-bookmark-verification/`. No merge or publication. The 1116 artifact below is
the preceding presentation candidate, retained for traceability.

The owner approved `feature/saved-places` from B43's `54a933a` to test both features in one APK.
Schema **23 is reserved for B42**, above B43's 22. The B43 branch stays unmerged and unchanged
during the owner's planned approximately 24-hour trial. The owner reported that its new layout
looks acceptable; usefulness and longer-term behaviour are still under evaluation. No merge was
authorised. B42 uses separate commits and independent bookmarking logic. See
[the plan](PLAN_SAVED_PLACES.md). The forced full gate passed in 234 s: **723 tests**, zero
failures/errors/skips; lint without errors; 581 page, 49 existing-WASM (not rebuilt), 8 SAP and
8 server checks, web database upgrade, 135 icon paths and store metadata validation. Two deliberate
mutations failed the intended identity/navigation tests; they were restored before this gate.
Two render tests produced eight previews. Device behaviour and usefulness remain unverified;
the release APK is ready. [Evidence and trial checklist](https://github.com/przunk/protracktor/blob/2c35319cfa8b665398a36141248b45544bd68ebe/docs/PR_B42_SAVED_PLACES.md).
B43's five-second rule and
status-column placement are unchanged. Never reinstall a schema-22 (or older) APK after upgrading
to this candidate: those older binaries retain a destructive downgrade path. A rejected feature
requires a forward-compatible build preserving the installed schema and user data.

**Combined B42 + B43 trial APK:** `dist/protracktor-release-1.0.0-1116-3f8df94-20261007-214351.apk`,
**1.0.0 (1116)**, built from clean `3f8df94` in 118 s, 11,739,334 bytes. SHA-256:
`d43b15266783814cf7202918687fc60f34faee9b78b7645b23971c587c23222a`.
Minified/non-debuggable release, three ABIs, APK Signature Scheme v2 verified with the same test
certificate as B43. Logs and previews: `dist/b42-verification/`. The following handoff commit
changes documentation only. No merge, push, tag, master change, AAB or publication.

## User discovery feedback — B43 selected as an experiment, 2026-10-07

**Second visual trial requested, 2026-10-07:** the owner asked to move the listened marker from
the subtitle to below the cache phone in Browse; Random uses the same lower slot below the
ordinal/play symbol. Its position does not depend on the cache mark, and selection takes priority.
Host verification passed: the forced full gate completed in 263 s with **705 tests**, zero
failures/errors/skips, lint without errors and the same companion checks listed below. The two
new layout tests failed on the old placement and passed after the change; seven UI tests include
actual long-press selection. Four native-render tests generated eight previews; EN-light and
PL-dark Browse/Random previews were inspected. Logs/previews are in
`dist/b43-status-column-verification/`. The 1111 artifact below is the first visual trial.
Playback, History qualification and schema 22 are unchanged.
Phone appearance and usefulness remain unverified; no acceptance or merge is inferred.

**Current trial APK:** `dist/protracktor-release-1.0.0-1113-d68f8d0-20261007-205016.apk`,
**1.0.0 (1113)**, built from clean `d68f8d0` in 98 s, 11,706,798 bytes. SHA-256:
`dfe9e2823d7d858231690220d44c8dbf89bddbc7352d4fbbdd40f620ac88758c`.
Minified/non-debuggable, all three ABIs, minSdk 29/targetSdk 36. APK Signature Scheme v2 verified;
the test certificate matches 1111. Same release script/toolchain as below; artifact filename
includes variant and source SHA. The following documentation commit does not change app sources.

B43 implementation started on `feature/played-before` from `00abd8f` after the owner confirmed
the remaining rules. Schema **22 is reserved for B43**. Playlist hiding is view-based, the threshold
is strictly five active seconds, qualification is per file/play occurrence, and Clear cancels the
current occurrence's eligibility. Implementation, host verification and the test-signed release
artifact are complete; the phone trial is pending. The baseline
helper's destructive downgrade is a known risk: never reinstall a schema-21 APK after the trial;
abandonment requires a forward-compatible build preserving all user data.

At the owner's request, expanded existing wishlist B43 ([Played before](PLAN_PLAYED_BEFORE.md))
and B42 ([Saved places](PLAN_SAVED_PLACES.md)) into separate implementation plans. The owner
identified both as user feedback being addressed. The record attributes them as relayed user
requests, without inventing individual reporters, closed-test participation or completed fixes.
The plans distinguish agreed requirements from remaining choices and specify the feedback →
analysis → implementation → PR/handoff → user test process. At the initial planning stage only
documents changed; implementation and new tests followed on the separate B43 branch.
The owner subsequently selected B43 for implementation as an experiment: it may be retained,
revised or abandoned if ordinary use shows it is not useful. The remaining rule choices in its
plan were confirmed before coding. B42 was subsequently selected as recorded above. Schema 22 is reserved above;
no phone trial result is claimed yet. Technical correctness is
required independently of usefulness; abandoning the trial must not delete data or require a
destructive schema downgrade. The plan records the feedback and final-decision evidence required.

**Checked on the host:** the forced full project gate passed in 240 s: **703 tests**, zero
failures/errors/skips; lint without errors; 581 page checks, 49 existing-WASM checks (no rebuild),
8 SAP checks, web database upgrade, 135 icon paths, 8 server checks and store metadata validation.
Command: `scripts/test-protracktor.sh --really --no-daemon --max-workers=2
-Pkotlin.compiler.execution.strategy=in-process`. Schema migration and production History SQL
were exercised with real SQLite. Deliberate mutations broke 12 of 42 focused tests; changing only
the exported schema broke its snapshot test. All mutations were restored before the full gate.
Two additional native-render tests passed; four EN/PL light/dark previews were inspected.

**Partly checked:** host Compose tests cover Browse/Random marks, playlist hiding, reactive
updates, PL accessibility and the Info action. **Not checked:** actual phone playback/UI,
background behaviour, upgrade on the owner's data and usefulness. See
[the local PR/handoff](https://github.com/przunk/protracktor/blob/2c35319cfa8b665398a36141248b45544bd68ebe/docs/PR_B43_PLAYED_BEFORE.md) for evidence and the trial checklist.
Verification logs/previews are retained in `dist/b43-verification/`. No merge, push, master/tag
change, AAB or publication occurred. Version name remains 1.0.0 under the owner's publication freeze.

**Release artifact checked:** built in 132 s from clean `7ea74d3` using
`scripts/build-release.sh --no-daemon --max-workers=2 -Pkotlin.compiler.execution.strategy=in-process`.
APK: `dist/protracktor-release-1.0.0-1111-7ea74d3-20261007-181629.apk`, **1.0.0 (1111)**,
11,706,010 bytes, minified/non-debuggable, arm64-v8a/armeabi-v7a/x86_64.
SHA-256: `d34b968427a24ef84fff0c039c02cf61aa678ded37eae2a4fe26146e7f6a6d84`.
Signature v2 verified with the same test certificate as the previous accepted APK
(`f97af50c2d869a2591dd82d24555894a2cc95927afb6c0a1fcd4bd197221e47e`), not a publication key.
The build script copied the APK to `dist/`; its legacy filename was renamed to include variant
and source SHA. The subsequent handoff-documentation commit does not change application sources.

## UnExoticA client identity — merged after owner approval, 2026-10-07

Branch `fix/unexotica-user-agent`, based on merged develop `9ed0359`. The owner approved the
follow-up previously deferred during the architecture work. Inspection found that Mod Archive
search already identifies the app, but `RemoteFiles` archive/index requests and their cookie
retry did not. The change shares the existing stable User-Agent between these callers;
no catalogue switch, cache, database or version policy changes are included.

**Partly checked:** five local HTTP-server tests cover archive downloads/cache reuse, uncached
index refreshes, verification retries, redirects and HTTP failures. All five failed before the
change because the server received the JVM default identity, then passed after the change.
A deliberate mutation omitting the header only on the cookie retry made exactly that test fail;
the correct implementation was restored.

**Checked:** forced full `scripts/test-protracktor.sh --really --no-daemon --max-workers=2
-Pkotlin.compiler.execution.strategy=in-process` passed in 251 s: 681 tests, zero failures/errors,
lint without errors, 581 page checks, 49 checks against the existing WASM, 8 SAP checks, web database
upgrade, 135 icon paths, 8 server checks and store metadata validation. No WASM rebuild is claimed.
Logs: `/tmp/protracktor-user-agent-{red,green,mutation}.log`,
`/tmp/protracktor-Protracktor-{test,lint}.log`; output root
`/tmp/protracktor-origin-sync-build.S8adiD`, Gradle cache `/tmp/protracktor-architecture-gradle`.
Copies of these logs and the build log are retained in `dist/unexotica-user-agent-verification/`.

**Release artifact checked:** `scripts/build-release.sh --no-daemon --max-workers=2
-Pkotlin.compiler.execution.strategy=in-process` passed in 611 s from clean `dfcc825`:
**1.0.0 (1103)**, minified/non-debuggable, arm64-v8a, armeabi-v7a and x86_64, minSdk 29/targetSdk 36.
The script copied the APK to `dist/`; its legacy filename was then renamed to include the variant
and source SHA: `dist/protracktor-release-1.0.0-1103-dfcc825-20261007-101401.apk`
(11,687,186 bytes). SHA-256: `a2574a0119583e331f6823a102e31867e4db0aed417a9249577e6ce5983db596`.
APK Signature Scheme v2 verification passed; the test certificate matches the accepted 1091 APK
(`f97af50c2d869a2591dd82d24555894a2cc95927afb6c0a1fcd4bd197221e47e`). No publishing key was used.
The later handoff-documentation commit does not change the application sources in this APK.

Jools's approval and the still-unconfirmed server whitelist are recorded in
[the catalogue plan](PLAN_UNEXOTICA.md). Server whitelisting remains unconfirmed.
After the phone report below, the owner explicitly authorised merging on 2026-10-07.
Merged into local `develop` as `3b22b92`, without conflicts. The merge tree exactly matches
`fix/unexotica-user-agent` at `1f8bfff`; app/native sources match the tested APK source `dfcc825`.
Tests and APK were not rebuilt for this content-identical merge. No push, master/tag change or
publication occurred; the task branch and other worktrees were retained.

**Phone result reported by the owner, 2026-10-07:** following the 1103 APK handoff, the owner
confirmed UnExoticA playback and the catalogue loading into cache. Device/Android version and the
installed build number were not separately restated. This does not establish a cold archive
download, subsequent archive reuse without a request, results for other catalogues/Mod Archive,
or activation of the server whitelist. Merge approval followed in a separate message.

**Regression checklist (not all steps separately confirmed):** install the test release as an update without clearing data. In Online Catalogues,
open UnExoticA and play a track from a game archive not already downloaded; another track from that
game should still reuse the cached archive. Check one fresh download from another catalogue and a
Mod Archive search. Refresh an index only if needed; no deletion of indexes or app data is required.
If Android rejects the update signature, keep the installed app/data and report the mismatch.

## Remote develop integration — checked, 2026-10-06

At the owner's request, fetched `origin/develop` to `485660b` and merged it into local develop
as `3cea6b0`, preserving all local work. Both accepted web PRs are included: #2 (`2390307`, merge
`75f3903`) centers transport controls and adjusts standalone bottom spacing; #1 (`31b9eb9`, merge
`485660b`) configures the optional iOS audio playback session. No conflicts, history rewrite,
push, master change or publication. `origin/develop` is now an ancestor of local develop.

**Checked:** the forced full project gate on the integrated sources passed in 341 s: 676 tests,
lint without errors, 581 page checks, 49 existing-WASM checks, 8 SAP checks, the web storage upgrade,
135 icon paths, 8 server checks and store metadata validation. Logs:
`/tmp/protracktor-Protracktor-test.log` and `/tmp/protracktor-Protracktor-lint.log`;
isolated output: `/tmp/protracktor-origin-sync-build.S8adiD`. Android/native sources are unchanged
from the owner-accepted local work. No new APK was needed or built for this web integration.
**Not checked:** real iOS background/screen-lock audio and browser/standalone layout on devices.
Host checks are not evidence of those behaviours; no new device result was supplied with the PRs.

## Random feedback correction stack — owner-accepted and merged, 2026-10-06

Owner authorised C97, then C98 and C99 on separate stacked branches, with analysis, regression
checks and PR handoffs. After the combined APK handoff, the owner reported successful operation
and explicitly authorised sequential merges on 2026-10-06. The first
branch is `fix/random-scope-history` from `develop` at `a72d098`, followed by
`fix/random-return-navigation` and `fix/random-filter-responsiveness`. All three have separately
verified release candidates. The final combined candidate is clean `9110e62`, **1.0.0 (1091)**:
676 host tests, lint without errors and all enabled project checks passed; minified, test-signed
release APK in main `dist/protracktor-release-1.0.0-1091-9110e62-20261006-234043.apk`.
Merged into local `develop` in order: C97 `43963a0`, C98 `ed2b4aa`, C99 `98e605f`.
Each merge tree exactly matched its corresponding branch tip; the final application sources
match the tested candidate. No tests or APK builds were rerun for these content-identical merges.
The owner's acceptance concerns the offered combined candidate; the installed build, device,
Android version and individual scenario results were not separately restated. No quantitative
latency measurements or independent external-user retests are claimed.
At this step no push, remote-web synchronization, master change or publication occurred; the
subsequent web integration and its repeated gate are recorded above. Historical PR drafts
and exact handoff evidence:
[Random feedback PRs](RANDOM_FEEDBACK_PRS.md).

## Architecture stabilization — merged after owner acceptance, 2026-10-06

Branch: `feature/architecture-stabilization`, based on `develop` at `1455422`.
Released reference: `v1.0.0` at `2aaeeeb` (versionCode 1055). The owner reported successful checks
of most functions and authorised merging the bounded refactor on 2026-10-06. It is now in local
`develop`; no push or release was performed. Long-term release approval remains separate.

**Resolved with C99 after owner acceptance, 2026-10-06: Random filter availability.** While Random played, platform and
Favourites filters were disabled. Downloading favourites with the sheet open enabled only that
chip; visiting Online Catalogues and downloading Modland/song metadata restored platform chips.
The exact installed build was not supplied. Code inspection found that opening the sheet does
not refresh its counters and unread counts look like missing data. This predates the extraction;
the exact sequence that left the owner's counts empty is not established.

**Earlier candidate, subsequently incorporated into C99 and merged into develop:**
`fix/random-filter-availability` first made each opening read platform and
favourites availability from the existing stores, independently of saved Browse snapshots. The
open sheet recounts after downloads and database preparation, distinguishes pending/failed reads
from empty indexes, and offers Retry on failure. No Random selection, database schema or playback
rule changed. Download/Retry use labelled icon controls. The following evidence describes that
earlier 1084 candidate; C99 below supersedes its sheet-local lifetime, not these historical results.

**Partly checked:** a Compose/Robolectric test failed on the old code's incorrect Download advice.
Eight panel tests then passed; deliberately removing refresh-effect keys made the download
completion test fail because Favourites remained disabled. The mutation was restored. Tests cover
fresh opening, stale counts on reopening, favourites download without closing, backfill completion,
retry, missing vs unindexed favourites, cancellation and unavailable platforms.

**Checked, 2026-10-06:** the full `scripts/test-protracktor.sh --really --no-daemon --max-workers=2
-Pkotlin.compiler.execution.strategy=in-process` gate passed in 260 seconds: 648 tests in 88 suites,
zero failures/errors/skips, lint without errors, 581 page checks, 49 existing-WASM checks, 8 SAP
checks, 135 icon paths, web v3→v4 storage upgrade, 8 server checks and store metadata validation.
The gate includes the eight Compose panel regressions and the new store-read test. Existing JDK
and isolated build/cache directories were retained. Local verification evidence is in
`dist/random-filter-verification/` in the worktree.

**Release-variant test APK checked, 2026-10-06:** `scripts/build-release.sh --no-daemon --max-workers=2
-Pkotlin.compiler.execution.strategy=in-process` passed in 164 seconds, with R8/resource shrinking.
Built from clean `0ab8c7dfa729e58bcc33113e75d9414ca6286ffd`, version **1.0.0 (1084)**,
all three ABIs. The copy in the main checkout is
`dist/protracktor-release-1.0.0-1084-0ab8c7d-20261006-215744.apk` (11,681,970 bytes).
SHA-256: `fff9efcc09fc0e4fa3212d1c85046ab862fdaf195c65b326184d60cbb64a235b`.
APK v2 signature verified; the Android Debug/test certificate matches the previous 1076 APK
(`f97af50c2d869a2591dd82d24555894a2cc95927afb6c0a1fcd4bd197221e47e`). This is a minified test
release, not a Play publication artifact. No push, fix-branch merge or publication was performed.

**Historical phone checklist for the earlier candidate (superseded by combined-build acceptance above):**
install as an update without clearing data. Start Random and open
Filter without first visiting Online Catalogues: available platforms should enable after the brief
check, with genuinely absent platforms still disabled. Close/reopen Filter and confirm the same
availability. If the favourites list is genuinely absent, download it with the sheet open and check
that Favourites enables on completion when matching Modland tracks exist; no catalogue or song
metadata re-download should be needed merely to refresh the chips. Keep existing data if Android
rejects the update; the installed/Play copy's certificate has not been inspected.

Worktree: `user/worktrees/architecture-stabilization` within the main project checkout. Its build
output is isolated at `/tmp/protracktor-architecture-build.Qogzrp`; set
`PROTRACKTOR_BUILD_DIR_ROOT` to that path for every build/test command in this worktree.
Since the 2026-10-04 environment restart, also set
`PROTRACKTOR_GRADLE_USER_HOME=/tmp/protracktor-architecture-gradle` (task-specific dependency cache).
Existing vendored sources and npm packages are linked read-only by convention; do not fetch or
patch through those links. The pre-existing WASM artefacts were copied, not shared for writing.

### Scope decision — 2026-10-05

The owner accepted stopping further extractions because the time cost did not justify completing
the original seven-file plan. Close out the existing controller changes through `6eb5577` with a
focused boundary review, limited comment corrections, full tests/lint and a release-variant test
APK. Library editing, sharing, playback extraction, Compose, web and native restructuring and the
repository-wide comment pass are **deferred, not completed**. At closeout no merge or release was
authorised; merge was subsequently approved on 2026-10-06, not a release.
The review, candidate and phone checklist are in [Architecture closeout](ARCHITECTURE_CLOSEOUT.md).

**Technical closeout checked — 2026-10-05:** clean candidate `3defe38`, version `1.0.0 (1076)`;
639 tests in 87 suites (zero failures/errors/skips), lint without errors and all enabled project
checks passed again. The minified, test-signed release APK was built for all three ABIs and copied
to the main checkout's `dist/protracktor-release-1.0.0-1076-3defe38-20261005-215545.apk`.
Its signature matches the preserved build 1057, not the separately test-signed 1055 baseline;
compatibility with the installed phone copy is unverified. See the closeout report for hashes,
commands and evidence. Phone/long-term checks and the previously recorded session-write risks
remain open. No merge, publication or new extraction occurred during closeout.

### Baseline evidence — 2026-10-03

- **Checked:** `scripts/check-tooling.sh` found JDK 17.0.19, SDK 36, NDK 29.0.14206865,
  CMake 3.31.6 and Ninja 1.12.1.
- **Checked:** `node scripts/check-page.mjs` exited 0; DOM/rule checks, not a real browser/audio
  session. The existing harness inlines modules, so the web extraction must also verify real
  module imports and the packaged entry point.
- **Checked:** `node scripts/check-web-store-upgrade.mjs` preserved history/playlists from a v3
  browser database through v4; `node scripts/check-server.mjs` passed all eight socket checks.
- **Partly checked:** `node scripts/check-engine.mjs` exited 0 against the copied existing WASM
  artefact. That demonstrates the artefact's boundary behaviour, not that current native sources
  were rebuilt. A fresh build and repeatable audio comparisons remain outstanding.
- **Checked:** 440 existing Android unit tests executed with no failures or skips before any
  production change. Five added `StateContractsTest` checks also passed before extraction; five
  deliberate contract mutations made all five fail, then were restored. The subsequent full
  `scripts/test-protracktor.sh --really` passed **445 unit tests**, lint (no errors), 581 page
  checks, 49 existing-WASM checks, 8 SAP checks, 135 icon paths, the web storage upgrade and 8
  server checks. Production code was still unchanged in this baseline run.
- **Baseline-run caveats:** the first sandboxed attempt could not open a network socket; the
  permitted retry downloaded Gradle 9.6.0. Adding the new test file while the first lint pass was
  running disrupted its analysis (`findFirCompiledSymbol`, new declaration not compiled). The
  repeat ran on unchanged source files and passed; no lint rule was disabled or suppressed.
- **Checked:** `scripts/build-web-engine.sh` rebuilt the baseline engine from current sources,
  using `EM_CACHE=/tmp/protracktor-architecture-build.Qogzrp/emscripten-cache`. Its module/WASM and
  build log are preserved locally in `dist/architecture-baseline/`. The fresh build subsequently
  passed all 49 engine checks. A real-file probe on 2026-10-04 played 5/5 fixtures without silence
  or refusal: libopenmpt's `test.mod` (two subsongs), gme's `test.nsf` (unknown duration), ASAP's
  `International_Karate.sap`, Hively's `karma.ahx`, and minimp3's `MEANDR_PHASE0.mp3`. Input/engine
  hashes and the probe log are in `dist/architecture-baseline/20261004-*`. This is a partial corpus,
  not coverage of every backend or a rendered-audio equivalence check; those remain pending.
- **Not checked:** release APK, current Android native/UADE probes, performance comparisons and
  long-term behaviour on a phone or in a real browser.

### Stages

- Baseline and ownership map: initial Android/web gate and state contracts checked
  (`ARCHITECTURE.md`, Current ownership). Workflow characterisation and native audio comparisons
  remain part of their respective extraction stages; this is not a completed refactor.
- Controller/workflow boundaries: state contracts, scan execution, search, track-file access,
  reference-data downloads, native replay installation, playback read-ahead, Browse cache/prefetch,
  playlist file transfer, playlist-session persistence/management, catalogue browsing and
  downloaded-data maintenance, local-folder browsing and Browse navigation/search publication extracted.
  Library editing, sharing and playback boundaries (including playback digressions) are deferred.
- Compose boundaries: not started; deferred by the scope decision above.
- Web modules: not started; deferred. No web PR synchronization is needed for this closeout.
- Native adapters and both build paths: no restructuring started; deferred.
- Repository-wide comment correction/shortening: deferred; only concrete misleading comments in
  reviewed branch code are included in closeout, separately from executable changes.
- Final documentation, full gate, test APK and manual checklist: technical closeout completed;
  results in [Architecture closeout](ARCHITECTURE_CLOSEOUT.md). Owner acceptance for merge was
  given on 2026-10-06; detailed per-scenario and long-term evidence was not supplied.
  Earlier checkpoint evidence follows below.

### Browse navigation and search publication extraction — 2026-10-05

Moved domain entry, Back routing and query/scope/result publication into `BrowseWorkflow`, using
the same Browse flow, coroutine scope, `BrowseNavigation` rules and `LibrarySearch` query service.
A narrow actions port keeps catalogue/folder/history reads with their existing owners; the
controller retains playback digressions and Android notice rendering. Construction must do no work.

Preserved immediate domain publication before refresh callbacks, Back's precedence and return
values, parent-read callbacks before the saved Back snapshot is published, and request capture
before launching a search. Filter toggles remain distinct from changing scope, live-result notices
keep their existing point, and failure/cancellation/late-result behaviour is unchanged. Tests exercise these through
the extracted workflow with real search logic and controlled sources/actions, including overlapping
reads and reentrant updates. No cancellation or stale-result guard was added during this move.

The controller delegates these interactions to the workflow and binds its actions directly to the
existing catalogue/folder readers and History entry point. The unused private favourite-count
forwarder was removed. No new job, mutable state store, native initialization or read starts during
construction. `PlaybackController.kt`: 3,394 → 3,270 lines; the workflow is 184 lines. The controller
was compared against the planned substitutions, then its integrated snapshot after cleanup.

**Checked:** 23 plain JVM tests passed before and after integration, using the actual
`LibrarySearch` with controlled sources. Two initial fixture mistakes were corrected without
changing production behaviour: a renderer callback shadowed its forwarding method and recursed,
and one assertion assumed an equal-value assignment replaced the flow's stored state object.

**Checked:** all 23 safeguards detected controlled mutations: 13 constructor/navigation checks,
then 10 search checks. Failures were assertions/comparisons, not compilation or VM failures;
the exact test names were matched against the passing integration report. The workflow was restored
and compared with its saved original, and the controller with its integrated snapshot.

**Checked:** full `scripts/test-protracktor.sh --really --no-daemon --max-workers=2
-Pkotlin.compiler.execution.strategy=in-process`: **639 tests in 87 suites**, zero
failures/errors/skips; lint without errors, 581 page checks, 49 existing-WASM checks, 8 SAP checks,
135 icon paths, browser storage upgrade, 8 server checks and store metadata; 226 seconds overall.
The existing shared JDK and isolated build/cache paths were used, without diagnostic JVM overrides;
`GRADLE_OPTS='-Dorg.gradle.daemon=false -Dorg.gradle.workers.max=2'` also limits the lint invocation.
Tests executed with `--rerun-tasks`. Focused/mutation/full XML, unit/lint logs and saved originals
are retained under `dist/architecture-baseline/20261005-browse-workflow-*`.

**Existing timing risks, not fixed:** an older search can publish its rows after a newer search
and a switch to History; Back can overwrite a reentrant parent-read publication with the snapshot
captured before the callback. Both orderings exist in `b635b5f` and are reproduced through controlled
sources/actions. Real database/main-dispatcher frequency and any cancellation/stale-result fix
remain separate, unapproved work. This refactor preserves the ordering rather than changing it.

**Not checked:** release APK, phone gestures/document providers, real-browser behaviour and
long-term stability. Host workflow tests and wiring inspection do not establish those outcomes.

### Local-folder browsing extraction — 2026-10-05

Move grant/forget/index-read and local-tree publication into `LocalFolderBrowser`, using the
existing scope, stores, Browse flow and `LocalTree` rules. Keep SAF grant/label/path handling in
an Android adapter. Scan ownership and navigation routing stay in the controller; both reuse
the extracted tree rendering instead of copying its rules.

Preserve grant → remember → refreshed rows → launch open → scan ordering, suspended folder-list
reads inside atomic retries, failure/cancellation behaviour, deferred native fingerprint reads
only for non-empty indexes, and completion-order publication after navigation. Opening a remembered
folder must not start a scan; explicitly granting one still does. Characterise root/level retention
during scan snapshots and the exact fields cleared by forget/close. No stale-result fix or new
permission/index policy belongs in this checkpoint.

The controller is now wired to the extracted browser, including the shared tree renderer used by
scan snapshots and Back. Its remaining body was compared with the prepared integration;
`PlaybackController.kt`: 3,456 → 3,394 lines. The native fingerprint stays a deferred lambda,
not a bound reference that could initialize the engine during construction.

**Checked:** 20 added tests (17 workflow/state, 3 Android grant/path checks) passed before
integration, in a temporary pre-integration control run, and after integration with build and
configuration caches disabled and `-Pkotlin.incremental=false` (68 seconds). An initial test incorrectly
expected an Unconfined nested launch to run before the scan callback; its expectations were
corrected to the observed event-loop order, without changing production code. These are host
checks, not measurements of Android's main dispatcher or real document providers.

**Earlier interrupted checks:** two integrated-build attempts stopped in JVM SIGSEGVs before test
execution (`MarkSweep::follow_array_chunk` and pointer adjustment in SerialGC), despite the
uncompressed-pointer flags from the previous checkpoint. The control run and an uncached integrated
run passed, but the first mutation batch then crashed in native method-signature rendering even
without caches or incremental compilation. Disabling Gradle instrumentation for one more attempt
(`-Dorg.gradle.internal.instrumentation.agent=false`, confirmed in the daemon context) also crashed,
in `MarkSweep::follow_stack`. Those runs did not establish the cause; none of these failed compilations counts
as mutation evidence. The integrated controller is retained, both deliberately changed helper files
are restored and compared with their originals. Logs and diagnostics:
`dist/architecture-baseline/20261005-local-folders-*`.

**Existing race, not fixed:** suspend folder A's index read, finish opening folder B, then release
A. The old tree is published while `openFolder` still names B. The same unguarded completion path
exists in `870042b`. A controlled-store test reproduces it; real-provider/SQLite frequency and a
stale-result fix remain separate, unapproved work.

**Checked after normal work resumed:** all 20 focused tests passed on the existing shared JDK,
without the earlier GC/JIT/pointer overrides. Controlled mutations then failed 4 constructor/Android
checks and 16 workflow checks; the unchanged constructor check passed in the latter batch. An
exact-name audit matched these failures to all 20 passing tests. Both helpers were restored and
compared with their saved originals; the integrated controller's hash remained unchanged.

**Checked:** full `scripts/test-protracktor.sh --really --no-daemon --max-workers=2
-Pkotlin.compiler.execution.strategy=in-process`: **616 unit tests in 86 suites**, zero
failures/errors/skips; lint without errors, 581 page checks, 49 existing-WASM checks, 8 SAP checks,
135 icon paths, browser storage upgrade, 8 server checks and store metadata; 231 seconds overall.
The two isolated build/cache variables above were set, with
`GRADLE_OPTS='-Dorg.gradle.daemon=false -Dorg.gradle.workers.max=2'` also limiting the separate lint
invocation. Unit tests executed with `--rerun-tasks`. No Java distribution or project build setting
was changed. Focused/mutation XML and logs plus the full-gate summary are retained under
`dist/architecture-baseline/20261005-local-folders-normal-*`. Full-suite XML was inspected before
the pause, but its temporary directory did not survive the turn boundary; it was not archived.

**Not checked:** release APK, real document providers/phone behaviour and long-term stability.
The full gate checks the host paths, not Android's main-dispatcher timing or device performance.

**Owner decision — 2026-10-05:** the owner declined a different JDK, reported a faulty memory
region under investigation, and requested normal work to continue. This is an owner-reported
hardware finding, not an independently verified diagnosis by this agent. Resume checks with the
existing toolchain and ordinary JVM settings; do not install Java, restart WSL, change shared
tooling or investigate other projects. Three other agents are working elsewhere in the workspace.
Keep this worktree's isolated build/cache paths and run one verification job at a time. A crashed
attempt remains incomplete evidence; the full gate is still required before the next extraction.

### Downloaded-data maintenance extraction — 2026-10-05

Checkpoint scope: move cache, catalogue-index/archive, downloaded-metadata and replay deletion
workflows into `LibraryMaintenance`, using the same stores, cache, scope, background dispatcher
and native configuration callback. Publish through a narrow adapter to the existing player/Browse
flows; keep file operations and localized messages Android-bound. Delete no new category of data.

Preserve immediate notices/optimistic rows before launching work, the catalogue download guard,
ordered store/file reads, Modland favourite-scope handling, archive deletion's ignored Boolean,
metadata failure refreshes, replay adoption cleanup and package-version/configuration timing.
Cache-cleared messages must still be created inside atomic state-update retries. Characterise
failures and cancellation as they exist, including the non-cancellable catalogue cleanup; no
rollback, new guard or improved error policy belongs in this extraction.

The controller now delegates these four workflows to `LibraryMaintenance`. The state adapter
writes the existing flows; Android adapters use the existing remote cache, replay directories and
localized resources. The remaining controller code was compared with its pre-extraction copy.
`PlaybackController.kt`: 3,602 → 3,456 lines. The workflow is 206 lines, state adapter 87,
file adapter 23 and notice adapter 32; no second store, cache or playback owner was introduced.

**Checked:** 21 added tests (15 workflow/state, 3 Android file boundaries, 3 PL/EN notice checks)
passed before and after integration. Two initial exception-identity assertions were corrected:
coroutine stack recovery can copy an exception across a dispatcher while retaining the original
as its cause; the tests now require the same type/message and original cause. Production exception
handling was not changed.

**Checked:** all 21 tests failed under controlled mutations: 7 construction/file/message checks,
then 14 workflow/state checks (the unchanged constructor test still passed in the second batch).
The audit matched failed test names against the 21-test integration run. All four helper files
were restored and compared with saved originals; the controller still matched its integrated copy.

**Checked:** full `scripts/test-protracktor.sh --really --no-daemon
-Pkotlin.compiler.execution.strategy=in-process`, with the environment below: **596 unit tests**
in 84 suites, zero failures/errors/skips, lint without errors, 581 page checks, 49 existing-WASM
checks, 8 SAP checks, 135 icon paths, browser storage upgrade, 8 server checks and store metadata;
253 seconds overall. Unit tests were forced to execute, not served from cache.

**Verification impediment:** JVM SIGSEGVs interrupted compilation, including SerialGC/C1 on both
the shared JDK and a task-local copy, and SerialGC with JIT disabled. The interpreter attempt
failed in `FastScanClosure<DefNewYoungerGenClosure>::do_oop`; this does not establish the cause.
Those interrupted mutation attempts produced no test results and are not counted as evidence.
A single read-only `rg` pipeline also exited 139; a subsequent narrower query succeeded. The
later focused pass, both successful mutation batches and full gate used a task-local copy of the
same JDK 17.0.19+10 (`PROTRACKTOR_JAVA_HOME=/tmp/protracktor-architecture-build.Qogzrp/toolchain/jdk`),
`JAVA_TOOL_OPTIONS='-XX:+UseSerialGC -XX:TieredStopAtLevel=1 -XX:-UseCompressedOops -XX:-UseCompressedClassPointers'`
and `-Pkotlin.compiler.execution.strategy=in-process --no-daemon`. This is a command-local
workaround, not proof of the cause or lasting environment stability. No repository toolchain or
shared configuration was changed. Logs, saved originals and diagnostics are under
`dist/architecture-baseline/20261005-library-maintenance-*`.

**Not checked:** real-phone deletion/SQLite/native timing, release APK and long-term behaviour.
No schema, deletion policy, native lifetime or UI change was intended or included.

### Catalogue-browsing extraction — 2026-10-04

`CatalogueBrowser` now owns catalogue/format/author reads and overview/platform/favourite counts
in the existing scope, with the same stores. `CatalogueBrowseState` publishes into the existing
Browse flow; `AndroidCatalogueStorage` reads the existing remote cache, replay stores, database
file and pairing preferences. `CatalogueTracks` is the shared row mapping for browsing, search,
neighbours and Random. Domain navigation, local folders and playback remain controller-owned.

Preserved: early held-data flags, a separately launched platform count, store/file read order and
dispatchers, selection capture before launching versus after starting, loading/error/cancellation
behaviour and unrelated Browse fields. The backend fingerprint remains a deferred lambda evaluated
inside atomic-update retries; construction performs no I/O or native initialization.

**Checked:** 20 added tests (13 workflow/state, 4 Android storage, 3 mapping) passed before and
after controller integration. The initial mapping fixture was corrected against the existing
`asma://` URI and `ASMA (Atari 8-bit)` label, without changing production behaviour. All 20 failed
under controlled mutations: 8 construction/storage/mapping checks, then 12 workflow checks.
All four production helper files were restored and compared with their saved originals.

**Checked:** full `scripts/test-protracktor.sh --really` with the JVM flags below: **575 unit
tests**, zero failures/errors/skips, lint without errors, 581 page checks, 49 engine checks,
8 SAP checks, 135 icon paths, browser storage upgrade and 8 server checks; 403 seconds overall.

**Verification environment:** Kotlin daemon disconnects were followed by JVM SIGSEGVs in G1GC
and then C2 compilation (`hs_err_pid30933.log`, `hs_err_pid33894.log`, archived with the checkpoint
diagnostics). The SIGSEGV attempts stopped before test results and are not counted as mutation
evidence. The successful second mutation batch and full gate used command-local
`JAVA_TOOL_OPTIONS='-XX:+UseSerialGC -XX:TieredStopAtLevel=1'` and
`-Pkotlin.compiler.execution.strategy=in-process --no-daemon`; no project/toolchain configuration
was changed. Logs/XML: `dist/architecture-baseline/20261004-catalogue-browser-*`.

**Existing race, not fixed:** a controlled read suspended the first catalogue query, completed a
second selection, then released the first query. Its groups replace the newer list while the
second catalogue remains selected. The same unguarded completion ordering exists in `6e14931`.
This is host sequence evidence, not a phone reproduction; stale-result protection needs separately
approved work, not a hidden behaviour change inside this extraction.

`PlaybackController.kt`: 3,727 → 3,602 lines. The new workflow is 161 lines, state adapter 103,
Android adapter 29 and shared mapping 31. **Not checked:** phone UI, actual SQLite-driven browse
timing, release APK or long-term behaviour. No schema, cache policy, native lifetime or UI change.

### Playlist-session extraction — 2026-10-04

`PlaylistSession` owns restoration, the active persistence ID, both debounced save jobs,
Save/Discard and playlist create/rename/delete/switch sequencing. It uses the controller's existing
scope and `LibraryStore`. `PlaylistSessionState` applies narrow session transitions to the same
player/Browse flows, without an independent queue or state copy. Native stop and metadata-resolution
callbacks remain controller-owned; construction performs no I/O.

Preserved: restore without autoplay; the pre-restore settings-save guard; 400 ms settings and
1500 ms track debounce; sampling after the delay; dynamic localized names/notices; explicit edits
versus settings; last-playlist reset; exceptions/cancellation; and the suspended list reads inside
atomic state-update retries. Other controller consumers read the session's single persistence ID,
which still changes before the destination playlist's tracks finish loading.

**Checked:** 18 initial workflow tests and one new PL/EN notice test passed before and after
integration. The first edit-mutation batch exposed a weak assertion: the test copied `dirty` from
the already-mutated state into its expectation. It now explicitly requires dirty state while the
write is pending and supplies an independent dirty edit. One more test characterises a pending
track write during a suspended switch. All **20 new tests** (19 session, one resource) subsequently
failed under controlled mutations: 1 construction, 7 restore/resources, 10 edits, 2 switch-order.
All three mutated sources were restored and compared with their pre-mutation copies.

**Checked:** full `scripts/test-protracktor.sh --really`: **555 unit tests**, zero failures/errors/
skips, lint without errors, 581 page checks, 49 engine checks, 8 SAP checks, 135 icon paths, browser
storage upgrade and 8 server checks. Logs and mutation XML:
`dist/architecture-baseline/20261004-playlist-session-*`.
`PlaybackController.kt`: 3,938 → 3,727 lines; session workflow: 216, state adapter: 118.
**Not checked:** this session against a real phone/SQLite instance, device UI, release APK or
long-term behaviour. No schema, stored format, native ownership or lifecycle change.

### Pre-existing session-write races — host sequence reproduced, not changed

Two controlled-store tests expose timing risks already present in `c1b0f0c`, before this
extraction. They exercise the real session/state pair, but do not measure how often either
interleaving happens with Android SQLite or on a phone:

- **Save completion can hide a newer edit.** Save captures the initial tracks, suspends in
  `replaceTracks`, then clears `dirty` on the current UI state. An edit made during that write
  remains visible but is marked clean, although only the earlier tracks were persisted.
- **A pending metadata write can target the destination with the previous queue.** Switching sets
  the persistence ID before awaiting destination tracks, while the UI queue still holds the old
  list. If the 1500 ms write runs in that interval, its late reads pair the new ID with the old
  tracks. The controlled store records that replacement; this is a data-loss risk, not a proposed
  new persistence policy.

The extraction preserves these sequences rather than silently introducing a behaviour fix.
Separate owner-approved work should guard save completion against newer edits and make a pending
write's playlist identity and track snapshot consistent across activation. The phone reproduction
and the fix remain open; passing characterisation tests do not mean these risks were removed.

**Follow-up, 2026-10-13:** the owner selected these two races for a separate behaviour fix, C103.
The implementation and regression results are recorded above; C103 subsequently merged with
owner approval at `f4c9bf1`. Individual playlist phone results were not supplied. This does not
change the historical extraction result.

### State-contract extraction — 2026-10-03

Preparation commit: `e48ccd8`. `Message`, `PlayerUiState`, `BrowseState` (with `BrowseDomain` and
`HeldSets`) and `DownloadKeys` now live in their own files in the same package. Their definition
bodies were compared byte for byte with the baseline, excluding file headers/imports. Defaults,
derived properties, message identity and mutable state ownership are unchanged. Existing comments
moved with their declarations; their correction remains a later, separate stage.

`PlaybackController.kt`: 5,287 → 4,771 lines. This is only the state boundary, not completion of
the controller refactor. No new dependency, storage change, dispatching change or version bump.

**Checked:** `scripts/test-protracktor.sh --really` after extraction: 445 unit tests, lint without
errors, 581 page checks, 49 engine checks, 8 SAP checks, 135 icon paths, browser storage upgrade
and 8 server checks. **Not checked:** behaviour on a phone, release APK and long-term stability.

### Scan-execution extraction — 2026-10-04

`ScanBatch` now executes the existing `IncrementalScan.Plan`. The controller still owns the scan
job, lists the SAF tree, takes the decoder fingerprint, probes through `NativeEngine`, binds the
folder's index writes, and updates notifications/Browse. The helper receives those operations;
it does not create a scope, dispatcher, database connection or second mutable UI state.

Preserved: four parallel-safe workers plus one serial worker on the original dispatchers;
200-file checkpoints polled every second; append-only partial writes; whole-folder replacement
only at completion; cancellation writes under `NonCancellable` and rethrows; progress every 25
files and snapshots at most twice a second, except completion. Native probing/stream lifetime
and the cancellation boundary around worker execution were not redesigned.

**Checked:** 7 new `ScanBatchTest` cases plus the 4 existing incremental-scan cases. Controlled
mutations of persistence, the worker limit, dispatcher selection and snapshot throttling made
all 7 new cases fail; the restored source matched the saved pre-mutation copy. A test initially
compared thread names and failed because coroutine debugging appends a suffix; it now checks
actual `Thread` identity, so it tests dispatching rather than a diagnostic label.

**Checked:** full `scripts/test-protracktor.sh --really`: **452 unit tests**, lint without errors,
581 page checks, 49 engine checks, 8 SAP checks, 135 icon paths, browser storage upgrade and 8
server checks. Evidence: `dist/architecture-baseline/20261004-scan-{tests,mutations,full-gate}.log`.
`PlaybackController.kt`: 4,771 → 4,707 lines; `ScanBatch.kt`: 115 lines.
**Not checked:** real SAF-provider cancellation/scan behaviour on a phone, release APK and long-term
playback. The remaining architecture/comment stages are still open.

### Library-search extraction — 2026-10-04

`LibrarySearch` owns the sequential four-source query and its result calculation. The controller
still owns the coroutine and Browse/player state, captures the query/scope/indexed catalogue IDs,
and presents the same localized messages before the count queries. The injected operations call
the existing stores and live search; no new job, cache, dispatcher or search policy was introduced.

Preserved: index then playlist then catalogue then live reads; source precedence and display order
through `SearchResults`; platform filtering; explicit/empty catalogue selections; per-source caps;
counts only when index/catalogue results reach the cap; no live request for a blank query; separate
unreachable/unreadable outcomes without discarding successful sources.

**Checked:** 11 `LibrarySearchTest` cases and the existing `SearchResultsTest` pass. Controlled
mutations of routing, filtering, limits and live search made all 11 new cases fail; restoration
was checked against the saved source. Logs: `dist/architecture-baseline/20261004-search-{tests,mutations}.log`.
`PlaybackController.kt`: 4,707 → 4,633 lines.
**Checked:** full `scripts/test-protracktor.sh --really`: **463 unit tests**, lint without errors,
581 page checks, 49 engine checks, 8 SAP checks, 135 icon paths, browser storage upgrade and 8 server
checks (`dist/architecture-baseline/20261004-search-full-gate.log`).
**Not checked:** live-network/phone interaction, release APK and long-term behaviour.

### Track-file extraction — 2026-10-04

`TrackFiles` now routes main/companion reads for playback, metadata probing, sharing and prefetch.
`AndroidTrackFileAccess` binds the existing `RemoteFiles`, content resolver and LHA bridge; it has
no independent cache, scope or decoder handle. The controller passes its existing archive worker
and retains Browse refresh ownership. LHA is still initialized only when extraction is called.

Preserved: HTTP/archive/local source precedence, success-only cache refresh, companion naming and
missing-companion tolerance, IO dispatch for normal document reads, and archive-worker dispatch
for LHA extraction. Scanning calls the synchronous document adapter on its existing scan lane.
Streams remain closed per read, including when a provider fails. Playback and prefetch policies
were not changed. `PlaybackController.kt`: 4,633 → 4,514 lines; routing: 111, Android adapter: 59.

**Checked:** 12 routing/dispatcher tests and 4 Android-adapter tests, plus 5 existing companion-rule
tests. The adapter tests use Robolectric API 29 and synthetic provider streams, not a phone.
All 16 new checks failed under controlled mutations (15 in the first batch, the success-refresh
check in a separate mutation); both source files were restored and compared with their saved
copies. Evidence: `dist/architecture-baseline/20261004-track-{files-tests,files-mutations,refresh-mutation}.log`.
**Checked:** post-integration `scripts/test-protracktor.sh --really`: **479 unit tests**, lint without
errors, 581 page checks, 49 engine checks, 8 SAP checks, 135 icon paths, browser storage upgrade and
8 server checks (`dist/architecture-baseline/20261004-track-files-full-gate.log`).
**Not checked:** real provider/network/decoder behaviour on a phone, release APK and long-term playback.

### Catalogue/metadata download extraction — 2026-10-04

`LibraryDownloads` owns catalogue, SID-length, songdb and favourite workflows and their selected-run
job. It receives the controller's existing scope, `StoreDownloadSources` reusing existing stores
and cache, a narrow Browse/message port, and `AndroidDownloadText`. Progress guards/counts stay in
the same Browse state. No second scope, state flow, store instance or download-policy implementation
was added. Native replay installation and cache eviction/prefetch remain separate pending boundaries.

Preserved: `DownloadPlan` ordering, archive storage before indexing, all listed formats passed to
the store, parse dispatching, partial persisted results, busy-step skips, success/failure counting,
per-step cleanup, and message creation/publication timing. `DownloadNotice` is exhaustive at the
Android resource mapping. Both privacy/source-credit tests now read the URL from its actual owner;
the first integration compile caught the second old reference, which was updated without relaxing
the check. Source URLs, their pinned revisions and all PL/EN resource texts are unchanged.

**Checked:** 13 workflow tests passed before controller integration; after integration those tests,
3 Robolectric PL/EN resource-mapping tests, and the existing privacy/source-credit checks passed.
Controlled mutations of persistence, routing/order, failure reporting, guards, cancellation cleanup
and text mapping made all 16 new tests fail. Both changed files were then restored and compared
with saved copies. Logs: `dist/architecture-baseline/20261004-downloads-{tests,integration-tests,mutations}.log`.
**Checked:** full `scripts/test-protracktor.sh --really`: **495 unit tests**, lint without errors,
581 page checks, 49 engine checks, 8 SAP checks, 135 icon paths, browser storage upgrade and 8 server
checks (`dist/architecture-baseline/20261004-downloads-full-gate.log`).
`PlaybackController.kt`: 4,514 → 4,183 lines;
`LibraryDownloads.kt`: 382 lines, including the existing comments and source constants.
**Not checked:** actual network/database/native-fingerprint integration on a phone, release APK and
long-term behaviour. The source adapter was compiled and its forwarding reviewed, not run against
the owner's database or live servers.

### Native replay download coordination — 2026-10-04

`ReplayDownloads` runs the sc68/UADE sequence through narrow native and presentation operations.
The controller retains its existing scope/background dispatcher, progress owner and native
bindings. Startup and post-delete UADE configuration remain in the controller. Constructing the
new collaborator neither starts work nor initializes an engine.

Preserved: sc68 before UADE; activation only for a nonzero result, on the existing background
worker; separate group/source progress guards; partial/failed result messages; exception propagation
and cleanup after success, failure or cancellation. The same `AndroidDownloadText` now formats
replay notices; all resource strings and native/download implementations are unchanged.

**Checked:** 6 new workflow tests and 4 resource-mapping tests passed before and after integration
(7 new tests in total, plus an expanded existing label test). Controlled mutations of progress,
guards, zero-result handling, dispatching, exception propagation, cancellation cleanup and replay
text mapping made all 6 workflow tests and the 2 replay-related resource checks fail. Both mutated
files were restored and compared byte for byte with their saved copies. Logs:
`dist/architecture-baseline/20261004-replays-{before-integration-tests,integration-tests,mutations}.log`.
`PlaybackController.kt`: 4,183 → 4,133 lines; `ReplayDownloads.kt`: 98 lines.
**Checked:** full `scripts/test-protracktor.sh --really`: **502 unit tests**, lint without errors,
581 page checks, 49 engine checks, 8 SAP checks, 135 icon paths, browser storage upgrade and 8 server
checks (`dist/architecture-baseline/20261004-replays-full-gate.log`).
**Not checked:** actual network/filesystem/native activation on a phone, release APK and long-term
playback. Injected host operations check orchestration, not the emulator's ability to use a download.

### Playback read-ahead ownership — 2026-10-04

`PlaybackReadAhead` owns the in-memory byte map and prefetch job. The controller retains selection
of playlist/random/external-file candidates and uses the same `TrackFiles.read` path and process
scope. The preparation body matches the former controller body byte for byte after substituting
the injected reader. Opening consumes the same entry; stopping calls cancel/clear at the same point
before closing the native track. No dispatcher, mutex, extra scope or disk cache was introduced.

**Checked:** 8 new workflow tests: concurrent reads, consume-on-open, wanted-ID eviction, retained
entries, 4 MB per-file/8 MB total limits, cancellation on replacement/stop, early-return ordering
and propagated read errors. Two controlled mutation batches made all 8 tests fail (6 then 2);
the source was restored and compared with its saved copy after each. Evidence:
`dist/architecture-baseline/20261004-read-ahead-{tests,mutations-1,mutations-2}.log`.
**Checked:** full `scripts/test-protracktor.sh --really`: **510 unit tests**, lint without errors,
581 page checks, 49 engine checks, 8 SAP checks, 135 icon paths, browser storage upgrade and 8 server
checks (`dist/architecture-baseline/20261004-read-ahead-full-gate.log`).
`PlaybackController.kt`: 4,133 → 4,087 lines; `PlaybackReadAhead.kt`: 73 lines.

**Existing behaviour retained:** when a new candidate list is empty or entirely held, preparation
returns before cancelling an older read. That read can subsequently store its bytes even though
its ID is no longer wanted. The test reproduces both cases; changing the policy is outside this
extraction. Folder cache-ahead and disk-cache ownership remain separate pending boundaries.
**Not checked:** real network/provider read-ahead, playback latency or memory behaviour on a phone,
release APK and long-term stability.

### Browse cache coordination — 2026-10-04

`BrowseCache` combines `OnPhone` source checks with the `FolderPrefetch` workflow. Disk-cache/shared
fetches remain owned by the existing `RemoteFiles`. The controller retains both observers and their
`collectLatest` cancellation, the refresh stale-result guard, the platform network check and Browse
state. Source/progress operations only forward to those existing owners; construction does no I/O.

Preserved: IO dispatch for cache checks, one archive-presence query per call, the full track-list
folder key and Modland-only eligibility, existing network/budget/parallelism rules, progress events,
and cleanup on cancellation. An absent folder clears fetch progress, not cached-row marks. The
controller still checks file existence and nonzero length for an archive; that binding was reviewed
and compiled, not exercised against a real Android cache.

**Checked:** 9 new workflow cases and 16 existing `OnPhone`/`FolderPrefetch` cases passed before
integration. The first run found a test assumption, not a production defect: a propagated exception
was a different object with the same type/message. The corrected test checks type, message and
the original instance in the cause chain. Controlled mutations made all 9 new tests fail; the
restored production source matched the saved copy. Logs:
`dist/architecture-baseline/20261004-browse-cache-{first-tests,tests,mutations}.log`.
**Checked:** full `scripts/test-protracktor.sh --really`: **519 unit tests**, lint without errors,
581 page checks, 49 engine checks, 8 SAP checks, 135 icon paths, browser storage upgrade and 8 server
checks (`dist/architecture-baseline/20261004-browse-cache-full-gate.log`).
`PlaybackController.kt`: 4,087 → 4,043 lines; `BrowseCache.kt`: 91 lines.
**Not checked:** real provider/network/Android cache behaviour, navigation on a phone, release APK
and long-term playback. The unchanged observer wiring was reviewed, not executed in a device UI.

### Playlist file transfer — 2026-10-04

`PlaylistTransfer` owns the M3U import/export sequence and matching precedence, reusing `PlaylistFile`
and the existing `LibraryStore`/`LibraryIndexStore`. The controller retains the working queue,
playlist activation and UI flows. The initial export selection is captured once; exporting an
active playlist uses its unsaved queue, while another playlist reads the store. Import still creates
a separate playlist, including when all offered entries remain unresolved, and passes duplicates
to the store while reporting offered/found counts as before.

Preserved: known ID → remote/catalogue ID → remote location → indexed filename/size resolution;
Default-dispatcher parsing/writing; document-name fallback and filename sanitization; persistence
and refresh/notice order; cancellation and exception propagation. `AndroidPlaylistFiles` reads UTF-8
on IO through the existing document adapter and forwards shared copies without changing their
calling dispatcher. The share intent fields, URI grant, manifest and PL/EN resources are unchanged.

**Checked:** 11 new workflow tests, 3 Android-file/intent tests and 2 PL/EN notice tests passed
before integration, alongside the existing `PlaylistFileTest` cases. Robolectric API 29 actually
read a shared file through the manifest-declared `FileProvider`; synthetic document streams checked
IO dispatch and closing on success/failure. This is host evidence, not another app opening a share
on a phone. Two controlled mutation batches made all 16 new tests fail (9 then 7); all 3 mutated
files were restored and compared with their saved copies. Logs:
`dist/architecture-baseline/20261004-playlist-transfer-{tests,mutations-1,mutations-2}.log`.
**Checked:** full `scripts/test-protracktor.sh --really`: **535 unit tests**, lint without errors,
581 page checks, 49 engine checks, 8 SAP checks, 135 icon paths, browser storage upgrade and 8 server
checks (`dist/architecture-baseline/20261004-playlist-transfer-full-gate.log`).
`PlaybackController.kt`: 4,043 → 3,938 lines; transfer workflow: 162, Android files: 35, text: 25.
**Not checked:** real document providers, the chooser/receiving app, import against a phone database,
release APK or long-term behaviour. No new database transaction, schema or persistence format.

### Pre-existing cancellation-notice defect — reproduced, not changed

`downloadSelected` tests `isActive` inside `withContext(NonCancellable)` before showing the stopped
notice. This observes the non-cancellable scope, so the notice is not emitted. **Checked:** the
production workflow's cancellation test completes ASMA, cancels while awaiting SID lengths, retains
the completed data, clears progress and emits no notice. A subsequent run succeeds. This is host
evidence with fake data ports, **not** a phone check. The copied cancellation boundary and its
behaviour were preserved; correcting the notice requires the owner's separate approval.

## What works

### Web PWA — published; owner confirmed installation and normal operation, 2026-10-11

Prepared on `fix/web-pwa`, merged through GitHub PR #3 and integrated locally on 2026-10-11.
Deployment and the subsequent owner report are recorded at the top; the original evidence below
predates that confirmation. The web player now declares its manifest,
standalone display mode, Android-derived launcher and maskable icons, an Apple Touch Icon and a
favicon. The system bar follows the selected light/dark theme. A service worker caches the player,
WASM engine and bundled notices for offline startup; remote music and pairing still need a network.
Content hashes version the cache, and updates wait for all player windows to close.

Contributor verification recorded on 2026-10-08: page, server, icon and PWA policy checks passed.
Chrome reported no manifest or
installability errors at both the root URL and a simulated `/Protracktor/` GitHub Pages deployment.
With its network disabled, both deployments reloaded the player, compiled the cached 3,221,869-byte
engine and loaded the AudioWorklet. The maskable icon is opaque and its entire mark fits inside
the safe circle. Installation and appearance on physical Android/iOS devices remain unverified.

The checkout lacks native dependency sources, so `package-web.sh` cannot restage their licences
here. Packaging passed in an isolated copy using the exact notices from `origin/gh-pages`, whose
engine and component-table SHA-256 hashes match this checkout. No native sources were fabricated
and no public deployment was changed. Usage and icon regeneration are in `docs/WEB_SERVER.md`.

### Existing application status

A usable player, as far as anything can be called that without a device saying so.

- **Formats**: tracker modules through libopenmpt (MOD, XM, S3M, IT and dozens more), Atari ST
  through sc68 3.0.0b (SNDH, `.sc68`), the AY register dumps `.ym` and `.vtx` through ZXTune and
  lhasa (`docs/PLAN_FORMATS.md` §8 — they are LHA-packed, which is why they did not play until
  2026-09-09), Atari 8-bit through ASAP (SAP and 13 tracker formats), Commodore 64
  through libsidplayfp (PSID, RSID), seven console families through game-music-emu (NSF, GBS,
  SPC, VGM, HES, AY, KSS — measured, and opened at the first track that has sound in it, because HES
  and KSS routinely hold nothing at track 0), and the Amiga synth trackers through HivelyTracker
  (AHX, HVL). **The Amiga custom formats through UADE** — TFMX, Hippel, David Whittaker, Sonic
  Arranger and thirty more, once the replay routines are downloaded (`docs/BACKLOG.md` A44).
  **Heard on a phone: TFMX (`mdat.coolbass` with its samples)**, which exercises the second
  process, the fetched companion and the file lookup at once. The other 34 names are checked on
  the host only — 140 of 140 through the unchanged engine, under a parent directory that cannot be
  listed, as `/data` cannot on a phone. **Not yet on a phone**: the other formats, subsong
  switching, deleting the routines. **Length and seeking**: the formats state no length, so a second emulator plays the subsong silently to its end once playback starts, and the total and the slider appear a few seconds in; a tune that loops stays without either.
  The browser cannot have them at all. Backends sit behind one interface and are asked what they can do rather
  than assumed — libopenmpt and HivelyTracker seek, sc68 and libsidplayfp cannot, and the UI
  reflects that.
- **Ships no code it has no right to.** Of sc68's 99 replay routines the APK carries one — sc68's
  own — and fetches the rest from sc68 if the user asks (`docs/LICENSES.md`). SNDH is unaffected;
  `.sc68` waits for the download.
- **Online archives**: Modland, browsed offline from a downloaded index and fetched per track; ASMA,
  which arrives as one 20 MB archive and then needs no network at all; The Mod Archive, searched
  live; and **UnExoticA**, Amiga game soundtracks — 4,338 playable tunes across 924 games, indexed
  from `songdb` and fetched a game's `.lha` at a time. That one is behind a switch and can be taken
  out again, because its archive's maintainers were asked a question on 2026-09-08 and have not
  answered it yet (`docs/PLAN_UNEXOTICA.md`). HVSC's song lengths give SID tunes the duration the
  format cannot carry. Fetched music is capped at 512 MB, least recently used first
  (`docs/ARCHITECTURE.md` §19).
- **Library**: folders granted through the storage access framework, remembered between sessions.
  A folder is **scanned by opening every file with a real decoder**, not by reading its name, and
  the result is stored so later launches read an index instead of walking the tree
  (`docs/ARCHITECTURE.md` §18).
- **Playlists**: several, named, created, renamed, deleted, exported and imported as M3U, switched
  from the top bar — each row's own actions behind the same three dots a track row uses. Shuffle and
  repeat work inside the active one, and repeat-one on a file with several tunes in it loops the
  file rather than its last tune.
- **Storage you can reclaim**: everything the app has downloaded — the fetched-music cache, each
  catalogue index and archive, the HVSC song lengths — is listed with its size and a delete, and
  every one of them is re-fetchable (`docs/ARCHITECTURE.md` §19).
- **Transport**: a dock on every screen with shuffle, previous, play, next and repeat. Shuffle keeps
  a real history so backward returns to what was actually played; without shuffle, backward is the
  row above. Seeking where the backend allows it.
- **Survives a restart**: playlist, settings and the track last played all come back.
- **Survives leaving the app**: a foreground service with transport in its notification. Audio focus
  and becoming-noisy are honoured.
- **Both languages**, Polish and English, selectable inside Settings alongside the system default.
  The override applies before the activity reads resources and works across the supported API range.

Verified here: **135 unit tests**, genuinely executed rather than served from the build cache — the
full `:app:testDebugUnitTest` task ran on 2026-09-04 after the Settings changes. A controlled defect
in the language mapping made two new tests fail before it was restored. `lintRelease` and a release
build through R8 are green; the build keeps all 13 JNI symbols, three ABIs and the one bundled sc68
replay routine. Every backend was also run on the host against real
files, which is where the measured coverage in this file comes from — **including the console
families and libopenmpt, both measured 2026-09-04 having never been** — the consoles 116 of 141, up
from 100 once the measurement found what was wrong, and libopenmpt 390 of 457, which is 98.0% of the
178,795 Modland files it is handed (`docs/PLAN_FORMATS.md` §0b and §2). **Every backend now has a
number.**

Verified on a device: modules play (2026-08-31), SAP plays (2026-09-01), reordering a
playlist works (2026-09-02), and on the evening of 2026-09-02 all five of round 3 were tested on a device —
**Random rolling on, history, "more from this author" and both shares all work**. Read-ahead was
working and fetching serially, which was audible; that is fixed.

**2026-09-04, round 6 on a device.** Indexes correctly reported themselves stale after the name list
changed, and re-indexing worked. **OctaMED `.mmd0`–`.mmd3` and Oktalyzer `.okta` play** — the 5,653
files that were always playable and never offered. Freeing and re-downloading the ASMA archive
works. Adding to a playlist reports what it did; the playlist row menus work.

**2026-09-05.** Ten things found by using it and fixed the same night: deleting the only playlist
did nothing at all, an exported list came back named after its document id, the top bar changed
shape with the playlist's name and its gaps came from three unrelated sources, and changing the
language or theme threw you out of Settings. Theme and language are segmented choices now, with a
separate switch for the wallpaper palette. All confirmed on a device.

**2026-09-04, afternoon.** The console formats confirmed on a device: HES plays, KSS mostly plays,
subsongs walk. Two faults came out of that session and are fixed — the search for an audible track
stopped after twelve tracks and missed `aleste 2.kss`, whose first tune is number 47 of 256, and the
subsong strip yanked the view back to the playing tune, making the far end of a long file unreadable
while it played.

Three things came back wrong and are fixed: **repeat-one still advanced through subsongs** (see C13
below — the rule this app follows, and the simpler one), the seek bar looked draggable on tracks that
cannot seek, and **a SID played for ever** because nothing acted on the length HVSC had been
supplying since round 4 (`docs/ARCHITECTURE.md` §14). All three merged after a device confirmed them.

**Confirmed on a device:**

- modules play (2026-08-31), SAP plays (2026-09-01)
- round 3's five features — Random rolling on, history, "more from this author", both shares
  (2026-09-02)
- **2026-09-03, round 5**: **SNDH plays** — which also means [docs/review.md](https://github.com/przunk/protracktor/blob/2c35319cfa8b665398a36141248b45544bd68ebe/docs/review.md) R1 is genuinely fixed,
  not merely fixed as far as a probe can tell; the **local index survives a restart** and is read
  instead of rescanned; **Browse comes back to where you were**; **C6** looks right; **C7**
  confirmed — adding from a row's menu no longer closes Browse.
- **2026-09-03**: **ASMA plays** (SAP files from the archive) and a **local SID plays**, so
  libsidplayfp is confirmed on a device. Reading a local library is confirmed correct.
- **2026-09-03, late**: **subsongs work on a device.** Tactic (8 tunes) played on a device and
  confirmed the strip scrolls with many more. **C11** — auto-advance racing through every tune in
  silence — is confirmed fixed.
- **2026-09-03, evening**: the **stale-index notice** appears on Modland; the **action row** in the
  Now Playing, the **Browse** button, the **playlist counts** in both the switcher and the
  add-to dialogue, the **current playlist first**, and **history showing when** — all confirmed.
  The single **"Add to playlist…"** replacing the two add actions is confirmed too, which matters
  because it changed a route already signed off as **C7**.
- **2026-09-04, late**: **Settings works on a device** — the gear and Browse placement look right,
  System/Polish/English all switch the whole interface and survive a restart, playback survives the
  activity recreation, and deleting stored catalogue data from its new home works.

**Still not verified by anyone on a device:**

- **The two corrections from the Settings device pass:** Save and Discard now have visible labels,
  and a downloaded catalogue without an index reinforces its already explicit warning with the
  error colour. Confirmed on a device: the labelled right-side actions fit on a phone; their
  spacing was then increased by 6 dp and that final spacing has not yet been seen on a device. The
  missing-index colour also remains unverified on a phone.
- **The console formats** (NSF, GBS, SPC, VGM, HES, AY, KSS) — built and measured on the host,
  never played on the device. Modland has 5,015 NSF and 918 GBS; real headers read on 2026-09-03
  give `lil' monster.gbs` **68** tunes, `mario golf.gbs` 42 and `shinsenden.nsf` 39, which is also
  the first real test of the subsong strip scrolling. They will only appear after a Modland
  re-index — that index predates game-music-emu as well as libsidplayfp.
- **HVSC song lengths** — downloaded and stored, but no SID has been seen showing a duration.
- **C64 from a catalogue.** A local SID plays; Modland's SIDs are still invisible because the index
  predates libsidplayfp (**A22**).
- **What a scan costs on a large library.** It works and reads correctly on a phone
  (confirmed 2026-09-03). How long it takes on a library of thousands is still unmeasured.
- **The 512 MB cache ceiling**, which nobody has yet had enough cached music to reach.

## Finished

- **2026-09-08** — **Hold next to leave the file.** A press moves by tune, a hold moves by file.
  `aleste 2.kss` holds 256 of them, so leaving it with the ordinary next was 256 presses. Not a
  fourth transport button: that would be on screen always for something wanted rarely, in the row
  read without looking. Deliberately not on the notification or a headset button either —
  neither has a long press, and inventing a double-tap for them would be a second vocabulary for one
  idea.

  It uncovered an older defect next door. **`canGoNext` counted only files**, so on the last track
  of a playlist the button was disabled while `next()` would happily have stepped to subsong two —
  a file with 256 tunes could only be walked from the Now Playing strip. The notification reads the
  same value, so it was wrong there too. It now asks whether *anything* would happen; the long press
  has its own file-level test.

- **2026-09-05** — **AHX and HVL** (`docs/PLAN_FORMATS.md` §6). The two names this app claimed on an
  assumption, removed on 2026-09-04 when the assumption was measured, and back a day later with
  something behind them: HivelyTracker's standalone replayer, BSD-3-Clause, three source files —
  the smallest vendored decoder here. **80 of 80 sampled Modland files loaded from a buffer, were
  audible and reached a song end**, the first backend to come back clean on both halves, across
  1,433 files. It is also the first to arrive with a **duration and a working seek bar**: running
  the sequencer without the mixer costs about a millisecond, so the length is measured at load
  rather than looked up. The probe is built twice, with 32- and 64-bit typedefs, because
  armeabi-v7a is one of our three ABIs and this is Amiga code that predates the question.
- **2026-09-03** — **A playlist is a file, both ways** (**A25**). Export writes M3U — readable by
  other players, with our own identifiers in comments they ignore — and import reads it back,
  matching each line by its recorded id first and by filename and size second, into a new playlist
  of its own.
- **2026-09-03** — **Subsongs** (**A2**). A file holding fifteen tunes played one of them; now the
  Now Playing lists them and a mode decides whether they play through. All five backends could
  already select — ASAP was calling `ASAP_PlaySong` with a fixed index — so the work was mostly
  saying so. sc68 and libsidplayfp count their tunes from one and the rest from zero; that is
  converted at the backend boundary rather than leaked to every caller.
- **2026-09-03** — **A scrollbar you can grab**, at the edge of the playlist and of Browse's track
  lists (**A3**), and **bulk operations on the playlist** (**A4**): long press to select, then add
  the selection to a playlist — existing or new — or remove it. Undo brings a whole bulk removal
  back, which is the part that could have lost somebody's playlist and so is a tested object of its
  own.
- **2026-09-03** — **One shape for an action, and one way to add.** Icons with their names
  underneath, in a row, wherever a track offers actions and wherever Browse is entered or left
  (**A17**, **A23**). The two add actions became one *"Add to playlist…"* opening the picker, with
  the current playlist first and **each playlist's size beside its name** (**A21**). History says
  **when** each track was played (**B15**), which is what the list is for rather than a
  list that reorders itself.
- **2026-09-03** — **A catalogue index says when it is out of date.** Every index records which
  decoders built it, and one built by a different set — or by an unrecorded set, which is every
  index that exists today — says so and points at the re-index button. This is what hid 60,572 C64
  tunes: a Modland index that predated libsidplayfp, and an index filtered at index
  time to what a backend can play looks *empty* rather than stale. Closes **A22** and the asymmetry
  `docs/BACKLOG.md` A7 had been carrying as a note asking a human to remember.
- **2026-09-03** — **C6 and C7.** Entering a Browse domain now forgets where you had got to, so
  choosing Online catalogues shows catalogues rather than an emptied folder; and adding one track
  from a row's menu stays in Browse and says what it did. The two rules about position are
  deliberate opposites: descend and return keeps your place, leave and re-enter does not.
- **2026-09-03** — **Browse remembers where you were.** Each level keeps its own scroll position for
  the life of a Browse session, and coming back puts the row you descended by on screen rather than
  an offset that may no longer mean anything. Closes **A20**.
- **2026-09-03** — **The fetched-file cache has a ceiling**: 512 MB, least recently used first,
  enforced after every write and at start-up (`docs/ARCHITECTURE.md` §19). Answers
  `docs/OPEN_QUESTIONS.md` Q5, which had left the cache growing without limit. Unfinished downloads
  and files in use are exempt from eviction; permanent downloads are in other directories and are
  not counted at all. The online screen now says what is held.
- **2026-09-03** — **The local library is scanned by opening files, not by reading their names**
  (`docs/ARCHITECTURE.md` §18). A scan hands every file to the same decoder path playback uses and
  stores the result, so re-entering a folder reads an index instead of walking the tree again. Rows
  record which decoder set produced them and a folder scanned by a different one is reported stale —
  which mattered the same day, since sc68 3.0.0b made a great many previous verdicts wrong. Closes
  **C4** and **A6**.
- **2026-09-03** — **sc68 3.0.0b**, replacing the 2003 release. Measured on one corpus, both
  libraries, same bytes: **`.sndh` 14/30 → 30/30**, `.sc68` 10/10 → 10/10, and SNDH files now carry
  durations at all because 3.x ships a database of known tunes. Closes **C1** and confirms **C2**.
  It was a rewrite rather than a version bump — `api68_*` no longer exists — and the licence moved
  from GPL-2-or-later to GPL-3-or-later.
- **2026-09-02** — **How a list behaves** (`docs/ARCHITECTURE.md` §17). Tapping a track plays it;
  a long press starts selecting; the always-visible checkbox and play button are gone; every row
  has a three-dot menu with add, information, both shares and "more from this author". Back leaves
  a selection before it leaves a level. The dock's **+** now appears for anything playing outside
  the playlist, not only for Random.
- **2026-09-02** — Read-ahead fetches its tracks **in parallel**; it was written serially and the
  owner heard the difference on a device. The Random icon is a die rather than a solid square, and
  the two share actions no longer share an icon.
- **2026-09-02** — **Sharing**: the file, from anywhere, through a `FileProvider` copy; and a link,
  for catalogue tracks, which is the file's own URL where the catalogue publishes one and the
  collection plus the path inside it where it does not.
- **2026-09-02** — **"More from this author"**, from Now Playing and from a track's menu:
  opens Browse at the author's folder in the catalogue the tune came from. **Catalogue tracks only** —
  a local file's neighbours would be its directory, and the local browser lists a whole granted tree
  flat rather than directory by directory, so there is no folder to open.
- **2026-09-02** — **History.** A fifth domain in Browse listing what has been played, most recent
  first, playable and addable exactly like any other list. One row per track rather than one per
  play, 500 of them, forgetting the oldest.
- **2026-09-02** — **Random plays on, and reads ahead.** A tune ending goes to the next random pick
  instead of stopping, and Random now decides two or three picks in advance so the read-ahead has
  something to fetch — it never could before, because Random picked at the moment you pressed it.
  Repeat-one still means what it says. The dice and next now differ: next walks forward into the
  queue, the dice re-rolls.
- **2026-09-02** — **SID durations**, from HVSC's song length database: 61,157 tunes, downloaded on
  request from the online screen and looked up by the MD5 of the file. SID position now counts the
  frames actually played, because libsidplayfp has no notion of a position to ask for and a
  duration with nothing counting towards it would have been half a feature. Seeking is still not
  possible in a SID — the only way to a position is to run the machine there.
- **2026-09-02** — **ASMA**, the Atari SAP Music Archive: 6,335 files, downloaded once as a 20 MB
  archive and then browsable and playable with no network at all. A second kind of catalogue
  (`docs/ARCHITECTURE.md` §13).

- **2026-09-02** — **Commodore 64.** libsidplayfp 3.1.1 with the SIDLite emulation, building for
  every ABI. Thirty random Modland SIDs played on the host with **no Commodore ROMs supplied at
  all**, none of them BASIC-compatible — which shrinks the ROM question rather than answering it.
  SID files have no intrinsic length, so durations came from HVSC the same day (below).

- **2026-09-02** — The follow-track button is a small icon rather than a labelled one, and adding
  tracks scrolls to them.

- **2026-09-02** — Haptics on the three gestures the screen does not already answer: a row picked up,
  put down, and each position it crosses; plus the snackbar's dismiss threshold.

- **2026-09-02** — Finding the playing track: a "Show in playlist" action in Now Playing, and
  a follow toggle over the list that is off by default, hides itself while following, and switches
  off the moment the user scrolls by hand.

- **2026-09-02** — A track can be played from Browse and from search results without adding it: the
  results become the queue while you are looking at them, and the playlist is untouched. Sources are
  named as paths everywhere, so a result says where it came from and Information shows
  `Modland/Format/Author`.

- **2026-09-01** — Adding tracks no longer raises a snackbar over the rows it is reporting, and
  titles and authors are filled in by a background pass instead of waiting for each track to be
  played.

- **2026-09-01** — The playlist row rebuilt to the agreed shape: ordinal or a play triangle, title
  with author and format, an overflow menu holding information and delete, and a drag handle that
  reorders. Reordering is an edit, so it waits for Save like the others.

- **2026-09-01** — **The consoles.** game-music-emu 0.6.5: NES (NSF/NSFE), SNES (SPC), Game Boy
  (GBS), Sega (VGM/VGZ/GYM), PC Engine (HES), ZX Spectrum (AY), MSX (KSS). Verified on the host
  before integration — NSF 4/4, SPC 4/4, GBS 3/3, VGM 3/3.

- **2026-09-01** — **Atari 8-bit.** ASAP 8.0.0 vendored and building for every ABI: SAP plus Chaos
  Music Composer, Raster Music Tracker, Theta Music Composer, Delta Music Composer, Music ProTracker
  and their double-play variants. Twelve of twelve random Modland SAP files played on the host
  probe, and **confirmed on a device on 2026-09-01**.
  It seeks, which the Atari ST backend cannot.

- **2026-09-01** — Search scope is explicit and two-level (library / online / which catalogues);
  local results show where the file lives. Tracks take their real name from the tune's metadata
  once played, keeping the filename. Random is a mode: the playlist goes behind glass, next and
  previous walk the random history, and keeping a track no longer leaves it.

- **2026-09-01** — Ducking: a notification lowers the music instead of stopping it, a call stops it
  and does not bring it back. Duplicate tracks are refused by file identity rather than by URI.
  Random plays without adding, with a control to keep what you hear.

- **2026-09-01** — Media session: lock-screen transport, Bluetooth and headphone buttons, with the
  notification bound to it through `Notification.MediaStyle`. Seeking is advertised only for
  backends that can honour it.

- **2026-09-01** — Read-ahead: the next track's bytes are loaded while the current one plays, and
  the dock says "Loading…" when a read is actually happening. R9's wait is a read, not a decode.

- **2026-09-01** — Browse is a full screen with four domains: local filesystem, online catalogues,
  random, and search across both. Modland is indexed into the database (schema version 2) and
  browsable offline by format and author; remote tracks stream and are cached. **None of it has run
  on a device.**

- **2026-09-01** — **R1: Atari ST.** sc68 2.2.1 vendored and building for every ABI, behind a backend
  interface alongside libopenmpt. SNDH files are 68000 machine code, so this is a whole emulated
  Atari. **Not confirmed playing** — there is no SNDH file on this machine and no emulator; the
  owner has to try one.

- **2026-09-01** — Audio focus and becoming-noisy: another app or a phone call takes the speaker and
  we pause; pulling headphones out pauses instead of playing to the room.

- **2026-09-01** — Removing a track offers undo instead of asking first; deleting a playlist asks.
  Snackbars can be pushed off the screen sideways.

- **2026-09-01** — **R6 met.** Several named playlists, created, renamed, deleted and switched from
  the top bar. Browse remembers granted folders and adds a *selection* rather than a whole folder.

- **2026-09-01** — Playback survives the app leaving the screen: `PlaybackController` is now a
  process-wide singleton and `PlaybackService` keeps it in the foreground with a notification
  carrying previous, play/pause, next and stop.

- **2026-09-01** — **R2 met.** Playlist, shuffle, repeat and the track last played survive a restart,
  in a hand-written SQLite database (`docs/ARCHITECTURE.md` §9 explains why not Room). Granted SAF
  folders are remembered too. Six tests run the schema against a real SQLite engine on the JVM.

- **2026-08-31** — The Compose UI: dock, playlist, Now Playing, Browse, theme with dynamic colour,
  Polish and English strings. `PlayerViewModel` ties `PlayQueue` to the native engine. The engine
  gained a finished flag and a restart, polled rather than pushed — signalling from the audio
  callback would mean attaching a JNI environment on the thread that must never be late.

- **2026-08-31** — `PlayQueue`: play order, history and repeat/shuffle semantics as pure Kotlin with
  no Android in sight. 16 unit tests, all four deliberate mutations of the logic killed by them.
  `scripts/test-protracktor.sh` runs them quietly.

- **2026-08-31** — Repository created. GPL-3.0-or-later, remote `https://github.com/przunk/protracktor`
  (private for now).
- **2026-08-31** — Build environment scripts: `use-tooling.sh`, `check-tooling.sh`,
  `build-debug.sh`. NDK 29.0.14206865 and CMake 3.31.6 added to the shared
  `/mnt/workspace/.tooling/android-sdk` with approval; nothing already there was
  modified.
- **2026-08-31** — `push-protracktor.sh` and `internal/git-askpass.sh`, adapted from the Kratkoza
  equivalents, with permission to read them.
- **2026-08-31** — Gradle project: `app` module, Compose, Material 3, minSdk 29 / targetSdk 36,
  Gradle wrapper 9.6.0, build directory off the 9p mount. Debug APK builds, was inspected, and runs
  on a device.
- **2026-08-31** — Decisions taken: formats to do first (Q2), how files reach the library (Q3),
  duration policy (Q4), and the two-kinds-of-source model with remote catalogues
  (`docs/ARCHITECTURE.md` §8). Modland's index and file endpoints were measured, not assumed.
- **2026-08-31** — `AGENTS.md` in the project root records the English-documentation override as
  applying to Protracktor only; the workspace-wide rule is untouched.
- **2026-08-31** — Build scripts rewritten to keep Gradle's output in a log file and print four
  lines: the token cost of an agent reading task names was the whole reason. Artifact naming now
  matches the workshop's other projects (`versionName-versionCode-timestamp`), release signing uses
  the shared `PRZUNK_UPLOAD_*` properties, and the failure path was tested by breaking a Kotlin
  source deliberately.
- **2026-08-31** — Native layer: libopenmpt 0.8.9 vendored by `scripts/fetch-native-deps.sh`
  (pinned, SHA-256 verified), built by a CMakeLists that reads its source list from upstream's own
  `Android.mk`. JNI engine with Oboe output in `native/engine/`. Kotlin facade in
  `engine/NativeEngine.kt`, with the R8 keep rule that stops the release build renaming the native
  method names.

## Surprises worth remembering

- **AGP 9 embeds Kotlin.** Applying `org.jetbrains.kotlin.android` alongside it is a hard error,
  not a warning. But the Compose compiler plugin (`org.jetbrains.kotlin.plugin.compose`) is *still*
  separate and *still* required whenever `buildFeatures.compose` is on. The two facts look
  contradictory and cost two failed configurations to establish.
- **`-keepclasseswithmembernames` preserves names but still allows shrinking.** The 0.1.0 release
  APK contains five of `NativeEngine`'s seven native declarations: `nativePositionSeconds` and
  `nativeDurationSeconds` were removed because no Kotlin code calls them yet. That is correct — JNI
  resolves lazily and per call — but it looks exactly like a broken keep rule at a glance, so check
  whether the missing method is simply unused before changing anything.
- **A test can pass for the wrong reason and mutation testing is how you find out.** The test for
  "forward after backward replays the same track" was built with plain `next()` calls, which makes
  the history identical to the play order — so deleting the redo branch entirely still produced the
  right answer by accident. It only became a real test once the history was made to *diverge* from
  the order with `startAt`. Two further rounds were needed: the mutation was also shadowed by
  `appended()` failing to truncate the forward branch, a latent inconsistency that is unreachable
  today only because another branch runs first.
- **`set -e` with `set -o pipefail` kills a script on a grep that matches nothing.** The test
  script's failure report died halfway through, so the run that most needed explaining printed
  least. Every grep that may legitimately find nothing now ends in `|| true`.
- **Filtering test-result XML by modification time reports zero tests on every second run**, because
  Gradle skips the task when nothing changed. The zero-tests guard has to distinguish "skipped as
  up to date" from "genuinely ran nothing", and now says which.
- **CMake's regex engine has no `\t` escape.** An anchor written `"^[ \t]*..."` matches nothing at
  all on a tab-indented file, silently. Strip the line with `string(STRIP)` and anchor on `^`
  instead.
- **`file(STRINGS)` treats a trailing backslash as a list escape.** Reading a makefile whose source
  list is continued with `\` yields the entire block as *one* list element beginning
  `LOCAL_SRC_FILES += \`, so per-line matching finds nothing. Read the file whole and use
  `string(REGEX MATCHALL)`.
  Both of the above produced zero parsed sources and both were caught only because the CMake asserts
  the count is over 100. Without that assert, the build would have produced a correctly linking
  libopenmpt with no decoders in it.
- **Oboe's prefab package requires `c++_shared`.** CMake's NDK default is `c++_static`, so the first
  native build fails with `[CXX1212] User is using a static STL but library requires a shared STL`.
  Fixed with `-DANDROID_STL=c++_shared`.
- **`unzip` is not installed in this environment.** Irrelevant to the app (archive handling will be
  native), but it will bite any script that reaches for it. Use Python's `zipfile` instead.
- **`sndh.net` does not resolve from this machine.** Whether the domain is gone or the network here
  blocks it is unknown. Recheck when `sc68` is integrated.
- **The workspace mount vanished mid-session** while WSL was being adjusted, taking the whole
  project directory with it until the mount came back. Nothing was lost, but the repository exists
  in exactly one place until it is pushed.

## Next

Nothing here is chosen; this is what the current state points at.

1. **A device pass.** The list of things nobody has confirmed on a phone is now longer than the list
   of things anybody has, and five formats and two archives went in without one.
2. **Subsongs** (`docs/BACKLOG.md` A2) — agreed in shape and unstarted. Console
   files and SIDs hold hundreds of tunes each, and today every one of them plays only the first.
   The song lengths already store every subsong's duration, so half the data is waiting.
3. **The persistent index and library scan**, which is what R9 (instant start) actually depends on.
4. **A3 fast scrolling** — explicitly deferred  for a conversation, not for want of a
   plan.

Blocked on a decision: `docs/OPEN_QUESTIONS.md` Q1 (navigation model), the Commodore ROM question,
and whether `develop` should be merged to `master`.

## C — known defects

Numbered to match the A (open work) and B (wishlist) lists. A defect is something that does not do
what it was meant to; work that was never started is in `docs/BACKLOG.md`.

Entries describe the defect, reproduction, evidence and resolution in our own words, without
personal names or conversation quotations. **From 2026-10-06, at the owner's request, also record
the report date and source category** (user/tester, owner testing or code review), so real user
feedback remains traceable through a fix and retest. A user report is not an independently
reproduced defect or evidence of a fix. The feedback index is in [Testing](TESTING.md#feedback-record).

### C103. ~~Playlist writes can overwrite another playlist or hide newer edits~~ — FIXED and merged with owner approval, 2026-10-13

**Source:** code review and controlled-store tests during the architecture extraction, 2026-10-04;
owner selected the safety fix on 2026-10-13. This is not an independent app-user report.
No frequency measurement or reproduction on Android SQLite/phone is claimed.

**Mechanism:** activation changes the persistence ID before destination tracks load. A debounced
write that reads ID and queue later can replace the destination with the source's tracks.
Save also used to clear the current dirty flag unconditionally after its suspended write, even
if a newer edit or another playlist had appeared. Capturing only the ID is insufficient: contents
must come from the same published state, and completion must still refer to that state.

**Controlled reproduction:** suspend the destination track read, let the source's pending metadata
write fire, then inspect the replacement target and rows. Separately suspend Save, edit again,
finish the write and inspect dirty state versus persisted contents. The safety assertions failed
against unchanged production in regression commit `016321a`.

**Implementation:** immutable request snapshots, shared session-write mutex, guarded Save/Revert
completion and per-playlist invalidation on Revert/Delete. Deleted targets reject new requests
before destination publication; last-playlist reset retires requests captured during its rename/read.
Explicit Save and automatic additions supersede older pending metadata for that same playlist.
This does not claim global serialization of every `LibraryStore` caller.

**State:** merged at `f4c9bf1` following owner approval of the combined C103/C102 work. Focused host
checks and the passed full gate are recorded above. Individual playlist device scenarios remain
**not checked**; the supplied phone results concern Bluetooth. The combined candidate is tracked
in C102. [Handoff](https://github.com/przunk/protracktor/blob/2c35319cfa8b665398a36141248b45544bd68ebe/docs/PR_C103_PLAYLIST_WRITE_SAFETY.md).

### C102. ~~Bluetooth connection interrupts sound and stalls the in-app progress~~ — FIXED, owner-accepted and merged, 2026-10-13

**Report recorded:** 2026-10-13. **Source:** owner observation; the owner is unsure whether
the problem still occurs. The occurrence date, installed build, phone/Android version,
Bluetooth receiver and route-selection details were not supplied.

**Reported sequence:** music is playing on the phone; a Bluetooth audio device is connected;
sound does not reach that device until playback is stopped and started again.
**Expected:** when Android selects that device as the media output, the ongoing playback should
continue there without a manual Stop/Play cycle. **Observed:** restarting playback restores sound,
according to the report. Playback/UI state during the interruption and whether the phone itself
continues producing sound are unknown.

**Status: not checked on a current build.** Confirm the sequence with a current release and
record the missing device/build details, selected output, playback state and precise restart
action. Inspect route-change/audio-stream handling only after establishing that it still occurs;
no stream, focus or decoder cause is diagnosed by this report. No fix, reproduction or device
acceptance claimed. Bluetooth transport-button verification is separately tracked as B47.

**Owner retest, 2026-10-13:** reproduced while connecting headphones during playback. Sound stops;
the notification's position keeps advancing while the in-app playback bar is stuck. Moving the
seek bar does not restore sound. Pause then Play restores normal operation. This is a current
device observation, superseding the uncertainty about occurrence above; the installed build,
phone/Android version and headphone model are still unspecified, and no cause is established.
The owner also recalls possibly needing to select another track in the car, but that variant
is not confirmed by the headphone test. No agent device reproduction or fix has been performed.

**Implementation follow-up, 2026-10-13:** owner approved a separate fix and a combined release
APK with C103. Source inspection confirms the Player registered only a data callback, retained a
stream pointer after disconnection and treated that pointer as a healthy output. Oboe 1.10.0's
default AAudio error callback stops/closes the underlying stream, but does not reopen it or clear
the Player's pointer. Decoder position is published by data callbacks; app state stays Playing,
and MediaSession continues advertising speed 1. Seeking does not recreate output; Pause/Play does.
This explains the reported symptom sequence but is **not** a confirmed device error trace.

`fix/bluetooth-output-recovery` adds per-stream error state, control-thread output-only recovery,
and a typed JNI result. It preserves the decoder and does not restart finished or paused tracks.
Unrecoverable errors pause and show `notice_audio_device_failed`. Host evidence and remaining
checks are recorded at the top and in the [C102 handoff](https://github.com/przunk/protracktor/blob/2c35319cfa8b665398a36141248b45544bd68ebe/docs/PR_C102_BLUETOOTH_OUTPUT_RECOVERY.md).
**Owner acceptance, 2026-10-13:** Bluetooth connection and disconnection while paused both leave
playback silent. Connecting during playback transfers sound to headphones; disconnecting pauses.
The owner authorized merging the combined work; C103 merged at `f4c9bf1`, then C102 at `69c42a2`.
Installed build/device details and other checklist results were not separately supplied.
B47 receiver-button verification remains a separate task.

### C97. ~~Random history and selection become inconsistent after changing scope from an earlier track~~ — FIXED, owner-accepted and merged 2026-10-06

**Source:** an app user, relayed by the owner. This is user-reported feedback, not a code-review
finding. Installed version/build, device, Android version and installation channel were not
provided. No screenshot, recording or log was supplied.

**Reported reproduction:**

1. Start Random with Everything or another scope.
2. Play several tracks, for example ten, so the Random list has history.
3. Select a track other than the last, for example track five, and let it play.
4. Change the Random scope, for example to Atari 8-bit.
5. Press Next, inspect the list and playing/selected row, then press Next again.

**Observed by the reporter:** the list around the current selection is reset; existing entries
disappear and three new-scope random tracks appear in their place. Further Next presses append
tracks at the end, while the highlighted position and playback progression appear inconsistent.
In the simplified example, row three is an Amiga track when the scope changes. The first Next
plays an Atari 8-bit track at row four, with two additional Atari entries already at rows five and
six. Another Next from row four appends row seven, which is marked as selected in the report.
The exact audible/highlighted pair on that second Next and which old rows disappear still need
confirmation; the description is retained as a report, not a measured queue trace.

**Expected:** changing scope should not unexpectedly erase already-played history. Each Next
should advance playback coherently, and the highlighted row should identify the track actually
playing. Prefetched entries must not cause a selection jump or apparent skip. During diagnosis,
distinguish already-played history from future queued picks before deciding what should be retained.

**Diagnosis (2026-10-06):** scope changes truncated the backing list after the selected cursor,
including already-heard entries, but kept the old played boundary. New speculative entries then
fell below that boundary and became visible as though heard. This truncation predates the refactor
(`1918d93` introduced it). A regression test reproduced the lost history in the shared production
state owner before the correction; the exact audible/highlight mismatch remains a user report,
not independently reproduced audio behaviour. Suspended fallback draws also lacked a final
generation check, allowing a stale-scope result to append after the scope changed.

**Owner decision:** Next traverses all remaining heard history before consuming a new-scope pick.
The filter change immediately prepares replacement speculation in the background, without
changing the current tune or showing future entries. An explicit dice re-roll still requests a
fresh tune at the end. A new tune replenishes the background queue.

**Implemented:** `RandomHistory` shares one boundary/cursor rule with the controller, discards only
unheard entries, serializes foreground/background fills and rejects obsolete work after both
database reads. Next from known history does not await another query. Publication and playback
handoff of a selected pick have no intervening suspension. Fifteen targeted tests passed; removing
the fallback guard made its regression test fail, then the guard was restored. Full gate/release
handoff is recorded in the PR document. The owner subsequently accepted the combined candidate
and authorised merge `43963a0`. No device model, per-scenario trace or measured timing was supplied;
the original reporter's device/build remain unknown.

### C98. ~~Browse remains usable behind the Random return-choice overlay~~ — FIXED, owner-accepted and merged 2026-10-06

**Source:** a different app user, relayed by the owner. Installed version/build, device, Android
version and installation channel were not provided.

**Reported reproduction:** start Random playback, then press Back. The playlist is dimmed and
offers Back to Random and Back to Playlist; those two actions behave as expected. Press Browse
instead: the normal Browse choices appear, including entry into Random again.

**Expected behaviour proposed by the owner:** while that return-choice overlay covers the
playlist, Browse and Web should be visibly unavailable; Settings may remain usable. The report
demonstrates Browse remaining active. Web is part of the requested consistency check, not a
separately confirmed observation. Both return actions must remain usable.

**Diagnosis and fix (2026-10-06):** `PlaylistScreen` covered its own body for `awayFromPlaylist`,
but the independently composed `PlaylistTopBar` kept Browse and Web enabled. The bar now uses
the same derived condition, including search/external covers, without adding another navigation
state. `LabelledAction.enabled` disables both tap and long press and exposes disabled semantics.
Settings and the two return actions are unchanged.

**Checked on host:** five Compose/Robolectric interaction tests exercise the production top bar
and covered playlist. Before the guard, three failed on enabled Browse/Web; after it all five
passed, including both Web pairing states, touch/hold suppression, Settings/return callbacks and
re-enabling after the cover disappears. This confirms component behaviour on the host, not a
phone navigation journey. Full gate/APK evidence is in `RANDOM_FEEDBACK_PRS.md`. The owner then
accepted the combined candidate and authorised merge `ed2b4aa`; individual gesture results and
an independent retest by the original reporter were not supplied.

### C99. ~~Random filters open with a perceptible delay and briefly appear unavailable~~ — FIXED, owner-accepted and merged 2026-10-06

**Source:** the owner's hands-on testing, distinct from the two external-user reports above.
Reported in follow-up to the offered release test APK 1.0.0 (1084), source `0ab8c7d`; the installed
build was not explicitly confirmed. Device and Android version were not supplied.

**Observed:** opening Random's filters has a slight lag. On another occasion, under uncertain
conditions, the chips initially appeared unavailable and became active after roughly one second.
The owner believes the interaction stayed within Random but is not certain. Availability
eventually recovered; this report does not establish that filters remained permanently disabled.

**Expected:** the filter sheet should feel responsive and communicate any availability check
clearly, rather than briefly looking as if known music has become unavailable. A transition from
loading to available should not be mistaken for missing indexes or require a re-download.

**Diagnosis (2026-10-06):** the 1084 candidate put availability in a sheet-local `remember` and
reset it to null before every refresh. Reopening discarded a successful read; downloading or
recounting temporarily disabled previously usable chips. The platform query runs on IO, not the
main dispatcher. Two new Compose checks reproduced these resets before the correction, but the
owner's uncertain sequence and perceived timing have not been independently measured on a phone.

**Implemented on `fix/random-filter-responsiveness`:** reuse the earlier availability correction
as an ancestor of this branch, preserving its history; merged into develop as `98e605f` after
owner acceptance. Availability
now lives beside the Random screen, preloads on entry, and survives opening/closing the sheet.
Unchanged sheet reopenings and percentage progress do not recount. Valid known chips stay usable
during refresh; database preparation/deletion invalidate them, inactive data changes clear the
snapshot, and cancellation guards reject late results. Cold/error states still show checking or
Retry, rather than interpreting an unknown count as missing music. No database/schema changes,
new dependencies, arbitrary delays or speculative enablement were added.

**Checked on host:** 16 Compose panel tests passed, including preloading/query counts, cold/warm
opens, downloads, deletion, inactive invalidation, retry and late responses. Removing the final
cancellation guard failed the obsolete-result test; the guard was restored. Full gate/APK evidence
is recorded in `RANDOM_FEEDBACK_PRS.md`. The owner subsequently reported successful operation
and authorised the merge. This is acceptance of the correction, not a quantitative device timing
measurement or a detailed trace of the original uncertain sequence. The cold/warm checklist
remains available in TESTING for future regression checks.

### C89. Search filtered to Atari 8-bit never looks in ASMA — **reported 2026-09-24; FIXED, merged and confirmed on the phone the same day**

**Fixed, the owner's choice (c), 2026-09-24:** each catalogue row stores its platform. Schema **20**
adds `catalogue_tracks.platform`; `Platforms.forCatalogueRow` is the one rule -- the archive when it is
one machine's (ASMA: Atari 8-bit; UnExoticA: Amiga), then Modland's directory, then the file's name
-- and `Platforms.sqlCase` is the same rule as the `UPDATE` that fills the column for rows already
stored, run by the re-decision that `Platforms.fingerprint`, now part of the index stamp, triggers at
the next start. Search, its count, the dice's platform scope and the chips' counts all read the
column: **the dice had the same defect**, drawing no ASMA tune under Atari 8-bit. A test holds the
`UPDATE` to the function for every name and directory in the table; another finds an ASMA row under
Atari 8-bit; a migration test keeps a version-19 row through 20. Rows in a Modland directory the table
does not map now get the platform of their name, where before they had none.

The report as noted:
### C90. "More from this author" from search results left the search behind — **FIXED, merged and confirmed on the phone 2026-09-24**

The owner: from Random the jump shows the digression -- *Browsing author* over the folder, a way
back -- and from Search it did not; "it has to work identically". **Mechanism:** the heading was
drawn only while the dice waited (`randomMode || diceWaiting`), and Back after a jump
(`arrivedByJump`) left Browse outright, so the results, their words and scope were gone. **Fixed:**
a search is a place to come back to, like the dice. The jump keeps it (`BrowseNavigation.searchToReturnTo`,
`searchWaiting`), the heading shows, next and previous walk the author when a result was what played
-- and not when the playlist was playing, since a look must not move the music -- and Back returns
to the results as they were (`resumeSearch`). A tune chosen in the folder meanwhile stops and the
results' tune waits paused, as the dice's does; otherwise the music plays on.

### C91. `Bonio.sap` plays to the ~3:00 fallback though its author knows its length — **(b) BUILT, merged and confirmed on the phone 2026-09-27 ("it works")**

The owner: `asma/Composers/przunk/Bonio.sap` (his own) is cut at ~3:00. **Mechanism, read from
the file:** its header has no `TIME` line -- `AUTHOR`, `NAME`, `DATE`, `STEREO`, `TYPE`, `INIT`,
`PLAYER` and nothing else -- and ASAP's length for a SAP is `TIME` alone, so the app knows none and
the fallback ends it, marked `~`. ASMA does not state it elsewhere either: its only other record of
the tune is a Demozoo entry with no length. B36's inventory put about 180 of Modland's SAPs in the
same place.

**Options:** (a) `TIME "m:ss"` added to the file, per subsong, and the file sent to ASMA -- every
player then knows it; the owner's seven files. (b) Measure a SAP's natural end, as NSF's variant (a)
does -- only for tunes that fall silent. (c) B36's length database. Recommended: (a) for these, (b)
after the launch.

**Found 2026-09-25: ASAP ships the tool that sets these `TIME` lines** -- `asapscan -t`, in
`native/vendor/asap/asapscan.c`, which is how ASAP's and ASMA's maintainers measure them. It runs the
tune without making sound and keeps POKEY's registers for every frame; it detects **silence** (five
seconds of none: the tune ended there) and **a loop** (the last three minutes of registers repeating
something already played: one pass ends where the repetition starts), scanning at most fifteen
minutes. So (b) can do more than NSF's measurement, which finds silence only -- and most Atari music
loops rather than ending.

What (b) costs this way: `asapscan` reads ASAP's internals, which `asap.h` does not expose, so a small
C file compiled in `asap.c`'s place (one translation unit, so the internals are visible and nothing is
defined twice) carries the port -- about 150 lines, the hashing included, since a plain search over
45,000 frames is too slow. Then a background measurement for a SAP with no `TIME`, as `GmeBackend`
does for NSF (`startedPlaying`, `durationArrivesLater`): a second, silent copy, a second or two of one
core on a phone. Tests on two SAPs built in the check -- one falling silent, one looping. **The page
does not get it**, as it has no NSF measurement: its engine has no threads.

**Recommended (2026-09-25), the owner agreeing to write it down:** now, the `TIME` lines for the
owner's seven files, worked out with the same detection, for him to put in the files and send to
ASMA -- every player anywhere then knows them, the page included. After the launch, (b) as above in
the app.

**(b) as built (2026-09-25..27).** `native/backends/asap/asap_measure.c`, compiled in `asap.c`'s place,
ports `asapscan`'s silence and loop detection; `AsapBackend` measures a subsong with no `TIME` on a
thread of its own when it starts playing, cancelled by a close or a switch. Across ASMA's 8,316
subsongs that state a `TIME`: 72% to within 50 ms, 82% to within a second; the rest mostly a silent
tail ASMA counts or a second pass of a loop; 4% nothing found in fifteen minutes, which keeps the
fallback. `asapscan`'s "ultrasound" answer (registers standing still, reported as nothing) is refused.
Bonio.sap measures 3:37.9. **The first phone build still showed `~`**: the decoder said no length was
coming the moment one arrived, and the host, which asks only while one is coming, never took it --
`ProtracktorAsap_LengthStillComing`, checked on the host, fixed that. The page does not measure: its
engine has no threads.


### C92. The web page zoomed and moved in a Safari tab on an iPhone — **FIXED and merged 2026-09-24; not yet seen on an iPhone**

Reported by a friend of the owner, opening the page from a link (a Safari tab, not the installed
app): pinch to zoom worked and the whole page could be dragged. The owner: the page should fit and
lock to the viewport. **Mechanism** (WebKit's documented behaviour; there is no Safari here to watch
it): a tab ignores `user-scalable=no` on purpose, and the earlier fix (2026-09-11) refused only
Safari's gesture events; and the document itself could move -- rubber-banding, a list passing its
drag on at the end, room left by the collapsing address bar. **Fixed:** a two-finger `touchmove` is
refused non-passively, the body is pinned to the viewport and only the lists inside scroll,
`overscroll-behavior: none`. To check: on the iPhone, from a link; and that every view still scrolls
to its end on a computer and on Android.

### C93. The web page on a phone: stretched buttons, Settings that would not scroll, a QR code past its sheet — **FIXED and merged 2026-09-24; first look on the phone "ok", full check pending**

The owner's screenshots (Chrome on Android, Safari on an iPhone): Browse, Pair and Paste were tall
slabs, the playlist's name cut to "Se…", Random's Playlist button stretched across its row; Settings
ran past the bottom with no way to scroll; the pairing code and address stuck out of their sheet.
**Mechanism:** the sizing written for Now Playing's squares was a bare `.action` rule, so since A27 it
reached every labelled button and let each grow to fill its row; a sheet taller than the screen had
nothing to scroll it, which the pinned page (C92) made plain; the code was a table of fixed 5-pixel
cells. **Fixed:** the rule is scoped to Now Playing (a page check fails if the top bar's buttons grow
again), a sheet scrolls inside itself with its heading kept, Settings takes nearly the whole height
on a phone, the code is a scalable picture and the address breaks anywhere.

### C94. Shuffle played the same order from any tune, and stopped early — **FIXED, merged and confirmed on the phone 2026-09-25 ("ok"), APK and page**

The owner, with his 41-tune Favorites, no repeat, shuffle on: from wherever he started, next next next
ended on the same tune (*Studium Fałszu*), with no next; and choosing another tune by hand gave the same
sequence. **Mechanism:** the shuffled order was one permutation dealt when shuffle came on and kept;
a tune chosen by hand stood somewhere in its middle, and next walked only what came after it -- the
same tunes from any start, ending on the permutation's last, and the ones before it never played.
Both sides, each in its own code. **Fixed, as the owner decided:** a tune chosen by hand deals a
fresh order starting at it, as does shuffle turned on (from what plays); next then plays every tune
once before the end, and **the chosen tune is the first**: back stops at it rather than stepping into
what played before the choice (the owner, on the first test build). **Repeat off + shuffle stops after one pass** and **repeat all + shuffle goes on,
dealing again each lap** -- the owner's first idea, shuffle without repeat going on for ever, was
talked through and dropped, since it would make the two the same. The rule is now in
`docs/rules/queue-cases.tsv` (`shuffleFromTap`), run by the phone's `PlayQueue` and by the page
through its own row and next; both failed before the fix.

### C95. Repeat-one did not repeat a tune played from Browse — **FIXED, merged and confirmed on the phone 2026-09-29**

The owner: an MP3 from a local folder, repeat-one lit, went on to the next tune. Not MP3's doing:
**every list played from Browse** -- a folder, results, History -- got a queue of its own built with
repeat off, and the repeat and shuffle buttons changed only the playlist's. The button showed one mode
and the list played another. **Fixed:** such a list is built with the modes the buttons show
(`PlayQueue.results`), and follows them when they change; shuffle deals from the tune tapped, as in
the playlist. The page did this right already.

### C96. The app crashed in UADE while a scan tried a file UADE did not recognise — **OPEN, avoided since 2026-09-29; reported 2026-09-29**

The owner, scanning a folder of 11,582 files (ASMA's SAPs, as many `.mid`, some `.txt`): the app
died at about 6,600, and the scan's notification stayed behind. The native stack: the scan's
one-at-a-time thread, `NativeEngine.open` → `openBackend` → UADE's `uade_cleanup_state` →
`uade_arch_kill_and_wait_uadecore` → `uade_atomic_close`. **Read from that:** past the SAPs only the
names no decoder claims were left, each tried by every decoder in turn; UADE checked one's content,
did not take it, and crashed tearing its core down. Which file, and why the teardown fails, is not
known. **Avoided, not fixed:** a scan now reads only names a decoder claims (A67), so a `.mid` or a
`.txt` never reaches UADE; but a misnamed file played by hand, or a UADE name that fails, would take
the same path. A stale scan notification is cleared at start.
### C88. 3,800 files offered by name that their decoder reads as another format — **found 2026-09-23; FIXED the same day, merged 2026-09-24**

**Checked on the owner's phone, 2026-09-23:** a file each from FamiTracker, Deflemask and Music Editor
did not play; SidMon 1's did (UADE). **Fixed (option (a)):** those three directories -- 3,718 files --
are listed as refused in `web/src/formats.tsv` (`directory` rows) and `SupportedFormats.refusedDirectories`,
held to one list by `SupportedFormatsFileTest`. Their rows stay in the index, unoffered; the list is
part of the index fingerprint, so an index already on a phone or in a browser re-decides itself at
the next start with no download. SidMon 1 is left alone.

**Seen by the owner, 2026-09-24:** on the phone, the three directories gone from an index that was
not downloaded again. **Not checked:** the page, and SidMon 1 still being offered. The recompute's SQL now lives in one
function the test runs too (`playableUpdate`), where the test used to keep a copy.

The finding as noted:

Found by B36's inventory (`feature/b36-length-inventory`, `docs/inventory/LENGTHS.md`): in twelve
Modland directories the page's engine opened **none** of the files sampled. The big four -- Deflemask
(1,807, `.dmf`), FamiTracker (1,779, `.ftm`), Music Editor (132, `.med`), SidMon 1 (61, `.sid`) -- carry
a name that `formats.tsv` gives to a decoder that reads it as a **different** format: X-Tracker's DMF,
Face The Music, OctaMED's MMD, a C64 SID. So they are indexed, offered, and refused; since A51 the
refusal at least names the directory. The `.gtk` case of A51 was four files and earned no machinery;
this is 3,800. **Not checked:** whether the phone, which has UADE as well, plays any of them. To
decide: leave them (A51 explains the refusal), or keep them out of the index by directory.

### C87. Online catalogues jumped while downloading — **FIXED 2026-09-23, merged, confirmed in a browser**

The owner: while indexing, the rows vanished and came back, and the download button and its spinner
moved. Two causes. Every download's start and end redraws the list, and the redraw **cleared it and
the note first**, then read from IndexedDB, then drew -- an empty moment each time, and one row
still waited mid-list for the stale-index check. And the spinner with "indexing…" under it was 84 px
against the button's 44, so the row's text rewrapped. Now the list is built aside and swapped in at
once, the note is rewritten only when it says something new, and the spinner takes the button's
44 × 44 box with its word for screen readers only.

### C86. GitHub Pages stopped deploying pushes to `gh-pages` — **worked around 2026-09-23; cause unknown**

Two pushes to `gh-pages` on 2026-09-23 (`af27036`, `43a61d5`) reached GitHub, and no *pages build and
deployment* run followed: the last was 2026-09-22 20:20 UTC, and the page kept serving that day's
files (`last-modified` of `src/app.js`), without the round's Browse fixes. Settings were unchanged.
**What worked:** *Settings → Pages → Branch: None → Save*, then *gh-pages, / (root) → Save*; a run
started at once (20:13 UTC, success). **How to tell next time:** the repository's *Actions* tab shows
no run after the push, or `curl -sI https://przunk.github.io/Protracktor/src/app.js` shows an old
`last-modified`. Why the pushes did not trigger a build is not known -- it happened the day the
`przunk.github.io` user-site repository was created, which may or may not be related.

### C85. Protracktor was offered as a browser — **FIXED 2026-09-23, merged, confirmed on the phone**

The owner, looking for "Open by default" for A40, found "Browser app" in the app's settings, with
Protracktor as a choice. Mechanism: the filter for module links (B24) named `http` and `https` and
no host, and Android ignores every path pattern in a filter without a host -- so it claimed every
web address, which is what a browser is. In 0.8.0 as published. Now the web half names the
archives' hosts (Modland, ASMA, The Mod Archive, UnExoticA) and the local half keeps `content` and
`file` as before; a test holds every `http(s)` filter to a host. **Not changed, noticed:** the local
half has no host either, so its patterns are ignored too and it matches any `content`/`file` link
that comes without a type -- which the mime-type filter above it is there for anyway.

### C84. Browse's search field stood over every list — **FIXED 2026-09-23, merged, confirmed in a browser**

The owner: the field was visible across Browse, where the phone shows it only in Search. It now
belongs to Search (`fix/web-search-in-its-place`).

### C83. Several catalogue downloads at once wrote over each other in Browse's note — **FIXED 2026-09-23, merged, confirmed in a browser**

The owner: pressing download on each catalogue made the note flicker between them. Progress is now
only the row's spinner; the note says what is held and what failed (`fix/web-download-note`).

### C82. Buttons with a label and no icon, outside dialogs — **found 2026-09-23; FIXED, merged and confirmed on the phone the same day**

**Fixed, with the icons the owner agreed to:** Add a folder -- folder; Add files -- document; Clear
history -- the cross; New playlist -- plus; Import -- download; Back to the playlist -- the playlist.
One `IconLabel` for all six, the pairing screen's shape.

Found while giving the pairing screen's two buttons their icons (`fix/scanner-button-icons`).
**Corrected 2026-09-23:** the first version of this entry also listed the dialogs' *Cancel*, *Save*,
*Delete* and *Close*. Those are not defects: `AGENTS.md` ("An action is an icon with a label")
exempts a dialogue's confirm and cancel as Material's own convention. What is left, text only today:
*Add folder* and *Add files* and *Clear history* in `BrowseScreen`; *New playlist* and *Import* in
`PlaylistSwitcher`; *Back to playlist* in `PlaylistScreen`. Found by a search for buttons whose first
child is a `Text`; a button built another way could be missing. Not fixed: outside the item it was
found in; the owner decides whether it is its own item.

### C81. An NSF refused as "invalid load/init/play address" — **reported 2026-09-22; diagnosed 2026-09-23; option (a) BUILT, merged and confirmed on the phone 2026-09-23; (b) still open**

The owner: `Nintendo Sound Format/Y. Matuo/19 neunzehn.nsf` (Modland, 26,768 bytes) does not play;
the reason shown is game-music-emu's "invalid load/init/play address".

**Mechanism (checked 2026-09-23).** The file is a **Famicom Disk System** rip: header chip flags
`0x04` (FDS), load `$6000`, init `$D000`, play `$D650`, bank bytes `01 01 01 01 01 00 01 01`.
The NSF specification allows exactly this -- on the FDS, `$6000–$DFFF` is RAM, a rip may load below
`$8000`, and two extra bank registers `$5FF6/$5FF7` map `$6000–$7FFF` -- while noting that it "is not
universally supported" ([NESdev wiki, NSF](https://www.nesdev.org/wiki/NSF)). **The file is valid;
game-music-emu 0.6.5 does not implement the FDS memory model.** `Nsf_Emu::load_` refuses any load
address below `rom_begin` (`$8000`) outright and calls it "Corrupt file", and even without that check
it maps `$8000–$DFFF` as read-only ROM and has no `$5FF6/$5FF7`. Reproduced on the host:
`native/probe/gme/build/probe-gme` on the file prints
`VERDICT reject:Corrupt file (invalid load/init/play address)`.

The later, unreleased line of the same library (blargg's "0.6pre", kept in DeaDBeeF's
`plugins/gme/game-music-emu-0.6pre/gme/Nsf_Impl.cpp`, LGPL-2.1 like ours) has it: the load address
is only too low below `$6000` when FDS is on, `$8000–$DFFF` is mapped as FDS RAM, and banks under
`$6000` are mapped for `$5FF6/$5FF7`. Read from its source 2026-09-23; not built here.

**How many files:** not known. The Modland index does not carry NSF headers; counting means
fetching every NSF, which B36's inventory step does anyway.

**Options** -- the owner chose (a), 2026-09-23:

- **(a) Chosen, BUILT: say it truthfully.** `GmeBackend::fdsLoadsLow` refuses the file before
  game-music-emu sees it: *a Famicom Disk System NSF that loads below $8000, which the console
  decoder (game-music-emu) cannot play yet*. Two engine checks: that file refused in those words,
  an FDS NSF at $8000 still opening; the first fails with the check switched off.
  The recommendation as written: The refusal becomes "a Famicom Disk
  System NSF that loads below `$8000` — not supported yet" instead of "Corrupt file". A check of
  the header in `GmeBackend` before gme sees it; a small change, both languages, no risk to what
  plays today.
- **(b) After the launch: teach gme the FDS memory model**, ported from 0.6pre's `Nsf_Impl` as a
  patch in `native/patches/` (as libopenmpt's is): RAM at `$6000–$DFFF` when FDS is flagged, the
  low load address, `$5FF6/$5FF7`. Checked with this file and other FDS rips; the existing NSF
  checks must stay unchanged. Medium; it touches the emulator's memory map.
- (c) Move the whole gme backend to 0.6pre. Would also be the moment to look at C26 (SPC tempo),
  but it swaps the engine under every console format ten days before a launch.

### C80. A SID that needs the C64's BASIC ~~plays silence~~ — **found 2026-09-21; the silence FIXED the same day, merged 2026-09-22, confirmed on the phone; playing them still OPEN**

**The small part is done:** the SID backend reads the header's own compatibility field and refuses a
BASIC tune with *"this tune is a BASIC program and needs the Commodore 64's BASIC ROM, which
Protracktor does not have"*, which the app shows as the reason the file would not open. Checked on
the host: `Prelfugueinfmaj_BASIC.sid` is refused with that sentence, and `scripts/check-engine.mjs`
builds a BASIC RSID and fails if it opens -- it did open, before the change. A folder scan now
records these files as unplayable. **Playing them is still the ROM decision below.**


The owner: `Pace_Wayne/Prelfugueinfmaj_BASIC.sid` "does not play on my phone".

**Why, checked on the host:** it is an **RSID with the BASIC flag set** — the tune is a BASIC
program, and running it needs the C64's BASIC ROM. The app plays SID without ROMs (by decision, when
libsidplayfp was integrated), so the engine opens it without complaint and renders **silence**: a peak
of 0.005 over 50 buffers, where music reaches tenths. **590 of HVSC's 60,572 files in Modland are
`_BASIC`**, and every one of them will do the same.

Two things, and only the second is small:

1. **Playing them** needs the BASIC ROM, which is the ROM question kept out of scope when
   libsidplayfp arrived: ship it, ask the user for it, or live without. The owner's decision.
2. **Silence is a defect meanwhile** (AGENTS.md §7): a tune the engine cannot play must say so, not
   open and play nothing. The flag is in the file's header, so the refusal can be as specific as
   "this tune is a BASIC program and needs the C64's BASIC ROM".
### C79. ~~Accented letters in a title came out as `�`~~ — FIXED 2026-09-21, merged; confirmed on the phone

`Zalza/akes lekhorna.mod` showed **"�kes lekh�rna (za)"**; `docs/BACKLOG.md` A47 noted it.

**The cause written down first was wrong, twice over.** A47 said the title was ISO-8859-1 (`0xC5`,
`0xF6`) spoiled by `NewStringUTF`. Read from the file, the bytes are **`0x86` and `0x94`: CP437**, a
DOS machine's "å" and "ö". And the `�` was not made at the JNI boundary at all: **libopenmpt** made
it, decoding a MOD's text as "Amiga, no C1", which turns every byte from 0x80 to 0x9F into U+FFFD
before the engine sees the string. The engine was handing out valid UTF-8 with the letters already
gone. Built to the first reading, the fix would have left this very file as it was.

**The fix is one rule, in two places** (the owner chose it, 2026-09-21): valid UTF-8 stays; text
holding a byte from 0x80 to 0x9F is CP437; anything else is ISO-8859-1.
- `native/patches/libopenmpt/0001-read-c1-text-as-cp437.patch`, applied by `fetch-native-deps.sh`,
  puts it inside libopenmpt for the three "no C1" charsets it uses.
- `protracktor::fileTextToUtf8` puts it on every line of every other decoder's description, through
  `describeOf`, the one way a description leaves the engine -- a SID's Latin-1 author reached the
  phone as raw bytes before.
- **Migration 18** hands back exactly the rows stored damaged: playlist and history rows get their
  file name as the title and an empty author, which the background pass and the next play look
  for; library rows lose their decoder fingerprint, so their folder offers a rescan.

**Not changed:** names inside LHA archives stay raw, because they are what an UnExoticA tune is
extracted by.

**What it cannot tell apart:** a DOS title with no byte in 0x80–0x9F but with, say, `ñ` or box
drawing reads as ISO-8859-1; a Windows-1252 apostrophe (`0x92`) in a MOD reads as CP437's `Æ`.
Both rare.

**Verified:** the reported file through the rebuilt engine now reads `åkes lekhörna (za)` (bytes
`c3 a5 … c3 b6`). `scripts/check-engine.mjs` builds a MOD and a PSID and checks CP437, ISO-8859-1
and UTF-8 text through both paths -- three of those checks failed before the change. The migration
is tested against real SQLite, and failed before the migration existed. **On the owner's phone,
2026-09-21:** `akes lekhorna.mod` reads `åkes lekhörna`. The migration's handing-back of stored rows
was not looked at separately.

### C78. ~~The privacy policy could not be scrolled to its end~~ — FIXED 2026-09-21, released in 0.7.0

Found the day the in-app policy was added: it stopped at the first line of "Changes", would not
scroll further, and ended against the bottom edge — its last lines lay under the navigation bar.

**The first fix did not fix it.** The page was a full-screen dialog; handing the insets to its
Scaffold (`decorFitsSystemWindows = false`) changed nothing on the phone, which says the dialog
received no system-bar insets at all. So the legal pages are no longer a window of their own: they
show in the place of the Settings list and take Settings' own padding, which the app's Scaffold
already computes to clear the top bar, the dock and the system bars — plus 32 dp after the last line.
Reasoned from the layout; there is no emulator here, and the phone decides.

### C77. ~~Several deletes spoke all at once, seconds after their rows had gone~~ — FIXED 2026-09-21

After C76 the rows went at the press, but each delete's message still waited for the database. Four
deletes in a row queued on the one database, finished almost together, and four snackbars arrived
at once. Every delete in Settings now says its message at the press, with the row: the size freed
is known before anything is deleted, so the message is the true one. The work goes on behind it,
and only a failure speaks again ("Could not delete …").

### C76. ~~Deleting Modland's index did nothing for ten seconds~~ — FIXED 2026-09-21

Confirmed delete in Settings, and nothing happened; ten to fifteen seconds later "Index deleted"
and the row went. ASMA and UnExoticA went at once. Deleting Modland is half a million rows and
their indexes in one transaction: **5.1 s on a desktop, measured**, on a table built to Modland's
size. Rebuilding the table from the rows that stay measured 2.3 s, and was not taken: it needs the
table's definition written a second time outside the schema, which the next schema change would
forget.

What was wrong was the silence, so the screen is told first: the catalogue shows as not indexed,
its storage row goes, and its Browse row reads "deleting…" and refuses a new download until the
delete has finished.

### C75. ~~A downloaded set's tick appeared seconds late~~ — FIXED 2026-09-21

The song metadata and replay routine rows showed the dim cloud for three to five seconds and then
the tick. The tick waited for the counts — `COUNT(*)` over tables of 380,000 and 476,919 rows,
queued behind the platform counts on the same database — when all it needs is whether anything is
there. `HeldSets` asks that with one `EXISTS` per table and one directory listing per set, and is
published before the counts are started; the counts still arrive later for the second line.
`DownloadOfferTest` holds the rules to the quick answer and not to the counts.

### C74. ~~The replay-routine rows appeared for a few seconds and vanished~~ — FIXED 2026-09-21

Opening Online catalogues on a phone holding both sets of replay routines showed their two
download rows for a few seconds, and then they went. C71's flash again, one screen down: the rows
were drawn while the counts were zero, and the counts are zero until the screen has read them —
which is also what "not downloaded" looks like. C71 fixed the offer at the top and left these.

Fixed by the regrouping of the same day rather than by a second gate: the song metadata and the
replay routines are now one row each, always drawn, and their second line waits for
`heldCountsKnown` — a row that is always there and says nothing until it knows cannot flash.
`DownloadOfferTest` covers the two "complete" rules the rows read.

### C73. ~~The next subsong after a tune ended was selected and never heard~~ — FIXED 2026-09-21

With "play all subsongs" on, `cust.paradroid` moved to its second subsong when the first ended, the
app showed it as playing, and nothing sounded. Pause and play brought it back; four seconds later it
stopped again.

When a tune ends, the audio callback returns `Stop` and Oboe never calls it again, but the stream
object stays. `requestSubsong` then selected the next subsong and called `start()` to be heard, and
`start()` returns at once when a stream exists. `restart()` closes the stream before starting, which
is why pause and play worked. It does the same now.

**Not a UADE defect**, though UADE exposed it: every subsong there ends with a clean short render,
and paradroid's second to sixth are two- to five-second effects, so the "stopped again" was the
same fault at the next boundary. Any backend whose subsongs end by running out would have met it.
Not reproducible off a phone — Oboe is the half the host run does not have — so it was reasoned
from the code; **confirmed on the phone 2026-09-21**, all seven of paradroid's subsongs in turn.
### C72. ~~The app died when Android refused the playback service the foreground~~ — FIXED 2026-09-21

Found on a phone while stepping through `cust.paradroid`'s subsongs, with logcat:
`ForegroundServiceStartNotAllowedException: Service.startForeground() not allowed`, thrown from
`PlaybackService.onCreate`, uncaught, and the process ended.

From Android 12 a service may enter the foreground only while its app is on screen, and the check
is made when the service calls `startForeground` — not when it was asked for. A press in the app
starts the service; if the app is no longer visible by the moment `onCreate` runs, the refusal is an
exception, and nothing caught it. Now it is caught: the service logs it and stops. The music is in
the engine and plays on; the notification returns with the next press in the app.

**What put the service in that position is not established.** The service stops itself when nothing
is playing, loading or current (C43), and something asked for it again from behind the screen. The
crash is fixed whatever the sequence was; the sequence is still owed an explanation.

It was first taken for a UADE crash, because it happened on an Amiga tune. It is not one, though
looking for it did find a real UADE defect of a different kind (`docs/BACKLOG.md` A44, SIGPIPE).

### C71. ~~"Get some music to browse" flashed on every visit to Online catalogues~~ — FIXED 2026-09-21

The row appeared for a moment each time Online catalogues opened, on a phone holding every index,
and then vanished.

The rule offered it while HVSC's song lengths or the track metadata were missing, and read both as
counts. **Those counts are read only when the screen opens**; before that they are zero, which is
also what "not downloaded" looks like. So for the frame between opening and the counts arriving,
the rule saw a phone with nothing on it.

`BrowseState.heldCountsKnown` now says whether the counts are real, and the rule lives in
`BrowseState.offersDownloadEverything` rather than in the composable, where it can be tested.
`DownloadOfferTest` fails with the gate removed. In since round 12 (`030d87a`).

### C70. ~~Random played, and the playlist stayed on screen~~ — FIXED 2026-09-19

Play anything from Browse, then open Random: a tune started and the playlist was still there. The
Random view opened and closed itself in the same frame.

**Two readings of one question.** The view closes when search results take over playback (C49), and
it asked `PlayerUiState.searchMode`, which was `resultsQueue != null` and nothing else. Playing a
dice pick sets `transient` and does not clear the queue Browse was last played from, so that queue
was still sitting in the state — not driving anything, but answering yes.

The transport had the rule right the whole time. `nextFile` reads `transient != null &&
!diceWaiting` as "the dice owns this", and `searchMode` now says the same thing, so a queue left
behind is no longer a queue playing. The dice waiting under an author's list (`docs/BACKLOG.md`
A41) is still search mode, because there the list really is driving next and previous.

`openRandom` also clears the queue now. The reading was the defect; the stale queue was what fed
it, and a session that starts should not carry the last source's state — which is what the page has
always done at the same point (`away = null` in `web/src/app.js`).

`RandomIsNotSearchTest` fails against the old definition.

### C64. ~~Uninstalling did not remove the database~~ — FIXED 2026-09-17

It was not the app's doing: **Android's Auto Backup is on unless a manifest says
otherwise.** The database goes to Google Drive and comes back on the next install, so an uninstall
is undone by the platform.

Now `android:allowBackup="false"` plus a `dataExtractionRules` file that excludes every domain from
device-to-device transfer as well — the flag alone does not cover that from Android 12 onwards.

Four reasons it is off rather than selective, in `res/xml/data_extraction_rules.xml`: most of the
database is a re-downloadable copy of somebody else's archive; a restored SAF grant is dead, because
the permission belongs to the installation that asked for it; a restored database is a schema
nobody tested, since backups cross versions freely; and uninstalling is how a person says "take this
off my phone". Playlists worth keeping have an export, which is a path somebody chooses rather than
one that happens to them.

`UninstallLeavesNothingTest` reads the manifest, because a manifest attribute has no test of its own
and the alternative is finding out on a phone a second time.

### C63. ~~The empty playlist offered Browse on a phone with nothing to browse~~ — FIXED 2026-09-17

The empty screen said "Nothing here yet" and offered **Browse**, which on a fresh install opens a
list of archives that all read "no index — tap the arrow". That is where the first round of testers
stopped (`docs/BACKLOG.md` A46).

It now offers the download sheet instead, and says "Nothing to browse yet" above it — unless there
is an index or a granted folder, in which case nothing changes. A local-files-only user is not
pushed towards a 49 MB download.

**The first attempt at the rule was wrong, and shipped in a build that went out for testing.**
`CatalogueSummary.indexed` is `trackCount > 0 || isOnlineOnly`, and The Mod Archive is online-only —
so `catalogues.any { it.indexed }` is true on every install ever made, including the empty one the
offer exists for. The rule is `BrowseState.hasSomethingToBrowse` now, with rows rather than
`indexed`, and `EmptyScreenOfferTest` fails against the old form.

It also needed the controller to read the catalogue summaries and the granted folders **at
start-up**: the empty playlist has to choose before anybody has opened Browse, and until now both
were loaded only when Browse opened.

### C69. ~~Play refused every deep link: the paths did not start at the root~~ — FIXED 2026-09-18, branch

*Play Console, on the published build: "Deep link not working. Users will not go directly to your
app… Add a `/` to the beginning of the android:path attribute in the `<data>` tag."*

All **111** `pathPattern` entries began `.*\.mod`, `.*\..*\.xm` and so on. A path always begins
with `/` — a `content://` one is `/document/…`, a `file://` one is absolute, an `http(s)` one starts
at the root — and Play refuses a pattern that does not say so.

**Checked rather than assumed to be harmless.** Android's `PatternMatcher` simple glob was
reimplemented and both forms run against four real paths: a picker's content URI, a short path, a
path with an earlier dot, and a Modland URL. Every one matches the same pattern before and after,
through the same one of the three variants. The slash costs nothing and satisfies the report.

`ManifestPathPatternsTest` fails on a pattern without it. There are over a hundred, three per
extension because `pathPattern`'s `.*` does not backtrack, and one missing slash among them is not
something anybody will see by reading.

**This reaches users only in the next release**, as the report says: the links start working when
somebody updates.

### C68. ~~With no length, the bar sat at the end and the total read 0:00~~ — FIXED 2026-09-18, branch

Both halves of C67's other side, and both were waiting to be found the moment a tune honestly
reported no length.

**The bar.** `SeekBar` floors the range at a thousandth of a second so the slider cannot divide by
zero. With a real duration that floor is invisible; with none it *is* the range, so the first tick
put the thumb at the far end. It now draws an empty bar and refuses the drag — a position is asked
for as a fraction of a length, and there is no length.

**The total.** `formatTime(0.0)` is "0:00", which where a total belongs says the tune is over before
it starts. `formatTotal` prints dashes instead. Elapsed zero is still "0:00", because that one is
true.

**What is not a defect:** the tune faded at 2:30 and ended around 2:35. That is `applyFade`, still
set from game-music-emu's `play_length` on purpose (C67) — a looping tune nothing can measure fades
out somewhere rather than running until the fallback cuts it dead.

**What is still missing** is a bar that moves. The app learns the real length every time such a
tune plays to its end and then throws it away; `docs/BACKLOG.md` A50 is the proposal to
write it down.

### C67. ~~Console tunes with no length all claimed 2:30~~ — FIXED 2026-09-18, branch

**Half of it was, and the interesting half was not.**

Measured on that exact file, through this engine: **reported duration 150.0 s, last audible sample
at 29.4 s, the library ended the tune at 30.5 s.** So the tune really is thirty seconds long and
moving on was right. What was wrong was the number over it.

game-music-emu's own header says what `play_length` is:

> Length if available, otherwise intro_length+loop_length*2 if available, otherwise **a default of
> 150000** (2.5 minutes).

`durationSeconds()` returned it whatever its provenance, so every NSF, AY, KSS and GBS that states
no length — and the formats have nowhere to state one — reported exactly 2:30. A default presented
as a measurement, which is the same fault as a SID playing for ever because nothing knew its length
(C56), wearing the opposite coat.

It now asks the fields that admit to not knowing: `length` is "total length, **if file specifies
it**", and the loop fields are -1 when unknown. With none of them known the app is told nothing, and
an unmeasured tune takes the path it already had. Checked both ways on real files: an SPC with an
ID666 length still reports 120.0 s, the NSF now reports none.

`applyFade` still uses `play_length` on purpose: a looping tune nothing can measure should fade out
somewhere rather than run until the fallback cuts it dead. What changed is only what the app claims
to know.

`scripts/check-engine.mjs` builds an NSF with no length in it and fails if a duration comes back.

### C66. ~~The previous-track button drew a wedge on somebody else's phone~~ — FIXED 2026-09-18

*A tester's screenshots of 0.5.0, 2026-09-18: the control left of play drawn as a bar and a
half-filled wedge. Correct on a phone, on every emulator here, and in every browser.*

**SVG says the current point after a close returns to the start of the subpath that was closed.
Some renderers leave it at the last point drawn.** The two readings agree for every circle and
every loop that ends where it began -- which is most of an icon set, and why nothing else looked
wrong -- and disagree the moment a subpath ends somewhere else.

`SkipPrevious` was `M6 6h2v12H6zm3.5 6l8.5 6V6z`. The bar begins at (6,6) and ends at (6,18), so on
that phone the triangle started twelve units lower and drew the wedge in the screenshot.

**Five more were the same shape**, found by walking every path in both players rather than by
looking at them: `Filter` on the phone and on the page, the page's own previous and next, and the
page's pause glyph. Browsers read the spec correctly, so those were right today and fragile
tomorrow.

Fixed by moving absolutely after a close: `zM` means one thing everywhere, `zm` means two.

`scripts/check-icons.mjs` walks all 108 paths in `PlayerIcons.kt`, `web/src/app.js` and
`web/src/index.html` -- joining the fragments first, because one of the fragile ones hid its `z` at
the end of one string and its `m` at the start of the next -- and fails on any path whose shape
depends on the reading. It fails against the old `SkipPrevious`.

**Worth remembering beyond icons:** this is the first defect here that no device in this workshop
could reproduce. It was found by making the rule explicit and checking it, which is the only way
"works on mine" ever ends.

### C65. ~~The button was four pixels too short for its own label~~ — FIXED 2026-09-17

C62 scaled the pill's height with the system text size and left the **base** at 46dp. Its contents
are 12dp of padding, a 20dp icon, 2dp under it and one line of `labelSmall`, whose line box is
16sp: **50dp**. Four short, at the *default* text size, on an ordinary phone — which is why fixing
the scaling did not fix what was on the screen. A descender is the first thing to go, so it read as
"Settings" with the tail cut off its g.

56dp now for a slim pill and 76dp for a full one, both still scaled, and the slim cap raised to the
64dp the bar itself is.

**The arithmetic is a test now, not a comment.** `ActionPillFitsTest` adds up what Compose stacks
inside the pill at seven text sizes and fails if the box is smaller than its contents, or if a slim
pill grows past the bar that would then clip it. It fails against 46dp. Twice wrong by eye is
enough.

### C62. ~~Buttons cut their labels in half at a larger text size~~ — FIXED 2026-09-17

Found by the first closed-testing round, on somebody else's phone: **Przeglądaj**, **Ustawienia**
and **Playlista** drawn with their lower halves missing.

`LabelledAction` fixed its pill at 46dp (slim) or 72dp, and the label is `labelSmall` — **sp, which
grows with the system text size, inside a box in dp, which does not**. At the size that tester runs,
the icon and one line of label no longer fit in 46dp, and Compose clips rather than overflows.

The height now follows `fontScale`, so the rule it was written for still holds — one height for
every pill in a row (C47) — and that one number grows with the words. A slim pill is capped at 56dp
because `TopAppBar` is 64dp and would clip it in turn, and above 1.25x its padding gives up three
pixels rather than the descenders. A slim label is one line with ellipsis: a second line in a bar of
fixed height has nowhere to go.

`PlaylistTopBar`'s name chip already used `defaultMinSize` for exactly this reason and says so in
its own comment. The pill did not.

### C61. ~~Every notice arrived in English on a Polish phone~~ — FIXED 2026-09-17

Also from the first testing round. The screens were translated; the sentences the app *says* were
not. Sixty-one of them were built from literals in `PlaybackController` — "Nothing is indexed yet.
Index a catalogue first." landing on top of a screen that was otherwise Polish throughout.

They are resources now, in both languages, with real plurals: Polish has four plural forms to
English's two, so "Added 3 tracks" cannot be assembled from a number and a noun.

The default playlist name was the same fault one layer down. `PlaybackController` carried
`DEFAULT_PLAYLIST_NAME = "Playlist"` while `playlist_default_name` already existed, translated, and
was read only by the screen — so a new install in Polish opened on a playlist called "Playlist".
Names created from now on are translated; an existing one is the user's data and is not renamed
underneath them.

`MessagesAreTranslatedTest` fails on a literal reaching `Message(`, and fails when the two
`strings.xml` files stop naming the same strings.

### C60. ~~A search matched the whole query as one substring~~ — FIXED 2026-09-16

Both players matched the typed query as **one** substring — `LIKE '%space ninja%'` on the phone,
`title.includes(needle)` on the page — so a single separator in the middle of a filename was enough
to miss it, and tracker files are named with separators.

**It is not a fuzzy search and does not need to be.** Fuzzy matching answers typos; this is about
`_` and `-` standing where a person types a space. Splitting the query and requiring **every word,
anywhere, in any order** covers `space_ninja`, `spaceninja`, `Space Ninja` and `ninja space` alike,
and a word may be satisfied by any of the columns searched — so `4-mat space` finds 4-Mat's
`space.mod`.

**What it deliberately does not do is normalise the stored name**, so typing `spaceninja` still
misses `space_ninja`. That was measured before it was rejected. On 500,000 catalogue rows the worst
case — a search matching nothing, which is every search still being typed — goes from **36 ms to
217 ms** in SQL, or costs a normalised column and about twelve megabytes; in the browser, where the
search runs on every keystroke, from **13 ms to 68 ms**. Splitting the query costs nothing
measurable: **36.5 ms to 36.8 ms** for two words, because the extra clause short-circuits over the
same scan.

The rule is `data/SearchTerms.kt` and `rules.js`'s `searchTerms`, with ten shared cases in
`docs/rules/queue-cases.tsv` that both suites run — including the row that expects the run-on query
to miss, so the day somebody pays for the other half, a failing test says what changed.
`SearchTermsTest` runs the SQL the rule builds against a real SQLite and compares it to the rule
itself, rather than to a hand-written expectation.

### C59. ~~A SID with a known length looped for ever on the web~~ — FIXED 2026-09-16

**The 4:05 is right, and measuring it is how the real fault was found.** The tune is Response's
*Normal People*, which plays to about 3:50 while both players say 4:05. HVSC's entry for it is
`4a2ab3b1574163989e510b70e80314ff=4:05`, the MD5 matches the file byte for byte, and rendering it
a second at a time shows why: the music fades out at **3:38**, twenty-six seconds of near-silence
follow, and at **4:05 the tune begins again**. HVSC times the loop, not the last audible note, and
4:05 is that to the second. Nothing about the length was wrong.

**What was wrong was that the browser did not stop there.** C56's clock checked `duration <= 0`,
on the reasoning that a format knowing its own length announces the end by running out of audio.
That is true of a tracker module and **false of the one format the clock was written for**: a SID
never runs out — it loops. So the moment HVSC's lengths arrived and a SID finally had a duration,
the clock stopped firing for exactly the tunes it existed to stop, and the bar sat pinned at the end
while the tune played round again.

A known length is now a reason to stop rather than a reason not to look: the engine's own length
wins over the fallback, and either ends the tune. The flag is called `endedByClock` rather than
`fallbackFired`, because it never was only about the fallback. The phone was never affected — it has
always taken the known length first.

**And the first fix carried a worse bug, found  within the hour**: a SID ending walked
the queue forward about four tracks. Clearing the guard in `playAt` looked like tidying up after
the old tune and is not — `playAt` begins *loading* the next one, and the worklet plays the old one
until the bytes arrive. Its position messages keep coming across that gap, still carrying the old
tune's clock, and `duration` has just been zeroed so the bar can start at nothing — so the limit
falls back to three minutes and every one of those messages is past it. The guard reopened and the
queue advanced once per message until the fetch finished.

The guard is cleared where a tune actually starts, which is `opened` and `subsong`, and nowhere
else. Three page checks now, and two of them fail against the two rules they replaced.

### C58. ~~Re-indexing was ninety times slower than indexing~~ — FIXED 2026-09-16

**The asymmetry was the diagnosis**: the first index has nothing to clear and the second has an
archive's worth. `catalogue.clear` walked a cursor and called `cursor.delete()` on every record,
which is the obvious way to write it and two orders of magnitude slower than the alternative,
because each step is its own request through the transaction.

Measured at 20,000 records, which is the shape Modland produces: **281 ms to write them all, 26,176
ms to delete them again**. One `IDBObjectStore.delete` over the key range instead — it takes a range
as happily as a key — brings that to **48 ms**.

It looked like it hung on "sorting" because the label is set before the clear and the next one is
not set until storing begins. That is now a window of 48 ms rather than 20 seconds, so it is left
alone; a stage that flashes for a twentieth of a second is noise.

Three page checks guard the thing that made the fast version worth trusting — that the range still
deletes exactly the right rows. `modlandish:` is in there deliberately: it is the case a
hand-written prefix scan gets wrong.

### C57. ~~Search kept the last query with none of its results~~ — FIXED 2026-09-16

**Half the screen was already being cleared.** `BrowseNavigation.enteringDomain` empties `tracks`,
`groups` and everything else about where you had got to, and the query was not in that list: it was
asserted to *survive*, in a test that grouped it with the search scope and the downloaded-data
counts as "things that are not about where you are".

That grouping was the mistake. A scope is a setting and a count is a fact, but **a query is the
input that produced the results the same function has just thrown away** — so what it showed was
the previous words with nothing underneath, which reads as a search that found nothing rather than as a
screen waiting for a new one. The query is cleared with its results now, and the test that defended
the old behaviour says why it changed.

Walking back out of a folder into results does not come through `enteringDomain`, so a search still
survives being walked away from and returned to.

**And the keyboard comes up with the screen**, asked for in the same breath. Search
is the one place nobody arrives to look around — they came to type — and two taps stood between
arriving and typing.

### C56. ~~A SID never ends on the web, and does not on a fresh phone either~~ — FIXED 2026-09-16

**Measured, and the phone's advantage is a database rather than a better engine.** `SidBackend`
reports `durationSeconds() == 0.0` and always has: a SID is a 6502 program with a play routine, and
nothing in the file says when it should stop. Four Rob Hubbard tunes through the engine confirm it —
all four open and play, all four report a length of zero.

So a SID ends only where something *else* supplies the length, and on the phone that is **HVSC's
song length database**: `PlaybackController` hashes the file, asks `SongLengthStore.forMd5`, and the
position poll calls `handleTrackEnded()` once the position passes it. About 61,000 rows. The web has
none of that machinery — no MD5, no store, no watchdog — so `_pt_duration` returns 0, `render` never
runs short, no `ended` message is ever posted, and the tune runs for ever.

**The phone is not actually immune, and this is the part that matters for a release.** The HVSC
database is a **manual, opt-in download** on the Browse screen. Until it is tapped,
`openSongLengths` is empty, the duration is 0, the watchdog's `known > 0.0` is false, and a SID
plays for ever on the phone exactly as it does on the web. The string next to the button
(`song_lengths_none`) says "SID tunes will show no length", which undersells it: they also never
end, and nothing on screen connects the two. A new install is in this state.

**Three separable pieces of work**, smallest first:

1. **Say what the missing database costs.** A one-line change to `song_lengths_none` and the storage
   confirmation. Honest immediately, and it makes the other two optional rather than urgent.
2. **A fallback length for a tune nothing knows** — **decided 2026-09-15**. A setting, with a
   slider, **3 to 10 minutes, defaulting to 3**. It applies wherever nothing supplies a length, not
   only to SID: the fault is "a tune that never ends", and a `.sndh` sc68 has no entry for hangs the
   same way. The default matters more than the range — most people will never open that screen, and
   three minutes is roughly where a C64 tune's loop has said what it has to say.

   **Both players, and the phone's watchdog already exists**: `known > 0.0 && position >= known`
   simply needs `known` to fall back to the setting. The web has no watchdog at all and needs one
   built, which is the larger half of this even though it is the smaller of the two changes.
3. ~~**Song lengths on the web.**~~ **Done 2026-09-16.** The page fetches HVSC's `Songlengths.md5`
   from the same address the phone uses, parses it with the same rules, and stores it in IndexedDB.
   Browse offers the download beside the Modland and ASMA indexes — which is where anybody would look
   for it — and Settings says how many are held and lets them go.

   **Sharded 256 ways on the first two characters of the MD5**, because a hash is uniform by
   construction: 61,157 tunes become 256 rows of about 240 each, rather than 61,157 rows to write
   and 61,157 to delete. Measured end to end against the real 5.2 MB file: **61,157 tunes stored in
   256 ms, a lookup in 1 ms.**

   **The MD5 is written out**, sixty lines in `web/src/songlengths.js`, because `crypto.subtle` does
   not offer MD5 and never will — it is broken as a *security* hash, and nothing here is security.
   HVSC chose it as a key twenty years ago and a lookup has to use the key the database was written
   with. It is checked against the vectors published with the algorithm, and against the block
   boundary at 56 and 64 bytes where a hand-written one goes wrong.

   **Hashed before the bytes are handed to the worklet**, which detaches them — the same point the
   phone hashes at — and only for `.sid`, `.psid` and `.rsid`, since HVSC is a C64 collection and
   hashing a five-megabyte MP3 to learn that is waste.

   The time tokens now live in `docs/rules/queue-cases.tsv` as thirteen shared cases that both
   `RuleCasesTest` and `check-page.mjs` run, so the two parsers cannot drift. The fraction is what
   they are there for: `.5` is half a second, and the field is called milliseconds in places.

   **The fallback length stays.** HVSC knows about the C64 and nothing else, so every other format
   nobody has measured still needs an answer.

   **Its source is the one the phone uses, and the note here said otherwise for a day.**
   `hvsc.c64.org` **does** send `Access-Control-Allow-Origin: *`, on a GET and on a preflight, and it
   answers ranged requests. The 2026-09-15 measurement that said it did not was taken with `curl -I`
   — a HEAD, which that server answers without the CORS filter. `Vary: Origin` was there in the HEAD
   response all along, which is exactly the tell that a CORS decision is being made per request, and
   it was not read.

   **The lesson is the method, not the host**: CORS is a property of the request, so it has to be
   measured with the request the page will actually make. The catalogue table in
   `docs/PLAN_CATALOGUES.md` was re-measured with GET afterwards and its verdicts stand — HVSC was
   the only one the HEAD got wrong, and HVSC was not in that table.

   So the web can do what the phone does. What is left is work rather than a question: 5,205,150
   bytes to fetch with progress, a parser mirroring `data/SongLengths.kt`, storage in IndexedDB, and
   an MD5 — which the page does not currently compute at all.

**Not a defect, and asked in the same breath — SID subsongs already work.** The question asked was what
becomes of them: `SidBackend::subsongCount()` returns `info_->songs()` and `selectSubsong` is
implemented, so the phone shows the strip, the "play all subsongs" setting applies, and HVSC's entry
is a length **per subsong** — `openSongLengths.getOrNull(index)` — so switching tune switches length
too. Three of the four Hubbard files tested carry four tunes each. The web reads the count and
switches as well. The only thing wrong with SID subsongs is the thing above: without HVSC, each of
the four plays for ever instead of one after another.

### C55. ~~A `.sap` refused with "wrong file type for this emulator"~~ — FIXED 2026-09-15

**Two faults in one report, and only one of them is ours.**

*The file is damaged.* `scene register 5 menu.sap`: Modland's copy is 7,562 bytes, and its last
block claims an end address two bytes past that — so ASAP parses the header, finds a valid TYPE B tune by Jakub Karwacki, and then
cannot load the binary into 6502 memory. Restoring the two bytes makes it play at peak 0.75, which
is how the truncation was measured rather than guessed. ASMA, which validates what it accepts, does
not carry the tune at all. **Nothing was made lenient about it**: 111 `.sap` files sampled across 25
Modland authors all played, so this is one broken file in an archive, not a gap in what we accept.

*The message was ours.* `openBackend` asks the backends in order of how strongly they can claim a
file — by name, then by magic, then by content — and each one **overwrote** the reason the one
before had given. `.sap` is claimed by name (`AsapBackend::claimsName`) and ASAP refused it first;
then game-music-emu, whose `gme_identify_header` knows `SAP\r\n` and whose build here has no Atari
emulator, refused it too and spoke over ASAP on its way past. What was shown was a decoder's opinion
about a file it had no business with. **The first refusal is now the one kept**, which by the order
of the asking is always the decoder with the best claim.

`scripts/check-engine.mjs` holds it: a malformed SAP built in the check — valid header, last block
two bytes short, exactly the damage above — is refused, and the reason must name ASAP and must not
name the emulator. The same bytes under a name ASAP does not claim must quote game-music-emu, since
the rule is "whoever claimed it first", not "never say game-music-emu". Both halves fail against the
previous code.

### C54. ~~The dock was taller over Random than over the playlist~~ — FIXED 2026-09-15

The now-playing card took its height from its tallest child, and over Random that is the keep
button — an `IconButton`, which carries Material's 48dp touch target, next to a chevron that is a
bare 24dp icon. The card has a height of its own now, with room for two lines and for that target,
so the dock is the same over every screen.

### C53. ~~Leaving Random kept playing the random tune~~ — FIXED 2026-09-15

`stopPlayback` cancelled the prefetch and closed what was open, but not the **open in flight**. A
fetch already running finished after the switch and started its tune over a screen naming another.
It is cancelled with the rest now.

### C52. ~~A crash while Random advanced~~ — FIXED 2026-09-15

`IndexOutOfBoundsException: Index -1 out of bounds for length 1`, on the main thread, while a track
was playing or changing.

`advanceRandom` reads the record by index, and it **suspends twice on the way there** — filling the
queue asks the database. Start the dice again, or leave for the playlist, while it waits, and it
resumes against a record that is not its own: the cursor back at -1 and the new session's first pick
already in the list, which is the length of 1 in the message. A session counter is raised wherever a
session begins or ends and checked after every wait, and the pick is read rather than indexed.

### C51. ~~Next rolled the dice while an author's folder was on screen~~ — FIXED 2026-09-14

A digression leaves the dice's tune playing, and the transport asks about the transient track before
it asks about a results queue — so next rolled another pick while the author's list filled the
screen. Walking into the folder now sets that queue **without playing anything**, pointing at the
tune the jump was made from, and the transport prefers it while a dice is waiting. The dice keeps
its record and its cursor, and Back still returns to it.

### C50. ~~Browse's rows changed height with the words in them~~ — FIXED 2026-09-14

`DomainRow` was a Material `ListItem`, which is as tall as its content. The Random row says what the
dice is set to, and that sentence is a line longer for some scopes than for others — so the row grew,
its icon sat above centre, and every row under it moved. The rows are now a fixed height with room
for two lines, the icon centred in it, and the text ellipsised rather than allowed to push.

### C49. ~~The Random view stayed up while something else was playing~~ — FIXED 2026-09-14

Reported step by step: in Random, "more from this author", play one of that author's tunes, go
back, press next on the Random list — and the next tune of the **author's folder** plays.

The cause was as reported. Playing from a Browse list moves playback to a results queue, and
`PlayerUiState.randomMode` goes false with it — but the Random *screen* is a flag in the UI that
nothing turned off. So the record of the dice stayed on screen while `next` walked the author's
folder. A file handed over by another app already closed it; a tune played from a list did not.

It closes now on both. Going back lands on the playlist, which says what is playing, as it does for
any search result. The page never had this fault, because a Browse tune there ended the dice session
outright — which is the half that changed next: `docs/BACKLOG.md` A41 made the dice **wait** on both
sides instead, and `docs/SPEC_RANDOM.md` is what the two are held to now.

### C48. ~~The top bar squeezes the playlist's name to fit its buttons~~ — FIXED 2026-09-14

A `TopAppBar` is one fixed-height row and cannot answer that, so the playlist screen no longer uses
one. Wrapping was tried first, and it costs this: the bar changed height as Save and
Discard came and went, and the list under it jumped. `PlaylistTopBar` is **two rows of fixed
height** instead — the name across the whole width with its count beside it, and under it a slim row
of actions with Browse at the left edge and Settings at the right. Save and Discard appear beside
Settings, growing into the gap in the middle, so nothing that was on the bar moves. The pills in
that row are a shorter variant with a smaller icon: five of them cross a phone with room to spare,
which a pill wide enough for an icon beside its label could not do.

### C47. ~~Now Playing's buttons are not the same height~~ — FIXED 2026-09-14

**Two attempts.** Forcing two lines of label on every pill did make the heights equal and put the
words against the top and bottom edges, which was rejected on sight. What it is now: one height for
every pill, whatever the name, with the icon and the label centred between real margins. A long name
still wraps to two lines and changes nothing about the row.

### C46. ~~Adding ticked tracks from search closes the search~~ — FIXED 2026-09-14

### C45. ~~The seek bar's position dot is nearly invisible~~ — FIXED 2026-09-14

**It was the tunes that cannot be seeked.** There the bar drew **no** dot at all -- a deliberate
answer to "this is progress, not a control" -- and Material's disabled track colours are `onSurface`
at a third, which on this surface is a line you cannot read. So where the tune had got to could not
be seen. The dot is now drawn there too, and after one build at half size, which was rejected,
because the track is inset by the thumb's radius and a smaller circle sits higher, taking the bar
with it -- it is the same size and in the same place as the one you can drag. The colours are the
change: the dot in the playing colour, and the disabled track dimmed rather than greyed.

### C44. ~~The playlist list keeps stale counts after "Add to playlist…"~~ — FIXED 2026-09-14

**Making, renaming and deleting a playlist already re-read the list; the writes that change only its
contents did not.** `refreshPlaylists()` now follows every one of them: adding to another playlist,
appending to the one showing, and the debounced write of the queue itself.

### C43. ~~The transport is sometimes missing from the notification~~ — FIXED 2026-09-15

**One path was found and closed.** The service stopped itself whenever the state carried no current
track — and that is not the end of playback: it happens while a queue is replaced and when Random or
History hands the playlist back. Audio is the controller's, not the service's, so the music went on
without a transport; and nothing brought the service back, because a track ending and the next one
starting never passes through the buttons that ask for it. It now stops only when nothing is playing
**and** nothing is loading.

**Not proven to be the reported case**, which was not reproduced then. So `stopForegroundAndSelf`
logs; that line separates "the service stopped" from "the notification was never posted".

**The rest of it, 2026-09-15, with a repro:** enter Random, play something, go to the home screen,
and the music stops — the process taken, with no player in the notification at any point. The
service is started by the view model, and only some of its doors did it: the transport buttons and a
file handed over by another app. **Entering Random, tapping a row in its record, and playing from a
Browse list went straight to the controller**, so those tunes played with no foreground service —
nothing in the notification, and nothing telling the system this process was doing anything. Every
entry point that can make a sound opens the service now, and `ensureServiceRunning` says so where it
is defined.

### C42. ~~One file the engine refuses by throwing ends the whole session~~ — GUARDED 2026-09-14 (web), 2026-09-15 (phone)

From the browser console: a Startrekker AM file (libopenmpt: "external synthesizes instruments …
not supported"), then `uncaught exception: 1464664` from `___cxa_throw` in `engine.mjs` — and after it **no tune plays at all** until the tab is reloaded. A C++ exception
escaping `pt_open` leaves the engine unusable, and the worklet with it. Second of seven in
`docs/PLAN_ROUND_9.md`.

**The file was never reproduced, and the hole was real anyway.** Eight Startrekker AM modules off
Modland open and render with that same libopenmpt warning, and their `.nt` companions are refused
cleanly — so the warning is not the failure and the throwing file is still unknown. What was found
instead: **no entry point of the web engine caught anything**. `openBackend` guards the *choosing*
of a decoder; describing the file (inside `pt_open`), rendering, seeking, asking a length and
selecting a subsong were all unguarded, so any throw from any backend went straight out through
`___cxa_throw` — which is what the report showed, and why nothing played afterwards.

Now every `pt_*` in `player_wasm.cpp` catches, remembers the reason for `pt_last_error`, and answers
with a refusal or with silence; and `processor.js` catches whatever is left, answers `failed`, and
keeps the worklet alive. Checked on one engine instance: a refused file, then a tune that opens and
renders. **If it happens again, the page will now say what the decoder said** — which is the next
piece of evidence.

**The phone had the same hole open until 2026-09-15, and worse consequences.** `player_oboe.cpp`
was written before its web counterpart and never caught anything either: sixteen JNI entry points
and Oboe's audio callback, all calling straight through to a decoder. On the web an escaping
exception wedges a worklet and the tab has to be reloaded; through a JNI frame, or out of the
real-time callback, it is `std::terminate` — the process is gone mid-tune with no message and no
Android crash dialog worth reading. The same shape is in place now: a `guarded` helper, one
fallback per call that means what the caller already reads as failure (no handle, `false`, an empty
string, zero), and the reason to logcat.

Three of those places are not merely "the same as the web's":

* **`onAudioReady`.** The one guard that could not have been put at the boundary, because this is
  Oboe's thread and nothing above it is ours. A throw mid-render is treated as a decoder that ran
  out — the buffer is silenced and the stream stops, which is what the end of a tune already does,
  so Kotlin's poll moves to the next one.
* **`publishDescribe`.** Where the reported crash actually came from: `describe()` walks a decoder's
  instrument and sample tables, and a truncated file is where a length read past the end turns into
  a throw. An unreadable description now costs a line of metadata instead of the process.
* **`nativeOpen`.** The only guarded call with somewhere to put the reason — it already carries a
  sentence back to the screen — so a `std::bad_alloc` from a file too large for the heap, or
  anything thrown that is not a `std::exception` at all, is written into it rather than swallowed.

### C41. ~~"Add to playlist…" takes the track out of the playlist it was in~~ — FIXED 2026-09-14

Adding to another playlist took the track out of the one it was in. It must copy, never move.
`PlaybackController.addToPlaylist(targetPlaylistId, tracks)` writes only the **target**, so the
source is being rewritten somewhere else on that journey — the caller, the selection, or a stale
copy written back afterwards. It looks like losing music, so it was first of the seven.

**It was the `tracks` row, and SQLite's REPLACE.** `LibraryStore.replaceTracks` wrote each track
with `CONFLICT_REPLACE`; a REPLACE is a DELETE and an INSERT; `playlist_tracks.track_id` references
`tracks(id)` `ON DELETE CASCADE`; and `ProtracktorDatabase.onConfigure` turns foreign keys on. So
writing the track for the target playlist deleted **every** row that put it in any playlist, and the
write that followed gave it back to the target alone. A copy behaved as a move. The row is now
inserted with `CONFLICT_IGNORE` and its fields updated, so it is never deleted. Two tests in
`SchemaSqlTest` run both forms against a real SQLite: the safe one keeps the track in both
playlists, the old one empties the source.

### C40. ~~The phone's Now Playing shows only the first line of a module's message~~ — FIXED 2026-09-11

*Found 2026-09-11 while giving the page the phone's Now Playing, not by a listener.*

The engine's `describe` block is `key<TAB>value` lines, and `message` is written **last** because it
is the one value with line breaks in it (`native/engine/engine.cpp`). `NativeEngine.describe()`
splits the whole block into lines and keeps those with a tab: the message's first line survives as
`message`, and every line after it — no tab — is dropped. A line of the message that happens to hold
a tab becomes a bogus key instead. `NowPlaying.kt` draws the message monospaced "because the
alignment is part of what they say", and gets one line of it.

The page had the other half of the fault: it read every line as a key, and asked for `comment`,
which no backend writes, so it showed no message at all. **Fixed on the page on 2026-09-11**
(`describeFields` takes everything after `message<TAB>`); the phone is left for its own branch, the
fix being the same few lines in `NativeEngine.describe()`.

**Fixed the same night** on `fix/module-message`: the parsing moved out of `NativeEngine`, which
loads the native library when touched, into `DescribeBlock`, where a JVM test can reach it — and it
reads the block by the page's rule. `DescribeBlockTest` holds it to a message of several lines with
its spacing, a line of it holding a tab, and the word `message` appearing inside another value.

### C39. ~~Round 8's web bundle, first look: four faults~~ — FIXED 2026-09-11

1. **Random played picks it could not open**, and stopped on them. The index keeps only formats the
   engine plays, but a file can still be refused, packed or gone, and the page had no answer: the
   phone has always walked past those (`skipFailedRandomPick`) and the page's Random was built
   without it. Now it walks past up to eight in a row, as the phone does, and **the failed pick
   leaves the record** — the record is what played, and it did not.
2. **The seek bar hung on the last tune's position** — 1:07 — through the next download. It was only
   ever set by the engine's position reports, which do not come until the new file opens; the phone
   zeroes it the instant a track is chosen. So does the page now.
3. **The playlist chip did nothing during Random.** Deliberately — the phone hides it there — and
   the other thing was wanted: the chip looks like a way out, so it should be one. It now
   opens the sheet, and choosing a playlist ends the session and shows that one.
4. **The page never asked for the re-index it needed.** It did ask, on the root of Browse — but with
   "From the phone" showing, Browse is a single panel that explains why it is shut, and the sentence
   was not on it. It is now.

A fifth report — text-only buttons where the app has icon and label — is a standing requirement the
page did not meet, and is being done on its own branch.

### C38. ~~The Random heading showed over every panel~~ — FIXED 2026-09-11

The heading is shown and hidden with the `hidden` attribute, and styled `display: flex`. An author
`display` outranks the browser's own `[hidden] { display: none }`, so it was never hidden: "Playing at
random", the Filter button and the scope were on screen whatever the page was doing. The same was
true of the Filter row, and of the Filter button History's heading hides.

**It was suspected during round 8 and dismissed on a bad check.** A search of the stylesheet for a
`[hidden]` rule found one and stopped — but the page has only *scoped* ones, `.panel[hidden]`,
`.overlay[hidden]` and the like, each added beside an element's own `display`. The search matched
`.panel[hidden]` and reported the page covered. The next element forgot its line, as it was always
going to.

Fixed with one global `[hidden] { display: none !important }`, so the next element cannot forget.
And jsdom turned out able to see it after all: it runs the page's stylesheet through
`getComputedStyle`, and a page check now asserts that every element marked hidden is not displayed.
Run against the unfixed page it names all three.

### C37. ~~Browse threw on "From the phone" once an index was held~~ — FIXED 2026-09-11, same round

*Found by `GOAL.md` round 8 while building item 4; introduced by item 3 of the same round.*

Item 3 added a Random row to Browse's "From the phone" branch, calling the `row` helper — which was
declared with `const` further down the function. Reading a `const` before its declaration throws, so
**opening Browse on the phone's list with an index downloaded failed outright**. The page checks
never reached that state: when they opened that branch no index record existed, so the call was
skipped. A check now builds exactly that state and asserts it does not throw.

jsdom reads attributes and runs code, but it had never been put in this state, which is how the
fault passed 195 checks. A related suspicion from the same pass — that the Random heading's
`display: flex` would outrank `hidden` — was right, and was wrongly dismissed: see C38.

### C36. A next from the keyboard or a media key can be swallowed after a long press — **OPEN**

*Found 2026-09-11 by `GOAL.md` round 8, while a page check was being written, not by a listener.*

`holdToSkipFile` (`web/src/app.js`) swallows the click that follows a long press on next or
previous, so the press does not count twice. It remembers that it owes a swallow in `held`, and
forgets only on the next `pointerdown`. A finger always sends `pointerdown` first. **The arrow keys
and the media keys do not** — they press the button with `$('next').click()` — so after a long press
that ended without a click (the finger slid off the button before lifting), the next arrow or media
key press is eaten silently.

Narrow, and reproduced only in jsdom: an earlier check holds next and lifts without a click, and a
later bare `.click()` on the same button did nothing. The Random checks now tap the way a finger does
(`pointerdown`, `pointerup`, `click`) rather than clicking bare.

**Not fixed, deliberately.** The obvious repair — forget the owed swallow on `pointerleave` — is
wrong on touch, where `pointerleave` arrives *after* `pointerup` and *before* the click it was meant
to swallow, so a long press would then skip a file and step a track both. The event order has to be
settled on a real touch device before this changes. Recorded so it is not rediscovered as a mystery.

### C35. ~~A Random tune ending skipped several picks at once~~ — FIXED 2026-09-10

**A race between the end-of-track poll and the Random advance.** The poll runs every 200 ms and
skips only while `playing` is false or a load is running. The playlist and search paths call
`load()` before returning, so the next tick finds a load in progress. Random did not: `randomNext`
moves the cursor and publishes the row, then waits on the database for the next read-ahead pick,
and only after that does `playTransient` clear `playing` and start the load. Every tick inside that
wait found the same finished track, still "playing", and advanced again — one more row each time.

Every detail of the report follows from that:

- **only at a natural end** — a pressed next leaves a track that is not finished, so the poll never
  sees one;
- **five, then two, then none** — the count is the query's duration divided by 200 ms;
- **worse under a platform filter** — `format IN (…)` cannot use an index, so the scope is scanned
  and sorted whole ([docs/review-round-8.md](https://github.com/przunk/protracktor/blob/2c35319cfa8b665398a36141248b45544bd68ebe/docs/review-round-8.md) R7 predicted the cost, not this consequence);
- **gone on the second try** — SQLite's page cache was warm by then.

**Fixed by clearing `playing` before the advance, not after.** The poll's own comment already stated
the rule — *"every skip sets `playing = false` before the load starts"* — and this path broke it.
Not reproduced on the host: it needs the phone's database under the phone's timing, so the device
is the test.

The query itself is still unmeasured and still sorts the scope per pick. That is R7, and it is now
known to be more than a performance question.

### C34. ~~The index download choked on the zip's own tail~~ — FIXED 2026-09-10

A zip is not a gzip. After the member's deflate stream come a data descriptor, a central directory
and an end record — **103 bytes** in `allmods.zip` — and the page was handing the decompressor the
whole rest of the file.

**Node ignores those bytes and Firefox refuses them**, which is why this shipped: it was checked by
asking node, and node is not what a phone runs. The same code, the same file, the same
`DecompressionStream` API, and one of them says nothing.

Fixed by feeding exactly the member: its compressed length from the local header, or — when bit 3
of the flags says the writer did not know it yet — from the central directory, found by scanning
back for the end record. `allmods.zip` fills in the local header today; the fallback is fifteen lines
and a zip writer is allowed not to.

**And the checks now build a zip rather than trusting one.** Two of them, one with the length in
each place, asserting the bounds are computed and that the bytes after the member are not passed on.
That is the part that could be checked anywhere; the leniency that hid it could not.

### C33. ~~A Beepola tune came back as MP3~~ — FIXED 2026-09-10

`Beepola/4mat/plastic galaxy.bbsong` — `BBSONG` at offset zero, 27 KB of ZX Spectrum beeper data.
libopenmpt refuses it, correctly, and then minimp3's content guess claimed it and played at **peak
1.08**, which is clipping.

**C32 moved that guess to last and did not make it stricter**, which was half a fix.
`mp3dec_detect_buf` searches for a frame sequence *anywhere* within its scan limit, and 27 KB of
anything obligingly contains one.

**A frame has to be where an MP3 keeps one**: at the start, or at the start after an ID3v2 tag whose
length is read from its four syncsafe bytes. A sync eight kilobytes in is not evidence, it is a
coincidence.

Strict enough to refuse a real MP3 that keeps junk before its first frame, and that is the right
trade: a file called `.mp3` never reaches this code at all, because `claimsName` takes it much
earlier. This only ever judges files that arrived under some other name.

| | |
|---|---|
| `plastic galaxy.bbsong` | refused, with libopenmpt's own reason |
| 3 MPEG streams stripped of their extension | still play |
| 83 conformance streams | 70 / 10 / 3, unchanged |
| 198 console, SID, SNDH, SAP and module files | 172 played, unchanged |

**`.bbsong` is not in `SupportedFormats`**, so Modland's 105 of them were never indexed — this only
ever bit a file opened by hand or arriving in a link.

### C32. ~~MP3 stole an Impulse Tracker module~~ — FIXED 2026-09-10

Reproduced first, which took a minute: the file's first four bytes are `IMPM` and the engine
answered `format MP3 (minimp3) … peak 10.36` — clipping noise, no duration.

**The comment on the dispatch predicted this and the code did nothing about it.** It said minimp3's
detector "walks the file looking for a frame that parses, which is … exactly the test that will
eventually say yes to somebody else's sample data", and then placed that test **before**
libopenmpt. An Impulse Tracker module is tens of kilobytes of sample data; something in it parses.

**MP3 is now claimed by name first and by content only last.**

- `Mp3Backend::claimsName` takes `.mp3`, early, the same shape ASAP has for the fourteen formats it
  tells apart by extension. Nothing else here claims that name.
- `Mp3Backend::recognises` is asked **after libopenmpt has refused** — so a file that reaches it is
  one that nothing which can *prove* what it holds recognised, and a guess costs nothing.

Measured after:

| | |
|---|---|
| `!!uu !! !!.it` | **Impulse Tracker 2.14**, `!!uu !! !!`, 72 s, peak 0.41 |
| 83 MPEG conformance streams, named `.mp3` | 70 played, 10 silent, 3 refused — unchanged |
| 3 of them with **no extension at all** | 3 played — the last resort still does its job |
| 195 console, SID, SNDH, SAP and module files | 169 played, unchanged |

**The lesson is not "guess later".** It is that a comment naming a risk is not a guard against it,
and this one had the risk written down in the same commit that shipped it.

### C31. ~~Back left the file, and a long press meant the opposite of the phone's~~ — FIXED 2026-09-10

**Half the job, done in C23.** `next` was taught to walk the tunes inside a file before moving the
queue; `previous` was not, so on a `.sndh` back left the file whatever the switch said. The phone has
done both since subsongs existed — `PlaybackController.previous()` steps back a subsong when
`playAllSubsongs` and there is one behind.

**And the long press was backwards, which is worse.** On the phone `ui/PlayerDock.kt` binds a long
click to `onNextFile` and `onPreviousFile`: **past the file**, skipping whatever is left inside it.
This page had a long press step *within* the file — the opposite gesture for the opposite meaning.
Found while fixing the first half, not reported, and it would have been maddening to hit.

Both now mirror the phone exactly, forwards and backwards:

| | short press | long press |
|---|---|---|
| switch on | the next or previous **tune**, then the file | past the file |
| switch off | the next or previous **file** | past the file |

`previous` is also live now while the *file* has somewhere to go, which it was not — the button was
greyed at the top of the queue even sitting on a file's fourth tune.

### C30. ~~Six backends never said which tune they were on~~ — FIXED 2026-09-10

`Backend::currentSubsong()` returns 0 unless a backend says otherwise, and **only two of eight said
otherwise** — game-music-emu, which had to because a HES opens at a track with sound in it, and
HivelyTracker. libopenmpt, sc68, ASAP and libsidplayfp all answered zero for ever, whatever was
playing.

Nothing noticed until C23 gave the page a reason to ask. The web player takes its position from the
worklet's answer rather than from its own request — deliberately, so the chips cannot disagree with
the audio — so it believed every file was on its first tune. `next` then asked for tune 2 on every
press, and got it, from the beginning, for ever. Both halves of the report are one line of missing
code.

**The phone was never affected**: it tracks the index itself in `selectSubsong`, which is why this
survived a year of subsong work.

Fixed in the four that were silent: libopenmpt has `get_selected_subsong`, sc68 keeps the track it
was told and converts back from its own one-based numbering, ASAP keeps `song_` — and it is not
always zero even at open, because a SAP names its own default song — and libsidplayfp now remembers
what it reselected. MP3 and ZXTune hold one tune and the default is the truth for them.

**Measured** on real multi-tune files, one per backend that was broken: a 7-tune ProTracker module,
two SNDH files of 6 and 4 tunes, and a 2-song SID. Asked for tune 1, all four now say 1. Before the
fix the interface's own default was the only answer any of them could give.

### C29. ~~The page opened without a trailing slash and every relative URL missed~~ — FIXED 2026-09-10

**The cause is `/src` against `/src/`, and it is ours.** Ask this server for a directory without a
trailing slash and it served `index.html` at that URL and said nothing. The page arrives and renders
— all of that markup is static — and then every relative URL in it resolves one level too high:
`./app.js` becomes `/app.js`, which is not there.

Reproduced exactly:

```
GET /src        200  text/html          ← the page, at the wrong address
GET /app.js     404                     ← what "./app.js" then means
```

And `../lib/qrcode.js` still resolves, from `/src` and from `/src/` alike — so **the QR library
loaded and only `app.js` died**, which is why the page looked right and every control was inert.
"The QR icon does not work" is what a dead `app.js` looks like from outside.

**Fixed** with the redirect every static server does: a directory without a trailing slash answers
301 to the slash form. The fragment rides along because the browser carries it across a redirect
itself, so a shared queue link still works.

### The two things found while chasing it, and kept

**A missing asset was reported as a MIME problem.** `text/javascript` is what this server sends for
a `.js` file that exists — measured. `text/plain` was what it sent for one that does not: the
friendly 404 page. So the browser's sentence pointed at a MIME bug in a server that does not have
one, and cost an evening. A missing asset now answers with its own content type and no body, so the
browser says 404; a path with no known extension — what a person types — keeps the friendly page.

**Every 404 is logged**, because the one place this gets diagnosed is a log file on a machine in
another room. `404 /app.js` in `~/server.log` would have named the cause in one line.

**And `root` no longer follows the caller's working directory** — it comes from the script's own
location. `run.sh` and `serve-web.sh` both `cd` first, so this worked as long as nobody started the
server any other way; a systemd unit with the wrong `WorkingDirectory` made every request 404.
Verified by serving from `/tmp`.

### C28. ~~A line about one track stayed under the next~~ — FIXED 2026-09-10

The status line at the foot of Now Playing is the machine talking about the track in front of it —
what the engine answered, what a control just did. **Nothing replaced it when the track changed.**
So "Tune 2 of 2", written while walking a `.sndh`, sat there under a file that holds one tune, two
tracks later.

It is written on `opened` now, with what the engine said about *this* file: the rate, and how many
tunes are inside when that is more than one. The rate warning still wins where it fires, because a
decoder playing sharp is the more important thing to say.

A sentence about the wrong file is worse than no sentence — the same rule that made a refusal stop
Now Playing describing whatever worked last (C16).

### C27. ~~game-music-emu's fade is set and then thrown away~~ — FIXED 2026-09-09

*Found 2026-09-09 while measuring C26 — by comparing our engine against the library directly, which
is the only reason it was noticed at all.*

`GmeBackend`'s constructor sets the fade and then calls `openAtSomethingAudible()`, which calls
`gme_start_track` again — in **both** its branches, including the early return taken by every file
that has music at track 0. And `Music_Emu::clear_track_vars()`, which `start_track` runs first,
does this:

```cpp
fade_start = INT_MAX / 2 + 1;
fade_step  = 1;
```

So the fade is discarded on every console file this app opens. The comment above the `gme_set_fade`
call says exactly why it is there — *"Without a fade the last buffer stops dead"* — and that is what
happens instead.

**Measured:** the same SPC rendered until the library says the track ended runs **128.0 s** with the
fade set last and **120.1 s** through our engine. The missing eight seconds are the fade.

**Fixed** by making it one function, `applyFade()`, called after every start that begins real
playback — the two ends of `openAtSomethingAudible`, the silent-file fallback, and `rewind`, which
lost it too on every replay. `selectSubsong` already had the order right and now says so through the
same call.

Measured after, on the same corpus and the same file:

| | before | after |
|---|---|---|
| `top gear 2 - title.spc` | 120.09 s | **128.08 s** — the library's own answer to the digit |
| 145 console files through the engine | 120 played, 4 silent, 21 refused | **unchanged** |

The second row is the one that matters: a fade that ends a track could have cut short every file
that states a length, and it did not.

### C26. SPC plays at a different tempo from every other player — OPEN, not being worked on

**Ruled out: the audio path.** This is the defect the web build shipped with until 2026-09-08 —
44,100 samples playing on a 48,000 context, 8.8% fast, about a semitone and a half sharp — and it
was found by a SID sounding quicker than it should. The Android side has the same shape, and the
assumption behind it -- *"Oboe resamples if need be"* -- had never been measured.

It has now. From a device, 2026-09-09:

```
AAudioStreamBuilder_openStream() called
rate   =  44100, channels  = 2, ... sharing = SH, dir = OUTPUT
AAudioStreamBuilder_openStream() got Legacy, devIds = [3], perf = NO, burst = 2215
AAudioStreamBuilder_openStream() returns 0 = AAUDIO_OK
```

**Asked for 44,100 and given 44,100.** The stream is opened at the rate `GmeBackend` synthesises at,
so nothing is resampled and nothing plays sharp. `Player::start()` keeps the check anyway (it costs
one comparison per track and says so on screen when it fires), and on this device it says nothing.

**Measured 2026-09-09, and it is probably not tempo either.** The tune was timed against several
YouTube rips that agree with each other: **Protracktor ends at 1:49, they end at 2:02** — 11.9%
short, which is not 8.8% and not any ratio the audio path can produce.

**An SPC has no end.** It is a memory dump of a running SNES and loops for ever; where it stops is a
number in its ID666 tag, not a musical fact. `GmeBackend` reads `info_->play_length` and hands it to
`gme_set_fade`, so **our ending is the file's own metadata** and a YouTube uploader's ending is
whatever they chose. Two different end policies, compared as if they were two tempos.

**The landmark test says the "it is only the ending" answer was wrong.** The same passage
plays at **0:51.5 here and 0:58.0 there** — 12.6% fast, which agrees with the 11.9% the endings gave.
So it *is* tempo: the music genuinely runs quick, and the early ending is a consequence rather than
the cause.

**And 12.6% is not a ratio this audio path can make.** The only one available is 48,000/44,100 =
8.8%, and the device's own log shows the stream opened at 44,100 anyway. So the error is below Oboe: either
game-music-emu, or the file is not the tune the videos are playing.

**Where it splits, and neither half has been measured yet:**

- **The same `.spc` in the web player.** Same `engine.cpp`, same gme, a different host and a context
  also forced to 44,100. Fast there too, and Android is out of it entirely.
- **The same `.spc` rendered on the host at 32,000 and at 44,100.** 32,000 is gme's native SPC rate
  and needs no resampling; 44,100 goes through `Fir_Resampler`. If the two disagree musically, the
  resampler ratio is wrong. If they agree, the emulation speed is, and gme is a much less likely
  culprit than the rip.
- **The rip.** Several videos agreeing with each other is weaker evidence than it looks — they may
  share one source. Top Gear 2 also exists on Mega Drive, Amiga and PC with different music, and a
  PAL SNES capture runs 5/6 the speed of an NTSC one, which is the same size of error in the same
  direction.

### Measured on the host, 2026-09-09

The file is `top gear 2 - title.spc`, one of two reported alongside a YM the same day. **Not
confirmed as a defect**; recorded because the obvious cause has been eliminated and nobody should
pay for that again. Its ID666 tag: **length 120,000 ms, fade 7,000 ms**, dumped by
Grass-eatin'me, artist *Patrick Phelan, Ashley Bennett*.

Rendered through the vendored game-music-emu on this machine, once at its native rate and once at
the rate the app asks for:

| opened at | tag | rendered until `gme_track_ended` |
|---|---|---|
| 32,000 (no resampling) | 120,000 ms | 4,099,200 frames = **128.1 s** |
| 44,100 (through `Fir_Resampler`) | 120,000 ms | 5,644,800 frames = **128.0 s** |

Repeated with `-funsigned-char`, which is ARM's default and the difference most likely to make a
decoder behave differently on a phone: **identical to the digit**.

**So the decoder is not the problem.** 128 s against the videos' 2:02 is the right answer plus the
fade; the two rates agree with each other; the emulation does not care about char signedness. What
is left is the difference between this machine and the phone, and the only thing in that gap is
Oboe.

**And the arithmetic now fits the classic fault exactly.** 120 s played 8.8% fast is **110.3 s**, and
it measures **1:49**. The landmark pair (0:51.5 against 0:58.0) is cruder and gave 12.6%, but it
was approximate, and 8.8% would put it at 53.3 s. **8.8% is 48,000/44,100** — the one ratio
this path can produce, and the same defect the web build had.

`Player::start()` says nothing, so `getSampleRate()` returns the 44,100 that was asked for. That
does not settle it: on the Legacy AAudio path the stream reports the requested rate while
`AudioTrack` below it is supposed to do the conversion. If it is not doing it, every number here
lines up.

### Settled 2026-09-09: nothing in this app plays it fast

**The listening test first, and it is the better one.** The file was played in the web player and on the
phone at the same time, one in each ear: they stayed in step. Two decoders, two hosts,
two audio stacks, no drift over two minutes. Whatever they are doing, they are doing it identically —
so Oboe was never the difference, and the 8.8% story above is wrong.

**Then, byte for byte.** Thirty seconds of `top gear 2 - title.spc` rendered at 44,100 twice — once
through game-music-emu directly, once through `pt_render` in the wasm engine, which is the same
`GmeBackend` the phone runs:

```
5,292,032 bytes each, cmp -l → 0 differing bytes
```

**Identical.** Not "close": the same file. Our engine reproduces the library exactly, the library
plays the tune for its tagged 120 seconds, and the videos end at 2:02.

**The offset theory was rejected on listening**, 2026-09-10: the tempo is plainly different from
the video. Both were listened to; that is worth more than an inference about
title cards, and this entry stays open on that rather than being closed on an inference.

**What the measurements above actually prove is narrower than I claimed.** They prove our engine
reproduces game-music-emu exactly, and that the phone and the browser agree with each other. **They
say nothing about whether game-music-emu itself plays SPC at the speed a Super Nintendo does.**
Every number in this entry is downstream of that library, so a fault in it would be invisible to all
of them — and a self-consistent wrong answer is exactly what this evidence would look like.

**Not being worked on** (owner, 2026-09-10: *"nie robimy teraz"*). When it is picked up, the missing
measurement is the one nobody has made: the same `.spc` through a **second, independent** SPC
implementation — snes9x, bsnes, or `snes_spc` used directly rather than through gme's wrapper — and
the two waveforms lined up. That is the only test left that can tell "gme is wrong" from "the video
is not this rip", and neither the tag, the fade nor the audio path can stand in for it.

**Two tests, both now run:**

- **A tracker module against the same tune elsewhere** — right (MOD certain, XM almost).
  libopenmpt returns 0 from `preferredSampleRate()`, so its stream opens at the device's own rate
  and nothing is converted. Had the fixed-rate path been broken, this is the comparison that would
  have shown it.
- **The same file in the web player** — identical to the phone, sample for sample by ear.

**Not needed, on the evidence above, but recorded because it is still the better design.** `gme_open_data` takes the rate, so
`GmeBackend` could be opened at whatever the stream turns out to be and return 0 from
`preferredSampleRate()` — no conversion by anybody. That is not available to sc68 or ZXTune, which
synthesise at a rate they do not choose, but it removes the whole question for the console formats.

What is left, in the order worth trying:

- **A different rip.** SPC files circulate in many dumps of the same tune, and Top Gear 2 exists on
  SNES, Mega Drive, Amiga and PC with **different music per platform**. The thing remembered may not
  be the thing playing.
- **gme's SPC timing.** It resamples 32,000 to whatever it is opened at, and a fault there would be
  a fixed ratio on every SPC rather than on two. Cheap to test: the same file in the web player,
  which links the same `engine.cpp` through a context also forced to 44,100. If both sound the same,
  the decoder is consistent and the phone is exonerated twice over.
- **A stopwatch.** An 8% error is ten seconds in two minutes — audible against any independent
  recording of the same rip, and not a matter of opinion once timed.

### C25. ~~Changing the theme restarts playback~~ — FIXED 2026-09-10

**The player was innocent, and the entry said so before the cause was found.** `PlaybackController`
is an application-scoped singleton; an activity being destroyed and rebuilt does not take it, the
backend or the Oboe stream with it. The note above this one warned that somebody would try to fix
that first, and it was right to.

**It was the intent.** `recreate()` — which a theme, palette or language change calls deliberately,
so the window is rebuilt with the new one — hands the activity back **the same intent**. `onCreate`
read `openableUri(intent)` every time, so an app opened by tapping a file in a file manager found
that URI again, handed it to `playExternal`, and started the music over.

Which also explains the shape of the report: it happens after opening a file from another app and
not after launching from the icon, and nobody would think to mention the difference.

The fix is the idiomatic one: read the intent only when `savedInstanceState` is null. That is exactly
"this activity has not been here before" — a configuration change and a restore after process death
both hand back a bundle, and neither is somebody asking to open a file. In the second the controller
restores what was playing by itself.

### C24. ~~Play does nothing on a track that has reached its end~~ — FIXED 2026-09-10

**"Finished" had two meanings and only one was being asked about.** `Track.isFinished()` is set by
the engine when a backend renders a short buffer, and some never do — libsidplayfp is running a 6502
in a loop and has no idea the music is over. For those the *app* ends the track, on the length HVSC
or the file supplied, and the engine still says it is running. So play called `start()`, the decoder
resumed from a position already past that length, and the poll ended it again within two hundred
milliseconds. From outside: a button that does nothing.

The rule is now `PlayFromEnd.shouldRestart`, in its own file and pure, for the reason
`SubsongAdvance` is — the alternative is reasoning about it inside a method that needs a phone to
run. Its awkward case has a test: a duration of zero means *nobody knows the length*, not *zero
seconds long*, and without that guard every paused tune with no stated duration would restart
instead of resuming.

In the browser the same press now sends `rewind` to the worklet rather than re-fetching bytes it is
already holding.

### C23. ~~The web player's `next` skips the file, never the tune inside it~~ — FIXED 2026-09-10

A `.sid` or a `.sndh` holds several tunes; the phone knew and the page did not, so on a file with
twelve subsongs eleven were reachable only by tapping a chip.

Built as the phone's, because the phone's behaviour is the specification: a switch in Now Playing
(*"Play every tune in this file"*, kept per browser and shown only where it decides something) and a
**long press on next** that skips within the file whatever the switch says. The end of a tune now
walks the file before it walks the queue — and repeat-one is checked first, deliberately, because it
means "this tune again" and a file's other tunes are not it.

Two things fell out of doing it properly. The worklet **answers** a subsong request now, with that
tune's own duration and title: a GBS gives each track its own length, and the page had been showing
the first one's against the third one's audio — the same defect `selectSubsong` fixed on the phone.
And `opened` carries `pt_current_subsong`, which is not always zero: `GmeBackend` opens a HES or KSS
at the first track with sound in it, so assuming zero pointed the chips at a tune that was not
playing.

### C22. ~~The volume control sits under the repeat button~~ — FIXED 2026-09-10

The cause was the thing that made it work: `.volume` was positioned absolutely so it could not
decentre the transport, and `.transport` is `justify-content: space-evenly`, so `repeat` was placed
knowing nothing was there.

**The space is reserved now rather than taken.** A spacer of the same width on the left, the five
buttons spread between them, and one `--volume-width` variable that both read — so the narrow-screen
rule that hides the slider cannot leave the buttons off-centre by the width of something invisible.
Absolute positioning could keep play centred or keep the two apart, never both.

### C21. ~~The seek bar does not fill in behind the handle~~ — FIXED 2026-09-10

The only thing saying where you were was the handle. Every player fills the part already played, and
the phone does.

`::-moz-range-progress` exists in one engine and has no webkit counterpart, so the portable answer is
to paint the **track itself** with a gradient and move its stop from script — a custom property on
the input, which inherits into the pseudo-element. The volume slider gets it too, and a disabled seek
bar deliberately does not: a slider that says "you cannot move this" must not also say "you are
here".

The one trap: assigning to `.value` fires no event, so every place that sets a slider from state
paints it as well.

### C20. ~~The index promises files no backend can open~~ — HALF FIXED 2026-09-09

*Found 2026-09-08 by the wasm probe (`docs/PLAN_WEB.md` §14), and it was an **Android** defect — the
same files were refused on the phone. The web build only made it easy to run hundreds of files at
once, which is what the probe is for.*

`SupportedFormats.extensions` is what a folder scan and a catalogue index are filtered through, and
it had the same rule written into it twice already: `.gym` and `.snd` were removed because listing
them "only indexed files that cannot open". Two more were in that state and had not been noticed.

**`.ym` — 4,961 files in Modland. Fixed.** They play, 20 of 20 sampled
(`docs/PLAN_FORMATS.md` §8). The entry below is left standing because the reasoning in it was
**wrong in one clause** and that is worth keeping visible:

> `Sc68Backend::worthTrying` claims a file only on `ICE!`, `SC68` or an `SNDH` tag in the first 256
> bytes; a YM file has none, so it never reaches sc68 at all. Nor would it help: the vendored
> `file68` has no YM loader and no LHA support, and **Modland's YM files are LHA-packed** (`-lh5-`
> at offset two). So nothing here can open one, packed or not.

Everything up to the last sentence holds. The last sentence surveyed the backends we *build* and
concluded about the ones we *have*: ZXTune ships a YM/VTX decoder and `native/backends/zxtune/
CMakeLists.txt` had been excluding it, in a comment on the exclusion, for want of lhasa. The lesson
is narrow and repeats one from `docs/PLAN_FORMATS.md` §7 — **check the library's source tree, not
the list of what the build compiles**, because the second is a decision somebody made and can be
unmade.

`.vtx` came with it: 879 files, the format the original exclusion was weighed against.

**`.med` — 132 of Modland's 140. Still open.** They begin `MED\x04`, the older Amiga *Music Editor*
format. libopenmpt's loader requires `MMD` at offset zero (`Load_med.cpp:865`) and handles MMD0–MMD3
only. The other 8 are genuine OctaMED and do play. lhasa does nothing for this; it needs a decoder
nobody here has, which puts it with UADE's territory rather than with anything cheap.

**The re-index this cost.** Adding `.vtx` changed `SupportedFormats.fingerprint`, so every stored
index went stale and Modland's 40 MB is downloaded again. That price was the argument *against*
option 1 in the original entry — paying it to remove 5,093 dead rows. Paying it to gain 5,840
playable ones is the same transaction with the sign flipped.

### C19. ~~"More from this author" offered itself where it could never work~~ — FIXED 2026-09-08

**The Mod Archive is searched live and has no index at all.** It publishes no file to download, so
`catalogue_tracks` never holds a row for it; the lookup this action does could not have succeeded
for one of its tracks on any day. Worse, its search result rows carry a title, a format and a module
id and **no artist** — so there is not even a name to search by instead.

The action was gated on "did this come from a catalogue", which The Mod Archive answers yes to. It
is gated on "…from a catalogue with an index" now, and is simply absent for a live-search one, the
same way it is absent for a local file and for the same reason: there is nowhere to go.

**Two wrong messages were behind it, one of them mine from an hour earlier.** The original said
"Only tracks from an online catalogue can do that", which was untrue. Splitting that produced "not
in the ${'$'}{catalogue} index — index it to jump to the author", which is right for a Modland track whose
index was deleted and **wrong here**, because The Mod Archive cannot be indexed. Both survive, for
the cases they are actually about, behind a gate that stops either being shown to a live-search
track.

The report contained the answer: the track had been added from a search the day before. The catalogue was
in the message the whole time.

### C18. ~~A downgrade left an app that could not start~~ — FIXED 2026-09-08

Historical recreation policy below is superseded by C108 (2026-10-15): preserve newer-schema data
and display a compatibility refusal. The earlier claim that recovery requires clearing app data
was wrong; keeping the file allows a compatible build to reopen it. C108 tests the actual helper
with native SQLite on the host, so this case is not limited to device-only reproduction.

*Found while adding schema 12, by reading the method next to the one being changed.*

`onDowngrade` exists so that installing an older build over a newer one recreates the database
instead of throwing on every launch — which is what SQLite's default does, and it leaves an app that
cannot be opened at all until its data is cleared by hand. **It had been doing exactly that itself
for seven versions.** It dropped a list of five tables, written when there were five, and then ran a
`CREATE` that had grown to twelve; the recreate met a `catalogues` that was still there and stopped.
Verified against a real SQLite: `[SQLITE_ERROR] table catalogues already exists`.

Nothing caught it because **a downgrade needs a device**. The tests here run migrations, and
migrations never take this path; there is no emulator in this environment (AGENTS.md §3).

**Why it had not been seen on a phone either:** Android refuses to install a lower `versionCode`
over a higher one, and `versionCode` is the commit count, so every older schema also has a smaller
number. The combination that bites is a **higher number carrying an older schema** — which is
precisely what a schema bump sitting on a branch produces while `develop` keeps moving. Schema 12
was on such a branch when this was found.

The tables are asked of `sqlite_master` now, because a hard-coded list is a claim about the schema
kept somewhere other than the schema, and it went stale the first time one was added.

**And the test earned its place immediately.** The first version dropped tables in the order
`sqlite_master` lists them and failed with `no such table: main.playlists`: `DROP TABLE` runs an
implicit delete of the table's rows, that delete resolves foreign keys, and the app turns foreign
keys on. Dropping in reverse is dependency order, because a child is always created after its
parent. That would have shipped as a second version of the same bug.

### C17. ~~Holding next skipped two files, then four~~ — FIXED 2026-09-08

**The first diagnosis was wrong and is recorded here because it was believable.** It said the 200 ms
position poll saw the outgoing decoder finish while its replacement loaded, and called
`handleTrackEnded` a second time. It cannot: every skip sets `playing = false` before it starts
loading, and the poll checks that first. The fix that came with it — the poll deciding nothing while
a load is in flight — is harmless and stays, but it never was the cause.

The cause is Compose. The dock passed `onLongClick = onNextFile.takeIf { state.canGoNextFile }`,
which builds a **new lambda on every recomposition**. `combinedClickable` keys its gesture detector
on the handlers it is given, so a new lambda tears the detector down and starts a fresh one — and a
detector started while the finger is still down begins timing a new long press from that moment.
Skipping a file changes the title, the title recomposes the dock, the dock restarts the detector,
and the detector fires again about every 500 ms for as long as the press is held. Two files for a
short hold, four for a longer one; the count follows the duration, which is what "teraz o 4" said.

`TransportButton` now takes a plain `onLongClick: () -> Unit` with a separate
`longClickEnabled: Boolean`, and wraps both handlers in `rememberUpdatedState` + `remember` so the
modifier is handed one stable lambda for the life of the button while the behaviour behind it stays
current. `DomainRow` in the browse screen had the same shape and got the same treatment.

**What to take from it:** "sometimes two" invited a race, and a race was duly found. The number
going to four when the hold got longer was the fact that did not fit, and it was in the second
report, not the first.

**And a third report that was not a defect.** With the fix installed the hold appeared to "jump
somewhere random, six forward, sometimes backwards" — shuffle was on, and had gone unnoticed. Under
shuffle the next *file* is the next one in the shuffled order, which is a jump; the short press does
exactly the same thing, which is the check that settles it in one press. Worth keeping because two
of the three reports about this button were about the play order rather than the button.

### C16. ~~One message for four different failures~~ — FIXED 2026-09-07

*Found twice in one day,  and by me, and it cost an hour each time.*

`ice.pt2` failed to **download** and the screen said nothing about the network; it played on the
second attempt. `&SFTDEMO.stc` is one file among Modland's 3,639, of which this build plays 95%, and
the screen said *"Spectrum is a format Protracktor cannot play yet"* — naming a whole platform.
Both readings sent the search somewhere useless, and in the second case the message was simply
false: the app claims the format and plays nearly all of it.

**The rule is short: never blame the format for a file we claim.** If `SupportedFormats` says the
name is playable, a failure is about that file. Four messages now, chosen by `OpenFailure` where the
choice can be tested and worded in string resources where it can be translated:

- the bytes never arrived — *"Could not download X. It may be the connection — try again."*
- no backend claims the name — *"X is a format Protracktor cannot play yet."*, which is the only
  case where naming the format is honest
- we claim it and a decoder gave a reason — the reason, attached to the file
- we claim it and nothing said why — *"X would not open. Other files of this format do play, so it
  is this one."*

The decoder's own reason is kept where there is one: it is true, and occasionally it is the only
thing that says which backend gave up. It is no longer offered as a verdict on the format.

**And the reasons are now one sentence in six voices instead of six.** The question was whether
naming ZXTune in the app was right; it is — the Information panel already credits every backend by
name, five of the six licences ask for attribution, and the library name is the one fact that makes
a report actionable, as `.stc` proved that morning. But "ZXTune" alone can read as a program the
user is missing, so every message now names the platform beside it:

> `&SFTDEMO.stc` would not open: the ZX Spectrum decoder (ZXTune) did not recognise it

The platform explains itself, the library stays as a credit. libopenmpt is the awkward one — it
throws its own exception, so its sentence is written where it is caught rather than where it is
raised. Two messages that wrapped another message ("ASAP refused it: ASAP could not load...") lost
their outer layer.

### C15. ~~The Mod Archive returns nothing~~ — FIXED 2026-09-06

Every search against The Mod Archive comes back with zero results. Modland and ASMA answer normally
in the same search, so it is this one source rather than the search.

**What has been ruled out:** not the catalogue being treated as unindexed. `CatalogueSummary.indexed`
is `trackCount > 0 || isOnlineOnly`, and The Mod Archive sets `isOnlineOnly`, so it is in the set an
empty selection expands to and reaches `ModArchive.search` normally.

**What is left to check**, in the order that costs least: whether the request is being made at all,
what the service answers, and whether the response shape it is parsed against still matches. It is
the only source that is a live call to somebody else's server, so it is also the only one that can
break without anything here changing.

**Not the blank-query change.** That skips The Mod Archive deliberately — there is no index here to
list — but the report is about typed searches.

### Worked on 2026-09-06 — the parser was fine, and two other things were not

**The scraper works.** `https://modarchive.org/index.php?request=search…&query=elysium` was fetched
with the app's own User-Agent: HTTP 200, no redirect, and the page contains two results in ordinary
`<tr>` rows. Every one of the parser's five patterns matches them. That page is now saved as
`app/src/test/resources/modarchive-search-elysium.html` and `ModArchiveSearchTest` parses it —
pinning a scraper to a real page is the only honest way to test one, and when the site changes that
test is what says so.

So the defect was somewhere else, and two candidates were found without a device:

**The failure was swallowed.** `runCatching { … }.getOrDefault(emptyList())` turned a blocked
request, a dead network and a changed page into the same answer as "this tune is not in the
archive". `search` now returns **null** when it could not ask, logs what the server said, and the
search reports "The Mod Archive could not be reached." A fact and a fault are different things and
the app was saying only one of them.

**The results were buried.** `SearchResults.combine` appended the live search last, after the
offline indexes — which return up to `PER_SOURCE_LIMIT`, two thousand rows. Forty live results at
row two thousand and one are present, correct and unreachable, which is exactly what "returns
nothing" looks like from the sofa, with nothing broken at all. Display order and de-duplication
precedence are now separate: a live result is placed with the local ones, while a duplicate is still
resolved in favour of the copy you already have.

**It came back "Nothing found for this search."** — so the request was made, the server
answered, and nothing was parsed. That rules out the network and points at the page.

Which turned up a third thing, found by asking the site rather than the code: **its "no results"
answer is not an empty page.** Searching for `zzzzqqqq` returns *"Or perhaps enjoy some of these…"*
and ten unrelated modules, each with a working download link. A parser that went looking for
download links returned all ten as matches — so this catalogue could report the wrong tunes as
readily as none. `parseSearchResults` now refuses any page without the results heading, and that
page is saved as a second test fixture.

The live search reports three outcomes now, not two: reached and read, could not reach, and answered
with something unreadable.

**Confirmed fixed on a device**: `elysium` returns its two modules. So the cause was the
burial — forty live results appended after a full catalogue page — and not the network or the
markup.

**An empty query is a separate case and stays skipped.** The archive answers one with the same
"perhaps enjoy" page: it has no way to list itself, so there is nothing to ask for. The screen used
to say "nothing found", which is a claim about the archive rather than about what we did; it now
says the source is searched live and needs a term, and only when that is the whole story.

### What The Mod Archive does not publish

**The artist is not in the search listing**, and this was checked rather than assumed: the expanded
view (`&detail=1`) adds Module ID, genre, size, channels, downloads, date and licence — and not the
author. So a result carries a blank author and a subtitle of `The Mod Archive/MOD`, where the second
part is the format because there is no folder to name; unlike Modland, this archive is flat.

Getting the artist means one request per result to the module page — forty requests for a page of
results. The shape that would work is the one the app already uses for local metadata: fetch it
lazily, when the user opens a track's information. Not started, and worth doing only if it is wanted.

### C1. ~~Roughly half of `.sndh` files do not play~~ — FIXED 2026-09-03 by sc68 3.0.0b

Measured on thirty random Modland files through the real backend logic: **16 play, 5 load and render
silence, 9 fail `api68_load_mem`**. sc68 2.2.1 is from 2003 and its SNDH support is partial.

The fix is sc68 3.0.0b — see `docs/PLAN_FORMATS.md` §0, which has the route and what it costs.
Failures now say which backend refused and what it said, rather than claiming the format is
unsupported.

### C2. ~~`.sc68` container files — status unknown~~ — ANSWERED 2026-09-02: they play

Ten random `.sc68` files from Modland (of 1,775) through the host probe: **8 play, 2 render silence,
0 fail to load**. Better than `.sndh`, and the two silent ones fail in the same shape as the silent
SNDH files — the same 2003 library, the same partial coverage, so C1's fix is this one's fix too.

Kept in the list with its answer rather than deleted: the entry existed because the reasoning
underneath it had gone stale, and the record of that is worth more than a shorter list.

### C8. ~~Back restored the position for one level only~~ — FIXED 2026-09-03

Reported  the same day A20 was confirmed working: *"back really does go back to where I
was, but only once — two folders down, the last level goes back and the one above starts at the top
again."*

**Cause.** Going back a level sets the new level immediately and fetches its contents
asynchronously, so for a moment the level key is the parent's while the list on screen is still the
child's. `RestorePosition` saw a non-empty list without the row it wanted, concluded the row had
gone, and threw the marker away. The real list then arrived with nothing left to restore.

The deepest level worked because its list is briefly *empty* rather than stale, and an empty list was
already treated as "not loaded yet".

**Fix.** The decision moved into `BrowseScroll.restoreFor`, which returns wait / scroll / forget, and
waits while the level is loading as well as while its list is empty. It is a separate function so it
could be tested: removing the loading check fails the test that catches this.

### C9. ~~"No backend recognised it" for files we could name~~ — FIXED 2026-09-03

Reported  2026-09-03: two `.sid` files from Modland refused with *"no backend recognised
it: error reading file"*.

**Not a SID defect.** Of Modland's 60,633 `.sid` files, **60,572 are HVSC (Commodore 64) and 61 are
"SidMon 1"** — an Amiga tracker format that happens to use the same extension. A SidMon file begins
`08 f9 00 01 00 bf e0 01`, which is 68000 machine code (`bset.b #1,$bfe001`, the Amiga "stop the
drive motor" idiom), not `PSID`. So libsidplayfp is never offered it, libopenmpt cannot parse it,
and the message is the fallback's.

**Two separate things follow, and neither is "fix SID".**

- **The message is true and useless.** The app knows the file came from Modland's `SidMon 1`
  directory: the catalogue index stores that as its format. Saying *"SidMon 1 is an Amiga format
  Protracktor cannot play yet"* costs a lookup and turns a dead end into an answer.
- **SidMon 1 needs UADE** (`docs/PLAN_FORMATS.md`, `docs/BACKLOG.md` A5). 61 files is not a reason
  to hurry, but it is a concrete instance of the Amiga gap rather than an abstract one.

**Fixed:** a catalogue track that no decoder opens now names the format the archive files it under.
The catalogue index still admits these files, because it filters by extension and that is the one
place content cannot be probed — half a million files live on somebody else's server
(`docs/ARCHITECTURE.md` §18). The format directory was information we already had and were not
using.

SidMon 1 itself still needs UADE (`docs/BACKLOG.md` A5). Sixty-one files is not a reason to hurry.

### C10. ~~The list stutters for the first ten to twenty seconds after launch~~ — NOT A DEFECT, 2026-09-03

Reported from a device, and established to **predate the scrollbar it was first blamed on** by
going back to the previous build. That mattered: it stopped the investigation looking at the new
thing.

**Cause.** Background metadata resolution opens each unidentified track to learn its real title, and
`adoptTitleFrom` wrote the playlist to disk after every one. `LibraryStore.replaceTracks` deletes
every row of the playlist and reinserts it — **two inserts per track** — so a three-hundred-track
playlist meant some six hundred inserts, roughly eight times a second, into the same database the
list was being read from, for as long as the resolution ran. Which is ten to twenty seconds.

**That explanation was wrong**, and one sentence disproved it: it still stutters for
twenty seconds on a playlist of **twenty-two** tracks. Twenty-two tracks is forty-four inserts per
write — nothing. The debounce is kept because writing the whole playlist per resolved track was
indefensible anyway, but it was not the cause.

**Second attempt, and what is actually known.** Twenty seconds for twenty-two tracks is roughly a
second each, which points at the per-track work rather than at anything cumulative: reading the
file, building a decoder, tearing it down. Two things were wrong with how that ran, and both are
fixed:

- `describe()` and `close()` were on the **caller's thread, which is the main one**. `close()`
  destroys a decoder — for sc68, an entire 68000 emulator.
- All of it ran at **default priority**. `Dispatchers.IO` competes with the UI thread on equal
  terms, so a second of native work per track is a second of contention per track. It now runs on
  one thread at `THREAD_PRIORITY_BACKGROUND`, in Android's background cgroup, where it gets a small
  share of the processor and cannot starve drawing however long it takes. The library scan uses the
  same thread for the same reason.

**And it is now measured rather than reasoned about.** Each resolution logs its own duration
(`Protracktor` tag). The first explanation here was confidently wrong; a number would have shown
that immediately.

**Third attempt, and this one came from measuring rather than reasoning.** Compared
the two screens: *Browse with a local folder of 300 tracks does not stutter; the playlist with 22
does.* That rules out the background work entirely — it runs the same either way — and points at
what the two screens do differently.

`PlaylistBody` took the whole `PlayerUiState`. `positionSeconds` is updated every **200 ms** while
anything is playing, so every row, every drag modifier and every list item recomposed **five times a
second**. Browse takes `BrowseState`, which does not tick, and stayed smooth at three hundred rows.
It now takes the track list rather than the state, so a position tick cannot reach it.

The scrollbar had the same shape of problem, smaller: it re-runs on every frame of a scroll — that
is what a scrollbar is — and was doing it with `BoxWithConstraints`, a subcomposition. It is three
weighted boxes now, which is pure layout.

**A fourth thing:** after the first twenty seconds it still stuttered a little, and
*most visibly when flinging the list hard*. A fling is a per-row cost rather than a per-second one,
so that pointed at what each row does — and every playlist row carried
`graphicsLayer { translationY = dragOffset }` unconditionally, which allocates a render node per
row, created and thrown away again for every row a fling brings past. Browse's rows have no such
modifier. At most one row is ever dragged, so the layer is now applied only to that one.

**Then the shape of it changed the question.** The fifth report is not about a per-item cost
at all:

> It stuttered. I added 200 SAP tracks — it still stuttered, but briefly. About five seconds after
> adding them it ran **better than it had with 22**. Restarted the app: swiping stutters, and after
> ten or fifteen swipes it stops.

**More items made it faster, and repeated scrolling cures it.** No amount of per-row or per-second
work behaves like that. That is a **warm-up** curve: ART interpreting until the JIT compiles the
paths, and Compose composing each composable type for the first time.

**Which makes the build type the first thing to rule out.** Every measurement so far has been on a
**debug** APK, and a debug build is `debuggable=true` — which turns off a great deal of ART's
optimisation and holds the JIT back, on top of having no R8. A release build is the honest
comparison and has never been tried.

**If it persists in release**, the answer is a **baseline profile**: this project has none, and
"janky until it warms up, then fine" is precisely what one exists to fix. Generating a real one
needs a device or emulator, so it is a run on a phone rather than a workshop one.

**Answer: the release build has none of it.** Installed and reported as *"zero stuttering
now"*. C10 was an artefact of testing on a **debug** APK, which is `debuggable=true` and gives up a
great deal of ART's optimisation for it. There is no defect in the app that ships.

**What that cost, and what it bought.** Four fixes went in chasing it, and all four were real
problems worth keeping — a full playlist rewrite per resolved track, background work at foreground
priority, the whole list recomposing five times a second while playing, and a render node allocated
per row. None of them was the cause. The cheapest check of all, "is this the debug build", came
fifth.

**The rule that follows is in `AGENTS.md`:** performance is judged on a release build. A debug build
is for finding out whether something *works*.

### C11. ~~Auto-advance raced through every tune in silence~~ — FIXED 2026-09-03

Reported  the day subsongs landed: pressing **next** moved to the following tune
correctly, but *letting one end* skipped instantly through all the remaining ones without a sound.

**Cause.** When a backend runs out, `onAudioReady` returns `Stop` and Oboe calls it no more. The
stream object is still there — it is simply never entered again. A subsong switch is handed to that
callback on purpose, because it is the only thread that touches the decoder; handed to it *after the
end*, it sat there forever. `finished_` stayed true, the poll on the Kotlin side asked for the next
tune, and the same thing happened again, all the way to the last.

**Fix.** When nothing is running the switch is applied directly and the stream is started again. The
test for "running" is **`stream_ != nullptr && !finished_`**, because the stream outlives the last
callback — which is the whole trap.

**`seek` had the same latent bug** and is fixed with it: seeking a tune that had just ended stored a
request for a callback that would never run. Nobody had reported it, because seeking a finished
track is a thing people rarely do.

### C12. ~~The notification had no skip buttons in Random~~ — FIXED 2026-09-04

Reported, who also guessed correctly that it would not be only Random.

**Cause.** The media session declared `ACTION_SKIP_TO_NEXT` and `ACTION_SKIP_TO_PREVIOUS` from
`queue.hasNext` and `queue.hasPrevious` — that is, from the **playlist**. But next does not always
walk the playlist: in Random it walks the picks, and in a search it walks the results. The app
already had `canGoNext` and `canGoPrevious` for exactly that, and the notification was not asking
them. So the buttons vanished in precisely the modes where the dock was still offering them, and
search had the same fault.

Since Android 13 the system builds a `MediaStyle` notification's buttons from the session's
`PlaybackState` rather than from the notification's own actions — which is why the
`Notification.Action`s, added unconditionally all along, were never the thing to look at.

### C13. ~~Repeat-one does not repeat a subsong~~ — FIXED 2026-09-04

Reported 2026-09-04: repeat-one did not repeat a subsong. Reading the code turns
one report into **two independent faults**, and only the first is unambiguously a bug.

**Fault 1 — `Sc68Backend::rewind()` hardcodes track 1.**

```cpp
void rewind() override {
    sc68_stop(sc68_);
    sc68_play(sc68_, 1, SC68_DEF_LOOP);   // <- always the first subsong
    ...
}
```

`restart()` is what repeat-one calls, and it goes through `rewind()`. So on a multi-tune SNDH sitting
on subsong 5, repeat-one plays **subsong 1** — it does repeat, just not the thing that was playing.
The other backends already do the right thing: ASAP replays `song_`, game-music-emu replays
`track_`, and libsidplayfp reloads the tune with its selected song. sc68 is alone in forgetting.

The fix is to remember what `selectSubsong` chose and rewind to that. It is provable on the host
through `probe_render.c` without a device: select a subsong, rewind, check which one plays.

**Fault 2 — with "play all subsongs" on, repeat-one is never consulted.**

`onTrackEnded` walks to the next subsong *before* it looks at the repeat mode:

```kotlin
if (now.playAllSubsongs && now.subsong + 1 < now.subsongCount) { selectSubsong(now.subsong + 1); return }
```

So under all-subsongs + repeat-one, every subsong but the last ignores repeat-one entirely, and the
last one then falls through to `restart()` — which on ASAP, GME and libsidplayfp loops that **last**
subsong forever, and on sc68 jumps to the first. Three backends, three behaviours, none of them
chosen.

**What repeat-one should mean here is a real question, not an oversight to patch.** "One" everywhere
else in this app means one row of the playlist, and a multi-tune file is one row. That reading says:
with all-subsongs on, the end of the last subsong goes back to the first and the whole file loops;
with all-subsongs off, the current subsong loops. It is coherent and it is a guess — it belongs in a
commit that says so, not in silence.

**Fixed 2026-09-04, both halves — and the guess in the paragraph above was wrong.**

Tested on a device the same day: with a multi-tune file, repeat-one walked on to the next subsong.
Whatever "play all" is set to, repeat-one should always repeat the one that is playing.
One to one."* **Repeat-one outranks "play all".** While it is on nothing advances, and the tune that
just ended plays again, whether it is a whole file or the fifth tune inside one.

The reading it replaces — "one" means one row of the playlist, so a multi-tune file loops from its
first tune — is defensible on paper and fails the only test that counts: with "play all" on,
pressing repeat-one still moved off the tune that was playing. **A repeat that goes somewhere
else is not a repeat.** This is the second time a subsong control was designed to mean different
things in different modes, and it was corrected to the simpler rule; the pattern is
worth naming rather than meeting again.

That correction rests on the other half, which was right: `Sc68Backend` remembers what
`selectSubsong` chose and rewinds to it — so "repeat what is playing" reaches the right tune.
`native/probe/sc68/probe_subsong_rewind.c` demonstrates the fault rather than arguing it, on the
same corpus the other sc68 probes use — six of the forty files have more than one tune, and all six
show the same thing:

```
subsongs=21 chose=11 selected=11 before=1 after=11 DEMONSTRATED
subsongs=9  chose=5  selected=5  before=1 after=5  DEMONSTRATED
```

Writing that probe found a second thing worth keeping: `sc68_play` sets the *pending* track and the
change lands when processing next runs, so asking which track is current before rendering reports
the previous one. The first version of the probe did exactly that and reported `selected=1` after
choosing 11 — which is the same mechanism as C11, met again from the other side.

The Kotlin half moved out of `onTrackEnded` into `SubsongAdvance`, which has no Android imports and
six tests. This decision has now been wrong twice in a method that needs a phone to run; it is worth
one small object that does not.

### C14. ~~The app crashed when a search found a track that was also in a playlist~~ — FIXED 2026-09-04

Reported  with the sequence, which is the ordinary one and that is the point of it:
find a Modland tune by searching, play it, add it to a playlist, search for it again.

```
java.lang.IllegalArgumentException: Key "https://modland.com/pub/modules/SNDH/Dubmood/
Tempest_fjortisfacials.sndh" was already used. If you are using LazyColumn/Row please make
sure you provide a unique key for each item.
```

A hard crash on the main thread, from `dispatchDraw` — a stack trace with nothing in it about
search, because by the time it throws the only thing left is a list drawing itself.

**`runSearch` merges four sources and de-duplicated only the first two.** The scanned library and
the playlists were guarded against each other; the catalogue indexes and The Mod Archive were then
concatenated on the end. Once a catalogue track is in a playlist it comes back from **both** the
playlist scan and the catalogue search — and for a catalogue track the id *is* its URL, so the two
are identical. The list is a `LazyColumn` keyed by track id.

Fixed in `SearchResults`, which has no Android imports and takes all four sources at once, keeping
the first occurrence of each id. Order decides which copy wins and is chosen rather than inherited:
library, playlists, catalogues, live search — a tune you already have should present itself as
yours rather than as a download. Five tests, one of them walking all six pairs of sources, because
the old code guarded exactly one of the six.

**The same shape existed on the other side and is closed too.** Nothing stopped an imported M3U
naming a tune twice, and a playlist row is keyed by id as well. `LibraryStore.replaceTracks` is the
single funnel every playlist write goes through, so the guarantee is made there once rather than
remembered at five call sites. Worth noting what the old code actually did with a repeat: the
second insert conflicted on the track id and *replaced* the first, leaving a hole in `position` and
a playlist shorter than the caller believed. Quietly wrong rather than loudly wrong, which is worse.

### C3. R9 is addressed but unmeasured

The next track is read while the current one plays and remote fetches are cached, but nobody has
measured whether that turns the original five-to-thirty second wait into nothing **on a
own library**, which is the only measurement that counts. The five-to-thirty second wait was his
complaint about another player and is real; the SMB share it was once attributed to was not
(`docs/BACKLOG.md`, R9's correction).

### C5. ~~`%,1$d` in a plural would have crashed the online screen~~ — FIXED 2026-09-02

Never reported, because nobody had downloaded the song lengths on a device. The argument index must
come before the flag (`%1$,d`); the other way round is not a format specifier and `String.format`
throws. The count is always the `other` quantity at 61,157 entries, so the crash was certain rather
than conditional.

**Generalised the same day.** `./scripts/test-protracktor.sh` now fails on a malformed specifier
and on any string that exists in English and not in Polish, naming the line either way. Both checks
were verified by breaking the files on purpose and watching the script exit non-zero — a check
nobody has seen fail is a check nobody should trust.

### C6. ~~Re-entering Online lands inside the last folder, or on nothing~~ — FIXED 2026-09-03

Reported  2026-09-02: opening Browse and choosing **Online catalogues** should show the
catalogues. Instead it shows some folder, or an empty screen.

**Cause, read in the code.** `openDomain` clears `tracks` and `groups` but leaves `openCatalogue`,
`openFormat` and `openAuthor` exactly as the last visit left them:

```kotlin
it.copy(domain = domain, tracks = emptyList(), groups = emptyList(), arrivedByJump = false)
```

`OnlineDomain` branches on those three, so it renders the deepest one still set — with the list
underneath just emptied. Hence a folder view with nothing in it. Longstanding rather than new; what
changed is that `docs/BACKLOG.md` A20 has made people notice where Browse thinks it is.

**Fixed with A20**, as predicted, and the two rules are deliberate opposites that both hold:
descending and returning keeps your place (A20), leaving a domain and re-entering it does not (this).
The transition lives in `BrowseNavigation.enteringDomain`, pulled out of the controller so it could
be tested — removing the three lines that clear the hierarchy fails the test that catches it.

### C7. ~~"Add to the playlist" from a row menu closes Browse~~ — FIXED 2026-09-03

Reported  2026-09-02.

**Cause.** The row menu's single-track add and the bulk add button call the same callback, and that
callback closes Browse:

```kotlin
onAdd = { tracks -> viewModel.addToPlaylist(tracks); showBrowse = false }
```

Closing is right for the bulk button — you have finished choosing and want to see what you chose.
It is wrong for a menu item on one row, where the whole point is to keep browsing. One callback for
two intentions.

**Fixed as one thing, because it was one question.** The row menu and the bulk button now call
different things: finishing a selection means you are done here, adding one track from its menu
means you are not. And because Browse stays open, the row-menu add speaks — naming the track and the
playlist, or saying the track was already there rather than doing nothing visible. Fixing the
navigation alone would have replaced a jarring screen change with no feedback at all.

### C4. ~~Folder scanning trusts file extensions~~ — FIXED 2026-09-03

Not content probing, as `docs/ARCHITECTURE.md` §5 requires. A misnamed file is skipped by a scan; a
misleadingly named one is added and refuses only when played. Tracked as work in `docs/BACKLOG.md`
A6 as well, because it is both a defect and a piece of work.

## Known limitations — deliberate, not defects

- ~~**Background metadata resolution waits for playback to stop.**~~ **Lifted 2026-09-03.** It was
  a sc68 2.2.1 limitation: that release kept its 68000 emulator in global state. 3.0.0b is
  instance-based and was measured safe across four concurrent threads
  (`native/probe/sc68/probe_concurrency.c`), so a library scan now runs while music plays.
- **"More from this author" does nothing for local files**, and is absent rather than disabled for
  them. The local browser lists a granted tree flat, so there is no directory to jump to;
  directory-level local browsing is its own piece of work and nobody has asked for it.
- **A SID cannot be seeked**, only played from the start. libsidplayfp runs the machine; the only
  way to a position is to run it there, and nothing about the song lengths changes that. The
  position readout counts frames played and the scrubber shows progress without accepting a drag.
- **Subsong selection does not exist.** Every backend plays track 0. Some console files hold
  hundreds. It is a defect in effect, but the design is an open decision rather than a bug to fix —
  `docs/BACKLOG.md` A2.

## Fixed

- **2026-09-06 — Search silently threw away most of its results.** A search for `.sap` and
  got 506, then `.mod` and got 308, and said it felt like too few. It was: **200 + 300 + 6** and
  **300 + 8**. Two hard caps, 200 on the scanned library and 300 on the catalogues — different
  numbers for no reason anybody wrote down — applied with nothing on screen to say a limit had been
  reached. An archive of 6,335 SAP files answering "506" reads as a broken index, not a full page.

  One cap now, 2,000, in `SearchResults` beside the rest of the search policy, and the count of
  what was left out shown above the list. The cap is not a memory limit — a `TrackRef` is small and
  the list is drawn lazily — it is the point past which the answer to "where is my tune" is a better
  query. Browse by format has no cap and is the tool for "every SAP file".

  The lesson is the one this project keeps relearning: **a number chosen once and never explained
  becomes a bug the day somebody counts.** The 12-track bound on gme's audible search went the same
  way (C9).

- ~~**Play/Stop label goes stale when a module reaches its end.**~~ Fixed 2026-08-31 by the polling
  loop: the native side raises a flag when a module ends and the tick that drives the progress bar
  notices it.

- **2026-09-01 — `.sndh` played silence.** Not a missing format and not a wrapper bug: **sc68 wraps
  SNDH in a replay routine that lives on disk**, not inside the tune, and opens it by path. We
  shipped none, so every Atari ST file loaded, reported its title and author correctly, and then
  rendered nothing.

  Found by building sc68 for the *host* and calling its API on a real file, which is what turned a
  guess into a trace: `-> external replay 'sndh_ice'` followed by `failed '/Replay/sndh_ice.bin'`.
  No device involved, and the run took seconds.

  Two fixes. The replay binaries are packaged as assets and unpacked to a real path on first launch.
  And the sc68 gate no longer trusts `api68_verify_mem`, which returns **-1** for an ICE-packed SNDH
  that `api68_load_mem` then loads and plays perfectly — gating on it was rejecting these files
  before sc68 ever saw them. See `docs/LICENSES.md` for the question the replay binaries raise.

- **2026-08-31 — pressing next twice quickly played two tracks at once.** Each press launched its
  own open; the second overwrote the handle without closing the first, which kept playing with
  nobody holding it. Two things were wrong: the queue advanced *inside* the coroutine, so both
  presses read the same starting point, and there was nothing to cancel an open already in flight.
  The queue now advances synchronously and the in-flight open is cancelled, with anything it had
  already opened closed rather than started.

- **2026-08-31 — next and previous "behaved randomly", reported from a device.** Navigation walked
  the play history in *every* mode. That is right under shuffle — R5 asks for exactly it — and wrong
  without, where the list is on screen and "previous" has to mean the row above. History now governs
  only when shuffle is on. Whatever the bookkeeping, a control that disagrees with the visible list
  looks broken.
- **2026-08-31 — the gesture bar covered the transport row.** The dock's contents now sit above the
  navigation-bar inset while the surface still paints behind it; padding the surface instead would
  leave a strip of the wrong colour beneath the dock.

- **2026-08-31 — `NoSuchElementException: List is empty` in `PlayQueue.advanced()` on adding the
  first folder.** `order` was a constructor property defaulting to `tracks.indices`, and **`copy()`
  does not re-evaluate default arguments** — so a queue created empty and then given tracks by
  `copy(tracks = …)` kept the empty order, and the first `hasNext` indexed into nothing.

  Fixed by deriving `order` from `tracks` and a shuffle seed instead of storing it, which makes the
  two impossible to disagree. Patching the one call site would have left the trap in place for the
  next person, and `copy()` cannot be taken away from a data class. Two regression tests cover both
  routes in, and both were confirmed to fail against the original code.

- **2026-08-31 — `IllegalStateException: Track already closed` on pressing Play.**
  `DisposableEffect` was keyed on the loaded module. It runs `onDispose` whenever its key changes,
  not only when the composable leaves, and the lambda read the state at dispose time — by which
  point it already held the *new* value. So opening a module closed the module that had just been
  opened, and Play hit a freed handle. Keyed on `Unit` instead; replacing a module is the picker's
  job, and the effect only has to catch the screen going away.

  Worth keeping: the crash came from the code written to prevent a leak, and the guard in
  `Track.handle()` is what turned a use-after-free in native memory into a named exception with a
  line number.

## Branches

- `master` — repository base. Merging to it is the decision (`AGENTS.md` §3).
- `develop` — current work; everything below is merged into it.
- **2026-09-04, round 6** — all merged: `feature/add-feedback`, `docs/round-6-goal`,
  `feature/uade`, `fix/missing-extensions`, `docs/amp-and-reindex`, `feature/storage-you-can-free`,
  `fix/repeat-one-subsongs-c13`, `docs/play-store-readiness`, `fix/two-more-extensions`,
  `review/round-6`, `fix/review-round-6-engine`, `fix/review-round-6-storage`,
  `docs/round-6-reconcile`.
- 2026-09-04 — `feature/bigger-dock`, `feature/launcher-icon` (Codex's), merged before the round.
- **2026-09-03, round 5** — all merged: `feature/sc68-3-0-0b`, `feature/local-index`,
  `feature/cache-budget`, `feature/browse-scroll`, `fix/browse-defects`, `review/round-5`,
  `fix/review-round-5`, `fix/open-error-race`, `fix/database-singleton`,
  `fix/engine-shared-state`, `tooling/test-cache-honesty`, `docs/round-5-reconcile`.
- 2026-09-02 — `feature/asma-catalogue`, `feature/hvsc-songlengths` and the round-3 branches, merged.
- `feature/project-scaffold` — scaffolding and the Gradle skeleton. Not merged.

- `feature/play-store-readiness` — Codex's, **not merged** and left alone on instruction.

**Merged without any of it having been run on a device.** `AGENTS.md` says to wait for that test
before merging to `develop`; the exception is an unattended `/goal` run, which rounds 5 and 6 both
were. Both are therefore in `develop` unreviewed by anyone but their author.

## B48 follow-up — stable save action and shared Now Playing text, 2026-10-14

Owner trial feedback recorded on 2026-10-13 identified a disappearing heart during saving:
Next and seek-bar geometry shift, and a quick second tap can skip a track. The integrated search
field was reported as acceptable; B46 is not changed by this correction. No exact installed build
or device was separately supplied. The owner approved keeping the heart visible throughout saving,
one shared dock/notification subtitle rule, a bold title and a flat Protracktor P small icon.

Implemented on `feature/notification-playlist-heart`. Both command surfaces retain identity while
`PlaylistHeartSave` rejects overlapping writes. `NowPlayingText` is used by the dock and production
`PlaybackPresentation` factory; missing fields are omitted. The latter provides the native title,
subtitle, stable controls and white-on-transparent P without changing service lifecycle/decoding.
Action names are accessibility metadata, not new visible captions. Search, schema, dependencies,
toolchain and actual launcher/web/store assets are unchanged. [Contract](PLAN_NOTIFICATION_HEART.md).

**Checked:** 88 focused host checks in seven suites, zero failures/errors/skips. The pending-action
regression failed 2/16 before correction; removing the shared year failed 6/17; reintroducing action
removal/wrong small icon failed 6/24. Mutated sources were restored and SHA-256 compared. Targeted
generation is repeatable; a headless full-generation fixture matched all 26 current assets.
These are host/configuration checks, not SystemUI/touch/typography checks. Full combined gate/lint
and a replacement minified release APK follow on the approved stacked B46 branch. No develop/master
merge, push, store release or phone acceptance is implied.
