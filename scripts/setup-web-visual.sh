#!/usr/bin/env bash
# SPDX-FileCopyrightText: 2026 Przunk
# SPDX-License-Identifier: GPL-3.0-or-later
set -euo pipefail
VISUAL_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
export PLAYWRIGHT_BROWSERS_PATH="$VISUAL_ROOT/user/web-visual/browsers"
mkdir -p "$PLAYWRIGHT_BROWSERS_PATH"
npm ci --prefix "$VISUAL_ROOT/scripts/web-visual" --ignore-scripts --no-audit --no-fund \
  --cache "$VISUAL_ROOT/user/web-visual/npm-cache"
node "$VISUAL_ROOT/scripts/web-visual/node_modules/playwright/cli.js" install --only-shell chromium
node "$VISUAL_ROOT/scripts/web-visual/linux-libs.mjs"
printf '\nVisual browser installed in %s\n' "$PLAYWRIGHT_BROWSERS_PATH"
