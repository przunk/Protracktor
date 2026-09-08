#!/usr/bin/env bash
# SPDX-FileCopyrightText: 2026 Przunk
# SPDX-License-Identifier: GPL-3.0-or-later
#
# Everything the web player needs, in one archive, for a machine that is not this one.
#
# **The engine is built here and copied, not built there.** Compiling seven decoders through
# Emscripten needs a 1.5 GB toolchain and the better part of an hour; the result is one 2.6 MB
# `.wasm` that runs identically anywhere, because that is what WebAssembly is for. A Raspberry Pi
# needs node and nothing else.
set -euo pipefail
cd "$(dirname "$0")/.."

[[ -f web/vendor/engine.wasm ]] || { echo "❌ no engine. Run scripts/build-web-engine.sh first"; exit 1; }

OUT="dist/protracktor-web-$(date +%Y%m%d-%H%M%S).tar.gz"
mkdir -p dist
STAGE="$(mktemp -d)"
trap 'rm -rf "$STAGE"' EXIT

mkdir -p "$STAGE/protracktor-web/scripts"
cp -r web "$STAGE/protracktor-web/"
cp scripts/serve-web.mjs "$STAGE/protracktor-web/scripts/"
cp docs/WEB_SERVER.md "$STAGE/protracktor-web/README.md"

# The engine and the page carry the licence of the decoders linked into them: GPL-3.0-or-later.
# Serving them is conveying them, so the offer of source travels in the archive (docs/LICENSES.md).
cp LICENSE "$STAGE/protracktor-web/"

tar -czf "$OUT" -C "$STAGE" protracktor-web
echo "📦 $OUT  ($(du -h "$OUT" | cut -f1))"
echo
echo "On the other machine:"
echo "    tar xzf $(basename "$OUT")"
echo "    cd protracktor-web && node scripts/serve-web.mjs"
