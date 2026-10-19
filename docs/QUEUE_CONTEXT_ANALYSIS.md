# Playback context and manual queue analysis

Updated: 2026-10-19. Owner and colleague feedback following the accepted playback context
experiment. The owner requested this complete analysis be saved before any Browse fix starts.

The merged mechanism supplies a playback source and cursor independent of navigation. The new
proposal adds a separate FIFO of manually requested tracks which temporarily interrupts that
source, then returns to its continuation. Preserve the working context rather than replace it
with an unrelated player or merge manual entries into a source playlist.

The recommended next task is the independent Browse navigation defect C110. Manual queue
implementation still needs the decisions below; a combined editable view is explicitly deferred.
This save authorizes documentation only, not a code change, build, feature branch or merge.

**Subsequent checkpoint:** the owner separately requested C110. Its isolated implementation is on
`fix/browse-root-navigation`, based on develop `a8539a2`; 963 host tests, lint without errors and
release APK verification passed, with phone acceptance pending.
[Implementation record](PR_C110_BROWSE_ROOT_NAVIGATION.md). The original analysis below
records the pre-fix state; manual queue decisions and the AMP hold are unchanged.

## Current implementation and handoff

The accepted experiment was merged into develop as `7cab210`. Baseline at the start of this
documentation task is `0d83b86`, a documentation closeout over that merge. The experiment branch
`feature/playback-queue-experiment` is integrated and must not receive the next iteration.

The APK supplied before this analysis was
`dist/protracktor-release-1.2.0-1228-7cab210-20261019-220200.apk`, version **1.2.0 (1228)**.
The owner subsequently reported that the app started, then supplied the feedback below. Launch
confirmation is not a separately completed device regression checklist; an installed build ID
was not supplied independently. Earlier owner acceptance of the intended context behaviour stands.

Historical host verification: clean source `069b238` passed 956 tests in 128 suites with no
failures, errors or skips; lint had zero errors and 72 warnings. The merge tree equals that source.
APK identity, test signature, minification and packaging were checked. These are not test results
for the proposed manual queue. Artifact hashes and full evidence remain in
[the original experiment report](QUEUE_EXPERIMENT.md).

Normal versioning is restored: every build uses the source commit count. VersionName stays
1.2.0 until the owner's release procedure; do not reintroduce the temporary code-1221 exception.
The older AMP APK cannot ordinarily replace a higher-code build. Do not uninstall or clear data
to force interchange. AMP remains on `feature/amp-catalogue` at `8e92002`, with permission pending;
its integration is deferred until the queue topic is completed in the next iteration. Master
remains `794d8f9`. No push, release tag or store publication is part of this work.

## Owner requests and design status

The owner distinguishes the existing playback context from a manual queue. The context may be a
playlist, Search, History, an online author, a local folder or Random. Navigating elsewhere must
not replace it. The requested manual queue lets a user add tracks from elsewhere without
immediately interrupting playback or adopting those tracks' source as the new context.

The proposed sequence is: finish the current track, play manually added entries in their insertion
order, then continue the original context. Additional entries join the end of that manual FIFO,
not ahead of earlier requests. Empty manual queue presentation must distinguish it from the
still available context.

The owner initially proposed separate Context and Queue tabs. A later combined view is explicitly
deferred; the owner suggested indentation or an icon to distinguish manual entries if that view
is developed. The owner also requested swipe removal from the manual queue. Swipe is related to
existing wishlist B25, not authorization for new gestures on every list.

Shuffle, repeat, transport history, changing context with pending entries and several editing
details remain design choices. The recommendations below are not confirmed requirements merely
because they are written down. The owner has not authorized implementing them in this save.

## Context and queue responsibilities

| State | Responsibility |
| --- | --- |
| Playback context | Source identity, source collection, cursor and continuation order, including existing shuffle and Random state |
| Manual queue | Ordered occurrences explicitly requested by the user and not yet started |
| Current playback | The occurrence actually loading or playing, whether supplied by the context or manually |
| Transport history | Navigable record needed for Previous and forward replay across context tracks and manual insertions |

Transport history is not the application's History screen. Enqueueing a track must not itself
record a listen, mark it played before or change playlist membership.

For example, with context `A -> B -> C -> D`, while B plays, enqueue X, Y and Z:

```text
Playback: B -> X -> Y -> Z -> C -> D
Context:  paused at its continuation after B during X, Y and Z
```

The return is to C, not a restart of B. The context cursor must not advance once for every manual
track. With shuffle, retain the existing permutation and cursor; do not activate the context by
calling its ordinary row-selection path again, which can deal a new shuffle. With Random, retain
the heard record, cursor and eligible prepared future rather than starting a new session.

The current context list and its manual additions must not be confused with a saved playlist.
Deleting a queued occurrence does not delete a file, a History entry or a source playlist row.

## Proposed Next and Previous behaviour

At the live end of playback, Next chooses the first manual pending entry. Only when none remains
does it request the next context entry. Natural completion follows that routing subject to repeat
and the existing subsong policy. A manual entry becomes current and leaves the pending list when
it starts; its transport record remains available for Previous.

Previous should navigate the actually played sequence across both kinds of occurrence. After
`B -> X -> Y -> C`, repeated Previous leads through Y, X and B. Without another explicit action,
Next should retrace that forward sequence instead of consuming pending entries again. Replaying
a historical manual occurrence is not a second dequeue and must not append a duplicate request.

### Adding after going backward

The recommendation is to honour a new manual request as an insertion before the existing forward
continuation, preserving that continuation behind it. Earlier pending manual requests retain
their FIFO priority. Do not truncate the old heard future as a side effect of enqueueing.

The exact precedence between a replayable manual occurrence, older pending requests and a new
insertion still needs a complete transition table. In particular, the promises that Previous
followed by Next is reversible and that a new manual request plays next are conditional on what
the user changed between the two commands. Do not implement competing unconditional priority
rules and assume that both promises hold.

C97 remains relevant: a Random filter change preserves its heard record and forward history;
only unplayed speculation is replaced. Manual insertion is a new explicit operation, not a
reason to revive the old history-truncation defect.

### Existing transport boundaries

The current finite unshuffled context uses the adjacent source row for Previous; shuffled contexts
use heard history. Random has its own heard boundary. Decide how the mixed transport record
falls back at its beginning without silently rewriting those existing behaviours.

System Back remains navigation only. It does not mean Previous track and must not stop playback.
Retain the existing distinction between subsong movement and next/previous file. The proposed
FIFO is initially discussed in terms of file references, not separately selected subsongs.
Whether to enqueue an individual subsong is a separate unresolved extension.

## Proposed shuffle and repeat rules

| Mode | Recommendation |
| --- | --- |
| Shuffle | Does not reorder manual requests. Preserve the context's own shuffle order for continuation. |
| Repeat Off | Drain manual entries, then continue the context. Stop when neither has another entry. |
| Repeat All | Repeat the context, not already consumed manual requests. Otherwise the manual block could prevent return to the source forever. |
| Repeat One | Repeat the actual current track, including a manual track. Explicit Next escapes it and advances normally. |

Under Repeat One, a manual queue may wait indefinitely while the current context track repeats.
That consequence should be visible rather than appear to be a failed enqueue. The owner suggested
respecting repeat but has not confirmed this per-mode interpretation or an override by manual requests.

The existing Play all subsongs setting remains a separate policy. Queue insertion must not
incidentally change a normal Next into a file skip or discard remaining subsongs. Confirm the
interaction before implementation, including repeat and completion of multi-subsong files.

## Context position and currently playing row

The owner raised a usability concern: a playlist's playing selection could suddenly disappear
when a queued local file starts, leaving the actual sequence visible only in the playback panel.

The recommendation is to distinguish the context position from an actually playing occurrence:

- While a context entry plays, use the normal playing indication.
- During a manual insertion, preserve a quieter context-position or continuation marker, without
  falsely indicating that the old source row is still sounding.
- Now Playing, notification, progress, metadata and heart actions refer to the actual manual track.
- Do not highlight a source row merely because its file ID equals that of a manual occurrence.
- A marker's meaning must be accessible without relying only on a colour change.

Enqueueing from local storage is a temporary insertion, not a switch to a local-folder context.
Ordinary explicit Play from that folder does change the context; the previous playlist then has
no active playback claim. These two actions must remain distinguishable.

The exact marker, label, treatment of selection and any explanatory text are not selected yet.
The implementation must not solve the visual concern by keeping a false playing animation.

## Playback panel and editing

Keep the proposed two tabs for the first design iteration: Context on the left, manual Queue
on the right. Context retains the current inspection/row-play/info behaviour without source
removal or reordering. The manual tab contains pending entries and exposes their editing actions.
Exact panel/button naming, initial tab and whether to remember the last selected tab are open.

A common current-track header is recommended. If the last manual entry is playing and no others
are pending, the queue can be empty while music continues. An empty-state explanation should
refer to remaining requests and the context that follows, not imply that playback has stopped.

Manual removal is requested. Reordering and an explicit Clear queue action were recommended but
have not been separately selected. Editing pending entries must not mutate the underlying context.
The behaviour of tapping a pending entry for immediate playback also remains open: decide what
happens to earlier pending entries rather than accidentally discarding them.

### Combined view deferred

The owner suggested indentation or an icon before manually inserted rows in a future unified
view. Preserve that idea without implementing the view in this iteration. Any future design must
make editing authority clear: manual pending entries may be editable, source continuation rows
are not. Colour alone is insufficient.

Random's speculative read-ahead is not a promised visible schedule. A combined view must not
expose disposable picks as guaranteed future tracks without a separate design decision.

### Swipe removal

The owner requested swipe removal from the manual queue. Recommendations for that interaction:

- Restrict it to pending manual entries, not source/context rows or the currently playing item.
- Remove one occurrence; keep the physical file and source playlist untouched.
- Offer snackbar Undo and an equivalent labelled/icon menu action for accessibility.
- Give each queued occurrence its own stable ID so removing one repeated file does not remove all.
- Keep vertical scrolling reliable. If reordering is later selected, define how its handle and
  swipe coexist with tap/long press instead of adding another ambiguous row gesture.

Swipe direction, activation threshold and the exact Undo window still need selection. Earlier
B25 concerns about gesture competition remain applicable; the renewed request does not select
swipe-to-add across Browse, Search or ordinary playlist rows.

## Other transition recommendations and open cases

These are recommendations or open decisions, not implementation authorization:

- Adding must not interrupt the current track or automatically resume paused playback.
- A queue without a context can play its manual entries and then stop; it should not require a
  fake playlist. Starting from idle and the resulting Play/Next availability need explicit rules.
- Ordinary Play in another source should, by recommendation, replace the context but preserve
  pending manual entries. Confirm that rule and what happens to an interrupted manual occurrence.
- Clearing pending entries should not stop the current track. Removal of a current item, if ever
  exposed, needs a separate skip/stop rule rather than sharing pending-row deletion.
- Allow duplicate requests as separate occurrences. A manually played file may also appear later
  in the context; do not silently skip it or introduce deduplication without owner selection.
- A failed download/open must not erase the context or falsely record a successful listen.
  Decide retry, skip, retention and user feedback; do not loop indefinitely or fail silently.
- Decide whether queue state survives process death. The accepted baseline restores the selected
  playlist paused and does not persist every temporary context. No new migration is selected.
- Decide History-origin behaviour for a track enqueued from History; its bookkeeping must not be
  inferred merely from the suspended context's origin.
- A new Random list or filter change must not accidentally clear unrelated manual requests.
  Pending-request survival on explicit reroll still needs confirmation.

## Architecture implications

The existing [ActiveQueue](../app/src/main/kotlin/com/przunk/protracktor/player/ActiveQueue.kt)
is the context model in this discussion. Its source variants and finite/Random rules remain useful;
do not rename every occurrence of queue or rewrite PlayQueue merely to change UI terminology.

[PlayerUiState](../app/src/main/kotlin/com/przunk/protracktor/player/PlayerUiState.kt) currently
derives `current` from `activePlayback.current`. A manual entry requires that actual playback be
independent of the suspended context's cursor. Otherwise metadata, progress, notification,
playlist highlights and the notification heart could act on the wrong track.

[PlaybackController](../app/src/main/kotlin/com/przunk/protracktor/player/PlaybackController.kt)
currently sends file Next/Previous/completion directly to ActiveQueue. One shared pure scheduling
model should arbitrate the context, pending manual occurrences and transport history for every
entry point: screen controls, headset, notification and natural completion. Do not add another
player or duplicate the rules in each adapter.

The design needs explicit occurrence identity and origin, immutable pending/history state,
continuation ownership and typed transition outcomes. The model must distinguish an enqueued
copy of a file from the same file occurring in the context or twice in the manual FIFO.

Read-ahead must prioritise the actual upcoming manual request and then the appropriate context
continuation. Preserve Random's hidden speculative boundary and the existing epoch/generation
guards: a delayed fetch or query must not steal playback after another explicit choice. Retries
must refer to the current occurrence/intent, not a stale source snapshot.

No new dependency, database migration, decoder, web implementation or new file-editing capability
has been selected. Persistence requirements, if later accepted, need separate schema planning.

## Browse navigation defect C110

Owner report, 2026-10-19, after the normal-code Queue handoff:

1. Play a track from History.
2. Back to the main Browse view.
3. Back again to the playlist.
4. Press Browse.
5. History reopens instead of the main Browse view.

Expected by the owner: Browse is the general entry to browsing, not an implicit return to the
source that happens to be playing. Music and its context must continue unchanged. Exact installed
build ID/device were not supplied independently; 1.2.0 (1228) is the last handed-over candidate.

### Cause established by code inspection

In [ProtracktorApp](../app/src/main/kotlin/com/przunk/protracktor/ui/ProtracktorApp.kt), `openBrowse`
calls `returnToSession()` before falling back to `openDomain(BrowseDomain.ROOT)`.
`playFromResults()` saves `sessionBrowse`; `returnToSession()` restores it while `searchMode`
is true. Despite its name, `searchMode` means any `QueueSource.Collection`, not only Search.

[SessionSource](../app/src/main/kotlin/com/przunk/protracktor/player/SessionSource.kt) maps History,
Search, online author folders and local folders to collection origins. Therefore the same restore
mechanism covers all those sources. It is a surviving A61 return-to-source convention, inconsistent
with the new independent-navigation expectation. This is code evidence, not an agent-run phone
reproduction or a completed regression test.

Random follows a different source/entry path, so this finding does not prove the same defect in
Random. Include it in the regression matrix because it previously had a distinct Back problem.

### Recommended correction boundary

The generic Browse action should open ROOT regardless of playback. Explicit author/source jumps
retain their own destinations, and Back moves one navigation level. Opening a context/queue panel
is an inspection action, not a reason to replace audio or automatically resume another source.

Keep the active context, its cursor, paused/playing state and current fetch intent intact. Do not
fix this by clearing the saved context or invoking a stop. Review all generic Browse entry points
in the shell and dock; do not indiscriminately remove snapshots needed for explicit navigation.

## Recommended work sequence

First, after a separate instruction to proceed, create `fix/browse-root-navigation` from the
current develop. Reproduce C110 with a regression test that exercises the actual Browse action,
then correct navigation without implementing a manual queue. Cover History, Search, local folders,
online author folders and Random, both toolbar/system Back, playing/paused states, explicit author
jumps and repeated entry. Assert that current track/context/cursor do not change.

Run the complete forced tests and lint, build a minified test-signed release APK in dist with the
normal commit-derived code, and provide device scenarios. Merge only after the owner's acceptance.
The present documentation task does not start this fix.

Next, settle the manual queue rules and create a separate experimental feature from an accepted
develop baseline. The suggested scope is FIFO insertion and return, mixed Next/Previous, agreed
repeat/shuffle rules, clear actual-playing/context markers, two tabs and pending removal/Undo.
Retain the requested swipe proposal; its exact interaction needs confirmation. Do not combine
the later unified view or AMP integration with this experiment.

## Verification required for a manual queue experiment

Pure state tests should cover the transition table before Android integration. New regressions
must fail against the missing/broken rule, not only call a helper beneath an untested gesture.
Host UI checks cover real row actions, Back dispatch, swipe/menu removal and Undo; those do not
replace the owner's phone tests. Important scenarios include:

| Scenario | Property to verify |
| --- | --- |
| B playing, X/Y/Z added | B completes, FIFO plays, context continues at C exactly once |
| Context shuffle enabled | Manual FIFO unchanged, source permutation preserved |
| Repeat Off at the last source row | Pending items still play; stop only when both continuations are exhausted |
| Repeat All and Repeat One | Selected scope and explicit Next escape are consistent across controls |
| Previous across manual/context boundary | Real played sequence and forward replay survive without double dequeue |
| New insertion after Previous | Agreed precedence applies, old forward record remains reachable |
| Random filter change during manual playback | C97 heard boundary survives and only eligible speculation changes |
| Same file enqueued twice or also in source | Stable occurrence identity, no accidental deduplication or cursor transfer |
| Removal, Clear and Undo | Only intended pending entries change, current track/source untouched |
| Pause, empty context and context switch | Agreed start/survival rules, no unexpected playback restart |
| Late fetch, load failure and retry | No stale takeover, silent loss or false listen record |
| Notification, heart, progress and highlights | All use actual playback; suspended context is not falsely shown as playing |
| Multi-subsong files | Existing transport layer and agreed completion boundary are preserved |

No manual queue code or tests exist as a result of this analysis. This documentation-only save
requires content, link and diff checks, not another application build or full test run.
