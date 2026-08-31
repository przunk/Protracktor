# Building Protracktor

Everything comes from the shared toolchain in `/mnt/workspace/.tooling`. Nothing needs to be
installed system-wide, and no build here writes to another project's state.

## Prerequisites

Check them before spending time on a failed build:

```bash
./scripts/check-tooling.sh
```

It prints what it found rather than a bare verdict, because a toolchain that is present but wrong
is the failure that costs the most time to diagnose.

| Component | Version | Source |
| --- | --- | --- |
| JDK | Temurin 17 | `.tooling/jdk` |
| Gradle | 9.6.0 | project wrapper |
| Android SDK | platform 36, build-tools 36.0.0 / 37.0.0 | `.tooling/android-sdk` |
| NDK | 29.0.14206865 | `.tooling/android-sdk/ndk` |
| CMake | 3.31.6 (ships Ninja) | `.tooling/android-sdk/cmake` |

CMake is pinned to 3.31 rather than 4.x on purpose: CMake 4 removed compatibility with
`cmake_minimum_required` below 3.5, and several of the decoder libraries we vendor are old enough
to declare exactly that.

## Environment

```bash
source scripts/use-tooling.sh
```

Sets `JAVA_HOME`, `ANDROID_HOME`, `ANDROID_NDK_HOME`, and points Gradle at a Protracktor-specific
`GRADLE_USER_HOME` and temp directory.

Those last two are not tidiness. `/mnt/workspace` is a 9p mount that rejects `chmod` and
`utimensat`; Gradle's native services and the Android build both fail on it. Every mutable path
lives on the Linux filesystem, and every one of them is namespaced to Protracktor so a build here
cannot disturb another project in the workspace.

## Builds

```bash
./scripts/build-debug.sh [extra gradle args…]
```

Runs `check-tooling.sh` first, writes `local.properties`, builds `:app:assembleDebug`, and copies
the result to `dist/protracktor-<versionName>-debug.apk`.

**Look in `dist/`, not in `app/build/outputs`.** The build directory is `~/.protracktor/build`
(see above), so the path every Android tutorial gives you is wrong here.

The script treats a green build that produced no APK as a failure. Gradle can report success while
skipping the packaging task, and "✅ nothing was produced" is the kind of message that costs an
afternoon.

Release builds are not written yet: they need a keystore, which is the owner's to create.

## Pushing to GitHub

```bash
./scripts/push-protracktor.sh [branch…]
```

Asks for a personal access token when it runs and keeps it nowhere: not in `.git/config`, not in
the remote URL, not in a credential store. Default is the current branch.

A rejected non-fast-forward means history was rewritten locally; `PROTRACKTOR_FORCE_PUSH=1`
re-pushes with `--force-with-lease`, which still refuses if the remote moved since the last fetch.

Pushing is the owner's action. Agents do not hold tokens (AGENTS.md §3).

## Notes

There is no emulator in this environment. Nothing about on-screen behaviour is confirmed until the
owner installs the APK on a phone. A build that compiles proves the build, and nothing else.
