# Release 1.5.0 preparation

Selected 2026-11-01. This is preparation, not an application release or confirmation of upload.
Keep versionName 1.4.0 until the release procedure selects 1.5.0. Preserve master/tags, prior
artifacts, database schema 23 and the separate normalization-slider experiment.

## Approved scope

1. Replace the disabled floating-controls constant with a saved default-Off Settings choice.
   Preserve hold-Play activation, explicit system permission, whole-panel dragging, edge docking,
   Hide target and file holds. Off hides immediately; no automatic panel after process restart.
2. Integrate the existing Android AMP trial with current development. Retain single-file,
   user-initiated cached playback, the existing local catalogue switch, no crawler/prefetch/bulk
   archive download, stable User-Agent and visible AMP attribution. Individual maintainer reply
   remains absent; the project selects an informational follow-up rather than continuing the
   previous approval hold. Do not describe this as permission granted by AMP. Its published FAQ
   allows attributed individual module links; the complete application catalogue is not
   explicitly described there. Source: https://amp.dascene.net/faq.php (indexed text checked
   2026-11-01; direct browser-tool request returned HTTP 403).

AMP's current switch is per installation. Withdrawal requires an application update and cannot
remotely disable older installed versions. No remote configuration service is selected.

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
`feature/amp-release-integration` (`8972f7cf`) without changing develop. AppTheme retains both
independent saved keys, controller initialization restores both choices, and Settings receives
both state/callback pairs. The source trial remains `feature/amp-catalogue` (`e728fa6`).

The floating-controls stage passes 21 focused tests, with a failing lazy-host negative control;
the AMP stage passes 64 focused tests. These are stage checks, not a complete combined gate.
Full tests/lint and the minified release APK are next; phone behaviour remains unchecked.
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
