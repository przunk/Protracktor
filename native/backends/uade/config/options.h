/* SPDX-FileCopyrightText: 2026 Przunk
 * SPDX-License-Identifier: GPL-3.0-or-later
 *
 * The other half of what UADE's `configure` writes: `src/frontends/include/uade/options.h`.
 *
 * Four of these six lines are answers about the machine, and two are **paths**, which is the part
 * that matters here. Upstream compiles the data directory and the `uadecore` binary's location into
 * the library, because on a desktop they are installed once and never move. On Android neither is
 * knowable at build time: the data directory is under `filesDir`, whose path contains the user id,
 * and `uadecore` lives in `nativeLibraryDir`, whose path contains the APK's install cookie.
 *
 * So both are set at runtime -- `UC_BASE_DIR` and `UC_UADECORE_FILE`, from `UadeBackend::configure`
 * -- and the compiled-in values are deliberately paths that cannot exist. A default that half
 * works is worse than one that cannot: if the runtime configuration is ever missed, UADE says it
 * cannot find its score file, which is the truth, instead of quietly reading somebody else's.
 */
#ifndef _UADE_OPTIONS_H_
#define _UADE_OPTIONS_H_

#define UADE_CONFIG_USER_MODE (0)
#define UADE_CONFIG_BASE_DIR "/nonexistent/uade-base-dir-is-set-at-runtime"
#define UADE_CONFIG_UADE_CORE "/nonexistent/uadecore-is-set-at-runtime"

/* bionic has it, and UADE uses it to seed the noise the Amiga's audio hardware would have had. */
#define UADE_CONFIG_HAVE_URANDOM

#define UADE_VERSION "3.05"

/* No `-mavx2`: it is an x86 answer, and two of the three ABIs are ARM. */

#endif /* _UADE_OPTIONS_H_ */
