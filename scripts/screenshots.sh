#!/usr/bin/env bash
# SPDX-FileCopyrightText: 2026 Przunk
# SPDX-License-Identifier: GPL-3.0-or-later
#
# Pictures of pieces of the app at a phone's width, drawn in JVM tests through Robolectric
# (`docs/WISHLIST.md` B39), for a look before an APK goes out. Not part of test-protracktor.sh: they
# take minutes. The pictures land in app/build/screenshots/, which git ignores.
set -euo pipefail
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "$SCRIPT_DIR/use-tooling.sh"
cd "$PROTRACKTOR_DIR"
printf 'sdk.dir=%s\n' "$ANDROID_HOME" > local.properties
LOG_FILE="$PROTRACKTOR_LOG_PREFIX-screenshots.log"
echo "📸 Protracktor — screenshots…"
if ! ./gradlew :app:testDebugUnitTest -Pscreenshots --rerun --console=plain > "$LOG_FILE" 2>&1; then
    echo "❌ Failed. Full log: $LOG_FILE"
    grep -aE "FAILED|^e: " "$LOG_FILE" | head -10
    exit 1
fi
ls -1 app/build/screenshots/*.png | sed 's/^/   /'
echo "✅ $(ls -1 app/build/screenshots/*.png | wc -l) pictures in $PROTRACKTOR_DIR/app/build/screenshots/"
