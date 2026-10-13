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
| 2026-10-13 | Code-review finding from 2026-10-04; safety fix selected by owner | Delayed playlist writes can overwrite another playlist; Save can hide newer edits | `STATUS.md` C103; controlled host evidence, merged with owner approval at `f4c9bf1`; no individual playlist phone results supplied |
| 2026-10-13 | Owner observation, reproduced on headphones | Bluetooth connection stops sound; notification position advances while the app's bar stalls; seeking fails and Pause/Play restores playback | `STATUS.md` C102; four routing states owner-confirmed after trial handoff; owner-accepted and merged at `69c42a2`; exact build/device/error trace unknown |
| 2026-10-13 | Owner request | Verify Bluetooth Next, Previous, Play/Pause and Stop; existing MediaSession callbacks do not establish receiver-button behaviour | `WISHLIST.md` B47; verification/improvement TODO |
| 2026-10-13 | Owner request | Reconsider AMP (Amiga Music Preservation) as an archive candidate | Existing `BACKLOG.md` A7; `PLAN_CATALOGUES.md`; access exploration TODO |
| 2026-10-13 | Real app-user feedback, relayed and discussed with the reporter by owner | Keep music playing when returning from search/author browsing to the playlist; explicit active queue proposed | `WISHLIST.md` B45; `OPEN_QUESTIONS.md` Q13; future behaviour change |
| 2026-10-13 | Real app-user feedback, relayed by owner | Mix separated stereo channels so music is audible in both ears; possible headphone-processing mode raised | Existing `WISHLIST.md` B6; `OPEN_QUESTIONS.md` Q14; no diagnosed output defect |
| 2026-10-13 | Real app-user feedback, relayed by owner | Offer recent queries when focusing search; three entries or a scrollable preview remain to be discussed | `WISHLIST.md` B46; `OPEN_QUESTIONS.md` Q15; feature request |
| 2026-10-06 | App user, relayed by owner | Random list/history and selection become inconsistent after selecting an earlier track, changing scope and pressing Next | `STATUS.md` C97 |
| 2026-10-06 | Different app user, relayed by owner | Browse opens while the playlist is covered by the Random return-choice overlay | `STATUS.md` C98 |
| 2026-10-06 | Owner testing | Random filters open with a slight lag; unavailable chips become active after about a second in an uncertain sequence | `STATUS.md` C99 |
| 2026-10-06 | Tester, relayed by owner | Android Auto support requested | `WISHLIST.md` B44; feature request, not a defect |

The 2026-10-13 requests are genuine user feedback according to the owner. Their exact reporter
identities, builds, devices, occurrence dates and installation channels were not supplied;
the record does not infer how many distinct reporters supplied them. They are pending discussion,
not completed fixes or retests. Stereo mixing renews B6 rather than creating a duplicate.

The first two 2026-10-06 defects were explicitly attributed to different users; the third came
from the owner's own tests. Do not reclassify that observation as an independent tester report.
The date above is the recording date, not an independently established occurrence timestamp.

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
