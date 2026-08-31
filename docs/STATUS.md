# Status

Updated: 2026-08-31

## What works

The project builds. `./scripts/build-debug.sh` produces an installable debug APK
(`dist/protracktor-0.1.0-debug.apk`) containing a single placeholder screen. **Nothing plays yet**
and there is no native code in the APK.

Verified with `aapt2 dump badging` on the produced artifact rather than from the build log:
package `com.przunk.protracktor`, `minSdkVersion 29`, `targetSdkVersion 36`, `compileSdkVersion 36`.

**Confirmed on a real device on 2026-08-31**: the owner installed the debug APK and the
placeholder screen appears. The build → phone chain works end to end, which is all this stage
claimed.

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
  Gradle wrapper 9.6.0, build directory off the 9p mount. Debug APK builds, was inspected, and runs
  on the owner's device.
- **2026-08-31** — Decisions taken: formats to do first (Q2), how files reach the library (Q3),
  duration policy (Q4), and the two-kinds-of-source model with remote catalogues
  (`docs/ARCHITECTURE.md` §8). Modland's index and file endpoints were measured, not assumed.
- **2026-08-31** — `AGENTS.md` in the project root records the English-documentation override as
  applying to Protracktor only; the workspace-wide rule is untouched.

## Surprises worth remembering

- **AGP 9 embeds Kotlin.** Applying `org.jetbrains.kotlin.android` alongside it is a hard error,
  not a warning. But the Compose compiler plugin (`org.jetbrains.kotlin.plugin.compose`) is *still*
  separate and *still* required whenever `buildFeatures.compose` is on. The two facts look
  contradictory and cost two failed configurations to establish.
- **`unzip` is not installed in this environment.** Irrelevant to the app (archive handling will be
  native), but it will bite any script that reaches for it. Use Python's `zipfile` instead.
- **`sndh.net` does not resolve from this machine.** Whether the domain is gone or the network here
  blocks it is unknown. Recheck when `sc68` is integrated.
- **The workspace mount vanished mid-session** while the owner was adjusting WSL, taking the whole
  project directory with it until the mount came back. Nothing was lost, but the repository exists
  in exactly one place until it is pushed.

## Next

1. Native layer proof of concept: `libopenmpt` + Oboe playing one `.mod` from the placeholder
   Activity. This decides whether the whole approach holds, so it comes before any UI work.
2. `sc68` integration for SNDH — the format that started this project.
3. Room index and the library scan, which is what R9 (instant start) actually depends on.

Blocked on the owner: `docs/OPEN_QUESTIONS.md` Q1 (navigation model). It does not block steps 1–3.

## Known defects

None recorded.

## Branches

- `master` — repository base.
- `develop` — current work.
- `feature/project-scaffold` — scaffolding and the Gradle skeleton. Not merged.
