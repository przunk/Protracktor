#!/usr/bin/env bash
#
# Runs the unit tests. Same bargain as the build scripts: Gradle's output goes to a log, the
# terminal gets the result and, on failure, the tests that failed and why.
#
# Usage:  ./scripts/test-protracktor.sh [extra gradle args…]
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=./use-tooling.sh
source "$SCRIPT_DIR/use-tooling.sh"
cd "$PROTRACKTOR_DIR"

LOG_FILE="$PROTRACKTOR_LOG_PREFIX-test.log"
printf 'sdk.dir=%s\n' "$ANDROID_HOME" > local.properties

started_at=$(date +%s)
echo "🧪 Protracktor — unit tests…"

set +e
./gradlew :app:testDebugUnitTest --console=plain "$@" > "$LOG_FILE" 2>&1
status=$?
set -e

# Counted from the JUnit XML rather than from the log. "BUILD SUCCESSFUL" with zero tests run is a
# failure wearing a success message, and it has fooled this workshop before.
#
# Deliberately NOT filtered by modification time. Gradle skips the task when nothing changed, so
# fresh-file filtering reports zero tests on every second run -- which is the same false alarm in
# the other direction. The results are still the results of the current sources; whether they were
# produced just now is reported separately below.
RESULTS_DIR="$PROTRACKTOR_BUILD_DIR_ROOT/app/test-results/testDebugUnitTest"
totals="$(find "$RESULTS_DIR" -name 'TEST-*.xml' \
    -exec grep -ho 'tests="[0-9]*"\|failures="[0-9]*"\|errors="[0-9]*"\|skipped="[0-9]*"' {} \; 2>/dev/null \
    | awk -F'"' '{a[$1]+=$2} END {printf "%d %d %d %d", a["tests="], a["failures="], a["errors="], a["skipped="]}')"
read -r total failures errors skipped <<< "${totals:-0 0 0 0}"

cached=""
if grep -aq '^> Task :app:testDebugUnitTest UP-TO-DATE' "$LOG_FILE"; then
    cached=" (up to date, not re-run)"
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

echo "✅ $total tests passed in $(($(date +%s) - started_at))s$cached"
