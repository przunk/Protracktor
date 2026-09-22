#!/usr/bin/env bash
#
# Builds an Android App Bundle for Google Play, and refuses to hand over one Play would reject.
#
# The signing mechanism is deliberately identical to the other projects in this workshop, because
# they all sign with the same upload key through the same `PRZUNK_UPLOAD_*` Gradle properties.
#
# ## Credentials
#
# Two ways in, and neither writes anything down:
#
# 1. `~/.gradle/gradle.properties`, the way `docs/BUILD.md` describes for release APKs. If
#    `PRZUNK_UPLOAD_STORE_FILE` is already there, this script leaves it alone and does not ask.
# 2. Typed at the prompt. They are handed to Gradle through `ORG_GRADLE_PROJECT_*` environment
#    variables -- how Gradle takes a project property from the environment -- and live only as long
#    as this process. Nothing reaches the repository, gradle.properties, or the shell history.
#
# Without a terminal (CI, a hook), set these instead:
#   PRZUNK_UPLOAD_STORE_FILE  PRZUNK_UPLOAD_KEY_ALIAS
#   PRZUNK_UPLOAD_STORE_PASSWORD  PRZUNK_UPLOAD_KEY_PASSWORD
#
# Usage:  ./scripts/build-bundle.sh [extra gradle args…]
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=./use-tooling.sh
source "$SCRIPT_DIR/use-tooling.sh"
cd "$PROTRACKTOR_DIR"

# shellcheck source=./internal/build-common.sh
source "$SCRIPT_DIR/internal/build-common.sh"

LOG_FILE="$PROTRACKTOR_LOG_PREFIX-bundle.log"
BUILD_AAB="$PROTRACKTOR_BUILD_DIR_ROOT/app/outputs/bundle/release/app-release.aab"
DIST_DIR="$PROTRACKTOR_DIR/dist"

fail() {
    echo "❌ $1"
    shift || true
    for line in "$@"; do echo "   $line"; done
    exit 1
}

# True when the signing configuration is already somewhere Gradle will find it on its own.
already_configured() {
    grep -qs '^[[:space:]]*PRZUNK_UPLOAD_STORE_FILE[[:space:]]*=' "$HOME/.gradle/gradle.properties"
}

collect_signing_configuration() {
    local entered current

    if already_configured && [ -z "${PRZUNK_UPLOAD_STORE_FILE:-}" ]; then
        echo "🔑 Using PRZUNK_UPLOAD_* from ~/.gradle/gradle.properties"
        return
    fi

    if [ -t 0 ]; then
        current="${PRZUNK_UPLOAD_STORE_FILE:-}"
        if [ -n "$current" ]; then
            read -r -p "Upload keystore path [$current]: " entered
            PRZUNK_UPLOAD_STORE_FILE="${entered:-$current}"
        else
            read -r -p "Upload keystore path: " PRZUNK_UPLOAD_STORE_FILE
        fi
    fi
    [ -n "${PRZUNK_UPLOAD_STORE_FILE:-}" ] || fail "no keystore given" \
        "Set PRZUNK_UPLOAD_STORE_FILE, put it in ~/.gradle/gradle.properties, or run this from a terminal."

    # ~ is the shell's, not the filesystem's; a typed path keeps it literal.
    PRZUNK_UPLOAD_STORE_FILE="${PRZUNK_UPLOAD_STORE_FILE/#\~/$HOME}"
    [ -f "$PRZUNK_UPLOAD_STORE_FILE" ] || fail "keystore not found: $PRZUNK_UPLOAD_STORE_FILE"

    if [ -t 0 ]; then
        current="${PRZUNK_UPLOAD_KEY_ALIAS:-protracktor-upload}"
        read -r -p "Key alias [$current]: " entered
        PRZUNK_UPLOAD_KEY_ALIAS="${entered:-$current}"
    fi
    [ -n "${PRZUNK_UPLOAD_KEY_ALIAS:-}" ] || fail "no key alias given"

    if [ -t 0 ]; then
        read -r -s -p "Keystore password: " PRZUNK_UPLOAD_STORE_PASSWORD
        echo
    fi
    [ -n "${PRZUNK_UPLOAD_STORE_PASSWORD:-}" ] || fail "no keystore password given"

    if [ -t 0 ]; then
        read -r -s -p "Key password [Enter = keystore password]: " entered
        echo
        PRZUNK_UPLOAD_KEY_PASSWORD="${entered:-$PRZUNK_UPLOAD_STORE_PASSWORD}"
    fi
    PRZUNK_UPLOAD_KEY_PASSWORD="${PRZUNK_UPLOAD_KEY_PASSWORD:-$PRZUNK_UPLOAD_STORE_PASSWORD}"

    # Gradle reads a project property from ORG_GRADLE_PROJECT_<name>, so the signingConfig in
    # app/build.gradle.kts picks these up without any of them touching a file.
    export ORG_GRADLE_PROJECT_PRZUNK_UPLOAD_STORE_FILE="$PRZUNK_UPLOAD_STORE_FILE"
    export ORG_GRADLE_PROJECT_PRZUNK_UPLOAD_KEY_ALIAS="$PRZUNK_UPLOAD_KEY_ALIAS"
    export ORG_GRADLE_PROJECT_PRZUNK_UPLOAD_STORE_PASSWORD="$PRZUNK_UPLOAD_STORE_PASSWORD"
    export ORG_GRADLE_PROJECT_PRZUNK_UPLOAD_KEY_PASSWORD="$PRZUNK_UPLOAD_KEY_PASSWORD"

    echo "🔑 $PRZUNK_UPLOAD_STORE_FILE (alias $PRZUNK_UPLOAD_KEY_ALIAS) — passwords not stored"
}

# The versionName is still a literal in build.gradle.kts. The versionCode is not -- it is the commit
# count, computed the same way here as there. A bundle cannot be asked what it contains the way an
# APK can, so these two expressions have to agree; if build.gradle.kts changes how it counts, this
# line changes with it.
version_name="$(sed -n 's/.*versionName = "\(.*\)".*/\1/p' app/build.gradle.kts | head -n 1)"
version_name="${version_name%%\$*}"
version_code="$(git rev-list --count HEAD 2>/dev/null || echo 1)"

artifact="$DIST_DIR/protracktor-${version_name:-unknown}-${version_code:-0}.aab"

echo "🔨 Protracktor — Play bundle ${version_name:-?} (${version_code:-?})"

# Play rejects a versionCode it has already seen. The versionCode is the commit count, so this file
# existing means this very commit count was bundled before: a rebuild is fine, but if that bundle
# was uploaded, this one cannot be -- there is no number to bump, only a commit to make.
if [ -f "$artifact" ]; then
    echo "ℹ️  A bundle for versionCode ${version_code} already exists and will be replaced."
    echo "   If it was uploaded, Play will refuse this one: the versionCode moves only with a commit."
fi

require_toolchain
write_local_properties
collect_signing_configuration
echo

started_at=$(date +%s)
if ! ./gradlew :app:bundleRelease --console=plain "$@" > "$LOG_FILE" 2>&1; then
    report_failure "$LOG_FILE"
fi

[ -f "$BUILD_AAB" ] || fail "the build reported success but produced no bundle at $BUILD_AAB"

# A bundle carries a JAR signature, not an APK one, so apksigner cannot read it -- which is why this
# is not build-release.sh with a different Gradle task.
JARSIGNER="$JAVA_HOME/bin/jarsigner"
[ -x "$JARSIGNER" ] || fail "jarsigner is unavailable in $JAVA_HOME"

VERIFY_LOG="$PROTRACKTOR_LOG_PREFIX-bundle-verify.log"
if ! "$JARSIGNER" -J-Duser.language=en -verify "$BUILD_AAB" > "$VERIFY_LOG" 2>&1 ||
    ! grep -Fq "jar verified." "$VERIFY_LOG"; then
    fail "the bundle carries no verifiable signature" "Full log: $VERIFY_LOG"
fi

signer="$("$JAVA_HOME/bin/keytool" -printcert -jarfile "$BUILD_AAB" 2>/dev/null \
    | grep -m1 'Owner:' || true)"
[ -n "$signer" ] || fail "no certificate found in the bundle"

# The hard rule, and the other reason this is a separate script. For an APK the debug fallback earns
# its place: an unsigned APK cannot be sideloaded at all, so a debug-signed one is still useful and
# build-release.sh only warns. For a bundle headed to Play it buys nothing except a rejection
# discovered later instead of now.
if echo "$signer" | grep -qi 'CN=Android Debug'; then
    fail "DEBUG KEY — Play will reject this bundle." \
        "$signer" \
        "" \
        "Gradle fell back to the debug key, which means it never saw the credentials." \
        "Check the alias exists in that keystore: keytool -list -keystore <path>"
fi

mkdir -p "$DIST_DIR"
cp "$BUILD_AAB" "$artifact"

echo "🔎 $signer"
echo "✅ OK in $(($(date +%s) - started_at))s"
echo "📦 $artifact ($(du -h "$artifact" | cut -f1))"
command -v sha256sum >/dev/null 2>&1 && sha256sum "$artifact"
echo
echo "Upload at https://play.google.com/console → Protracktor → Testing → Closed testing."
