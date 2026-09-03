#!/usr/bin/env bash
#
# Shared by the build scripts. Sourced after use-tooling.sh, never executed.

# Fail before a build spends minutes discovering the toolchain is incomplete.
require_toolchain() {
    if ! "$SCRIPT_DIR/check-tooling.sh" > "$PROTRACKTOR_LOG_PREFIX-toolchain.log" 2>&1; then
        echo "❌ Toolchain incomplete:"
        sed 's/^/   /' "$PROTRACKTOR_LOG_PREFIX-toolchain.log"
        exit 1
    fi
    if [ ! -d "$PROTRACKTOR_DIR/native/vendor/libopenmpt" ]; then
        echo "❌ Native sources are missing. Run ./scripts/fetch-native-deps.sh first."
        exit 1
    fi
}

# ANDROID_HOME is exported, but Android Studio writes local.properties too and a stale one from
# another machine points somewhere that does not exist.
write_local_properties() {
    printf 'sdk.dir=%s\n' "$ANDROID_HOME" > "$PROTRACKTOR_DIR/local.properties"
}

# Prints the part of a Gradle log worth reading and exits. Everything else in that file is task
# names.
report_failure() {
    local log_file="$1"
    echo "❌ Build failed. What went wrong:"
    if grep -aq '^\* What went wrong:' "$log_file"; then
        sed -n '/^\* What went wrong:/,/^\* Try:/p' "$log_file" \
            | grep -av '^\* Try:' | sed "s|$PROTRACKTOR_DIR/||" | head -n 25
    fi
    # Kotlin errors, then C/C++ errors -- the native build reports through neither of the above.
    grep -a '^e: ' "$log_file" | sed "s|file://$PROTRACKTOR_DIR/||" | head -n 25
    grep -aE '(error|fatal error):' "$log_file" | sed "s|$PROTRACKTOR_DIR/||" | head -n 25
    echo
    echo "Full log: $log_file"
    exit 1
}

# Copies the built APK into dist/ under a name that says which build it is.
#
# versionCode as well as versionName, and a timestamp: two builds of one versionName are otherwise
# indistinguishable once they leave dist/, which is exactly when it matters which one is on the
# phone. Matches how the other projects in this workshop name their artifacts.
publish_artifact() {
    local build_apk="$1" kind="$2"

    # A green build with no artifact is a failure, not a success. Gradle can report success while
    # skipping the packaging task.
    if [ ! -f "$build_apk" ]; then
        echo "❌ Build reported success but produced no APK at $build_apk" >&2
        exit 1
    fi

    # Read out of the artifact rather than out of the source. The versionCode is derived from the
    # commit count now, so there is no number in build.gradle.kts to scrape -- and asking the APK
    # what it is cannot disagree with what it is.
    local version_name version_code stamp infix artifact aapt
    aapt="$(ls -d "$ANDROID_HOME"/build-tools/*/aapt2 2>/dev/null | sort -V | tail -n 1)"
    if [ -x "$aapt" ]; then
        local badging
        badging="$("$aapt" dump badging "$build_apk" 2>/dev/null | head -n 1)"
        version_name="$(sed -n "s/.*versionName='\([^']*\)'.*/\1/p" <<< "$badging")"
        version_code="$(sed -n "s/.*versionCode='\([^']*\)'.*/\1/p" <<< "$badging")"
    fi
    version_name="${version_name:-unknown}"
    version_code="${version_code:-0}"
    stamp="$(date +%Y%m%d-%H%M%S)"
    infix=""
    [ "$kind" = "debug" ] && infix="debug-"

    mkdir -p "$PROTRACKTOR_DIR/dist"
    artifact="$PROTRACKTOR_DIR/dist/protracktor-${infix}${version_name:-unknown}-${version_code:-0}-$stamp.apk"
    cp "$build_apk" "$artifact"
    printf '%s\n' "$artifact"
}
