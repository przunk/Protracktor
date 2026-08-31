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

## Native dependencies

```bash
./scripts/fetch-native-deps.sh [--force]
```

Downloads the decoder sources into `native/vendor/`, which is gitignored. Each is pinned to an exact
version and verified against a SHA-256 recorded in the script; a mismatch refuses to unpack rather
than warning. Downloads are cached in `~/.protracktor/downloads`, so a re-fetch costs nothing.

Run it once before the first build. `build-debug.sh` does not run it for you, because a build that
silently reaches out to the network is a build that behaves differently depending on whether you
noticed.

### Checking one decoder quickly

A full Gradle build compiles every ABI. To check that a newly vendored library compiles at all,
configure the backends alone for one ABI — about a minute instead of several:

```bash
source scripts/use-tooling.sh
cmake -S native -B /tmp/protracktor-nativetest -G Ninja \
  -DCMAKE_TOOLCHAIN_FILE="$ANDROID_NDK_HOME/build/cmake/android.toolchain.cmake" \
  -DANDROID_ABI=arm64-v8a -DANDROID_PLATFORM=android-29 \
  -DCMAKE_BUILD_TYPE=Release -DPROTRACKTOR_BUILD_ENGINE=OFF
ninja -C /tmp/protracktor-nativetest
```

`PROTRACKTOR_BUILD_ENGINE=OFF` is required outside Gradle: the engine links Oboe, which arrives as a
prefab package unpacked from an AAR, and only the Gradle build unpacks it.

## Builds

```bash
./scripts/build-debug.sh   [extra gradle args…]
./scripts/build-release.sh [extra gradle args…]
```

**Gradle's output goes to a log file, never to the terminal.** On success you get four lines; on
failure you get the part of the log that says what broke — the `What went wrong` block, Kotlin `e:`
lines, and compiler errors from the native build — plus the path to the rest. Thousands of lines of
task names are worth reading exactly never, and cost real money when an agent is the one reading
them.

Logs land at `/tmp/protracktor-<checkout>-{debug,release}.log`, named after the checkout so two
builds running at once cannot overwrite each other's.

### Artifacts

```
dist/protracktor-debug-<versionName>-<versionCode>-<YYYYmmdd-HHMMSS>.apk
dist/protracktor-<versionName>-<versionCode>-<YYYYmmdd-HHMMSS>.apk
```

Same scheme as the workshop's other projects. The `versionCode` and the timestamp are both there
because two builds of one `versionName` are otherwise indistinguishable once they leave `dist/` —
which is exactly the moment it matters which one is on the phone.

**Look in `dist/`, not in `app/build/outputs`.** The build directory is `~/.protracktor/build`, so
the path every Android tutorial gives you is wrong here.

Both scripts treat a green build that produced no APK as a failure. Gradle can report success while
skipping the packaging task, and "✅ nothing was produced" is the kind of message that costs an
afternoon.

### Signing

Release signing credentials never live in this repository. Put them in `~/.gradle/gradle.properties`:

```properties
PRZUNK_UPLOAD_STORE_FILE=/absolute/path/to/protracktor-release.jks
PRZUNK_UPLOAD_STORE_PASSWORD=…
PRZUNK_UPLOAD_KEY_ALIAS=…
PRZUNK_UPLOAD_KEY_PASSWORD=…
```

The same property names the workshop's other projects use, and **the owner decided on 2026-08-31
that Protracktor shares their keystore** rather than getting its own. A separate key was considered
because a GPL-3 app may end up on F-Droid; sharing won because one key across the workshop is one
thing to keep safe instead of five.

Without them the release build still succeeds, signed with the local **debug** key. That keeps
sideloading alive on a machine with no release key — an unsigned APK cannot be installed at all —
but such an APK must never be published. `build-release.sh` prints the certificate's subject rather
than assuming, because the Gradle config can look right and still have fallen back, and the mistake
is otherwise invisible until an upload is rejected.

### App Bundle (.aab)

Not written. The owner is adding a bundle signing key later, and a bundle script guessing at how it
will be configured would have to be rewritten when it arrives. There is nothing to upload to a store
yet either.

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
