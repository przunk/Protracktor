/*
 * Hand-written stand-in for the config.h that libsidplayfp's `configure` would produce.
 *
 * The release tarball ships a working `configure`, but running it per Android ABI would mean three
 * cross-compiles feeding three generated headers into one CMake build. The defines it produces that
 * actually matter here are the same on every ABI we target -- all little-endian, `int` four bytes,
 * `short` two -- so they are written down once instead.
 *
 * Kept deliberately short: only what the sources check. Compare against a freshly generated
 * config.h whenever the vendored version is bumped.
 */
#pragma once

#define PACKAGE "libsidplayfp"
#define PACKAGE_NAME "libsidplayfp"
#define PACKAGE_VERSION "3.1.1"
#define PACKAGE_STRING "libsidplayfp 3.1.1"
#define PACKAGE_TARNAME "libsidplayfp"
#define PACKAGE_BUGREPORT ""
#define PACKAGE_URL ""
#define VERSION "3.1.1"

#define HAVE_STDINT_H 1
#define HAVE_INTTYPES_H 1
#define HAVE_STDLIB_H 1
#define HAVE_STRING_H 1
#define HAVE_STRINGS_H 1
#define HAVE_STDIO_H 1
#define HAVE_UNISTD_H 1
#define HAVE_FCNTL_H 1
#define HAVE_SYS_TYPES_H 1
#define HAVE_SYS_STAT_H 1
#define STDC_HEADERS 1

/* configure probes the compiler for these; the NDK's clang has both. Without them sidcxx11.h
   #errors out with "This is not a C++11 compiler" regardless of the -std flag actually in use. */
#define HAVE_CXX11 1
#define HAVE_CXX20 1

#define HAVE_INT8_T 1
#define HAVE_INT16_T 1
#define HAVE_INT32_T 1
#define HAVE_UINT8_T 1
#define HAVE_UINT16_T 1
#define HAVE_UINT32_T 1

#define HAVE_STRCASECMP 1
#define HAVE_STRNCASECMP 1

#define SIZEOF_INT 4
#define SIZEOF_SHORT 2

/* Every ABI this app ships is little-endian. */
/* #undef WORDS_BIGENDIAN */

/* Android has neither OSS nor exSID hardware. */
/* #undef HAVE_SYS_SOUNDCARD_H */
/* #undef EXSID_THREADED */
