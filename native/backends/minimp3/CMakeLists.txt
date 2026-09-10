# minimp3 -- CC0-1.0. MP3, and the smallest vendored decoder here: two headers and one object.
#
# **Not a music format, and that is the interesting part.** This app is a chiptune player and MP3 is
# the format its whole point is not (`docs/BACKLOG.md` A29). It is here for the owner's own local
# files -- a rip of something, a recording -- and deliberately not for the catalogues, none of which
# holds an MP3.
set(MINIMP3 "${CMAKE_CURRENT_SOURCE_DIR}/../../vendor/minimp3")

if(NOT EXISTS "${MINIMP3}/minimp3_ex.h")
    message(FATAL_ERROR
        "minimp3 sources are missing. Run ./scripts/fetch-native-deps.sh first.\n"
        "  expected: ${MINIMP3}/minimp3_ex.h")
endif()

add_library(minimp3 STATIC "${CMAKE_CURRENT_SOURCE_DIR}/minimp3.c")
target_include_directories(minimp3 PUBLIC "${MINIMP3}")

# **PUBLIC, and that is load-bearing.** It decides whether `mp3d_sample_t` is a `short` or a `float`,
# so a build where the decoder and its caller disagree links without complaint and then reads every
# sample as half of two. PUBLIC is what makes it impossible for them to disagree.
#
# Float because Oboe and the worklet both want floats: converting here costs nothing and converting
# afterwards would cost a pass over every buffer.
target_compile_definitions(minimp3 PUBLIC MINIMP3_FLOAT_OUTPUT)

# Somebody else's warnings. The library is decoder arithmetic and says so in its own README.
target_compile_options(minimp3 PRIVATE -w)
