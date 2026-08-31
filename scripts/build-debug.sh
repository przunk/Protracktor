#!/usr/bin/env bash
#
# Builds the debug APK.
#
# Gradle's output goes to a log file, not to the terminal. On success you get four lines; on failure
# you get the part of the log that says what broke, and the path to the rest. Thousands of lines of
# task names are worth reading exactly never, and cost real money when an agent is the one reading.
#
# Usage:  ./scripts/build-debug.sh [extra gradle args…]
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=./use-tooling.sh
source "$SCRIPT_DIR/use-tooling.sh"
cd "$PROTRACKTOR_DIR"

# shellcheck source=./internal/build-common.sh
source "$SCRIPT_DIR/internal/build-common.sh"

LOG_FILE="$PROTRACKTOR_LOG_PREFIX-debug.log"
BUILD_APK="$PROTRACKTOR_BUILD_DIR_ROOT/app/outputs/apk/debug/app-debug.apk"

require_toolchain
write_local_properties

started_at=$(date +%s)
echo "🔨 Protracktor — debug APK…"

if ! ./gradlew :app:assembleDebug --console=plain "$@" > "$LOG_FILE" 2>&1; then
    report_failure "$LOG_FILE"
fi

artifact="$(publish_artifact "$BUILD_APK" debug)"

echo "✅ OK in $(($(date +%s) - started_at))s"
echo "📦 $artifact ($(du -h "$artifact" | cut -f1))"
echo "   adb install -r $artifact"
