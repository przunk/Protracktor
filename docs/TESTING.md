# What only a phone can prove

The consolidated [WEB parity browser trial](WEB_PARITY_CHECKLIST.md) covers W01-W25, supported
environments, persistence/PWA updates and remaining physical-browser acceptance. Its unchecked
boxes are not implied passes from host checks. Current source/artifact and automated results are
in [STATUS](STATUS.md#web-feature-implementation). The complete gate also
runs the actual WEB packaging scripts against disposable reproducibility/identity fixtures.

## WEB W22 selected downloads — browser checks pending, 2026-10-29

Host companions: `node scripts/check-library-downloads.mjs`,
`node scripts/check-dataset-downloads.mjs`, `node scripts/check-download-picker.mjs`; the page
suite drives actual empty-list/catalogue actions, Download, Stop, Retry, Close icon, Escape and
live language controls. Shared download-plan rows also run against production Kotlin DownloadPlan.
Removing a row must fail both readers and invalidate Gradle's declared input without a new command.
Controlled faults cover the real event path as well as cancellation, order, locks and storage.

Real browser trial remains unchecked:

- Start without indexes. Open the empty-playlist offer, inspect three checked choices and the
  approximate 47 MB total, then close without fetching. With everything held, no choice should be
  checked and disabled Download should explain what to select. Refresh remains an explicit choice.
- While music plays, choose Modland and metadata, change selections and start. Check stable controls
  and current-step progress; scrolling/large text must leave Stop reachable. Closing via its icon,
  the scrim or Escape must keep work running and return focus to the originating view's action.
  Tab/Shift-Tab must wrap within the dialog, including Close. Reopen the same frozen selection.
- Interrupt a later request. Earlier completed datasets must remain browsable. Stop should report
  retained/unfinished work and start nothing later; Retry should fetch only failed/pending substeps.
  Check another tab's existing user data and current audio/source/FIFO/editor remain intact.
- Refuse storage or disconnect during refresh. Old data must survive with an explicit partial
  outcome. Do not infer success from a spinner stopping. Test the canonical HVSC request in the
  actual browser; host Origin-bearing GET/CORS headers and identical SHA are partial evidence.
- Switch PL/EN, use keyboard/touch and check both themes at narrow/wide sizes. Native comparison
  covers behaviour; host DOM identities and stylesheet checks do not establish physical layout,
  audible continuity, mobile network conditions or browser background lifetime.

## WEB W19 read-ahead — browser checks pending, 2026-10-29

Run `node scripts/check-playback-read-ahead.mjs`, `node scripts/check-folder-prefetch.mjs` and
`node scripts/check-playback-context.mjs`; the complete page suite drives the real playlist Next,
manual FIFO, Clear, Browse and Settings handlers. Shared folder rows also run against production
Kotlin FolderPrefetchTest as a declared Gradle input. Fault controls must detect stale publication,
wrong FIFO priority, missing total budget, skipped over-budget rows, unknown-network permission,
missing real opened hook and hidden-folder work. Finish with the forced complete gate and lint.

Browser trial remains unchecked:

- Play a finite list, then Next; verify its prepared successor, manual FIFO priority, duplicates,
  shuffled Repeat All boundary and uninterrupted current playback when Clear retires speculation.
- Open a Modland author's folder. The default Wi-Fi only choice must explain why automatic
  folder downloads are paused in this browser. Always starts up to three files concurrently;
  Off stops new starts. Leaving ends pending folder work; existing shared downloads may finish.
- During progress, select rows, scroll, retain keyboard focus and open/close Queue over Browse.
  Spinner and offline-copy marks must update in place, with no shifted controls or stale folder.
- Reload with each policy, including refused storage. Restore the choice without initial music
  requests or autoplay; report refusal while keeping the selected runtime choice usable. Changing
  PL/EN updates the live explanation without changing playback, selection or scroll.
- Check actual mobile network/CORS, cache quota, touch scrolling, audible transitions and background
  behaviour. Host DOM/stream tests establish none of these physical-browser properties.

Use the current APK in [Status](STATUS.md) for the active regression checklists. Older
APK numbers identify the original handoff, not a recommendation to install an older build.

## WEB W18 restored playback modes — browser checks pending, 2026-10-28

Host fresh-page/IndexedDB and control events are partial evidence; physical reload, storage
permissions and reader/touch behaviour remain unchecked until tried in a browser.

- Choose Shuffle and each Repeat mode, select a later playlist track and reload. Restore that
  playlist/selection, marks and Repeat One glyph, paused, without opening audio. Try empty/new
  storage and a saved all-subsongs value from the previous WEB version; defaults are Off.
- Toggle Play every tune from Settings and Now Playing. Both reflect the same choice and persist
  on reload. Next walks subsongs only when enabled; direct numeric selection works either way.
  Settings offers this global choice even with no file or a single-tune file.
- Play one source, view another playlist and change modes: actual playback and manual duplicate
  requests survive, with no dirty playlist. Refuse preference writes: keep the session's mode and
  explain the refusal. Permit a later write and try again. Do not clear user storage to recover.
- With slow saved-state reads, select another playlist before initialization finishes: the newer
  choice wins. Open a full-list handoff link on startup: its list wins and remains paused, keeping
  modes. Existing explicit play links retain their separate playback behaviour.

## WEB W17b subsong strip — browser checks pending, 2026-10-28

Host DOM rectangles and actual native Robolectric components are comparison evidence; real
browser scroll clamping, touch, focus, painting and background behaviour remain unchecked.

- Open a file at an arbitrary tune, e.g. 69 of 256, then Now Playing. One horizontal strip should
  reveal the active chip. Advance while nearby: already-visible chips do not scroll; once the
  new chip leaves the watched region it comes into view. A partly visible previous chip counts.
- Manually scroll to 249–256 while tune 70 plays. Further advances must leave that distant region
  in place. If music later reaches that region, ordinary following resumes. Change file/count,
  close/reopen Now Playing and test both subsong modes: a fresh opening reveals actual current.
- Select numeric chips by pointer/keyboard. Current selection must not restart, marks must follow
  decoder answers, and Space must not also Play/Pause. Focus should survive current-only redraws.
  Start another file with the strip visible: old controls disappear before the load answers.
- Inspect a different file: read-only metadata, no controls for its subsongs affecting the audible
  file. Return through the dock: actual current title/metadata/chips. Delay its description and
  leave/reopen the live panel: old replies/errors must not replace the new view. Try EN/PL, themes,
  narrow/wide windows, zoom, reduced motion and real touch scrolling; these remain manual checks.

## WEB W17a Browse positions — browser checks pending, 2026-10-28

Host fixtures mock row rectangles and execute actual navigation/Back events; they do not establish
physical scrolling, scrollbars, browser clamping or touch feel. No bundle/deployment yet.

- Scroll far down Modland formats/authors, open a folder and Back through several levels. Return
  to the exact departed row. Refresh/change an index so the row moves; remove that row: return
  to its identity when present, otherwise retain the parent's offset without an old-index jump.
  Back during a delayed read must keep the newer level and its position. Try empty/loading rows.
- Close Browse and open it anew: top/root. Queue over Browse preserves its underlying position.
  Same-level index/status refresh must not reset scroll or move input focus. Use mouse, keyboard
  Enter/Space and touch; neither navigation nor restoration may restart music or edit the queue.
- History fresh entry/query starts at the top. Scroll page one, change page and return: each page
  keeps its own offset within that phrase. Open/cancel Add to another playlist over History: same
  query/page/offset. Queue open/close preserves it. Returning to an earlier phrase starts fresh.
- Search positions belong to the result's producing query/scope; pending field/chip edits must
  keep that result's position. Open a Saved place, add another bookmark ahead of it while away,
  then Back: wait for refreshed membership before locating the departed bookmark. Try deletion,
  refused/delayed refresh and EN/PL. Verify no old result scrolls a newer view.

## WEB W16 dock — browser checks pending, 2026-10-28

Host DOM/CSS/event checks cannot prove physical geometry, focus/touch feel or visual overlap.
No browser screenshot or bundle/deployment is claimed for this checkpoint.

- Play Battle of honour.it by Treks, then files with 1, 5 and 256 subsongs in both modes. Title
  stays upper left, Add upper right, shared metadata lower left and full Tune N of M lower right;
  Queue stays outside. Add's target and title width must not shift as counts or ownership change.
- Compare with/without Add over the active playlist: ordinary card height stays unchanged, with
  balanced text insets and no top spacer. Try Polish, narrow/wide windows and enlarged text/zoom;
  footer wrapping may grow the card, never overlay text or the Add target. Check both themes and
  reduced-motion marquee behaviour. These rendering scenarios remain unchecked.
- Tap Add, the footer and Queue separately. Add edits only the selected draft, footer opens Now
  Playing, Queue opens its own panel; transport and manual requests stay unchanged. Tab to the
  card and use Space/Enter without also triggering Play/Pause. Check visible focus and its reader
  name/expanded state. Inspect another file, change subsong, load/fail a different file and return
  to a single-tune track: the counter must describe only the actual opened file.

## WEB W13 Browse selection — browser checks pending, 2026-10-28

Candidate only; no bundle/deployment. Host pointer/keyboard/IndexedDB checks do not establish
physical touch, checkbox geometry or browser focus behaviour.

- Long-press a playable Browse/Search/History row, then tap rows/checkboxes and Select all/none.
  Space/Enter and the keyboard context menu must work without also toggling audio. Check stable
  row/header spacing, hidden contextual actions, disabled swipe and no playback scroll-follow.
  First Back/Escape clears ticks; the next follows normal navigation. Nothing changes playback,
  the current source, elapsed time or manual requests just by selecting.
- Select rows out of order; Add to current must append in source order to its draft and wait for
  Save. Other playlist/New playlist saves its own destination without changing the editor or
  playback; cancel does not restore ticks. Refuse storage, retry, or leave during a delayed read:
  no empty action, duplicate append or obsolete chooser/error over the newer visit. From the phone
  offers only other destinations, with no protected write.
- Share a batch and verify portable ordered references without buffers. Add held local tracks,
  Save, switch editors and play them without resending. Reload without those bytes: unavailable
  placeholders explain the missing data. Stored lists and links must contain no audio payload.
- Open/close Queue with a Browse selection, live-refresh History, change its query/page, submit
  Search and navigate folders. Only IDs belonging to the same producing visit survive; a new
  list clears ticks. Pending general-Search field/chip edits keep its existing result selection.
  Check keyboard, mouse/touch, PL/EN, both themes and narrow/wide layouts in a real browser.

## WEB W12b M3U8 transfer — browser checks pending, 2026-10-28

No bundle/deployment yet. Host events exercise FileReader and IndexedDB, but native file-picker,
file-download and cross-application sharing UI remain unchecked.

- In Playlists, Import a UTF-8 native M3U8, a plain list, Unicode names and HTTP(S)/ASMA references.
  A new playlist appears without selecting it, changing the current draft, pausing audio or
  touching manual requests. Try partial/missing locals, duplicates and all-missing input: counts
  refer to resolved offerings; imported membership keeps first-occurrence order. A nonempty
  all-missing input creates an empty new list and says 0/N. Empty/unreadable/comment-only input
  and cancelled picker must not create a playlist. Try the same picked file twice.
- Reorder/remove without Save and export that playlist: the file must carry the draft. Export
  another playlist while changing the visible one: contents/name remain the initial requested
  selection. No implicit Save or playback change. Unicode label survives inside a sanitized
  filename. From the phone offers only read-only Export, never Rename/Delete. Empty export and
  read/download failures explain themselves. Reimport browser exports, including unknown artist
  titles containing a separator. Android's existing empty-artist reader discrepancy is recorded
  in Status; it is not fixed by this WEB branch.
- Import references to files currently held from the phone. Replace the phone list, choose the
  imported list and play it; held buffers stay usable. Edit and Save, then export: neither the
  stored record nor the file may contain audio payload. Reload the tab with that list selected:
  missing local bytes must disable its rows, not try to fetch `content://`. Reconnect/resend bytes
  and check rehydration, without clearing playlists or History.
- Leave the view/change playlist during a delayed file read. Completion creates its own list
  without stealing the new view or editor. Storage refusal/collision must preserve existing data;
  failed refresh after a committed import reports both outcomes honestly. Check EN/PL, both
  themes, narrow/wide windows, keyboard, actual picker/download and Android round trips.

## WEB W12a live reorder — browser checks pending, 2026-10-28

Candidate only; no WEB bundle or deployment yet. Host geometry/pointer hooks cannot establish
physical touch, pointer capture, overlap painting or scroll feel.

- In an editable playlist, hold its labelled Reorder handle. Move less than one row, then across
  several rows, pause between moves and partially reverse. Logical moves apply while held;
  the raised row stays under the pointer, above neighbours, with the viewport pinned except
  for edge auto-scroll. Cancellation retains already committed moves.
- Play a middle item, add duplicate manual requests and reorder that playlist. Track, elapsed
  time, position and pending requests stay unchanged; Next uses the edited source order. Repeat
  and shuffle preserve their Android semantics. Reorder visible B while A plays: A stays intact.
- Save and reload the draft; discard another reorder. Verify the selected playlist is the one
  saved/restored. Try a pasted list containing repeated URLs, including the playing occurrence.
- In Queue, drag duplicate pending requests within their indentation. Current/source rows cannot
  be reordered through their handles. Close Queue or change membership while dragging: no hidden
  continued movement. Existing swipe/long press and departure animation must still behave normally.
- Use keyboard arrows and the handle's labelled move menu; check focus, boundary actions, EN/PL,
  both themes, narrow/wide windows and reduced motion. A completed drag must not trigger playback
  or the menu through its synthesized click. Protected From the phone, Random/transient and
  selection-mode lists must not gain editable-playlist handles.

## WEB W11 Search scopes — browser checks pending, 2026-10-28

Candidate on `feature/web-feature-parity`; no merge, bundle or deployment yet. Host events and
IndexedDB checks cannot establish appearance, keyboard feel, touch or browser latency.

- Search a title, author and words split across both. Try accent folding, blank and one-character
  queries, punctuation and a result beyond the former 300-row cap. Check global title ordering,
  the 2000-per-source limit and visible full catalogue counts.
- Put the same URL in a saved playlist and a catalogue; the saved copy should win once. Search
  across another saved playlist with a different selected editor and check that neither playlist
  nor playback changes. A missing local phone copy explains its refusal; a still-held copy plays
  without persisting bytes or detaching the source buffer.
- Choose Saved playlists, Online, individual catalogue chips, By platform and several machines.
  Empty chip selections cover all in that kind. Scope selection never auto-searches. Kind changes
  clear old rows; chip/text edits keep the producing rows stable until explicit Search. Recent
  suggestions submit immediately in the selected scope and close the keyboard/dropdown.
- Back first widens narrowed Search, then returns to Browse retaining the phrase; reentry retains
  the previous answer. Folder and History Back must not consume cached general-Search text.
- Play an answer, change text/chips without submitting, then use Queue Go to source. The restored
  query/scope/membership must be those that produced the playing list; music/manual FIFO stay
  intact. Opening/closing Queue over Search leaves the field usable.
- Try rapid submissions, leave/reentry, refused storage, scope Retry, index removal/replacement,
  and saved-reference search without indexes. No obsolete rows/errors or false missing-index
  explanation while reads are pending. Check EN/PL, both themes, narrow/wide screens, zoom,
  keyboard-only operation and software keyboard. These device/browser scenarios remain unchecked.

## WEB W09-W10 — browser checks pending, 2026-10-28

No bundle handoff yet; these changes remain on `feature/web-feature-parity`. Host event/database
checks are not proof of rendering, software-keyboard behaviour or touch.

- Submit five queries; focus Search and check three plain-text recent prefix matches, case
  handling, immediate option submission, arrow keys/Enter and Escape/Tab. Typing alone must not
  search or persist, and result completion must not reopen the keyboard/dropdown.
- Open History with more than 100 entries. Type part of an older title, author and filename;
  edits filter after 200 ms without Enter. Check literal punctuation/accents, matching totals,
  second/last-page boundaries and clearing the phrase. Input focus should survive results.
- Play a filtered History result, then alter the filter and continue playback. Queue Go to source
  must restore the producing query/page/results, without restarting music. Open/close Queue over
  History and continue typing. A fresh Browse → History visit starts unfiltered at the top.
- Check no matches versus empty History, labelled keyboard-accessible Retry after a read failure,
  rapid edits/leave/reentry and Clear during a pending read. No obsolete rows/counts should return;
  failed erase must explain the refusal and leave a usable filter. History Clear leaves recent
  general queries intact. Check EN/PL and both themes, narrow/tall viewports and software keyboard.

## WEB W01-W02 — browser checklist, not yet a bundle handoff

2026-10-27: host checks pass on `feature/web-feature-parity`, including the production
DOM/MessagePort/IndexedDB paths. They do not establish appearance, real touch, sound or background
operation. Keep these checks open until the completed WEB parity candidate is packaged; W03-W25
are still in progress and no intermediate APK/bundle is supplied here.

- [ ] Play playlist A, view/edit B and open Browse. Playback, Next/Previous and metadata remain
  owned by A; Save writes B. Explicitly playing B transfers the source.
- [ ] Add repeated files from main/Browse/History menus and right swipes. Verify independent FIFO
  occurrences, unchanged playlist documents and History replay-origin behaviour.
- [ ] Open Queue from its separate dock button. The shared dock remains reachable, the current row
  is revealed, and Close/Escape restores the prior view. Keyboard focus enters the current row and
  returns to the toggle on close; Help starts collapsed.
- [ ] Advance through manual requests. Played rows depart smoothly without a whole-list blink;
  Previous returns to the parked source and consumed requests do not reappear. Test Repeat One,
  explicit file Next, shuffle and subsong controls.
- [ ] Swipe left on manual/source rows and Undo. Only that occurrence/session row changes. Check
  named green/red reveals, vertical/diagonal scrolling priority, short/reversed drags, cancellation
  and a second finger elsewhere on screen.
- [ ] Drag pending grips in both directions and near viewport edges. They stay under the finger,
  above neighbours and inside the manual block. Source/current rows have no grip. Try keyboard
  arrows, the grip menu and a membership change during drag.
- [ ] Hold a Queue row for Information. It must not play, open a second browser context menu or
  swallow the next button press. Check right-click and keyboard row actions too.
- [ ] Clear while playing, paused and downloading. Keep the actual file/position/job, remove extra
  indentation and allow guarded Undo without a decoder restart or rollback of newer editor edits.
- [ ] Check reduced motion, EN/PL, both themes, narrow/wide viewports, larger text and accessible
  action names. Verify full-width manual highlights and stable dock height.

## WEB W03 source return — focused host checkpoint, browser checks pending

The source label navigates through production handlers and passes the combined context/navigation
host gate. W10 now wires History query/page restoration; W11 Search scopes remain pending. Do not treat this as a packaged handoff.

- [ ] While A plays and B is selected, open Queue and activate its source label. Reveal A without
  restarting, resuming a paused file or changing pending manual requests. Repeat during a manual
  interruption; the label still follows its parked source.
- [ ] Repeat with dirty B: Cancel leaves Queue/draft intact; Save persists B before switching;
  Discard abandons it when the destination is available. A missing source gives feedback.
- [ ] Return to folder, Search, History and Random. Restore the captured route/list, not an
  unrelated author jump; retain live download/cache facts. Back follows the source hierarchy.
  History starts at its top; Search keeps its query; Random return never rerolls.
- [ ] Check the inline name/arrow, full accessible description and destination focus in EN/PL,
  narrow/large-text layouts and both themes. A manual-only/shared-link source is a plain label.

## WEB W04 remembered Random — host integration, browser checks pending

- [ ] Enter Random for the first time: one pick starts, with three hidden prepared picks. Leave
  and return: the same heard list remains, even if a different playlist/author/History is playing.
  Global transport controls follow that actual source; only tapping a Random row activates it.
- [ ] Use ordinary Back and Queue's source return. Random goes back one level to Browse without
  stale author headings, hidden Close controls or a second Back to the playlist.
- [ ] Double-click/double-tap the dice: one new list, same filter, retained manual requests and
  independent global History. A single pointer tap does nothing. Check keyboard activation,
  visible hint, 360-degree rotation, reduced motion, drags, holds and a second finger.
- [ ] Walk an earlier heard row with Next, then pass the heard boundary. One new row appears per
  advance. During manual playback no Random row is highlighted; Previous returns to its anchor.
  Clear/Undo preserves the audible file and remembered record, also while a draw/download waits.
- [ ] Try empty/unavailable indexes and a very small pool. Revisiting an empty session does not
  silently retry; deliberate reroll does. Repeated files have separate positions; pruning one
  never removes another occurrence or changes another active source.

## WEB W05 filters — integrated host checks, browser acceptance pending

- [ ] Open Random/Filter with no indexes, a legacy index and current Modland/ASMA indexes.
  Cold checks explain unknown counts; warm reopening keeps usable chips. Everything remains
  available; platforms with no playable indexed files are disabled with an explanation.
- [ ] Download Favourites while the panel stays open. Distinguish the absent list, a retained list
  without a matching Modland index, and playable matches. All chips refresh together; Retry
  preserves known counters after a failed read. Deleting an index blocks stale availability.
- [ ] Build ten heard picks, select the fifth and change platform. The entire heard record remains;
  Next walks its suffix, then appends one new pick from the selected pool. Check the same choice
  while an author owns playback: neither its current file nor Next changes to Random.
- [ ] Reload and verify the selected scope. Check a prompt explicit choice during slow startup,
  a failed save, empty/unsupported pools and an index update during an older read.
- [ ] Check all chips, the selected shape, explanations and Retry/Download in EN/PL, themes,
  narrow/wide layouts, zoom and keyboard/screen-reader navigation. The inline panel should remain
  scrollable with the shared player dock available.

## C121 WEB decoder filenames — private-window playback reported, remaining checks pending

2026-10-26 feedback reports playback working in a private window while the normal session
initially still failed, with stale cache suspected. Browser/device, per-file results and worker
state were not supplied. Cache activation in the normal session and the other checks below
remain pending; the private-window result does not establish those outcomes.

Use [the corrected web test bundle](../dist/protracktor-web-20261026-224832.tar.gz), source
`3bfa0ea`. For an installed PWA, close its windows and reopen after updating the files so its
new worker can activate, preserving existing app data. Open title-bearing links or saved entries for Modland's
`Spectrum/Pro Tracker 3/Mr. Beeper/african.pt3` and ASMA's
`Composers/Rogers_Charles/Quest_For_Cool.sap`. Playback and Information should use ZXTune and
ASAP respectively while preserving the display titles. Replay from History and test audio
export on a browser with AAC support. Check phone handoffs with an explicit filename, absent
or empty filename fields and The Mod Archive's filename fragment. Preserve existing user data.
Page-message checks and real-file WASM rendering passed on the host. Private-window playback
feedback is recorded above; normal-session activation, Information and AAC export remain
unchecked. [Evidence and scope](WEB_DECODER_FILENAMES.md).

## B54 Floating controls — experimental, disabled by default

Practical phone operation was reported satisfactory on 2026-10-26, followed by approval to
retain the experiment in develop with **`FloatingControls.ENABLED = false`**. The exact installed
build and individual scenario outcomes were not separately supplied. Public availability is
not approved. With the constant false, the checks below require a deliberately enabled trial
build: ordinary builds expose no floating-controls action or permission flow. Merged as
**afab610**, matching checked candidate **0e9d361**: 54 focused checks / 7 suites, zero
failures/errors/skips, fresh lint 0 errors / 79 warnings, and independently inspected minified
Off build **1.4.0 (1315)**. The activation regression failed before the gate. The full suite was
not repeated, as requested. Default-off behaviour has host evidence, not a separate phone test;
overall enabled-trial acceptance does not individually confirm every scenario below.

[Contract and host evidence](FLOATING_CONTROLS.md). Retained enabled trial release:
**1.4.0 (1313), source 2497632**, recorded in [Status](STATUS.md); it adds whole-panel dragging,
edge docking, drop-to-hide and file holds while retaining Play/Pause hold activation. Fifty-one
focused checks, lint and independent APK inspection passed. The full suite was not repeated for this iteration, as requested; the initial
1195-test full pass belongs to source 9c73ad0. These are not phone acceptance. Test while parked.

1. Start Random and hold the dock's **Play/Pause** control. An ordinary tap must still only
   play/pause, with no new button or row in expanded Now Playing. Decline the explanation
   or the Android permission once: audio stays unchanged and refusal is explained. Try again,
   choose **Open settings**, enable **Display over other apps** for Protracktor, and return.
   Repeat with a rotation while Android settings is open; the pending activation should survive.
   If Android blocks the switch, check **Settings → Apps → Protracktor → ⋮ → Allow restricted
   settings**, if available, then retry **Display over other apps**. Record whether this resolves
   the reported refusal; the APK does not override system restrictions.
2. Open actual Google Maps. The four controls should remain above it, with Maps usable outside
   their bounds and no Move/Hide buttons. Drag from the background and from every control,
   including a disabled one. It should follow the finger without executing a click or file hold.
   Release on either half of the screen: animate to that edge and retain height. Start another
   drag during that animation; it must follow from the position actually touched, without jumping.
3. Check Previous, Play/Pause and Next against the same source/manual queue as the dock. During
   a slow load, Stop cancels that load. Pause must retain the panel for a later Play.
   Hold Next/Previous on a multi-subsong file: skip files exactly as in the dock, once per hold.
   Where only a subsong transition is available, the hold must not become a normal tap.
4. Press **Keep** twice quickly: the heart stays in its slot, no accidental Next occurs, and
   saving completes to the selected playlist. Hide, return to the playlist, restart the app and
   check persistence. A filled heart must not remove a track.
5. During a drag, a bottom **× Hide** target appears. Moving into it changes its ring/size/caption
   and gives haptic feedback; dropping there hides the panel while audio continues. A downward
   move elsewhere, cancelling or adding a second finger must not hide it. The target disappears
   when the drag ends. Holding the dock's **Play/Pause** again also hides without pausing.
   Reopen with another hold: restore the last resting edge/height, rather than the drop location.
   Rotate with Maps visible, then hide/reopen in each orientation. Avoid clipping at cutouts,
   navigation/status bars and screen edges. Verify EN/PL and enlarged system text.
6. Lock/unlock the phone: no panel over the lock screen, and an explicitly enabled session can
   restore it after unlock. A previously hidden panel must stay hidden. End the playback service
   and restart the app: the panel must not silently reactivate or resume audio.
   Lock or rotate during a drag: no orphaned **× Hide** target or delayed accidental dismissal.
7. Revoke **Display over other apps** while the feature is active. The window must disappear;
   subsequent activation must explain the missing permission. Playback and ordinary notification
   controls remain usable. Some apps/system screens can suppress overlays by design.

Overall practical operation is accepted for the experiment; individual checklist outcomes remain
unchecked unless separately reported. Host renders preview the production panel, not its
composition over Google Maps or behaviour on an actual navigation device.

## B52 Louder normalization target — accepted and merged, 2026-10-27

The branch changes the target from -23 to -18 LUFS while preserving boost caps and sample-peak
protection. [Host measurements and limits](VOLUME_NORMALIZATION.md#louder-target-trial--2026-10-27-unmerged).
Clean source fbf2958 passed 1217 tests/full gate/lint and 54 sanitized native/DSP checks.
Minified test APK [1.4.0 (1330)](../dist/protracktor-release-1.4.0-1330-fbf2958-20261027-223550.apk)
was supplied. The overall listening result was accepted and merged as **641b875**; exact build
and individual checklist outcomes were not separately supplied.

1. Use the same output device, system-volume step, stereo mode and subsong. Enable **Volume
   normalization** in Settings; an existing preference is retained. Compare the same passages
   in Studium Fałszu (Jakim), Lotus 3 Space ninja and several quiet/loud Random selections.
2. Compare this APK's normalization Off and On over the same passages, and with the other player
   if available. Keep its processing settings consistent. Record the installed build,
   track/subsong and stereo mode. Earlier listening notes can provide the -23 LUFS baseline;
   this check does not require installing an older APK. Off preserves the original signal.
3. Listen for loudness jumps, distortion, changes to attacks, quiet intros and fade-outs.
   A higher target can increase first-play differences because quiet tracks still hit the
   partial +6 dB cap. Repeat after longer listening to check measurement reuse.
4. Toggle during playback, pause/resume, seek, select another subsong or stereo mode, and
   exercise the established Bluetooth transitions. Retain notification/navigation ducking.

Individual phone sound/interaction cases remain **unchecked** unless separately reported; the
general listening acceptance does not imply that the full checklist was executed.

## B52 Volume normalization — accepted and merged, 2026-10-23

[Contract and limits](VOLUME_NORMALIZATION.md). Phone feedback reports comparable perceived
loudness for Studium Fałszu (Jakim) and Lotus 3 Space ninja with normalization On, followed by
approval to keep the function on develop. Merged as **4aab46e**, with the same tree as checked
cleanup source **d68bfad**: 1163 tests / 152 suites, all companion checks and lint
(0 errors / 73 unchanged warnings). All 53 native/DSP cases pass; the unchanged native code
also passed ASan/UBSan at the review checkpoint.

The later minified [develop test APK](STATUS.md#latest-develop-test-apk--2026-10-23) includes
the final copy. It was built without repeating long tests or standalone lint, as requested;
compiled labels/help were inspected independently. Earlier **1.3.0 (1297), source 9b99bb4**
still uses **Volume normalization (experimental)**; the new APK drops that suffix. The phone
report did not identify an exact installed build or individually confirm the retained checklist.

1. In Settings, enable **Volume normalization**. Start with a modest device
   volume, then compare several quiet/loud tunes with Off and On. On can intentionally sound
   quieter overall; the aim is fewer track-to-track jumps, not maximum loudness.
2. Use Random and manual Queue entries, Next/Previous, a quiet intro followed by a loud passage,
   and a natural fade-out. Listen for distortion, pumping or unpleasant settling during an
   unknown first play. A partial measurement intentionally limits amplification; not every
   quiet file reaches the target on its first play.
3. Replay after longer listening and after restarting the app. Try different subsongs in the
   same file, including a short one and a file opening at a nonzero default. Their corrections
   must not bleed into each other; the initial indicator should identify the actual played tune.
4. Toggle while playing and paused; paused playback must stay paused. Try Original/Narrow/Mono,
   seek, repeat, and a notification/navigation interruption. Normalization must not cancel ducking.
5. Repeat the accepted BT transitions: playing to BT continues there; disconnect pauses;
   paused route changes stay silent. Listen for changes in trim or new stalls/crackles.
6. Confirm the preference survives restart and that the existing Queue-source return, dock and
   Settings controls remain usable. Normalization must not process exported audio; when exporting
   a played subsong, its selection should match the indicator. Web playback stays unchanged.
7. Switch Original/Narrow/Mono repeatedly while playing, including a quiet track: the gain
   transition must be smooth rather than jumping with the mix. Also select Mono/Narrow before
   starting a new short track, let it finish and replay it to exercise complete-profile reuse.
8. During a cold/slow load, cancel with Pause or immediately choose another track. The abandoned
   decoder must not start later or interrupt the newer track. Try subsong controls during loading;
   no half-prepared track should start under the previous track's metadata.

Final copy on a phone, per-case transition/loading behaviour, accessibility and CPU/battery
cost remain **unchecked** unless separately reported. The comparison supports integration,
not a claim of catalogue-wide equalization or exhaustive device coverage.

## Queue return-to-source — trial

Practical phone operation was reported satisfactory and integration approved on 2026-10-22.
Merged as **70e1a61**. The agent compared the merge tree with the accepted branch and application
sources with tested **b225ff9**; no new APK or full gate for unchanged sources. Individual
outcomes for every retained scenario below were not separately supplied.

[Contract and safeguards](QUEUE_SOURCE_NAVIGATION.md). Use minified release APK **1.3.0 (1290),
source b225ff9**, identified in [Status](STATUS.md), from `feature/queue-source-navigation`.
The forced full host gate passed 1147 tests / 149 suites, all companion checks and fresh lint
(0 errors / 72 unchanged warnings); host renders and independent artifact checks passed.
Overall practical phone operation is accepted. APK 1285 predates this feature.

1. Start Random, return to the playlist, open Queue and tap its source name. The remembered
   Random list/filter returns without rerolling, resuming a pause or interrupting audio. Back
   leads to Browse root, then playlist. Repeat with an unrelated Browse route visited in between.
2. Repeat from Search with a narrowed scope, filtered/paged History, an online author and a local
   subfolder. Restore the actual originating view/results, not just the main Browse section.
   Back follows that source's ordinary hierarchy; generic Browse still starts at root (C110).
3. Play playlist A, select B without starting it, then return to A via Queue. Context, current
   position, shuffle/repeat and pending requests are unchanged. With dirty edits in B, test
   Cancel, Save and Discard; Cancel keeps Queue open, Save completes before switching. A source
   order differing from disk returns as an unsaved draft without overwriting the saved playlist.
4. While a manual request plays, the source action still opens the underlying context. After
   Clear, or with an externally opened file without a source view, there is no misleading action.
   A deleted source playlist must explain unavailability and retain Queue/playback.
5. Compare normal-size header height and Clear/Help placement with the previous presentation.
   No new caption or row; long source names remain limited to two lines. At enlarged text,
   source and both actions must remain reachable; full action captions wrap and the row may
   grow vertically to fit them. Only the source name may ellipsize. Check EN/PL, narrow width
   and TalkBack's explicit source action label.
6. Recheck Queue Help/Clear/Undo, row Play/Info/swipes/reorder, dock controls and the C112 compact
   dock. Returning to a source must not consume manual requests or change Next/Previous order.

Host checks/renders are partial evidence only. Overall practical phone operation is accepted;
detailed scenario outcomes, enlarged-text/accessibility and TalkBack remain **unchecked
individually** unless separately reported. Retain this checklist for regression and longer-term use.

## C119 nested Modland downloads — accepted trial

Practical phone playback was reported satisfactory and integration approved on 2026-10-22.
Merged as **0a7bcc0**; the agent checked the merge tree against the accepted branch and
application sources against tested **657f4da**. No new APK or full gate for unchanged sources.
Individual outcomes for every retained scenario below were not separately supplied.

[Cause and bounded recovery](MODLAND_LEGACY_DOWNLOADS.md). Use minified release APK **1.3.0
(1285), source 657f4da**, identified in [Status](STATUS.md), from
`fix/modland-nested-legacy-paths`. The forced full gate passed **1119 tests / 146 suites**, all
companion checks and fresh lint **0 errors / 72 warnings**. C119 phone acceptance is recorded
separately above; it is not inferred from original C109 acceptance, host HTTP/decoder checks
or APK inspection.

1. Without refreshing the existing Modland index, select both reported old references:
   `Ad Lib/Screamtracker 3 AdLib/- unknown/(opl2) totally nuts.s3m` and
   `Video Game Music/Sega Megadrive/- unknown/World Championship Soccer/06 ending theme.vgz`.
   They should download and start, without a connection-error notice. If the current index no
   longer exposes `- unknown`, use an already saved playlist/History reference instead; playing
   only the new `_unknown` address does not exercise recovery.
2. Replay a recovered file, including after restarting the app and with the network disabled.
   Its existing saved-list membership and cache availability should remain intact. Do not clear
   data or refresh the index merely to exercise this fix.
3. Recheck Conflict Europe YM, Same Game SPC, an ordinary Modland download and an UnExoticA
   download. A genuinely unavailable file must still report failure. Current `_unknown`
   references must continue working directly.
4. Check that the accepted compact dock, next/previous and Queue behave as before; this change
   touches download recovery, not presentation or playback-context rules.

Overall practical phone playback is accepted. Detailed scenario results remain **unchecked
individually** unless supplied separately; keep this checklist for regression and longer-term use.

## C112 two-row dock — accepted trial

Positive phone feedback after the corrected APK handoff and integration approval were received
on 2026-10-22; merged as **e90c817**. This is practical trial acceptance, not individual outcomes
for every retained scenario below. No new APK or full gate was run for the unchanged merge tree.

[Presentation decision and verification](STATUS.md#c112-dock-subsong-count-crowds-now-playing-text).
Use corrected minified release APK **1.3.0 (1281), source 0b9169b**. The forced full gate passed
1113 tests / 145 suites, all companion checks and fresh lint (0 errors / 72 warnings).
The first trial **1.3.0 (1279), source bb7a75b** was not accepted because it increased height and
changed text margins; its 1111-test host gate did not protect those proportions. The new render
was inspected beside the original and unchanged dock regions compared pixel-by-pixel. Overall
phone operation is accepted; detailed TalkBack, large-font and touch-target outcomes were not
separately reported.

1. Compare the dock's overall height and title/metadata spacing with the retained pre-change
   screenshot, rather than downgrading the installed app or clearing its data. The normal-size
   card must remain 60 dp with the original 8 dp text edge insets, not 76 dp.
   Compare one-subsong and multi-subsong files, including
   `Impulsetracker/Treks/battle of honour.it`. Title is top-left, metadata bottom-left,
   Keep top-right and the full tune-position text bottom-right. Keep and Queue do not move
   horizontally as the tune count changes. Queue remains outside the card, at the right.
2. Tap Keep: retain the previous save-to-playlist behaviour without opening Now Playing. Tap
   title, metadata or tune count: open Now Playing and its existing subsong selector. Tap Queue:
   open Queue only. Tap just below Keep and across the tune label: these must open the player,
   not accidentally save through an expanded Keep hit target.
3. Return to playback from the selected playlist, where Keep is absent. Title width, card height
   and Queue placement should stay stable. Recheck idle/loading and a long title/subtitle.
4. Check EN/PL, a narrow viewport and enlarged system text. The card may grow vertically; at
   accessibility sizes the footer may wrap, keeping the counter end-aligned instead of hiding
   metadata. Text and the tune label must not overlap or clip. The compact Keep slot is 48 x
   32 dp with the same 24 dp icon; judge its smaller vertical target honestly on the phone.
   Verify TalkBack can distinguish opening the player, keeping a track and opening Queue.
5. Seek, play/pause, previous/next (including file-skip holds), shuffle and repeat remain in their
   current places. Recheck Queue's usable space above the dock and real scrolling/tap behaviour.

Host geometry and screenshots alone do not establish phone appearance or touch feel. Practical
phone acceptance is reported separately above; retain unreported cases for regression checks.

## C109/C111 download and Browse alignment — accepted trial

The two fixes were accepted and merged in order on 2026-10-21: C109 **122368f**, then C111
**03dfc1d**. Shared minified release APK **1.3.0 (1273), source d370ea2** covers both;
its [artifact and checks](STATUS.md#c109c111--accepted-and-merged-2026-10-21) are recorded.
Playback is satisfactory so far; the remaining slight layout movement is accepted. Individual
checklist outcomes below were not separately supplied. No new APK or gate was run for merging.
[C109 mechanism and detailed checks](MODLAND_LEGACY_DOWNLOADS.md);
[C111 measurements](STATUS.md#c111-add-music-and-online-catalogues-have-inconsistent-padding).

1. Before refreshing Modland, play `YM/- unknown/conflict europe.ym` from the existing index,
   saved playlist or History. It should download and play, without changing saved membership.
   Replay it to exercise the cache. Try the reported Same Game SPC legacy reference too.
2. Check an ordinary Modland entry and an UnExoticA download. A genuinely missing file must
   still report failure; the recovery is not a general network or decoder workaround.
3. Repeatedly move between Add music and Online catalogues. Compare the leading icons and
   text starts, including missing/downloaded catalogues, an online-only catalogue, Song metadata
   and Replay routines. Content should not shift sideways during navigation or state updates.
4. Try EN/PL and enlarged system text. Online descriptions/warnings may make rows taller, but
   must remain readable with their existing download/refresh actions. Root row heights and
   Random scope changes should behave as before.

HTTP/decoder probes and rendered host geometry remain host evidence; the practical phone trial is
accepted. Detailed accessibility, archive/cache permutations and long-term coverage stay unverified.

## C118 History freshness — new report, not corrected

Keep History open while playback creates an entry eligible for recording. The reported behaviour
is that it appears only after leaving History and opening it again. Exact playback origin, query,
page and installed build were not supplied; no agent-run reproduction is claimed. Before a fix,
reconcile live updates with A56's rule that plays originating from History do not record/reorder
themselves. A future regression check must also preserve filtering, paging, scroll stability and
the current playback context. [Report](STATUS.md#c118-history-does-not-show-newly-recorded-plays-while-the-view-remains-open).

## Prepared release 1.3.0 — Play-generated install not yet confirmed

AAB **1.3.0 (1269)** is prepared from `v1.3.0` / `d456fa7`. Its app/native/web/script sources
match the accepted Queue candidate below; the release Gradle change is versionName only.
Overall Queue trial acceptance does not establish installation or testing of Google's generated
APK, upload/rollout or production access. Keep the queue, navigation, output and data-preservation
scenarios below for the store-delivered build. [Release evidence and limits](RELEASE_NOTES_1.3.0.md).

## Manual queue — overall trial accepted and merged, 2026-10-21

Positive phone testing was reported and integration approved; merged as **3bc5a98**.
Latest supplied APK: **1.2.0 (1261), source 1a1503f**, unchanged. This is overall acceptance,
not individual confirmation of every scenario below or a separate installed build-ID report.
Keep unreported accessibility, notification/Bluetooth, timing and long-term checks as regression
scenarios. Earlier handoff states below are historical. [Integration record](QUEUE_BRANCH_REVIEW.md#acceptance-and-integration--2026-10-21).

## Queue follow and branch audit — phone checks pending

[C116](QUEUE_AUTO_FOLLOW.md) and [the complete branch review](QUEUE_BRANCH_REVIEW.md) are included
in the accepted integration. Use release APK **1.2.0 (1261), source 1a1503f**, identified in [Status](STATUS.md);
APK 1258 predates these corrections. The focused run passed 218 tests / 14 suites, followed by
1096 tests / 142 suites and all forced gates/lint. Overall phone acceptance is recorded above;
individual checklist results were not separately supplied.

1. With a long Queue open, repeatedly press Next until it scrolls at the lower edge. Surviving
   rows must remain visible throughout, without a whole-list flash/fade. Try several rapid Next
   presses, natural completion and Previous near the upper edge. The actual current row should
   end fully visible above the dock. Repeat with Random and manual inserts.
2. Consumed/removed manual rows should still fade and close the gap smoothly. New rows appear
   immediately, without fading the rest of the list. Retest Clear/Undo, full-width highlighting,
   saved-playlist drag, bounded manual grips and vertical-versus-horizontal intent below.
3. With shuffle enabled on a three-track source, choose C, choose B repeatedly inside Queue,
   then choose A. Swipe-left B from this session. Previous must still reach C; Next must cross
   the omitted B history and return to A. The saved playlist must retain all three tracks.
4. Hold a Queue row for Info while progress is updating. Check haptic feedback, no accidental
   Play and EN/PL Help wording for manual requests both inside a context and after Clear.

No merge acceptance is inferred from host results or installation alone.

## Queue row and playlist drag corrections — phone checks pending

[C113–C115](QUEUE_ROW_REGRESSIONS.md) extend the unmerged branch. Their historical handoff was
**1.2.0 (1258), source aca3cd6**, with 1090 host tests/full gates/lint passed. Rerun these
regressions on the current APK 1261 above; APK 1255 is the originally reported predecessor.

1. Clear while a source or manual track is current. With no source rows, the retained current
   and newly queued tracks align normally, without a group inset. Undo restores the source and
   its indented manual block, without a playback/seek change.
2. A current manual row inside a context highlights its whole width, including the inset. Tap
   and hold that margin: it belongs to the row (Play/Info respectively), not a dead gutter.
3. On the saved playlist, grab the first row and move it down across several rows. It stays
   under the finger and above neighbours; the viewport must not follow its key back to the top.
   Repeat upward, from the middle, with a partially scrolled list and with music playing.
4. Begin with a small grip movement, pause, continue across a cell, pause and continue again.
   Try a fast burst across several cells. The same row must remain held, with no offset left
   behind after release/cancel. Cancel a partial move, then grab again and try a row right-swipe.
5. Reordering changes only the playlist draft: no unexpected Play, queue addition or file edit.
   Check Save/Revert, current-track identity, selection, row menus/Info, normal vertical scrolling
   and right-add gestures. Rerun bounded manual-queue grip/edge-scroll checks below.
6. Check release performance, long lists, EN/PL, large text and TalkBack on the actual phone.

Host gesture/render tests are partial evidence; device timing, feel and regression acceptance
remain unchecked until owner confirmation. No merge is authorized.

## Clear queue — phone checks pending

The [Clear queue follow-up](QUEUE_CLEAR.md) is unmerged. Its historical handoff was release APK
**1.2.0 (1255), source 1aec1aa**, with 1080 host tests/full gates and lint passed. Use the current
APK 1261 identified above for these checks; real audio/device behaviour is unchecked.

1. Play a playlist, Search, Browse, History, Random or external file, with several pending manual
   requests. Open Queue and press Clear queue: only the actual current track remains, with no
   audible break, seek/reset, metadata loss or position jump. Repeat while paused and loading.
2. Add B, C, B after Clear on A. Next/completion plays B, C, B, consuming the preceding one-shot
   each time, then stops. Previous must not revive A or the old context. Repeat All cannot revive
   them; Repeat One still holds the current track until explicit Next.
3. Clear during a slow Random draw or network open. No delayed callback may restore the source.
   Reopening Random or the playlist only changes navigation; explicit row Play can select it again.
   After failed loading, retry the retained current track; it must not retry an older occurrence.
   Clear/Undo during the very first Random draw, before any pick: Undo stays idle, then explicit
   Play must draw or explain the lack of indexed files, not silently do nothing.
4. Press Undo before advancing: restore source/pending order without rewinding or unpausing audio.
   After an advance, completion, enqueue or linked playlist edit, old Undo must not replace new
   work. A visible expired action explains it; an already-clear action gives feedback.
5. Exhaust the manual-only list, add new requests and press Play: start the new first request,
   not the finished previous track. Try duplicates, swipe/remove/Undo and bounded grip reorder.
6. Saved playlists, dirty draft, History and files must remain intact. Check EN/PL header labels,
   small screens, large fonts, TalkBack, dock, notifications and Bluetooth transport.

Host tests and compilation are partial evidence, not real audio or device acceptance.

## Scroll-first row gestures — phone checks pending

The [scroll-first follow-up](QUEUE_SWIPE_INTENT.md) is unmerged. Its original APK was **1.2.0 (1252),
source f6a5ee4**, identified in [Status](STATUS.md). It passed 1056 host tests/full gates and lint;
APK 1250 is the previous handoff, not proof of this change.

1. In a long Queue, scroll up/down with slight sideways wobble and diagonal starts. Rows should
   stay still horizontally; no green/red reveal, manual addition, removal, playback or Info.
   Repeat on Playlist, Random and Browse/Search/History track rows.
2. Move a little horizontally then continue vertically; vertical scrolling should take priority.
   Start vertically then drift sideways farther: the same gesture must not turn into a swipe.
3. Perform clear horizontal swipes in both queue directions, including a small vertical drift.
   After a deliberate start, reveal the action, commit once on release and retain Undo. Partial,
   reversed or cancelled gestures must not enqueue/remove or accidentally play. A second finger
   on the same row should cancel its swipe.
4. Outside Queue, unsupported left swipes must not play a track. Normal taps and holds must still
   work afterwards; test Info haptics and the bounded manual grip drag/edge scroll as before.
5. Try quick flicks, slow scrolling and one-handed use on the phone. The proposed start distance
   is max(24 dp, twice platform touch slop), with more than 2:1 horizontal dominance. Real-world
   comfort needs owner testing; host gesture checks do not prove it.

Clear queue is not part of APK 1252; the later owner-approved follow-up is described above.

## Queue presentation polish — phone checks pending

The [presentation follow-up](QUEUE_PRESENTATION_POLISH.md) is unmerged. Its original APK was **1.2.0
(1250), source 6043902**, identified in [Status](STATUS.md). It passed 1043 host tests/full gates
and lint; APK 1248 is the previous handoff, not proof of the following changes.

1. Open Queue with several manual requests, including duplicates. Next and natural completion
   should fade the departing row and smoothly close the gap, including when the current row is
   at the top. Playback must advance immediately, without waiting for the animation. Repeat on
   the final manual request and with rapid Next presses; no ghost rows should accept gestures.
2. Swipe-left removal/red reveal/Undo and right enqueue/green reveal must still work. Restoring
   a request must not create another occurrence. Drag a grip across several waiting rows and
   near the screen edge: stay inside the indented block, without competing movement or swipe.
3. Hold a source, current manual and waiting manual row: one clear haptic response and Info, not
   playback. A grip hold must not open Info. Disable system haptics: Info still opens without
   forcing vibration. Real vibration and TalkBack behaviour have not been verified by the agent.
4. The source header should show a compact labelled Help action, not permanent instructions.
   Open/close it without changing audio or the scroll position. Check EN/PL, long source names,
   large text and a small display; all four gesture sections must remain readable by scrolling.
5. With Android animations disabled, list changes should settle immediately, without a stuck
   gap, retained row or blocked input. Check dock transport, Back and reopening Queue as before.

Host frame/pixel tests are partial evidence, not device acceptance.

## Manual-block reorder and long-press Info — phone checks pending

Use the follow-up APK identified in [Status](STATUS.md), not the earlier code-1245 artifact.
The branch remains unmerged. [Contract and host evidence](QUEUE_MANUAL_REORDER.md).

1. While a manual track is current, queue at least three requests, including duplicates. Drag the
   last grip to first and back across several rows. Only waiting requests move inside the indented
   block; source and current rows have no grip. Current music/position/metadata do not change.
   Next follows the reordered requests, consuming each once, then resumes the original context.
2. Drag well past both block boundaries and try sideways/diagonal motion from the grip. It must
   not cross source/current rows, enqueue/remove a request, play it or open Info. Holding the grip
   still must not reorder automatically. Hold a row away from the grip: Info opens, not playback.
   Tap still plays; swipe on the row body and normal vertical scrolling retain their behaviour.
3. With more pending rows than fit, drag toward either viewport edge and hold after moving. The
   block should scroll to permit further ordering. Release/cancel leaves a settled row without a
   stuck offset. Exercise rapid direction changes and both duplicate occurrences independently.
4. Press Next or change source while holding a grip; also test enqueue/remove during dragging.
   The drag cancels safely if membership/layout generation changes, never affecting another
   request. Source replacement retains the resulting pending order; mere navigation changes none.
5. Remove a pending row, reorder survivors, then Undo: restore only the removed occurrence near
   surviving neighbours, without restarting audio. Saved playlists/History/index/files must not
   acquire reorder edits. History-origin requests keep their playback-recording policy.
6. TalkBack exposes labelled Info and valid Move earlier/Move later actions; boundary actions are
   absent, and a lone waiting request has no grip. Check EN/PL, large text, portrait/landscape and
   the dock/snackbar touch area. Recheck shuffle/repeat/subsongs and the unified-list scenarios below.

Report installed version/code/SHA and expected/observed results. All real device behaviour remains
unchecked until owner testing; host gestures and compilation do not establish phone usability.

## Unified queue — third iteration, phone checks pending

Use the new third-iteration APK identified in [Status](STATUS.md), not historical APK 1239.
Branch `feature/manual-playback-queue` remains unmerged. Host UI/controller tests do not prove
device sound, gestures, layout, notification/service behaviour or usability. Install without
uninstalling/clearing data; older AMP APK cannot ordinarily replace a higher-code build.

1. Play B from A/B/C/D. Right-swipe X, Y and X from other track lists. Nothing starts yet; Queue
   shows A/B, the marked/indented X/Y/X block, then C/D. Next plays each manual occurrence FIFO,
   removing each only when left/completed; after the last, play C. Previous goes to B, never to a
   consumed manual row. While X plays, Previous returns to B and consumes X; Y/X remain pending.
2. Add copies during another manual track; append after older requests. Tap a later pending row:
   play only that occurrence, retain earlier requests next. Tapping the current row restarts it.
   Equal file IDs must not produce two playing highlights or disappear together.
3. One unified panel slides above the same operable dock. No duplicate Playback/current-song
   block or Context/Queue tabs. Play/Pause, Next, Queue toggle and Now Playing must work physically.
   Back closes only the panel; reopening starts with the current occurrence as high as possible.
4. Left-swipe either source/manual row: red reveal with remove text/icon, one removal on release,
   reachable Undo above dock. Right-swipe: green reveal with add text/icon, one manual copy at the
   FIFO end; original row returns to rest. Partial/reversed/vertical drags must not enqueue/remove.
   Tap plays; follow-up long-press opens Info, with grips only on waiting manual rows. No visible
   Play/Remove row buttons. Try rapid gestures.
5. Remove current while playing/loading: advance to pending before source, stop if exhausted.
   Remove current while paused: select its successor but stay silent until Play; no decoder/History
   write merely from editing. Undo restores content only, not the previous audio position.
6. Remove source C; Next and source Repeat All skip it. Saved playlist/History/index/files remain
   unchanged. Undo restores only C. A stale Undo/action after a new context must not affect it.
   Select another editor playlist, edit/save/discard/empty and switch back while manual audio plays:
   no manual rows or temporary omissions are persisted and no removed source content is resurrected.
7. Test right enqueue on Playlist, Random, Search, History, author, local folder and online file
   rows. Folder/header rows must not enqueue. Selection/drag reorder retain their existing gestures;
   menus and TalkBack custom actions offer accessible equivalents. Test EN/PL, large text and RTL.
8. Change source with pending X/Y: the newly tapped N3 plays now, then X/Y, then N4. Mere navigation
   or selecting another playlist does not transfer playback. Evaluate this provisional precedence.
9. Shuffle changes source order only, not manual FIFO; the panel reflects actual source order.
   Repeat One retains actual manual tune and explains waiting requests; file Next escapes it.
   Test short natural completion and multi-subsong files, Play all subsongs on/off and file transport.
10. Random: heard rows only, manual block after cursor; Previous skips consumed requests and omitted
    occurrences. Equal Random files retain separate IDs when pruning its old menu record. Change
    filter while rewound: heard suffix remains, new draws use the new filter only past its boundary.
    Exhausted Random must not keep a consumed manual row. Reopen remembered Random without stealing
    author playback; only double-tap dice resets its record and source omissions.
11. Unavailable manual file over Random stays retryable rather than auto-skipping. Skip it, rapidly
    change sources/retry during slow reads, pause during loading, then test offline/revoked grants
    and Bluetooth. Retired requests must never start later. History origin/learned titles/listened
    marks, notification heart, Keep and sharing must use the actual manual track, not parked source.
12. C110 regression: History → Browse → playlist → Browse opens root; repeat Search, Random,
    online author/local folder, Settings and explicit author jumps. Process death loses this
    temporary queue/omissions, restoring the selected playlist paused. Retained AMP data survives
    and stays unavailable here. C111 padding and C112 cramped dock count are explicitly deferred.

Report installed code/SHA, steps and expected/observed result. Product choices/questions are in
[the current contract](QUEUE_UNIFIED_LIST.md); no merge before the owner's acceptance.

## Manual queue — historical second-iteration phone checklist

Superseded by the unified-list checklist above after owner/app-user feedback on 2026-10-20.
The following preserves the old APK's expectations, not instructions for the new implementation.

Branch `feature/manual-playback-queue`. Use the final **second-iteration** artifact identified in
[the implementation report](MANUAL_QUEUE_IMPLEMENTATION.md), not the first checkpoint APK.
All scenarios below are **unchecked on a phone**. Install over the existing test-signed build;
do not uninstall or clear storage. VersionName/schema are unchanged; commit-derived versionCode
does not permit ordinary replacement by the older AMP APK.
Final second-iteration candidate: **1.2.0 (1239)**, source `3d27868`,
`dist/protracktor-release-1.2.0-1239-3d27868-20261020-001402.apk` (1013 host tests; lint 0 errors).

1. Play source A/B/C/D at B. Add X, Y and Z from different views: B continues uninterrupted,
   then file Next/completion gives X/Y/Z/C/D. Merely browsing or selecting another editor playlist
   must not change this. Test screen, notification and headset transport.
2. Add the same file twice. Queue shows two occurrences. Play the second pending row explicitly:
   only that occurrence becomes current; earlier pending remains. Header/dock/notification refer
   to the actual manual file, while Context keeps a quiet source position, even for equal file IDs.
3. During Y, Previous returns to X/B; without new requests Next replays the forward record.
   Add P/Q while back at X: Next plays P/Q before the retained Y/C suffix. No source rows disappear.
4. Enqueue while paused: remain paused. Ordinary Play resumes that track; file Next selects a
   request. From idle with a never-started selected playlist, enqueue then Play: drain requests
   and stop, without automatically starting that playlist.
5. Swipe a pending row end-to-start; only it disappears and Undo is reachable **inside** the panel.
   Undo restores its position among surviving neighbours, not a duplicate. Check labelled Remove,
   rapid repeated actions, vertical scrolling, large text, TalkBack, EN/PL and unrelated playlist Undo.
6. Shuffle affects source continuation only. Repeat All repeats source, not consumed requests.
   Repeat One keeps actual tune repeating with a waiting explanation; file Next escapes to FIFO.
   Test multi-subsong files with Play all subsongs both on/off and long-press file transport.
7. Explicitly play a different source row or reroll Random: pending survives, but old mixed
   traversal is reset. Reopening remembered Random must not resume or steal author playback.
   Change Random filter while rewound: retain the heard boundary and new-filter speculation rules.
8. Edit/delete/empty/rename the parked source while a manual file plays: do not interrupt it or
   resurrect old editor content during Previous/Next. Learning titles must not move duplicate rows.
9. Enqueue an unavailable file over Random. Failure stays visible for retry/Next, not silently
   skipped as a failed random pick. Test rapid source changes/retries during slow loading, revoked
   local grants, offline catalogue downloads, pause during loading and Bluetooth transitions.
10. History-origin requests must not move their History row to the top; requests from other views
    should. Short-completion/five-second listened marks, notification heart, Keep and sharing must
    use the actual manual file, not the parked source. Retained AMP entries stay protected.
11. Process death loses this session-only pending/traversal; normal paused selected-playlist
    restoration remains. Recheck C110 generic Browse-root entry and normal playlist Save/Undo.

Report installed code/SHA, steps and expected/observed result. Host tests do not certify audio,
service/notification layout, gestures or usability. Product questions remain in
[the contract](QUEUE_CONTEXT_ANALYSIS.md#questions-retained-for-post-implementation-evaluation).

## C110 — Browse root navigation

Branch `fix/browse-root-navigation`; APK **1.2.0 (1231)**, source `fa09e7a`. Artifact and host evidence are in
[the handoff](https://github.com/przunk/protracktor/blob/2c35319cfa8b665398a36141248b45544bd68ebe/docs/PR_C110_BROWSE_ROOT_NAVIGATION.md). The owner accepted the correction on 2026-10-19;
merged as `7e7f242`. Individual results for every scenario below were not separately supplied;
retain this as a regression checklist, not a claim that all items were independently certified.
Install over the existing test-signed build without uninstalling or clearing data.

1. Play History. Toolbar Back to Browse, system Back to the playlist, then Browse: the main
   Browse view opens, not History. Music, position and the active context remain unchanged.
2. Repeat with Search (also with a platform/catalogue scope), an online author's folder and a
   scanned local folder. Back should move one level; every fresh generic Browse opens the root.
   Repeat entry/exit several times, including during a slow read; no stale folder should reappear.
3. Repeat while paused. Navigation must not resume, stop, replace or rewind the current track.
   Next/Previous after returning still follow the original context, not the selected playlist.
4. Play Random and repeat both toolbar and system Back: Random → Browse → playlist. Enter Browse:
   root first. Open Random: the remembered list is retained, without rerolling or resuming a paused
   session. Repeat after playing an author from Random; navigation must not steal author transport.
5. Explicit More from this author must still open that author's folder rather than the root.
   Back returns to the place the jump came from, including Search/Random. A subsequent generic
   Browse still opens root. Check saved-place opening/Back and the read-only context sheet too.

Report the installed build ID, steps and expected/observed result. Host navigation with preset
playback does not prove device audio output, notification/service behaviour or gestures.

## QUEUE — accepted and merged; detailed regression checklist retained, 2026-10-19

The owner confirmed the intended Queue behaviour and authorized integration on 2026-10-19.
Individual checklist results and the installed build ID were not separately supplied; keep this
list for regression testing rather than treating every item as independently certified.
Merged into develop as `7cab210`, with a tree equal to tested source `069b238` (956 host tests;
lint 0 errors/72 warnings). Accepted baseline's minified normal-code APK:
`dist/protracktor-release-1.2.0-1228-7cab210-20261019-220200.apk`.
The artifact/signature/version are verified. The owner reported startup, not individual results
for the detailed phone checklist below.
Previous code-1221 artifacts remain historical. Installing this higher-code build prevents ordinary
replacement by the old code-1221 AMP APK. AMP integration is deferred. Do not uninstall or clear storage.

1. On AMP, retain an indexed/cached track, bookmark, History row and saved playlist. Install QUEUE:
   those records must not be deleted. AMP is unavailable here and must not appear in Search or
   Random; explicitly playing its retained reference must explain the limitation.
2. Play playlist A, select playlist B. A keeps sounding; position/metadata, notification and
   Next/Previous still belong to A. B has no playing selection. Save/edit B without changing A.
3. Queue is a separate labelled action at the far right, outside the expandable Now Playing card.
   Tap it: the full-height sheet opens, not Now Playing. With A's cursor far down the list, its
   current row appears at the top (or as high as the trailing boundary allows). Scroll away,
   dismiss, change track and reopen: the new current row is shown immediately. Play another row:
   A's position changes, B remains unchanged. View A to see its selection.
4. Play a row of B: ownership and transport now belong to B. No automatic start on mere selection.
5. Play from Search, return to the playlist, enter Settings or edit a new search. Audio and Next
   remain with the captured results until another row is explicitly played.
6. Repeat with an online author/local folder and filtered History. Learning titles must not reorder
   the queue. History-origin playback must not move History entries to the top.
7. First entry into Random starts a session. After several draws, return to the playlist and
   reopen Random: same record/current track, no reset or restart. Reopening while paused stays paused.
   Check both the toolbar arrow and system Back: Random returns to Browse, then Browse returns
   to the playlist. The explicit playlist shortcut still goes directly to the playlist. Back from
   an author opened over Random first returns to Random; Settings also closes before Random.
8. From Random open an author: merely looking keeps Random transport. Play an author row, then
   return/reopen Random: author music and Next/Previous continue. Only selecting a Random row
   transfers playback back to its remembered record. No Resume Random button.
9. Single-tap the dice: no reset. Double-tap: one new list/first selection, visible throw/rotation,
   same filter, global History intact. Check persistent EN/PL help and accessibility activation.
10. Rewind within the heard Random list, change filter, Next through the remaining heard entries:
    new picks from the new filter appear only beyond that boundary. Check repeated/rapid Next.
11. Queue row menus permit information but no Remove, drag or selection editing. Ordinary playlist
    editing/Undo, saved places and notification heart still work; the heart saves to the selected list.
12. Test repeat/shuffle/subsongs, headset/notification controls and navigation during slow loading.
    A retired Random request must not start after playing another source. After process death the
    existing selected-playlist restoration is paused; temporary queue restoration is not included.
13. Historical interchange check, only for the original code-1221 APK pair: reinstall AMP and
    verify its index, cache, playlist, History and bookmark. Do not attempt that downgrade after
    installing a new higher-code QUEUE/develop build; no data-destructive workaround is selected.

Report installed artifact/SHA, steps and expected/observed results. Compilation, host UI tests
and signing checks do not establish device audio/gesture behaviour. [Experiment](QUEUE_EXPERIMENT.md).

*Started 2026-09-15, because a build was handed over with a fix in it that no check in this
repository can reach.*

The suite covers what a machine can decide: unit/UI-host tests, web checks and native checks.
Current counts and durations are recorded in [Status](STATUS.md). **None of them has a phone.**
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

## Browse/download stability trial — 2026-10-16

Branch `fix/browse-download-stability`; APK **1.2.0 (1214)**, source `f32e6cc`.
Merged into develop on 2026-10-18 at the owner's request; this is not phone acceptance.
Exact identity and successful 929-test/lint host results are recorded in
[the handoff](https://github.com/przunk/protracktor/blob/2c35319cfa8b665398a36141248b45544bd68ebe/docs/PR_BROWSE_DOWNLOAD_STABILITY.md). **All scenarios below are unchecked on a phone.**
Use existing disposable/saved test playlists; do not uninstall or clear app data.

1. In an indexed catalogue, quickly open different formats/authors, press Back during loading,
   and reopen the same location. Final rows must match the final heading; an older list must
   not replace them and loading must finish normally.
2. During a folder read, enter another domain (Search, History, Local or Saved places). Revisit
   Online catalogues. Old folder rows must not appear under another domain or at the catalogue root.
3. Open a previously scanned local grant, close/reopen it and switch to another grant while it
   loads. Confirm the final root, subfolders and files belong to the selected grant; walk Back
   through subfolders. No new scan should start merely from opening a grant.
4. Use cached/indexed views where parent reads finish quickly. Back from an author must show
   its format's authors, then the catalogue's formats, without a blank/stuck-loading parent.
5. Open an author's folder from Search and Random; leave quickly or return to its waiting session.
   Test the same via a bookmark and return to Saved places. No late jump should reopen a dismissed
   folder or change the transport's source. Existing session/playlist return rules remain unchanged.
6. Exercise Random Next/Previous, a filter change while replaying history, and switching to a
   playlist. Heard history, current selection, notification heart and transport must remain coherent.
7. With folder prefetch enabled on an allowed network, play a row while downloads are running;
   navigate away/back and repeat. Confirm complete tracks play and cached markers settle. Then
   enable airplane mode and replay already fetched test files. Cache may evict under its normal
   limit; permanent archives must remain available. Do not manufacture a full disk on the phone.
8. Optionally clear only fetched music via the existing storage control while a fetch is active.
   No crash/corrupt partial track; an in-flight shared transfer may finish and repopulate cache,
   which is existing behaviour. Playlists, bookmarks, History and permanent archives must survive.

Controlled host gates establish the exact adverse orderings; rapid phone navigation alone does
not prove every race occurred. Record APK/build, device/Android version, scenarios and outcomes.

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

## Version-history reconciliation — validated and promoted, 2026-10-15

The owner approved normalizing unpublished test-version bumps back to release baseline 1.1.0.
63 old/new commit pairs pass tree, metadata and ordered-parent checks; commit count stays 1193.
Three deliberate verifier mutations are rejected. The forced candidate full gate on `912122c`
passed 910 tests/121 suites, zero failures/errors/skips, in 485 s; lint zero errors/72 warnings.
All auxiliary checks passed too. Backup import/fsck and saved artifact checksums passed.
Guarded promotion succeeded at `f0ca1a9`: nine local refs changed, one remained identical;
master/tags/remote refs and other worktrees were preserved. Later closeout is documentation-only,
with application/build source matching tested `912122c`. The rewrite retained 1193 commits; three
ordinary audit/documentation commits bring the final count to 1196. No release procedure was run.
This is not a new phone test or a new APK. Dated records below retain the actual original
artifact versions and SHA values; see the [rewrite map and provenance](VERSION_HISTORY_REWRITE.md).

## C108 database compatibility guard — integration approved, 2026-10-15

After the trial handoff the owner authorized integration; merged at `8f3ef56`. Its tracked tree
matches approved branch `83468f4`; app/build sources match the APK and full gate below. Verified
by source comparison, not another test/lint run or APK build. Device/Android, exact installed
build and individual scenario results were not separately supplied. In particular, approval does
not establish a newer-schema refusal test on a phone. Retain the checklist below for regression
work; do not treat each item as confirmed or as agent-run device evidence.

Trial: **1.4.1 (1190)**, clean source `2fe0311`,
`dist/protracktor-release-1.4.1-1190-2fe0311-20261015-213009.apk`.
Forced full gate: 910 tests/121 suites, zero failures/errors/skips; lint zero errors/72 warnings.
Minified release artifact and test signature checked, not phone behaviour.

Code finding selected by the owner, not independent tester feedback. Host-only native SQLite
fixtures cover newer schema refusal/preservation and later compatible reopen; real Activity,
Retry/Close and service entry checks also run on the host, not a phone.
[Analysis and evidence](https://github.com/przunk/protracktor/blob/2c35319cfa8b665398a36141248b45544bd68ebe/docs/PR_C108_PRESERVE_NEWER_DATABASE.md).

For the release-APK phone trial, retain existing data and use the normal schema-23 installation:
check startup, playlist contents, History search, bookmarks, external file opening and playback/
notification/Bluetooth after the startup gate. No unexpected reset, error screen or interruption.
Do not alter the phone's database version or install an old destructive binary to create a test.
Unsupported-schema screen timing/layout on a phone remains unverified without an isolated fixture;
normal use of this APK alone cannot prove that refusal path. Record device/build and observations.

## B49 live History search — overall owner acceptance, 2026-10-15

After the trial handoff the owner reported satisfactory operation and authorized merging at
`ef8cd39`. The merged tree matches accepted branch `1820c9b`; application/build sources match the
APK and full gate below. Source comparison was run, not a new full gate or APK build. Device,
Android, exact installed build and individual checklist outcomes were not separately supplied.
This is overall owner acceptance; retain the following scenarios for future regression checks,
without treating them as individually confirmed or agent-run phone tests.

Trial: **1.4.0 (1185)**, clean source `e4f5ccc`,
`dist/protracktor-release-1.4.0-1185-e4f5ccc-20261015-201306.apk`.
Full forced gate: 897 tests/119 suites, zero failures/errors/skips; lint zero errors/72 warnings.
Artifact/signature checks passed; no phone behaviour or acceptance is inferred.

Test title/author/filename phrases including mixed case, non-ASCII case, literal `%`/`_`, and an
entry beyond the first hundred. Results/counts cover the whole History in newest-first order.
Check fast edits, paging, clearing input, no matches versus empty History, keyboard focus during
loading, PL/EN, retry after read failure and leave/reentry. Filtering must not interrupt audio or
replace the playing queue. Selecting a result still captures the displayed page as before.
Clear History while a filter is pending: all entries must disappear without late rows returning.
[Contract, host evidence and full checklist](PLAN_HISTORY_SEARCH.md). Host UI/native SQLite checks
are not phone timing or behaviour evidence; overall owner acceptance is recorded above.

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
[Evidence and complete checklist](https://github.com/przunk/protracktor/blob/2c35319cfa8b665398a36141248b45544bd68ebe/docs/PR_C107_HISTORY_ENTRY_SCROLL.md). The trial includes pending
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
See [C106 evidence and phone checklist](https://github.com/przunk/protracktor/blob/2c35319cfa8b665398a36141248b45544bd68ebe/docs/PR_C106_SEARCH_REQUEST_OWNERSHIP.md) and the
[C105 playlist checklist](https://github.com/przunk/protracktor/blob/2c35319cfa8b665398a36141248b45544bd68ebe/docs/PR_C105_LATEST_PLAYLIST_ACTIVATION.md#phone-checklist).

## B48 and B46 — overall owner acceptance, 2026-10-14

C105 subsequently received a separately selected activation fix on `fix/latest-playlist-activation`.
Its code-review source must not be presented as independent user feedback. Full gate passed 859
tests with lint 0 errors/72 warnings. Offered APK: **1.3.1 (1170)**, clean source `07300b1`,
`dist/protracktor-release-1.3.1-1170-07300b1-20261014-005316.apk`; device results still pending.
Use the [C105 checklist](https://github.com/przunk/protracktor/blob/2c35319cfa8b665398a36141248b45544bd68ebe/docs/PR_C105_LATEST_PLAYLIST_ACTIVATION.md#phone-checklist) for
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
[C102 handoff](https://github.com/przunk/protracktor/blob/2c35319cfa8b665398a36141248b45544bd68ebe/docs/PR_C102_BLUETOOTH_OUTPUT_RECOVERY.md). The owner confirmed these phone scenarios:

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

### Live WEB language regression — W24, 2026-10-28

Run `node scripts/check-ui-messages.mjs` for UI/data ownership, immutable action arguments,
composition, Polish plural forms and retained node/attribute bindings. The full project gate runs
page startup and interaction checks with the actual production module graph:
`PROTRACKTOR_PAGE_MODULES=1 node --experimental-vm-modules scripts/check-page.mjs`.
The VM flag is a Node host-test requirement, not a browser installation step.

The live-language fixtures drive real Settings radios and System events. They retain decoder/audio
references, runtime bytes, elapsed listening, raw names, independent editor/context, duplicate FIFO,
paused state and transport-message counts. They check write failures, focus/query ranges, Search
answers/selection/suggestions, Saved places explanations and Undo, Queue rows/handles/menus, and
held source acknowledgements/privacy prose. Requested page navigation is observed explicitly;
jsdom reports reload requests without performing them, so unchanged references alone are not proof.
Native comparison uses accepted `c8209b1` language/resource/controller paths and `AppLanguageTest`.

Browser acceptance remains unchecked: change English/Polish/System while playing, paused, browsing,
editing a query and inspecting another file; verify sound continuity, theme, focus, scroll, retained
selection and meaningful accessibility labels. Repeat with an external shared link and background
playback. Host stubs do not establish audible continuity, mobile focus or OS behaviour.

### Retained WEB music — W20, 2026-10-28

Run the registered `check-cache-budget`, `check-music-download`, `check-remote-music`,
`check-music-cache-store`, `check-music-cache-migrations` and `check-music-cache-view` scripts.
Shared TSV cases are also consumed by the actual native CacheBudgetParityTest and declared as
Gradle inputs. Removing a case must fail both implementations and force native test execution.
Store tests use the production opener with host IndexedDB and real platform Blobs, not a copied
schema or alternate application store. Controller tests exercise the same production coordinator
with controlled network/storage ports. Stream tests use real Response bodies and controlled timers.

Actual-module page checks drive Browse, selection, transport, Settings Clear/Retry and HTTP handoff.
They verify byte identity, retained replay, cache-only cleanup, unchanged playback/FIFO/editor,
protected documents, readable errors, refused quota and transferred-buffer lifetime. Cold-page
startup additionally plays a pre-existing retained copy without network or initialization autoplay.
Cache-budget maintenance may write `remoteMusic` during startup; every other store stays protected
by the original no-initialization-write guard. The page harness supplies object-URL cleanup and
rejects uncaught UI exceptions rather than ignoring delayed timer errors.

Manual browser acceptance remains open: play online, reload, disable the network and replay; clear
copies during playback; preserve handoff bytes during this tab while refusing persistence after
reload; check size/mark/focus behaviour and truthful storage refusal. After W21, full ASMA must
survive fetched-cache Clear, while deleting ASMA removes its archive and index together. Host tests
establish no audible output, real browser storage allocation, mobile touch or background behaviour.


### WEB retained Modland paths — W23, 2026-10-28

`node scripts/check-modland-recovery.mjs` consumes the 23 shared URL cases and runs thirteen
production downloader/coordinator checks with real Response streams and host IndexedDB. Native
ModlandLegacyPathsTest consumes the same file, declared as a Gradle test input; the ten existing
RemoteFilesModlandTest cases remain unchanged. Controlled faults must fail before restoration.
The existing seven music-transfer deadline/cancellation cases remain required. A removed shared row
fails both readers and invalidates the native task without changing its command or adding a rerun
flag; restoration executes and passes all fifteen reference tests.

Actual page tests click saved playlist rows for conflict europe.ym, same game (22).spc,
(opl2) totally nuts.s3m and 06 ending theme.vgz. They check exact original/recovered requests,
bytes delivered to the worklet, original History and saved IDs, and replay without network.
The common file-action resolver must reuse those bytes without issuing playback commands.
These are host event/storage observations, not real-browser sound or CORS acceptance.

Manual browser trial remains unchecked: play those retained legacy references online, replay
from retained storage offline and inspect their unchanged playlist/History addresses. Compare
an actual HTTP404 with an access/server refusal and a disconnected network; only the eligible
404 may try one alternate path. Confirm that clearing retained music during a slow recovery
leaves current sound alone and that the late transfer does not restore a cleared disk copy.
Run the forced complete project gate/lint before closing this stage.


### WEB stereo mix — W14, 2026-10-29

The full gate registers `check-stereo-mix.mjs`, `check-engine-stereo.mjs` and
`check-worklet-stereo.mjs`. They exercise the production mode/preferences module, actual packaged
WASM and actual processor message/callback path. Generated looping MOD fixtures supply left-only,
right-only, correlated and opposite-phase PCM without external downloads. Check the first-frame
matrix, bit-preserving Original, 32/44.1/96 kHz smoothing and mid-ramp retarget, invalid/empty
requests, per-handle isolation, pause, seek, rewind and subsong continuation. Compare original
and raw export PCM to the retained pre-change engine where that local baseline is available;
mandatory public tests do not depend on private artifacts or browser encoding.

The ten shared stored-mode rows also run in StereoMixTest alongside the unchanged native mode,
preference and actual Compose Settings cases. Removing a row must fail both readers and invalidate
the Gradle task. Restore it and repeat the same native command without a rerun flag; require eleven
passing tests and an actually executed task. Deliberate UI, initial/live worklet, pause, fallback,
storage refusal and compiled DSP/rate faults must fail their relevant checks before restoration.

The actual page suite drives all three Settings choices, English/Polish, focus/labels, refused
storage and recovery while preserving another visible playlist, duplicate FIFO, current source,
clock and audio object. The startup runner includes stored Narrow/Mono, unknown values, refused
reads and a mode change during delayed engine loading; no startup choice may create audio or
write defaults. The legacy adapter is also exercised, without a second locale or mixer algorithm.

Real-browser acceptance remains unchecked. In Settings → Stereo mix, compare Original, Narrow
and Mono on strongly separated and ordinary stereo music; change modes while playing and paused,
then advance files/subsongs, seek and restart. Confirm position, queue and draft preservation.
Reload to verify the retained selection and explicit-Play startup rule. Check keyboard/touch,
English/Polish/System, themes and zoom, and export audio while the live decoder is in Mono.
Browser output quality, AAC encoding, mobile routing and background lifetime are separate checks.

The C121 Information filename guard waits for the actual describe request before asserting its
original filename. Storage reads can exceed 20 ms; a fixed sleep did not synchronize the action.
Controlled read delay must pass with the correct name, and a broken production name must fail.

### WEB volume normalization — W15, 2026-10-29

The public full gate includes profile/identity, preferences, atomic storage, preparation and
playback-ownership checks. Shared validation, binary/CRC and content-key fixtures also run in
LoudnessProfilesTest alongside the unchanged preparation and physical native Settings tests.
Declare every fixture as a Gradle input. Remove an actual identity row to demonstrate both
consumers fail; restore it and require the same native command to execute eighteen tests in
three suites without rerun flags or cached results.

`check-engine-loudness.mjs` uses actual packaged WASM/decoded PCM. It covers initial prepared
gain, fallback, boost/attenuation caps, peak precedence, analysis budgets, short input,
seek/restart, width/rate/subsong transitions and whole-song continuity. It compiles a standalone
native-header oracle and compares decoded PCM and input statistics across four sample rates.
`check-worklet-loudness.mjs` drives the real message/callback path for deferred startup, pause,
stale profile packets, Off, subsong resets, old close identities and natural/seeked EOF.

`check-loudness-worker.mjs` runs the actual worker handler/native analysis in real Node threads;
its adapter supplies browser transport only. Check independent decoder position, serial reuse,
owned transfers, cancellation, load/refusal recovery and source-buffer survival. Storage tests
use the production IndexedDB adapter, two concurrent connections, CRC corruption, quota failure,
complete/partial replacement, 512 entries and user-data preservation through Clear.

The full gate also executes the production ESM page with
`PROTRACKTOR_LOUDNESS_BRIDGE=1`. Browser message transports and speaker output are adapted;
app/processor/worker/WASM, gestures and IndexedDB are real. Inspect the first PCM, cancelled
private startup and History, a generated NSF whose actual first audible subsong is one, old
profile identities and the real ten-second watchdog followed by late delivery. Settings checks
cover PL/EN, keyboard focus/icon/label, live pause/source/FIFO/editor continuity and visible
refused writes. Twenty-five fresh page processes cover retained/default/malformed/refused
preferences and a switch during delayed engine loading. A pause assertion waits for the actual
transport state; a fixed delay or direct setter is not equivalent evidence.

Demonstrate that missing Settings actions, premature PCM, stale source/subsong/configuration,
broken CRC/cache quality and a compiled native DSP bypass fail the relevant guards. Preserve
exact source hashes after restoration. Rebuild the normal WASM whenever its inputs change and
run the complete forced project gate/lint before closing W15; focused checks are partial evidence.

Real-browser acceptance remains unchecked. In Settings → Volume normalization, compare quiet/loud
tracks with On/Off, including first and repeated plays, silence/short files and separated stereo.
Toggle while playing and paused, change stereo/subsongs, seek/restart and advance through source
and duplicate manual requests. Reload without autoplay, test refused/offline storage and Clear,
use keyboard/touch in PL/EN and both themes, and confirm exports retain the original audio.
Listening, device worker scheduling, routing, encoding and background lifetime require observation.

### WEB system controls — W25, 2026-10-29

Focused host commands:

```sh
node scripts/check-system-media.mjs
node scripts/check-track-artwork.mjs
PROTRACKTOR_PAGE_MODULES=1 PROTRACKTOR_SYSTEM_MEDIA=1 node --experimental-vm-modules scripts/check-page.mjs
PROTRACKTOR_PAGE_MODULES=1 PROTRACKTOR_SYSTEM_PCM=1 node --experimental-vm-modules scripts/check-page.mjs
```

The project gate includes these checks. The ESM harness adapts browser transport and canvas only;
the PCM bridge loads the actual page, processor and Native WASM. Shared artwork fixtures run
against Android `TrackArtworkParityTest` with a declared Gradle input; missing-row failure and
non-cached restoration are confirmed. Focused results do not replace the full project gate or
physical browser acceptance. The comparison and deliberate-defect inventory are in the
[implementation ledger](WEB_IMPLEMENTATION.md#w25--browser-system-controls-2026-10-29).

Real-browser checklist, all unchecked; record OS/browser/version and the delivered bundle SHA:

- [ ] Repeated Play/Pause from system controls stays idempotent, including loading and resuming.
- [ ] Pause/Stop during loading or analysis prevents late startup. Play explicitly retries.
- [ ] Next/Previous follow subsong settings and the manual FIFO, including duplicate requests;
  hold a page control first, then press a hardware control and verify it still acts.
- [ ] Change the visible playlist without changing the playing source; system metadata stays on
  the actual tune. Stop dismisses it while preserving position/source; Play restores it.
- [ ] Seek to an exact position and issue several seeks rapidly. The last target wins. Change
  subsong during seeking; old work must not seek the new tune or restore old metadata.
- [ ] Select a tune chip while paused; controls, actual sound, duration and title agree. Press
  Next/Previous rapidly and Pause before the acknowledgement; the later Pause wins.
- [ ] Check generated format cover, title and shared author/platform/year subtitle. Unknown
  duration must not invent a system duration. Switch PL/EN without restarting audio.
- [ ] Background/lock-screen and headset/Bluetooth controls are tested separately on each browser.
  Record unavailable actions or absent WebAudio session routing as unsupported, not as a pass.
- [ ] Browser interruption/suspension updates system presentation; explicit Play resumes only if
  the platform permits it. Paused wall-clock time must not appear as playback progress.
- [ ] A browser without Media Session or with a rejected action still supports all page controls.

No browser/device run is claimed by the host event and PCM evidence. C104 remains under physical
verification; no custom notification heart/layout or native audio-focus port is included.
