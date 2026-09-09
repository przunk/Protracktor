// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later

#include "lha_extract.h"

#include <lhasa.h>

#include <algorithm>
#include <cstring>

namespace protracktor {
namespace {

/** What lhasa reads through. It pulls; we hold the whole archive, so this is a cursor. */
struct MemoryStream {
    const unsigned char *data;
    std::size_t size;
    std::size_t position;
};

int streamRead(void *handle, void *buffer, std::size_t length) {
    auto *stream = static_cast<MemoryStream *>(handle);
    const std::size_t take = std::min(length, stream->size - stream->position);
    std::memcpy(buffer, stream->data + stream->position, take);
    stream->position += take;
    return static_cast<int>(take);
}

int streamSkip(void *handle, std::size_t bytes) {
    auto *stream = static_cast<MemoryStream *>(handle);
    // Refuses rather than clamping. lhasa skips over a member's compressed data to reach the next
    // header, so a skip past the end means the archive is truncated -- and clamping would leave the
    // reader parsing whatever followed as if it were a header.
    if (bytes > stream->size - stream->position) return 0;
    stream->position += bytes;
    return 1;
}

void streamClose(void *) {}

const LHAInputStreamType kMemoryStreamType = {streamRead, streamSkip, streamClose};

/** `Total_Recall\dw.intro` and `Total_Recall/dw.intro` are the same member. */
std::string normalise(const std::string &name) {
    std::string result;
    result.reserve(name.size());
    for (const char c : name) {
        if (c == '\\') {
            result.push_back('/');
        } else {
            result.push_back(static_cast<char>(std::tolower(static_cast<unsigned char>(c))));
        }
    }
    return result;
}

std::string fullName(const LHAFileHeader *header) {
    std::string name;
    if (header->path != nullptr) name += header->path;
    if (header->filename != nullptr) name += header->filename;
    return name;
}

/**
 * Walks the archive, handing each member to [visit] until it says stop.
 *
 * One reader for both entry points because the awkward part -- a stream that must outlive the
 * reader, and a reader that must be freed before it -- is worth writing once.
 */
template <typename Visit>
void walk(const void *data, std::size_t size, Visit visit) {
    MemoryStream stream{static_cast<const unsigned char *>(data), size, 0};
    LHAInputStream *input = lha_input_stream_new(&kMemoryStreamType, &stream);
    if (input == nullptr) return;
    LHAReader *reader = lha_reader_new(input);
    if (reader == nullptr) {
        lha_input_stream_free(input);
        return;
    }

    while (LHAFileHeader *header = lha_reader_next_file(reader)) {
        // Directories and symlinks are members too, and neither has bytes to read. `-lhd-` is the
        // directory method; lhasa also invents entries for directories an archive only implies.
        if (std::strcmp(header->compress_method, "-lhd-") == 0) {
            continue;
        }
        if (!visit(reader, header)) break;
    }

    lha_reader_free(reader);
    lha_input_stream_free(input);
}

}  // namespace

std::vector<std::string> lhaList(const void *data, std::size_t size) {
    std::vector<std::string> names;
    walk(data, size, [&](LHAReader *, LHAFileHeader *header) {
        names.push_back(fullName(header));
        return true;
    });
    return names;
}

std::vector<char> lhaExtract(const void *data, std::size_t size, const std::string &member) {
    const std::string wanted = normalise(member);
    std::vector<char> result;

    walk(data, size, [&](LHAReader *reader, LHAFileHeader *header) {
        const std::string name = normalise(fullName(header));
        // The full path first, then the bare filename. UnExoticA's index and its archives agree on
        // the name often enough that the second case is a fallback rather than the rule -- but a
        // member the index calls `Total_Recall/mod.x` and the archive stores flat as `mod.x` is
        // still the file the user asked for, and refusing it would be pedantry the user pays for.
        const bool matches =
            name == wanted ||
            (name.size() > wanted.size() && name.compare(name.size() - wanted.size(),
                                                         wanted.size(), wanted) == 0 &&
             name[name.size() - wanted.size() - 1] == '/') ||
            (wanted.size() > name.size() && wanted.compare(wanted.size() - name.size(),
                                                           name.size(), name) == 0 &&
             wanted[wanted.size() - name.size() - 1] == '/');
        if (!matches) return true;

        // `length` is what the header states. A short read means the archive is damaged, and the
        // vector is resized down to what actually arrived rather than left with a tail of zeros --
        // a decoder handed zeros reports a corrupt module instead of a truncated download.
        result.resize(static_cast<std::size_t>(header->length));
        std::size_t filled = 0;
        while (filled < result.size()) {
            const std::size_t read =
                lha_reader_read(reader, result.data() + filled, result.size() - filled);
            if (read == 0) break;
            filled += read;
        }
        result.resize(filled);
        return false;
    });

    return result;
}

}  // namespace protracktor
