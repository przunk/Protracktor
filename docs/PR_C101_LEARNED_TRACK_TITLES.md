# C101 — Learned titles without changing list order

Branch: `fix/learned-track-titles`, stacked on C100 `0f70540`.
Status: host verified; release handoff and phone trial pending. No merge, push or publication.

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
137 icon paths, 8 server checks and store metadata validation. All 31 unit-test build tasks executed.
Command: `scripts/test-protracktor.sh --really --no-daemon --max-workers=2
-Pkotlin.compiler.execution.strategy=in-process`, with the same JDK and project-specific temporary
build/cache paths as C100. A combined C100+C101 release APK is pending. Phone behaviour is unverified.
B42/B43 remain experiments without acceptance or merge approval. Evidence: `dist/c101-verification/`.

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
