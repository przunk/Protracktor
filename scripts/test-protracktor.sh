#!/usr/bin/env bash
#
# Runs the full gate with concise progress, per-stage deadlines and complete file logs.
#
# Pass --really to force the tests to execute rather than be served from the cache. Worth it before
# claiming a suite is green on code nobody has run it against.
#
# Usage:  ./scripts/test-protracktor.sh [--really] [extra gradle args…]
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=./use-tooling.sh
source "$SCRIPT_DIR/use-tooling.sh"
cd "$PROTRACKTOR_DIR"

LOG_FILE="$PROTRACKTOR_LOG_PREFIX-test.log"
CHECK_RUNNER="$SCRIPT_DIR/internal/run-check.py"
UNIT_TIMEOUT="${PROTRACKTOR_UNIT_TEST_TIMEOUT_SECONDS:-1200}"
CHECK_TIMEOUT="${PROTRACKTOR_CHECK_TIMEOUT_SECONDS:-1200}"
printf 'sdk.dir=%s\n' "$ANDROID_HOME" > local.properties

run_check() {
    local label="$1" suffix="$2"; shift 2
    if python3 "$CHECK_RUNNER" --label "$label" --log "$PROTRACKTOR_LOG_PREFIX-$suffix.log" \
        --timeout "$CHECK_TIMEOUT" -- "$@"; then
        return 0
    else
        local status=$?
        echo "❌ $label failed or stopped (exit $status); see the full log above."
        return "$status"
    fi
}

force=""
if [ "${1:-}" = "--really" ]; then
    force="--rerun-tasks"
    shift
fi

started_at=$(date +%s)
echo "🧪 Protracktor — unit tests…"

set +e
python3 "$CHECK_RUNNER" --label "Unit tests" --log "$LOG_FILE" --timeout "$UNIT_TIMEOUT" -- \
    ./gradlew :app:testDebugUnitTest --console=plain $force "$@" --no-daemon \
    -Pkotlin.compiler.execution.strategy=in-process
status=$?
set -e

if [ "$status" -eq 124 ] || [ "$status" -eq 125 ] || [ "$status" -ge 128 ]; then
    echo "❌ Unit-test gate stopped (exit $status); no release is permitted."
    echo "Full log: $LOG_FILE"
    exit "$status"
fi

# Counted from the JUnit XML rather than from the log. "BUILD SUCCESSFUL" with zero tests run is a
# failure wearing a success message, and it has fooled this workshop before.
#
# Deliberately NOT filtered by modification time. Gradle skips the task when nothing changed, so
# fresh-file filtering reports zero tests on every second run -- which is the same false alarm in
# the other direction. The results are still the results of the current sources; whether they were
# produced just now is reported separately below.
RESULTS_DIR="$PROTRACKTOR_BUILD_DIR_ROOT/app/test-results/testDebugUnitTest"
totals=""
if [ -d "$RESULTS_DIR" ]; then
    totals="$(find "$RESULTS_DIR" -name 'TEST-*.xml' \
        -exec grep -ho 'tests="[0-9]*"\|failures="[0-9]*"\|errors="[0-9]*"\|skipped="[0-9]*"' {} \; 2>/dev/null \
        | awk -F'"' '{a[$1]+=$2} END {printf "%d %d %d %d", a["tests="], a["failures="], a["errors="], a["skipped="]}')"
fi
read -r total failures errors skipped <<< "${totals:-0 0 0 0}"

# Two ways Gradle reports a green suite it did not run, and only one of them was noticed here
# before: UP-TO-DATE means nothing changed, FROM-CACHE means the answer came out of the build cache.
# Both print BUILD SUCCESSFUL and neither executes a test. Saying which is the difference between
# "87 tests passed" and "87 tests passed some time ago, possibly on other code".
cached=""
if grep -aq '^> Task :app:testDebugUnitTest UP-TO-DATE' "$LOG_FILE"; then
    cached=" (up to date, not re-run)"
elif grep -aq '^> Task :app:testDebugUnitTest FROM-CACHE' "$LOG_FILE"; then
    cached=" (from the build cache, not re-run)"
fi

# A compile error is not a test failure, and reporting stale counts from the previous run's XML
# alongside it says "29 passed" about code that did not build. Named for what it is.
if [ "$status" -ne 0 ] && grep -aq '^e: ' "$LOG_FILE"; then
    echo "❌ Did not compile:"
    { grep -a '^e: ' "$LOG_FILE" || true; } | sed "s|file://$PROTRACKTOR_DIR/||" | sed 's/^/   /' | head -n 20
    echo
    echo "Full log: $LOG_FILE"
    exit 1
fi

if [ "$status" -ne 0 ]; then
    echo "❌ Tests failed:"
    # "|| true" on every one of these. With set -o pipefail a grep that legitimately matches
    # nothing fails the whole pipeline, and set -e then kills the script mid-report -- so the run
    # that most needs explaining is the one that prints least. Found by tracing a real failure.
    { grep -aoE '^[A-Za-z0-9_.]+ > .* FAILED' "$LOG_FILE" || true; } | sed 's/^/   /' | head -n 25
    { grep -aA2 -E '^[A-Za-z0-9_.]+ > .* FAILED$' "$LOG_FILE" || true; } \
        | grep -aE 'Exception|Error|expected:|actual:' | sed 's/^/       /' | head -n 15 || true
    echo
    echo "   $total run, $failures failed, $errors errored, $skipped skipped"
    echo "Full log: $LOG_FILE"
    echo "Report:   $PROTRACKTOR_BUILD_DIR_ROOT/app/reports/tests/testDebugUnitTest/index.html"
    exit 1
fi

if [ "$total" -eq 0 ]; then
    echo "❌ Gradle reported success but no test results exist. That is a failure, not a pass."
    echo "   Looked in: $RESULTS_DIR"
    echo "Full log: $LOG_FILE"
    exit 1
fi

if [ "$failures" -ne 0 ] || [ "$errors" -ne 0 ] || [ "$skipped" -ne 0 ]; then
    echo "❌ JUnit results are not a complete pass: $total tests, $failures failures, $errors errors, $skipped skipped."
    echo "Full log: $LOG_FILE"
    exit 1
fi

# --- string resources -------------------------------------------------------------------------
#
# Two things the compiler is happy with and a phone is not.
#
# 1. A format specifier writes its flags BEFORE the argument index -- "%,1$d" rather than "%1$,d".
#    That is not a specifier at all and String.format throws when the string is rendered. It
#    shipped once, in a plural whose quantity is always "other", so the crash was certain and
#    nothing here noticed.
# 2. A string exists in one language and not the other. This app is bilingual from the first
#    screen; fifteen strings had drifted to English-only before anybody looked.
bad_format=$(grep -ahoE '%[,+#0 -]+[0-9]+\$' app/src/main/res/values*/strings.xml || true)
if [ -n "$bad_format" ]; then
    echo "❌ Malformed format specifiers in string resources (flags before the argument index):"
    grep -anE '%[,+#0 -]+[0-9]+\$' app/src/main/res/values*/strings.xml | sed 's/^/   /' | head -n 10
    exit 1
fi

untranslated=$(comm -23 \
    <(grep -ohE '<(string|plurals) name="[^"]+"' app/src/main/res/values/strings.xml | grep -oE '"[^"]+"' | sort -u) \
    <(grep -ohE '<(string|plurals) name="[^"]+"' app/src/main/res/values-pl/strings.xml | grep -oE '"[^"]+"' | sort -u))
if [ -n "$untranslated" ]; then
    echo "❌ Strings with no Polish translation:"
    echo "$untranslated" | tr -d '"' | sed 's/^/   /' | head -n 20
    exit 1
fi

# The store listing, which is text in a directory and therefore the easiest thing in this project to
# let drift. The fields have hard character limits and the two locales have to hold the same files;
# both are checkable here and neither is checkable in Play Console until an upload is refused.
"$SCRIPT_DIR/check-store-metadata.sh"
run_check "Test-runner fixtures" runner python3 "$SCRIPT_DIR/check-test-runner.py"
run_check "Release-flow fixtures" release-flow bash "$SCRIPT_DIR/check-release.sh"

# These domain cases need only Node, not a browser/DOM dependency or a compiled WASM engine.
run_check "Playback context" playback-context node scripts/check-playback-context.mjs
run_check "Playback schedule" playback-schedule node scripts/check-playback-schedule.mjs
run_check "Playback effects" playback-effects node scripts/check-playback-steps.mjs

# The web page, if its DOM is installed. Not required -- somebody checking out this repository to
# build an APK should not have to run npm -- but when it is there it is part of the suite, because
# the page is the one half of this project nobody can see while writing it.
if [ -d web/node_modules/jsdom ]; then
    run_check "Row gestures" row-gestures node scripts/check-row-gestures.mjs
    run_check "Pending reorder" queue-reorder node scripts/check-queue-reorder.mjs
    run_check "Queue presentation" queue-view node scripts/check-queue-view.mjs
    run_check "Web page" page node scripts/check-page.mjs
    page_output="$(<"$PROTRACKTOR_LOG_PREFIX-page.log")"
    page_checks=$(echo "$page_output" | grep -c '✓' || true)
    echo "🖥  $page_checks page checks passed"
fi

# The engine, when one has been built. Optional for the same reason the page checks are: building
# the WebAssembly engine needs emsdk, and somebody checking out this repository to build an APK
# should not have to. When it is there it is part of the suite, because the two faults it guards --
# a refusal quoting the wrong decoder (C55) and a throw crossing the boundary (C42) -- are both in
# `native/engine/engine.cpp`, which is the file Android compiles too.
if [ -f web/vendor/engine.mjs ]; then
    run_check "Web engine checks" engine node scripts/check-engine.mjs
    engine_output="$(<"$PROTRACKTOR_LOG_PREFIX-engine.log")"
    engine_checks=$(echo "$engine_output" | grep -c '✓' || true)
    echo "🔊 $engine_checks engine checks passed"
fi

# The SAP length measurement (`docs/STATUS.md` C91), built on the host from the same C the phone
# compiles. Only where there is a C compiler, and said when there is not, since a check that quietly
# did not run is a check that quietly passed.
if command -v cc >/dev/null 2>&1; then
    run_check "SAP measurement" sap ./scripts/check-sap-measure.sh
    sap_output="$(<"$PROTRACKTOR_LOG_PREFIX-sap.log")"
    sap_checks=$(echo "$sap_output" | grep -c '✓' || true)
    [ "$sap_checks" -gt 0 ] || { echo "❌ SAP measurement: no checks ran"; exit 1; }
    echo "📼 $sap_checks SAP measurement checks passed"
else
    echo "⚠️  SAP measurement checks skipped: no C compiler (cc) on this machine"
fi

# The page's database upgraded under data a browser already holds (a migration meets it once).
if [ -d web/node_modules/fake-indexeddb ]; then
    run_check "Web database upgrade" web-upgrade node scripts/check-web-store-upgrade.mjs
fi

# The Android Player itself with a fake device; the WASM checks above do not exercise Oboe output.
run_check "Native audio" audio "$SCRIPT_DIR/check-audio-host.sh"

# The icons, both players' sets, walked rather than looked at. A path whose shape depends on where
# the pen sits after `z` draws correctly here and wrongly on somebody else's phone
# (`docs/STATUS.md` C66), which is not a thing an eye or a screenshot will catch.
run_check "Icons" icons node scripts/check-icons.mjs

run_check "PWA" pwa node scripts/check-pwa.mjs

# The server, over a real socket. Needs no npm -- it is node and the standard library -- and it is
# separate from the page checks because the bug it exists for (`docs/STATUS.md` C29) lives in the
# address a file is served at, which jsdom never sees.
run_check "Local server" server node scripts/check-server.mjs
server_output="$(<"$PROTRACKTOR_LOG_PREFIX-server.log")"
server_checks=$(echo "$server_output" | grep -c '✓' || true)
echo "🌐 $server_checks server checks passed"

# Lint, for its errors (warnings are reported, not fatal). Added 2026-10-01: an AppLinkUrlError had
# sat in the manifest since the scan work because nothing here ran lint, and the release build's
# lintVital checks a narrower set.
set +e
python3 "$CHECK_RUNNER" --label "Lint" --log "$PROTRACKTOR_LOG_PREFIX-lint.log" \
    --timeout "${PROTRACKTOR_LINT_TIMEOUT_SECONDS:-1200}" -- \
    ./gradlew :app:lintDebug --console=plain --no-daemon -Pkotlin.compiler.execution.strategy=in-process
lint_status=$?
set -e
if [ $lint_status -ne 0 ]; then
    echo "❌ Lint failed or stopped (exit $lint_status):"
    { grep -E "Error:|errors? and" "$PROTRACKTOR_LOG_PREFIX-lint.log" || true; } \
        | sed 's/^ *//' | awk '!seen[$0]++' | sed 's/^/   /' | head -20
    echo "   Full log: $PROTRACKTOR_LOG_PREFIX-lint.log"
    exit "$lint_status"
fi
echo "🧹 Lint: $(grep -oE '[0-9]+ errors? and [0-9]+ warnings?|No issues found' "$PROTRACKTOR_LOG_PREFIX-lint.log" | head -1 || echo 'no errors')"

echo "✅ $total tests passed in $(($(date +%s) - started_at))s$cached"
