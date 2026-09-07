#!/usr/bin/env bash
# Fetch ZXTune's own sources and build the ZX Spectrum probe against them.
#
# `docs/PLAN_FORMATS.md` §7: ayfly plays these formats and cannot be shipped -- no licence on the
# players, GPL-2-**only** on its Z80 emulator. ZXTune is LGPL-3 and reaches that same emulator from
# exactly one plugin (`ayemul`, for `.ay` machine code) which is not built here.
#
# **A sparse, blobless clone**, because the repository is 182 MB and almost none of it is wanted:
# `3rdparty` alone is 280 MB and only `fmt` is needed. The AY source set this compiles is under
# 2 MB.
#
# C++20 is not a preference. `-std=c++17` fails on `std::to_address` and a `concept` declaration in
# ZXTune's own headers; the app already builds at C++20, so this matches it.
#
# Usage:  ./scripts/build-zxtune-probe.sh [--clean]
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
W="${ZXTUNE_HOST_BUILD:-$HOME/.protracktor/zxtune-host}"

# Pinned, because "whatever master was that afternoon" is not a measurement anybody can repeat.
REPO="https://github.com/vitamin-caig/zxtune.git"

[ "${1:-}" = "--clean" ] && rm -rf "$W"

if [ ! -d "$W/.git" ]; then
    echo "→ cloning ZXTune (sparse, blobless)…"
    git clone --quiet --filter=blob:none --sparse --depth 1 "$REPO" "$W"
    git -C "$W" sparse-checkout set src include make 3rdparty/fmt
fi
echo "  at $(git -C "$W" rev-parse --short HEAD)"

OUT="$ROOT/native/probe/zxtune/build"
mkdir -p "$OUT"

# The source set, by directory. `ayemul.cpp` is excluded by name and that exclusion is the licence
# decision: it is the only AY plugin that reaches `3rdparty/z80ex`, which is GPL-2-only.
# `sound/impl/resampler.cpp` is excluded because it pulls in an N64 emulator for its resampling and
# Oboe does ours.
# **Narrowed by hand, and the narrowing is most of the work.** A sweep of `src/module` pulls in the
# xsf players (PlayStation, DS, GBA, N64 emulators), the DAC players, v2m and the rest -- every one
# of which wants a different component of `3rdparty`. `src/sound` sweeps in the ALSA and OSS
# backends; `src/binary` sweeps in zlib and lhasa; `src/l10n` wants boost. None of that is on the
# path from "bytes of a .pt3" to "samples".
#
# `ayemul.cpp` is excluded by name and that exclusion is the licence decision: it is the only AY
# plugin reaching `3rdparty/z80ex`, which is GPL-2-only. `ym_vtx.cpp` is excluded because VTX is
# LHA-compressed and pulling in `3rdparty/lhasa` for 879 Modland files is not this measurement's
# business.
mapfile -t SOURCES < <(
    {
        find "$W/src/formats/chiptune/aym" "$W/src/module/players/aym" "$W/src/devices/aym/src" \
             "$W/src/binary/src" "$W/src/parameters/src" "$W/src/strings/src" "$W/src/math" \
             "$W/src/tools" "$W/src/debug/src" "$W/src/time" -name '*.cpp' 2>/dev/null
        find "$W/src/module/players" -maxdepth 1 -name '*.cpp' 2>/dev/null
        find "$W/src/sound" -maxdepth 2 -name '*.cpp' -not -path '*/backends/*' 2>/dev/null
        find "$W/src/formats/chiptune" -maxdepth 1 -name '*.cpp' 2>/dev/null
        find "$W/src/binary/format" -maxdepth 1 -name '*.cpp' 2>/dev/null
        # `full` rather than `lite`: they define the same `Binary::CreateFormat` and the lite one
        # drops the pattern syntax the chiptune decoders' format strings use. Picking either is a
        # decision; picking neither is one undefined symbol at the end of an 89-file link.
        echo "$W/src/binary/format/full/factory.cpp"
        # The localisation stub rather than the boost-backed one: nothing here shows a string to
        # anybody, and `boost_locale.cpp` would drag in Boost for the sake of messages the probe
        # never prints.
        echo "$W/src/l10n/stub/stub.cpp"
        # Note tables, which every AY tracker needs and which live under the plugin registry rather
        # than with the players.
        echo "$W/src/core/plugins/players/ay/freq_tables.cpp"
        echo "$W/3rdparty/fmt/src/format.cc"
    } | grep -v -e '/ayemul\.cpp$' -e '/resampler\.cpp$' -e '/test' -e '/dumper/' \
        -e '/ym_vtx\.cpp$' | sort -u
)
echo "→ compiling ${#SOURCES[@]} ZXTune sources plus the probe"

# `PROBE_CHAR_FLAGS` exists to reproduce Android on this machine. Plain `char` is **signed** on
# x86-64 and **unsigned** on ARM, which is the difference most likely to make a decoder work here and
# fail on a phone. Set it to `-funsigned-char` to build the ARM behaviour:
#
#     PROBE_CHAR_FLAGS=-funsigned-char ZXTUNE_PROBE_OUT=unsigned ./scripts/build-zxtune-probe.sh
FLAGS=(-O2 -w -std=c++20 ${PROBE_CHAR_FLAGS:+"$PROBE_CHAR_FLAGS"}
       -I"$W/src" -I"$W/include" -I"$W" -I"$W/3rdparty/fmt/include")
OBJ="$OUT/obj${ZXTUNE_PROBE_OUT:+-$ZXTUNE_PROBE_OUT}"
mkdir -p "$OBJ"

# One object per source, in parallel, kept between runs. A single `g++` over 89 files took minutes
# and recompiled every one of them each time an include path turned out to be wrong -- which, on a
# library this size, is most of the first afternoon.
OBJECTS=()
for src in "${SOURCES[@]}"; do
    flat="$(echo "${src#"$W/"}" | tr '/' '_')"
    obj="$OBJ/${flat%.*}.o"
    OBJECTS+=("$obj")
    if [ ! -f "$obj" ] || [ "$src" -nt "$obj" ]; then
        g++ "${FLAGS[@]}" -c -o "$obj" "$src" &
        while [ "$(jobs -rp | wc -l)" -ge "$(nproc)" ]; do wait -n; done
    fi
done
wait

for obj in "${OBJECTS[@]}"; do
    [ -f "$obj" ] || { echo "❌ missing object: $obj"; exit 1; }
done

g++ "${FLAGS[@]}" -o "$OUT/probe-zxtune${ZXTUNE_PROBE_OUT:+-$ZXTUNE_PROBE_OUT}" \
    "$ROOT/native/probe/zxtune/probe_zxtune.cpp" "${OBJECTS[@]}" -lm

echo "✅ built. Now: ./scripts/probe-zxtune.py"
