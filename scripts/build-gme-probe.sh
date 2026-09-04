#!/usr/bin/env bash
# Build game-music-emu for the host and compile the console probe against it.
#
# The library is already vendored and its CMake works as-is, which is why this is six lines rather
# than the hundred `build-uade-probe.sh` needs. It exists so the measurement can be repeated by
# somebody who did not write it.
#
# **Built without zlib**, because this machine has `libz.so.1` at runtime and no headers. That only
# affects `.vgz`, which is gzip around a `.vgm`, and `probe-gme.py` unwraps those itself — so what
# gets measured is the VGM decoder, which is the part in question. The Android build does link
# zlib, from the NDK sysroot; that was checked in its `CMakeCache.txt` rather than assumed.
#
# Usage:  ./scripts/build-gme-probe.sh
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
BUILD="${GME_HOST_BUILD:-$HOME/.protracktor/gme-host}"

source "$ROOT/scripts/use-tooling.sh"

cmake -S "$ROOT/native/vendor/gme" -B "$BUILD" -G Ninja \
      -DCMAKE_BUILD_TYPE=Release -DBUILD_SHARED_LIBS=OFF >/dev/null
ninja -C "$BUILD" >/dev/null

mkdir -p "$ROOT/native/probe/gme/build"
gcc -O2 -Wall -o "$ROOT/native/probe/gme/build/probe-gme" \
    "$ROOT/native/probe/gme/probe_gme.c" \
    -I"$ROOT/native/vendor/gme" -I"$BUILD" "$BUILD/gme/libgme.a" -lm -lstdc++

echo "✅ built. Now: ./scripts/probe-gme.py"
