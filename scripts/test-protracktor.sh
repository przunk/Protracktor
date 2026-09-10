#!/usr/bin/env bash
#
# Runs the unit tests. Same bargain as the build scripts: Gradle's output goes to a log, the
# terminal gets the result and, on failure, the tests that failed and why.
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
printf 'sdk.dir=%s\n' "$ANDROID_HOME" > local.properties

force=""
if [ "${1:-}" = "--really" ]; then
    force="--rerun-tasks"
    shift
fi

started_at=$(date +%s)
echo "🧪 Protracktor — unit tests…"

set +e
./gradlew :app:testDebugUnitTest --console=plain $force "$@" > "$LOG_FILE" 2>&1
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

# The web page, if its DOM is installed. Not required -- somebody checking out this repository to
# build an APK should not have to run npm -- but when it is there it is part of the suite, because
# the page is the one half of this project nobody can see while writing it.
if [ -d web/node_modules/jsdom ]; then
    if ! page_output=$(node scripts/check-page.mjs 2>&1); then
        echo "❌ Page checks failed:"
        echo "$page_output" | sed 's/^/   /'
        exit 1
    fi
    page_checks=$(echo "$page_output" | grep -c '✓' || true)
    echo "🖥  $page_checks page checks passed"
fi

# The server, over a real socket. Needs no npm -- it is node and the standard library -- and it is
# separate from the page checks because the bug it exists for (`docs/STATUS.md` C29) lives in the
# address a file is served at, which jsdom never sees.
if ! server_output=$(node scripts/check-server.mjs 2>&1); then
    echo "❌ Server checks failed:"
    echo "$server_output" | sed 's/^/   /'
    exit 1
fi
server_checks=$(echo "$server_output" | grep -c '✓' || true)
echo "🌐 $server_checks server checks passed"

echo "✅ $total tests passed in $(($(date +%s) - started_at))s$cached"
