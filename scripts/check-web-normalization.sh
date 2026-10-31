#!/usr/bin/env bash
# SPDX-FileCopyrightText: 2026 Przunk
# SPDX-License-Identifier: GPL-3.0-or-later
set -euo pipefail
NORMALIZATION_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$NORMALIZATION_ROOT"
export PLAYWRIGHT_BROWSERS_PATH="$NORMALIZATION_ROOT/user/web-visual/browsers"
if [[ ! -d scripts/web-visual/node_modules/playwright ]]; then
    echo 'Run ./scripts/setup-web-visual.sh first.' >&2
    exit 1
fi
exec node scripts/web-visual/check-normalization.mjs "$@"
