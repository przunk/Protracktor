# ASAP -- GPL-2.0-or-later. Atari 8-bit POKEY: SAP, plus Chaos Music Composer, Raster Music Tracker,
# Theta Music Composer, Delta Music Composer, Music ProTracker and their double-play variants.
#
# The easiest vendored library in this project by a wide margin. ASAP is written in Ć and shipped as
# transpiled C: one 8,000-line asap.c with no configure step, no generated headers, and no options.
set(ASAP_SRC "${CMAKE_CURRENT_SOURCE_DIR}/../../vendor/asap")

if(NOT EXISTS "${ASAP_SRC}/asap.c")
    message(FATAL_ERROR
        "ASAP sources are missing. Run ./scripts/fetch-native-deps.sh first.\n"
        "  expected: ${ASAP_SRC}/asap.c")
endif()

# **`asap_measure.c` in `asap.c`'s place**: it includes `asap.c` and adds the measurement of a SAP
# that states no length (`docs/STATUS.md` C91), which needs ASAP's internals. One translation unit,
# so every ASAP symbol is still defined exactly once.
add_library(asap STATIC "${CMAKE_CURRENT_SOURCE_DIR}/asap_measure.c")
target_include_directories(asap PUBLIC "${ASAP_SRC}" "${CMAKE_CURRENT_SOURCE_DIR}")

# Transpiler output. The warnings are about how a generator writes C, not about anything we could
# act on -- silenced so they do not bury a warning that is ours.
target_compile_options(asap PRIVATE -w)
