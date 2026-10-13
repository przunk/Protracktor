# B48 — notification playlist heart

Selected 2026-10-13 from real app-user feedback relayed by the owner. Branch
`feature/notification-playlist-heart`, based on develop `debb00d`. B46 will use a second branch
starting here, explicitly approved by the owner so a single APK can test both. No merge before
owner acceptance.

## Selected behaviour

- Outlined heart adds the displayed current file to the selected playlist. Filled heart means
  membership; pressing it never removes a track or adds a duplicate.
- Immediately save the playlist's entire working contents, including prior unsaved edits. This
  exception is deliberate: notification use may happen in the background/on a locked screen.
  Normal Browse additions and the ordinary Save/Revert contract stay unchanged.
- Use existing `sameFileAs` identity, not title or subsong identity. Random/Browse playback does
  not change the destination: the selected playlist owns the saved tracks.
- Bind requests to the displayed track and playlist. Reject stale, loading/unrestored or missing
  targets with feedback. A pending save does not restart playback or accept overlapping presses.
- Preserve the C103 snapshot, serialization and invalidation guards. Confirm success only after
  persistence; failures retain the added working track as an unsaved edit for retry.
- Provide outlined/filled icons and PL/EN labels in both the legacy notification actions and the
  session custom action. Android 13+ derives media controls from PlaybackState; see
  [platform media-control behaviour](https://developer.android.com/about/versions/13/behavior-changes-13#media-controls)
  and [custom action builder](https://developer.android.com/reference/android/media/session/PlaybackState.CustomAction.Builder).
  Actual lock-screen placement remains a phone check, not guaranteed by the host.
- No new favourites collection, migration, dependency, WWW control or decoder change.

## Verification and handoff

Test the production workflow with fake persistence, including transient/results playback,
duplicates, prior edits, failures, pending work, stale actions and transport continuity. Test
actual Android PendingIntent/custom-action payloads and PL/EN labels on the host. Exercise the
real PlaylistSession completion path and its existing C103 safeguards. Deliberately disconnect
acceptance/persistence or payload binding and require test failures, then restore sources.

The full forced test/lint gate and clean-source minified release APK follow on the stacked B46
branch. Owner checks the expanded notification/lock screen while playing and paused, playlist
membership, restart durability, prior edit saving and unchanged playback. Installed build/device
and scenario outcomes must be recorded only when supplied. No phone result is established yet.
