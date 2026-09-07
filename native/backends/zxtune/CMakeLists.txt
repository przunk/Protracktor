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
#   ym_vtx.cpp     VTX is LHA-compressed and would pull in `3rdparty/lhasa` for 879 files.
#   resampler.cpp  reaches for an N64 emulator to resample. Oboe does ours.
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
file(GLOB ZX_SOURCES
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
)
# `encoding.cpp` is excluded for a reason the host build could not have shown. It defines
# `Utf16ToUtf8(std::basic_string_view<uint16_t>)`, and libc++ -- which the NDK uses -- has no
# `std::char_traits<unsigned short>`, so it does not compile there. libstdc++ on the host does, which
# is why the probe built cleanly and the first Android build did not. Nothing on the AY path calls
# it; if that ever changes the link will say so rather than the audio going quiet.
list(FILTER ZX_SOURCES EXCLUDE REGEX "/(ayemul|ym_vtx|resampler|encoding)\\.cpp$")

add_library(zxtune STATIC ${ZX_SOURCES})
target_include_directories(zxtune PUBLIC
    "${ZX}/src" "${ZX}/include" "${ZX}" "${ZX}/3rdparty/fmt/include")

# C++20 is not a preference here. ZXTune's own headers use `std::to_address` and a `concept`
# declaration, so C++17 does not compile them; the app is already at 20.
set_target_properties(zxtune PROPERTIES CXX_STANDARD 20 CXX_STANDARD_REQUIRED ON)

# Somebody else's warnings, and there are a great many. Silenced so they do not bury one of ours.
target_compile_options(zxtune PRIVATE -w)
