# Search within the open playlist (B55)

Decision, 2026-11-01: implement a compact search mode on a task branch, with Android and WEB
using the same interaction. This is local filtering of the open editor, including unsaved edits;
it does not change catalogue search, saved queries or B56.

## Selected behaviour

- The playlist chooser shares its existing row with a subtle, labelled Search action on the right.
  Opening search replaces that row with a full-width field, animated in both directions. It adds
  no row and preserves header height. The other action buttons and the dock retain their layout.
- Entering search focuses the field and opens the keyboard. Editing dims the surrounding UI,
  including the list; results remain tappable. A result starts playback, dismisses the keyboard
  and removes dimming while retaining the phrase and filtered list. Refocusing dims again.
- Dimmed header/dock controls dismiss editing on the first press instead of activating underneath.
  The field has a clear-query action and a separate close-search action. Closing restores the
  full playlist and its previous scroll position. Back/Escape first ends editing, then closes
  search. Keyboard Done/Enter ends editing and retains the filter.
- Match every query word across title, author, filename and source path, ignoring case and accents,
  using the existing search folding rule. Keep original order and playlist row numbers. No file
  opening, network request or persistent store is required.
- A row tap addresses its original index in the full playlist. Shuffle, repeat, Next, Previous and
  the manual queue continue using that full context, never just the visible subset.
- Row removal and bulk actions address the visible occurrences in the underlying editor. Selection
  cannot silently include hidden results. Reordering is available with an empty filter; clear the
  query before arranging the full playlist. Search does not save or discard edits.
- Leaving the playlist closes search; switching playlists starts with an unfiltered view. The
  original scroll position is retained while filtering, independently of result scrolling.
- No matches has an explicit empty-results message and offers clearing through the field. It
  must not claim the underlying playlist is empty or offer index downloads.

## Implementation and evidence

In progress on `feature/playlist-search`, based on clean develop `d22afc5`.
The matching projection is pure Kotlin/JavaScript; the player remains the owner of playback.
UI, full regression checks, release APK and browser visual inspection are pending. Device
interaction and animations are **unchecked** until the APK is tested on a phone.
