#!/usr/bin/env bash
#
# Builds the debug APK and puts it somewhere findable.
#
# The build directory lives outside the project tree (see settings.gradle.kts), so "look in
# app/build/outputs" is wrong here and would waste your time. The APK is copied to dist/ and its
# path is printed.
#
# Usage:  ./scripts/build-debug.sh [extra gradle args…]
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
cd "$PROJECT_DIR"

# shellcheck source=./use-tooling.sh
source "$SCRIPT_DIR/use-tooling.sh"

"$SCRIPT_DIR/check-tooling.sh" || { echo; echo "❌ toolchain incomplete, not building"; exit 1; }
echo

# Gradle needs to be told where the SDK is even though ANDROID_HOME is set, because Android Studio
# writes this file too and a stale one from another machine points somewhere that does not exist.
printf 'sdk.dir=%s\n' "$ANDROID_HOME" > local.properties

started=$(date +%s)
./gradlew :app:assembleDebug "$@"
elapsed=$(( $(date +%s) - started ))

apk="$(find "$PROTRACKTOR_BUILD_DIR_ROOT" -name '*-debug.apk' -newermt "-${elapsed} seconds" -print 2>/dev/null | head -1)"
if [ -z "$apk" ]; then
    apk="$(find "$PROTRACKTOR_BUILD_DIR_ROOT" -name '*-debug.apk' -print 2>/dev/null | head -1)"
fi

# A green build with no artifact is a failure, not a success. Saying so here is the difference
# between finding out now and finding out when the install fails.
[ -n "$apk" ] || { echo "❌ build reported success but produced no APK under $PROTRACKTOR_BUILD_DIR_ROOT"; exit 1; }

mkdir -p dist
version="$(sed -n 's/^ *versionName = "\(.*\)"/\1/p' app/build.gradle.kts | head -1)"
out="dist/protracktor-${version:-unknown}-debug.apk"
cp -f "$apk" "$out"

echo
echo "✅ $out  ($(du -h "$out" | cut -f1), ${elapsed}s)"
echo "   install with: adb install -r $out"
