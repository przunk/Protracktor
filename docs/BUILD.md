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

### Trying a decoder on the host before believing in it

Every backend here was run on this machine against real files before it was integrated, and that has
caught something every time — sc68 2.2.1's half-broken SNDH, the SidMon executables hiding among
Modland's `.sid` files, and the render-contract bug that would have stopped every Atari ST track on
its first audio callback. The probes are committed; their binaries are not, because they are
host-specific.

```bash
./scripts/build-sc68-probes.sh     # sc68 2.2.1 vs 3.0.0b, the render contract, concurrency,
./scripts/probe-sc68.py            #   and the subsong-rewind demonstration for C13

./scripts/build-uade-probe.sh      # fetches and builds UADE 3.05, its two support libraries,
./scripts/probe-uade.py            #   and the song database, then measures against Modland

./scripts/build-gme-probe.sh       # the seven console families, which went in unmeasured
./scripts/probe-gme.py             #   and were 100/141 until the probe said why

./scripts/probe-openmpt.py         # the backend that carries 61% of the library
./scripts/probe-extensions.py      # what libopenmpt would play if the app ever offered the file
```

Each downloads a deterministic, seeded sample from Modland and caches it under `~/.protracktor`, so
a second run measures the same files and can be compared with the first. `build-uade-probe.sh`
prints the two environment variables `probe-uade.py` needs.

**Set `PYTHONUNBUFFERED=1` when redirecting one of these to a file**, or the progress lines sit in
Python's buffer and a long run looks hung.

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

The same property names the workshop's other projects use, and **it was decided on 2026-08-31
that Protracktor shares their keystore** rather than getting its own. A separate key was considered
because a GPL-3 app may end up on F-Droid; sharing won because one key across the workshop is one
thing to keep safe instead of five.

Without them the release build still succeeds, signed with the local **debug** key. That keeps
sideloading alive on a machine with no release key — an unsigned APK cannot be installed at all —
but such an APK must never be published. `build-release.sh` prints the certificate's subject rather
than assuming, because the Gradle config can look right and still have fallen back, and the mistake
is otherwise invisible until an upload is rejected.

### App Bundle (.aab)

```
./scripts/build-bundle.sh [extra gradle args…]
```

Written 2026-09-02, adapted from Kratkoza's — both projects sign with
the same workshop upload key through the same `PRZUNK_UPLOAD_*` Gradle properties, so the mechanism
is deliberately identical rather than merely similar.

**It refuses to hand over a bundle Play would reject.** That is the difference from
`build-release.sh`, which only warns: for an APK the debug fallback earns its place, because an
unsigned APK cannot be sideloaded at all and a debug-signed one is still useful. For a bundle headed
to Play it buys nothing but a rejection discovered later instead of now, so a debug signature is a
hard failure here.

A bundle carries a **JAR** signature rather than an APK one, so `apksigner` cannot read it; the
check uses `jarsigner -verify` and then `keytool -printcert -jarfile`.

**Credentials**, in order of preference and neither written down:

1. `~/.gradle/gradle.properties`, as above. If `PRZUNK_UPLOAD_STORE_FILE` is there, the script does
   not ask.
2. Typed at the prompt, passed to Gradle as `ORG_GRADLE_PROJECT_*` for the life of the process only.
   Nothing reaches the repository, `gradle.properties`, or the shell history.

Without a terminal, set `PRZUNK_UPLOAD_STORE_FILE`, `PRZUNK_UPLOAD_KEY_ALIAS`,
`PRZUNK_UPLOAD_STORE_PASSWORD` and `PRZUNK_UPLOAD_KEY_PASSWORD` in the environment.

The artifact is `dist/protracktor-<versionName>-<versionCode>.aab`, **without** the timestamp the
APK names carry: Play identifies an upload by its versionCode, so the filename should be identified
by the same thing. The script says so when a bundle for that code already exists — a rebuild is
fine, a forgotten bump is not, and only you can tell which.

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

## Versions

**`versionCode` is the commit count.** `git rev-list --count HEAD`, read by Gradle through
`providers.exec`. It only ever grows, changes on every merge without anyone doing anything, and
needs no discipline — which matters because Play refuses an upload whose code it has seen, and a
number a person has to remember to raise is a number that eventually is not raised. It stood at 2
for the whole project and blocked an upload; it is 199 as of 2026-09-03.

Outside a git checkout it falls back to 1. That is wrong and harmless: nothing built that way is
going to a store.

**`versionName` is typed by hand and means something.**

| | when |
| --- | --- |
| **patch** — 0.3.**1** | a batch of fixes handed over |
| **minor** — 0.**4**.0 | a round of work that added capability |
| **major** — **1**.0.0 | reserved for "publishable" |

Bumped at **hand-over**, which is when the number gets used, not at merge.

**Bumping the minor on every merge was considered and rejected.** There were about twenty merges on
2026-09-03 alone, several of them documentation. It would have reached 0.22.0 in a day and told
nobody anything a timestamp does not — while costing an edit and a commit each time. The instinct
behind it was right, though: something *should* move on every merge. That something is the
versionCode, and now it does.

**Artifact names are read out of the artifact**, with `aapt2 dump badging`, rather than scraped from
`build.gradle.kts`. There is no number in the source to scrape any more, and asking the APK what it
is cannot disagree with what it is. A bundle cannot be asked, so `build-bundle.sh` recomputes the
commit count the same way Gradle does — the two expressions have to agree, and the script says so.

## Which build to hand over

**Release.** Since 2026-09-03, the APK handed over for testing is the release build:

```
./scripts/build-release.sh
```

It is what a tester actually experiences — a debug build is `debuggable=true`, which costs a great
deal of ART optimisation and once produced twenty seconds of stutter that did not exist in release
(`docs/STATUS.md` C10). It also goes through R8, so a missing keep rule or a stripped resource
surfaces at hand-over rather than at a release.

Debug stays the right build **while iterating** — it is two to three times faster — and **when a
crash needs a readable stack trace**. Diagnose on debug, judge on release.

Without `PRZUNK_UPLOAD_*` configured the release APK is signed with the local debug key: sideloadable
and not publishable. `build-release.sh` says which key signed it, every time.

## Telling a green suite from a green suite that ran

`./scripts/test-protracktor.sh` prints how the result was reached, because Gradle has two ways of
reporting a passing suite it did not execute:

| what the script says | what happened |
| --- | --- |
| `124 tests passed in 13s` | they ran |
| `… (up to date, not re-run)` | nothing changed since last time |
| `… (from the build cache, not re-run)` | the answer came out of the build cache |

Both of the second two print `BUILD SUCCESSFUL` and execute nothing. The FROM-CACHE case was
invisible here until 2026-09-03, when a "genuinely executed" run at the end of a review turned out
to have been served from the cache in one second.

**`--really`** forces execution (`--rerun-tasks`). Worth it before claiming a suite is green on code
nobody has actually run it against.

## What the test script checks, beyond the tests

`./scripts/test-protracktor.sh` runs the unit tests and then two things the Kotlin compiler and
`aapt2` are both happy to let through:

- **Format specifiers with their flags in the wrong place.** `%,1$d` is not a specifier — the
  argument index comes first, `%1$,d` — and `String.format` throws when the string is rendered
  rather than when it is built. One shipped, in a plural whose quantity is always `other`, so the
  crash was certain and invisible here (`docs/STATUS.md` C5).
- **Strings that exist in English and not in Polish.** The app is bilingual from the first screen
  and fifteen strings had drifted to English-only across three days before anyone looked.

Both were verified by breaking the files deliberately and confirming the script exits non-zero.
