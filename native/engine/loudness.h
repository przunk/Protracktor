// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later
#pragma once

#include <algorithm>
#include <array>
#include <cmath>
#include <cstddef>
#include <cstdint>

namespace protracktor {

struct LoudnessProfile {
    double seconds = 0, lufs = -100, peak = 0;
    bool complete = false;
    bool usable() const { return std::isfinite(seconds) && seconds >= 0.4 && std::isfinite(lufs) && lufs >= -70 &&
        lufs <= 30 && std::isfinite(peak) && peak > 0; }
};

/** Stereo BS.1770 K weighting, 400ms/75%-overlap gates; bounded 0.1-LU histogram.
 * Mathematical filter reference: ITU-R BS.1770; no third-party implementation is embedded.
 * Histogram boundary quantisation is not a claim of certified EBU-meter compliance.
 */
class LoudnessMeter {
    struct Filter {
        double b0=1, b1=0, b2=0, a1=0, a2=0, z1=0, z2=0;
        double apply(double x) {
            const double y=b0*x+z1; z1=b1*x-a1*y+z2; z2=b2*x-a2*y;
            // Long silence must not leave costly denormals in the real-time filter state.
            if (std::abs(z1)<1e-30) z1=0;
            if (std::abs(z2)<1e-30) z2=0;
            return y;
        }
    };
public:
    void reset(int rate) {
        *this = LoudnessMeter(); rate_ = std::clamp(rate, 8000, 192000);
        hop_ = std::max(1, rate_/10);
        const double k=std::tan(3.141592653589793*1681.974450955533/rate_);
        const double q=0.7071752369554196, vh=std::pow(10.,3.999843853973347/20.);
        const double vb=std::pow(vh,0.4996667741545416), d=1+k/q+k*k;
        Filter shelf{(vh+vb*k/q+k*k)/d, 2*(k*k-vh)/d,
            (vh-vb*k/q+k*k)/d, 2*(k*k-1)/d, (1-k/q+k*k)/d};
        const double h=std::tan(3.141592653589793*38.13547087602444/rate_);
        const double hd=1+h/0.5003270373238773+h*h;
        Filter high{1,-2,1,2*(h*h-1)/hd,(1-h/0.5003270373238773+h*h)/hd};
        shelf_.fill(shelf); high_.fill(high);
    }
    void process(const float* audio, std::size_t frames, int rate) {
        if (rate_ != rate) reset(rate);
        for (std::size_t i=0;i<frames;++i) {
            for (int c=0;c<2;++c) {
                const double x=std::isfinite(audio[2*i+c]) ? audio[2*i+c] : 0.;
                peak_=std::max(peak_,std::abs(x));
                const double y=high_[c].apply(shelf_[c].apply(x)); energy_+=y*y;
            }
            ++frames_;
            if (++inHop_ == hop_) {
                window_[hops_%4]=energy_; energy_=0; inHop_=0; ++hops_;
                if (hops_>=4) {
                    double sum=0; for (double e:window_) sum+=e;
                    const double e=sum/(4.*hop_);
                    const double l=-0.691+10*std::log10(std::max(e,1e-30));
                    if (l>=-70) {
                        const int bin=std::clamp(static_cast<int>((l+70)*10),0,1199);
                        sums_[bin]+=e; ++counts_[bin];
                    }
                }
            }
        }
    }
    double seconds() const { return rate_ ? frames_/double(rate_) : 0.; }
    int sampleRate() const { return rate_; }
    LoudnessProfile profile(bool complete=false) const {
        double sum=0; std::uint64_t count=0;
        for (int i=0;i<1200;++i) { sum+=sums_[i]; count+=counts_[i]; }
        if (!count) return {rate_ ? frames_/double(rate_) : 0,-100,peak_,complete};
        const double relative=-0.691+10*std::log10(sum/count)-10;
        sum=0; count=0;
        for (int i=0;i<1200;++i) if (-70+(i+0.5)/10.>=relative) {
            sum+=sums_[i]; count+=counts_[i];
        }
        return {frames_/double(rate_), count ? -0.691+10*std::log10(sum/count) : -100,peak_,complete};
    }
private:
    int rate_=0, hop_=1, inHop_=0;
    std::uint64_t frames_=0, hops_=0;
    double energy_=0, peak_=0;
    std::array<Filter,2> shelf_{}, high_{};
    std::array<double,4> window_{};
    std::array<double,1200> sums_{};
    std::array<std::uint64_t,1200> counts_{};
};

/** Pure playback policy, separate from system volume and transient focus ducking. */
class VolumeNormalizer {
public:
    static constexpr float ceiling = 0.794328235f; // -2 dBFS sample-peak headroom, not true-peak certification.
    static float gainFor(const LoudnessProfile& p) {
        if (!p.usable()) return 0.5f; // Unknown/silence never starts at an inherited boosted gain.
        const double maxBoost=p.complete ? 12. : 6.;
        return static_cast<float>(std::min(std::pow(10.,std::clamp(-23.-p.lufs,-36.,maxBoost)/20.),
            double(ceiling)/p.peak));
    }
    void configure(bool enabled, const LoudnessProfile& p={}) {
        configureGain(enabled,gainFor(p),p.usable());
    }
    void configureGain(bool enabled,float gain,bool prepared) {
        enabled_=enabled; target_=enabled ? gain : 1.f;
        if (!started_) gain_=target_;
        prepared_=prepared;
    }
    void reset() {
        resetMeasurement(); started_=false; gain_=target_=0.5f; continuous_=true;
    }
    /** A live mix change invalidates statistics, not the gain currently reaching the listener. */
    void resetMeasurement() {
        continuous_=continuous_ && !hasMeasurement();
        meter_=LoudnessMeter(); prepared_=false; nextProbe_=3;
    }
    bool hasMeasurement() const { return meter_.seconds()>0; }
    void process(float* audio, std::size_t frames, int rate) {
        if (!frames || (!enabled_ && (!started_ || gain_==1))) return;
        if (enabled_) {
            if (meter_.seconds()>0 && meter_.sampleRate()!=rate) continuous_=false;
            meter_.process(audio,frames,rate);
        }
        if (enabled_ && !prepared_ && meter_.seconds()>=nextProbe_) {
            const auto p=meter_.profile(); nextProbe_=meter_.seconds()+1;
            if (p.usable()) { target_=gainFor(p); prepared_=true; }
        }
        float peak=0;
        for (std::size_t i=0;i<frames*2;++i) if (std::isfinite(audio[i])) peak=std::max(peak,std::abs(audio[i]));
        // An unforeseen louder passage may reduce the fixed trim, never amplify the outro back up.
        if (enabled_ && peak>0) target_=std::min(target_,ceiling/peak);
        const float step=1.f/(std::max(rate,1)*0.1f);
        for (std::size_t i=0;i<frames;++i) {
            gain_+=std::clamp(target_-gain_,-step,step);
            // Stereo-linked emergency guard, before focus ducking. Normally the measured peak
            // already limits trim; this catches a later transient outside the analysed prefix.
            const float left=std::isfinite(audio[2*i])?audio[2*i]:0;
            const float right=std::isfinite(audio[2*i+1])?audio[2*i+1]:0;
            const float largest=std::max(std::abs(left),std::abs(right));
            const float safe=enabled_ && largest>0?std::min(gain_,ceiling/largest):gain_;
            audio[2*i]=left*safe; audio[2*i+1]=right*safe;
        }
        started_=true;
    }
    LoudnessProfile profile(bool complete=false) const { return meter_.profile(complete && continuous_); }
private:
    bool enabled_=false, prepared_=false, started_=false;
    bool continuous_=true;
    float gain_=1, target_=1;
    double nextProbe_=3;
    LoudnessMeter meter_;
};
} // namespace protracktor
