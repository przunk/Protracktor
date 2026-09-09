// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later

// Reading one file out of an LHA archive held in memory.
//
// **This is the UnExoticA half of lhasa** (`docs/PLAN_UNEXOTICA.md`). The other half -- the raw
// decoder, without any archive around it -- is what ZXTune's `.ym` path uses, and the two share
// nothing but the library. If UnExoticA is ever removed, this file and its JNI wrapper go with it
// and the YM decoder is untouched.
//
// In memory rather than from a file, because that is the shape the app already has: an archive
// arrives from `RemoteFiles.fetch` as a `ByteArray` and is cached by URL. Writing it to a temporary
// file to hand lhasa a `FILE *` would be a second copy of a file we already hold.

#pragma once

#include <cstddef>
#include <string>
#include <vector>

namespace protracktor {

/** Every member's name, `directory/file`, in the order the archive stores them. */
std::vector<std::string> lhaList(const void *data, std::size_t size);

/**
 * The bytes of one member, or an empty vector if it is not there.
 *
 * [member] is matched against `directory/file` case-insensitively, with `\` treated as `/`: LHA
 * archives written on an Amiga use either separator, and UnExoticA's index names its members with
 * one while its archives store them with the other.
 */
std::vector<char> lhaExtract(const void *data, std::size_t size, const std::string &member);

}  // namespace protracktor
