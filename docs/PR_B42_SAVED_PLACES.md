# B42 — Saved places (local PR handoff)

Branch: `feature/saved-places`, explicitly stacked on `feature/played-before` at `54a933a`.
Status: implemented, host verified and release APK prepared; awaiting phone trial.
No GitHub PR, push, merge or publication.

## Feedback → analysis → implementation

User feedback relayed by the owner requested a way to return to author folders later. The agreed
scope also covers game groups, rather than inventing a global artist identity. The owner selected
B42 for a combined trial with B43, whose branch remains unchanged while its approximately 24-hour
trial continues. Features remain separately reviewable; positive feedback on B43's layout is not
approval to merge either feature. [Plan and decisions](PLAN_SAVED_PLACES.md).

Schema 23 adds persistent exact route bookmarks independently of indexes and History. The Save
for later action appears only in supported online author/game views. Saved places in Browse lists
them offline, opens current indexed contents, prevents duplicates and offers explicit removal.
Missing indexes/groups offer a route to Online catalogues, not an automatic download. Removed
catalogues remain named, explained and removable. Back returns to the saved list in one step.

No changes to B43 timing, History clearing semantics, Random draws, playlist ownership, source
switches, versionName or publication policy. The existing Browse cover remains in force.

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

## Combined release candidate

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
2. Browse to a Modland/ASMA author or UnExoticA game group. Save it for later; check the saved
   indication. Playback should continue unchanged, and saving must not start a download.
3. Return to Browse → Saved places. Restart the app and reopen the bookmark at the same exact
   source/format/group. Back should return to Saved places, then the Browse root.
4. Save same-named folders in different formats/catalogues. They must remain separate. Remove
   a bookmark: tracks, favourites, playlists and B43 marks must remain unaffected.
5. Optionally remove only a re-downloadable catalogue index. Its bookmark must remain with an
   explanation and Catalogues action. Reindex explicitly and return; the bookmark should
   open the current folder contents. Do not clear all app data for this test.
6. Check both themes, PL/EN, long group names, playing from a bookmarked folder, Back, and Random's
   return cover. Evaluate B42 and B43 independently; neither is assumed useful in advance.

**Do not install the previous B43-only APK after this schema-23 candidate.** Older binaries have a
destructive database downgrade path. Ask for a forward-compatible build if either feature is
rejected. A signature mismatch also means stop and report it, not uninstall and erase data.
