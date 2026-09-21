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

# Applies our patches to a fetched tree: native/patches/<name>/*.patch, in name order.
#
# **Idempotent, and checked in both directions.** A patch that applies is applied; one whose
# reverse applies is already in; one that does neither means the upstream file moved under it, and
# that stops the fetch rather than building an unpatched decoder that looks patched. Each patch says
# at its top why it exists.
apply_patches() {
    local name="$1" dest="$VENDOR/$1" patch_file
    for patch_file in "$PROJECT_DIR/native/patches/$name/"*.patch; do
        [ -e "$patch_file" ] || continue
        if patch -d "$dest" -p1 -R --dry-run --silent < "$patch_file" > /dev/null 2>&1; then
            echo "  ✅ $name: $(basename "$patch_file") (already applied)"
        elif patch -d "$dest" -p1 --forward --silent < "$patch_file" > /dev/null; then
            echo "  🩹 $name: $(basename "$patch_file")"
        else
            echo "  ❌ $name: $(basename "$patch_file") no longer applies -- the upstream file changed."
            exit 1
        fi
    done
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
# One patch, for text only: a MOD's title in CP437 came out as U+FFFD (docs/BACKLOG.md A47).
apply_patches libopenmpt

# sc68 -- GPL-2.0-OR-LATER.
#
# **2.2.1, and it is no longer what the app plays.** The app uses 3.0.0b, which exists only in
# SourceForge SVN and is fetched by ./scripts/fetch-sc68-svn.py. This release is kept because
# ./scripts/probe-sc68.py measures the two against each other, and a measurement nobody can re-run
# costs a day the next time the question is asked. Removing the ability to compare is how that
# happens.
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

# minimp3 -- CC0-1.0, a public domain dedication (verified 2026-09-10: `LICENSE` is the CC0 text and
# both headers repeat "the author(s) have dedicated all copyright and related and neighboring rights
# to this software to the public domain worldwide"). MP3, and nothing else.
#
# **Two headers, no library.** `minimp3.h` is the decoder and `minimp3_ex.h` adds the part that
# matters here -- a duration for a variable-bitrate file and an index to seek with, neither of which
# a plain frame decoder can give. `docs/BACKLOG.md` A29 chose this over Android's own MediaCodec: the
# engine is native from the file to the speaker, and routing one format through the platform would
# mean two playback paths to keep in step and nothing at all for the browser.
#
# Pinned to a commit, because there are no releases. GitHub generates these tarballs rather than
# storing them, so the checksum can in principle move without the commit moving -- the same caveat
# HivelyTracker's tag tarball carries above, and pinned anyway for the same reason.
fetch minimp3 \
      "ea99364" \
      "https://codeload.github.com/lieff/minimp3/tar.gz/ea99364f61c14656440e8d77e9c233ccf3124633" \
      "5628166eb82a9bb581317918a334c317a2c0a30278bb14a20381307976768f34" \
      1

# lhasa -- ISC (verified 2026-09-09: COPYING.md is the ISC text, Copyright (c) 2011-2025 Simon
# Howard, and all 35 sources and headers under lib/ repeat the grant per file). An LHA/LZH
# decompressor, and the only one here that decodes no music at all.
#
# It is vendored for two things that turn out to be the same thing. ZXTune's `.ym` and `.vtx`
# decoders read an LHA-compressed stream and were excluded from our build for want of it
# (`native/backends/zxtune/CMakeLists.txt`), which left 4,961 Modland `.ym` files indexed and
# unopenable -- `docs/STATUS.md` C20. And every UnExoticA tune lives inside a `.lha` archive
# (`docs/PLAN_CATALOGUES.md`), which needs the other half of this library, the archive reader.
#
# ZXTune bundles its own patched copy of an older lhasa. We fetch upstream instead and adapt to it
# in `native/backends/zxtune/lha_zxtune.cpp`: one lhasa in the tree, pinned and checksummed like
# everything else here, rather than two of which one arrives inside a 182 MB sparse clone.
fetch lhasa \
      "0.6.0" \
      "https://github.com/fragglet/lhasa/releases/download/v0.6.0/lhasa-0.6.0.tar.gz" \
      "9840154367f73e9d9c3196f944a121ab4d398d84e921c8fe8fca8a931274aed7" \
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
