# B42 — Save for later / Saved places

Status: implemented and host verified on `feature/saved-places`; combined phone trial pending, 2026-10-07.
Recorded: 2026-10-07. Original wishlist entry: 2026-10-05.

## User feedback and purpose

User-requested improvement, relayed by the owner, who approved the saved-place concept and
requested explicit user-feedback attribution on 2026-10-07. The original request concerned
favourite authors or author folders worth returning to. Save a location rather than a global
artist identity: an UnExoticA game soundtrack is useful even when it is not an author folder.

This is the existing B42 in [WISHLIST.md](WISHLIST.md), not two separate favourite-author and
listen-later features. No individual reporter, closed-test membership or completed implementation
is inferred. The owner selected B42 after the B43 candidate, for a combined APK trial.

## Agreed user flow

1. Open an author/game folder in an online catalogue and toggle **Bookmark**.
2. Open **Saved places** from Browse, separately from favourite tracks and playlists.
3. Each entry shows its name, source catalogue and enough path context to distinguish namesakes.
4. Open the bookmark to browse that location's current contents, not a frozen copy of its tracks.
5. Re-saving the same location creates no duplicate. A saved place can be removed explicitly.

Saving does not start playback, download tracks, create a playlist, mark tracks as favourites or
subscribe to author updates. Merely opening the saved list needs no network. Clearing playback
History does not delete bookmarks, and bookmarks do not depend on B43.

## Scope and identity

First scope: online catalogue author/game groups already represented by Browse. Use the existing
`catalogueId`, format/group level and exact author/group key, not just a display name. This avoids
merging different authors with the same name or folders across formats/catalogues. Human-readable
labels are presentation data and may be refreshed; bookmark identity is immutable.

`BrowseState` currently holds `openCatalogue`, `openFormat` and `openAuthor`; `CatalogueStore`
supplies those groups. Analyse their existing navigation methods and reuse them, rather than
serialising a whole Browse snapshot or creating a second route resolver inside a composable.

Do not add a save action to a source that has no supported stable group route (for example an
arbitrary live search result). Local SAF folders, cross-catalogue artist unification, arbitrary
search bookmarks, tags, custom collections, sorting options and web support are outside this
first task. A need for them is a separate decision, not a silent expansion.

## Persistence and unavailable places

- Store typed saved locations in the existing local database, with a uniqueness constraint over
  the complete route key. Use a small repository and domain model, not controller-owned SQL.
- Persist enough display information to show a bookmark when its source index is absent.
  Do not cascade-delete saved places when an index is removed or rebuilt.
- Reindexing keeps bookmarks and resolves them against the new contents. If a group has vanished
  or changed identity, keep the bookmark and explain that the location is unavailable; do not
  silently open a similar name or delete the user's saved entry.
- If an index is missing, explain the requirement and offer a labelled route to prepare it.
  Do not silently download it merely because the bookmark was saved/opened. Existing catalogue
  prefetch preferences still apply once its ordinary Browse view opens; bookmarking adds no new
  download behaviour.
- A disabled/removed catalogue remains visibly unavailable with an explanation and a remove
  action. Offline browsing works to the extent the ordinary local index supports it.
- Reuse normal Back behaviour. Saving/removing/opening must not unexpectedly replace playback,
  reset the Random session, bypass the playlist return cover, or move the user's playlist.

## Presentation

Use localised PL/EN labels and the project's icon-plus-label controls. The folder action clearly
distinguishes saved from unsaved state, and the Saved places list supports opening and removal.
Show useful feedback on save, duplicate save and removal; no silent no-op. Provide an explanatory
empty state, accessible labels and usable layouts for long names in both themes.

Recommended initial ordering: most recently saved first; re-saving does not reorder the entry.
Do not implement additional ordering modes in this task. Exact button placement and translated
copy are presentation decisions to validate on the release APK, not settled pixel specifications.

## Work stages and acceptance evidence

1. **Analysis:** confirm stable route keys for every supported catalogue/group and map reopening to
   existing navigation. Inspect missing-index/disabled-catalogue paths and the return cover.
   Schema **23 is reserved for B42** on `feature/saved-places`, above B43's schema 22.
2. **Storage:** implement bookmark identity, duplicate handling, persistence, listing and removal.
   Write real SQLite migration tests preserving History, playlists, favourites and indexes, and
   export the schema. Test duplicate saves, same names in different sources/formats, restart,
   index deletion/rebuild and source/group disappearance. No destructive migration/downgrade.
3. **Navigation and UI:** add Save for later and Saved places, including the empty and unavailable
   states. Test opening the correct current group, returning Back, removal, translated labels,
   accessibility and the existing navigation cover. Assert no playback/download is triggered by
   saving or listing. Check existing Browse prefetch separately from bookmark actions.
4. **Regression proof:** deliberately break route-key uniqueness or reopening and verify the
   corresponding tests fail. Run the full project suite and lint; inspect both themes and long
   labels using the existing host UI checks. Do not present those as phone testing.
5. **Handoff:** build a minified test-signed release APK into `dist/`. Prepare a local PR description
   linking the feedback, analysis, implementation and actual checks. Give the owner the checklist
   below, record the result and wait for explicit merge approval.

Phone scenarios: save an author folder and an UnExoticA game; re-save without a duplicate; restart
and reopen each at the right location; distinguish matching names in different catalogues/formats;
remove a bookmark without changing favourites/playlists; keep a bookmark when its index is
removed and get a useful recovery action; verify Back and uninterrupted playback/Random.
Index-removal scenarios must use only re-downloadable data, never clearing all app data.

## Workflow and completion

The owner explicitly approved stacking `feature/saved-places` on B43 at `54a933a`, rather than
develop, on 2026-10-07. B43 remains unmerged and frozen for the owner's approximately 24-hour
trial. B42 has separate commits and no dependency on listened-status logic; the combined APK
contains both for independent evaluation. Planned merges, only after explicit approval, are
B43 into develop followed by B42. If B43 is rejected, preserve the installed database with a
forward-compatible build; never send an older-schema APK as a rollback.

Keep feedback → analysis → implementation → PR/handoff → user test traceability. Each verified
stage gets its own commit. Update this plan, WISHLIST and STATUS, including what remains untested.
Push/publication belong to the owner; merge needs explicit approval. No claim of user validation
or closed-test participation without an actual report.

Relative effort/risk: lower than B43, with most risk in stable bookmark identity, index lifecycle
and navigation rather than playback timing. No phone result is claimed before the trial.

## Implementation record

Schema 23 adds `saved_places`, keyed by the exact catalogue/format/author tuple, without a foreign
key to downloadable indexes. `SavedPlacesStore` performs local I/O; `SavedPlaces` owns independent
read/write/open state. Catalogue availability and current tracks are read through `CatalogueBrowser`
and the ordinary index APIs. Cancelled opens cannot take over Browse after Back. A completed open
publishes the ordinary Online author view, with a one-step return to Saved places.

Browse has a Saved places entry; supported author/game views have a **Bookmark** toggle.
Unavailable entries retain their label and removal action, explain the cause, and offer Online
catalogues for missing indexes/groups. Save/list/remove have no playback or download dependency.
See [the local PR/handoff](PR_B42_SAVED_PLACES.md) for verification and the combined APK trial.

Presentation refinement requested by the owner on 2026-10-07: replace Save for later / Remove
with one stable Bookmark label (PL: Zakładka), using an outlined or filled bookmark for state.
In Saved places, put the name above its path on the left; Open and the filled Bookmark sit
beside it on the right, with Bookmark rightmost. Keep unavailable-location explanations and
explicit catalogue recovery. Announce bookmark state to accessibility services, not only visually.
Storage, navigation and B43 rules remain unchanged; phone acceptance is still required.
