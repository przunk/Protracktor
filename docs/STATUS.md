# Status

Updated: 2026-08-31

## What works

The project builds. `./scripts/build-debug.sh` produces an installable debug APK
(`dist/protracktor-0.1.0-debug.apk`) containing a single placeholder screen. **Nothing plays yet**
and there is no native code in the APK.

Verified with `aapt2 dump badging` on the produced artifact rather than from the build log:
package `com.przunk.protracktor`, `minSdkVersion 29`, `targetSdkVersion 36`, `compileSdkVersion 36`.

Not verified: anything about how it looks or behaves on screen. There is no emulator here
(AGENTS.md §3), so that waits for the owner to install it.

## Finished

- **2026-08-31** — Repository created. GPL-3.0-or-later, remote `https://github.com/przunk/protracktor`
  (private for now).
- **2026-08-31** — Build environment scripts: `use-tooling.sh`, `check-tooling.sh`,
  `build-debug.sh`. NDK 29.0.14206865 and CMake 3.31.6 added to the shared
  `/mnt/workspace/.tooling/android-sdk` with the owner's approval; nothing already there was
  modified.
- **2026-08-31** — `push-protracktor.sh` and `internal/git-askpass.sh`, adapted from the Kratkoza
  equivalents with the owner's permission to read them.
- **2026-08-31** — Gradle project: `app` module, Compose, Material 3, minSdk 29 / targetSdk 36,
  Gradle wrapper 9.6.0, build directory off the 9p mount. Debug APK builds and was inspected.

## Surprises worth remembering

- **AGP 9 embeds Kotlin.** Applying `org.jetbrains.kotlin.android` alongside it is a hard error,
  not a warning. But the Compose compiler plugin (`org.jetbrains.kotlin.plugin.compose`) is *still*
  separate and *still* required whenever `buildFeatures.compose` is on. The two facts look
  contradictory and cost two failed configurations to establish.
- **`unzip` is not installed in this environment.** Irrelevant to the app (archive handling will be
  native), but it will bite any script that reaches for it.

## Next

1. Native layer proof of concept: `libopenmpt` + Oboe playing one `.mod` from the placeholder
   Activity. This decides whether the whole approach holds, so it comes before any UI work.
2. `sc68` integration for SNDH — the format that started this project.
3. Room index and the library scan, which is what R9 (instant start) actually depends on.

Blocked on the owner: `docs/OPEN_QUESTIONS.md` Q2 (which formats first) and Q3 (how the library
gets its files) both change what step 1 and step 3 look like.

## Known defects

None recorded.

## Branches

- `master` — repository base.
- `develop` — current work.
- `feature/project-scaffold` — scaffolding and the Gradle skeleton. Not merged.
