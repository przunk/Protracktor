# sc68 3.0.0b -- GPL-2.0-or-later. Atari ST: SNDH and the .sc68 container.
#
# SNDH files are 68000 machine code, not note data, so playing one means emulating an Atari ST.
# That is what sc68 brings: emu68 (the CPU), io68 (the YM2149, MFP, STE microwire and Paula),
# file68 (loading, SNDH parsing and a database of known tunes), libsc68 (the player) and unice68
# (the ICE depacker these files usually arrive in).
#
# ## Why 3.0.0b and not the 2.2.1 release
#
# Measured, not assumed. `./scripts/probe-sc68.py` runs both libraries over the same thirty SNDH
# files and ten .sc68 files picked deterministically from Modland:
#
#     .sndh plays      14/30  ->  30/30
#     .sc68 plays      10/10  ->  10/10
#
# 2.2.1 is from 2003 and its SNDH support is roughly half. Nothing on our side changes that.
#
# ## What that cost
#
# 3.x is not 2.2.1 with more formats: `api68_*` is gone entirely and there is no compatibility
# header, so `Sc68Backend` is written against `sc68_*` instead. It also has no released tarball --
# it exists only in SourceForge SVN, fetched by ./scripts/fetch-sc68-svn.py at a pinned revision
# with a checksum manifest.
set(SC68_SRC "${CMAKE_CURRENT_SOURCE_DIR}/../../vendor/sc68-3")
set(SC68_GEN "${CMAKE_CURRENT_SOURCE_DIR}/generated")

if(NOT EXISTS "${SC68_SRC}/libsc68/src/api68.c")
    message(FATAL_ERROR
        "sc68 3.0.0b sources are missing. Run ./scripts/fetch-sc68-svn.py first.\n"
        "  expected: ${SC68_SRC}/libsc68/src/api68.c")
endif()

# The source lists come from upstream's own Makefile.am files, not from a glob. Globbing picks up
# three kinds of file that must not be compiled:
#
#   * lines68.c   -- the monolithic alternative to the sixteen line*_68.c files, and #errors unless
#                    EMU68_MONOLITIC is set;
#   * cc68.c      -- #errors that it is deprecated;
#   * ym_atarist_table.c / ym_linear_table.c -- data tables that are #included, not compiled;
#   * unice68.c   -- the command-line depacker, which defines main().
#
# A count assert below catches the tree changing shape under us, which is how the libopenmpt list
# silently became empty once.
set(SC68_EMU68
    emu68.c error68.c getea68.c inst68.c ioplug68.c mem68.c
    line0_68.c line1_68.c line2_68.c line3_68.c line4_68.c line5_68.c line6_68.c line7_68.c
    line8_68.c line9_68.c lineA_68.c lineB_68.c lineC_68.c lineD_68.c lineE_68.c lineF_68.c
    table68.c)
set(SC68_IO68
    io68.c mfp_io.c mfpemul.c mw_io.c mwemul.c paula_io.c paulaemul.c shifter_io.c
    ym_envel.c ym_blep.c ym_dump.c ym_io.c ym_puls.c ymemul.c)
# vfs68_ao.c and vfs68_curl.c are here although libao and libcurl are not: they compile to stubs,
# and init68.c calls their shutdown functions unconditionally. Dropping them fails at link.
set(SC68_FILE68
    error68.c file68.c gzip68.c ice68.c init68.c
    vfs68.c vfs68_ao.c vfs68_curl.c vfs68_fd.c vfs68_file.c vfs68_mem.c vfs68_null.c vfs68_z.c
    msg68.c option68.c registry68.c rsc68.c string68.c timedb68.c uri68.c)
set(SC68_LIB68 api68.c conf68.c libsc68.c mixer68.c)
# New in 3.x: a small configuration/dialogue module that sc68_cntl calls into. Not optional.
set(SC68_DIAL68 dial68.c dial_conf.c dial_tsel.c dial_finf.c)
set(SC68_UNICE68 unice68_pack.c unice68_unpack.c unice68_version.c)

set(SC68_SOURCES "")
foreach(f IN LISTS SC68_EMU68)
    list(APPEND SC68_SOURCES "${SC68_SRC}/libsc68/emu68/${f}")
endforeach()
foreach(f IN LISTS SC68_IO68)
    list(APPEND SC68_SOURCES "${SC68_SRC}/libsc68/io68/${f}")
endforeach()
foreach(f IN LISTS SC68_FILE68)
    list(APPEND SC68_SOURCES "${SC68_SRC}/file68/src/${f}")
endforeach()
foreach(f IN LISTS SC68_LIB68)
    list(APPEND SC68_SOURCES "${SC68_SRC}/libsc68/src/${f}")
endforeach()
foreach(f IN LISTS SC68_DIAL68)
    list(APPEND SC68_SOURCES "${SC68_SRC}/libsc68/dial68/${f}")
endforeach()
foreach(f IN LISTS SC68_UNICE68)
    list(APPEND SC68_SOURCES "${SC68_SRC}/unice68/${f}")
endforeach()

foreach(f IN LISTS SC68_SOURCES)
    if(NOT EXISTS "${f}")
        message(FATAL_ERROR "sc68: ${f} is missing. The vendored tree is not the shape this expects.")
    endif()
endforeach()

list(LENGTH SC68_SOURCES SC68_SOURCE_COUNT)
message(STATUS "sc68 3.0.0b: ${SC68_SOURCE_COUNT} sources")

add_library(sc68 STATIC ${SC68_SOURCES})

# generated/ first: it holds config.h, sc68/file68_features.h and sc68/trap68.h, which autoconf
# would have produced and which no amount of include order in the vendored tree provides.
# `generated/` twice, deliberately: some sources include the features header as
# <sc68/file68_features.h> and others as "file68_features.h". Autoconf generates it into
# file68/sc68/ and puts that directory on the include path, which makes both spellings work. Two
# include paths reproduce that with one file, rather than two copies that can drift apart.
target_include_directories(sc68 PUBLIC
    "${SC68_GEN}"
    "${SC68_GEN}/sc68"
    "${SC68_SRC}"
    "${SC68_SRC}/libsc68"
    "${SC68_SRC}/libsc68/sc68"
    "${SC68_SRC}/libsc68/emu68"
    "${SC68_SRC}/libsc68/io68"
    "${SC68_SRC}/file68"
    "${SC68_SRC}/file68/sc68"
    "${SC68_SRC}/unice68"
)

target_compile_definitions(sc68 PRIVATE
    HAVE_CONFIG_H=1
    # **Assertions off, deliberately, in every build including debug.**
    #
    # sc68 asserts on an MFP timer mode it does not implement (event-count / pulse-width) and then
    # handles it anyway -- mfpemul.c sets the timer to zero and carries on. Two of thirty measured
    # SNDH files reach it. With assertions on, a debug build aborts the whole app on an ordinary
    # file from the user's library; with them off, all thirty play. Upstream clearly intends the
    # fallback, and a decoder's assertion is not a reason to take a music player down.
    NDEBUG=1
)

# zlib for file68's gzip support (FILE68_Z in generated/sc68/file68_features.h). The NDK ships it.
target_link_libraries(sc68 PUBLIC z)

# Twenty years of new warnings are not defects we introduced and not ones we can fix without forking
# upstream. Silenced rather than left to bury a warning that IS ours.
target_compile_options(sc68 PRIVATE
    -w
    -Wno-implicit-function-declaration
    -Wno-int-conversion
    -Wno-incompatible-pointer-types
)
