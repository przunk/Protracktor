// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later

// `Binary::Compression::Lha`, ZXTune's two-function LHA interface, implemented against **upstream
// lhasa** instead of the patched copy ZXTune bundles.
//
// **Why this file exists rather than another line in the source glob.** ZXTune ships its own
// `src/binary/compression/src/lha.cpp`, and it does the same thing this does. It cannot be
// compiled here: it includes `3rdparty/lhasa/lib/lha_decoder.h`, a path that only exists inside
// ZXTune's tree, and its copy of lhasa is patched -- `lha_decoder_for_name` returns a non-const
// pointer there and a const one upstream, so the file does not build against the real library. The
// choice was between fetching a second, older, forked lhasa into the vendor tree for the sake of
// forty lines, or writing the forty lines. `docs/ARCHITECTURE.md` §3 says we do not fork a
// dependency; carrying two copies of one is the same bargain wearing a different hat.
//
// What it unlocks: `formats/chiptune/aym/ym_vtx.cpp`, the decoder for YM2!/YM3!/YM5!/YM6! and VTX.
// Modland's `.ym` files are LHA-packed and were unopenable without it (`docs/STATUS.md` C20).

#include "binary/compression/lha.h"

#include "binary/container.h"
#include "binary/data_builder.h"
#include "binary/input_stream.h"

#include <lhasa.h>

#include <algorithm>

namespace Binary::Compression::Lha {
namespace {

/** lhasa pulls; ZXTune's stream pushes. This is the whole adaptation. */
std::size_t readFromStream(void *buffer, std::size_t length, void *opaque) {
    return static_cast<InputStream *>(opaque)->Read(buffer, length);
}

Container::Ptr decode(InputStream &input, const LHADecoderType *type, std::size_t maxOutputSize) {
    // `stream_length` is what lhasa calls the *uncompressed* size, and it is not advisory: the
    // decoder stops there. The callers -- both in `ym_vtx.cpp` -- read it out of the file's own
    // header, so a corrupt file asks for a plausible number and gets fewer bytes back, which is
    // the case the length check below is for.
    LHADecoder *decoder = ::lha_decoder_new(type, &readFromStream, &input, maxOutputSize);
    if (decoder == nullptr) {
        return {};
    }

    DataBuilder result(maxOutputSize);
    const std::size_t decoded = ::lha_decoder_read(
        decoder, static_cast<uint8_t *>(result.Allocate(maxOutputSize)), maxOutputSize);
    ::lha_decoder_free(decoder);

    // Short is wrong here, not merely short. A YM header states how long the register dump is and
    // the dump is read as a fixed-size matrix; half of one is not half a tune, it is a decoder
    // reading past the end of what it got. Refusing is what ZXTune's own copy does too.
    if (decoded != maxOutputSize) {
        return {};
    }
    result.Resize(decoded);
    return result.CaptureResult();
}

}  // namespace

Container::Ptr DecodeRawData(InputStream &input, const String &method, std::size_t maxOutputSize) {
    // The method is the five characters out of the LHA header -- "-lh5-" for everything Modland
    // holds. An unknown one is a null type, which is a refusal rather than a crash.
    if (const LHADecoderType *type = ::lha_decoder_for_name(method.c_str())) {
        return decode(input, type, maxOutputSize);
    }
    return {};
}

Container::Ptr DecodeRawData(const Container &input, const String &method,
                             std::size_t maxOutputSize) {
    InputStream stream(input);
    return DecodeRawData(stream, method, maxOutputSize);
}

}  // namespace Binary::Compression::Lha
