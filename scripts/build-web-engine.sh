#!/usr/bin/env bash
# SPDX-FileCopyrightText: 2026 Przunk
# SPDX-License-Identifier: GPL-3.0-or-later
#
# The engine, as WebAssembly (docs/PLAN_WEB.md §13 S1).
#
# **The same `engine.cpp` the phone links**, with `player_wasm.cpp` where `player_oboe.cpp` goes on
# Android. That is the whole point of the S0 split, and it is checked rather than assumed: this
# script builds from the shared sources and nothing is copied or forked.
#
# Output lands in web/vendor/, which is gitignored: it is build output, and this repository already
# keeps build output out of the tree because the mount cannot take it (BUILD.md).
set -euo pipefail

cd "$(dirname "$0")/.."
ROOT="$PWD"
EMSDK="${PROTRACKTOR_TOOLING:-/mnt/workspace/.tooling}/emsdk"

[[ -f "$EMSDK/emsdk_env.sh" ]] || { echo "❌ no emsdk. Run scripts/fetch-emsdk.sh"; exit 1; }
# CMake and Ninja come from the Android SDK's copy, which is the one every other build here uses --
# there is no system cmake on this machine and adding a second is a second thing to keep in step.
source "$(dirname "$0")/use-tooling.sh" >/dev/null 2>&1 || true
# shellcheck disable=SC1091
source "$EMSDK/emsdk_env.sh" >/dev/null 2>&1

BUILD="${PROTRACKTOR_BUILD_DIR_ROOT:-$HOME/.protracktor/build}/web-engine"
OUT="$ROOT/web/vendor"
mkdir -p "$BUILD" "$OUT"

# Emscripten builds a port the first time something asks for it, and 353 parallel compilations all
# ask at once: the first `-sUSE_ZLIB=1` file to run creates the cache, the rest race it, and the
# error is `FileNotFoundError: cache.lock` from inside the compiler rather than anything about zlib.
# Building the port once, serially, before the parallel build is the documented cure.
echo "📦 zlib port…"
embuilder build zlib >"$BUILD/ports.log" 2>&1 || { tail -20 "$BUILD/ports.log"; exit 1; }

echo "🔧 configuring…"
emcmake cmake -S "$ROOT/native" -B "$BUILD" \
    -DCMAKE_BUILD_TYPE=Release \
    -DPROTRACKTOR_BUILD_ENGINE=OFF \
    -DPROTRACKTOR_WITH_ZXTUNE=OFF \
    -G Ninja >"$BUILD/configure.log" 2>&1 || { tail -30 "$BUILD/configure.log"; exit 1; }

echo "🔨 backends…"
# `gme_static` by name, because native/backends/gme adds the vendored tree with EXCLUDE_FROM_ALL:
# on Android it is pulled in by linking gme::gme into the engine target, and here there is no engine
# target to pull it. Without this the whole build succeeds and the link fails on `gme_play`, which
# looks like a missing symbol rather than a library nobody asked to be built.
cmake --build "$BUILD" -j "$(nproc)" --target all gme_static \
    >"$BUILD/backends.log" 2>&1 || { tail -40 "$BUILD/backends.log"; exit 1; }

# The engine and its web host are linked here rather than through CMake: the target needs Emscripten
# link flags that mean nothing to the Android build, and adding a second engine target to
# native/engine/CMakeLists.txt would put a browser's concerns in the file the phone builds from.
V="$ROOT/native/vendor"; B="$ROOT/native/backends"
echo "🔗 linking…"
emcc "$ROOT/native/engine/engine.cpp" "$ROOT/native/engine/player_wasm.cpp" \
    -o "$OUT/engine.mjs" \
    -std=gnu++20 -fexceptions -frtti -O3 \
    -DFMT_CONSTEVAL= -DHAVE_ZLIB_H -DPROTRACKTOR_WITH_ZXTUNE=0 \
    -I"$V/libopenmpt" -I"$V/libopenmpt/src" -I"$V/libopenmpt/common" \
    -I"$B/sc68/generated" -I"$B/sc68/generated/sc68" -I"$V/sc68-3" -I"$V/sc68-3/libsc68" \
    -I"$V/sc68-3/libsc68/sc68" -I"$V/sc68-3/libsc68/emu68" -I"$V/sc68-3/libsc68/io68" \
    -I"$V/sc68-3/file68" -I"$V/sc68-3/file68/sc68" -I"$V/sc68-3/unice68" \
    -I"$V/asap" -I"$V/gme" -I"$V/gme/gme" \
    -I"$V/sidplayfp/src" -I"$V/sidplayfp/src/builders/sidlite-builder" -I"$B/sidplayfp/public" \
    -I"$V/hively/hvl2wav" \
    $(find "$BUILD" -name '*.a' | sort) \
    -sUSE_ZLIB=1 \
    -sMODULARIZE=1 -sEXPORT_ES6=1 -sENVIRONMENT=web,worker,node \
    -sALLOW_MEMORY_GROWTH=1 -sINITIAL_MEMORY=33554432 \
    -sEXPORTED_RUNTIME_METHODS=ccall,cwrap,UTF8ToString,HEAPU8,HEAPF32 \
    -sEXPORTED_FUNCTIONS=_malloc,_free \
    2>&1 | grep -vE "^$" | head -40

ls -l "$OUT"/engine.* | awk '{printf "📦 %s  %.2f MB\n", $NF, $5/1e6}'
