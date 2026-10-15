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

**Our patches** live in `native/patches/<library>/` and are applied by the same script, after the
unpack. Each is checked both ways: already applied is fine, applies is applied, and one that does
neither stops the fetch, because the upstream file moved under it. **A checkout fetched before a
patch was added needs the script run again** — the version is unchanged, so it will not re-download,
but it will patch. Today there is one, for libopenmpt's text (`docs/STATUS.md` C79).

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

### Schema snapshots (B42, schema 23, stacked on B43's 22)

`app/schemas/1.sql` through `23.sql` export each historical database shape. `SchemaSqlTest` builds
each version through the real SQLite engine and compares the snapshot; the directory is a declared
Gradle test input. To deliberately regenerate snapshots after a reviewed schema change, source the
normal toolchain environment and run:

```bash
./gradlew :app:testDebugUnitTest --tests '*SchemaSqlTest' -PexportSchemas
```

Review and commit the resulting SQL. Normal tests do not rewrite snapshots. These describe schema
shape, not a replacement for migration logic or a backup of user data. B43's legacy qualification
backfill is tested separately with populated databases.

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
dist/protracktor-debug-<versionName>-<versionCode>-<shortSHA>[-dirty]-<YYYYmmdd-HHMMSS>.apk
dist/protracktor-release-<versionName>-<versionCode>-<shortSHA>[-dirty]-<YYYYmmdd-HHMMSS>.apk
```

Same scheme as the workshop's other projects. The `versionCode` and the timestamp are both there
because two builds of one `versionName` are otherwise indistinguishable once they leave `dist/` —
which is exactly the moment it matters which one is on the phone.
The SHA identifies sources when parallel branches have equal commit counts; `dirty` marks a
build whose source changes were not committed. Ignored build outputs do not make the tree dirty.
This filename scheme is emitted by the project scripts from 2026-10-13.

**Look in `dist/`, not in `app/build/outputs`.** The build directory is `~/.protracktor/build`, so
the path every Android tutorial gives you is wrong here.

Both scripts treat a green build that produced no APK as a failure. Gradle can report success while
skipping the packaging task, and "✅ nothing was produced" is the kind of message that costs an
afternoon.

### Signing

**Device-test APKs, from 2026-10-13:** `build-release.sh` passes `-PprotracktorTestApk=true`, so
Gradle does not select upload signing properties for that build. The APK remains the release
variant with R8/resource shrinking, signed using the existing local debug/test certificate.
It is installable but not for Play publication. This implements the workspace's separation of
agent-built test APKs from owner-signed AABs; it does not change the AAB signing workflow below.

The following credentials configure the **owner's store build**, not that test-APK script.
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

Direct Gradle release builds without them still succeed, signed with the local **debug** key. That keeps
sideloading alive on a machine with no release key — an unsigned APK cannot be installed at all —
but such an APK must never be published. `build-release.sh` prints the certificate's subject rather
than assuming, because the Gradle config can look right and still have fallen back, and the mistake
is otherwise invisible until an upload is rejected.

### A whole release

```
./scripts/release.sh 0.7.0 --check     # only check; changes nothing
./scripts/release.sh 0.7.0             # do it
```

Written 2026-09-21 on the owner's request, so a release is one command run from his terminal;
changed 2026-10-01 to release from `master` (below). Run on `develop` for a release, or on a
`hotfix/<name>` branch for a hotfix. It does, in order and stopping at the first failure: the tree
(on one of those, clean, tag free, version newer, `master` able to fast-forward), the release notes
in both languages, the web engine and the whole suite, `versionName` committed on the branch,
`master` fast-forwarded to that commit and tagged `v<version>`, the bundle built there through
`build-bundle.sh` — which asks for the keystore passwords itself, so the release script never sees
them — the evidence entry in `store/play-console/releases.md` read from the bundle and committed on
the branch, and for a hotfix the branch merged back into `develop`. **It does not push and does not
upload**; its last lines say what to push and what to upload where, and how to check the upload
(App bundle explorer → the versionCode → Downloads → Original app bundle, and its SHA-256). It asks
once, by having the version typed again, before it changes anything.

**If the bundle fails** (a mistyped keystore password), `master` has already moved and the tag
exists; the script goes back to the branch and prints how to finish from the tag.

**`./scripts/check-release.sh`** runs `release.sh` for real in disposable repositories, builds stubbed and
a throwaway key: a release, `--check`, a failed bundle, a hotfix, a hotfix that conflicts with
`develop`, and the refusals. Run it after changing `release.sh`. Verified by making the release merge
instead of fast-forward: four checks failed. **First real run: 1.0.0, 2026-10-02** -- the owner's keystore,
`master` fast-forwarded onto `v1.0.0` with no merge commit, `develop` one evidence commit ahead,
the bundle's SHA-256 matching the one recorded.

### Bounded release validation — 2026-10-15

`release.sh` and `--check` use the same gate. The web build, unit tests, auxiliary checks and lint
stream output into both the terminal and stage logs, with an elapsed-time message every 30 seconds
even when the child is silent. Logs are under `/tmp/protracktor-<checkout-name>-<stage>.log`;
the main ones end in `web-build`, `test` and `lint`. The stage prints its complete path at start/end.
Each stage is bounded, not the entire release or interactive signing:

| Environment override | Default | Includes |
| --- | --- | --- |
| `PROTRACKTOR_UNIT_TEST_TIMEOUT_SECONDS` | 1200 | Gradle compilation and unit tests |
| `PROTRACKTOR_LINT_TIMEOUT_SECONDS` | 1200 | Gradle lint |
| `PROTRACKTOR_WEB_BUILD_TIMEOUT_SECONDS` | 1200 | Release-path WASM engine build |
| `PROTRACKTOR_CHECK_TIMEOUT_SECONDS` | 1200 | Each auxiliary check separately |

Use positive seconds, e.g. `PROTRACKTOR_UNIT_TEST_TIMEOUT_SECONDS=1800 ./scripts/release.sh 1.2.0 --check`.
The normal Test configuration isolates classes (one worker at a time, 1 GiB heap) and prints test
start/results. This contains observed shared-worker Compose idle failures; their underlying cause
has not been established. A failed test stops further scheduling. Release validation forces actual
unit execution with `--really`; zero results, failures/errors/skips, timeout or cancellation refuse
success. Do not substitute cached/focused checks for a completed release gate.

Deadline exit status is 124; Ctrl+C is 130. Linux/WSL subreaper cleanup terminates only processes
created under that stage, including detached workers, escalating TERM to KILL after five seconds.
Cleanup can add up to two grace intervals; an unkillable process causes failure, not a green gate.
There is no `gradlew --stop` affecting shared daemons. Gradle uses `--no-daemon` (possibly a
single-use daemon) and in-process Kotlin compilation. Generated `.kotlin/` sessions are ignored,
not deleted or committed, so normal compilation does not dirty a release checkout.

`python3 scripts/check-test-runner.py` verifies deadline/streaming/signals/scoped process cleanup.
`bash scripts/check-release.sh` verifies Git-flow transitions with real working scripts, fixed
fixture versions, stub builds and a throwaway signer; neither touches release refs or owner keys
in this checkout. Both are part of the full gate. Failure, timeout and Ctrl+C must stop before the
release-version commit, master movement and tag. [Analysis and actual verification](PR_RELEASE_TEST_RUNNER.md).

### Releasing from `master` — decided 2026-10-01, from the first release after the public launch

*The owner, 2026-10-01: `master` is the release point. `develop` goes on; a release is `master`
brought level with `develop`, a tag on `master`, and the build made from it. `release.sh` does all of
this; the commands below are what it runs, for reading and for a release by hand. The workspace-wide
rules (`/mnt/workspace/AGENTS.md` §5 and §11) say the same since 2026-10-03.*

**Once, when it changed:** the old script had left a merge commit on `master` that `develop` did not
have, so `master` could not fast-forward. That merge was taken into `develop` once, with no file
changes; from then on `master` is always an ancestor of `develop`.

```
feature/* ──► develop ──(fast-forward at a release)──► master ── tag vX.Y.Z ── build
                  ▲                                       │
                  └────────── hotfix/* merged back ───────┘
```

**A release:**

```
git checkout master
git merge --ff-only develop          # master moves to the very commit develop is on
git tag -a vX.Y.Z -m "Protracktor X.Y.Z"
git checkout develop                 # work goes on here
```

**Fast-forward, never `--no-ff`.** `versionCode` is the commit count of what is built. A merge commit
on `master` at every release would give `master` commits `develop` never has, the two counts would
drift apart, and a test APK from a branch could carry a lower number than the published release --
which Android refuses to install over it. Fast-forwarded, `master` *is* the commit `develop` was on,
and the numbers stay one sequence. `--ff-only` also refuses, rather than merging, when `master`
holds something `develop` does not -- the tell of a hotfix not yet taken back.

**A hotfix** for what is published, when `develop` already holds unreleased work. With the script:

```
git checkout -b hotfix/<name> master
# fix, test, commit
./scripts/release.sh X.Y.Z              # on the hotfix branch; merges it back into develop at the end
git branch -d hotfix/<name>
```

Its evidence entry and `develop`'s latest one are both written at the top of `releases.md`, so that
file always conflicts on the way back; the script resolves it (both entries, the hotfix on top) and
stops for anything else, telling you to merge by hand. By hand, the same release is:

```
git checkout -b hotfix/<name> master
# fix, test, commit
git checkout master
git merge --ff-only hotfix/<name>     # master moves to the fix
git tag -a vX.Y.Z -m "Protracktor X.Y.Z"
git checkout develop
git merge --no-ff hotfix/<name>       # the same commit, taken back
git branch -d hotfix/<name>
```

**Taken back by merging, not by cherry-picking.** A cherry-pick makes a copy with another hash, so
`master` would keep a commit `develop` does not have, and the next release's `--ff-only` would refuse.
The merge brings the very commit into `develop`, after which `master` is again behind `develop` and
the next release fast-forwards as usual. `develop`'s count is then above `master`'s, so `versionCode`
keeps rising.

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

**`versionName` is selected for a release and set by `scripts/release.sh`.**

| | when |
| --- | --- |
| **patch** — 0.3.**1** | a released batch of fixes |
| **minor** — 0.**4**.0 | a released round of new capabilities |
| **major** — **1**.0.0 | reserved for "publishable" |

Owner decision, **2026-10-15**: bump **only during the release procedure**, not at a test APK
handoff, feature/fix commit or merge. This supersedes the previous handoff-bump wording. Between
releases the literal remains the latest prepared release's version. Test APKs are identified by
their Git-derived versionCode and source SHA, plus timestamp/dirty marker in the filename.

The prepared baseline is **1.1.0** and the next selected feature release is **1.2.0**. The
release script checks the version, creates its version commit/tag and builds the AAB from that
point; do not manually raise the literal in advance. Do not use an extra release-number bump
merely to satisfy the script after incorrectly bumping test builds. The approved unpublished
history correction is recorded in [Version history reconciliation](VERSION_HISTORY_REWRITE.md).
Existing APK/AAB versions, filenames and SHA-256 hashes are historical evidence, not strings to
rewrite. A local tag/prepared AAB is separate from a GitHub push or Play upload/publication.

**Bumping the minor on every merge was considered and rejected.** There were about twenty merges on
2026-09-03 alone, several of them documentation. It would have reached 0.22.0 in a day and told
nobody anything a timestamp does not — while costing an edit and a commit each time. The instinct
behind it was right, though: something *should* move on every merge. That something is the
versionCode, and now it does.

**Artifact names are read out of the artifact**, with `aapt2 dump badging`, rather than scraped from
`build.gradle.kts`. There is no literal versionCode in the source to scrape, and asking the APK what it
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

The script explicitly chooses the local debug/test certificate even if upload signing is
configured: sideloadable and not publishable. `build-release.sh` reports the actual certificate,
source SHA and artifact path. Direct Gradle store builds retain their upload-key configuration.

### Checking Android audio-host lifetime without a phone

```bash
./scripts/check-audio-host.sh
./scripts/check-audio-host.sh --sanitize
```

Compiles the actual `player_oboe.cpp`/JNI implementation against a controlled fake Oboe device and
decoder. No actual decoder cache, Android service or sound output is used. Requires the host C++
compiler and the existing pinned JDK's JNI headers; no extra toolchain/dependency is installed.
The normal check is part of `test-protracktor.sh`; ASan/UBSan is an additional lifetime check.
LeakSanitizer cannot finish under a ptrace-based sandbox, so run the sanitizer variant outside it
when permitted. These checks do not reproduce Bluetooth on a phone or replace the release-APK test.

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

## Flat notification icon

The notification glyph shares `GenerateLauncherIcons.java`'s P geometry with the launcher, but
has its own 24 dp viewport, white silhouette and transparent background. Regenerate only that
resource from the project root using the existing JDK:

```sh
source scripts/use-tooling.sh
java -Djava.awt.headless=true scripts/GenerateLauncherIcons.java --notification-only
```

The normal full icon-generation mode also emits it. The targeted mode does not rewrite launcher,
web or store assets. Android supplies the small-icon tint; no fixed multi-colour appearance is promised.

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

**Lint, since 2026-10-01** (`:app:lintDebug`; errors fail the run, warnings stay in the report). An
`AppLinkUrlError` had sat in the manifest unnoticed because nothing ran lint and the release build's
`lintVital` checks a narrower set. That one is silenced on its filter with the reason beside it --
the rule wants a host, and `content://` has none. Verified by removing the silencing: the script
failed and named the line. It adds about 45 s to a run.
