// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later
#include "../../native/engine/loudness.h"
#include <iostream>
#include <vector>
#include <stdexcept>
#include <limits>
using namespace protracktor;
void require(bool value,const char* message) { if (!value) throw std::runtime_error(message); }
std::vector<float> tone(int rate,double seconds,float amplitude) {
    std::vector<float> samples(static_cast<std::size_t>(rate*seconds)*2);
    for (std::size_t i=0;i<samples.size()/2;++i)
        samples[2*i]=samples[2*i+1]=amplitude*std::sin(2*3.141592653589793*1000*i/rate);
    return samples;
}
int main() {
    int failures=0,total=0;
    auto test=[&](const char* name, auto run) { ++total; try { run(); std::cout<<"✓ "<<name<<'\n'; }
        catch(const std::exception& e) { ++failures; std::cerr<<"✗ "<<name<<": "<<e.what()<<'\n'; } };
    test("calibrated stereo tone measures about -20 LUFS at both engine rates",[] {
        for (int rate:{44100,48000}) { LoudnessMeter m; auto a=tone(rate,4,.1f); m.process(a.data(),a.size()/2,rate);
            require(std::abs(m.profile().lufs+20)<.15,"K-weighted tone calibration failed"); }
    });
    test("absolute and relative gating exclude long silence",[] {
        LoudnessMeter m; auto a=tone(48000,4,.1f); m.process(a.data(),a.size()/2,48000);
        const auto before=m.profile(); std::vector<float> silence(48000*8*2);
        m.process(silence.data(),silence.size()/2,48000);
        require(std::abs(m.profile().lufs-before.lufs)<.2,"silence changed integrated loudness");
    });
    test("short and silent signals do not become valid boost profiles",[] {
        LoudnessMeter m; auto a=tone(48000,.2,.1f); m.process(a.data(),a.size()/2,48000);
        require(!m.profile().usable(),"sub-window signal treated as integrated programme");
        m.reset(48000); std::vector<float> silent(48000*2); m.process(silent.data(),48000,48000);
        require(!m.profile().usable(),"silence acquired a gain");
    });
    test("relative gate excludes quiet audible material above the absolute gate",[] {
        LoudnessMeter m; auto loud=tone(48000,4,.1f); m.process(loud.data(),loud.size()/2,48000);
        const auto before=m.profile(); auto quiet=tone(48000,16,.00316227766f);
        m.process(quiet.data(),quiet.size()/2,48000);
        require(std::abs(m.profile().lufs-before.lufs)<.4,"relative gate included the quiet section");
    });
    test("known trim equalises quiet and loud copies without changing dynamics",[] {
        double final[2]; int i=0;
        for (float amplitude:{.04f,.5f}) { auto a=tone(48000,4,amplitude); LoudnessMeter m;
            m.process(a.data(),a.size()/2,48000); VolumeNormalizer n; n.configure(true,m.profile(true));
            n.process(a.data(),a.size()/2,48000); LoudnessMeter output; output.process(a.data(),a.size()/2,48000);
            final[i++]=output.profile().lufs; }
        require(std::abs(final[0]-final[1])<.2,"track copies were not equalised");
    });
    test("Off is an exact bypass even for unusual sample values",[] {
        VolumeNormalizer n; auto a=tone(48000,1,1.2f), expected=a; n.process(a.data(),a.size()/2,48000);
        require(a==expected,"disabled path altered PCM");
    });
    test("unforeseen peak is bounded with linked stereo gain",[] {
        VolumeNormalizer n; n.configure(true,{20,-30,.02,false});
        std::vector<float> a{4,2,-2,-4}; n.process(a.data(),2,48000);
        for(float x:a) require(std::abs(x)<=VolumeNormalizer::ceiling+1e-6,"sample peak escaped guard");
        require(std::abs(a[0]/a[1]-2)<1e-5,"independent channel limiting shifted stereo");
    });
    test("partial profiles have a conservative boost cap",[] {
        require(VolumeNormalizer::gainFor({20,-50,.001,false})<=2.0001,"partial boost exceeds 6 dB");
        require(VolumeNormalizer::gainFor({20,-50,.001,true})<=4.0001,"complete boost exceeds 12 dB");
    });
    test("non-finite input cannot poison following samples",[] {
        VolumeNormalizer n; n.configure(true); std::vector<float> a{NAN,INFINITY,.1f,-.1f};
        n.process(a.data(),2,48000); for(float x:a) require(std::isfinite(x),"non-finite output");
        require(n.profile().lufs==-100,"invalid profile manufactured");
    });
    test("a measured quiet outro retains its relative dynamics",[] {
        VolumeNormalizer n; n.configure(true,{20,-20,.1,false});
        auto a=tone(48000,2,.1f); n.process(a.data(),a.size()/2,48000); const auto loud=a;
        auto quiet=tone(48000,2,.01f); n.process(quiet.data(),quiet.size()/2,48000);
        require(std::abs(quiet[202]/loud[202]-.1)<1e-5,"quiet outro was amplified independently");
    });
    test("fallback still learns audio arriving after a long silent intro",[] {
        VolumeNormalizer n; n.configure(true); std::vector<float> silent(48000*4*2);
        n.process(silent.data(),silent.size()/2,48000);
        auto a=tone(48000,5,.1f); n.process(a.data(),a.size()/2,48000);
        require(n.profile().usable(),"silence prevented later metering");
        auto b=tone(48000,1,.1f); n.process(b.data(),b.size()/2,48000);
        require(std::abs(b[202])>std::abs(tone(48000,1,.1f)[202])*.5,"fallback froze during silence");
    });
    test("a sample-rate reset cannot claim a whole-song measurement",[] {
        VolumeNormalizer n; n.configure(true,{20,-20,.1,false});
        auto a=tone(48000,1,.1f); n.process(a.data(),a.size()/2,48000);
        auto b=tone(44100,1,.1f); n.process(b.data(),b.size()/2,44100);
        require(!n.profile(true).complete,"partial rate-change measurement marked complete");
    });
    std::cout<<total<<" loudness checks, "<<failures<<" failed\n";
    return failures?1:0;
}
