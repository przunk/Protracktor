#!/usr/bin/env python3
"""Fetch just enough of ZXTune to build the AY backend.

ZXTune has no release tarball and the repository is 182 MB, of which almost none is wanted: the
`3rdparty` tree alone is 280 MB and only `fmt` is needed, and 60 MB of what is left are HVSC
song-length databases checked in as `.md5` files. So this is a **sparse, blobless clone** rather
than a download -- the same reason `fetch-sc68-svn.py` exists rather than another `fetch` line in
`fetch-native-deps.sh`.

Pinned to a commit. "Whatever master was that afternoon" is not something anybody can reproduce, and
`docs/PLAN_FORMATS.md` §7 records a measurement made against exactly this one.

Usage:  ./scripts/fetch-zxtune.py [--force]
"""
from __future__ import annotations

import argparse
import pathlib
import shutil
import subprocess
import sys

ROOT = pathlib.Path(__file__).resolve().parent.parent
DEST = ROOT / "native" / "vendor" / "zxtune"

REPO = "https://github.com/vitamin-caig/zxtune.git"
# Measured against this revision on 2026-09-07: 72 of 72 sampled Modland files played.
REVISION = "c93e81d"

# What the AY path needs and nothing else. `3rdparty/fmt` is the only third-party component on it;
# `3rdparty/z80ex` is deliberately absent, being GPL-2-**only** and reachable from one plugin
# (`ayemul`, for `.ay`) that is not built. See `docs/LICENSES.md`.
# Non-cone patterns, because the interesting part is the exclusion. `src/core/plugins/players/sid`
# carries HVSC's song-length databases as checked-in `.md5` files -- about 60 MB of the 109 that a
# plain `src` checkout costs, for a plugin this build does not compile. We have our own copy of
# those lengths already, downloaded on request.
SPARSE = [
    "/src/",
    "/include/",
    "/make/",
    "/3rdparty/fmt/",
    "/LICENSE.md",
    "!/src/core/plugins/players/sid/",
]


def run(*args: str, cwd: pathlib.Path | None = None) -> None:
    subprocess.run(args, cwd=cwd, check=True, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--force", action="store_true")
    args = parser.parse_args()

    stamp = DEST / ".protracktor-version"
    if stamp.is_file() and stamp.read_text().strip() == REVISION and not args.force:
        print(f"  ✅ zxtune {REVISION} (already present)")
        return 0

    if DEST.exists():
        shutil.rmtree(DEST)
    DEST.parent.mkdir(parents=True, exist_ok=True)

    print(f"  ⬇  zxtune {REVISION} (sparse, blobless)")
    try:
        # `--filter=blob:none` fetches file contents on demand, so the 280 MB of `3rdparty` never
        # arrives. `--no-checkout` first, so nothing is written before the sparse set is chosen.
        run("git", "clone", "--quiet", "--filter=blob:none", "--no-checkout", REPO, str(DEST))
        run("git", "sparse-checkout", "set", "--no-cone", *SPARSE, cwd=DEST)
        run("git", "checkout", "--quiet", REVISION, cwd=DEST)
    except subprocess.CalledProcessError:
        print("  ❌ zxtune could not be fetched. The ZX Spectrum backend will not build.")
        return 1

    head = subprocess.run(["git", "rev-parse", "--short", "HEAD"], cwd=DEST,
                          capture_output=True, text=True).stdout.strip()
    if not head.startswith(REVISION[:7]):
        print(f"  ❌ zxtune: checked out {head}, expected {REVISION}")
        return 1

    stamp.write_text(REVISION + "\n")
    size = sum(f.stat().st_size for f in DEST.rglob("*") if f.is_file()) / 1e6
    print(f"  ✅ zxtune {REVISION} ({size:.0f} MB checked out)")
    return 0


if __name__ == "__main__":
    sys.exit(main())
