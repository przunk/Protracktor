# lhasa -- ISC. An LHA/LZH decompressor, and the only library vendored here that decodes no music.
#
# It is built because two unrelated wishes turn out to need the same thing: ZXTune's `.ym` and
# `.vtx` decoders read an LHA-compressed stream (`docs/STATUS.md` C20 -- 4,961 Modland `.ym` files
# indexed and unopenable), and every UnExoticA tune lives inside a `.lha` archive
# (`docs/PLAN_CATALOGUES.md`). The first needs the raw *decoder* half of this library; the second
# needs the *reader* half, which walks an archive's headers. Both halves are here, so both are built.
#
# Globbed, then four names taken back out. `lib/` is the whole library and the two architecture
# files guard themselves on `_WIN32`, so "every C file in lib/" is nearly the source set -- but
# lhasa has four `.c` files that are **textually included** by the decoders that use them
# (`lh5_decoder.c` does `#include "lh_new_decoder.c"`, with the ring-buffer sizes defined above the
# include). They are `EXTRA_DIST` in upstream's `Makefile.am` and are not compiled on their own;
# compiling them anyway is 40 errors about undeclared `HISTORY_BITS`. The exclusion is read off
# that `Makefile.am`, which is the list upstream maintains.
#
# `lib/public/` holds the installed headers and is what anything outside this directory includes --
# `lib/` itself is private and its headers are only reachable from the sources here.
#
# No `config.h`: nothing under `lib/` includes one. The autotools build generates one for the
# command-line tool in `src/`, which we do not build.
set(LHASA "${CMAKE_CURRENT_SOURCE_DIR}/../vendor/lhasa")

if(NOT EXISTS "${LHASA}/lib/lha_decoder.c")
    message(FATAL_ERROR
        "lhasa sources are missing. Run ./scripts/fetch-native-deps.sh first.\n"
        "  expected: ${LHASA}/lib/lha_decoder.c")
endif()

file(GLOB LHASA_SOURCES CONFIGURE_DEPENDS "${LHASA}/lib/*.c")
list(FILTER LHASA_SOURCES EXCLUDE REGEX
     "/(bit_stream_reader|lh_new_decoder|pma_common|tree_decode)\\.c$")

add_library(lhasa STATIC ${LHASA_SOURCES})
target_include_directories(lhasa PUBLIC "${LHASA}/lib/public")
target_include_directories(lhasa PRIVATE "${LHASA}/lib")

# Upstream's own warnings, not ours. This is vendored C from 2011 and we do not patch it; a wall of
# warnings on every build is a wall nobody reads, which is worse than none.
target_compile_options(lhasa PRIVATE -w)
