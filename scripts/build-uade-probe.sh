#!/usr/bin/env bash
# Build UADE for the host and compile the probe against it.
#
# `GOAL.md` round 6 item 1 says to prove a backend on the host before integrating it. UADE needs
# more setting up than the other four did: two libraries of its own (bencodetools and libzakalwe,
# both by UADE's author), and a *separate executable* -- libuade forks and execs `uadecore` and
# talks to it over pipes. That process model is the central question for Android and this script
# is deliberately honest about it: it builds exactly what upstream builds, so the number the probe
# produces is a number about UADE, not about a fork of it.
#
# Nothing here touches the app. Everything lands under a work directory outside the repository.
#
# Usage:  ./scripts/build-uade-probe.sh [--work DIR] [--clean]
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
WORK="${UADE_WORK_DIR:-$HOME/.protracktor/uade-build}"
CLEAN=0

while [ $# -gt 0 ]; do
    case "$1" in
        --work) WORK="$2"; shift 2 ;;
        --clean) CLEAN=1; shift ;;
        *) echo "unknown argument: $1" >&2; exit 2 ;;
    esac
done

# Pinned, because "whatever master was that afternoon" is not a measurement anybody can repeat.
UADE_URL="https://gitlab.com/uade-music-player/uade.git"
UADE_REV="d40dcc7"          # 3.05
BENCODE_URL="https://gitlab.com/heikkiorsila/bencodetools.git"
BENCODE_REV="5fa73d3"
ZAKALWE_URL="https://gitlab.com/hors/libzakalwe.git"
ZAKALWE_REV=""              # HEAD; libzakalwe is a support library and does not affect playback

[ "$CLEAN" = 1 ] && rm -rf "$WORK"
mkdir -p "$WORK"
DEPS="$WORK/deps"
INSTALL="$WORK/install"

fetch() {  # url rev dir
    local url="$1" rev="$2" dir="$3"
    if [ ! -d "$dir/.git" ]; then
        echo "→ cloning $(basename "$dir")…"
        git clone "$url" "$dir" >/dev/null 2>&1
    fi
    if [ -n "$rev" ]; then
        git -C "$dir" checkout -q "$rev" 2>/dev/null || {
            echo "  (pinned revision $rev unavailable; staying on $(git -C "$dir" rev-parse --short HEAD))" >&2
        }
    fi
}

echo "=== dependencies"
fetch "$BENCODE_URL" "$BENCODE_REV" "$WORK/bencodetools"
if [ ! -f "$DEPS/lib/libbencodetools.so" ]; then
    ( cd "$WORK/bencodetools"
      ./configure --prefix="$DEPS" >/dev/null
      make -j"$(nproc)" >/dev/null
      # install-python writes outside the prefix and is not needed; the C targets are.
      make install-c install-headers >/dev/null 2>&1 || make install >/dev/null 2>&1 || true )
fi
[ -f "$DEPS/lib/libbencodetools.so" ] || { echo "❌ libbencodetools did not build"; exit 1; }

fetch "$ZAKALWE_URL" "$ZAKALWE_REV" "$WORK/libzakalwe"
if [ ! -f "$DEPS/lib/libzakalwe.so" ]; then
    # Its `make install` targets /usr/local unconditionally, so the files are placed by hand.
    ( cd "$WORK/libzakalwe"
      ./configure >/dev/null 2>&1 || true
      make -j"$(nproc)" >/dev/null 2>&1 || true )
    mkdir -p "$DEPS/lib" "$DEPS/include"
    cp "$WORK/libzakalwe/libzakalwe.so" "$DEPS/lib/"
    cp -r "$WORK/libzakalwe/include/"* "$DEPS/include/"
fi
[ -f "$DEPS/lib/libzakalwe.so" ] || { echo "❌ libzakalwe did not build"; exit 1; }

echo "=== uade"
fetch "$UADE_URL" "$UADE_REV" "$WORK/uade"
if [ ! -f "$INSTALL/lib/uade/uadecore" ]; then
    ( cd "$WORK/uade"
      # uade123 and uadesimple play to a sound card through libao and SDL2. A probe renders to a
      # buffer and needs neither, and asking for them would make the build depend on packages that
      # have nothing to do with whether UADE can decode a tune.
      ./configure --prefix="$INSTALL" --bencode-tools-prefix="$DEPS" \
                  --libzakalwe-prefix="$DEPS" --no-debug \
                  --without-uade123 --without-uadesimple >/dev/null
      make -j"$(nproc)" >/dev/null 2>&1 || true )

    # `make install` trips over a missing libuade.pc before it finishes, so the pieces are placed
    # directly. This is the same set the Makefile's own install target copies.
    DATA="$INSTALL/share/uade"
    mkdir -p "$DATA" "$INSTALL/lib/uade" "$INSTALL/include" "$INSTALL/lib"
    cp -f "$WORK/uade/uade.conf" "$WORK/uade/amigasrc/score/score" \
          "$WORK/uade/uaerc" "$WORK/uade/eagleplayer.conf" "$DATA/"
    cp -rf "$WORK/uade/players" "$DATA/"
    cp -f "$WORK/uade/src/uadecore" "$INSTALL/lib/uade/"
    cp -f "$WORK/uade/src/frontends/libuade/libuade.so" \
          "$WORK/uade/src/frontends/libuade/libuade.a" "$INSTALL/lib/"
    cp -rf "$WORK/uade/src/frontends/include/uade" "$INSTALL/include/"
fi
[ -f "$INSTALL/lib/uade/uadecore" ] || { echo "❌ uadecore did not build"; exit 1; }

echo "=== probe"
mkdir -p "$ROOT/native/probe/uade/build"
gcc -O2 -Wall -o "$ROOT/native/probe/uade/build/probe-uade" \
    "$ROOT/native/probe/uade/probe_uade.c" \
    -I"$INSTALL/include" -L"$INSTALL/lib" -L"$DEPS/lib" \
    -Wl,-rpath,"$INSTALL/lib" -Wl,-rpath,"$DEPS/lib" \
    -luade -lbencodetools -lzakalwe -lm

# The song database, without which several formats fail in a way that looks like the format not
# working. UADE cannot tell from a filename which player a Hippel or TFMX variant needs -- the
# collections disagree about prefixes and suffixes -- so Matti Tiainen, one of UADE's maintainers,
# keeps a table of md5 overrides for the Audacious plugin. Its own maintainer pointed us at it on
# 2026-09-05, and it is the difference between 196/300 and 206/300 on our corpus, and between 0/12
# and 11/12 on Modland's Hippel ST COSO.
#
# `conf/song.conf` and **not** `conf/songdb`: the project is GPL-2.0-or-later, which is compatible
# with ours, but the songdb directory beside it is CC BY-NC-SA 4.0 and could never ship in a store
# app (`docs/LICENSES.md`).
SONG_CONF_URL="https://raw.githubusercontent.com/mvtiaine/audacious-uade/master/conf/song.conf"
if [ ! -f "$INSTALL/share/uade/song.conf" ]; then
    echo "=== song database"
    if curl -sSL --max-time 60 -o "$INSTALL/share/uade/song.conf.part" "$SONG_CONF_URL" &&
       [ -s "$INSTALL/share/uade/song.conf.part" ]; then
        mv "$INSTALL/share/uade/song.conf.part" "$INSTALL/share/uade/song.conf"
    else
        rm -f "$INSTALL/share/uade/song.conf.part"
        echo "  ⚠️  could not fetch song.conf -- Hippel and TFMX variants will under-report" >&2
    fi
fi

echo
echo "✅ built. $(ls "$INSTALL/share/uade/players" | wc -l) replay binaries, $(du -sh "$INSTALL/share/uade/players" | cut -f1) total."
echo
[ -f "$INSTALL/share/uade/song.conf" ] &&
    echo "   $(grep -c '^md5=' "$INSTALL/share/uade/song.conf") song database overrides."
echo
echo "Run the measurement with:"
echo
echo "  export UADE_BASE_DIR=$INSTALL/share/uade"
echo "  export UADE_CORE_FILE=$INSTALL/lib/uade/uadecore"
echo "  ./scripts/probe-uade.py"
