// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later

// One line of logging, so `engine.cpp` needs nothing from a platform.
//
// It was the last Android include left in the decoder half after the split (`docs/PLAN_WEB.md` §13
// S0): thirty-odd `LOGE` calls, each reporting a decoder that refused a file. Rather than delete
// them -- they are how a refusal gets diagnosed at all -- the macro is chosen by the platform, and a
// build that is not Android's writes the same message to stderr, where a node probe will see it.

#pragma once

#if defined(__ANDROID__)

#include <android/log.h>
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, "protracktor", __VA_ARGS__)

#else

#include <cstdio>
#define LOGE(...)                       \
    do {                                \
        std::fprintf(stderr, __VA_ARGS__); \
        std::fputc('\n', stderr);       \
    } while (0)

#endif
