#!/usr/bin/env bash
#
# Verifies that everything a Protracktor build needs is present, before a build spends ten minutes
# discovering it is not. Prints what it found, not just a verdict, because a wrong-but-present
# toolchain is the failure that costs the most time to diagnose.
set -uo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=./use-tooling.sh
source "$SCRIPT_DIR/use-tooling.sh"

failures=0
ok()   { printf '  ✅ %-14s %s\n' "$1" "$2"; }
fail() { printf '  ❌ %-14s %s\n' "$1" "$2"; failures=$((failures + 1)); }

echo "Protracktor toolchain"
echo

if [ -x "$JAVA_HOME/bin/java" ]; then
    ok "JDK" "$("$JAVA_HOME/bin/java" -version 2>&1 | head -1)"
else
    fail "JDK" "not executable at $JAVA_HOME/bin/java"
fi

if [ -d "$ANDROID_HOME/platforms" ]; then
    ok "SDK platforms" "$(ls "$ANDROID_HOME/platforms" | tr '\n' ' ')"
else
    fail "SDK platforms" "missing $ANDROID_HOME/platforms"
fi

if [ -d "$ANDROID_HOME/build-tools" ]; then
    ok "build-tools" "$(ls "$ANDROID_HOME/build-tools" | tr '\n' ' ')"
else
    fail "build-tools" "missing $ANDROID_HOME/build-tools"
fi

if [ -f "$ANDROID_NDK_HOME/source.properties" ]; then
    ok "NDK" "$PROTRACKTOR_NDK_VERSION at $ANDROID_NDK_HOME"
else
    fail "NDK" "missing $ANDROID_NDK_HOME — install with:
                   sdkmanager --sdk_root=\"\$ANDROID_HOME\" \"ndk;$PROTRACKTOR_NDK_VERSION\""
fi

if command -v cmake >/dev/null 2>&1; then
    ok "CMake" "$(cmake --version | head -1)"
else
    fail "CMake" "not on PATH — expected $ANDROID_HOME/cmake/$PROTRACKTOR_CMAKE_VERSION/bin"
fi

if command -v ninja >/dev/null 2>&1; then
    ok "Ninja" "$(ninja --version)"
else
    fail "Ninja" "not on PATH — it ships inside the SDK cmake package"
fi

if command -v git >/dev/null 2>&1; then
    ok "git" "$(git --version)"
else
    fail "git" "not installed"
fi

echo
if [ "$failures" -eq 0 ]; then
    echo "✅ toolchain complete"
else
    echo "❌ $failures problem(s) above"
fi
exit "$failures"
