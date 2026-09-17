#!/usr/bin/env bash
#
# Checks the Play listing fields that can be validated without Play Console.
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"

failed=0

character_count() {
    local value
    value="$(<"$1")"
    LC_ALL=C.UTF-8 printf '%s' "$value" | LC_ALL=C.UTF-8 wc -m
}

check_field() {
    local path="$1" limit="$2" label="$3" count
    if [ ! -s "$path" ]; then
        echo "❌ Missing or empty store field: $path"
        failed=1
        return
    fi

    count="$(character_count "$path")"
    if [ "$count" -gt "$limit" ]; then
        echo "❌ $label is $count characters; Google Play allows $limit: $path"
        failed=1
    fi
}

for locale in en-US pl-PL; do
    listing="$PROJECT_DIR/store/listing/$locale"
    check_field "$listing/title.txt" 30 "$locale title"
    check_field "$listing/short-description.txt" 80 "$locale short description"
    check_field "$listing/full-description.txt" 4000 "$locale full description"

    # **Every release-note file, not one named in this script.** Checking a file by name means
    # that renaming it for the release it ships with -- the obvious thing to do, since versionCode
    # is the commit count -- silently stops checking anything.
    notes=0
    for note in "$listing/release-notes/"*.txt; do
        [ -e "$note" ] || continue
        check_field "$note" 500 "$locale release notes ($(basename "$note"))"
        notes=$((notes + 1))
    done
    if [ "$notes" -eq 0 ]; then
        echo "❌ No release notes at all for $locale: $listing/release-notes/"
        failed=1
    fi
done

# **The two locales must hold the same files.** A listing that exists in English and not in Polish
# is the failure this catches: Play shows the default-language text instead, silently, and the app
# is bilingual from its first screen.
en_files="$(cd "$PROJECT_DIR/store/listing/en-US" && find . -name '*.txt' | sort)"
pl_files="$(cd "$PROJECT_DIR/store/listing/pl-PL" && find . -name '*.txt' | sort)"
if [ "$en_files" != "$pl_files" ]; then
    echo "❌ The two locales do not hold the same files:"
    diff <(echo "$en_files") <(echo "$pl_files") | sed 's/^/   /' || true
    failed=1
fi

if [ "$failed" -ne 0 ]; then
    exit 1
fi

echo "✅ Store metadata fields fit Google Play limits"
