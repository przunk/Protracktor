# HivelyTracker -- BSD-3-Clause. AHX and HVL: the Amiga synth trackers, 1,433 Modland files that
# nothing else here can open. libopenmpt has no loader for either, which is why `.ahx` and `.hvl`
# were taken out of the format list on 2026-09-04 and why they are coming back now.
#
# One source file. Nothing to configure, no generated headers, no options -- smaller even than ASAP,
# which held that title until today. `docs/PLAN_FORMATS.md` §6 has the measurement: 80 of 80 sampled
# files loaded from a buffer, were audible and reached a song end.
set(HIVELY_SRC "${CMAKE_CURRENT_SOURCE_DIR}/../../vendor/hively/hvl2wav")

if(NOT EXISTS "${HIVELY_SRC}/replay.c")
    message(FATAL_ERROR
        "HivelyTracker sources are missing. Run ./scripts/fetch-native-deps.sh first.\n"
        "  expected: ${HIVELY_SRC}/replay.c")
endif()

add_library(hively STATIC "${HIVELY_SRC}/replay.c")
target_include_directories(hively PUBLIC "${HIVELY_SRC}")

# 2006 Amiga C. The warnings are upstream's -- unterminated `strncpy`, mostly -- and silencing them
# keeps them from burying one of ours. What is *not* done here is copy upstream's makefile, which
# sets -D_BIG_ENDIAN_ for `PLATFORM=Linux`; that flag only reaches the WAV writer we do not build,
# but it is wrong on every Linux anyone runs and is not worth carrying forward.
target_compile_options(hively PRIVATE -w)

# `types.h` typedefs uint32 to `unsigned long`, which is 32 bits on arm64's cousin armeabi-v7a and
# 64 on arm64 and x86_64. Both were measured through `./scripts/probe-hively.py`, which builds the
# probe once each way: they agree on what plays, the subsong count, whether a tune ends and how long
# it is. The typedefs are therefore left exactly as upstream wrote them.
