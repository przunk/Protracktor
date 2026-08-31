#!/usr/bin/env bash
#
# Downloads the third-party decoder sources into native/vendor/.
#
# They are fetched rather than committed: they are large, they are not ours, and a pinned version
# with a verified checksum says more about what we built against than a copy in our history does.
# native/vendor/ is gitignored.
#
# Every download is pinned to an exact version and verified against a SHA-256 recorded here. An
# unverified download is a supply chain with nobody watching it.
#
# Usage:  ./scripts/fetch-native-deps.sh [--force]
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
VENDOR="$PROJECT_DIR/native/vendor"
CACHE="${PROTRACKTOR_DOWNLOAD_CACHE:-$HOME/.protracktor/downloads}"

FORCE=0
[ "${1:-}" = "--force" ] && FORCE=1

mkdir -p "$VENDOR" "$CACHE"

# name | version | url | sha256 | strip-components
fetch() {
    local name="$1" version="$2" url="$3" sha="$4" strip="$5"
    local dest="$VENDOR/$name"
    local archive="$CACHE/$(basename "$url")"

    if [ -f "$dest/.protracktor-version" ] && [ "$(cat "$dest/.protracktor-version")" = "$version" ] && [ "$FORCE" = "0" ]; then
        echo "  ✅ $name $version (already present)"
        return
    fi

    if [ ! -f "$archive" ]; then
        echo "  ⬇  $name $version"
        curl -sSfL -o "$archive.part" "$url"
        mv "$archive.part" "$archive"
    fi

    local actual
    actual="$(sha256sum "$archive" | cut -d' ' -f1)"
    if [ "$actual" != "$sha" ]; then
        echo "  ❌ $name: checksum mismatch"
        echo "     expected $sha"
        echo "     actual   $actual"
        echo "     Refusing to unpack. Delete $archive and retry, or update the pin deliberately."
        exit 1
    fi

    rm -rf "$dest"
    mkdir -p "$dest"
    tar xzf "$archive" -C "$dest" --strip-components="$strip"
    printf '%s\n' "$version" > "$dest/.protracktor-version"
    echo "  ✅ $name $version"
}

echo "Fetching native dependencies into native/vendor/"
echo

# libopenmpt -- BSD-3-Clause. Tracker formats: MOD, XM, S3M, IT and dozens of others.
# The "makefile" package is the one that carries build/android_ndk/Android.mk, which our
# CMakeLists.txt reads to get the authoritative source list.
fetch libopenmpt \
      "0.8.9" \
      "https://lib.openmpt.org/files/libopenmpt/src/libopenmpt-0.8.9+release.makefile.tar.gz" \
      "9273b88b67973cc69e54d748ab1b749399d6d07695f1c37d0c59f88b4106074f" \
      1

echo
echo "✅ done — sources in native/vendor/ (gitignored)"
