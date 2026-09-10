#!/usr/bin/env bash
# SPDX-FileCopyrightText: 2026 Przunk
# SPDX-License-Identifier: GPL-3.0-or-later
#
# Emscripten, for the web build of the engine (docs/PLAN_WEB.md §13 S1).
#
# Kept out of the tree for the same reason the Android SDK is: it is a toolchain, not a source, and
# it is 1.5 GB. It lands beside the rest of the tooling so one directory holds everything fetched.
#
# Pinned, because a toolchain that moves under a measurement invalidates the measurement. 4.0.14 is
# the version this project was built with first; raising it is a decision, not a side effect.
set -euo pipefail

VERSION="${EMSDK_VERSION:-4.0.14}"
ROOT="${PROTRACKTOR_TOOLING:-/mnt/workspace/.tooling}"
DIR="$ROOT/emsdk"

if [[ -f "$DIR/upstream/emscripten/emcc" ]] && [[ "$(cat "$DIR/.protracktor-version" 2>/dev/null)" == "$VERSION" ]]; then
    echo "✅ emsdk $VERSION already at $DIR"
    exit 0
fi

mkdir -p "$ROOT"
if [[ ! -d "$DIR/.git" ]]; then
    echo "📥 cloning emsdk…"
    git clone --depth 1 https://github.com/emscripten-core/emsdk.git "$DIR"
fi

cd "$DIR"
git fetch --depth 1 origin main >/dev/null 2>&1 || true
./emsdk install "$VERSION"
./emsdk activate "$VERSION"
echo "$VERSION" > "$DIR/.protracktor-version"

echo "✅ emsdk $VERSION at $DIR"
echo "   source $DIR/emsdk_env.sh   # to use emcc directly"
