# What only a phone can prove

*Started 2026-09-15, because a build was handed over with a fix in it that no check in this
repository can reach.*

The suite covers what a machine can decide: 237 unit tests, 390 page checks, 15 engine checks, 8
server checks, and every one of them runs in a second on a desktop. **None of them has a phone.**
Four kinds of fault live entirely outside their reach, and each one has already shipped at least
once:

- **the process dying** — an exception out of a JNI frame or an audio callback is `std::terminate`,
  and there is no test host to observe it from;
- **the foreground service** — whether a notification survives leaving the app, which is
  Android's decision and depends on the device;
- **audio in a real output** — glitching, sample rate, ducking under a notification;
- **anything about how it looks**, which is the whole of the UI.

So a handover build is not "tested" when the suite is green. This is what a person checks on a device, and it
is written down so it is the same list every time rather than whatever anybody remembers.

## Every build

**Play for twenty minutes without touching it.** Random, scope *Everything*, screen off, phone in a
pocket. This is the single most valuable test in the document because it is the only one that runs
the decoders against files nobody chose, and the library is 516,118 tunes of which a fair number are
damaged. **What you are looking for is the app still being there.**

A tune that cuts short and moves on is **not** a fault — since 2026-09-15 that is a contained
decoder failure behaving exactly as intended (`docs/STATUS.md` C42). The app vanishing is the fault.

**Leave the app while it plays.** Home button, then use the phone normally for ten minutes. The
player notification must stay up and the music must not stop. A process killed here is `C43`, and it
returned once after being called fixed.

**Open Now Playing on three files of different kinds.** A tracker module, a SID, and something
emulated — SNDH or NSF. Since `describe()` was guarded, a decoder that fails while reading its own
metadata shows an **empty information block** rather than killing the app. An empty block is worth
reporting with the file name; it is the visible end of a contained failure.

## Before production: 0.9.1, 2026-09-24

*The last look before the app goes public on 2026-10-02, on the release build of 0.9.1, installed
over what was there so that nothing stored is lost. Fifteen to twenty minutes. What changed since
0.8.0 first, then the paths every new user takes.*

**Since 0.8.0**

1. **SID and SNDH seek.** Drag the bar in a SID (HVSC) and an SNDH: it gets there, with a spinner
   if it takes a moment; several drags in a row end at the last one.
2. **A tune with no length.** An NSF that loops: the total reads `~3:00` or less, and the bar does
   not change length between it and a MOD.
3. **A folder fetched ahead.** On Wi-Fi, open a Modland author: spinners three at a time, then the
   phone-with-a-tick; on mobile data with the default setting, nothing spins. *Settings → Online
   catalogues* has the setting.
4. **Links.** A *Share with Protracktor* link from https://przunk.github.io/Protracktor/ opens the
   app; *Settings → Apps → Protracktor* shows no "Browser app".
5. **Refusals.** `Nintendo Sound Format / Y. Matuo / 19 neunzehn.nsf` says Famicom Disk System;
   `Beaver Sweeper / Steffo / nokia.gtk` ends "Modland lists it as Beaver Sweeper."
6. **Modland's list.** No Deflemask, FamiTracker or Music Editor; SidMon 1 is there and plays.

**What every new user does**

7. **First start with nothing.** Clear the app's storage (*Settings → Apps → Protracktor → Storage →
   Clear storage*) -- **this deletes playlists and history on the phone**, so only on a phone where
   that does not matter, or skip it. The empty playlist offers the download; Modland downloads and
   Browse fills.
8. **Browse, search, play.** A search with an accent left out (`michal`), a result played, next and
   previous walking the results, the playlist untouched.
9. **Random.** Start it, next a few times, leave it: the playlist is where it was.
10. **Playlists.** Add a tune to one, remove it, undo; switch playlists with an unsaved edit and see
    the question.
11. **The screen off.** Play, lock the phone, ten minutes: still playing, the notification there.
12. **Settings → Language → Polski, then back**: every screen follows, nothing half-translated.

A step that fails goes into `docs/STATUS.md` with the file and what was seen, before anything else.

**Run by the owner, 2026-09-24,** on the C88 test build (`0.9.0-913`, the app code 0.9.1 will carry;
0.9.1 itself not yet tagged): **1-6, 8, 9, 11, 12 pass.** 2: some NSFs show an approximate `~2:38`,
others an exact time -- the measured and the looping, as intended -- and the bar holds its length.
**10** (playlists, undo, the unsaved question) passes too, the same day. **7** (first start from
nothing) not run, by the owner's choice: it clears the phone's playlists and history.

## When something was fixed, add its own check

A fix that cannot be told apart from the bug by looking is not finished. Each entry below says what
to do and **what distinguishes a fix from a coincidence**.

### Build 639 — 2026-09-16 — the fallback length, and the Spectrum in the browser

*In it: `docs/STATUS.md` C56 and `docs/BACKLOG.md` A32. Build 628's list below has not been reported
on yet and still stands.*

1. **A SID must now end on its own.** Play one with the HVSC database **not** downloaded — Settings
   → Storage says whether it is. Before this it played until you pressed next. *Fix looks like:* it
   stops after three minutes and the next tune starts. *Watch for the opposite fault:* a tune that
   ends the **instant** it starts means the setting read zero and took it literally, which is worse
   than the bug being fixed.
2. **The slider**, Settings → Playback → *When nothing knows the length*. Drag it end to end: the
   label must read whole minutes only, 3 to 10, never something like "3 minutes" sitting on a notch
   that stores 239 seconds. Leave it somewhere other than 3, force-stop the app, reopen: it must
   come back where you left it.
3. **It is not only SID.** A `.sndh` that sc68's database has no entry for behaves the same way.
   Nothing to do but know it, so that a tune stopping at your setting reads as correct rather than
   as a fault.
4. **Nothing else may have started stopping.** This is the risk the change carries: the fallback
   applies wherever a length is missing, so a format that reports none and deserves longer will now
   be cut off — and stopping looks normal, so nobody thinks to report it. If a tune ends at exactly
   your fallback and you expected more, that is the report I need, with the file name.
5. **The phone should be unchanged by A32.** ZXTune went into the *browser* engine; the patch
   touches sources Android compiles too, so play a few Spectrum files (`.pt3`, `.stc`, `.asc`) and
   confirm they are exactly as they were. The web player is where the new thing is: those formats
   now play there as well, which they never did.

### Build 628 — 2026-09-15 — the engine boundary and the SAP message

*In it: `docs/STATUS.md` C42 (the phone's half) and C55.*

1. **The twenty-minute Random run above is the main test of this build.** Before 628, an exception
   escaping a decoder ended the process immediately, with no dialog and nothing in the app to see.
   Sixteen JNI entry points and Oboe's audio callback are guarded now. *Fix looks like:* the app
   survives a session it would previously not have. *Coincidence looks like:* a twenty-minute run
   that happened not to meet a bad file — so length matters more than attention here.
2. **`scene register 5 menu.sap`** (Modland, Slight Atari Player / Yezus). **It will still refuse**,
   because the file on Modland is truncated by two bytes and no player can open it. What changed is
   the sentence: it must name the **Atari 8-bit decoder (ASAP)**. If it still says *"wrong file type
   for this emulator"*, the fix did not take.
3. **If anything does go wrong**, `adb logcat` carries a line containing the word `contained`. That
   line means the guard caught something and names the call it happened in, which is the difference
   between a report I can act on and one I cannot.

## What to send back

The file name, and the line from `logcat` if there is one. A screenshot for anything visual — those
live in `user/` and are gitignored, so they never end up in the repository.

**"It worked" is worth saying too.** Several fixes in `docs/STATUS.md` were closed on a build nobody
confirmed, and two of them came back.

## Feedback record

Owner decision, 2026-10-06: retain the source of actual user/tester feedback alongside reproduction
and follow-up, so the testing history can be described accurately when reapplying for Google Play
production access. Reports below are paraphrased from the owner's messages, without personal data.
Detailed defect records live in `STATUS.md`; this is an index, not a second set of defect statuses.

| Recorded | Source | Report | Tracking |
| --- | --- | --- | --- |
| 2026-10-15 | Owner feature request and decision | Filter the History list live while typing, across all entries, without replacing the active queue | B49 on `feature/history-live-search`; host evidence and pending phone checklist in `PLAN_HISTORY_SEARCH.md`; not an independently identified tester report |
| 2026-10-15 | Owner observation and decision | History appears below the top on entry; every new visit should start at the top | C107; three actual Browse UI checks fail before correction; existing C106 branch selected explicitly, phone retest pending |
| 2026-10-13 | Owner testing and presentation decision | During a heart save, Next and seek geometry move into its space; a second tap can skip a track. Keep the heart present; share the dock subtitle, bold title and use a flat P. Search field reported acceptable. | B48 follow-up in `STATUS.md` / `PLAN_NOTIFICATION_HEART.md`; restored 852-test gate/lint passed, 1.3.0 (1162) APK ready, phone retest pending; no inferred full B46 acceptance |
| 2026-10-13 | Owner testing after combined B48+B46 APK handoff | Heart moves into a missing Previous slot; integrated search magnifier was changed to a labelled button; suggestions should be plain text autocomplete rather than an icon-button section | B48/B46 follow-up in `STATUS.md` and their plans; corrected 835-test gate/lint passed, replacement 1.3.0 (1158) APK ready, phone acceptance pending |
| 2026-10-13 | App-user request after testing, relayed by owner | Add a heart action to the player notification for saving the playing track to a playlist | B48/Q16; implemented on task branch: add-only, immediately save all selected-playlist edits; combined host gate passed, phone acceptance pending |
| 2026-10-13 | Code-review finding from 2026-10-04; safety fix selected by owner | Delayed playlist writes can overwrite another playlist; Save can hide newer edits | `STATUS.md` C103; controlled host evidence, merged with owner approval at `f4c9bf1`; no individual playlist phone results supplied |
| 2026-10-13 | Owner observation, reproduced on headphones | Bluetooth connection stops sound; notification position advances while the app's bar stalls; seeking fails and Pause/Play restores playback | `STATUS.md` C102; four routing states owner-confirmed after trial handoff; owner-accepted and merged at `69c42a2`; exact build/device/error trace unknown |
| 2026-10-13 | Owner request and follow-up observation | Android headset buttons appear to work; WWW buttons do not control playback | B47/C104; WWW investigation deferred; exact device/browser/build/buttons and mechanism not supplied |
| 2026-10-13 | Owner request | Reconsider AMP (Amiga Music Preservation) as an archive candidate | Existing `BACKLOG.md` A7; `PLAN_CATALOGUES.md`; access exploration TODO |
| 2026-10-13 | Real app-user feedback, relayed and discussed with the reporter by owner | Keep music playing when returning from search/author browsing to the playlist; explicit active queue proposed | `WISHLIST.md` B45; `OPEN_QUESTIONS.md` Q13; future behaviour change |
| 2026-10-13 | Real app-user feedback, relayed by owner | Mix separated stereo channels so music is audible in both ears; possible headphone-processing mode raised | B6/Q14; three-mode Android trial accepted and merged at `f4d304c`; individual remaining device results not supplied |
| 2026-10-13 | Real app-user feedback, relayed by owner | Offer recent queries when focusing search | B46/Q15; implemented on approved stacked branch: 20 queries, up to three prefix matches, immediate submission in current scope; combined host gate passed, phone acceptance pending |
| 2026-10-06 | App user, relayed by owner | Random list/history and selection become inconsistent after selecting an earlier track, changing scope and pressing Next | `STATUS.md` C97 |
| 2026-10-06 | Different app user, relayed by owner | Browse opens while the playlist is covered by the Random return-choice overlay | `STATUS.md` C98 |
| 2026-10-06 | Owner testing | Random filters open with a slight lag; unavailable chips become active after about a second in an uncertain sequence | `STATUS.md` C99 |
| 2026-10-06 | Tester, relayed by owner | Android Auto support requested | `WISHLIST.md` B44; feature request, not a defect |

The 2026-10-13 requests are genuine user feedback according to the owner. Their exact reporter
identities, builds, devices, occurrence dates and installation channels were not supplied;
the record does not infer how many distinct reporters supplied them. Each tracking entry distinguishes
selection, implementation and acceptance; a request alone is not a completed fix or retest.
Stereo mixing renews B6 rather than creating a duplicate.

The first two 2026-10-06 defects were explicitly attributed to different users; the third came
from the owner's own tests. Do not reclassify that observation as an independent tester report.
The date above is the recording date, not an independently established occurrence timestamp.

## B49 live History search — phone checks pending, 2026-10-15

Test title/author/filename phrases including mixed case, non-ASCII case, literal `%`/`_`, and an
entry beyond the first hundred. Results/counts cover the whole History in newest-first order.
Check fast edits, paging, clearing input, no matches versus empty History, keyboard focus during
loading, PL/EN, retry after read failure and leave/reentry. Filtering must not interrupt audio or
replace the playing queue. Selecting a result still captures the displayed page as before.
Clear History while a filter is pending: all entries must disappear without late rows returning.
[Contract, host evidence and full checklist](PLAN_HISTORY_SEARCH.md). Host UI/native SQLite checks
are not phone timing or behaviour evidence; owner acceptance remains pending.

## C105–C107 — overall owner acceptance, 2026-10-15

After the combined 1.3.3 trial handoff the owner accepted the changes and authorized merging.
C105 entered develop at `557acbb`, then C106/C107 at `d74d339`. The merged tree matches accepted
branch `96a7298`; app/native/scripts match offered APK `ffdbd87` and the recorded 876-test full
gate (lint 0 errors/72 warnings). These are retained source-matching results, not a new merged-head
test run. No rebuild was needed for unchanged application sources.

Device/Android, exact installed build and individual checklist outcomes were not separately
supplied; no agent-run phone tests are claimed. Earlier pending entries below are historical,
superseded by overall acceptance, and their scenario lists remain available for further checks.
Nothing was pushed and master/tags/store are unchanged. At that closeout B49 History search was
only a wishlist request; its subsequent implementation and pending phone checks are recorded above.
It is not another independently identified tester report.

## C107 History entry scroll — pre-acceptance checklist, 2026-10-15

Owner feedback, not an independently identified tester. Exact installed build/device were not
separately supplied. Test a new History entry after scrolling, via Browse root/another domain and
after closing Browse. Each visit should start at the newest row with zero offset. Page changes
within one visit should retain their positions; the next visit resets all History page positions.
[Evidence and complete checklist](PR_C107_HISTORY_ENTRY_SCROLL.md). The trial includes pending
C105 and C106; no acceptance of any branch is inferred from the new host tests.
The forced gate passed 876 tests/116 suites with lint 0 errors/72 warnings. Offered combined
release: **1.3.3 (1178)**, clean source `ffdbd87`,
`dist/protracktor-release-1.3.3-1178-ffdbd87-20261015-191612.apk`. Install without clearing data;
phone behaviour remains unverified. Earlier 1.3.2 metadata below is its historical handoff.

## C106 search ownership — pre-acceptance checklist, 2026-10-15

The owner selected this repair after the C105 handoff. It is a code-review finding, not another
independent user report. `fix/search-request-ownership` is deliberately stacked from C105
`ad9ec54`; the previous C105 branch and 1.3.1 (1170) APK remain unchanged. A combined trial also
contains C105, whose acceptance is still pending. Host regressions demonstrate overlapping-search,
navigation, loading and failure ownership; they do not demonstrate phone frequency or screen behaviour.
The forced gate passed 872 tests with lint 0 errors/72 warnings. Offered combined APK: **1.3.2 (1174)**,
clean source `a4b0a6f`; phone checks below remain pending, not inferred from compilation or host tests.
See [C106 evidence and phone checklist](PR_C106_SEARCH_REQUEST_OWNERSHIP.md) and the
[C105 playlist checklist](PR_C105_LATEST_PLAYLIST_ACTIVATION.md#phone-checklist).

## B48 and B46 — overall owner acceptance, 2026-10-14

C105 subsequently received a separately selected activation fix on `fix/latest-playlist-activation`.
Its code-review source must not be presented as independent user feedback. Full gate passed 859
tests with lint 0 errors/72 warnings. Offered APK: **1.3.1 (1170)**, clean source `07300b1`,
`dist/protracktor-release-1.3.1-1170-07300b1-20261014-005316.apk`; device results still pending.
Use the [C105 checklist](PR_C105_LATEST_PLAYLIST_ACTIVATION.md#phone-checklist) for
rapid A/B/C switching, final-playlist Save/Discard/restart and notification-heart destination checks.
No C105 phone result or merge is established by the earlier B48/B46 acceptance below.

After the latest trial handoff, the owner reported no objections and authorized both merges.
B48 entered develop at `61886e7`, then B46 at `7111c47`. This supersedes earlier pending tracking
entries, not their historical defect reports. Device/Android, exact installed build and individual
checklist outcomes were not separately supplied. The lists below remain available for further
checks; no agent-run SystemUI/phone tests are claimed. Sources match the offered APK and recorded
852-test gate. Merging did not rebuild the APK or rerun those tests; no push/store action occurred.

## Stable B48 notification — pre-acceptance APK handoff, 2026-10-14

The owner's save-state report and approved presentation are recorded above. The 1.3.0 (1158)
APK below is superseded by this follow-up. Install the replacement without uninstalling or clearing
data: `dist/protracktor-release-1.3.0-1162-ea04525-20261014-001502.apk`, **1.3.0 (1162)**,
clean source `ea04525`. The restored forced gate passed 852 tests/115 suites, lint 0 errors/72 warnings.
The integrated search field is unchanged and was reported as acceptable;
this is not evidence for every search/history checklist result. Device/Android and exact installed
build were not separately supplied. Corrected SystemUI geometry and final appearance remain unverified.
First retest a rapid double tap on the heart: it must stay after Next, without shifting controls or
seek geometry and without skipping a track. Then compare the bold title/shared subtitle with the
dock and check the flat P. Use the detailed B48 list below. [Artifact checks](STATUS.md) establish
version/signature/resources, not phone execution or Play-track activity. No merge is authorised.

## Earlier placement/search correction APK — superseded after saving feedback, 2026-10-13

The earlier offered artifact was:
`dist/protracktor-release-1.3.0-1158-f289863-20261013-232128.apk`, **1.3.0 (1158)**,
clean source `f289863`. The corrected forced gate passes 835 tests/112 suites, lint 0 errors/72
warnings. [Version/hash/test-signature checks](STATUS.md) establish the artifact, not installation,
keyboard behaviour or SystemUI layout. First check the integrated magnifier and text-only dropdown,
then heart placement with Previous absent/present and Next absent; these were the earlier checks.
The owner-reported original defects are recorded above. Corrected phone acceptance is still
pending; the owner's later save-state report above is its observed defect. No merge, push or
Play-track outcome is implied.

## Earlier combined B48+B46 APK — superseded after owner feedback, 2026-10-13

The earlier offered minified release was:
`dist/protracktor-release-1.3.0-1154-1452309-20261013-224501.apk`, **1.3.0 (1154)**,
clean source `1452309`. [Artifact checks](STATUS.md) confirm its version/test signature, not actual
installation or phone behaviour. The earlier `9583569` APK lacks the in-app-language correction
and is also superseded. The owner subsequently reported layout/style defects; neither APK is the
corrected trial. Record the replacement's installed build/device/Android and observed results
before approving either branch. No merge or Play-track activity is implied.

## B46 recent searches — owner accepted and merged; remaining device checks

Branch: `feature/recent-search-suggestions`, stacked on B48 with owner approval.
[Contract and host evidence](PLAN_RECENT_SEARCHES.md). The owner's original presentation feedback
is recorded above, followed by acceptance of the field's appearance. Individual search-history
and restart checks still need their own reported outcomes; source code is unchanged in this trial.

1. Submit several distinct queries, then focus an empty search field: expect the latest three.
   Type a prefix, including different letter case: expect at most three matching queries from
   the full retained history, including older ones outside the initial three. A substring that
   is not a prefix must not match; typing must not start a search.
2. Select a suggestion: it must fill the field and immediately search exactly once in the current
   scope, without restoring an earlier scope. Suggestions/keyboard should dismiss; tap the field
   again to reopen them. The original integrated magnifier and keyboard Search must still submit
   normally. Expect an attached dropdown with plain text rows, no icons or Recent searches heading;
   opening it must not push the scope controls or results downward.
3. Reuse a query with different casing/outer whitespace: retain one entry at the front. Submit
   more than 20 queries and restart normally: only the latest 20 remain. Blank searches keep
   their existing scope behaviour but must not add an empty suggestion. Playback History and
   its Clear History action must not change query history.
4. Check English/Polish, accented and long queries, larger system font, keyboard insets and
   result-list space. Report installed build/device/Android and the actual scenario outcome.
5. Run B48 below and the previously accepted stereo/Bluetooth scenarios on the same APK.

## B48 notification heart — owner accepted and merged; remaining device checks

The task branch is `feature/notification-playlist-heart`; B46 starts from it with owner
approval. [Contract and host evidence](PLAN_NOTIFICATION_HEART.md). Use disposable playlists
and exported backups: pressing a heart intentionally saves ALL current edits, not only this track.

1. Play a Browse/Random track absent from the selected playlist. Expand the notification and
   check its outlined heart and target label, including on the locked screen. The heart must stay
   immediately after Next whether Previous is available or absent; also check the end of a queue
   when Next is unavailable. No unavailable skip command should become active. Press it once:
   expect feedback, a filled heart and unchanged sound/position/subsong. Return to the playlist
   and restart normally: the track must remain saved without pressing Save in the app.
   During saving the heart must never disappear or move Next/seek geometry. Tap twice rapidly
   at its position: no skip, deletion or duplicate; pending presses may report that saving continues.
2. Press the filled heart: no deletion or duplicate, and feedback that the playlist is saved.
   Repeat with a track already playing from the playlist and with earlier unsaved edits in a
   disposable playlist: those edits must also become saved, as expressly selected by the owner.
3. Repeat paused and in English/Polish. Saving must not start playback or change Browse/Random
   context. Next/Previous and Stop must remain available as before; check stereo mode retention.
4. Change tracks or playlists around a notification press. An older button must not save a
   different track or another playlist. During startup/loading there must be no misleading action.
5. Observe pending/failure feedback if encountered; failures must not discard the working list.
   Report device/Android/build and whether the heart appears in expanded notification, quick
   settings and lock screen. Host construction does not guarantee manufacturer placement.
6. Compare with the dock: title is prominent/bold, subtitle has the same author/platform/year
   information and fallbacks. Check missing year/author, long titles, PL/EN, large font and paused
   playback. The app adds no notification marquee. The upper small icon is a flat P without the
   launcher's black background or decorative cells; system tint/font/clipping can vary by device.
   Heart labels are accessibility/controller metadata, not new visible text beside the icon.

The original defects and subsequent overall correction acceptance are established by owner
feedback. Individual saving/search checklist results were not separately supplied; overall
acceptance and sideload testing do not prove Play-track activity.

## B6 stereo mix — owner accepted; remaining device checks

Branch: `feature/stereo-mix-modes`. The [trial plan](PLAN_STEREO_MIX.md) records implementation and
artifact evidence. Install the minified release APK without uninstalling or clearing data.
Offered build: **1.2.0 (1146)**, source `ad6e3cd`; this does not establish the installed build.
Positive feedback after handoff and explicit owner merge approval were received on 2026-10-13;
merged at `f4d304c`. Device/headphone details, preferred mode and individual scenario outcomes
were not separately reported. The checklist below remains available for further verification.

1. Record build/device/Android/headphones and a representative separated stereo track. Open
   **Settings → Playback → Stereo mix**. Original is initially selected unless a trial choice was
   already saved. PL labels: **Ustawienia → Odtwarzanie → Miks stereo**, **Oryginał / Zwężone / Mono**.
2. While playing, compare Original, Narrow and Mono. Narrow should retain stereo but reduce its
   separation; Mono should produce the same mix in both ears. Look for clicks, volume jumps,
   interruptions, position resets or changed subsongs. Already-mono content should not change.
   Listen to multiple formats, including MP3 if available; the shared processing is after decoding.
3. Pause, change the mode and verify it stays silent; Play should use that mode. Check Next,
   Previous, subsong changes and choosing a mode while another track is still loading.
4. Leave/reopen Settings, switch playlists, then restart the app normally: the choice should
   remain global, without autoplay or lost playlist edits. Check English and Polish labels,
   selected border/text and readability at your font size. No app-data reset is needed.
5. Repeat the accepted Bluetooth connection/disconnection scenarios in Narrow and Mono. The
   preference should survive routing and disconnection must still pause. Shared/exported audio
   and the web player intentionally remain unchanged. Opposite-phase stereo can cancel in Mono;
   report any objectionable sound with the exact track/mode rather than assuming a decoder fault.

Report whether Narrow/Mono are useful and whether the 50% Narrow strength is comfortable. This
is a reversible user-feedback trial, not a promise to keep it or add crossfeed/Dolby. All phone
scenarios remain **not checked individually** until reported by the owner; do not infer
Play-test participation from general acceptance or the merge.

## C102 Bluetooth output recovery — combined trial

Origin branch: `fix/bluetooth-output-recovery`, stacked on C103; merged with owner approval at
`69c42a2` on 2026-10-13. Release APK details are in the
[C102 handoff](PR_C102_BLUETOOTH_OUTPUT_RECOVERY.md). The owner confirmed these phone scenarios:

| Starting state | Bluetooth transition | Reported result |
| --- | --- | --- |
| Paused | Connect | Remains silent |
| Paused | Disconnect | Remains silent |
| Playing on speaker | Connect | Playback continues through headphones |
| Playing on headphones | Disconnect | Playback pauses |

The owner accepted these results and authorized the combined merge. These checks do not establish
the remaining scenarios below, including Pause racing with connection rather than already being
paused. Installed build/device details were not separately supplied after the trial handoff.
No app-data reset/uninstall is needed or authorised.

1. Record installed version/build, phone/Android version, receiver and track/format. Start a
   reasonably long track on the phone speaker; connect headphones and confirm Android selected
   them as media output. Sound should return on its own, position should continue rather than
   restart, and both app/notification bars should agree. A short handoff gap may occur.
2. Seek after switching; use Pause/Play, Next/Previous and change subsong on a suitable file.
   Repeat with an emulated format and a tracker; position/subsong should not reset during recovery.
   Receiver-button support itself is still B47, not established by this patch.
3. Repeat speaker → headphones several times, including with the screen locked and app in the
   background. Disconnecting headphones must still honour the existing becoming-noisy Pause policy,
   not automatically broadcast sound through the speaker.
4. Pause just as headphones connect. Remain paused; reconnecting must not start music. Explicit
   Play should resume when requested. Also try another player's focus takeover during connection:
   audio recovery must not override focus loss or resume after a call.
5. Let a short tune end around the route switch. Existing repeat/next/subsong rules should apply;
   output recovery must not replay the ended tune by itself.
6. If reopening fails, expect paused controls plus the audio-device notice, not indefinite silent
   Playing. Explicit Play is the retry. Any repeatable failure needs a log and device/build details.
7. If available, repeat the original scenario in the car safely while parked. The earlier car
   observation is not confirmed by a headphone result. Include the [C103 checklist](#c103-playlist-write-safety--phone-retest-pending)
   using disposable playlists for further regression checks.

For diagnostic evidence capture a short logcat around the transition; native tag `protracktor`
logs the output interruption/open/start failure, Kotlin tag `Protracktor` logs successful recovery.
Do not collect/upload private app files or a whole device log unnecessarily. Report whether sound,
both progress bars, seek and manual controls recover, rather than only whether a device connects.

## C103 playlist-write safety — phone retest pending

Branch: `fix/playlist-write-safety`, included in the stacked `fix/bluetooth-output-recovery` trial.
Merged with owner approval at `f4c9bf1` on 2026-10-13; no individual playlist phone results supplied.
The owner requested the release APK only together with the Bluetooth fix. The combined artifact
is tracked in C102's handoff; no phone result is established by the host tests.
Use exported backups and disposable playlists; do not clear storage or uninstall to test this.

1. Create A and B with visibly different tracks. Play a track in A whose metadata is still arriving,
   then switch to B promptly (confirm discarding edits if asked). Switch back, restart the app and
   verify each playlist retains its own contents. Repeat while adding tracks automatically.
2. Edit A, press Save and immediately make another edit. The newer edit must still require Save;
   after saving again and restarting, both accepted edits must be present. During another Save,
   change only playback position/repeat: these transport changes must not leave the playlist dirty.
3. Try two consecutive saves with an edit between them, then switch playlists. Verify the newest
   saved contents after restart; a completion from A must not hide an unsaved edit in B.
4. Let metadata arrive after an edit, then use Revert. Check the stored contents remain restored,
   including after waiting and restarting. Delete a disposable playlist while metadata is arriving;
   verify another playlist is intact. Finally reset the only disposable playlist: it must stay empty
   after waiting/restart, without old entries reappearing. Back up real lists before this last case.
5. Check ordinary add/remove/reorder/Undo, Save/Revert, playlist counts, switch/create/rename/delete
   and restore without autoplay. Existing editing and playback controls should remain unchanged.

The races are forced deterministically with suspended stores on the host; manual timing may never
hit the same interleavings. A successful ordinary phone run is useful acceptance evidence, not
proof that every suspended interleaving or global store writer has been exercised.
Installation channel and Play closed-test participation were not supplied for these reports;
neither may be inferred from their existence. The offered sideload APK is not evidence of a Play
closed-track installation or activity.

For each follow-up, record the actual build and device/Android version when supplied, reproduction
result, diagnosis, fix commit, APK or Play-track build delivered, and tester retest result/date.
Keep missing information explicit. A recorded report is not a completed fix; passing host tests
is not a phone retest; preparing an APK is not uploading or publishing an update. C97–C99 have
host regression checks and were accepted by the owner on 2026-10-06 after the combined release
APK handoff. The owner reported successful operation and authorised sequential merges; this is
owner feedback, not an independent retest by the original reporters. The offered candidate was
1.0.0 (1091), source `9110e62`; the installed build/device/Android version were not separately
restated. No per-scenario checklist results or measured latency were supplied. Only completed,
evidenced steps should be described as such
in a production-access application. This record does not itself establish production eligibility
or guarantee approval.

### C97 regression checklist (owner acceptance recorded above)

Use the release APK identified in [Random feedback PRs](RANDOM_FEEDBACK_PRS.md). Play ten Random
tracks, select track five, and change the filter to Atari 8-bit. The current tune and all ten
rows must remain. Next should play rows six through ten in order; only the following Next appends
and plays the first new-scope track at row eleven. One Next exposes one entry, never three future
ones. Confirm that highlighted row, dock title and audible tune agree on each step. Repeat from
the last row, switch filters rapidly, test Previous and the explicit dice re-roll, and briefly
leave/return to Random. Record the installed build, device/Android version and actual observations;
background generation is host-tested but its latency and audible transitions need the phone.

### C98 regression checklist (owner acceptance recorded above)

While Random plays, press Back to the covered playlist. Browse and WEB must be visibly disabled;
neither tapping nor holding WEB should open pairing or send anything. Settings must open normally;
return from Settings and check both Back to Random and Back to the playlist. After returning to
the playlist, Browse and WEB must work again. Repeat with a paired browser and a playlist containing
tracks so WEB is present. The same guard applies to the existing search/external playback covers.

### C99 regression and timing checklist (accepted; quantitative timing unmeasured)

Start the final release APK without visiting Online Catalogues. Enter Random, then open Filter
immediately (cold) and after listening briefly (warm). A genuinely pending first read may show
Checking available filters; it must not falsely recommend downloading favourites. Close/reopen
Filter repeatedly: known available chips should not flash disabled. Check unavailable platforms
remain disabled. Download a genuinely absent favourites list with the panel open, then verify
availability updates without re-entering it. After removing/rebuilding an index, check the new
availability rather than the old cached answer. A read failure should offer Retry.

Compare with the preserved 1084 APK only if it is already installed or can be updated safely;
do not uninstall/clear data or force a downgrade for this comparison. Record APK/build, device,
Android version, cold/warm sequence and observed opening/enablement times. No on-device timing
or performance improvement has been measured by the agent.
