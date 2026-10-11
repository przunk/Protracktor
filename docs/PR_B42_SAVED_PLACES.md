# B42 — Saved places (local PR handoff)

Branch: `feature/saved-places`, originally stacked on B43 at `54a933a`, then rebased on B43+C100.
Status: owner phone acceptance and local merge into `develop`, 2026-10-11.
The owner accepted the combined B43/B42/C100/C101 trial overall; no itemised checklist result
is inferred. No GitHub PR, push or publication. Historical handoff notes follow;
see [current status](STATUS.md#owner-acceptance-and-merge--2026-10-11).

## Feedback → analysis → implementation

User feedback relayed by the owner requested a way to return to author folders later. The agreed
scope also covers game groups, rather than inventing a global artist identity. The owner selected
B42 for a combined trial with B43, whose branch remains unchanged while its approximately 24-hour
trial continues. Features remain separately reviewable; positive feedback on B43's layout is not
approval to merge either feature. [Plan and decisions](PLAN_SAVED_PLACES.md).

Schema 23 adds persistent exact route bookmarks independently of indexes and History. The Bookmark
action appears only in supported online author/game views. Saved places in Browse lists
them offline, opens current indexed contents, prevents duplicates and offers explicit removal.
Missing indexes/groups offer a route to Online catalogues, not an automatic download. Removed
catalogues remain named, explained and removable. Back returns to the saved list in one step.

No changes to B43 timing, History clearing semantics, Random draws, playlist ownership, source
switches, versionName or publication policy. The existing Browse cover remains in force.

## Bookmark presentation refinement — 2026-10-07

### Follow-up: removal Undo and equal controls (host verified, phone trial pending)

The owner requested Undo on the Saved places removal snackbar and reported unequal icons/controls.
The icon boxes already share their slim size; the button surfaces grew independently with their
labels. The added layout assertion failed before the fix: Open was 112 px, Bookmark 134 px.
The pair now shares the wider intrinsic width, retaining equal heights and translated labels.

Removal snapshots the persisted bookmark before deletion. Only removal initiated in Saved places
offers Undo, capturing that exact record in a one-shot action on its own message. It restores the
original timestamp/order; INSERT OR IGNORE preserves any independently re-saved bookmark.
Undo queues behind an in-flight bookmark write rather than disappearing while busy. Failures
remain visible, and unsuccessful removal offers no Undo. Dismissal/expiry does not restore data.
The shared snackbar dispatches the displayed message's action; existing playlist Undo keeps its
fallback. Dismissal is message-ID guarded so it cannot clear a newer notice. Schema 23, playback
and B43 behaviour remain unchanged.

Focused verification passed 22 tests: bookmark domain/store, real snackbar clicks, dismissal,
legacy playlist Undo and equal button geometry. Disabling restoration and the snackbar action
then broke four of 20 focused tests, as expected; both mutations were restored. The SQLite store
test also confirms restoration with the original timestamp returns the entry to its former order.

Full gate passed in **190 s: 731 tests**, zero failures/errors/skips, lint without errors,
581 page checks, 49 existing-WASM checks (not rebuilt), 8 SAP checks, web database upgrade,
137 icon paths, 8 server checks and store metadata validation. Command:
`scripts/test-protracktor.sh --no-daemon --max-workers=2 -Pkotlin.compiler.execution.strategy=in-process`.
The test task executed, rather than returning an up-to-date/cached result. Two native-render tests
produced eight previews; EN-light and PL-dark lists were inspected. Phone Undo, timeout/swipe,
visual layout and playback continuity remain unverified.

Follow-up APK: `dist/protracktor-release-1.0.0-1120-3c7ae8e-20261007-224320.apk`,
**1.0.0 (1120)**, clean source `3c7ae8e`, built in 118 s; 11,742,546 bytes.
SHA-256: `1c587ca3013d7c29e8105e127c2627db05427c291f91fafed052c0bff24d2383`.
Minified/non-debuggable release, minSdk 29/targetSdk 36, three ABIs. APK Signature Scheme v2
verification passed with the same test certificate as 1118 (SHA-256
`f97af50c2d869a2591dd82d24555894a2cc95927afb6c0a1fcd4bd197221e47e`).
Logs and previews: `dist/b42-undo-verification/`. The following handoff commit changes docs only.
No merge, push, tag, AAB or publication.

The owner requested a stable Bookmark label (PL: Zakładka) with an outlined/filled icon instead
of separate Save for later / Remove actions. The same control removes a saved entry from the list.
Each list row places the name above its source/path on the left, with Open and then Bookmark
at the right. Unavailable entries retain their explanation, disabled Open and explicit catalogue
recovery below. Bookmark state has a localised accessibility description; it is not colour-only.
This is a presentation-only refinement on the existing branch: schema 23, stored data, opening
logic and B43 are unchanged. Four of eight UI tests failed against the previous presentation.
Eight UI interaction tests now pass in a separate focused run; two render tests generated eight
EN/PL light/dark previews, with EN-light header/list and PL-dark list inspected. Screenshot mode
selects render tests only and is not evidence of passing interaction tests.

Three full attempts and a focused diagnostic run exposed host UI lifecycle/synchronisation
failures. Replacing content during teardown and then manual disposal did not resolve them.
The fixture now uses AndroidComposeTestRule to own content and activity lifetime, explicitly checks
the empty-list result after removing the last bookmark, and drains pending updates before teardown.
The focused run passed, but shared-environment synchronisation still failed in the next full run.
The interaction fixture now targets the supported minimum API 29, separate from the other UI
fixtures' default SDK; native-render previews retain their default SDK. No app behaviour was
changed to work around these test failures and no tests were excluded.

Final full gate passed in **211 s: 724 tests**, zero failures/errors/skips, lint without errors,
581 page checks, 49 existing-WASM checks (not rebuilt), 8 SAP checks, web database upgrade,
137 icon paths, 8 server checks and store metadata. Command:
`scripts/test-protracktor.sh --no-daemon --max-workers=2 -Pkotlin.compiler.execution.strategy=in-process`.
Changed test sources forced test execution; the test task was neither up-to-date nor from cache.
Phone behaviour remains unverified.

Updated APK: `dist/protracktor-release-1.0.0-1118-84fa8a8-20261007-222557.apk`, **1.0.0 (1118)**,
built from clean `84fa8a8` in 98 s with the same release command below; 11,740,154 bytes.
SHA-256: `5f9e373fbe4e282eee41ba9b0d99262fee49e37fb227905683f0a88ce3fd968f`.
Minified/non-debuggable, minSdk 29/targetSdk 36, arm64-v8a/armeabi-v7a/x86_64;
APK Signature Scheme v2 verified with the same test certificate as 1116.
Logs/previews: `dist/b42-bookmark-verification/`. The following handoff commit changes docs only.

## Verification

Focused tests have exercised real SQLite 22→23 migration, production store SQL, route identity,
duplicate handling, reopen after index rebuild/History deletion, exact group lookup, current
contents, cancelled late opens, Back, missing sources/groups/indexes and EN/PL Compose actions.
The migration starts from exported schema 22, not a reconstructed current schema. Schema 23 is
exported; schemas 1–22 remain unchanged. Two deliberate mutations (dropping format from bookmark
uniqueness and resolving a different format) failed the corresponding two tests in an 18-test
run; both were restored before the full gate. Two native-render tests generated eight EN/PL
light/dark previews. Inspected EN-light header/list and PL-dark list; short action labels avoid
clipping at the rendered width. These are host previews, not phone evidence.
The forced full gate passed in 234 s: **723 tests**, zero failures/errors/skips, lint without errors,
581 page checks, 49 existing-WASM checks (no rebuild), 8 SAP checks, web database upgrade,
135 icon paths, 8 server checks and store metadata validation. Command:
`scripts/test-protracktor.sh --really --no-daemon --max-workers=2
-Pkotlin.compiler.execution.strategy=in-process`, using the existing shared toolchain and isolated
Protracktor build/cache paths. Device playback, migration of the owner's actual data, navigation
and usefulness remain **not checked**.

The first full run exposed two fixture issues: the SDK-29 Robolectric process retained a
catalogue row across store tests, and the UI host stopped becoming idle after changing the
header's saved state. That failed run was stopped. The storage fixture now starts with empty
test tables; the UI fixture supplies an explicit viewport and flushes snapshot notifications,
following the existing host UI fixtures. These changes affect tests only, not app behaviour.

## Initial combined release candidate (before bookmark presentation refinement)

`dist/protracktor-release-1.0.0-1116-3f8df94-20261007-214351.apk` — **1.0.0 (1116)**,
built from clean `3f8df94` in 118 s using `scripts/build-release.sh --no-daemon --max-workers=2
-Pkotlin.compiler.execution.strategy=in-process`. Version name remains frozen at 1.0.0.
11,739,334 bytes; SHA-256 `d43b15266783814cf7202918687fc60f34faee9b78b7645b23971c587c23222a`.
Minified, non-debuggable, arm64-v8a/armeabi-v7a/x86_64. Signature v2 verified with test certificate
`f97af50c2d869a2591dd82d24555894a2cc95927afb6c0a1fcd4bd197221e47e`, matching the B43 candidate.
The artifact filename includes variant and source SHA. Logs/previews are in `dist/b42-verification/`.
The handoff documentation commit does not change app sources or this artifact.

## Owner trial (not yet run)

1. Install the combined release APK as an update without clearing data. Confirm History, B43
   marks, playlists and existing catalogue indexes remain intact.
2. Browse to a Modland/ASMA author or UnExoticA game group. Tap Bookmark: its outlined icon should
   fill without changing the label. Tap again to remove it. Playback should continue unchanged,
   and saving must not start a download. Check Zakładka in Polish as well.
3. Return to Browse → Saved places. Restart the app and reopen the bookmark at the same exact
   source/format/group. Back should return to Saved places, then the Browse root.
4. Save same-named folders in different formats/catalogues. They must remain separate. In the
   list, check the name/path on the left, Open beside it and the filled Bookmark at the right.
   Toggle that bookmark off: tracks, favourites, playlists and B43 marks must remain unaffected.
5. Optionally remove only a re-downloadable catalogue index. Its bookmark must remain with an
   explanation and Catalogues action. Reindex explicitly and return; the bookmark should
   open the current folder contents. Do not clear all app data for this test.
6. Check both themes, PL/EN, long group names, playing from a bookmarked folder, Back, and Random's
   return cover. Evaluate B42 and B43 independently; neither is assumed useful in advance.
7. Remove a bookmark in Saved places and tap Undo: it should return to its previous list position.
   Repeat and dismiss the snackbar (or let it expire): the entry should stay removed. Try two
   removals and confirm Undo restores the entry from the latest removal, not a playlist
   track. Check that Open and Bookmark have equal button sizes in both languages.

**Do not install the previous B43-only APK after this schema-23 candidate.** Older binaries have a
destructive database downgrade path. Ask for a forward-compatible build if either feature is
rejected. A signature mismatch also means stop and report it, not uninstall and erase data.
