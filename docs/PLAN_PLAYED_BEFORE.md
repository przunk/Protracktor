# B43 — Played before

Status: selected as an experiment; implementation authorised, pending the remaining rule decisions.
Recorded: 2026-10-07. Original wishlist entry: 2026-10-05.

## User feedback and purpose

User-requested improvement, relayed and refined by the owner. On 2026-10-07 the owner explicitly
asked that this and B42 be recorded as user feedback being addressed. No individual reporter,
closed-test membership or completed implementation/test is inferred from that attribution.

Listeners want to recognise music they have already tried, including tracks they rejected after
a short listen. This is an informational status, not a rating, favourite or instruction to skip.
Keep the existing B43 identity in [WISHLIST.md](WISHLIST.md); do not create a duplicate feature.

## Experimental status — owner decision, 2026-10-07

The owner selected B43 for implementation as a trial of a user request, not a commitment to keep
the feature permanently. A technically correct implementation may still prove unhelpful and be
rejected. B42 remains unselected and is not part of this work.

Evaluate two distinct outcomes:

- **Technical correctness:** qualification, clearing, migration and rendering meet the agreed
  contract without regressions. Passing tests does not establish that the feature is useful.
- **Usefulness:** during ordinary listening, the owner/users can recognise previously explored
  tracks without distracting row decoration, confusing the marker with favourites/cache status,
  or needing more effort than the indication saves. The phone report determines this outcome;
  no positive verdict or testing duration is assumed in advance.

After the release-APK trial, record the actual feedback and the owner's decision: retain, revise,
or abandon as not useful. An abandoned experiment is a valid outcome, not a reason to invent
favourable feedback or quietly expand the feature. Record the feedback-to-decision trail even if
the code never merges. No telemetry or collection of listening data outside the device is added.

Keep the implementation isolated on its task branch and avoid coupling other features to it.
If it is abandoned before merge, leave it unmerged. A phone that has already received a newer
database schema must not be sent an older APK as a casual rollback: prefer a forward-compatible
build disabling/removing the experimental behaviour while preserving History, playlists and all
other user data. Do not lower the schema version or clear data to end the experiment. Any later
schema cleanup is a separate, tested migration. Merge and publication still require owner approval.

## Confirmed requirements

- Five seconds of actual playback is enough to mark a track as listened to. No 30-second threshold.
- The indicator must be genuinely discreet. A small non-interactive icon or another subtle
  treatment is possible; do not add a prominent badge or extra button to every row.
- Do not skip, suppress, rank down or filter previously played tracks in Random. Its selection,
  heard queue, speculative preparation and navigation remain unchanged.
- Existing persistent History entries seed the status on the first run of the new version.
- Clearing History also clears the listened status; it must not survive in a separate hidden store.
- The requested marking concerns tracks outside the playlist; the exact interpretation below
  still needs confirmation before implementing its visibility rule.

## What the current code establishes

- `data/HistoryStore.kt` stores one `play_history` row per `track_id`, with last-play time/count,
  metadata and no listened-duration field. History is not limited to its first visible page.
- `PlaybackController.recordPlayed` runs after opening the decoder and reading metadata, without
  waiting five seconds. It is also called when `opened.start()` returns false. Consequently, row
  presence alone cannot enforce the new five-second rule for future plays.
- `player/HistoryRecording.kt` deliberately avoids updating/reordering History while playing
  from History itself. Preserve that behaviour.
- `HistoryStore.clear` deletes the History rows. `RandomHistory` is a separate navigation queue,
  not the persistent evidence source for this feature.
- The current schema is 21. Do not reserve the next number in this planning change.

Existing rows have no evidence of their listening duration. Accept them as legacy listened
entries because the owner requested seeding from History; do not claim that they met five seconds.

## Recommended implementation shape

Keep History as the single persistent source. Add a qualification field to its existing row,
rather than an independently maintained duplicate list. A transactional one-time migration marks
existing rows as qualified; new rows start unqualified. Preserve qualification when the current
History upsert refreshes metadata, time and count. Do not silently change when entries appear in
History merely to implement the marker.

Use a small framework-independent playback qualification state machine, with an injected
monotonic clock and a playback-generation token. Feed it playback start/progress, pause/resume,
stop/failure, track changes and History-clear events. Count elapsed active playback, not download
time, time paused, elapsed time since selecting a row, or the seek position. Background/screen-off
playback counts. Prefetch and decoder opening alone never qualify a track.

Mark a row once when the threshold is reached, without changing its last-play time/count or
reordering an open History screen. Queries should resolve status for visible track IDs in batches
off the main thread, with invalidation after qualification and clearing; no query per composed row,
whole-history reload every playback tick, or network fetch to decide the marker.

Serialize History recording, qualification and clearing, or use an equivalent generation guard:
a write queued before Clear must not restore a cleared status afterwards. Clear also invalidates
in-memory indicators and pending bootstrap/read results. Never seed again merely because the
qualified set is empty. Keep playlists, favourites, bookmarks and the Random queue intact.

## Decisions to settle before implementation

1. **Outside the playlist:** recommended meaning is hidden in playlist views, visible in Browse,
   search and Random regardless of playlist membership. Alternative: hide it for any track saved
   in any playlist. The owner has not yet chosen between these meanings.
2. **Short tracks:** recommended strict rule is five seconds; a track ending earlier remains
   unmarked. Alternative: a natural end before five seconds qualifies it. Do not assume that the
   earlier assistant proposal for short tracks was accepted.
3. **Accumulation and identity:** recommend cumulative active time within one playback occurrence
   (pause/resume keeps it; moving to a different track resets it), a file-level mark using existing
   track identity, no per-subsong marks and no cross-source hash deduplication. Confirm these defaults.
4. **Clear during playback:** recommend cancelling the current occurrence's eligibility until a
   new playback begins, so the clear action is not immediately undone by the still-playing track.
5. **Visual treatment:** recommend a small neutral history/check glyph in the existing row metadata
   area, with a localised accessibility description and an explanation in track details. It is a
   status, not an icon-only action. Do not dim the whole row or rely solely on colour. Approve its
   appearance on a release APK in both themes; the final visual design is not settled here.

## Work stages and acceptance evidence

1. **Analysis and agreed contract:** resolve the choices above, inspect every playback entry point
   and identity mapping, and reserve a schema version against current develop before changing it.
   Record expectations for clear/late-write ordering and old History imports.
2. **Storage and qualification:** implement the pure state machine and History qualification.
   Test 4.999/5 seconds, pause/resume, seek, stop/failure, rapid switching, repeated playback,
   background progression, History-origin playback and clear while a write is pending. Verify
   the tests fail when the threshold/generation protections are deliberately broken.
3. **Migration:** test against real SQLite with existing rows, empty History and repeated database
   opens. Preserve timestamps/counts/metadata and unrelated user data. A qualified row must stay
   qualified after a metadata upsert. Export schemas; never use destructive migration/downgrade.
4. **Presentation:** add batched status reads and discreet PL/EN presentation only in the agreed
   views. Test updates without reopening, clearing while the view is open, accessibility, long
   titles and both themes. Prove Random selection/navigation and playlist behaviour are unchanged.
5. **Handoff:** full project tests and lint, minified test-signed release APK in `dist/`, local PR
   description with the feedback, mechanism, implementation and evidence. The owner checks the
   release on a phone; record the actual result before requesting merge approval.

Phone scenarios: an old History item is marked after upgrade; a fresh item skipped before five
seconds is not; five seconds qualifies it; pausing/seeking does not fake listening; Clear removes
marks without deleting playlists; restart does not resurrect cleared legacy marks; Random still
draws previously heard tracks normally. Short-track and playlist scenarios follow the decisions above.

## Boundaries and workflow

Android only in this task; web parity, listened-only filters, automatic Random exclusions, manual
ratings and favourites changes are not included. The owner has selected this task as an experiment;
settle the remaining explicit rule choices before coding. Use its own `feature/played-before` branch from
current develop, independently of B42. Keep a feedback → analysis → implementation → PR/handoff →
user test record, with separate commits for verified stages. Push/publication remain the owner's
actions; merge requires explicit approval. Update this plan, WISHLIST and STATUS truthfully.

Relative effort/risk: higher than B42 because playback timing, existing History semantics,
migration and clearing races all meet in this change.
