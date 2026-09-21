/* SPDX-FileCopyrightText: 2026 Przunk
 * SPDX-License-Identifier: GPL-3.0-or-later
 *
 * libzakalwe's `include/zakalwe/config.h`, answered for bionic rather than for the host.
 *
 * Its `configure` asks four questions by compiling four test programs. Cross-compiled, each one
 * would be answered about this Linux box, so they are answered here about Android at API 29:
 *
 *   `reallocarray`  bionic has it from API 29, which is the app's `minSdk` -- so no replacement.
 *   `strlcpy`       bionic has always had it; it is a BSD function and bionic is BSD-descended.
 *   `select`        present.
 *   `mkdtemp`       present.
 */
#ifndef _Z_CONFIG_H_
#define _Z_CONFIG_H_

/* #define _Z_NEED_REALLOCARRAY 1 */
/* #define _Z_NEED_STRL 1 */

#endif

#define _Z_SYSCALL_SELECT 1
#define _Z_HAS_MKDTEMP 1
