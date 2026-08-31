# Status

Updated: 2026-09-01 — version 0.2.0, versionCode 2

## What works

A usable player, as far as anything can be called that without a device saying so.

- **Formats**: tracker modules through libopenmpt (MOD, XM, S3M, IT and dozens more) and Atari ST
  through sc68 (SNDH, YM). Backends sit behind one interface and are asked what they can do rather
  than assumed — libopenmpt seeks, sc68 cannot, and the UI reflects that.
- **Library**: folders granted through the storage access framework, remembered between sessions.
  Browse shows what a folder holds and adds the ticked rows, not the whole thing.
- **Playlists**: several, named, created, renamed, deleted, switched from the top bar. Shuffle and
  repeat work inside the active one.
- **Transport**: a dock on every screen with shuffle, previous, play, next and repeat. Shuffle keeps
  a real history so backward returns to what was actually played; without shuffle, backward is the
  row above. Seeking where the backend allows it.
- **Survives a restart**: playlist, settings and the track last played all come back.
- **Survives leaving the app**: a foreground service with transport in its notification. Audio focus
  and becoming-noisy are honoured.
- **Both languages**, Polish and English, from the first screen.

Verified here: 29 unit tests, a release build through R8 with all ten JNI symbols intact, and
`aapt2` on the artifact. Verified by the owner on a device up to 2026-08-31: modules play.

**Not verified by anyone yet**: SNDH playback, everything added on 2026-09-01, and every gesture.

## Finished

- **2026-09-01** — **R1: Atari ST.** sc68 2.2.1 vendored and building for every ABI, behind a backend
  interface alongside libopenmpt. SNDH files are 68000 machine code, so this is a whole emulated
  Atari. **Not confirmed playing** — there is no SNDH file on this machine and no emulator; the
  owner has to try one.

- **2026-09-01** — Audio focus and becoming-noisy: another app or a phone call takes the speaker and
  we pause; pulling headphones out pauses instead of playing to the room.

- **2026-09-01** — Removing a track offers undo instead of asking first; deleting a playlist asks.
  Snackbars can be pushed off the screen sideways.

- **2026-09-01** — **R6 met.** Several named playlists, created, renamed, deleted and switched from
  the top bar. Browse remembers granted folders and adds a *selection* rather than a whole folder.

- **2026-09-01** — Playback survives the app leaving the screen: `PlaybackController` is now a
  process-wide singleton and `PlaybackService` keeps it in the foreground with a notification
  carrying previous, play/pause, next and stop.

- **2026-09-01** — **R2 met.** Playlist, shuffle, repeat and the track last played survive a restart,
  in a hand-written SQLite database (`docs/ARCHITECTURE.md` §9 explains why not Room). Granted SAF
  folders are remembered too. Six tests run the schema against a real SQLite engine on the JVM.

- **2026-08-31** — The Compose UI: dock, playlist, Now Playing, Browse, theme with dynamic colour,
  Polish and English strings. `PlayerViewModel` ties `PlayQueue` to the native engine. The engine
  gained a finished flag and a restart, polled rather than pushed — signalling from the audio
  callback would mean attaching a JNI environment on the thread that must never be late.

- **2026-08-31** — `PlayQueue`: play order, history and repeat/shuffle semantics as pure Kotlin with
  no Android in sight. 16 unit tests, all four deliberate mutations of the logic killed by them.
  `scripts/test-protracktor.sh` runs them quietly.

- **2026-08-31** — Repository created. GPL-3.0-or-later, remote `https://github.com/przunk/protracktor`
  (private for now).
- **2026-08-31** — Build environment scripts: `use-tooling.sh`, `check-tooling.sh`,
  `build-debug.sh`. NDK 29.0.14206865 and CMake 3.31.6 added to the shared
  `/mnt/workspace/.tooling/android-sdk` with the owner's approval; nothing already there was
  modified.
- **2026-08-31** — `push-protracktor.sh` and `internal/git-askpass.sh`, adapted from the Kratkoza
  equivalents with the owner's permission to read them.
- **2026-08-31** — Gradle project: `app` module, Compose, Material 3, minSdk 29 / targetSdk 36,
  Gradle wrapper 9.6.0, build directory off the 9p mount. Debug APK builds, was inspected, and runs
  on the owner's device.
- **2026-08-31** — Decisions taken: formats to do first (Q2), how files reach the library (Q3),
  duration policy (Q4), and the two-kinds-of-source model with remote catalogues
  (`docs/ARCHITECTURE.md` §8). Modland's index and file endpoints were measured, not assumed.
- **2026-08-31** — `AGENTS.md` in the project root records the English-documentation override as
  applying to Protracktor only; the workspace-wide rule is untouched.
- **2026-08-31** — Build scripts rewritten to keep Gradle's output in a log file and print four
  lines: the token cost of an agent reading task names was the whole reason. Artifact naming now
  matches the workshop's other projects (`versionName-versionCode-timestamp`), release signing uses
  the shared `PRZUNK_UPLOAD_*` properties, and the failure path was tested by breaking a Kotlin
  source deliberately.
- **2026-08-31** — Native layer: libopenmpt 0.8.9 vendored by `scripts/fetch-native-deps.sh`
  (pinned, SHA-256 verified), built by a CMakeLists that reads its source list from upstream's own
  `Android.mk`. JNI engine with Oboe output in `native/engine/`. Kotlin facade in
  `engine/NativeEngine.kt`, with the R8 keep rule that stops the release build renaming the native
  method names.

## Surprises worth remembering

- **AGP 9 embeds Kotlin.** Applying `org.jetbrains.kotlin.android` alongside it is a hard error,
  not a warning. But the Compose compiler plugin (`org.jetbrains.kotlin.plugin.compose`) is *still*
  separate and *still* required whenever `buildFeatures.compose` is on. The two facts look
  contradictory and cost two failed configurations to establish.
- **`-keepclasseswithmembernames` preserves names but still allows shrinking.** The 0.1.0 release
  APK contains five of `NativeEngine`'s seven native declarations: `nativePositionSeconds` and
  `nativeDurationSeconds` were removed because no Kotlin code calls them yet. That is correct — JNI
  resolves lazily and per call — but it looks exactly like a broken keep rule at a glance, so check
  whether the missing method is simply unused before changing anything.
- **A test can pass for the wrong reason and mutation testing is how you find out.** The test for
  "forward after backward replays the same track" was built with plain `next()` calls, which makes
  the history identical to the play order — so deleting the redo branch entirely still produced the
  right answer by accident. It only became a real test once the history was made to *diverge* from
  the order with `startAt`. Two further rounds were needed: the mutation was also shadowed by
  `appended()` failing to truncate the forward branch, a latent inconsistency that is unreachable
  today only because another branch runs first.
- **`set -e` with `set -o pipefail` kills a script on a grep that matches nothing.** The test
  script's failure report died halfway through, so the run that most needed explaining printed
  least. Every grep that may legitimately find nothing now ends in `|| true`.
- **Filtering test-result XML by modification time reports zero tests on every second run**, because
  Gradle skips the task when nothing changed. The zero-tests guard has to distinguish "skipped as
  up to date" from "genuinely ran nothing", and now says which.
- **CMake's regex engine has no `\t` escape.** An anchor written `"^[ \t]*..."` matches nothing at
  all on a tab-indented file, silently. Strip the line with `string(STRIP)` and anchor on `^`
  instead.
- **`file(STRINGS)` treats a trailing backslash as a list escape.** Reading a makefile whose source
  list is continued with `\` yields the entire block as *one* list element beginning
  `LOCAL_SRC_FILES += \`, so per-line matching finds nothing. Read the file whole and use
  `string(REGEX MATCHALL)`.
  Both of the above produced zero parsed sources and both were caught only because the CMake asserts
  the count is over 100. Without that assert, the build would have produced a correctly linking
  libopenmpt with no decoders in it.
- **Oboe's prefab package requires `c++_shared`.** CMake's NDK default is `c++_static`, so the first
  native build fails with `[CXX1212] User is using a static STL but library requires a shared STL`.
  Fixed with `-DANDROID_STL=c++_shared`.
- **`unzip` is not installed in this environment.** Irrelevant to the app (archive handling will be
  native), but it will bite any script that reaches for it. Use Python's `zipfile` instead.
- **`sndh.net` does not resolve from this machine.** Whether the domain is gone or the network here
  blocks it is unknown. Recheck when `sc68` is integrated.
- **The workspace mount vanished mid-session** while the owner was adjusting WSL, taking the whole
  project directory with it until the mount came back. Nothing was lost, but the repository exists
  in exactly one place until it is pushed.

## Next

1. `sc68` integration for SNDH — the format that started this project. `sndh.net` did not resolve
   from here, so finding a source for it is part of the step.
3. Room index and the library scan, which is what R9 (instant start) actually depends on.

Blocked on the owner: `docs/OPEN_QUESTIONS.md` Q1 (navigation model). It does not block steps 1–3.

## Not built yet — and the requirements they leave open

Naming these because a `STATUS.md` that implies more than exists is worse than none.

- **No media session.** The foreground service keeps playback alive and its notification has
  transport buttons, but lock-screen controls and Bluetooth or headphone buttons do nothing.
- **`.sc68` container files will not play**, though `.sndh` should. That format references external
  replay binaries which we do not ship. SNDH carries its own code and needs none.
- **R9 (playback starts immediately) is untested.** Nothing is cached and nothing is prepared ahead;
  a small local module is fast because it is small, not because we made it so.
- **Folder scanning filters by file extension**, not by probing content as
  `docs/ARCHITECTURE.md` §5 requires. Probing means reading every candidate, which belongs with the
  index rather than with a foreground scan. `SupportedFormats` says so in its own documentation.

## Known defects

- ~~Play/Stop label goes stale when a module reaches its end.~~ **Fixed 2026-08-31** by the polling
  loop in `PlayerViewModel`: the native side raises a flag when the module ends and the same tick
  that drives the progress bar notices it.

## Fixed

- **2026-08-31 — pressing next twice quickly played two tracks at once.** Each press launched its
  own open; the second overwrote the handle without closing the first, which kept playing with
  nobody holding it. Two things were wrong: the queue advanced *inside* the coroutine, so both
  presses read the same starting point, and there was nothing to cancel an open already in flight.
  The queue now advances synchronously and the in-flight open is cancelled, with anything it had
  already opened closed rather than started.

- **2026-08-31 — next and previous "behaved randomly", reported from a device.** Navigation walked
  the play history in *every* mode. That is right under shuffle — R5 asks for exactly it — and wrong
  without, where the list is on screen and "previous" has to mean the row above. History now governs
  only when shuffle is on. Whatever the bookkeeping, a control that disagrees with the visible list
  looks broken.
- **2026-08-31 — the gesture bar covered the transport row.** The dock's contents now sit above the
  navigation-bar inset while the surface still paints behind it; padding the surface instead would
  leave a strip of the wrong colour beneath the dock.

- **2026-08-31 — `NoSuchElementException: List is empty` in `PlayQueue.advanced()` on adding the
  first folder.** `order` was a constructor property defaulting to `tracks.indices`, and **`copy()`
  does not re-evaluate default arguments** — so a queue created empty and then given tracks by
  `copy(tracks = …)` kept the empty order, and the first `hasNext` indexed into nothing.

  Fixed by deriving `order` from `tracks` and a shuffle seed instead of storing it, which makes the
  two impossible to disagree. Patching the one call site would have left the trap in place for the
  next person, and `copy()` cannot be taken away from a data class. Two regression tests cover both
  routes in, and both were confirmed to fail against the original code.

- **2026-08-31 — `IllegalStateException: Track already closed` on pressing Play.**
  `DisposableEffect` was keyed on the loaded module. It runs `onDispose` whenever its key changes,
  not only when the composable leaves, and the lambda read the state at dispose time — by which
  point it already held the *new* value. So opening a module closed the module that had just been
  opened, and Play hit a freed handle. Keyed on `Unit` instead; replacing a module is the picker's
  job, and the effect only has to catch the screen going away.

  Worth keeping: the crash came from the code written to prevent a leak, and the guard in
  `Track.handle()` is what turned a use-after-free in native memory into a named exception with a
  line number.

## Branches

- `master` — repository base.
- `develop` — current work.
- `feature/project-scaffold` — scaffolding and the Gradle skeleton. Not merged.
