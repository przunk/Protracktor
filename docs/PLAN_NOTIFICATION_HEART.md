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

## App-language correction — 2026-10-13

Final inspection found that service/application contexts do not receive MainActivity's language
wrapper. Testing only a Polish resource configuration had missed the actual saved app selection.
A regression using `AppLocale.select` failed on both SDK 29 and 33 before the correction (2/14).
New labels and background feedback now resolve resources through `AppLocale.wrap` on invocation.
The new checks also switch back to English; no service restart is needed to resolve new text.
The original transport/channel labels are outside this feature's changes.

**Checked:** all 67 focused checks passed after correction: 10 domain, 16 SDK 29/33 adapter/locale
and 41 session checks, zero failures/errors/skips. The full combined gate and final APK must be
rerun on the rebased B46 branch. The earlier 1.3.0/1154 APK from `9583569` lacks this correction
and is not the final trial artifact. Actual service delivery and phone language behaviour remain
**not checked**. Only local unpublished feature history is updated; develop/master are unchanged.

## Host implementation checkpoint — 2026-10-13

Implemented the pure acceptance/save workflow, session write completion, two platform actions,
immutable URI payload binding and PL/EN feedback. Focused host checks passed 63 tests: 10 domain
checks, 12 Android action checks (six each on SDK 29/33), and 41 real PlaylistSession checks.
The latter include workflow-to-session persistence and earlier working edits. Removing stale/busy
acceptance, URI PendingIntent identity and truthful persistence completion failed 7/63 checks
(2 domain, 4 Android and 1 session). All three sources were restored and SHA-256 compared;
the final focused rerun again passed 63 with zero failures/errors/skips.

No actual PlaybackService lifecycle, real MediaController/SystemUI delivery, lock-screen layout or
phone persistence result is established by those host checks. Full gate and combined APK follow on
B46's explicitly approved stacked branch. Evidence: ignored `dist/notification-search-verification/`.

## Owner-reported placement correction — 2026-10-13

After the combined trial handoff, the owner reported that the heart occupies the left-hand
Previous position when Previous is unavailable, then moves right when Previous becomes available.
The intended position is always after Next. Device/Android version and installed build were not
separately supplied; this is real owner feedback, not an agent reproduction on a phone.

Android 13+ fills unreserved transport slots with custom actions. The initial implementation
omitted both session slot reservations. Reserve Previous and Next through the production
MediaSession factory, without advertising unavailable skip commands or changing playback.
The documented wire keys work with the framework session and need no AndroidX media dependency.
Host SDK 29/33 checks record the production factory's actual `MediaSession.setExtras` call.
Robolectric does not deliver framework session extras/state to its controller, so the initial
controller-based checks were invalid host evidence and were replaced, not reported as a reproduced
phone failure. SystemUI's final layout remains a phone check. Full combined verification and a
replacement APK follow on B46.

**Checked:** 71 focused host checks passed (the previous 67 plus four factory checks on SDK
29/33). Removing the reservations from the same factory failed all four new checks. Sources
were restored and SHA-256 compared. These checks prove the session configuration call, not
SystemUI rendering or controller delivery on Android. The full combined gate remains pending.
