#!/usr/bin/env bash
#
# Downloads the third-party decoder sources into native/vendor/.
#
# They are fetched rather than committed: they are large, they are not ours, and a pinned version
# with a verified checksum says more about what we built against than a copy in our history does.
# native/vendor/ is gitignored.
#
# Every download is pinned to an exact version and verified against a SHA-256 recorded here. An
# unverified download is a supply chain with nobody watching it.
#
# Usage:  ./scripts/fetch-native-deps.sh [--force]
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
VENDOR="$PROJECT_DIR/native/vendor"
CACHE="${PROTRACKTOR_DOWNLOAD_CACHE:-$HOME/.protracktor/downloads}"

FORCE=0
[ "${1:-}" = "--force" ] && FORCE=1

mkdir -p "$VENDOR" "$CACHE"

# name | version | url | sha256 | strip-components
fetch() {
    local name="$1" version="$2" url="$3" sha="$4" strip="$5"
    local dest="$VENDOR/$name"
    local archive="$CACHE/$(basename "$url")"

    if [ -f "$dest/.protracktor-version" ] && [ "$(cat "$dest/.protracktor-version")" = "$version" ] && [ "$FORCE" = "0" ]; then
        echo "  ✅ $name $version (already present)"
        return
    fi

    if [ ! -f "$archive" ]; then
        echo "  ⬇  $name $version"
        curl -sSfL -o "$archive.part" "$url"
        mv "$archive.part" "$archive"
    fi

    local actual
    actual="$(sha256sum "$archive" | cut -d' ' -f1)"
    if [ "$actual" != "$sha" ]; then
        echo "  ❌ $name: checksum mismatch"
        echo "     expected $sha"
        echo "     actual   $actual"
        echo "     Refusing to unpack. Delete $archive and retry, or update the pin deliberately."
        exit 1
    fi

    rm -rf "$dest"
    mkdir -p "$dest"
    tar xzf "$archive" -C "$dest" --strip-components="$strip"
    printf '%s\n' "$version" > "$dest/.protracktor-version"
    echo "  ✅ $name $version"
}

echo "Fetching native dependencies into native/vendor/"
echo

# libopenmpt -- BSD-3-Clause. Tracker formats: MOD, XM, S3M, IT and dozens of others.
# The "makefile" package is the one that carries build/android_ndk/Android.mk, which our
# CMakeLists.txt reads to get the authoritative source list.
fetch libopenmpt \
      "0.8.9" \
      "https://lib.openmpt.org/files/libopenmpt/src/libopenmpt-0.8.9+release.makefile.tar.gz" \
      "9273b88b67973cc69e54d748ab1b749399d6d07695f1c37d0c59f88b4106074f" \
      1

# sc68 -- GPL-2.0-OR-LATER.
#
# **2.2.1, and it is no longer what the app plays.** The app uses 3.0.0b, which exists only in
# SourceForge SVN and is fetched by ./scripts/fetch-sc68-svn.py. This release is kept because
# ./scripts/probe-sc68.py measures the two against each other, and the whole reason item 1 of
# GOAL.md round 5 took a day was that nobody could re-run the earlier measurement. Removing the
# ability to compare is how that happens again.
#
# Licence verified 2026-09-01: all 51 licensed sources say "or (at your option) any later version",
# whatever COPYING says on its own.
fetch sc68 \
      "2.2.1" \
      "https://downloads.sourceforge.net/project/sc68/sc68/2.2.1/sc68-2.2.1.tar.gz" \
      "d7371f0f406dc925debf50f64df1f0700e1d29a8502bb170883fc41cc733265f" \
      1

# ASAP -- GPL-2.0-OR-LATER (verified 2026-09-01: README and 34 sources say "either version 2 of the
# License, or (at your option) any later version"). Atari 8-bit POKEY: SAP and thirteen tracker
# formats. Transpiled from Ć to a single self-contained asap.c, so there is nothing to configure.
fetch asap \
      "8.0.0" \
      "https://downloads.sourceforge.net/project/asap/asap/8.0.0/asap-8.0.0.tar.gz" \
      "062d7db2a0747bf9200560141452f8fa2289909b147ac40a5bb5b60d5275a14f" \
      1

# game-music-emu -- LGPL-2.1-OR-LATER (verified 2026-09-01: 48 sources say "or (at your option) any
# later version"). Seven console families in one library: NES, SNES, Game Boy, Sega, PC Engine,
# ZX Spectrum AY, MSX. The only vendored library here that ships a working CMake build.
fetch gme \
      "0.6.5" \
      "https://github.com/libgme/game-music-emu/releases/download/0.6.5/libgme-0.6.5-src.tar.gz" \
      "a133f19278222136ba0d8c27b64a07987ba05fec9d2e6d293ccd8cabdd97ddbb" \
      1

# libsidplayfp -- GPL-2.0-OR-LATER (verified 2026-09-02: COPYING is the version 2 text again, and the
# sources say "either version 2 of the License, or (at your option) any later version"). Commodore
# 64: PSID and RSID, roughly 72,000 files in Modland alone.
fetch sidplayfp \
      "3.1.1" \
      "https://github.com/libsidplayfp/libsidplayfp/releases/download/v3.1.1/libsidplayfp-3.1.1.tar.gz" \
      "12b79190593bf480b2d11481b5c2de62bac07f344437a66cd8d887329875c626" \
      1

# HivelyTracker -- BSD-3-Clause (verified 2026-09-05: LICENSE is the three-clause text, Copyright
# (c) 2006-2018 Pete Gordon, and `hvl2wav/` carries no other notice). AHX and HVL, the Amiga
# synth-tracker formats libopenmpt has no loader for at all.
#
# The whole backend is `hvl2wav/replay.c` plus two headers -- the smallest vendored decoder here,
# smaller than ASAP. The rest of the archive is the tracker's GUI and is not built.
#
# GitHub generates tag tarballs rather than storing them, so this checksum can in principle change
# without the tag changing. It is pinned anyway: a download nobody checks is worse than one that
# occasionally needs a deliberate look.
fetch hively \
      "V1_9" \
      "https://github.com/pete-gordon/hivelytracker/archive/refs/tags/V1_9.tar.gz" \
      "a3c6d8a041fe9952f0a72a31953461254b497d3bfbf3d07301d1bf4e9e8fb65d" \
      1

# sc68 3.0.0b is not a release and has no tarball: it lives only in SourceForge SVN. Its fetch
# pins a revision and verifies a checksum manifest, which the tarball fetch above gets for free
# from a published sha256 -- so it is a separate script rather than another `fetch` line.
echo
if ! "$PROJECT_DIR/scripts/fetch-sc68-svn.py"; then
    echo "  ❌ sc68 3.0.0b could not be fetched. The Atari ST backend will not build."
    exit 1
fi

# ZXTune has no release tarball either, and its repository is 182 MB of which almost nothing is
# wanted -- so it is a sparse, blobless clone rather than a `fetch` line, for the same reason sc68
# is a script. Its own header explains the exclusions.
echo
if ! "$PROJECT_DIR/scripts/fetch-zxtune.py"; then
    echo "  ❌ ZXTune could not be fetched. The ZX Spectrum backend will not build."
    exit 1
fi

echo
echo "✅ done — sources in native/vendor/ (gitignored)"
