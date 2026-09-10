#!/usr/bin/env bash
# SPDX-FileCopyrightText: 2026 Przunk
# SPDX-License-Identifier: GPL-3.0-or-later
#
# The page, on localhost (docs/PLAN_WEB.md §13 S3).
#
# `http://localhost` is a secure context, which is all `AudioWorklet` asks for -- no certificate, no
# COOP/COEP headers, nothing to configure. Reaching it from another machine is a later problem and a
# different one.
#
# Node rather than python's http.server because the module graph needs correct MIME types: an `.mjs`
# served as application/octet-stream is refused by the module loader, and the error names the
# import rather than the server.
set -euo pipefail
cd "$(dirname "$0")/.."
PORT="${1:-8173}"
[[ -f web/vendor/engine.wasm ]] || { echo "❌ no engine. Run scripts/build-web-engine.sh"; exit 1; }
echo "🌐 http://localhost:$PORT/src/   (ctrl-c to stop)"
exec node scripts/serve-web.mjs "$PORT"
