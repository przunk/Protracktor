# Manual block reordering and long-press Info

Owner decision, 2026-10-20. Continue on `feature/manual-playback-queue` in separate commits;
no merge is authorized. This extends [the unified-list contract](QUEUE_UNIFIED_LIST.md).
APK 1.2.0 (1245), source `613b154`, remains historical evidence until a new verified APK is built.

## Contract

- Tap a unified row to play. Long-press the row for Info, with a labelled accessibility action
  and an EN/PL hint. Remove the trailing Info button; no new selection mode or context menu.
- Only waiting manual occurrences have a trailing drag grip. Reorder within that contiguous
  indented block, never across context rows or ahead of the actual manual occurrence.
  Current manual and all source rows are not draggable. A single pending row needs no grip.
- Move by request ID, not filename or visible list index. Equal files stay separate and retain
  History origin. Reordering changes neither the actual track/position/playback intent nor
  source order, shuffle/repeat, generation, saved playlists, storage or History.
- Additions still append after older pending requests. Explicit reorder is the only override
  of their FIFO order; transport consumes the resulting order using existing one-shot rules.
- Keep swipe-left removal/Undo and swipe-right copy. A handle drag must not also play, show Info,
  scroll as an ordinary row gesture, enqueue or remove. Normal row scrolling stays available.
- Offer accessible move-up/down actions only when the corresponding pending neighbour exists.
  Stale IDs, a consumed/current request or a removed target cannot reorder anything else.
- Source replacement preserves pending order. Next during dragging may consume a request;
  stop dragging when its occurrence or layout generation ceases to be valid.

No arbitrary interleaving with source rows, source reorder, persistence, schema/dependency/native/
web/AMP change or dock count redesign. C111/C112 remain excluded. Phone behaviour is unchecked.

## Stages and verification

1. Pure ID-based pending move, controller/VM entry point and tests; commit independently.
2. Long-press Info, bounded drag grip, accessibility and real host-gesture/shell regression tests.
3. Review, force full tests/lint, build a clean minified test-signed APK into dist, verify artifact
   identity and record handoff. No merge before owner testing/acceptance.

Implemented in separate commits on the same branch; final artifact/evidence below. Host tests
do not certify device gestures, TalkBack or audio. No merge is authorized.

## Model checkpoint

Pure ID-to-ID movement changes only pending order. Controller/VM forwarding refreshes read-ahead
without playback/service activation. A no-op implementation failed the new ordering regression
(`/tmp/protracktor-reorder-domain-before.log`, 23 s); the implemented scheduler/controller run
passed **43 tests / 2 suites**, zero failures/errors/skips, in 37 s
(`/tmp/protracktor-reorder-domain-final.log`). UI/full gates/new APK remain pending.

## UI and boundary review

Info uses the row's labelled long-click action, with a persistent bilingual hint. Grips are
separate semantic groups, not swallowed into row playback semantics. They appear only when at
least two pending occurrences can be reordered; current/source rows stay fixed. A queue-specific
gesture coordinator uses stable request keys, local order between Flow frames, bounded offsets,
and edge scrolling after actual movement. Membership/generation changes retire the drag.

Host gestures found a real collision: a sideways grip movement could trigger parent swipe removal.
The shared swipe wrapper now latches its permission before child cancellation; Queue supplies the
live grip lock without changing row structure. Other lists retain the default behaviour/API.
The failing reproduction is `/tmp/protracktor-reorder-gesture-second.log`, 30 s. Grip semantic
grouping was also corrected after the restored handle was merged into parent row semantics.
An initial test compilation error used the wrong custom-action key; corrected to the existing
SemanticsActions API. No phone verification is implied by those host iterations.

Verification probes (production code restored afterwards):

- Disconnecting only physical drag movement failed its real-shell test, while accessible moves
  stayed wired: `/tmp/protracktor-reorder-drag-mutation.log`, 28 s.
- Disconnecting edge scroll failed the long-block test, which compares the final request position
  with its position before scrolling: `/tmp/protracktor-reorder-scroll-mutation.log`, 25 s.
- Disconnecting long-press Info failed the physical-hold test:
  `/tmp/protracktor-reorder-info-mutation.log`, 26 s.

Restored combined run: **79 tests / 7 suites**, zero failures/errors/skips, **57 s**
(`/tmp/protracktor-reorder-focused-final.log`). Includes both accessible directions, duplicates,
clamping over multiple rows, held/immediate sideways grip motion, pending changes during drag,
edge scrolling, tap/hold/swipe retention, actual Undo restoration and C110 navigation.
At that checkpoint full gates/lint and a clean release APK were pending; final evidence follows.
All phone behaviour is unchecked.

## Final verification and APK

Model commit `ce87d4a`, UI/review source **`0082064`**, versionName **1.2.0**, commit-count
versionCode **1248**. The clean source passed `./scripts/test-protracktor.sh --really` in **507 s**:

- **1037 tests / 135 suites**, zero failures/errors/skips; test task actually executed.
- Store metadata, runner/release-flow fixtures, web page/engine/store upgrade, SAP, native audio,
  icons, PWA and local server checks all passed.
- Lint **0 errors / 72 warnings**. Issue ID/message/file records equal the archived code-1245
  report; no new warning is hidden by the unchanged total.

Minified release APK, built in **123.8 s** with the unchanged public test certificate:
[protracktor-release-1.2.0-1248-0082064-20261020-185705.apk](../dist/protracktor-release-1.2.0-1248-0082064-20261020-185705.apk),
**11,887,299 bytes**.
SHA-256: `f3bee93a044085672cb6da0718e16d837a281eb1a8daef5d9c5734fa911ddb72`.

Checked package `com.przunk.protracktor`, version/code, APK v2 signature with certificate SHA-256
`f97af50c2d869a2591dd82d24555894a2cc95927afb6c0a1fcd4bd197221e47e`, absent debuggable flag,
executed R8/nonempty mapping, minSdk **29**/targetSdk **36** and actual engine libraries for
**arm64-v8a / armeabi-v7a / x86_64**. No AAB, publication-key or store operation.

Retained evidence under ignored `dist/manual-reorder-verification/0082064/`: JUnit/lint reports,
full gate/build/companion logs, R8 mapping, manifest/badging, public signature, APK contents and
focused/negative probes. [Phone scenarios](TESTING.md#manual-block-reorder-and-long-press-info--phone-checks-pending).
Final documentation check: 168 local destinations exist; application/native/web/script sources
still equal the APK source after the documentation-only closeout.

**Checked partially:** domain/controller/real-host gesture behaviour, full host gates/lint and
release artifact properties. **Unchecked:** real device gesture feel/layout, TalkBack, audio,
service/notification/Bluetooth and usability. Phone acceptance and merge remain pending.
Develop `d6d3297`, AMP `8e92002` and master `794d8f9` are unchanged. No push/tag/release change,
schema update or versionName bump. A later documentation-only commit does not relabel this APK.
