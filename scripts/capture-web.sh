#!/usr/bin/env bash
# SPDX-FileCopyrightText: 2026 Przunk
# SPDX-License-Identifier: GPL-3.0-or-later
set -euo pipefail
VISUAL_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$VISUAL_ROOT"
export PLAYWRIGHT_BROWSERS_PATH="$VISUAL_ROOT/user/web-visual/browsers"
if [[ ! -d scripts/web-visual/node_modules/playwright ]]; then
  echo 'Run ./scripts/setup-web-visual.sh first.' >&2
  exit 1
fi
exec node scripts/web-visual/capture.mjs "$@"
