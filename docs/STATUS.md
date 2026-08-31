# Status

Updated: 2026-08-31

## What works

Nothing yet. The repository holds scaffolding only: licence, build environment scripts, and these
documents. No Gradle project, no application code, no audio.

## Finished

- **2026-08-31** — Repository created. GPL-3.0-or-later, remote `https://github.com/przunk/protracktor`
  (private for now).
- **2026-08-31** — Build environment scripts: `scripts/use-tooling.sh`, `scripts/check-tooling.sh`.
  NDK 29.0.14206865 and CMake 3.31.6 installed into the shared `/mnt/workspace/.tooling/android-sdk`
  (owner approved adding to that directory; nothing existing there was modified).
- **2026-08-31** — `scripts/push-protracktor.sh` and `scripts/internal/git-askpass.sh`, adapted from
  the Kratkoza equivalents with the owner's permission to read them.

## Next

1. Gradle project skeleton — `app` module, Compose, minSdk 29 / targetSdk 36, off-tree build
   directory, debug build script.
2. Native layer proof of concept: `libopenmpt` + Oboe playing one `.mod` from a bare Activity. This
   is the step that decides whether the whole approach holds, so it comes before any UI work.
3. `sc68` integration for SNDH — the format that started this project.

## Known defects

None recorded. There is no code to defect.

## Branches

- `master` — repository base.
- `develop` — current work.
- `feature/project-scaffold` — this scaffolding. Not merged.
