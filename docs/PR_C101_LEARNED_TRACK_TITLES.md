# C101 — Learned titles without changing list order

Branch: `fix/learned-track-titles`, stacked on `feature/saved-places` (`cb79b1b`).
On 2026-10-11 the owner requested that C100 belong to B43 itself, while C101 remain separate.
The reordered stack is B43+C100 (`3cde2ec`) → B42 (`cb79b1b`) → C101 (`4b26bdb`).
The former C100 branch is retired; original tips remain under `refs/archive/20261011/`.
Status: host verified; release APK ready, phone trial pending. No merge, push or publication.

## Feedback → analysis → agreed design

User report relayed by the owner on 2026-10-11: History shows decoded titles, but Random and
Browse/author folders still show filenames. The owner approved learned titles during playback,
the original filename retained in the source context, and no change in list order. He also
approved reusing History: names persist through restart and file-cache eviction, but Clear
History removes the learned overlay until a later play records it again. See [Q12](OPEN_QUESTIONS.md#q12--learned-titles-in-random-and-browse).

`adoptTitleFrom` already records decoded titles in History while Random and Browse retain their
source references. Updating `catalogue_tracks.title` would alter SQL sorting on later visits;
updating Random's internal record risks changing navigation semantics. Neither is necessary.

The shared, batched History presentation read returns listened status and nonblank learned titles
for current Browse/Random IDs, including unqualified plays shorter than five seconds. Recording a
play invalidates the read after the write; clear/navigation guards reject late results. Rendering
projects a title onto a copy of the source reference, keeping its ID, original filename, author,
size and position. Browse source text includes the filename when renamed; Random's secondary line
retains it too. Catalogue queries, search matching, sorting, Random record, prefetch and selection
indices are unchanged. No schema change (still 23), eager decoding, network lookup or new dependency.

## Verification and handoff

Two actual Compose view regressions failed before UI wiring (Random and Browse did not show the
available learned title), then passed in a 21-test focused run including the production SQLite
store, batched read, write invalidation and pure label projection. Before the full gate, source
path augmentation was confined to the rendered text: modifying `TrackRef.subtitle` would also
change its fallback author. The domain test now asserts both subtitle and inferred author are
preserved. The source filename remains visible without changing the reference's source metadata.

The forced full gate passed in **309 s: 749 tests**, zero failures/errors/skips; lint without errors;
581 page checks, 49 existing-WASM checks (not rebuilt), 8 SAP checks, web database upgrade,
137 icon paths, 8 server checks and store metadata validation. The unit-test invocation executed
all 31 Gradle tasks; its test result was not cached.
Command: `scripts/test-protracktor.sh --really --no-daemon --max-workers=2
-Pkotlin.compiler.execution.strategy=in-process`, with the same JDK and project-specific temporary
build/cache paths as C100. Phone behaviour is unverified.
B42/B43 remain experiments without acceptance or merge approval. Evidence: `dist/c101-verification/`.

After reordering, Git comparison confirms all tracked files outside `docs/` are byte-identical
to `cbb6f3e`, the tested APK source. No new tests or APK build were run for the history-only
reorganisation. The existing artifact below retains its original source SHA and remains valid
for the combined trial. The separate B43+C100 branch was not independently rebuilt/tested.

### Combined release artifact — 2026-10-11

- APK: [`protracktor-release-1.0.0-1123-cbb6f3e-20261011-174025.apk`](../dist/protracktor-release-1.0.0-1123-cbb6f3e-20261011-174025.apk).
- Version **1.0.0 (1123)**, schema 23 unchanged, clean source `cbb6f3e`; includes C100 and the
  existing B42/B43 trials. Built in 195 s; 11,745,002 bytes.
- SHA-256: `fd138fe8185f710985de8b0a325352f69db945ad0a37ad2f21d9be890c473cb2`.
- Minified/non-debuggable release; minSdk 29/targetSdk 36; arm64-v8a, armeabi-v7a and x86_64.
- APK Signature Scheme v2 verified; test certificate SHA-256
  `f97af50c2d869a2591dd82d24555894a2cc95927afb6c0a1fcd4bd197221e47e`, matching 1120.
- Command: `scripts/build-release.sh --no-daemon --max-workers=2
  -Pkotlin.compiler.execution.strategy=in-process`, existing JDK, temporary project Gradle cache,
  existing project build output at `/home/andrzej/.protracktor/build` to reuse native outputs.
  Release log is retained with the test evidence. The subsequent handoff commit changes docs only.

Install as an update without clearing data. No merge, push, tag, AAB or publication was performed.

## Phone checklist

1. In Random, play a file with a distinct embedded title. Its existing row should acquire that
   title without moving the cursor or changing Next/Previous. The secondary line keeps the filename.
2. Open Browse/author for that file: the title should match History, the source line should retain
   the filename, and the catalogue order should remain the same even after leaving and returning.
3. Try a file with no embedded title: keep the source name. Metadata learning must not wait for
   C100 qualification or download/open every file in a folder.
4. Restart and revisit the folder; learned titles should remain. Removing downloaded audio should
   not erase names. Clear History should remove the overlay, including on the currently visible list.
5. Tap/long-press renamed rows, add/share/open details, and check identity, selection and source
   filename. Verify long titles/source paths in EN/PL and both themes. Playlist presentation should
   not gain Random's extra filename label.
6. Re-run C100's short-track, seek and Clear History cases using the combined APK.
