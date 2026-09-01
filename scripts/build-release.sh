#!/usr/bin/env bash
#
# Builds a signed release APK and reports which key actually signed it.
#
# Signing credentials never live in this repository. Put them in ~/.gradle/gradle.properties:
#
#   PRZUNK_UPLOAD_STORE_FILE=/absolute/path/to/protracktor-release.jks
#   PRZUNK_UPLOAD_STORE_PASSWORD=…
#   PRZUNK_UPLOAD_KEY_ALIAS=…
#   PRZUNK_UPLOAD_KEY_PASSWORD=…
#
# Without them the build still succeeds, signed with the local debug key. That keeps sideloading
# alive on a machine with no release key -- an unsigned APK cannot be installed at all -- but such
# an APK must never be published. Which key was used is printed rather than assumed.
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

if ! ./gradlew :app:assembleRelease --console=plain "$@" > "$LOG_FILE" 2>&1; then
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
        echo "       Set PRZUNK_UPLOAD_* in ~/.gradle/gradle.properties to sign with the upload key."
    else
        echo "   ✅ Release key."
        echo "       $signer_line"
    fi
else
    echo "   ⚠️  apksigner not found under $ANDROID_HOME/build-tools; signature not verified."
fi

echo
echo "✅ OK in $(($(date +%s) - started_at))s"
echo "📦 $artifact ($(du -h "$artifact" | cut -f1))"
