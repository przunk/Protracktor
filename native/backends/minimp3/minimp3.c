// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later

// minimp3's implementation, in a translation unit of its own.
//
// The library is two headers and no build system: everything is inline until exactly one file
// defines `MINIMP3_IMPLEMENTATION`. That file is this one, so `engine.cpp` gets declarations and
// nothing else — and so the decoder is compiled **as C**, which is what it was written as.
//
// `MINIMP3_FLOAT_OUTPUT` is not set here. It is set by `CMakeLists.txt` as a PUBLIC definition of
// this target, because it changes what `mp3d_sample_t` *is* — and a define that reaches one of two
// translation units gives them different ideas of the same struct, which links cleanly and then
// reads the wrong bytes. Putting it where it cannot reach only one of them is the point.

#define MINIMP3_IMPLEMENTATION
#include <minimp3_ex.h>
