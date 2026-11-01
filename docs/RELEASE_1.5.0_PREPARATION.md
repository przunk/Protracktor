# Release 1.5.0 preparation

Selected 2026-11-01. This is preparation, not an application release or confirmation of upload.
Keep versionName 1.4.0 until the release procedure selects 1.5.0. Preserve master/tags, prior
artifacts, database schema 23 and the separate normalization-slider experiment.

## Develop integration — approved and merged, 2026-11-02

Integration of the combined candidate is approved and completed as **`3519f05baad6c67556572c3e103219f812de4d7b`**.
The merge tree equals `test/1.5.0-candidate` **`3e15b122452c7ee5d1a7df1a3878fe571ba94581`** exactly; all 845 checked inputs
match application source `2794a1db`. AMP stays `Amp.ENABLED = true` without a Settings switch;
Floating controls keeps its saved default-Off Settings opt-in. The original AMP trial is retained.

The supplied minified test APK **1.4.0 (1434)** keeps its source, signature and SHA-256. Previous
host results remain evidence for that source: 1323 tests / 181 suites, 85 gate stages, 1396 page
assertions, 57 engine checks and fresh lint 0 errors / 81 warnings. No new app test or APK is
claimed for this identical-tree integration. The merge approval supplies no itemized device,
installed-build or checklist outcomes; those remain separately unreported.

Master/tag v1.4.0 stay at `5b6936e`; no 1.5.0 version commit/tag, store AAB, upload, push or WEB
deployment is made. Release notes are prepared. The corrected AMP follow-up remains drafted and
not confirmed sent; no reply or individual maintainer approval is claimed. The next release step
is the interactive 1.5.0 procedure from develop, with signing/upload/push performed separately.
Earlier dated pending-integration statements describe their original handoffs and are superseded
by this record; they do not assert further physical verification.

## Approved scope

1. Replace the disabled floating-controls constant with a saved default-Off Settings choice.
   Preserve hold-Play activation, explicit system permission, whole-panel dragging, edge docking,
   Hide target and file holds. Off hides immediately; no automatic panel after process restart.
2. Integrate the existing Android AMP trial with current development. Retain single-file,
   user-initiated cached playback, the build-time `Amp.ENABLED` gate, no crawler/prefetch/bulk
   archive download, stable User-Agent and visible AMP attribution. Individual maintainer reply
   remains absent; the project selects an informational follow-up rather than continuing the
   previous approval hold. Do not describe this as permission granted by AMP. Its published FAQ
   allows attributed individual module links; the complete application catalogue is not
   explicitly described there. Source: https://amp.dascene.net/faq.php (indexed text checked
   2026-11-01; direct browser-tool request returned HTTP 403).

AMP availability is fixed by **`Amp.ENABLED = true`**. There is no AMP user preference; the
older trial key is ignored. Withdrawal means setting the constant false and shipping an update.
A disabled build preserves user data and blocks AMP discovery/transfers, even after an internal
re-enable attempt. Older installed versions are unaffected; no remote service is selected.

## Implementation and handoff

Keep `feature/floating-controls-settings` and a new AMP integration branch independently
reviewable, each based on current develop. Preserve `feature/amp-catalogue`; merge its trial into
the AMP integration branch without rewriting that history. Combine the feature branches only in
a test candidate to supply one minified, test-signed APK. Inspect permission/lifecycle races,
retained AMP data and queue/cache ownership; run complete tests/lint before the handoff.

Prepare an English follow-up email, with credits and exact access behaviour, before publication.
Correspondence remains local and is not claimed sent until sending is confirmed. The current
workspace has no email-sending tool; the prepared text is for manual sending.

After phone acceptance, merge the approved features, finalize EN/PL Play notes and full release
notes. The interactive `scripts/release.sh 1.5.0 --check` / `scripts/release.sh 1.5.0`, AAB upload
and `scripts/push-protracktor.sh master develop v1.5.0` remain the release terminal workflow.
No release, tag, upload, push or WEB deployment is performed by this preparation record.

## Combined candidate — 2026-11-01

`test/1.5.0-candidate` combines `feature/floating-controls-settings` (`17a7161`) and
`feature/amp-release-integration` (`8972f7cf`) without changing develop. AppTheme retains the
Floating controls saved key and Settings receives its state/callback pair. AMP availability is now compiled; its old
preference and state/controller plumbing are removed by the clarification below. The source trial remains `feature/amp-catalogue` (`e728fa6`).

The floating-controls stage passes 21 focused tests, with a failing lazy-host negative control;
the AMP stage passes 64 focused tests. These are stage checks, not a complete combined gate.
Earlier combined verification is retained below; the build-gate correction requires a new
complete gate and replacement APK. Phone behaviour remains unchecked.
Draft Play notes and [full 1.5.0 notes](RELEASE_NOTES_1.5.0.md) prepare the release description,
without creating a release/tag or asserting publication.

### Shared download fixture correction

The first combined full gate stopped at 392 cases: 391 passed and one assertion failed.
The shared WEB download fixture expected AMP to be unavailable on Android too; Android now
correctly offers its index. The fixture has explicit Android/WEB expectations and dedicated AMP
and AMP-with-ASMA cases, so neither platform's actual output is filtered before assertion.
A negative control changing the WEB AMP expectation to a downloadable step fails the production
WEB check. The fixture is already declared as a Gradle test input. Full verification is repeated
with the corrected scope; no application download behaviour was changed for this correction.

## AMP build-gate correction — selected 2026-11-01

Keep AMP availability as **`Amp.ENABLED = true`**; no AMP Settings entry is wanted. Remove its
saved preference reader/writer and controller/VM/UI state. Old values remain stored but ignored.
The internal access guard must clamp initial and re-enabled work to the build flag, including
retained playlist/History/cache references. No schema or data reset is involved.

The real Settings absence regression failed against the preceding UI. Test a false-constant
build and a deliberately broken re-enable guard, restore true, then run the complete gate/lint
and build a replacement APK. Complete host verification and replacement packaging are recorded in [Replacement APK after the AMP build-gate correction — 2026-11-02](#replacement-apk-after-the-amp-build-gate-correction--2026-11-02);
phone behaviour remains unchecked.
The 1432 artifact below is historical and still contains the rejected AMP Settings entry.

## Verified candidate and APK — 2026-11-01

**Historical first candidate, superseded by the build-gate correction above.**

Agent-run host verification and artifact inspection are complete for clean source
**`8a2ca43eb8917fa9ce0379b4db610e5dcd1df861`**, `test/1.5.0-candidate`. This is ready for a phone trial, not accepted for
develop integration or released. Later documentation commits do not relabel this APK.

| Check | Result |
| --- | --- |
| Complete host gate | **1,319 tests / 180 suites**, zero failures/errors/skips; all **85** gate stages pass; **1487 s** overall |
| Executed Android unit task | **561.8 s**; compilation reused, all tests executed rather than served from cache |
| Actual-ESM WEB page | **1,396 assertions** |
| Real WASM engine | **57 checks**; other PCM/worklet, native audio, packaging, storage, icon, PWA and server stages pass |
| Fresh lint | **0 errors / 82 warnings**, **132.3 s** |
| Minified test-signed release APK | [APK 1.4.0 (1432)](../dist/protracktor-release-1.4.0-1432-8a2ca43e-20261101-232154.apk); **12,093,731 bytes**, built in **175 s** |
| APK inspection | Non-debuggable, embedded source/version match, ZIP CRCs, three native ABIs and 16 KB ZIP alignment verified; AMP/overlay host retained by R8 and AMP credit packaged |
| Source consistency | **844 tracked inputs** match across verification and packaging |
| Phone behaviour | **Unchecked** for this combined candidate; historical trials retain their original scope |

APK SHA-256: `eef30d78943696a50029d55406930df1c43565cd862c5d9b10e807d7b04b7a99`.

The current test name remains **1.4.0 (1432)**; 1.5.0 is selected only by the release
procedure after acceptance. Schema stays 23. Develop `ed1bc88`, master/tag v1.4.0 `5b6936e` and
the original AMP branch `e728fa6` are preserved. No AAB, release tag, upload, push or WEB deployment
was made. Host logs, JUnit/lint XML, failure/negative-control evidence, frozen input hashes and
artifact inspection are retained locally in ignored `user/release-1.5/`.

### Phone handoff and publication

Use [the current checklists](TESTING.md): enable Floating controls while music is already playing,
then hold Play/Pause and check the permission flow, transport/file holds, dragging/Hide and Off.
Restart to confirm saved availability without an automatic panel. Check AMP browse/search,
selected cached playback, manual queue and absence of an AMP availability setting. A false
constant is checked on the host; it cannot be selected from this APK's UI. Verify EN/PL and both themes.

The informational email is prepared in ignored
`docs/letters/2026-11-01-amp-integration-follow-up-draft.md` and remains **not sent**. Its stable
User-Agent and selected-file/companion, cache, attribution and no-prefetch claims match the code.
There is no connected mail-sending tool; manual sending is required before publication.

After phone acceptance, merge the combined candidate so the shared fixture correction and release
notes accompany both features. Then run the interactive release procedure from develop, upload the
AAB and push the selected refs as described above. No individual AMP maintainer approval is claimed.

### Disabled-build verification — 2026-11-01

The real `Amp.ENABLED = false` build passes **12 checks / eight suites**, zero failures,
errors or skips, in **43 s**. The same code paths verify initial/re-enabled access, catalogue and
index choices, download sizes, module/index transfer admission, saved reads, SQLite/user-reference
retention and the Settings section without an availability control. Removing the re-enable clamp
fails the build-gate assertions. The initial UI absence test also failed on the previous AMP row.
The host UI fixture uses a bounded catalogue-section viewport because the unrelated About footer
requires Android JNI. The target availability section is rendered and inspected.

Restored `Amp.ENABLED = true` for the full gate and replacement APK. Old `amp_enabled=false` data
is seeded before real controller startup in the admission tests, covering an upgrade from an
opted-out trial. The full gate and replacement artifact now pass; [current results](#replacement-apk-after-the-amp-build-gate-correction--2026-11-02). Earlier
artifact/source identities are retained; no develop merge or release is performed here.

## Replacement APK after the AMP build-gate correction — 2026-11-02

Agent-run verification is complete for clean source **`2794a1db22e8b100eba9d839ce6f89e536c09dff`** on
`test/1.5.0-candidate`, with **`Amp.ENABLED = true`**. AMP has no Settings control, stored
availability or UI/VM/controller toggle. Old trial preferences are ignored. Floating controls
keeps its independent saved default-Off switch. The earlier 1432 APK remains historical.

| Check | Result |
| --- | --- |
| Full executed Android suite | **1,323 tests / 181 suites**, zero failures/errors/skips, **547.1 s** |
| Complete gate | All **85** stages exit 0; **1415 s** overall |
| WEB and engine | **1,396 actual-ESM page assertions**, **57 engine checks**, all other companion stages pass |
| Fresh lint | **0 errors / 81 warnings**, **118.7 s** |
| Disabled build | Actual false constant: **12 tests / eight suites**, zero failures/errors/skips; broken re-enable clamp fails |
| Minified test-signed release | [APK 1.4.0 (1434)](../dist/protracktor-release-1.4.0-1434-2794a1db-20261102-001444.apk); **12,087,871 bytes**, built in **168 s** |
| Artifact inspection | Non-debuggable; version/source/test signature, ZIP CRCs, three ABIs and 16 KB ZIP alignment verified; AMP/overlay host retained by R8 |
| AMP control removal | No settings_amp resources or amp_enabled preference key in DEX; packaged credit and privacy exactly match their sources |
| Source consistency | All **845 tracked inputs** match verification/packaging |
| Phone behaviour | **Unchecked** for this replacement APK; use the current [checklists](TESTING.md) |

SHA-256: `1fbfb5c569e0ff10f3cae92219a0b59aecd9dc4162da8e274fa6a44f5a8108e2`.

This test build keeps **1.4.0 (1434)**, schema 23. The release procedure selects 1.5.0 after
phone acceptance. Develop/master/tags and the original AMP branch are unchanged. No store AAB,
release, push or WEB deployment was made. The AMP follow-up is corrected and **not sent**; there
is no connected mail tool. Detailed receipts, logs, JUnit/lint XML, source hashes and inspection
are retained locally in ignored `user/release-1.5/amp-build-gate/`.

Test the absence of an AMP switch, normal AMP playback/cache/queue and the independent floating
controls opt-in. After acceptance, merge this combined candidate, then use the interactive
release procedure and publication workflow above. A false build requires a new application
update; it cannot remotely alter installed copies or erase their saved data.
