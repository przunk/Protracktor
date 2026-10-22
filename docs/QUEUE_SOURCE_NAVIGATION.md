# Return from Queue to its source

Trial selected 2026-10-22. This is navigation to the active playback context's originating view,
not a Random-only shortcut and not another way to start playback. Implementation branch:
`feature/queue-source-navigation`, from develop after the B52 planning record.

## Interaction and layout

- Make the existing source name in Queue's header actionable, with a small navigation arrow.
  Do not add a caption, another header row or another right-hand button. Preserve the header's
  existing padding, two-line source limit and Clear/Help placement; long names still ellipsize.
  The inline icon/name action is the selected exception to the usual stacked LabelledAction.
  At enlarged text (above 1.25x), cap action widths to reserve about 80 dp for the source after
  the 32 dp outer insets. The previous Polish header could leave zero source width. This
  adaptive fallback gives the longer Clear caption a slightly larger width share, tightens
  internal horizontal padding, wraps full captions and lets their shared row grow vertically;
  it must not ellipsize these action captions. This is an accessibility-size exception to fixed
  height, not another source caption or separate header row. Normal-size height and placement
  must match the previous header. Other LabelledAction callers keep their existing sizing.
- Opening a source closes Queue and shows its destination. Keep playback, position, shuffle,
  repeat, waiting manual requests and source identity unchanged. Never reroll Random or
  automatically select/play a row as a side effect of navigation.
- Source means the underlying context, including while a manual request is playing. A manual-only
  queue after Clear, or an externally opened file without a source view, retains a plain label
  rather than a misleading jump action. Missing/stale destinations produce feedback.
- The visible name labels the action; expose a localized, explicit Go to source description and
  button role for accessibility. No snackbar substitutes for the accessibility description.

## Destinations

| Active source | Destination | Back behaviour |
| --- | --- | --- |
| Playlist | Its actual playlist ID, even if another playlist is selected | Normal playlist navigation; do not reopen Queue |
| Random | The remembered session/list and current filter | Browse root, then playlist; no leftover unrelated Browse route |
| Search | Captured query, scope and results | Existing scope/parent navigation, then Browse root |
| History | Captured filtered page/results | Browse root, then playlist; no History play recording |
| Online folder | Captured catalogue, format and author/group | Normal author/format/catalogue hierarchy |
| Local folder | Captured granted root and selected subfolder | Normal local hierarchy |

Returning to a different playlist must preserve the existing unsaved-edits protection. Cancel
keeps Queue open. Save must succeed before switching; Discard explicitly permits abandoning
the selected draft. A deleted playlist is not recreated and must not become an empty substitute.
Returning to a playlist must not rebuild the already-playing context from its stored contents.
The source playlist's editor and active order are live-linked by PlayerUiState. If its captured
membership/order differs from disk, reveal the captured order as an unsaved draft, without an
automatic write. A draft that was saved/discarded before leaving can still differ from the
running context; silently loading disk here would change what Next plays. Metadata-only label
differences do not mark this restored view dirty. Normal playlist-chooser behaviour is unchanged.

Collection navigation needs the captured route, not the display string: a local folder name or
search label does not uniquely identify the destination. Restore only route/result fields while
retaining live download/storage/index/cache state. Retire old asynchronous Browse ownership and
unrelated return routes so later completions/Back cannot pull the screen somewhere else.

## Verification and limits

Exercise actual Queue-header taps through the application shell/VM/controller, not just a test
callback. Cover all source shapes, paused/playing state, manual requests, missing destinations,
unsaved playlist choices and Back. Compare header bounds and Clear/Help positions against the
previous layout at normal/narrow widths and enlarged text; inspect generated before/after images.
Run the forced full test/lint gate and build a minified test-signed release APK into `dist/`.

Host evidence and images do not establish phone appearance or behaviour. This trial stays
unmerged pending device acceptance. B52 loudness normalization is the next task, not part of this
implementation. Cross-restart restoration of playback contexts and automatic re-indexing are
outside this trial.

Initial evidence: the actual header-tap regression failed before the feature existed. A first
playlist-return draft failed the context-preservation test because keeping only the explicit
playback field did not protect it from the live editor getter. Enlarged Polish text exposed a
zero-width source slot. Deliberately restoring a whole captured Browse state failed the live
resource regression; current-state preservation was restored. A delayed successful Save after
the context changed initially posted a false unavailable-source error; its controlled regression
failed before retiring that navigation completion without altering successful persistence.
Late playlist reads are also guarded against replaced sources and release their write protection
when abandoned.

## Host verification checkpoint

The latest public focused set passed **91 tests / 7 suites**, zero failures/errors/skips, covering
actual shell taps for all source shapes, dirty-draft choices and failure, delayed Save ownership,
domain restoration, playlist persistence, existing Browse/Back regressions, complete captions
and shared action-pill sizing. The final run completed in **67.4 s**.

Four separate diagnostic render cases passed and all eight before/after images were inspected by the
agent: ordinary Random, narrow/long online source, and enlarged EN/PL. The previous visible
header was copied from **d0d5b8d** solely as an ignored test reference; the new render invokes
production QueueHeader. Both are rendered inside the application's real dark-theme Surface.
Normal/narrow headers remain **64 dp**, with identical Clear/Help bounds. The first enlarged
candidate retained **83 dp** but ellipsized action captions. The first forced full gate rejected
that candidate after **1050 tests**, one failure in the existing Polish 200%-font caption guard;
lint and companion checks were not reached. Full captions are now wrapped rather than shortened;
the guard still checks every character, every line and height overflow. A subsequent render exposed
an ugly mid-word Polish wrap; a word-boundary assertion and slightly unequal action width shares
protect that case. The corrected enlarged EN/PL headers are **100 dp** rather than the reference's
83 dp, retaining complete action words and the source slot. Normal/narrow geometry is unchanged.
The final four renders passed in **24.4 s** and the corrected images were inspected again.
Images are host component renders, not
full application or phone screenshots. Evidence is retained in ignored `user/queue-source-verification/`.

The full forced gate/lint and a new minified APK remain pending. Android behaviour, real touch
feel and TalkBack are unchecked; no integration approval is inferred from these host results.
