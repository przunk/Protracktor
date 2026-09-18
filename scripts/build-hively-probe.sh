#!/usr/bin/env bash
# Fetch HivelyTracker's standalone replayer and build the probe against it, twice.
#
# A backend is proved on the host before it is integrated. This is the
# cheapest of the five that have been through it: `hvl2wav/` is three source files -- `replay.c`,
# `replay.h`, `types.h` -- with no build system to configure, no second process, no replay binaries
# and no library to link. BSD 3-Clause, so the licence question that cost a day on sc68 and UADE
# does not arise.
#
# It is built **twice**, and that is the point of the script rather than an extra. Upstream's
# `types.h` typedefs `uint32` to `unsigned long`, which was 32 bits on the Amiga this replayer comes
# from and is 64 on every target we ship. `native/probe/hively/stdint/` holds a second set of
# typedefs with the widths the code was written for; if the two builds disagree on any file, the
# replayer depends on 32-bit wrapping and the app needs the narrow typedefs. If they agree, we know
# it rather than hope it.
#
# Nothing here touches the app, and nothing lands in the repository: the sources go to the same
# download cache the other fetches use.
#
# Usage:  ./scripts/build-hively-probe.sh [--force]
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
CACHE="${PROTRACKTOR_DOWNLOAD_CACHE:-$HOME/.protracktor/downloads}"
WORK="${HIVELY_HOST_BUILD:-$HOME/.protracktor/hively-host}"

# Pinned and verified, for the reason `fetch-native-deps.sh` gives: an unverified download is a
# supply chain with nobody watching it. V1_9 is the newest tag; upstream last moved in June 2023.
VERSION="V1_9"
URL="https://github.com/pete-gordon/hivelytracker/archive/refs/tags/${VERSION}.tar.gz"
SHA256="a3c6d8a041fe9952f0a72a31953461254b497d3bfbf3d07301d1bf4e9e8fb65d"

ARCHIVE="$CACHE/hivelytracker-${VERSION}.tar.gz"
SRC="$WORK/src"

[ "${1:-}" = "--force" ] && rm -rf "$WORK"
mkdir -p "$CACHE" "$WORK"

if [ ! -f "$ARCHIVE" ]; then
    echo "⬇  hivelytracker $VERSION"
    curl -sSfL -o "$ARCHIVE.part" "$URL"
    mv "$ARCHIVE.part" "$ARCHIVE"
fi

actual="$(sha256sum "$ARCHIVE" | cut -d' ' -f1)"
if [ "$actual" != "$SHA256" ]; then
    echo "❌ hivelytracker: checksum mismatch"
    echo "   expected $SHA256"
    echo "   actual   $actual"
    echo "   Refusing to unpack. GitHub's tag tarballs are generated, so this can change without the"
    echo "   tag changing -- verify the contents before updating the pin."
    exit 1
fi

if [ ! -f "$SRC/hvl2wav/replay.c" ]; then
    rm -rf "$SRC"; mkdir -p "$SRC"
    tar xzf "$ARCHIVE" -C "$SRC" --strip-components=1
fi

REPLAY="$SRC/hvl2wav"
OUT="$ROOT/native/probe/hively/build"
mkdir -p "$OUT"

# -Wall but not -Werror: this is 2006 Amiga C and the warnings are upstream's, not ours. What the
# build must not do is fail silently, so the compile lines are shown when they say anything.
#
# NOT -D_BIG_ENDIAN_. Upstream's makefile sets it for `PLATFORM=Linux`, which is wrong on every
# Linux anyone runs; it only reaches `hvl2wav.c`'s WAV writer, which we do not build, but copying a
# flag because a makefile had it is how that kind of thing spreads.
#
# The typedef override is forced with `-include`, not `-I`. A first attempt used the include path
# and measured nothing: `replay.c` says `#include "types.h"`, and the quoted form searches the
# including file's own directory first, so the replayer kept upstream's typedefs while the probe
# took the override -- two translation units reading the same struct at different offsets, which
# looks exactly like a portability bug and is not one. `-include` reaches both, and the shared
# include guard then makes the real `types.h` a no-op.
build() {  # name [override-header]
    local name="$1" override="${2:-}"
    local flags=(-I"$REPLAY" -I"$ROOT/native/probe/hively")
    [ -n "$override" ] && flags+=(-include "$override")
    gcc -O2 -Wall -std=c99 -o "$OUT/probe-hively-$name" \
        "$ROOT/native/probe/hively/probe_hively.c" "$REPLAY/replay.c" \
        "${flags[@]}" -lm
}

echo "→ building with upstream types.h (uint32 = unsigned long = $(getconf LONG_BIT) bits here)"
build native
echo "→ building with 32-bit typedefs"
build stdint "$ROOT/native/probe/hively/stdint/types.h"

echo "✅ built. Now: ./scripts/probe-hively.py"
