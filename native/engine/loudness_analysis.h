// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later
#pragma once
#include "engine.h"
#include "loudness.h"
#include "stereo_mix.h"
#include <chrono>
#include <array>

namespace protracktor {
/** Control/worker only. Never calls startedPlaying (which starts independent length emulators). */
inline LoudnessProfile analyseLoudness(Backend& backend, int subsong, int width,
    int budgetMs=200, double limitSeconds=20) {
    if (subsong != backend.currentSubsong() && !backend.selectSubsong(subsong)) return {};
    const int rate=backend.preferredSampleRate()>0?backend.preferredSampleRate():48000;
    if (rate<8000 || rate>192000) return {};
    const auto deadline=std::chrono::steady_clock::now()+std::chrono::milliseconds(std::clamp(budgetMs,1,500));
    const auto limit=static_cast<std::size_t>(rate*std::clamp(limitSeconds,0.4,30.));
    LoudnessMeter meter; StereoMixer mixer;
    std::array<float,4096*2> block{};
    std::size_t done=0;
    while (done<limit && std::chrono::steady_clock::now()<deadline) {
        const auto wanted=std::min<std::size_t>(4096,limit-done);
        const auto got=backend.render(rate,wanted,block.data());
        mixer.process(block.data(),got,width,rate);
        meter.process(block.data(),got,rate); done+=got;
        if (got<wanted) return meter.profile(true);
    }
    return meter.profile(false);
}
} // namespace protracktor
