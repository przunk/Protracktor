#!/usr/bin/env bash
# Fetch ayfly's replay library and build the ZX Spectrum probe against it.
#
# A backend is proved on the host before it is integrated. ZX Spectrum is the
# largest platform in Modland this build cannot play -- 23,891 files, of which 58 open -- and
# `./scripts/probe-platforms.py` is what says so.
#
# **ayfly rather than ZXTune**, which the Modizer survey also names. ZXTune covers more and is a
# 182 MB C++ project with its own build system; ayfly is about 900 KB, its `players/` already covers
# every name Modland files under Spectrum (PT3, PT2, STC, ASC, SQT, and more), and it renders into a
# buffer. That is the same shape of bargain HivelyTracker was, and the reason that one took an
# afternoon while UADE took a day.
#
# Only the replay library is built. `src/gui` needs wxWidgets and `unix/SDLAudio.cpp` needs SDL;
# neither is compiled, because `ay_initsongindirect` takes a null player and `ay_rendersongbuffer`
# hands the samples back rather than pushing them at a device.
#
# Usage:  ./scripts/build-ayfly-probe.sh [--force]
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
CACHE="${PROTRACKTOR_DOWNLOAD_CACHE:-$HOME/.protracktor/downloads}"
WORK="${AYFLY_HOST_BUILD:-$HOME/.protracktor/ayfly-host}"

# Pinned to a commit, not a branch: upstream last moved in December 2020, and "whatever master was
# that afternoon" is not a measurement anybody can repeat. A commit archive is also the only form
# GitHub generates from fixed content, so the checksum below means something.
REV="c1ff6d559edc24ec43c7df0bf23a4531b976133d"
URL="https://github.com/l29ah/ayfly/archive/${REV}.tar.gz"

ARCHIVE="$CACHE/ayfly-${REV:0:12}.tar.gz"
SRC="$WORK/src"

[ "${1:-}" = "--force" ] && rm -rf "$WORK"
mkdir -p "$CACHE" "$WORK"

if [ ! -f "$ARCHIVE" ]; then
    echo "⬇  ayfly ${REV:0:12}"
    curl -sSfL -o "$ARCHIVE.part" "$URL"
    mv "$ARCHIVE.part" "$ARCHIVE"
fi

if [ ! -f "$SRC/src/libayfly/ay.cpp" ]; then
    rm -rf "$SRC"; mkdir -p "$SRC"
    tar xzf "$ARCHIVE" -C "$SRC" --strip-components=1
fi

LIB="$SRC/src/libayfly"
OUT="$ROOT/native/probe/ayfly/build"
mkdir -p "$OUT"

# The source list is upstream's own, from `src/libayfly/Makefile.am`, minus the audio back end.
# Reading it from the makefile rather than restating it is the same rule the app's CMake follows for
# libopenmpt: a second copy of a source list is a copy that goes stale.
SOURCES=$(sed -n 's/^libayfly_a_SOURCES = //p' "$LIB/Makefile.am")
[ -n "$SOURCES" ] || { echo "❌ could not read the source list from Makefile.am"; exit 1; }

echo "→ building libayfly ($(echo "$SOURCES" | wc -w) sources) and z80ex"

# z80ex is C and its header declares C linkage, so it is compiled by `gcc` into its own object.
# Handing the `.c` to `g++` compiles it as C++ and mangles the names the C++ side is asking for
# unmangled -- which links to nothing and says so in fifteen lines of `undefined reference`.
gcc -O2 -w -std=c99 -c -o "$OUT/z80ex.o" "$LIB/z80ex/z80ex.c" \
    -I"$LIB/z80ex" -I"$LIB/z80ex/include"

# shellcheck disable=SC2086
g++ -O2 -w -std=c++17 -DDISABLE_AUDIO -o "$OUT/probe-ayfly" \
    "$ROOT/native/probe/ayfly/probe_ayfly.cpp" \
    $(for s in $SOURCES; do echo "$LIB/$s"; done) \
    "$OUT/z80ex.o" \
    -I"$LIB" -I"$LIB/z80ex" -I"$LIB/z80ex/include" -lm

echo "✅ built. Now: ./scripts/probe-ayfly.py"
