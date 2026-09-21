/* SPDX-FileCopyrightText: 2026 Przunk
 * SPDX-License-Identifier: GPL-3.0-or-later
 *
 * libzakalwe's `include/zakalwe/tree.h`: which red-black tree implementation to use.
 *
 * Its `configure` prefers the system's `<sys/tree.h>` and falls back to the copy it carries.
 * bionic has no `<sys/tree.h>`, so the copy is the answer -- the same answer glibc gives, which is
 * why the host build uses it too.
 */
#include <zakalwe/ztree.h>
