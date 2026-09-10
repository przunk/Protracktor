# ZXTune -- LGPL-3.0. The ZX Spectrum AY trackers: PT3, PT2, STC, ASC, SQT, STP and their relatives.
# 23,891 Modland files, of which this app played 58 before.
#
# **Only the AY path is built, and the narrowing is the work.** ZXTune is a whole player: sweeping
# `src/module` pulls in the PlayStation, DS, GBA and N64 emulators, `src/sound` pulls in ALSA and
# OSS backends, `src/binary` pulls in zlib and lhasa, `src/l10n` wants Boost. None of that is on the
# path from the bytes of a `.pt3` to samples. `docs/PLAN_FORMATS.md` §7 records how the list below
# was arrived at -- by compiling and reading the linker, which is the only way to find out.
#
# Two exclusions carry decisions rather than convenience:
#
#   ayemul.cpp     the only AY plugin reaching `3rdparty/z80ex`, which is GPL-2-**only** and cannot
#                  be combined with our GPL-3. It plays `.ay`, 1,202 Modland files, and leaving it
#                  out is not a workaround -- the component is simply not used. See `docs/LICENSES.md`.
#   resampler.cpp  reaches for an N64 emulator to resample. Oboe does ours.
#
# `ym_vtx.cpp` was a third exclusion until 2026-09-09, for the same reason -- YM and VTX are
# LHA-compressed streams and it wanted `3rdparty/lhasa`. It is built now, against upstream lhasa
# rather than ZXTune's patched copy, through the adapter in `lha_zxtune.cpp`. That is 4,961 Modland
# `.ym` files (`docs/STATUS.md` C20) and 879 `.vtx`.
#
# `native/probe/zxtune/` builds the same set for the host. The two lists are written twice and must
# agree; the probe script says so at its own head.
set(ZX "${CMAKE_CURRENT_SOURCE_DIR}/../../vendor/zxtune")

if(NOT EXISTS "${ZX}/src/module/players/aym/protracker3.cpp")
    message(FATAL_ERROR
        "ZXTune sources are missing. Run ./scripts/fetch-zxtune.py first.\n"
        "  expected: ${ZX}/src/module/players/aym/protracker3.cpp")
endif()

# Globbed by directory rather than listed file by file: ninety names would be ninety chances to
# forget one when the pin moves, and the directories are the real unit -- "everything that turns AY
# tracker bytes into samples".
# `CONFIGURE_DEPENDS`, so moving the pin in `fetch-zxtune.py` and re-fetching does not leave the
# build compiling yesterday's list of files. A glob read once at configure time is a source list
# that silently stops matching the sources.
file(GLOB ZX_SOURCES CONFIGURE_DEPENDS
    "${ZX}/src/formats/chiptune/aym/*.cpp"
    "${ZX}/src/formats/chiptune/*.cpp"
    "${ZX}/src/module/players/aym/*.cpp"
    "${ZX}/src/module/players/*.cpp"
    "${ZX}/src/devices/aym/src/*.cpp"
    "${ZX}/src/sound/*.cpp"
    "${ZX}/src/sound/impl/*.cpp"
    "${ZX}/src/binary/src/*.cpp"
    "${ZX}/src/binary/format/*.cpp"
    "${ZX}/src/parameters/src/*.cpp"
    "${ZX}/src/strings/src/*.cpp"
    "${ZX}/src/math/*.cpp"
    "${ZX}/src/tools/*.cpp"
    "${ZX}/src/tools/src/*.cpp"
    "${ZX}/src/debug/src/*.cpp"
    "${ZX}/src/time/*.cpp"
)
list(APPEND ZX_SOURCES
    # `full` rather than `lite`: both define `Binary::CreateFormat` and `lite` drops the pattern
    # syntax the chiptune decoders' format strings use.
    "${ZX}/src/binary/format/full/factory.cpp"
    # The localisation stub rather than the boost-backed one. Nothing here shows a string to anybody.
    "${ZX}/src/l10n/stub/stub.cpp"
    # Note tables, which every AY tracker needs and which live under the plugin registry rather than
    # with the players.
    "${ZX}/src/core/plugins/players/ay/freq_tables.cpp"
    "${ZX}/3rdparty/fmt/src/format.cc"
    # Ours, not ZXTune's: `Binary::Compression::Lha` against upstream lhasa. ZXTune's own
    # `binary/compression/src/lha.cpp` is not on this list and cannot be -- see the file's header.
    "${CMAKE_CURRENT_SOURCE_DIR}/lha_zxtune.cpp"
)
list(FILTER ZX_SOURCES EXCLUDE REGEX "/(ayemul|resampler)\\.cpp$")

# `encoding.cpp` needs a `std::char_traits<uint16_t>` that libc++ does not provide and libstdc++
# does, so the host probe compiled it and the first Android build did not. It was excluded for one
# round and the link said no: `strings/src/sanitize.cpp` calls `ToAutoUtf8`, and sanitising is what
# cleans a module's title before the app shows it. The shim is force-included into that one file and
# reaches nothing else -- see the header for what it costs.
set_source_files_properties("${ZX}/src/strings/src/encoding.cpp" PROPERTIES
    COMPILE_OPTIONS "-include;${CMAKE_CURRENT_SOURCE_DIR}/uint16_char_traits.h")

add_library(zxtune STATIC ${ZX_SOURCES})
target_include_directories(zxtune PUBLIC
    "${ZX}/src" "${ZX}/include" "${ZX}" "${ZX}/3rdparty/fmt/include")

# C++20 is not a preference here. ZXTune's own headers use `std::to_address` and a `concept`
# declaration, so C++17 does not compile them; the app is already at 20.
set_target_properties(zxtune PROPERTIES CXX_STANDARD 20 CXX_STANDARD_REQUIRED ON)

# Somebody else's warnings, and there are a great many. Silenced so they do not bury one of ours.
target_compile_options(zxtune PRIVATE -w)

# `fmt` 9's compile-time format checking does not survive this clang. `format-inl.h` calls a
# `consteval` constructor from a context clang does not accept as constant, and the NDK's clang is
# stricter about that than the host's GCC -- so the host probe built it and the first Android build
# did not.
#
# **`FMT_CONSTEVAL`, not `FMT_USE_CONSTEVAL`.** The first is what `core.h` guards with `#ifndef` and
# what actually decides; the second is only consulted when the first is undefined, so setting it had
# no effect at all and produced the identical error a second time. Defining `FMT_CONSTEVAL` empty
# makes the constructor an ordinary one and moves the check to run time, which costs nothing here:
# the only format strings are `fmt`'s own, inside a library whose messages this app never shows.
target_compile_definitions(zxtune PUBLIC FMT_CONSTEVAL=)

# lhasa, for the YM/VTX path. `PUBLIC` rather than `PRIVATE`: `lha_zxtune.cpp` is compiled into this
# target and includes `<lhasa.h>`, so the include directory has to reach the target's own sources.
target_link_libraries(zxtune PUBLIC lhasa)
