# game-music-emu -- LGPL-2.1-or-later. NES, SNES, Game Boy, Sega, PC Engine, ZX Spectrum AY, MSX.
#
# The only vendored library in this project that ships a CMake build that works as-is, so this file
# configures it rather than rebuilding it. Options are set before add_subdirectory because that is
# the only point at which they can be.
set(GME_SRC "${CMAKE_CURRENT_SOURCE_DIR}/../../vendor/gme")

if(NOT EXISTS "${GME_SRC}/CMakeLists.txt")
    message(FATAL_ERROR
        "game-music-emu sources are missing. Run ./scripts/fetch-native-deps.sh first.\n"
        "  expected: ${GME_SRC}/CMakeLists.txt")
endif()

set(GME_BUILD_STATIC ON CACHE BOOL "" FORCE)
set(GME_BUILD_SHARED OFF CACHE BOOL "" FORCE)

# VGZ files are gzipped VGM, and there are 14,167 of them in Modland alone. The NDK carries zlib.
set(GME_ZLIB ON CACHE BOOL "" FORCE)

# gme has its own SAP emulator; ASAP is the reference implementation for that format and is asked
# first anyway. Off, so the binary does not carry two answers to one question.
set(USE_GME_SAP OFF CACHE BOOL "" FORCE)

# "Nuked" is LGPL-2.1+; the alternative "MAME" core is GPL-2+. Either would be compatible with our
# GPL-3, but the weaker copyleft leaves more room for whoever reads this next.
set(GME_YM2612_EMU "Nuked" CACHE STRING "" FORCE)

add_subdirectory("${GME_SRC}" "${CMAKE_CURRENT_BINARY_DIR}/gme-build" EXCLUDE_FROM_ALL)

# gme's targets export their include directory only for an installed package, not for one consumed
# from the build tree, so gme.h is not on the path without this.
target_include_directories(gme_static INTERFACE "${GME_SRC}/gme")
