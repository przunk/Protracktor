#!/usr/bin/env bash
#
# Builds a minified release APK for device testing, signed with the local debug certificate.
# The upload key belongs to the owner's AAB workflow; this script never selects it.
#
# Usage:  ./scripts/build-release.sh [extra gradle args…]
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=./use-tooling.sh
source "$SCRIPT_DIR/use-tooling.sh"
cd "$PROTRACKTOR_DIR"

# shellcheck source=./internal/build-common.sh
source "$SCRIPT_DIR/internal/build-common.sh"

LOG_FILE="$PROTRACKTOR_LOG_PREFIX-release.log"
BUILD_APK="$PROTRACKTOR_BUILD_DIR_ROOT/app/outputs/apk/release/app-release.apk"

require_toolchain
write_local_properties

started_at=$(date +%s)
echo "🔨 Protracktor — release APK…"

if ! ./gradlew :app:assembleRelease --console=plain "$@" -PprotracktorTestApk=true > "$LOG_FILE" 2>&1; then
    report_failure "$LOG_FILE"
fi

artifact="$(publish_artifact "$BUILD_APK" release)"

echo
echo "🔎 Signature"
APKSIGNER="$(ls -d "$ANDROID_HOME"/build-tools/*/apksigner 2>/dev/null | sort -V | tail -n 1 || true)"
if [ -n "$APKSIGNER" ]; then
    # The certificate's subject is the only trustworthy answer to "which key is this?" -- the Gradle
    # config can look right and still have fallen back.
    signer_line="$("$APKSIGNER" verify --print-certs "$artifact" 2>/dev/null | grep -m1 'certificate DN' || true)"
    if [ -z "$signer_line" ]; then
        echo "   ⚠️  Could not read the certificate. The APK may be unsigned."
    elif echo "$signer_line" | grep -qi 'CN=Android Debug'; then
        echo "   ⚠️  DEBUG KEY — sideloadable, NOT publishable."
        echo "       $signer_line"
        echo "       Test certificate only; the release variant remains minified."
    else
        echo "   ✅ Release key."
        echo "       $signer_line"
    fi
else
    echo "   ⚠️  apksigner not found under $ANDROID_HOME/build-tools; signature not verified."
fi

echo
echo "✅ OK in $(($(date +%s) - started_at))s"
echo "Source: $(git rev-parse --short HEAD)$(if [ -n "$(git status --porcelain --untracked-files=normal)" ]; then printf ' (dirty)'; fi)"
echo "📦 $artifact ($(du -h "$artifact" | cut -f1))"
