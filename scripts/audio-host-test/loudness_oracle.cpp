// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later
#include "../../native/engine/loudness.h"
#include "../../native/engine/stereo_mix.h"
#include <array>
#include <bit>
#include <cstdlib>
#include <iomanip>
#include <iostream>

/** Feed the exact decoded WEB PCM through the native headers, outside WASM and JavaScript. */
int main(int argc, char** argv) {
    static_assert(std::endian::native == std::endian::little);
    if (argc != 9) return 2;
    const int enabled=std::atoi(argv[1]), width=std::atoi(argv[2]), rate=std::atoi(argv[3]);
    if (rate<8000 || rate>192000 || (width!=0 && width!=50 && width!=100)) return 2;
    protracktor::VolumeNormalizer normalizer;
    normalizer.configure(enabled!=0,{std::atof(argv[4]),std::atof(argv[5]),std::atof(argv[6]),std::atoi(argv[7])!=0});
    const int block=std::atoi(argv[8]);
    std::array<float,4096*2> buffer{};
    if (block<=0 || block>4096) return 2;
    protracktor::StereoMixer mixer;
    while (std::cin.read(reinterpret_cast<char*>(buffer.data()),block*2*sizeof(float)) || std::cin.gcount()>0) {
        const auto size=std::cin.gcount();
        if (size%(2*sizeof(float))!=0) return 3;
        const auto frames=size/(2*sizeof(float));
        mixer.process(buffer.data(),frames,width,rate);
        normalizer.process(buffer.data(),frames,rate);
        std::cout.write(reinterpret_cast<const char*>(buffer.data()),size);
    }
    const auto profile=normalizer.profile();
    std::cerr<<std::setprecision(17)<<profile.seconds<<' '<<profile.lufs<<' '<<profile.peak<<'\n';
    return std::cout.good()?0:4;
}
