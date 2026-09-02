/* What autoconf would have produced for sc68 3.0.0b, written by hand.
 *
 * There are no autotools on this machine, so `configure` cannot run. These are the defines a trial
 * compile actually demanded -- not a guess at what configure emits, and not a copy of somebody
 * else's config.h. Each one was added because a named file failed to compile without it.
 *
 * HAVE_BASENAME is the odd one: without it api68.c pulls in sc68-libc, a replacement C library for
 * platforms that lack one. The host has basename, and so does Android's bionic.
 */
#ifndef PROTRACKTOR_SC68_3_CONFIG_H
#define PROTRACKTOR_SC68_3_CONFIG_H

#define PACKAGE_NAME "sc68"
#define PACKAGE_VERSION "3.0.0b"
#define PACKAGE_STRING "sc68 3.0.0b"
#define PACKAGE_TARNAME "sc68"
#define PACKAGE_BUGREPORT ""
#define PACKAGE_URL ""
#define VERSION "3.0.0b"
#define HAVE_STDINT_H 1
#define HAVE_STDLIB_H 1
#define HAVE_STRING_H 1
#define HAVE_STDIO_H 1
#define HAVE_UNISTD_H 1
#define HAVE_ASSERT_H 1
#define HAVE_LIMITS_H 1
#define HAVE_FCNTL_H 1
#define HAVE_SYS_STAT_H 1
#define HAVE_SYS_TYPES_H 1
#define HAVE_BASENAME 1

/* file68's feature switches. These come from config.h, NOT from file68_features.h -- that header is
 * included by almost nothing (io68/default.h and vfs68_ao.c) and defining them only there leaves
 * ice68.c compiling its "*NOT SUPPORTED*" branch. That cost a measurement run that reported 30/30
 * load failures for a library whose own NEWS claims near-perfect SNDH support; the number was the
 * probe's, not sc68's. */

/* The ICE depacker. Most SNDH files in Modland are ICE-packed, so without this almost nothing
 * loads. unice68_unpack.c is compiled in and provides it. */
#define FILE68_UNICE68 1

/* Where the replay binaries live when nothing overrides it. The probe overrides it at runtime
 * through rsc68_set_share(), which is 3.x's replacement for api68_init_t::shared_path. */
#define FILE68_SHARED_PATH ""
#define FILE68_USER_PATH ""
#define FILE68_MUSIC_PATH ""
#define FILE68_RMUSIC_PATH ""

#endif
