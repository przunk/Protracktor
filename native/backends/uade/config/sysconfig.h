/* SPDX-FileCopyrightText: 2026 Przunk
 * SPDX-License-Identifier: GPL-3.0-or-later
 *
 * What UADE's `configure` would have written, answered for Android instead of for the host.
 *
 * `src/sysconfig.h` is generated: upstream's configure probes the machine it runs on and writes a
 * header of `HAVE_*` answers. Cross-compiling makes that probe answer the wrong question -- it
 * would describe this Linux box, not the phone -- so the answers are written here, each one about
 * bionic at API 29, which is the app's `minSdk`.
 *
 * **Only the macros the sources actually read are here**, and there are nineteen of them rather
 * than the two hundred configure emits. The set was taken from the code:
 *
 *     grep -rhoE '#if(n?def)? +(HAVE_|SIZEOF_)[A-Z0-9_]+' src/
 *
 * A macro nobody reads is a claim nobody checks, and the long generated header is mostly that.
 *
 * The three sizes hold on every ABI the app ships (arm64-v8a, armeabi-v7a, x86_64): `int` is 4
 * bytes and `long long` is 8 on all of them, which is what `include/sysdeps.h` turns into `uae_u32`
 * and `uae_u64`. `long` is deliberately not defined -- it is 4 bytes on 32-bit ARM and 8 on the
 * other two, and nothing here needs it, so a wrong answer cannot be given.
 */
#ifndef _H_SYSCONFIG
#define _H_SYSCONFIG

/* Headers. bionic has all of these; `values.h` is the one it does not, and the sources guard on
 * exactly that. */
#define HAVE_DIRENT_H 1
#define HAVE_FCNTL_H 1
#define HAVE_STRING_H 1
#define HAVE_STRINGS_H 1
#define HAVE_SYS_STAT_H 1
#define HAVE_SYS_TYPES_H 1
#define HAVE_UNISTD_H 1
#define HAVE_UTIME_H 1
/* #undef HAVE_VALUES_H */
/* #undef HAVE_SYS_UTIME_H -- the Windows spelling */

/* Functions. */
#define HAVE_GETTIMEOFDAY 1
#define HAVE_SIGACTION 1
#define HAVE_STRDUP 1
#define HAVE_VFPRINTF 1

/* Both `<sys/time.h>` and `<time.h>` can be included together, as on any POSIX system this side of
 * 1995. */
#define TIME_WITH_SYS_TIME 1

#define STDC_HEADERS 1
#define RETSIGTYPE void

#define SIZEOF_CHAR 1
#define SIZEOF_SHORT 2
#define SIZEOF_INT 4
#define SIZEOF_LONG_LONG 8

/* An x86 asm path in `machdep/maccess.h` for reading big-endian words. ARM would need its own and
 * the portable C is what the other ABIs use, so nobody claims it here. */
/* #undef HAVE_GET_WORD_UNSWAPPED */

#endif
