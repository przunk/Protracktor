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


# --- the WebAssembly build's seven lines ---------------------------------------------------------
#
# **ZXTune assumes `std::string_view::const_iterator` is a raw `const char*`.** It is, in the NDK's
# libc++, which is why the Android build never noticed. Emscripten's libc++ runs at ABI version 2,
# where that iterator is a `__wrap_iter` class -- deliberately, to stop code relying on the
# implementation detail -- and every `const auto*` initialised from `begin()` stops compiling.
#
# That is the whole of what stood between this project and a browser that plays the ZX Spectrum
# (`docs/BACKLOG.md` A32). It was recorded for months as "ZXTune does not build under Emscripten",
# inherited from a comment rather than from a log; building it produced exactly these seven, in five
# files, all the same idiom.
#
# **Not a fork** (`docs/ARCHITECTURE.md` §3). Nothing is redesigned and no behaviour changes: each
# line either lets the iterator keep its own type or asks the string_view for the pointer it is
# about to need anyway. Upstream has the same code today, checked 2026-09-15, so there is no newer
# revision to move to instead.
#
# **Applied here rather than committed into the tree**, because a fetch deletes and re-clones the
# whole directory -- an edit made by hand would vanish the next time anybody ran this script, and
# the symptom would be a build that worked yesterday.
PATCHES = [
    (
        1,
        "src/binary/format/lexic_analysis.cpp",
        "      for (const auto* lexemeEnd = lexemeStart + 1; !candidates.empty(); ++lexemeEnd)",
        "      for (auto lexemeEnd = lexemeStart + 1; !candidates.empty(); ++lexemeEnd)",
    ),
    (
        # `from_chars` wants pointers, so this asks the view for them rather than for iterators.
        1,
        "src/strings/conversion.h",
        "      const auto* const it = str.begin();\n      const auto* const lim = str.end();",
        "      const auto* const it = str.data();\n      const auto* const lim = str.data() + str.size();",
    ),
    (
        1,
        "src/formats/chiptune/aym/protracker3_vortex.cpp",
        "        const auto* it = val.begin();",
        "        auto it = val.begin();",
    ),
    (
        # A `std::find` over an array of strings, whose iterator is a wrapper for the same reason.
        1,
        "src/formats/chiptune/aym/protracker3_vortex.cpp",
        "        const auto* const notePos = std::find(NOTES.begin(), NOTES.end(), Val.substr(0, 2));",
        "        const auto notePos = std::find(NOTES.begin(), NOTES.end(), Val.substr(0, 2));",
    ),
    (
        1,
        "src/parameters/src/convert.cpp",
        "    const auto* src = val.begin();",
        "    auto src = val.begin();",
    ),
    (
        # **Twice in this file**, at two different decoders, and the count is written down rather
        # than replaced blindly: a patch that quietly matches a different number of times than its
        # author saw is a patch nobody is checking.
        2,
        "src/strings/src/encoding.cpp",
        "      for (const auto* it = str.begin(); it != str.end(); ++it)",
        "      for (auto it = str.begin(); it != str.end(); ++it)",
    ),
    (
        1,
        "src/strings/src/encoding.cpp",
        "    for (const auto* it = str.begin(); it != str.end();)",
        "    for (auto it = str.begin(); it != str.end();)",
    ),
]


def apply_patches():
    """
    Applies PATCHES, and **fails loudly when one no longer matches**.

    A patch that silently stops applying is the same failure as a glob that stops matching: the
    build goes on working on the machine that has the old tree and breaks on the next clean one,
    far from its cause. So a miss is an error here, with the file and the line it wanted -- which
    is also the signal that moving the pin needs this list looked at.
    """
    applied = 0
    for expected, relative, before, after in PATCHES:
        path = DEST / relative
        if not path.is_file():
            print(f"  ❌ zxtune: {relative} is not in the sparse checkout; PATCHES needs updating")
            return False
        text = path.read_text(encoding="utf-8")
        if before not in text and text.count(after) >= expected:
            continue  # already applied; --force re-clones, so this is belt and braces
        found = text.count(before)
        if found != expected:
            print(f"  ❌ zxtune: {relative} has {found} matches for a patch that expects {expected}")
            print(f"     wanted: {before.splitlines()[0].strip()}")
            return False
        path.write_text(text.replace(before, after), encoding="utf-8")
        applied += found
    print(f"  🩹 {applied} lines patched for the WebAssembly build (Emscripten's libc++ iterators)")
    return True


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

    if not apply_patches():
        return 1

    stamp.write_text(REVISION + "\n")
    size = sum(f.stat().st_size for f in DEST.rglob("*") if f.is_file()) / 1e6
    print(f"  ✅ zxtune {REVISION} ({size:.0f} MB checked out)")
    return 0


if __name__ == "__main__":
    sys.exit(main())
