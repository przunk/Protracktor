# C108 — preserve databases from newer schemas

## Report and scope — 2026-10-15

Code finding during B49, not independent user feedback. The owner selected protection after
accepting and merging B49. `fix/preserve-newer-database` starts from develop `f41a5fa`.

`ProtracktorDatabase.onDowngrade` enumerates and drops all user tables, then recreates the older
schema. This loses playlists, History, bookmarks and unknown future data; the previous C18 fix
made recreation complete rather than preserving data. A higher app versionCode does not imply
a higher database schema. No current phone loss or affected-user count is claimed.

## Intended correction

- Refuse an unsupported schema downgrade before any destructive SQL. Preserve version, user
  records and unknown future tables/indexes/triggers. Do not increment schema 23 or add a reset.
- Check compatibility read-only before constructing playback or its ViewModel. The activity
  performs the check on IO and shows loading, then a PL/EN explanation with labelled Retry/Close
  if blocked. A retry must not clear data. External-open intents remain pending behind this gate.
- Protect service-only entry too: inspect only the version, without migrating on its main thread;
  a blocked service must not construct the player or handle transport. Give notification feedback
  linking to the explanation, then stop. A stale scan Stop must not instantiate a new player.
- Fresh/current/older compatible databases retain their normal create/upgrade paths and migration
  progress UI. Never recreate or rewrite a newer database during preflight, including on an error.

## Verification plan

First reproduce against the actual helper and a synthetic newer file using native SQLite on the
host. Verify repeated refusal, retained version/data/future objects and a subsequent compatible
helper reopening the same file. Add production preflight, genuine UI/retry and service-entry
checks; exercise fresh/current/upgrade paths. Make controlled negative changes and confirm tests
fail, then restore. Run the complete forced test/lint gate and build a minified test-key release
APK. Preserve prior artifacts. No merge without owner acceptance; no push/Play/tag/master change.

## Verification state

Before correction, the final isolated real-helper run fails **both tests**, zero errors/skips,
94 s: the old helper does not refuse a newer schema and changes its version, so a subsequently
compatible helper tries an unexpected migration instead of reopening preserved data. Evidence:
ignored `dist/c108-before-isolated.log` and `dist/c108-before-results/`.

The first attempt failed compiling an inferred mixed array type (73 s, no tests). Correcting it
produced one failure out of two checks (152 s); its apparently passing preservation check was not
valid because Robolectric changed the application directory while the process singleton retained
the old context. The fixture now closes/resets that singleton per synthetic process and verifies
the same file. Do not count the stale-context result as data-preservation evidence.

Implementation/final verification are in progress; no passing final gate or phone result is claimed.
Never simulate this case by modifying the owner's real database or installing an old destructive
binary over user data; use only an isolated test fixture.

The probe uses [Android's explicit read-only open API](https://developer.android.com/reference/android/database/sqlite/SQLiteDatabase#openDatabase(java.lang.String,android.database.sqlite.SQLiteDatabase.CursorFactory,int,android.database.DatabaseErrorHandler))
with a non-destructive corruption handler, because the
[platform default handler deletes corrupted database files](https://android.googlesource.com/platform/frameworks/base/+/master/core/java/android/database/DefaultDatabaseErrorHandler.java).
This protects the probe itself; it is not a redesign of normal helper corruption recovery.

## Focused correction checkpoint

**51 checks in four suites passed**, zero failures/errors/skips, 125 s: seven real-file/helper,
seven actual Activity/gate/service/scan-action, three preparation and 34 existing schema checks.
This includes retained playlist membership, History/listened/counts, bookmarks and future tables/
index/trigger, unchanged newer-file bytes after preflight, a committed live WAL, missing/current
files, ordinary schema-22 upgrade, corrupt-probe file preservation, EN/PL UI, loading/content
gating, real Retry/Close, external-open refusal and cold service/scan notification actions.

The first integration attempt did not compile due to an invalid test import (45 s). Its corrected
run failed two of 51 checks (136 s): a result-returning PRAGMA needed rawQuery, and the Retry test
needed to wait for rendered output instead of an unsynchronized plain counter. Both test paths
were corrected; no production change was needed between that run and the 51-pass gate.
Evidence: ignored `dist/c108-focused*` / `dist/c108-first-focused-results/`.
Controlled negative checks and the full restored gate/APK follow; no phone acceptance is claimed.

## Controlled negative checks

Temporarily making `onDowngrade` a no-op and disconnecting the real Retry callback failed all
three selected checks in 163 s, zero errors/skips: helper refusal, compatible reopen and actual
Retry action. No-op downgrade changed the file's version without needing destructive SQL, which
the tests also reject. Restored both production files and checked their SHA-256 sums against the
pre-mutation copies. Evidence: ignored `dist/c108-mutation*` / `dist/c108-restored.sha256`.
The restored full gate follows; trial patch version 1.4.1, schema 23, no versionCode policy change.

## First full-gate checkpoint

Forced run on `9e61064` executed 910 tests in 121 suites: one failure, zero errors/skips,
298 s for Gradle. `NumbersAreGroupedTest` rejects the ungrouped integer placeholders in the new
EN/PL compatibility message. All other 909 tests passed, including the restored C108 checks.
The script stopped before lint and auxiliary checks; those were not run in this attempt.
Both placeholders now use the existing grouped-number convention. Evidence: ignored
`dist/c108-full-first-*`. A complete fresh gate is required after the resource correction.

## Restored full gate

Forced run on corrected source `a7bd098` passed in **463 s**: **910 tests in 121 suites**,
zero failures/errors/skips, with the unit task actually executed. Lint XML: zero errors and
72 warnings, unchanged from the accepted base. PL/EN resources/store metadata, 581 page checks,
49 WASM engine checks, eight SAP checks, browser database upgrade, 30 native audio checks,
140 icon paths, PWA and 11 loopback server checks passed. Existing JDK and temporary per-class
isolation (one worker, 1 GiB heap), no exclusions or repository build-setting changes.
Restoration SHA-256 checks still pass. Evidence: ignored `dist/c108-full-gate*`.
Release APK and phone acceptance remain pending; no merge/push/Play step.

## Release APK handoff

Clean source `2fe0311`, **1.4.1 (1190)**, built in 151 s:
`dist/protracktor-release-1.4.1-1190-2fe0311-20261015-213009.apk`, 11,823,499 bytes.
SHA-256: `5322d6bf815a130305b51eefc9122df572faeb90027310a2b32fdbc1713213f3`.
R8 executed; independent checks confirm non-debuggable manifest, minSdk 29/targetSdk 36,
arm64-v8a/armeabi-v7a/x86_64, all five new compatibility resources and APK v2 signature with
unchanged test certificate SHA-256 `f97af50c2d869a2591dd82d24555894a2cc95927afb6c0a1fcd4bd197221e47e`.
This is a minified test-key APK, not a Play upload artifact.

App/native/scripts/web/build sources match full-gate `a7bd098`; subsequent checkpoints are
documentation-only. Checked 95 local documentation file links with no missing targets, not a full
anchor/external-link audit. Existing APKs remain intact, including the unchanged 1.4.0 SHA-256.
Evidence: ignored `dist/c108-release*` / `dist/c108-apk-*`. Phone startup/playback and refusal UI/
timing remain **not checked**; no owner acceptance, merge, push, tag/master/store change or AAB.
Use the [normal-use phone checklist](TESTING.md); never manipulate the owner's database to stage
the failure. A future-schema phone fixture would require separate, isolated test data.

## Owner approval and integration — 2026-10-15

The owner authorized integration after the 1.4.1 trial handoff. Merge `8f3ef56` on develop has
the same tracked tree as approved branch `83468f4`; app/native/scripts/web/build sources match
the offered clean APK source `2fe0311` and full-gate source `a7bd098`. Checked by Git tree/source
comparison, not another test/lint run. No rebuild was needed for unchanged application sources.
The retained full gate is 910 tests/121 suites, zero failures/errors/skips, lint zero errors and
72 warnings. Device/Android, exact installed build and individual scenario results were not
separately supplied; this records integration approval, not a claim of agent-run phone checks or
of a newer-schema refusal being exercised on a phone. Earlier pending checkpoints are historical.
Master/tags/schema/store remain unchanged; no push, publication or AAB build. Older installed
destructive binaries remain unsafe for schema rollback; this merge changes only the new code.
