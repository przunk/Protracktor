// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later
#pragma once

#include <algorithm>
#include <cstddef>

namespace protracktor {

/** Callback-owned PCM processing; the control thread only publishes the target width. */
class StereoMixer {
public:
    void process(float *samples, std::size_t frames, int widthPercent, int sampleRate) {
        if (frames == 0) return;
        const float target = widthPercent / 100.0f;
        // A new track starts in its selected mode, not with a burst of the default stereo.
        if (!initialized_) { width_ = target; initialized_ = true; }
        if (width_ == 1.0f && target == 1.0f) return; // Original is a bit-preserving bypass.
        const float step = 1.0f / (std::max(sampleRate, 1) * 0.01f);
        for (std::size_t i = 0; i < frames; ++i) {
            width_ += std::clamp(target - width_, -step, step);
            if (width_ == 1.0f) continue;
            const float cross = (1.0f - width_) * 0.5f;
            const float keep = 1.0f - cross;
            const float left = samples[i * 2], right = samples[i * 2 + 1];
            samples[i * 2] = keep * left + cross * right;
            samples[i * 2 + 1] = cross * left + keep * right;
        }
    }

private:
    float width_ = 1.0f;
    bool initialized_ = false;
};
} // namespace protracktor
