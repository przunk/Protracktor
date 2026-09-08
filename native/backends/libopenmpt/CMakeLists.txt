# libopenmpt -- BSD-3-Clause. Tracker formats: MOD, XM, S3M, IT and dozens more.
#
# Upstream ships no CMakeLists, only build/android_ndk/Android.mk. Rather than transcribe its ~150
# source paths into a list that silently rots at the next version bump, this file READS that
# Android.mk and takes the source list from it. Upstream stays the authority on what belongs in the
# library, and a bump that adds or drops a loader is picked up without anyone noticing it had to be.

set(LOMPT_SRC "${CMAKE_CURRENT_SOURCE_DIR}/../../vendor/libopenmpt")
set(LOMPT_MK "${LOMPT_SRC}/build/android_ndk/Android.mk")

if(NOT EXISTS "${LOMPT_MK}")
    message(FATAL_ERROR
        "libopenmpt sources are missing. Run ./scripts/fetch-native-deps.sh first.\n"
        "  expected: ${LOMPT_MK}")
endif()

# Only the four directories that hold the library proper. The other LOCAL_SRC_FILES entries in that
# file sit behind MPT_WITH_* guards for optional codecs (minimp3, stb_vorbis) that we do not enable,
# and skipping them here is what keeps this regex honest rather than merely convenient.
# Read whole and match globally rather than line by line. Android.mk continues its source list with
# trailing backslashes, and file(STRINGS) treats those as list escapes -- the entire block arrives
# as one element beginning "LOCAL_SRC_FILES += \\", so a per-line "^" anchor matches nothing at all.
file(READ "${LOMPT_MK}" LOMPT_MK_TEXT)
string(REGEX MATCHALL
       "(common|libopenmpt|soundlib|sounddsp)/[A-Za-z0-9_/]+\\.cpp"
       LOMPT_REL_SOURCES "${LOMPT_MK_TEXT}")
list(REMOVE_DUPLICATES LOMPT_REL_SOURCES)

set(LOMPT_SOURCES "")
foreach(rel IN LISTS LOMPT_REL_SOURCES)
    list(APPEND LOMPT_SOURCES "${LOMPT_SRC}/${rel}")
endforeach()

# A parse that quietly yields nothing would produce a library with no decoders in it that still
# links. Upstream has well over a hundred of these; anything near zero means the format changed.
list(LENGTH LOMPT_SOURCES LOMPT_SOURCE_COUNT)
if(LOMPT_SOURCE_COUNT LESS 100)
    message(FATAL_ERROR
        "Parsed only ${LOMPT_SOURCE_COUNT} sources from ${LOMPT_MK}, expected well over 100.\n"
        "Upstream's Android.mk layout has probably changed -- fix the parse, do not lower this bound.")
endif()
message(STATUS "libopenmpt: ${LOMPT_SOURCE_COUNT} sources from upstream Android.mk")

add_library(openmpt STATIC ${LOMPT_SOURCES})

target_compile_definitions(openmpt PRIVATE LIBOPENMPT_BUILD MPT_WITH_ZLIB)
target_compile_options(openmpt PRIVATE -fvisibility=hidden -Wall -fexceptions -frtti)
set_target_properties(openmpt PROPERTIES CXX_STANDARD 20 CXX_STANDARD_REQUIRED ON)

# Mirrors LOCAL_C_INCLUDES from upstream's Android.mk, with the tarball root standing in for
# LOCAL_PATH -- ndk-build resolves that to the makefile's own directory, which is not where these
# sources actually live.
target_include_directories(openmpt PUBLIC "${LOMPT_SRC}" "${LOMPT_SRC}/src" "${LOMPT_SRC}/common")

# zlib comes with the NDK; MPT_WITH_ZLIB above is what asks for it.
#
# Emscripten has no NDK to take it from, but it ships zlib as a *port* -- source it fetches and
# builds on demand. The flag has to be on the compile line as well as the link line: it is what puts
# the port's headers on the include path, and without it `load_j2b.cpp` cannot find `zlib.h` while
# every other file in the library compiles happily, which reads as one broken file rather than a
# missing dependency.
if(EMSCRIPTEN)
    target_compile_options(openmpt PUBLIC -sUSE_ZLIB=1)
    target_link_options(openmpt PUBLIC -sUSE_ZLIB=1)
else()
    target_link_libraries(openmpt PUBLIC z)
endif()
