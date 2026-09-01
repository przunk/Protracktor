# sc68 -- GPL-2.0-or-later. Atari ST: SNDH and raw YM.
#
# SNDH files are 68000 machine code, not note data, so playing one means emulating an Atari ST.
# That is what sc68 brings: emu68 (the CPU), io68 (the YM2149, MFP and friends), file68 (loading and
# SNDH parsing), api68 (the player) and unice68 (the ICE depacker these files often arrive in).
#
# Released in 2003 with autotools. Rather than cross-compiling `configure` for Android, the handful
# of defines it would have produced are supplied directly below -- the alternative is carrying a
# generated config into the repository, where nobody would ever notice it going stale.
set(SC68_SRC "${CMAKE_CURRENT_SOURCE_DIR}/../../vendor/sc68")

if(NOT EXISTS "${SC68_SRC}/api68/api68.c")
    message(FATAL_ERROR
        "sc68 sources are missing. Run ./scripts/fetch-native-deps.sh first.\n"
        "  expected: ${SC68_SRC}/api68/api68.c")
endif()

# The tools (as68, desa68, sourcer68, debug68, info68, sc68) are deliberately not here: they are
# command-line programs, and one of them defines main().
set(SC68_MODULES emu68 io68 file68 api68 unice68)
set(SC68_SOURCES "")
foreach(module IN LISTS SC68_MODULES)
    file(GLOB module_sources CONFIGURE_DEPENDS "${SC68_SRC}/${module}/*.c")
    list(APPEND SC68_SOURCES ${module_sources})
endforeach()

# unice68/unice68.c is the command-line depacker and defines main(). Harmless inside a shared
# library, but it has no business in one, and it is what a host build trips over first.
list(FILTER SC68_SOURCES EXCLUDE REGEX "unice68/unice68\\.c$")

list(LENGTH SC68_SOURCES SC68_SOURCE_COUNT)
if(SC68_SOURCE_COUNT LESS 40)
    message(FATAL_ERROR
        "Found only ${SC68_SOURCE_COUNT} sc68 sources, expected around fifty. "
        "The vendored tree is not the shape this expects.")
endif()
message(STATUS "sc68: ${SC68_SOURCE_COUNT} sources")

add_library(sc68 STATIC ${SC68_SOURCES})

target_include_directories(sc68 PUBLIC "${SC68_SRC}")

target_compile_definitions(sc68 PRIVATE
    HAVE_CONFIG_H=0
    # 2003-era code assumes a hosted C library with these; Android's bionic has them all.
    HAVE_STDLIB_H=1
    HAVE_STRING_H=1
    HAVE_STDIO_H=1
    HAVE_UNISTD_H=1
    # Where sc68 would look for external replay binaries. Empty because we ship none: an SNDH file
    # carries its own 68000 code, which is the whole reason this backend needs a CPU emulator. The
    # .sc68 container format does use external replays and will not play until that is dealt with --
    # noted in docs/BACKLOG.md rather than left to be discovered.
    SC68_SHARED_DATA_PATH=\"\"
)

# Twenty-three years of new warnings are not defects we introduced and not ones we can fix without
# forking upstream. Silenced rather than left to bury a warning that IS ours.
target_compile_options(sc68 PRIVATE
    -w
    -Wno-implicit-function-declaration
    -Wno-int-conversion
    -Wno-incompatible-pointer-types
)
