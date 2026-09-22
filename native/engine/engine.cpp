// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later

// The decoders, and the dispatch that picks one. **No Android in this file** -- the audio callback
// and the JNI surface are next door in `player_oboe.cpp`, and the interface between them is
// `engine.h`.
//
// Seven backends behind one `Backend`, each vendored and each with its own reasons, which are in
// `docs/PLAN_FORMATS.md` and in the comments below. The order `openBackend` tries them in is the
// only part that is subtle, and it is documented where it happens.

#include "engine.h"
#include "log.h"

#include <libopenmpt/libopenmpt.hpp>

extern "C" {
#include <sc68/sc68.h>
#include <sc68/file68_rsc.h>
#include <asap.h>
#include <gme.h>
/* HivelyTracker. `types.h` is a generic name and its typedefs -- TEXT, BOOL, CONST, TRUE, FALSE --
 * are unqualified Amiga ones, so it goes last in this block, after the four headers that would
 * otherwise have to live with them. */
#include <types.h>
#include <replay.h>
/* minimp3. Declarations only -- the implementation is compiled once, as C, in
 * `native/backends/minimp3/minimp3.c`. `MINIMP3_FLOAT_OUTPUT` arrives from that target as a PUBLIC
 * definition rather than being written here, because it decides what `mp3d_sample_t` is and a
 * define that reaches one translation unit and not the other links cleanly and then reads
 * nonsense. */
#include <minimp3_ex.h>
/* `hvl_play_irq` is the sequencer half of `hvl_DecodeFrame` and upstream does not declare it in
 * `replay.h`, though it exports it. `HivelyBackend` uses it to measure a tune's length without
 * mixing a note -- 1ms instead of 30ms, and the same answer. Declared here rather than patched into
 * the vendored header because the vendored tree is fetched, not committed. */
void hvl_play_irq(struct hvl_tune *ht);
}

/*
 * Which decoders this build carries, decided at build time.
 *
 * **Only ZXTune is optional, and only because it is the one that will not compile everywhere.**
 * It fails under Emscripten's libc++ on the first file: `lexic_analysis.cpp` initialises a
 * `const auto*` from a `std::string::const_iterator`, which the NDK's libc++ hands over as a raw
 * pointer and Emscripten's does not. Patching it means forking a library `docs/ARCHITECTURE.md` §3
 * says we deliberately do not fork, so the web build goes without and says so -- 3,639 Modland
 * files of 516,107 (`docs/PLAN_WEB.md` §13 S2).
 *
 * Defaulted on, so the Android build is byte-identical and needs no flag.
 */
#ifndef PROTRACKTOR_WITH_ZXTUNE
#define PROTRACKTOR_WITH_ZXTUNE 1
#endif

/*
 * UADE, and the reason it is the second optional one.
 *
 * Not a compiler this time but a **process model**: libuade does not decode anything, it forks and
 * execs `uadecore` and reads rendered audio back over a socketpair. `fork` and `exec` do not exist
 * in WebAssembly, so the browser cannot have this backend at all -- which is the whole of why the
 * page will say out loud that these formats are the phone's (`docs/PLAN_FORMATS.md` §4, A44).
 *
 * Defaulted **off**, the opposite of ZXTune: the Gradle build asks for it and nothing else does.
 */
#ifndef PROTRACKTOR_WITH_UADE
#define PROTRACKTOR_WITH_UADE 0
#endif

#if PROTRACKTOR_WITH_UADE
extern "C" {
#include <uade/uade.h>
}
#include <atomic>
#include <csignal>
#include <dirent.h>
#include <fcntl.h>
#include <thread>
#include <strings.h>
#include <fstream>
#include <sys/stat.h>
#include <unistd.h>
#endif

#if PROTRACKTOR_WITH_ZXTUNE
// ZXTune. C++ with its own namespaces, so outside the `extern "C"` block above.
//
// `include/types.h` first, and **the path is not decoration**. It defines `uint_t` and `int_t`,
// which every other ZXTune header uses and none of them includes -- their own sources get it from a
// compiler-forced include, so the omission is invisible inside their build and immediate outside
// it. And a plain `<types.h>` finds *HivelyTracker's*, because that library ships a file of the
// same name and its include directory is on the path too. Two vendored libraries with a generically
// named header is not a problem until it is: the error arrives sixty lines later, in a third
// library's header, saying a type does not exist.
#include <include/types.h>

#include <binary/container_factories.h>
#include <formats/chiptune/aym/ascsoundmaster.h>
#include <formats/chiptune/aym/protracker2.h>
#include <formats/chiptune/aym/protracker3.h>
#include <formats/chiptune/aym/soundtracker.h>
#include <formats/chiptune/aym/soundtrackerpro.h>
#include <formats/chiptune/aym/sqtracker.h>
#include <formats/chiptune/aym/ym.h>
#include <module/holder.h>
#include <module/information.h>
#include <module/players/aym/ascsoundmaster.h>
#include <module/players/aym/fasttracker.h>
#include <module/players/aym/globaltracker.h>
#include <module/players/aym/prosoundmaker.h>
#include <module/players/aym/aym_base.h>
#include <module/players/aym/protracker1.h>
#include <module/players/aym/protracker2.h>
#include <module/players/aym/protracker3.h>
#include <module/players/aym/soundtracker.h>
#include <module/players/aym/soundtrackerpro.h>
#include <module/players/aym/sqtracker.h>
#include <module/players/aym/ymvtx.h>
#include <module/renderer.h>
#include <parameters/container.h>
#include <sound/chunk.h>
#endif  // PROTRACKTOR_WITH_ZXTUNE

#include <sidplayfp/sidplayfp.h>
#include <sidplayfp/SidTune.h>
#include <sidplayfp/SidTuneInfo.h>
#include <sidplayfp/SidConfig.h>
#include <sidlite.h>

#include <atomic>
#include <mutex>
#ifndef __EMSCRIPTEN__
#include <chrono>
#include <thread>
#endif
#include <cstdint>
#include <cstring>
#include <memory>
#include <stdexcept>
#include <sstream>
#include <algorithm>
#include <cstdlib>
#include <string>
#include <vector>

namespace {

using protracktor::Backend;

/**
 * Instrument or sample names as one line of the describe block (`docs/PLAN_INSTRUMENT_NAMES.md`).
 *
 * **One line, because `message` must stay the only value with line breaks, and last**
 * (`docs/STATUS.md` C40). So the names are joined by the unit separator, and a tab, a line break or
 * a unit separator inside a name becomes a space. Empty names inside the list are kept -- the scene
 * built text and pictures out of them -- and trailing empty ones are dropped. Empty when every name
 * is, so the caller can leave the line out.
 */
std::string joinNames(const std::vector<std::string> &names) {
    const char separator = static_cast<char>(0x1f);
    std::vector<std::string> clean;
    clean.reserve(names.size());
    for (std::string name : names) {
        for (char &c : name) {
            if (c == '\t' || c == '\r' || c == '\n' || c == separator) c = ' ';
        }
        clean.push_back(std::move(name));
    }
    const auto blank = [](const std::string &text) {
        return text.find_first_not_of(' ') == std::string::npos;
    };
    while (!clean.empty() && blank(clean.back())) clean.pop_back();
    std::string out;
    for (size_t i = 0; i < clean.size(); ++i) {
        if (i) out += separator;
        out += clean[i];
    }
    return out;
}

/**
 * Seeking a backend that cannot jump: **run the machine there, silently.**
 *
 * libsidplayfp and sc68 emulate a computer running a program, and a program has no "position" to
 * set -- the only way to minute two is through minute one. So a seek forward renders and discards
 * until the backend's own clock reaches [target]; a seek backward goes back to the start of the
 * tune playing and does the same. Measured 2026-09-22 in the browser's engine: a SID runs about
 * 41 times faster than it plays, an SNDH about 265 times, so a minute costs about 1.5 s and 0.2 s.
 *
 * **The host decides where the waiting happens.** On the phone `Player::seek` holds the decoder's
 * lock on a control thread while the audio callback plays silence; nothing with a deadline waits.
 * Stops early if the tune ends before the target: there is nothing past the end to reach.
 */
void seekByRendering(Backend &backend, double target, int sampleRate) {
    // **A bound, because the cost is real time.** The players only ask for a place inside a length
    // they know, but a wrong number from anywhere -- 99999 s, say -- would otherwise hold the
    // decoder for most of an hour. Twenty minutes of music is about thirty seconds of a SID.
    constexpr double kFurthest = 20.0 * 60.0;
    if (target > kFurthest) target = kFurthest;
    if (target < backend.positionSeconds()) backend.rewind();
    constexpr std::size_t kChunk = 4096;
    static thread_local std::vector<float> discard(kChunk * 2);
    while (backend.positionSeconds() < target) {
        // A newer seek has been asked for: stop here, and let it start from wherever this got to.
        if (backend.seekAbandoned && backend.seekAbandoned()) return;
        const double left = (target - backend.positionSeconds()) * sampleRate;
        const std::size_t want = left < kChunk ? static_cast<std::size_t>(left) + 1 : kChunk;
        if (backend.render(sampleRate, want, discard.data()) == 0) break;
    }
}

class OpenmptBackend : public Backend {
public:
    explicit OpenmptBackend(const std::vector<char> &bytes)
        : module_(std::make_unique<openmpt::module>(bytes.data(), bytes.size())) {}

    std::size_t render(int sampleRate, std::size_t frames, float *out) override {
        return module_->read_interleaved_stereo(sampleRate, frames, out);
    }

    int subsongCount() const override { return static_cast<int>(module_->get_num_subsongs()); }

    // **Answered rather than defaulted**, which is `docs/STATUS.md` C30. `Backend::currentSubsong`
    // returns 0 unless a backend says otherwise, and only two of eight did -- so the web player,
    // which trusts this to know where it is, thought every file was on its first tune for ever and
    // `next` played the second one again on every press.
    int currentSubsong() const override { return static_cast<int>(module_->get_selected_subsong()); }

    bool selectSubsong(int index) override {
        if (index < 0 || index >= subsongCount()) return false;
        // Throws for an out-of-range subsong, which the check above prevents -- but a corrupted
        // module can still surprise it, and an exception crossing JNI would take the app down.
        try {
            module_->select_subsong(index);
            return true;
        } catch (const std::exception &e) {
            LOGE("libopenmpt refused subsong %d: %s", index, e.what());
            return false;
        }
    }

    bool canSeek() const override { return true; }
    void seek(double seconds) override { module_->set_position_seconds(seconds); }
    void rewind() override { module_->set_position_seconds(0.0); }
    double positionSeconds() const override { return module_->get_position_seconds(); }
    double durationSeconds() const override { return module_->get_duration_seconds(); }

    std::string describe() const override {
        std::ostringstream o;
        o << "title\t" << module_->get_metadata("title") << '\n'
          << "format\t" << module_->get_metadata("type_long") << '\n'
          << "tracker\t" << module_->get_metadata("tracker") << '\n'
          << "artist\t" << module_->get_metadata("artist") << '\n'
          // Present in IT and MPTM and rare elsewhere, and it was never sent across at all --
          // libopenmpt knew the date of every module that carried one and nobody asked.
          << "date\t" << module_->get_metadata("date") << '\n'
          << "channels\t" << module_->get_num_channels() << '\n'
          << "patterns\t" << module_->get_num_patterns() << '\n'
          << "instruments\t" << module_->get_num_instruments() << '\n'
          << "samples\t" << module_->get_num_samples() << '\n'
          << "subsongs\t" << module_->get_num_subsongs() << '\n'
          << "seekable\t1" << '\n';
        // Where the scene wrote when the format had nowhere else: a MOD's 31 sample names are its
        // only text. Before `message`, which must stay last. `message_raw` rather than `message`,
        // which would fall back to these very names and mix the two.
        const std::string samples = joinNames(module_->get_sample_names());
        const std::string instruments = joinNames(module_->get_instrument_names());
        if (!samples.empty()) o << "sample_names\t" << samples << '\n';
        if (!instruments.empty()) o << "instrument_names\t" << instruments << '\n';
        o << "message\t" << module_->get_metadata("message_raw");
        return o.str();
    }

private:
    std::unique_ptr<openmpt::module> module_;
};

/**
 * Atari ST, through a 68000 emulator.
 *
 * An SNDH file is machine code, not note data: the music is a program that drives the YM2149. That
 * is why this backend is a whole emulated computer and why it cannot seek -- the only way to reach
 * a position is to run the machine there.
 */
// sc68 declares its allocator as taking unsigned int, which is not malloc's signature on a 64-bit
// target. Adapters rather than a cast: a cast here would compile and then pass a truncated size.
void *sc68Alloc(unsigned int bytes) { return std::malloc(bytes); }
void sc68Free(void *pointer) { std::free(pointer); }

class Sc68Backend : public Backend {
public:
    /**
     * Where sc68's replay binaries were unpacked. Set once from Kotlin before anything is opened.
     *
     * sc68 does not carry these routines inside the tunes: SNDH and `.sc68` both reference small
     * 68000 binaries that live on disk. Without them a file loads and then plays silence, which is
     * indistinguishable from a broken decoder and cost a day to diagnose on 2.2.1.
     */
    static void setSharedDataPath(const std::string &path) {
        std::lock_guard<std::mutex> held(sharedDataMutex());
        sharedDataPath() = path;
    }

    /** A copy, taken under the lock. Callers must not hold a reference into it. */
    static std::string sharedDataPathCopy() {
        std::lock_guard<std::mutex> held(sharedDataMutex());
        return sharedDataPath();
    }

    /**
     * A cheap pre-filter, not a verdict.
     *
     * `api68_verify_mem` returned -1 for an ICE-packed SNDH that then loads and plays perfectly,
     * so gating on it rejected the format wholesale. 3.x has no equivalent and loses nothing: the
     * magic checks do the work, and the real answer is whether the load succeeds.
     */
    static bool worthTrying(const std::vector<char> &bytes) {
        if (bytes.size() < 16) return false;
        if (!std::memcmp(bytes.data(), "ICE!", 4)) return true;   // packed; sc68 unpacks it itself
        if (!std::memcmp(bytes.data(), "SC68", 4)) return true;

        // SNDH files carry the tag a few bytes in, after a branch instruction.
        const std::size_t window = std::min<std::size_t>(bytes.size() - 4, 256);
        for (std::size_t i = 0; i < window; ++i) {
            if (!std::memcmp(bytes.data() + i, "SNDH", 4)) return true;
        }
        return false;
    }

    explicit Sc68Backend(const std::vector<char> &bytes) {
        ensureLibraryReady();

        sc68_create_t create;
        std::memset(&create, 0, sizeof(create));
        create.sampling_rate = kSampleRate;

        sc68_ = sc68_create(&create);
        if (!sc68_) throw std::runtime_error("the Atari ST decoder (sc68) would not start");

        if (sc68_load_mem(sc68_, bytes.data(), static_cast<int>(bytes.size())) < 0) {
            sc68_destroy(sc68_);
            sc68_ = nullptr;
            throw std::runtime_error("the Atari ST decoder (sc68) could not load it");
        }
        if (sc68_play(sc68_, current_.load(std::memory_order_relaxed), SC68_DEF_LOOP) < 0) {
            sc68_destroy(sc68_);
            sc68_ = nullptr;
            throw std::runtime_error("the Atari ST decoder (sc68) loaded it but would not play it");
        }
        sc68_music_info(sc68_, &info_, SC68_CUR_TRACK, nullptr);
    }

    ~Sc68Backend() override {
        if (sc68_) {
            sc68_stop(sc68_);
            sc68_destroy(sc68_);
        }
    }

    /**
     * Fills the buffer, asking sc68 as many times as that takes.
     *
     * **A single call is not enough, and getting this wrong ends every track.** `sc68_process`
     * returns `SC68_IDLE|SC68_CHANGE` and **zero frames** on its first call for a freshly played
     * tune -- the machine has not been run yet -- and a short render means end-of-tune to the
     * player: `onAudioReady` silences the rest of the buffer and stops the stream. Returning that
     * first zero stopped every SNDH and `.sc68` file on the first audio callback
     * (`docs/review.md` R1).
     *
     * Measured: three of three real files give `code=3 frames=0` on pass 0 and full buffers after.
     */
    std::size_t render(int, std::size_t frames, float *out) override {
        if (ended_) return 0;

        // sc68 produces interleaved 16-bit stereo; Oboe is running in float. Converting here keeps
        // the whole conversion on the audio thread and out of the rest of the engine.
        if (scratch_.size() < frames * 2) scratch_.resize(frames * 2);

        std::size_t produced = 0;
        int idlePasses = 0;

        while (produced < frames) {
            int count = static_cast<int>(frames - produced);
            const int code = sc68_process(sc68_, scratch_.data(), &count);

            // `SC68_ERROR` is ~0 -- every bit set -- so testing it with `&` is true of any non-zero
            // status, including the ordinary SC68_IDLE|SC68_CHANGE of the first pass. It is a
            // failure only when it IS the value.
            if (code == SC68_ERROR) {
                ended_ = true;
                break;
            }

            if (count > 0) {
                const std::size_t got = static_cast<std::size_t>(count);
                // `produced * 2` because both are interleaved stereo: frame n is samples 2n and
                // 2n+1 in each.
                for (std::size_t i = 0; i < got * 2; ++i) {
                    out[produced * 2 + i] = static_cast<float>(scratch_[i]) / 32768.0f;
                }
                produced += got;
                idlePasses = 0;
            } else if (++idlePasses > kMaxIdlePasses) {
                // A bound, because this runs on the audio thread. A decoder that never produces
                // anything must cost one silent buffer, not a locked-up device.
                break;
            }

            if (code & SC68_END) {
                ended_ = true;
                break;
            }
        }

        rendered_ += produced;
        return produced;
    }

    // No seek of its own: a 68000 running a program has no position to set. Run there instead.
    bool canSeek() const override { return true; }
    void seek(double seconds) override { seekByRendering(*this, seconds, kSampleRate); }

    /**
     * Back to the start of **what is playing**, which used to mean track 1 whatever was playing.
     *
     * `docs/STATUS.md` C13: repeat-one goes through here, so on a multi-tune SNDH sitting on
     * subsong five it repeated subsong one. It did repeat -- just not the thing the user was
     * listening to. ASAP replays `song_`, game-music-emu replays `track_`, libsidplayfp reloads
     * with its selected song; sc68 was the one that forgot.
     */
    void rewind() override {
        sc68_stop(sc68_);
        sc68_play(sc68_, current_.load(std::memory_order_acquire), SC68_DEF_LOOP);
        rendered_ = 0;
        ended_ = false;
    }

    double positionSeconds() const override {
        return static_cast<double>(rendered_) / static_cast<double>(kSampleRate);
    }

    /**
     * How long the tune is, which 2.2.1 could not answer for SNDH at all.
     *
     * 3.x ships a database of known SNDH files with their durations, so most of these now have a
     * real length rather than none.
     */
    double durationSeconds() const override {
        const std::lock_guard<std::mutex> held(infoGuard_);
        return static_cast<double>(info_.trk.time_ms) / 1000.0;
    }

    std::string describe() const override {
        const std::lock_guard<std::mutex> held(infoGuard_);
        const auto text = [](const char *value) { return value ? value : ""; };
        std::ostringstream o;
        o << "title\t" << text(info_.title) << '\n'
          << "format\tAtari ST (sc68)" << '\n'
          << "tracker\t" << text(info_.replay) << '\n'
          << "artist\t" << text(info_.artist) << '\n'
          << "hardware\t" << text(info_.trk.hw) << '\n'
          << "year\t" << text(info_.year) << '\n'
          << "subsongs\t" << info_.tracks << '\n'
          << "seekable\t1";
        return o.str();
    }

    int preferredSampleRate() const override { return kSampleRate; }

    int subsongCount() const override {
        const std::lock_guard<std::mutex> held(infoGuard_);
        return info_.tracks > 0 ? info_.tracks : 1;
    }

    /** sc68 counts from one and the interface from zero, so this is the same conversion back. */
    int currentSubsong() const override {
        return std::max(0, current_.load(std::memory_order_acquire) - 1);
    }

    /** sc68 counts its tracks from one; the interface counts from zero. Converted here, once. */
    bool selectSubsong(int index) override {
        if (index < 0 || index >= subsongCount()) return false;
        if (sc68_play(sc68_, index + 1, SC68_DEF_LOOP) < 0) return false;
        // Remembered so `rewind` can come back to it. Everything that replays this tune without
        // choosing a subsong -- repeat-one, and replaying a finished track -- goes through there.
        current_.store(index + 1, std::memory_order_release);
        {
            const std::lock_guard<std::mutex> held(infoGuard_);
            sc68_music_info(sc68_, &info_, SC68_CUR_TRACK, nullptr);
        }
        rendered_ = 0;
        ended_ = false;
        return true;
    }

private:
    /**
     * `sc68_init` is process-wide and must happen exactly once, before any player is created.
     *
     * The replay path is applied here too: 3.x dropped `shared_path` from its init struct and
     * `rsc68_set_share` replaces it, which has to be called after the library is up.
     */
    static void ensureLibraryReady() {
        static const bool ready = [] {
            sc68_init_t init;
            std::memset(&init, 0, sizeof(init));
            // Do not read or write a config file. There is nowhere sensible for one on Android, and
            // a player that remembers settings nobody set is a player that behaves differently on
            // its second run for no visible reason.
            init.flags.no_load_config = 1;
            init.flags.no_save_config = 1;
            if (sc68_init(&init) < 0) return false;
            const std::string share = sharedDataPathCopy();
            if (!share.empty()) rsc68_set_share(share.c_str());
            return true;
        }();
        if (!ready) throw std::runtime_error("the Atari ST decoder (sc68) would not initialise");
    }

    static constexpr int kSampleRate = 44100;

    /**
     * How many empty passes to tolerate before giving up on a buffer.
     *
     * One is normal -- the first call after `sc68_play` always produces nothing. More than a
     * handful means the decoder is not going to produce anything, and the audio thread is the
     * wrong place to find out slowly.
     */
    static constexpr int kMaxIdlePasses = 8;

    /**
     * Where the replay binaries are, and the lock that makes reading it safe.
     *
     * Written once from JNI during start-up and read when a file is opened. Nothing ordered the two
     * until now, and a library scan can reach an open while start-up is still running -- a torn
     * read gives an empty path, and sc68 with no replay path loads files and plays silence, which
     * is indistinguishable from a broken decoder and cost a day to diagnose once already
     * (`docs/review.md` R5).
     */
    static std::string &sharedDataPath() {
        static std::string path;
        return path;
    }

    static std::mutex &sharedDataMutex() {
        static std::mutex guard;
        return guard;
    }

    sc68_t *sc68_ = nullptr;

    /**
     * What sc68 says about the tune, and the lock that makes reading it legal.
     *
     * **`selectSubsong` runs on the audio thread.** A pending subsong is applied inside the audio
     * callback (`Player::render`) precisely so a switch cannot land under a read in progress -- and
     * that is also what puts this struct on two threads, because `selectSubsong` refills it while
     * `describe()`, `durationSeconds()` and `subsongCount()` read it whenever the UI asks what is
     * playing. Filling a struct on one thread and reading it on another is a race whatever the
     * hardware does about it (`docs/review.md` R6, and round 6's R1 and R2).
     *
     * **A lock on the audio thread is deliberate here and is not the usual mistake.** It is taken
     * once per *subsong change* -- never per buffer -- and the section it holds is one library call
     * that fills a struct already in memory. The alternative was publishing half a dozen scalars
     * and a handful of strings as atomics, which is more machinery guarding the same thing less
     * clearly.
     */
    mutable std::mutex infoGuard_;
    sc68_music_info_t info_{};

    /**
     * Which track is playing, in sc68's own one-based numbering.
     *
     * Atomic for the reason its neighbours below are: written by the audio thread in
     * `selectSubsong`, read by the control thread in `rewind`. `restart()` stops the stream before
     * rewinding, so in practice the write is already visible -- which is exactly the argument round
     * 5 declined to rely on.
     *
     * One rather than zero because that is what `sc68_play` is given before any subsong is chosen.
     */
    std::atomic<int> current_{1};
    std::vector<short> scratch_;
    // Written by render() on the audio thread and read from elsewhere by positionSeconds(), which
    // is a race by the language's rules however benign it looks on ARM (`docs/review.md` R6). The
    // default sequentially-consistent ordering is more than a counter needs and costs one fence per
    // buffer, which is nothing next to emulating a 68000 -- so it is left alone rather than tuned.
    std::atomic<std::size_t> rendered_{0};
    std::atomic<bool> ended_{false};
};


/**
 * Atari 8-bit, through a 6502 and a POKEY.
 *
 * Twelve of twelve random SAP files from Modland played on the first try, which is not a sentence
 * that could be written about the Atari ST backend. ASAP also seeks, which few emulator backends do.
 *
 * It needs the **filename**: several of the fourteen formats it handles are told apart by extension
 * rather than by a header, and one of them (`.fc`) collides with an Amiga format libopenmpt claims.
 * That is why the whole engine now carries a name alongside the bytes.
 */
class AsapBackend : public Backend {
public:
    static bool claimsName(const std::string &name) {
        return ASAPInfo_IsOurFile(name.c_str());
    }

    AsapBackend(const std::vector<char> &bytes, const std::string &name)
        : asap_(ASAP_New()) {
        if (!asap_) throw std::runtime_error("the Atari 8-bit decoder (ASAP) would not initialise");

        ASAP_SetSampleRate(asap_, kSampleRate);
        if (!ASAP_Load(asap_, name.c_str(),
                       reinterpret_cast<const uint8_t *>(bytes.data()),
                       static_cast<int>(bytes.size()))) {
            ASAP_Delete(asap_);
            asap_ = nullptr;
            throw std::runtime_error("the Atari 8-bit decoder (ASAP) could not load it");
        }

        info_ = ASAP_GetInfo(asap_);
        song_ = ASAPInfo_GetDefaultSong(info_);
        durationMs_ = ASAPInfo_GetDuration(info_, song_);

        if (!ASAP_PlaySong(asap_, song_, durationMs_)) {
            ASAP_Delete(asap_);
            asap_ = nullptr;
            throw std::runtime_error("the Atari 8-bit decoder (ASAP) loaded it but would not start it");
        }
    }

    ~AsapBackend() override {
        if (asap_) ASAP_Delete(asap_);
    }

    std::size_t render(int, std::size_t frames, float *out) override {
        if (ended_) return 0;

        const int channels = ASAPInfo_GetChannels(info_);
        const std::size_t wanted = frames * static_cast<std::size_t>(channels);
        if (scratch_.size() < wanted) scratch_.resize(wanted);

        const int bytes = ASAP_Generate(
            asap_, reinterpret_cast<uint8_t *>(scratch_.data()),
            static_cast<int>(wanted * sizeof(short)), ASAPSampleFormat_S16_L_E);

        if (bytes <= 0) {
            ended_ = true;
            return 0;
        }

        const std::size_t produced = static_cast<std::size_t>(bytes) / sizeof(short) / channels;
        for (std::size_t i = 0; i < produced; ++i) {
            // Mono is duplicated rather than left in one ear: half these tunes are single-POKEY,
            // and a phone whose second channel is a screen vibrator would play them inaudibly.
            const float left = static_cast<float>(scratch_[i * channels]) / 32768.0f;
            const float right = channels > 1
                ? static_cast<float>(scratch_[i * channels + 1]) / 32768.0f
                : left;
            out[i * 2] = left;
            out[i * 2 + 1] = right;
        }
        if (produced < frames) ended_ = true;
        return produced;
    }

    bool canSeek() const override { return true; }

    void seek(double seconds) override {
        ASAP_Seek(asap_, static_cast<int>(seconds * 1000.0));
        ended_ = false;
    }

    void rewind() override {
        ASAP_PlaySong(asap_, song_, durationMs_);
        ended_ = false;
    }

    double positionSeconds() const override { return ASAP_GetPosition(asap_) / 1000.0; }

    // A negative duration means the file does not say, which is common. Zero reads as "unknown" to
    // the rest of the app and disables the scrubber rather than offering a meaningless one.
    double durationSeconds() const override {
        return durationMs_ > 0 ? durationMs_ / 1000.0 : 0.0;
    }

    std::string describe() const override {
        std::ostringstream o;
        o << "title\t" << (ASAPInfo_GetTitle(info_) ? ASAPInfo_GetTitle(info_) : "") << '\n'
          << "format\tAtari 8-bit (ASAP)" << '\n'
          << "artist\t" << (ASAPInfo_GetAuthor(info_) ? ASAPInfo_GetAuthor(info_) : "") << '\n'
          << "date\t" << (ASAPInfo_GetDate(info_) ? ASAPInfo_GetDate(info_) : "") << '\n'
          << "channels\t" << ASAPInfo_GetChannels(info_) << '\n'
          << "subsongs\t" << ASAPInfo_GetSongs(info_) << '\n'
          << "seekable\t1";
        return o.str();
    }

    int preferredSampleRate() const override { return kSampleRate; }

    int subsongCount() const override { return ASAPInfo_GetSongs(info_); }

    // Not always zero even before anything is chosen: a SAP names its own default song and this
    // opens there.
    int currentSubsong() const override { return song_; }

    bool selectSubsong(int index) override {
        if (index < 0 || index >= subsongCount()) return false;
        song_ = index;
        // ASAP wants the duration of the song being started; -1 lets it play to its own end.
        durationMs_ = ASAPInfo_GetDuration(info_, song_);
        if (!ASAP_PlaySong(asap_, song_, durationMs_)) return false;
        ended_ = false;
        return true;
    }

private:
    static constexpr int kSampleRate = 44100;

    ASAP *asap_ = nullptr;
    const ASAPInfo *info_ = nullptr;
    int song_ = 0;
    int durationMs_ = -1;
    std::vector<short> scratch_;
    bool ended_ = false;
};

/**
 * The consoles, through game-music-emu: NES, SNES, Game Boy, Sega, PC Engine, ZX Spectrum, MSX.
 *
 * One library for seven machines, and the only one here that identifies files **by content** --
 * `gme_identify_header` reads the bytes rather than trusting a name. That makes it the safest
 * backend to ask early, and it is why nothing had to be added to the filename plumbing for it.
 *
 * Verified on the host before integration: NSF 4/4, SPC 4/4, GBS 3/3, VGM 3/3.
 */
class GmeBackend : public Backend {
public:
    static bool recognises(const std::vector<char> &bytes) {
        if (bytes.size() < 16) return false;
        const char *type = gme_identify_header(bytes.data());
        return type && *type;
    }

    explicit GmeBackend(const std::vector<char> &bytes) : bytes_(bytes) {
        if (const gme_err_t err = gme_open_data(bytes.data(), static_cast<long>(bytes.size()),
                                                &emu_, kSampleRate)) {
            throw std::runtime_error(std::string("the console decoder (game-music-emu) refused it: ") + err);
        }
        if (!emu_) throw std::runtime_error("the console decoder (game-music-emu) returned nothing");

        gme_track_info(emu_, &info_, track_);
        if (const gme_err_t err = gme_start_track(emu_, track_)) {
            const std::string message = std::string("the console decoder (game-music-emu) could not start it: ") + err;
            if (info_) gme_free_info(info_);
            gme_delete(emu_);
            emu_ = nullptr;
            throw std::runtime_error(message);
        }

        // The fade is applied by `openAtSomethingAudible`, at the end, after the last
        // `gme_start_track` — because that call throws it away. See `applyFade`.
        openAtSomethingAudible();
#ifdef __EMSCRIPTEN__
        // No threads in the browser's engine: measured here, before the page asks for the length.
        measureNow(track_);
#endif
    }

    ~GmeBackend() override {
        stopMeasuring_.store(true, std::memory_order_release);
#ifndef __EMSCRIPTEN__
        if (measurer_.joinable()) measurer_.join();
#endif
        if (info_) gme_free_info(info_);
        if (emu_) gme_delete(emu_);
    }

    /**
     * Starts finding where a tune that states no length really ends (the owner's variant (a),
     * 2026-09-22) -- on the phone, off every thread that has a deadline, as UADE's measurement does.
     *
     * **Only a tune that falls silent has a length to find.** Game music mostly loops; played on,
     * such a tune never goes quiet and game-music-emu fades it at its default of 2:30. So the
     * measurement plays a separate copy of the tune silently up to that default and keeps the time
     * only if the tune ended before it -- a jingle, a short theme. A tune still playing at 2:30
     * loops, and stays without a length: the app's bar then runs to where playback will stop
     * (`ends_at` in `describe`), shown as approximate.
     */
    void startedPlaying() override {
#ifndef __EMSCRIPTEN__
        wanted_.store(track_, std::memory_order_release);
        if (!measurer_.joinable() && !known()) {
            measurer_ = std::thread([this] { measureLoop(); });
        }
#endif
    }

    /** While a measurement may still give this track a length, the host keeps asking for one. */
    bool durationArrivesLater() const override {
        if (known()) return false;
        return measuredTrack_.load(std::memory_order_acquire) != track_
            || measuredSeconds_.load(std::memory_order_acquire) > 0.0;
    }

    /**
     * Start where the music is, not where the file starts.
     *
     * **HES and KSS routinely put nothing at track 0.** Measured 2026-09-04 across forty files from
     * Modland: every one of twenty HES files had music, and seven of them were silent at track 0 —
     * so a third of the format appeared broken while the music sat one track along. KSS is the same
     * shape. These are sound *banks* as much as albums; track 0 is often an empty slot or an effect.
     *
     * So when the first track renders nothing, look for one that does — entered **only** when
     * track 0 was silent, so a file that starts with music never pays for this.
     *
     * Bounded by time rather than by a track count. `aleste 2.kss` has 256 tracks, 82 of them
     * audible, and the first audible one is number 47 — any count small enough to be safe is too
     * small to find it. A wall-clock budget makes no guess about how fast the device is, and time
     * is the quantity being protected: the wait before sound.
     *
     * It does not touch `gme_track_count`, which reports a flat 256 for KSS and HES whatever the
     * file holds. That number is wrong — `aleste 2.kss` really has 82 tunes, not 256 — and finding
     * the truth means rendering every one of them, which is not something to do while somebody
     * waits. Recorded in `docs/PLAN_FORMATS.md` §2 rather than guessed at here.
     */
    void openAtSomethingAudible() {
        if (!emu_ || audible(kProbeFrames)) {
            gme_start_track(emu_, track_);
            applyFade();
            return;
        }

        const auto deadline = std::chrono::steady_clock::now() + kSearchBudget;
        const int limit = gme_track_count(emu_);
        for (int candidate = 1; candidate < limit; ++candidate) {
            if (std::chrono::steady_clock::now() > deadline) break;
            if (gme_start_track(emu_, candidate)) continue;
            if (!audible(kProbeFrames)) continue;

            track_ = candidate;
            if (info_) { gme_free_info(info_); info_ = nullptr; }
            gme_track_info(emu_, &info_, track_);
            gme_start_track(emu_, track_);
            applyFade();
            return;
        }

        // Nothing audible anywhere we looked. Back to the beginning: a silent file is still that
        // file, and starting it somewhere arbitrary would be worse than starting it where it says.
        gme_start_track(emu_, track_);
        applyFade();
    }

    /**
     * Sets the fade for whatever `info_` currently describes.
     *
     * **After every `gme_start_track`, and that is the whole point of this being a function.**
     * Without a fade the last buffer stops dead — the reason the call exists at all — and in this
     * library the fade is also what *ends* a track: "Once fade ends track_ended() returns true".
     *
     * `gme_start_track` discards it. `Music_Emu::clear_track_vars()` runs first and sets
     * `fade_start = INT_MAX / 2 + 1`, so a fade set before a start is a fade that never happens.
     * It was set once in the constructor and then thrown away by `openAtSomethingAudible`, on
     * every console file this app has ever opened, and by `rewind` on every replay
     * (`docs/STATUS.md` C27). Measured on one SPC: 128.0 s with the fade against 120.1 s without.
     *
     * Found by rendering thirty seconds through the library directly and thirty through ours and
     * comparing the bytes — which is the only reason anybody noticed, because what it sounds like
     * is a tune that ends.
     */
    void applyFade() {
        if (emu_ && info_ && info_->play_length > 0) gme_set_fade(emu_, info_->play_length);
    }

    /** Renders a moment and says whether any of it was above silence. Consumes what it renders. */
    bool audible(std::size_t frames) {
        const std::size_t samples = frames * 2;
        if (scratch_.size() < samples) scratch_.resize(samples);
        std::size_t done = 0;
        while (done < samples) {
            const std::size_t chunk = std::min<std::size_t>(2048, samples - done);
            if (gme_track_ended(emu_)) break;
            if (gme_play(emu_, static_cast<int>(chunk), scratch_.data())) break;
            for (std::size_t i = 0; i < chunk; ++i) {
                if (scratch_[i] != 0) return true;
            }
            done += chunk;
        }
        return false;
    }


    std::size_t render(int, std::size_t frames, float *out) override {
        if (!emu_ || gme_track_ended(emu_)) return 0;

        const std::size_t samples = frames * 2;  // gme counts samples, not frames
        if (scratch_.size() < samples) scratch_.resize(samples);

        if (gme_play(emu_, static_cast<int>(samples), scratch_.data())) return 0;

        for (std::size_t i = 0; i < samples; ++i) {
            out[i] = static_cast<float>(scratch_[i]) / 32768.0f;
        }
        return frames;
    }

    bool canSeek() const override { return true; }
    void seek(double seconds) override { gme_seek(emu_, static_cast<int>(seconds * 1000.0)); }
    void rewind() override { gme_start_track(emu_, track_); applyFade(); }

    int subsongCount() const override { return emu_ ? gme_track_count(emu_) : 1; }

    /** Which track is playing. Not always zero: see `openAtSomethingAudible`. */
    int currentSubsong() const override { return track_; }

    bool selectSubsong(int index) override {
        if (!emu_ || index < 0 || index >= subsongCount()) return false;
        if (const gme_err_t err = gme_start_track(emu_, index)) {
            LOGE("gme refused track %d: %s", index, err);
            return false;
        }
        track_ = index;
        // The info is per track: a GBS names each tune and gives each its own length, and a stale
        // struct would show the first one's title against the third one's audio.
        //
        // **Freed first.** `gme_track_info` allocates and its header says so -- "Must be freed
        // after use" -- and this overwrote the pointer, so every subsong change leaked a struct and
        // its strings. Small, and a 256-track HES walked through under "play all" leaks it 256
        // times. The destructor only ever freed the last one.
        if (info_) { gme_free_info(info_); info_ = nullptr; }
        gme_track_info(emu_, &info_, track_);

        // **And the fade has to move with it**, because in this library the fade is what ends the
        // track: "Once fade ends track_ended() returns true". It was set once in the constructor
        // from track 0's length, so every later tune in a GBS faded at the first tune's time --
        // early for the long ones, and for a file whose first track states no length, not at all.
        // This path had the order right when the other three did not; it is the same call now.
        applyFade();
#ifdef __EMSCRIPTEN__
        measureNow(track_);
#else
        // Asked for, not measured here: this runs on the audio thread (a pending subsong is applied
        // in the callback), and a measurement is up to a few seconds of emulation.
        wanted_.store(track_, std::memory_order_release);
#endif
        return true;
    }
    double positionSeconds() const override { return gme_tell(emu_) / 1000.0; }

    /**
     * How long the tune is, **or nothing when the file never said**.
     *
     * `play_length` is not a measurement. game-music-emu's own header spells out what it is:
     *
     *     Length if available, otherwise intro_length+loop_length*2 if available,
     *     otherwise a default of 150000 (2.5 minutes).
     *
     * So every NSF, AY, KSS and GBS that carries no length at all -- which is most of them, the
     * format has no field for it -- reported **exactly 2:30**, and the app drew a progress bar
     * promising two and a half minutes over a tune that stops when it stops. Measured on Tadpole's
     * `stars through the clouds.nsf`: reported 150.0 s, last audible sound at 29.4 s, ended by the
     * library at 30.5 s. The skip was right; the number above it was fiction.
     *
     * Asked of the fields that are honest about not knowing: `length` is "total length, if file
     * specifies it", and the two loop fields are -1 when unknown. When none of them knows, this
     * says so, and the app treats the tune as unmeasured -- the same path a SID with no HVSC entry
     * takes (`docs/BACKLOG.md` C56).
     *
     * [applyFade] deliberately still uses `play_length`: a looping tune that nothing can measure
     * should still fade out at some point rather than run until the fallback cuts it dead, and
     * 2.5 minutes is as good a point as any. What changes is only what we claim to know.
     */
    double durationSeconds() const override {
        if (!info_) return 0.0;
        if (!known()) {
            // Measured, and found to fall silent: that is a length. Otherwise still nothing.
            if (measuredTrack_.load(std::memory_order_acquire) == track_) {
                return measuredSeconds_.load(std::memory_order_acquire);
            }
            return 0.0;
        }
        return info_->play_length > 0 ? info_->play_length / 1000.0 : 0.0;
    }

    std::string describe() const override {
        const auto field = [](const char *value) { return value && *value ? value : ""; };
        std::ostringstream o;
        o << "title\t" << (info_ ? field(info_->song) : "") << '\n'
          << "format\t" << (info_ ? field(info_->system) : "") << '\n'
          << "artist\t" << (info_ ? field(info_->author) : "") << '\n'
          << "game\t" << (info_ ? field(info_->game) : "") << '\n'
          << "copyright\t" << (info_ ? field(info_->copyright) : "") << '\n'
          << "dumper\t" << (info_ ? field(info_->dumper) : "") << '\n'
          << "subsongs\t" << (emu_ ? gme_track_count(emu_) : 0) << '\n'
          // Where this file actually opened. Zero for nearly everything; not for a HES whose first
          // track is an empty slot (`openAtSomethingAudible`), and the playlist row and the subsong
          // strip both have to agree with the audio.
          << "subsong\t" << track_ << '\n'
          << "seekable\t1" << '\n';
        // **Where playback will stop, when the tune's own length is unknown**: game-music-emu fades
        // it at `play_length` -- its default of 2:30 -- over `kFadeSeconds`. Not a length, and not
        // said as one: the app draws the seek bar to it as approximate (the owner's variant (a)).
        const bool measured = measuredTrack_.load(std::memory_order_acquire) == track_
            && measuredSeconds_.load(std::memory_order_acquire) > 0.0;
        if (info_ && !known() && !measured && info_->play_length > 0) {
            o << "ends_at\t" << (info_->play_length / 1000.0 + kFadeSeconds) << '\n';
        }
        o << "message\t" << (info_ ? field(info_->comment) : "");
        return o.str();
    }

    int preferredSampleRate() const override { return kSampleRate; }

private:
    /** Whether the file itself states this track's length (see `durationSeconds`). */
    bool known() const {
        return info_ && (info_->length > 0 || info_->intro_length > 0 || info_->loop_length > 0);
    }

    /**
     * Plays [index] on a copy of its own, silently, up to game-music-emu's default length, and
     * answers the time it fell silent -- or 0 when it was still playing there, which is a loop.
     * Its own `Music_Emu`, so it touches nothing the playing one uses.
     */
    double measureNaturalEnd(int index) const {
        Music_Emu *copy = nullptr;
        if (gme_open_data(bytes_.data(), static_cast<long>(bytes_.size()), &copy, kSampleRate) || !copy) return 0.0;
        gme_info_t *about = nullptr;
        long cap = 150000;
        if (!gme_track_info(copy, &about, index) && about && about->play_length > 0) cap = about->play_length;
        if (about) gme_free_info(about);
        double found = 0.0;
        if (!gme_start_track(copy, index)) {
            std::vector<short> scratch(4096 * 2);
            while (!gme_track_ended(copy) && gme_tell(copy) < cap
                   && !stopMeasuring_.load(std::memory_order_acquire)) {
                if (gme_play(copy, static_cast<int>(scratch.size()), scratch.data())) break;
            }
            if (gme_track_ended(copy) && gme_tell(copy) < cap) found = gme_tell(copy) / 1000.0;
        }
        gme_delete(copy);
        return found;
    }

    /** Measures [index] and publishes it: the seconds first, then which track they are for. */
    void measureNow(int index) {
        if (known()) return;
        const double seconds = measureNaturalEnd(index);
        measuredSeconds_.store(seconds, std::memory_order_release);
        measuredTrack_.store(index, std::memory_order_release);
    }

#ifndef __EMSCRIPTEN__
    /** The phone's measuring thread: measures whichever track is wanted, until the tune closes. */
    void measureLoop() {
        int done = -1;
        while (!stopMeasuring_.load(std::memory_order_acquire)) {
            const int wanted = wanted_.load(std::memory_order_acquire);
            if (wanted >= 0 && wanted != done) {
                done = wanted;
                const double seconds = measureNaturalEnd(wanted);
                measuredSeconds_.store(seconds, std::memory_order_release);
                measuredTrack_.store(wanted, std::memory_order_release);
            } else {
                std::this_thread::sleep_for(std::chrono::milliseconds(40));
            }
        }
    }
#endif

    static constexpr int kSampleRate = 44100;
    /** game-music-emu's fade after `play_length`: eight seconds (`gme_set_fade`'s default). */
    static constexpr double kFadeSeconds = 8.0;
    /** A fifth of a second is plenty to tell music from an empty slot, and cheap to throw away. */
    static constexpr std::size_t kProbeFrames = kSampleRate / 5;
    /**
     * How long the search may take, measured on the clock the listener is also watching.
     *
     * 300 ms is the number because it is roughly where a delay stops reading as "loading" and
     * starts reading as "broken", and because the alternative it replaced -- a fixed twelve tracks
     * -- missed a file whose first tune is number 47. On a phone that emulates at fifty times real
     * time this reaches well past that; on a slow one it gives up early, which is the behaviour it
     * had before and no worse.
     */
    static constexpr std::chrono::milliseconds kSearchBudget{300};
    // Subsong selection is a UI feature that does not exist yet; some of these files hold hundreds.
    int track_ = 0;

    Music_Emu *emu_ = nullptr;
    gme_info_t *info_ = nullptr;
    std::vector<short> scratch_;

    // The measurement's side (`measureNaturalEnd`): the file, kept for the copy it plays; the track
    // asked for and the one measured; what was found; the thread, on the phone only.
    std::vector<char> bytes_;
    std::atomic<int> wanted_{-1};
    std::atomic<int> measuredTrack_{-1};
    std::atomic<double> measuredSeconds_{0.0};
    std::atomic<bool> stopMeasuring_{false};
#ifndef __EMSCRIPTEN__
    std::thread measurer_;
#endif
};

/**
 * MP3, through minimp3 — and it is the one format here that is not a chiptune.
 *
 * Why a chiptune player has one is `docs/BACKLOG.md` A29. It is deliberately a *local file*
 * format: no catalogue here holds an MP3, and `SupportedFormats` keeps it out of the list an
 * online index is judged against.
 *
 * **`mp3dec_ex` rather than the plain frame decoder**, and the difference is the two things a
 * player needs and a frame loop cannot give: a length for a variable-bitrate file, and an index to
 * seek with. Both come from a scan at open, which is the cost — measured before it was chosen.
 *
 * It renders at the file's own rate. An MP3 is 44,100 nearly always and 48,000 sometimes, and
 * saying which is the difference between a recording that plays and one that plays sharp — the
 * defect the web build shipped with in September, from the other direction.
 */
class Mp3Backend : public Backend {
public:
    /**
     * Whether the *name* says MP3, which for this format is the reliable signal.
     *
     * **The content test is not**, and that is `docs/STATUS.md` C32. An MP3 has no magic worth the
     * name, so `mp3dec_detect_buf` walks the file looking for something that parses as a frame —
     * and a tracker module is megabytes of sample data in which something eventually will. It
     * claimed `!!uu !! !!.it`, a file whose first four bytes are `IMPM`.
     *
     * So the name is asked first and the content only as a last resort, the same shape ASAP has
     * for its fourteen extension-told-apart formats.
     */
    static bool claimsName(const std::string &name) {
        const auto dot = name.find_last_of('.');
        if (dot == std::string::npos) return false;
        std::string extension = name.substr(dot + 1);
        for (auto &c : extension) {
            c = static_cast<char>(std::tolower(static_cast<unsigned char>(c)));
        }
        return extension == "mp3";
    }

    /**
     * minimp3's own detector, which walks for a frame header rather than trusting four bytes.
     *
     * **A guess, and it is asked last for that reason.** It is right about a file that really is an
     * MP3 under any name, and wrong about anything whose bytes happen to contain a plausible frame
     * — see `claimsName`. Every decoder that can *prove* what it is holding gets asked before this.
     */
    static bool recognises(const std::vector<char> &bytes) {
        if (bytes.size() < 16) return false;
        const auto *b = reinterpret_cast<const unsigned char *>(bytes.data());

        // **A frame has to be where an MP3 keeps one**, and that is `docs/STATUS.md` C34.
        // `mp3dec_detect_buf` searches for a frame sequence *anywhere* in its scan limit, which a
        // 27 KB file of ZX Spectrum beeper data obligingly contains -- so `plastic galaxy.bbsong`
        // came back as MP3 and played at peak 1.08, which is clipping. Requiring the frame at the
        // start turns a coincidence back into evidence.
        std::size_t at = 0;
        if (bytes.size() > 10 && b[0] == 'I' && b[1] == 'D' && b[2] == '3') {
            // ID3v2: three characters, two version bytes, flags, then a length in four bytes of
            // seven bits each -- syncsafe, so that the length itself can never look like a frame.
            const std::size_t tag = 10 + ((static_cast<std::size_t>(b[6] & 0x7f) << 21) |
                                          (static_cast<std::size_t>(b[7] & 0x7f) << 14) |
                                          (static_cast<std::size_t>(b[8] & 0x7f) << 7) |
                                          static_cast<std::size_t>(b[9] & 0x7f));
            if (tag + 4 > bytes.size()) return false;
            at = tag;
        }
        // 0xFF then three set bits: the eleven-bit sync every MPEG audio frame opens with.
        if (at + 4 > bytes.size() || b[at] != 0xff || (b[at + 1] & 0xe0) != 0xe0) return false;

        // Strict enough to refuse a few real MP3s that keep junk before their first frame, and that
        // is the right trade: a file called `.mp3` never reaches here at all -- `claimsName` takes
        // it much earlier -- so this only judges files that arrived under some other name.
        return mp3dec_detect_buf(b, bytes.size()) == 0;
    }

    explicit Mp3Backend(const std::vector<char> &bytes) : bytes_(bytes) {
        // **The bytes are kept, and they have to be.** `mp3dec_ex_open_buf` does not copy: it holds
        // the pointer and reads from it for the life of the decoder, including on every seek. The
        // caller's vector is a parameter and goes away.
        std::memset(&dec_, 0, sizeof(dec_));
        if (mp3dec_ex_open_buf(&dec_, reinterpret_cast<const uint8_t *>(bytes_.data()),
                               bytes_.size(), MP3D_SEEK_TO_SAMPLE) != 0) {
            throw std::runtime_error("the MP3 decoder (minimp3) could not open it");
        }
        opened_ = true;
        if (dec_.info.hz <= 0 || dec_.info.channels <= 0) {
            mp3dec_ex_close(&dec_);
            opened_ = false;
            throw std::runtime_error("the MP3 decoder (minimp3) found no audio in it");
        }
    }

    ~Mp3Backend() override {
        if (opened_) mp3dec_ex_close(&dec_);
    }

    std::size_t render(int, std::size_t frames, float *out) override {
        const int channels = dec_.info.channels;
        if (channels == 2) {
            // Already interleaved stereo floats, which is what the caller wants. No copy.
            const std::size_t got = mp3dec_ex_read(&dec_, out, frames * 2);
            return got / 2;
        }
        // Mono, duplicated into both ears for the same reason ASAP's is: half a signal in one ear
        // is a choice nobody made.
        if (scratch_.size() < frames) scratch_.resize(frames);
        const std::size_t got = mp3dec_ex_read(&dec_, scratch_.data(), frames);
        for (std::size_t i = 0; i < got; ++i) {
            out[i * 2] = scratch_[i];
            out[i * 2 + 1] = scratch_[i];
        }
        return got;
    }

    bool canSeek() const override { return dec_.samples > 0; }

    void seek(double seconds) override {
        const auto sample = static_cast<std::uint64_t>(
            std::max(0.0, seconds) * dec_.info.hz) * static_cast<std::uint64_t>(dec_.info.channels);
        mp3dec_ex_seek(&dec_, sample);
    }

    void rewind() override { mp3dec_ex_seek(&dec_, 0); }

    // `cur_sample` counts channels in, like `samples` does. Both are divided by the same thing.
    double positionSeconds() const override {
        return static_cast<double>(dec_.cur_sample) /
               (static_cast<double>(dec_.info.hz) * dec_.info.channels);
    }

    double durationSeconds() const override {
        return static_cast<double>(dec_.samples) /
               (static_cast<double>(dec_.info.hz) * dec_.info.channels);
    }

    std::string describe() const override {
        std::ostringstream o;
        // **No title, no artist, and that is not an omission.** minimp3 decodes; it does not read
        // ID3, and inventing a tag reader for one format would be a second metadata path to keep in
        // step with the one `SongDbMetadata` already fills. The file's name is what names it, which
        // is what the rest of the app does for every format that says nothing about itself.
        o << "format\tMP3 (minimp3)" << '\n'
          << "channels\t" << dec_.info.channels << '\n'
          << "rate\t" << dec_.info.hz << " Hz" << '\n'
          // `dec_.info` is the *last frame decoded*, so this is that frame's rate rather than the
          // file's average -- which for a variable-bitrate file is a different number every time it
          // is asked. Honest label, rather than a wrong word over a right number.
          << "bitrate\t" << dec_.info.bitrate_kbps << " kbps" << '\n'
          << "seekable\t" << (canSeek() ? 1 : 0);
        return o.str();
    }

    int preferredSampleRate() const override { return dec_.info.hz; }

private:
    // A copy of the file, because the decoder reads from it for as long as it lives.
    std::vector<char> bytes_;
    mp3dec_ex_t dec_{};
    bool opened_ = false;
    std::vector<float> scratch_;
};

/**
 * Commodore 64, through libsidplayfp.
 *
 * The largest single body of music left after trackers: roughly 72,000 files in Modland alone.
 *
 * No Commodore ROMs are supplied. Thirty random Modland SIDs played without them and none needed
 * BASIC (`docs/PLAN_FORMATS.md`); whether to ship or source ROMs is an open question there.
 *
 * The emulation is SIDLite rather than ReSIDfp: 3.x split ReSIDfp into a separate library, and
 * SIDLite ships inside this one.
 */
class SidBackend : public Backend {
public:
    static bool recognises(const std::vector<char> &bytes) {
        if (bytes.size() < 4) return false;
        return !std::memcmp(bytes.data(), "PSID", 4) || !std::memcmp(bytes.data(), "RSID", 4);
    }

    explicit SidBackend(const std::vector<char> &bytes)
        : tune_(reinterpret_cast<const uint_least8_t *>(bytes.data()),
                static_cast<uint_least32_t>(bytes.size())),
          builder_("sidlite") {
        if (!tune_.getStatus()) {
            throw std::runtime_error(std::string("the Commodore 64 decoder (libsidplayfp) does not see a SID here: ") + tune_.statusString());
        }
        tune_.selectSong(0);
        info_ = tune_.getInfo();

        // **A BASIC program, not machine code** (`docs/STATUS.md` C80). An RSID with this flag is
        // started by BASIC's RUN and runs in the BASIC interpreter, which lives in a ROM this app
        // does not carry -- so libsidplayfp loads it without complaint and plays silence. A tune
        // that cannot play has to say so instead: this is the header's own statement, not a guess.
        if (info_ && info_->compatibility() == SidTuneInfo::COMPATIBILITY_BASIC) {
            throw std::runtime_error(
                "this tune is a BASIC program and needs the Commodore 64's BASIC ROM, which Protracktor does not have");
        }

        SidConfig cfg = engine_.config();
        cfg.frequency = kSampleRate;
        cfg.sidEmulation = &builder_;
        if (!engine_.config(cfg)) {
            throw std::runtime_error(std::string("the Commodore 64 decoder (libsidplayfp) refused the configuration: ") + engine_.error());
        }
        if (!engine_.load(&tune_)) {
            throw std::runtime_error(std::string("the Commodore 64 decoder (libsidplayfp) could not load it: ") + engine_.error());
        }

        // Without this, mix() dereferences a mixer that does not exist yet. Nothing in the header
        // says so; it cost a segfault and a read of player.cpp to find.
        engine_.initMixer(false);
    }

    std::size_t render(int sampleRate, std::size_t frames, float *out) override {
        if (sampleRate > 0) rate_ = sampleRate;
        std::size_t produced = 0;

        while (produced < frames) {
            if (spare_.empty()) {
                // play() runs the machine for a while and reports how many samples are waiting;
                // mix() takes them out. The two are separate in 3.x.
                const int waiting = engine_.play(kCyclesPerPass);
                if (waiting <= 0) break;

                const unsigned int want = std::min<unsigned int>(waiting, kScratchSamples);
                scratch_.resize(kScratchSamples);
                const unsigned int got = engine_.mix(scratch_.data(), want);
                if (got == 0) break;
                spare_.assign(scratch_.begin(), scratch_.begin() + got);
                spareRead_ = 0;
            }

            // Mono, duplicated into both channels. A SID is one chip and putting it in one ear
            // would be a choice nobody made.
            const short sample = spare_[spareRead_++];
            out[produced * 2] = static_cast<float>(sample) / 32768.0f;
            out[produced * 2 + 1] = out[produced * 2];
            ++produced;
            if (spareRead_ >= spare_.size()) spare_.clear();
        }
        rendered_ += produced;
        return produced;
    }

    // libsidplayfp has no seek: the only way to a position is to run the machine there, which is
    // what `seekByRendering` does.
    bool canSeek() const override { return true; }
    void seek(double seconds) override { seekByRendering(*this, seconds, rate_); }

    void rewind() override {
        spare_.clear();
        spareRead_ = 0;
        rendered_ = 0;
        engine_.load(&tune_);
        engine_.initMixer(false);
    }

    // Counted, because there is nothing to ask. The other backends know where they are in a song;
    // libsidplayfp is running a program and has no notion of a position at all. Frames handed to
    // the output device is the same quantity by another route, and it is the one the listener is
    // actually hearing.
    double positionSeconds() const override {
        return static_cast<double>(rendered_) / static_cast<double>(rate_);
    }

    int subsongCount() const override { return info_ ? static_cast<int>(info_->songs()) : 1; }

    int currentSubsong() const override { return song_; }

    /**
     * libsidplayfp selects on the *tune* and the engine has to be handed it again.
     *
     * Its songs are numbered from one, like sc68's; the interface counts from zero. And unlike the
     * others this cannot simply be told to jump -- the tune is reselected and the whole machine
     * reloaded, which is why the mixer is initialised again as well. Forgetting that is a segfault,
     * and it is the same one that cost a day when this backend was written.
     */
    bool selectSubsong(int index) override {
        if (index < 0 || index >= subsongCount()) return false;
        tune_.selectSong(static_cast<unsigned int>(index + 1));
        if (!engine_.load(&tune_)) return false;
        engine_.initMixer(false);
        info_ = tune_.getInfo();
        song_ = index;
        spare_.clear();
        spareRead_ = 0;
        rendered_ = 0;
        return true;
    }

    // SID files carry no length. HVSC's Songlengths database is what supplies one, and that is a
    // catalogue feature rather than a backend one.
    double durationSeconds() const override { return 0.0; }

    std::string describe() const override {
        const auto field = [this](unsigned int i) -> const char * {
            return (info_ && i < info_->numberOfInfoStrings() && info_->infoString(i))
                ? info_->infoString(i) : "";
        };
        std::ostringstream o;
        o << "title\t" << field(0) << '\n'
          << "format\tCommodore 64 (SID)" << '\n'
          << "artist\t" << field(1) << '\n'
          << "copyright\t" << field(2) << '\n'
          << "channels\t" << (info_ ? info_->sidChips() : 1) << '\n'
          << "subsongs\t" << (info_ ? info_->songs() : 1) << '\n'
          << "seekable\t1";
        return o.str();
    }

    int preferredSampleRate() const override { return kSampleRate; }

private:
    static constexpr int kSampleRate = 44100;
    static constexpr unsigned int kCyclesPerPass = 20000;
    static constexpr unsigned int kScratchSamples = 8192;

    // Both written by render() on the audio thread and read by positionSeconds() from elsewhere.
    // Same reasoning as Sc68Backend: a race by the language's rules, and the default ordering costs
    // a fence per buffer against the price of emulating a 6502 (`docs/review.md` R6).
    std::atomic<std::uint64_t> rendered_{0};
    std::atomic<int> rate_{kSampleRate};

    SidTune tune_;
    SIDLiteBuilder builder_;
    sidplayfp engine_;
    const SidTuneInfo *info_ = nullptr;
    int song_ = 0;
    std::vector<short> scratch_;
    std::vector<short> spare_;
    std::size_t spareRead_ = 0;
};

/**
 * AHX and HVL, through HivelyTracker's standalone replayer.
 *
 * The Amiga synth trackers: no samples in the file, a handful of waveforms and a per-instrument
 * program that bends them. libopenmpt has **no loader for either** -- measured in
 * `docs/PLAN_FORMATS.md` §0b, 0/12 and 0/12 -- which is why `.ahx` and `.hvl` were taken out of
 * `SupportedFormats` on 2026-09-04 and why they are back now. 1,433 Modland files.
 *
 * Measured on the host before integration (§6): **80 of 80 sampled files loaded from a buffer, were
 * audible and reached a song end.** The first backend here to come back clean on both halves.
 *
 * Two things make it unlike the other five:
 *
 * **It renders a PAL frame at a time.** `hvl_DecodeFrame` fills exactly one 1/50s buffer and picks
 * that size itself; everything else here renders however many frames it is asked for. So this is
 * the one backend that carries a ring buffer, and `framesPerCall_` is computed the way
 * `hvl_DecodeFrame` computes it -- `rate/50/multiplier*multiplier`, which is 880 rather than 882
 * when the speed multiplier is four. Assuming 882 would emit two stale samples every frame.
 *
 * **It knows how long the tune is, cheaply.** `hvl_play_irq` is the sequencer without the mixer, so
 * running a tune to its end costs about a millisecond instead of thirty. The length is therefore
 * measured at load, unconditionally, and `.ahx` and `.hvl` get a real duration and a working seek
 * bar without `SongLengths` knowing anything about them. Verified on the host: sequencer-only and
 * full-render lengths agreed on every file tried.
 */
class HivelyBackend : public Backend {
public:
    /** Both of the replayer's own magics. AHX is "THX" -- the format is older than its tracker. */
    static bool recognises(const std::vector<char> &bytes) {
        if (bytes.size() < 16) return false;
        const auto *b = reinterpret_cast<const unsigned char *>(bytes.data());
        return (b[0] == 'T' && b[1] == 'H' && b[2] == 'X' && b[3] < 3) ||
               (b[0] == 'H' && b[1] == 'V' && b[2] == 'L' && b[3] < 2);
    }

    explicit HivelyBackend(const std::vector<char> &bytes) {
        static std::once_flag once;
        std::call_once(once, [] { hvl_InitReplayer(); });

        // The loader walks the file computing sizes and **never checks against its length**: a
        // truncated file sends it reading past the end, and `strncpy(ht_Name, &buf[offset], 128)`
        // does the same with an offset taken straight from the header. Rather than restate its
        // data-dependent walk here -- a second copy of a parser is a copy that goes stale -- the
        // bytes are handed over inside a padded, zeroed allocation big enough that the walk cannot
        // leave it.
        //
        // The bound is arithmetic, not a guess. Every count it reads is a byte or a twelve-bit
        // field: at most 4,095 positions of 67 channels x 2, 256 tracks x 255 rows x 5 bytes, and
        // 64 instruments of 22 + 255 x 5. That is under 960 KB, so a mebibyte of zeros past the end
        // is provably past the worst the header can ask for -- and zeros terminate both walks
        // early, because a zero row is not 0x3f and a zero instrument has no program.
        std::vector<unsigned char> padded(bytes.size() + kLoaderSlack, 0);
        std::memcpy(padded.data(), bytes.data(), bytes.size());

        ahx_ = static_cast<unsigned char>(bytes[0]) == 'T';

        // freeit = 0: `padded` stays ours and is dropped on the way out of this constructor. The
        // tune is one allocation of its own by then.
        ht_ = hvl_reset(padded.data(), static_cast<uint32>(bytes.size()), kStereoSeparation,
                        static_cast<uint32>(kSampleRate), 0);
        if (!ht_) throw std::runtime_error("the Amiga AHX decoder (HivelyTracker) could not load it");

        const uint32 multiplier = ht_->ht_SpeedMultiplier ? ht_->ht_SpeedMultiplier : 1;
        framesPerCall_ = static_cast<std::size_t>(kSampleRate / 50 / multiplier) * multiplier;
        frame_.resize(framesPerCall_ * 2);

        duration_ = measureSeconds(0);
        startSubsong(0);
    }

    ~HivelyBackend() override {
        if (ht_) hvl_FreeTune(ht_);
    }

    std::size_t render(int, std::size_t frames, float *out) override {
        std::size_t written = 0;
        while (written < frames) {
            if (framePos_ == framesPerCall_) {
                // A tune that has reached its end is not decoded again: the replayer would happily
                // keep going round, and a short render is what tells the engine the file is over.
                if (ht_->ht_SongEndReached) break;
                hvl_DecodeFrame(ht_, reinterpret_cast<int8 *>(frame_.data()),
                                reinterpret_cast<int8 *>(frame_.data()) + 2, 4);
                framePos_ = 0;
            }
            const std::size_t take = std::min(frames - written, framesPerCall_ - framePos_);
            for (std::size_t i = 0; i < take; ++i) {
                out[(written + i) * 2] = frame_[(framePos_ + i) * 2] / 32768.0f;
                out[(written + i) * 2 + 1] = frame_[(framePos_ + i) * 2 + 1] / 32768.0f;
            }
            framePos_ += take;
            written += take;
        }
        rendered_ += written;
        return written;
    }

    bool canSeek() const override { return true; }

    /**
     * Seeks by starting again and decoding forward, because the replayer has no other way in.
     *
     * Decoding rather than sequencing alone: the sequencer gives the right position but the wrong
     * voice state, and the difference is audible as a wrong note at the seek point. Full decoding
     * costs about 30ms per minute skipped on a desktop, which is what a seek can afford and a load
     * cannot -- the length measurement is the other way round for exactly that reason.
     */
    void seek(double seconds) override {
        const auto target = static_cast<std::size_t>(std::max(0.0, seconds) * kSampleRate);
        startSubsong(subsong_);
        while (rendered_ + framesPerCall_ <= target && !ht_->ht_SongEndReached) {
            hvl_DecodeFrame(ht_, reinterpret_cast<int8 *>(frame_.data()),
                            reinterpret_cast<int8 *>(frame_.data()) + 2, 4);
            rendered_ += framesPerCall_;
        }
        framePos_ = framesPerCall_;
    }

    void rewind() override { startSubsong(subsong_); }

    double positionSeconds() const override {
        return static_cast<double>(rendered_) / kSampleRate;
    }

    double durationSeconds() const override { return duration_; }

    std::string describe() const override {
        std::ostringstream o;
        o << "title\t" << title() << '\n'
          << "format\t" << (ahx_ ? "Amiga AHX (HivelyTracker)" : "Amiga HVL (HivelyTracker)") << '\n'
          << "channels\t" << ht_->ht_Channels << '\n'
          << "subsongs\t" << subsongCount() << '\n'
          << "seekable\t1";
        // 1-based, as the tracker numbers them: `hvl_load_ahx` and `hvl_load_hvl` fill 1..N and never 0.
        std::vector<std::string> names;
        for (int i = 1; i <= ht_->ht_InstrumentNr; ++i) {
            const auto &name = ht_->ht_Instruments[i].ins_Name;
            names.emplace_back(name, strnlen(name, sizeof name));
        }
        const std::string instruments = joinNames(names);
        if (!instruments.empty()) o << '\n' << "instrument_names\t" << instruments;
        return o.str();
    }

    int preferredSampleRate() const override { return kSampleRate; }

    // `ht_SubsongNr` is how many *extra* subsongs there are -- `hvl_InitSubsong` accepts indices up
    // to and including it -- so a file with none reports zero and holds one tune.
    int subsongCount() const override { return static_cast<int>(ht_->ht_SubsongNr) + 1; }

    int currentSubsong() const override { return subsong_; }

    bool selectSubsong(int index) override {
        if (index < 0 || index >= subsongCount()) return false;
        duration_ = measureSeconds(index);
        if (!startSubsong(index)) return false;
        return true;
    }

private:
    // 44100 divides by 50, which the replayer's frame size assumes without saying so.
    static constexpr int kSampleRate = 44100;

    // 4 is upstream's "Paula" setting: full stereo separation, the Amiga's own. It only affects AHX;
    // HVL files carry their own panning.
    static constexpr uint32 kStereoSeparation = 4;

    // Derived in the constructor's comment. A mebibyte, once per file loaded.
    static constexpr std::size_t kLoaderSlack = 1024 * 1024;

    bool startSubsong(int index) {
        if (!hvl_InitSubsong(ht_, static_cast<uint32>(index))) return false;
        ht_->ht_SongEndReached = 0;
        subsong_ = index;
        rendered_ = 0;
        framePos_ = framesPerCall_;      // nothing buffered; the next render decodes
        return true;
    }

    /**
     * How long a subsong runs, by running the sequencer without the mixer.
     *
     * Bounded at ten minutes, which is what `hvl2wav` uses, because a tune that never sets
     * `ht_SongEndReached` would otherwise spin here. A zero return reads as "unknown" upstream and
     * disables the scrubber rather than offering a wrong one.
     */
    double measureSeconds(int index) {
        if (!hvl_InitSubsong(ht_, static_cast<uint32>(index))) return 0.0;
        ht_->ht_SongEndReached = 0;
        const uint32 multiplier = ht_->ht_SpeedMultiplier ? ht_->ht_SpeedMultiplier : 1;
        long ticks = 0;
        const long limit = 600 * 50;
        for (; ticks < limit && !ht_->ht_SongEndReached; ++ticks)
            for (uint32 i = 0; i < multiplier; ++i) hvl_play_irq(ht_);
        return ticks >= limit ? 0.0 : static_cast<double>(ticks) / 50.0;
    }

    /** `ht_Name` is filled by a 128-byte `strncpy` that need not have terminated it. */
    std::string title() const {
        const char *name = ht_->ht_Name;
        const std::size_t length = static_cast<std::size_t>(
            std::find(name, name + sizeof(ht_->ht_Name), '\0') - name);
        std::string out(name, length);
        std::replace_if(out.begin(), out.end(),
                        [](char c) { return static_cast<unsigned char>(c) < 0x20; }, ' ');
        return out;
    }

    struct hvl_tune *ht_ = nullptr;
    bool ahx_ = false;
    std::size_t framesPerCall_ = 0;
    std::size_t framePos_ = 0;
    std::vector<short> frame_;
    std::size_t rendered_ = 0;
    double duration_ = 0.0;
    int subsong_ = 0;
};

/**
 * The ZX Spectrum AY trackers, through ZXTune: PT3, PT2, STC, ASC, SQT, STP and their relatives.
 *
 * The largest platform in Modland this app could not play -- 23,891 files, of which 58 opened,
 * because `.ay` happens to be a name game-music-emu also uses. These are trackers driving an
 * AY-3-8912, which is why they belong here and the 95,000 `*SF` files do not: those are console
 * emulators (`docs/PLAN_FORMATS.md` §7).
 *
 * Measured on the host before integration: **72 of 72 sampled files loaded from a buffer and were
 * audible, and every one stated its own length** -- median 149s. So these arrive with a duration
 * and a working seek bar without `SongLengths` knowing anything about them.
 *
 * Three things make it unlike the other backends:
 *
 * **Two shapes of factory.** ProTracker3 hands back a `Module::Factory`, which produces a holder
 * directly; every other AY plugin hands back an `AYM::Factory`, which produces a *chiptune* that
 * `AYM::CreateHolder` then wraps. ZXTune's own plugin layer papers over this and drags in its whole
 * plugin registry to do it, so this does it by hand.
 *
 * **The renderer needs the module's own properties, not fresh ones.** The AY renderer reads its
 * frequency table from parameters -- ZXTune's source says "frequency table is mandatory!!!" and
 * throws without it -- and the table is something the *file* chose, put there while loading. Handing
 * it an empty container throws at the first render rather than at construction, which reads as a
 * decoder that cannot decode.
 *
 * **Render returns chunks of its own size**, like HivelyTracker and unlike everything else, so this
 * carries a ring buffer.
 */
#if PROTRACKTOR_WITH_ZXTUNE

class ZxTuneBackend : public Backend {
public:
    /**
     * Whether any of ZXTune's AY decoders will take these bytes.
     *
     * There is no cheap magic to test: `.stc` and `.pt2` have no signature worth the name, and
     * ZXTune's decoders check structure instead. So recognition *is* loading, and the answer is
     * whether the constructor throws -- which is why this backend is asked late, after everything
     * that can identify a file by a header.
     */
    static bool worthTrying(const std::vector<char> &bytes, const std::string &name) {
        if (bytes.size() < 64) return false;
        const auto dot = name.find_last_of('.');
        if (dot == std::string::npos) return false;
        std::string extension = name.substr(dot + 1);
        // Through `unsigned char`: `std::tolower` on a negative value is undefined, and a filename
        // can hold one. Every other byte-classifying call in this file goes the same way.
        for (auto &c : extension) {
            c = static_cast<char>(std::tolower(static_cast<unsigned char>(c)));
        }
        return extension == "pt3" || extension == "pt2" || extension == "pt1" ||
               extension == "stc" || extension == "st1" || extension == "st3" ||
               extension == "asc" || extension == "as0" || extension == "sqt" ||
               extension == "stp" || extension == "psm" || extension == "ftc" ||
               extension == "gtr" ||
               // Not ZX Spectrum trackers at all: `.ym` and `.vtx` are register dumps off an
               // Atari ST or a Spectrum, and the same AY chip plays them back. They arrive here
               // because ZXTune is the only decoder in this build that reads one.
               extension == "ym" || extension == "vtx";
    }

    explicit ZxTuneBackend(const std::vector<char> &bytes) {
        const auto data = Binary::CreateContainer(
            Binary::View(bytes.data(), bytes.size()));

        namespace FC = Formats::Chiptune;
        // Ordered by how much of Modland each is worth, so the common case is decided first.
        tryFull("PT3", Module::ProTracker3::CreateFactory(FC::ProTracker3::CreateDecoder()), data);
        tryFull("PT3", Module::ProTracker3::CreateFactory(FC::ProTracker3::VortexTracker2::CreateDecoder()), data);
        tryAym("PT2", Module::ProTracker2::CreateFactory(), data);
        tryAym("STC", Module::SoundTracker::CreateFactory(FC::SoundTracker::Ver1::CreateCompiledDecoder()), data);
        tryAym("ST1", Module::SoundTracker::CreateFactory(FC::SoundTracker::Ver1::CreateUncompiledDecoder()), data);
        tryAym("ST3", Module::SoundTracker::CreateFactory(FC::SoundTracker::Ver3::CreateDecoder()), data);
        tryAym("ASC", Module::ASCSoundMaster::CreateFactory(FC::ASCSoundMaster::Ver1::CreateDecoder()), data);
        tryAym("AS0", Module::ASCSoundMaster::CreateFactory(FC::ASCSoundMaster::Ver0::CreateDecoder()), data);
        tryAym("STP", Module::SoundTrackerPro::CreateFactory(FC::SoundTrackerPro::CreateCompiledModulesDecoder()), data);
        tryAym("SQT", Module::SQTracker::CreateFactory(), data);
        // These three were claimed by `worthTrying` and by `SupportedFormats` before they were
        // implemented here, so `.psm`, `.ftc` and `.gtr` opened a file, refused it, and the app
        // said "is a format Protracktor cannot play yet" -- which is the message for a decoder that
        // does not exist, and was true only because this list was three lines short.
        tryAym("PT1", Module::ProTracker1::CreateFactory(), data);
        tryAym("PSM", Module::ProSoundMaker::CreateFactory(), data);
        tryAym("FTC", Module::FastTracker::CreateFactory(), data);
        tryAym("GTR", Module::GlobalTracker::CreateFactory(), data);
        // Register dumps rather than trackers, and three decoders for what looks like one format:
        // a `.ym` is either LHA-packed (all 4,961 of Modland's are) or bare, and the packed
        // decoder is the one that reads the container. Asked in that order for that reason.
        tryYm("YM", FC::YM::CreatePackedYMDecoder(), data);
        tryYm("YM", FC::YM::CreateYMDecoder(), data);
        tryYm("VTX", FC::YM::CreateVTXDecoder(), data);

        if (!holder_) throw std::runtime_error("the ZX Spectrum decoder (ZXTune) did not recognise it");

        durationSeconds_ = holder_->GetModuleInformation().Duration.CastTo<Time::Second>().Get();
        start();
    }

    std::size_t render(int, std::size_t frames, float *out) override {
        std::size_t written = 0;
        while (written < frames) {
            if (chunkPos_ == chunk_.size()) {
                if (ended_) break;
                chunk_ = renderer_->Render();
                chunkPos_ = 0;
                if (chunk_.empty()) { ended_ = true; break; }
            }
            const std::size_t take = std::min(frames - written, chunk_.size() - chunkPos_);
            for (std::size_t i = 0; i < take; ++i) {
                const auto &sample = chunk_[chunkPos_ + i];
                out[(written + i) * 2] = sample.Left() / 32768.0f;
                out[(written + i) * 2 + 1] = sample.Right() / 32768.0f;
            }
            chunkPos_ += take;
            written += take;
        }
        rendered_ += written;
        return written;
    }

    bool canSeek() const override { return durationSeconds_ > 0; }

    /**
     * Moves the position, **never past the end**.
     *
     * `Renderer::SetPosition` reaches a position by running the emulation forward to it, and asked
     * for one the tune never reaches it runs forward for ever. Measured on the host, 2026-09-10:
     * seeking to 90% of a 192-second PT3 returns at once, before or after a first render, and
     * seeking to `duration + 30s` had not returned after four minutes. On the phone that came back
     * as `SIGSEGV` inside `AYMRenderer::SetPosition` -- a runaway walking off the end of its own
     * state rather than a null anybody passed in.
     *
     * Reached by dragging the slider to the far right: the bar's maximum *is* the duration, and a
     * float rounding a hair over it is enough.
     *
     * Half a second short of the end, not a hair short: the last position the renderer can actually
     * reach is the last one it emits, which is a frame before the end rather than the end itself.
     */
    void seek(double seconds) override {
        const double limit = durationSeconds_ > 0.5 ? durationSeconds_ - 0.5 : 0.0;
        const double target = std::clamp(seconds, 0.0, limit);
        renderer_->SetPosition(
            Time::AtMillisecond() + Time::Milliseconds(static_cast<uint_t>(target * 1000.0)));
        rendered_ = static_cast<std::size_t>(target * kSampleRate);
        chunk_ = Sound::Chunk();
        chunkPos_ = 0;
        ended_ = false;
    }

    void rewind() override { start(); }

    double positionSeconds() const override {
        return static_cast<double>(rendered_) / kSampleRate;
    }

    double durationSeconds() const override { return durationSeconds_; }

    std::string describe() const override {
        std::ostringstream o;
        o << "title\t" << property("Title") << '\n'
          // Every tracker on this list is a Spectrum one, but a `.ym` register dump is usually an
          // Atari ST recording -- the AY chip is what the two machines share, not the platform.
          // Saying "ZX Spectrum YM" of a Mad Max tune would be wrong in the one place the app
          // states what a file *is*.
          << "format\t" << (format_ == "YM" ? "AY/YM " : "ZX Spectrum ")
                         << format_ << " (ZXTune)" << '\n'
          << "artist\t" << property("Author") << '\n'
          << "tracker\t" << property("Program") << '\n'
          << "channels\t3" << '\n'
          << "seekable\t" << (canSeek() ? 1 : 0) << '\n'
          << "message\t" << property("Comment");
        return o.str();
    }

    int preferredSampleRate() const override { return kSampleRate; }

private:
    static constexpr int kSampleRate = 44100;

    /** A plugin that produces a holder itself. ProTracker3 is the only one of these. */
    void tryFull(const char *name, Module::Factory::Ptr factory, const Binary::Container::Ptr &data) {
        if (holder_ || !factory) return;
        try {
            auto props = Parameters::Container::Create();
            if (auto found = factory->CreateModule(*props, *data, props)) {
                holder_ = std::move(found);
                format_ = name;
            }
        } catch (...) {
            // A decoder that half-recognises a truncated file throws. That means "not this one".
        }
    }

    /**
     * A YM or VTX register dump, whose factory is built from a decoder rather than standing alone.
     *
     * The decoder is what tells the three apart -- packed YM, bare YM, VTX -- and the factory is
     * the same one for all three, so this cannot be folded into [tryAym] without the caller
     * constructing the factory itself three times.
     */
    void tryYm(const char *name, Formats::Chiptune::YM::Decoder::Ptr decoder,
               const Binary::Container::Ptr &data) {
        if (holder_ || !decoder) return;
        tryAym(name, Module::YMVTX::CreateFactory(std::move(decoder)), data);
    }

    /** A plugin that produces a chiptune, which `AYM::CreateHolder` turns into a holder. */
    void tryAym(const char *name, Module::AYM::Factory::Ptr factory, const Binary::Container::Ptr &data) {
        if (holder_ || !factory) return;
        try {
            auto props = Parameters::Container::Create();
            if (auto chiptune = factory->CreateChiptune(*data, props)) {
                holder_ = Module::AYM::CreateHolder(std::move(chiptune));
                format_ = name;
            }
        } catch (...) {
        }
    }

    void start() {
        renderer_ = holder_->CreateRenderer(kSampleRate, holder_->GetModuleProperties());
        chunk_ = Sound::Chunk();
        chunkPos_ = 0;
        rendered_ = 0;
        ended_ = false;
    }

    /** One of the module's own string properties, or empty. */
    std::string property(const char *key) const {
        const auto value = holder_->GetModuleProperties()->FindString(key);
        return value ? std::string(*value) : std::string();
    }

    Module::Holder::Ptr holder_;
    Module::Renderer::Ptr renderer_;
    Sound::Chunk chunk_;
    std::size_t chunkPos_ = 0;
    std::size_t rendered_ = 0;
    double durationSeconds_ = 0.0;
    bool ended_ = false;
    std::string format_ = "AY";
};

#endif  // PROTRACKTOR_WITH_ZXTUNE

#if PROTRACKTOR_WITH_UADE
/**
 * UADE: an emulated Amiga running the tune's original replay routine, in a process of its own.
 *
 * **Everything unusual about this class follows from one fact**: libuade is a client, not a
 * decoder. `uade_new_state` opens a socketpair, forks, and execs `uadecore`; rendered audio comes
 * back over the socket. So an instance here owns a *process*, and the two things that would
 * normally be free are not:
 *
 * - **It plays from a path, not from a buffer.** `uade_play_from_buffer` says in its own header
 *   that it does not work with multifile songs, and multifile is much of what UADE is for: TFMX is
 *   `mdat.name` beside `smpl.name`, and asking about the `mdat` alone reports "unsupported" for
 *   the largest Amiga custom format in Modland. That was measured the wrong way once already
 *   (`docs/PLAN_FORMATS.md` §4). So the bytes are written to a scratch directory, with whatever
 *   companions the caller was able to supply beside them, and UADE is given that path.
 * - **The directory lives as long as the backend does.** The emulated program asks for its files
 *   *while playing* -- that is how a replay routine loads a sample -- so deleting the scratch
 *   after `uade_play` returns would break tunes some way in rather than at the start.
 *
 * One scratch directory per instance, made with `mkdtemp`. A folder scan runs concurrently with
 * playback (round 5) and both may reach UADE, so a single shared directory would be two openings
 * writing over each other's files.
 */
class UadeBackend : public Backend {
public:
    /**
     * Where `uadecore` is and where UADE's data directory was put. Set once from Kotlin.
     *
     * Neither is knowable at build time. `uadecore` lives in `nativeLibraryDir`, whose path holds
     * the APK's install cookie, and the data directory is under `filesDir`. The compiled-in
     * defaults are deliberately paths that cannot exist, so a missed configuration fails loudly
     * (`native/backends/uade/config/options.h`).
     */
    static void setPaths(const std::string &core, const std::string &base,
                         const std::string &scratch) {
        // **A dead emulator must not take the app with it**, and without this it did. libuade
        // writes to uadecore over a socket, and a write to a socket whose other end has died raises
        // SIGPIPE, whose default action ends the process -- the app, with no message and nothing
        // in any log the listener can see. That is exactly the failure fork+exec was chosen to
        // contain (`docs/BACKLOG.md` A44): uadecore has 51 `exit()` calls, and one of them firing
        // was meant to end a tune, not the player. Found on the phone as a crash on
        // `cust.paradroid`'s seventh subsong, and reproduced on the host by killing uadecore
        // mid-tune: the driver died of signal 13. Ignored, the write fails with EPIPE, libuade
        // reports an error, and the tune ends.
        //
        // Process-wide, because a signal disposition is; ignoring SIGPIPE is what every program
        // that writes to sockets does, and Java's own sockets never relied on it.
        static std::once_flag ignored;
        std::call_once(ignored, [] { ::signal(SIGPIPE, SIG_IGN); });

        std::lock_guard<std::mutex> held(pathMutex());
        corePath() = core;
        basePath() = base;
        scratchPath() = scratch;
    }

    /**
     * Whether there is anything to try.
     *
     * **The replay routines are downloaded, not shipped** (`docs/LICENSES.md`), so on a fresh
     * install this is false and UADE is simply not among the backends. Asked before every open
     * rather than cached: the download can finish while the app is running, and a cached "no"
     * would mean the formats stayed dead until a restart with nothing on screen explaining it.
     */
    static bool available() {
        const Paths paths = pathsCopy();
        if (paths.core.empty() || paths.base.empty() || paths.scratch.empty()) return false;
        // The two files `uade_new_state` itself checks, checked here so a refusal can say which
        // one is missing instead of libuade printing a warning nobody sees.
        if (::access(paths.core.c_str(), X_OK) != 0) return false;
        const std::string uaerc = paths.base + "/uaerc";
        const std::string score = paths.base + "/score";
        return ::access(uaerc.c_str(), R_OK) == 0 && ::access(score.c_str(), R_OK) == 0;
    }

    /**
     * Opens [bytes], named [name], with [companions] written beside it.
     *
     * The companions are whatever the caller could find: the other half of a TFMX song, the sample
     * file a Sonic Arranger tune names. An empty list is normal and most formats are one file.
     */
    UadeBackend(const std::vector<char> &bytes, const std::string &name,
                const std::vector<protracktor::Companion> &companions) {
        const Paths paths = pathsCopy();
        if (paths.core.empty()) throw std::runtime_error("the Amiga decoder (UADE) is not set up");
        // **Said apart from "does not recognise".** UADE answers a missing player file with the
        // same zero it gives a file it has never heard of, and prints the difference to stderr,
        // which on a phone goes nowhere. A tune refused on a phone that had downloaded the
        // routines could not be told from a tune refused on one that had not, until this.
        if (!hasPlayers(paths.base)) {
            throw std::runtime_error("the Amiga replay routines are not where the decoder looks for them");
        }

        makeScratch(paths.scratch);
        for (const protracktor::Companion &companion : companions) write(companion.name, companion.bytes);

        // **UADE's reasons, kept.** libuade and uadecore explain a refusal only on stderr and
        // stdout -- "Could not load player", "load: request error: smpl.x" -- and on a phone both
        // go nowhere, so every refusal read "does not recognise it" whatever the cause. Both
        // descriptors point into this instance's scratch directory while the song is opened, and
        // uadecore inherits them at fork, so its own complaints land there too. The last line is
        // attached to the refusal (`docs/BACKLOG.md` A44). One opening at a time, because the
        // descriptors are the process's, not this object's.
        std::unique_lock<std::mutex> opening(openMutex());
        const Captured captured(scratch_ + "/.uade-said");
        // The tune itself last, and its path is the one UADE is asked about. Companions are only
        // ever *found* by the emulated program, by the name the tune asks for.
        modulePath_ = write(name, bytes);

        struct uade_config *config = uade_new_config();
        if (!config) {
            cleanScratch();
            throw std::runtime_error("the Amiga decoder (UADE) would not configure itself");
        }
        uade_config_set_option(config, UC_BASE_DIR, paths.base.c_str());
        uade_config_set_option(config, UC_UADECORE_FILE, paths.core.c_str());
        uade_config_set_option(config, UC_FREQUENCY, "44100");
        // **One subsong per render stream.** Left to itself UADE moves on to the next subsong when
        // one ends, inside the same stream -- found on the host, where `mdat.bundesliga manager`
        // reported 3.5 s after 10 s of rendering because the position had restarted with subsong
        // two. Here the engine owns that decision: a short render ends the subsong, and whether the
        // next one follows is the player's "play all subsongs", as it is for every other backend.
        uade_config_set_option(config, UC_ONE_SUBSONG, nullptr);
        // **Not `UC_CONTENT_DETECTION`**, despite the name. uade123 exposes it as "detect strictly
        // by file content", and `get_eagleplayer` then rejects a filename match whenever the bytes
        // alone did not identify the format. Several real formats are known only by their prefix or
        // suffix; Hippel COSO was the case that caught this, with the probe reporting 0 of 12 while
        // uade123 played the same files. UADE's default tries content first and then permits the
        // filename match, which is what an integrating player needs.
        state_ = uade_new_state(config);
        std::free(config);
        if (!state_) {
            std::string reason = "the Amiga decoder (UADE) would not start";
            const std::string said = captured.lastLine();
            if (!said.empty()) reason += " (UADE: " + said + ")";
            cleanScratch();
            throw std::runtime_error(reason);
        }
        uade_set_amiga_loader(&UadeBackend::loadAmigaFile, this, state_);

        const int claimed = uade_play(modulePath_.c_str(), -1, state_);
        if (claimed <= 0) {
            std::string reason = claimed == 0
                ? "the Amiga decoder (UADE) does not recognise it"
                : "the Amiga decoder (UADE) failed while opening it";
            const std::string said = captured.lastLine();
            if (!said.empty()) reason += " (UADE: " + said + ")";
            close();
            throw std::runtime_error(reason);
        }
        if (const struct uade_song_info *info = uade_get_song_info(state_)) subsongs_ = info->subsongs;
        skipEmptyLeadingSubsongs();
    }

    ~UadeBackend() override { close(); }

    std::size_t render(int, std::size_t frames, float *out) override {
        std::size_t written = 0;
        while (written < frames) {
            const std::size_t want = std::min(frames - written, kChunkFrames);
            const ssize_t got = uade_read(chunk_.data(), want * 2 * sizeof(int16_t), state_);
            // Zero is the song ending; negative is the emulator gone. Both are a short render,
            // which is what tells the engine the file is over.
            if (got <= 0) break;
            const std::size_t samples = static_cast<std::size_t>(got) / sizeof(int16_t);
            for (std::size_t i = 0; i < samples; ++i) {
                out[written * 2 + i] = chunk_[i] / 32768.0f;
            }
            written += samples / 2;
        }
        return written;
    }

    /**
     * Yes, by running the emulator to the position, which is how every emulator here seeks.
     *
     * Measured before saying so, 2026-09-21: UADE renders 120 to 150 times faster than real time
     * on the host, so a minute in costs about half a second there and a few on a phone. The host
     * holds the decoder lock for the duration and the audio thread plays silence meanwhile, as it
     * does for a SID (`docs/ARCHITECTURE.md` §5). Backwards starts the subsong again, since the
     * emulator has no other way back. Without a length the app offers no slider at all, so in
     * practice this waits for [startedPlaying]'s measurement.
     */
    bool canSeek() const override { return true; }

    void seek(double seconds) override {
        if (!state_) return;
        const double known = durationSeconds();
        const double target = std::max(0.0, known > 0.0 ? std::min(seconds, known) : seconds);
        if (target < positionSeconds()) {
            uade_stop(state_);
            if (uade_play(modulePath_.c_str(), subsongs_.cur, state_) <= 0) return;
        }
        // Rendered and thrown away, a chunk at a time, until the position is reached or the
        // subsong ends first.
        while (positionSeconds() + static_cast<double>(kChunkFrames) / kSampleRate <= target) {
            if (uade_read(chunk_.data(), kChunkFrames * 2 * sizeof(int16_t), state_) <= 0) break;
        }
    }

    void rewind() override {
        // Back to the start by playing the subsong again: the emulator has no other way in.
        if (!state_) return;
        uade_stop(state_);
        uade_play(modulePath_.c_str(), subsongs_.cur, state_);
    }

    double positionSeconds() const override {
        const struct uade_song_info *info = state_ ? uade_get_song_info(state_) : nullptr;
        if (!info) return 0.0;
        // Bytes of 16-bit stereo that the emulator has synthesized for this subsong.
        return static_cast<double>(info->subsongbytes) / (kSampleRate * 2 * sizeof(int16_t));
    }

    /**
     * The length, once it is known, and nothing invented before.
     *
     * An Amiga replay routine has no notion of a length; the file states none, and UADE's own
     * figure is zero for nearly every tune. What a routine does know is when it has finished, so
     * [startedPlaying] runs a second emulator to that point in the background and this reports
     * what it found. Until then it is zero, which the bar and the total read correctly (C68) --
     * a guess here is what C67 was about. A subsong that never ends stays at zero.
     */
    double durationSeconds() const override {
        const double measured = measured_.load(std::memory_order_acquire);
        if (measured > 0.0) return measured;
        // What the host already knew, reported as this backend's own, so a seek can be held to it.
        const double known = knownFor(subsongs_.cur);
        if (known > 0.0) return known;
        const struct uade_song_info *info = state_ ? uade_get_song_info(state_) : nullptr;
        return info ? info->duration : 0.0;
    }

    /**
     * While the measurement runs, **and after it has found a length**.
     *
     * The first version said yes only while measuring, which stops being true at the moment the
     * length becomes known -- so the host, which asks for the length only while this says yes,
     * stopped asking just as there was something to read, and the phone never showed one. The host
     * stops asking by itself once it has published a length, so saying yes afterwards costs one
     * load per buffer until then and nothing after.
     */
    bool durationArrivesLater() const override {
        return measuring_.load(std::memory_order_acquire) ||
               measured_.load(std::memory_order_acquire) > 0.0;
    }

    void startedPlaying() override {
        playing_ = true;
        if (knownFor(subsongs_.cur) <= 0.0) measure(subsongs_.cur);
    }

    void knownLengths(const std::vector<double> &lengths) override { known_ = lengths; }

    int preferredSampleRate() const override { return kSampleRate; }

    int subsongCount() const override { return subsongs_.max - subsongs_.min + 1; }

    /** Zero-based here, whatever UADE numbers them from -- usually one (`engine.h`). */
    int currentSubsong() const override {
        const struct uade_song_info *info = state_ ? uade_get_song_info(state_) : nullptr;
        return (info ? info->subsongs.cur : subsongs_.cur) - subsongs_.min;
    }

    bool selectSubsong(int index) override {
        if (!state_ || index < 0 || index >= subsongCount()) return false;
        const int wanted = subsongs_.min + index;
        uade_stop(state_);
        if (uade_play(modulePath_.c_str(), wanted, state_) <= 0) return false;
        if (const struct uade_song_info *info = uade_get_song_info(state_)) subsongs_ = info->subsongs;
        // Another subsong is another length. Only if a measurement was ever started: a scan never
        // plays, and switching subsongs there must not start an emulator either.
        // Only while playing -- a scan switches no subsongs worth measuring -- and only a subsong
        // the host did not already know.
        if (playing_ && knownFor(wanted) <= 0.0) {
            measure(wanted);
        } else {
            stopMeasuring();
            measured_.store(0.0, std::memory_order_release);
        }
        return true;
    }

    /**
     * The same `key\tvalue` lines every backend gives (`DescribeBlock`).
     *
     * The first version returned a sentence, which parsed as no keys at all: the information panel
     * showed no format, and `seekable` was absent rather than stated.
     */
    std::string describe() const override {
        const struct uade_song_info *info = state_ ? uade_get_song_info(state_) : nullptr;
        std::ostringstream o;
        // The format is what the user wants; the player is what tells two Hippel variants apart,
        // and is the thing to quote back when a file does not work.
        const std::string format = info && info->formatname[0] ? info->formatname : "Amiga custom";
        o << "format\t" << format << " (UADE)" << '\n';
        if (info && info->playername[0]) o << "player\t" << info->playername << '\n';
        if (info && info->modulename[0]) o << "title\t" << info->modulename << '\n';
        // Where it opened, as `GmeBackend` says it: `skipEmptyLeadingSubsongs` can start past a
        // first subsong that is only silence, and the app reads this to point the subsong strip,
        // and the length, at what is actually playing.
        o << "subsongs\t" << subsongCount() << '\n'
          << "subsong\t" << currentSubsong() << '\n'
          << "seekable\t" << (canSeek() ? 1 : 0);
        return o.str();
    }

private:
    static constexpr int kSampleRate = 44100;
    /** Rendered in pieces so one buffer serves any request; 1024 frames is the engine's own. */
    static constexpr std::size_t kChunkFrames = 1024;

    struct Paths {
        std::string core;
        std::string base;
        std::string scratch;
    };

    static Paths pathsCopy() {
        std::lock_guard<std::mutex> held(pathMutex());
        return Paths{corePath(), basePath(), scratchPath()};
    }

    static std::string &corePath() { static std::string p; return p; }
    static std::string &basePath() { static std::string p; return p; }
    static std::string &scratchPath() { static std::string p; return p; }
    static std::mutex &pathMutex() { static std::mutex m; return m; }

    /**
     * Opens at the first subsong with sound in it, as `GmeBackend` does for HES and KSS.
     *
     * Found on the host: `reach for the skies-german.avp` begins with a subsong of half a second of
     * silence that ends, and the music is subsong one. UADE left to itself walks on to it; this
     * engine keeps one subsong per stream (see `UC_ONE_SUBSONG`), so without this the tune would
     * be half a second of nothing and then the next track.
     *
     * **Only a subsong that ends silent is skipped.** One that is merely quiet at the start -- a
     * slow fade-in, a long intro -- is still playing after the look, and is kept. The look is at
     * most two seconds of emulation per subsong, which UADE renders many times faster than real
     * time, and at most eight subsongs, so a file of nothing but empty subsongs costs a bounded
     * moment and then plays its first one anyway.
     */
    void skipEmptyLeadingSubsongs() {
        const int start = subsongs_.cur;
        int chosen = start;
        for (int subsong = start; subsong <= subsongs_.max && subsong < start + 8; ++subsong) {
            if (subsong != start) {
                uade_stop(state_);
                if (uade_play(modulePath_.c_str(), subsong, state_) <= 0) break;
            }
            bool heard = false;
            bool ended = false;
            std::size_t looked = 0;
            while (looked < static_cast<std::size_t>(kSampleRate) * 2) {
                const ssize_t got = uade_read(chunk_.data(), kChunkFrames * 2 * sizeof(int16_t), state_);
                if (got <= 0) { ended = true; break; }
                const std::size_t samples = static_cast<std::size_t>(got) / sizeof(int16_t);
                for (std::size_t i = 0; i < samples && !heard; ++i) heard = chunk_[i] != 0;
                looked += samples / 2;
                if (heard) break;
            }
            if (heard || !ended) { chosen = subsong; break; }
        }
        // Back to the start of whichever subsong was chosen: the look consumed audio.
        uade_stop(state_);
        uade_play(modulePath_.c_str(), chosen, state_);
        if (const struct uade_song_info *info = uade_get_song_info(state_)) subsongs_ = info->subsongs;
    }

    static std::mutex &openMutex() { static std::mutex m; return m; }

    /** The host's length for UADE's [subsong] number, or zero. */
    double knownFor(int subsong) const {
        const int index = subsong - subsongs_.min;
        return index >= 0 && index < static_cast<int>(known_.size()) ? known_[index] : 0.0;
    }

    /** The longest subsong measured before it is taken to loop for ever: ten minutes of audio. */
    static constexpr std::size_t kMeasureCapFrames = static_cast<std::size_t>(600) * kSampleRate;

    /**
     * Finds [subsong]'s length by playing it to the end in a second emulator, on a thread.
     *
     * A second process, not this one's: this one is playing, and running it ahead would be
     * running the music ahead. Silent and as fast as the machine allows -- 120 to 150 times real
     * time on the host, so a three-minute tune is under two seconds there and a few on a phone.
     * Stopped and joined whenever the tune closes or the subsong changes, so a measurement never
     * outlives the scratch directory it reads from.
     */
    void measure(int subsong) {
        stopMeasuring();
        measured_.store(0.0, std::memory_order_release);
        measuring_.store(true, std::memory_order_release);
        stop_.store(false, std::memory_order_release);
        measurer_ = std::thread([this, subsong] {
            struct uade_state *probe = nullptr;
            {
                // Forked under the same lock as an opening, so it cannot inherit descriptors
                // another opening has pointed at its own scratch directory for a moment.
                std::lock_guard<std::mutex> held(openMutex());
                probe = newState();
            }
            if (probe && uade_play(modulePath_.c_str(), subsong, probe) > 0) {
                std::vector<int16_t> buffer(kChunkFrames * 2);
                std::size_t frames = 0;
                bool ended = false;
                while (!stop_.load(std::memory_order_acquire) && frames < kMeasureCapFrames) {
                    const ssize_t got = uade_read(buffer.data(), buffer.size() * sizeof(int16_t), probe);
                    // Zero is the routine's own end. Negative is the emulator gone -- a crash, not
                    // an ending -- and the length at that point would be invented.
                    if (got == 0) { ended = true; break; }
                    if (got < 0) break;
                    frames += static_cast<std::size_t>(got) / (2 * sizeof(int16_t));
                }
                if (ended && frames > 0) {
                    measured_.store(static_cast<double>(frames) / kSampleRate, std::memory_order_release);
                }
            }
            if (probe) {
                uade_stop(probe);
                uade_cleanup_state(probe);
            }
            measuring_.store(false, std::memory_order_release);
        });
    }

    void stopMeasuring() {
        stop_.store(true, std::memory_order_release);
        if (measurer_.joinable()) measurer_.join();
    }

    /** A state configured exactly as the playing one is, for the measurement. */
    struct uade_state *newState() {
        const Paths paths = pathsCopy();
        struct uade_config *config = uade_new_config();
        if (!config) return nullptr;
        uade_config_set_option(config, UC_BASE_DIR, paths.base.c_str());
        uade_config_set_option(config, UC_UADECORE_FILE, paths.core.c_str());
        uade_config_set_option(config, UC_FREQUENCY, "44100");
        uade_config_set_option(config, UC_ONE_SUBSONG, nullptr);
        // **No timeouts in the measurement.** UADE ends a subsong after 512 seconds, and after
        // 20 of silence, and reports both exactly as it reports a replay routine saying "the end"
        // -- even marked as a happy ending. Measured with them on, 19 of 140 tunes came back 512.0
        // seconds long, which is the timeout and not a length (C67 is that mistake once already).
        // Off, only the routine's own end yields a length, and a tune that loops runs into this
        // class's ten-minute cap and stays unknown.
        uade_config_set_option(config, UC_DISABLE_TIMEOUTS, nullptr);
        struct uade_state *state = uade_new_state(config);
        std::free(config);
        if (state) uade_set_amiga_loader(&UadeBackend::loadAmigaFile, this, state);
        return state;
    }

    /**
     * Finds a file the emulated Amiga asks for -- TFMX's `smpl.` beside its `mdat.`.
     *
     * **UADE's own search cannot work on a phone.** `uade_find_amiga_file` matches names without
     * regard to case, as AmigaOS did, and does it by walking the path from `/` and *listing* every
     * directory on the way. An app on Android may pass through `/data` but may not list it, so the
     * walk stopped on its second step, the samples were "not found" beside the tune that had just
     * been written next to them, and the replay routine died: "score died" on the phone for a file
     * that played on the host. Reproduced on the host with a parent directory of mode 111.
     *
     * Everything a song can ask for by a plain name is in this instance's scratch directory,
     * because this class put it there, so that is the only place looked -- listed, which it may
     * be, and matched without regard to case. Names on an Amiga volume (`ENV:`, `S:`) are the
     * players' own configuration under the data directory and go to UADE's search unchanged; so
     * does anything outside the scratch directory, which nothing here puts there.
     */
    static struct uade_file *loadAmigaFile(const char *name, const char *playerdir, void *context,
                                           struct uade_state *state) {
        auto *self = static_cast<UadeBackend *>(context);
        const std::string requested = name ? name : "";
        if (requested.find(':') != std::string::npos || self->scratch_.empty()) {
            return uade_load_amiga_file(name, playerdir, state);
        }
        // The part to look up: whatever follows the scratch directory in an absolute request, or
        // the name itself in a relative one.
        std::string relative = requested;
        const std::string prefix = self->scratch_ + "/";
        if (!relative.empty() && relative[0] == '/') {
            if (relative.compare(0, prefix.size(), prefix) != 0) {
                return uade_load_amiga_file(name, playerdir, state);
            }
            relative = relative.substr(prefix.size());
        }
        std::string directory = self->scratch_;
        std::size_t start = 0;
        while (start <= relative.size()) {
            const std::size_t slash = relative.find('/', start);
            const std::string segment =
                relative.substr(start, slash == std::string::npos ? std::string::npos : slash - start);
            if (!segment.empty() && segment != ".") {
                if (segment == "..") return uade_load_amiga_file(name, playerdir, state);
                const std::string found = matchIgnoringCase(directory, segment);
                if (found.empty()) return nullptr;
                directory += "/" + found;
            }
            if (slash == std::string::npos) break;
            start = slash + 1;
        }
        return uade_file_load(directory.c_str());
    }

    /** The entry in [directory] whose name equals [wanted] ignoring case, or empty. */
    static std::string matchIgnoringCase(const std::string &directory, const std::string &wanted) {
        DIR *dir = ::opendir(directory.c_str());
        if (!dir) return {};
        std::string match;
        while (struct dirent *entry = ::readdir(dir)) {
            if (::strcasecmp(entry->d_name, wanted.c_str()) == 0) {
                match = entry->d_name;
                if (match == wanted) break;  // an exact match wins over a case-folded one
            }
        }
        ::closedir(dir);
        return match;
    }

    /**
     * Points stdout and stderr at [path] for as long as it lives, and puts them back after.
     *
     * The file is not closed early on purpose: uadecore inherited the descriptor at fork and keeps
     * writing to it while it plays, and the scratch directory's removal is what ends it.
     */
    class Captured {
    public:
        explicit Captured(const std::string &path) : path_(path) {
            std::fflush(stdout);
            std::fflush(stderr);
            const int fd = ::open(path.c_str(), O_WRONLY | O_CREAT | O_TRUNC, 0600);
            if (fd < 0) return;
            savedOut_ = ::dup(1);
            savedErr_ = ::dup(2);
            ::dup2(fd, 1);
            ::dup2(fd, 2);
            ::close(fd);
        }
        ~Captured() { restore(); }

        /** The last non-empty line written so far, trimmed; empty if nothing was said. */
        std::string lastLine() const {
            std::fflush(stdout);
            std::fflush(stderr);
            std::ifstream in(path_);
            std::string line, last;
            while (std::getline(in, line)) {
                while (!line.empty() && (line.back() == '\r' || line.back() == ' ')) line.pop_back();
                // libuade's own chatter about a config file this app does not use is not a reason.
                if (!line.empty() && line.find("uadeconfig not loaded") == std::string::npos) last = line;
            }
            return last.size() > 160 ? last.substr(0, 160) : last;
        }

    private:
        void restore() {
            std::fflush(stdout);
            std::fflush(stderr);
            if (savedOut_ >= 0) { ::dup2(savedOut_, 1); ::close(savedOut_); savedOut_ = -1; }
            if (savedErr_ >= 0) { ::dup2(savedErr_, 2); ::close(savedErr_); savedErr_ = -1; }
        }
        std::string path_;
        int savedOut_ = -1;
        int savedErr_ = -1;
    };

    static bool hasPlayers(const std::string &base) {
        DIR *dir = ::opendir((base + "/players").c_str());
        if (!dir) return false;
        bool found = false;
        while (struct dirent *entry = ::readdir(dir)) {
            if (entry->d_name[0] != '.') { found = true; break; }
        }
        ::closedir(dir);
        return found;
    }

    void makeScratch(const std::string &root) {
        ::mkdir(root.c_str(), 0700);
        std::string pattern = root + "/uadeXXXXXX";
        std::vector<char> buffer(pattern.begin(), pattern.end());
        buffer.push_back('\0');
        if (!::mkdtemp(buffer.data())) {
            throw std::runtime_error("the Amiga decoder (UADE) has nowhere to put the file");
        }
        scratch_ = buffer.data();
    }

    /**
     * Writes one file into the scratch directory and returns its path.
     *
     * **The name matters and is kept**, because for a good part of what UADE plays the filename is
     * the identification: `mdat.` and `smpl.` prefixes, `.tfx` suffixes, the conventions each
     * collection settled on. What is not kept is any directory in it -- a name arriving from an
     * archive index is somebody else's string, and one with a separator in it would write outside
     * the directory this class is responsible for.
     */
    std::string write(const std::string &name, const std::vector<char> &bytes) {
        std::string leaf = name;
        const std::size_t slash = leaf.find_last_of("/\\");
        if (slash != std::string::npos) leaf = leaf.substr(slash + 1);
        if (leaf.empty() || leaf == "." || leaf == "..") leaf = "tune";

        const std::string path = scratch_ + "/" + leaf;
        std::ofstream file(path, std::ios::binary);
        if (!file) throw std::runtime_error("the Amiga decoder (UADE) could not stage the file");
        file.write(bytes.data(), static_cast<std::streamsize>(bytes.size()));
        file.close();
        if (!file) throw std::runtime_error("the Amiga decoder (UADE) could not stage the file");
        return path;
    }

    void close() {
        stopMeasuring();
        if (state_) {
            uade_stop(state_);
            uade_cleanup_state(state_);
            state_ = nullptr;
        }
        cleanScratch();
    }

    /** Removes the scratch directory. One level deep, which is all this class ever creates. */
    void cleanScratch() {
        if (scratch_.empty()) return;
        if (DIR *dir = ::opendir(scratch_.c_str())) {
            while (struct dirent *entry = ::readdir(dir)) {
                const std::string leaf = entry->d_name;
                if (leaf == "." || leaf == "..") continue;
                ::unlink((scratch_ + "/" + leaf).c_str());
            }
            ::closedir(dir);
        }
        ::rmdir(scratch_.c_str());
        scratch_.clear();
    }

    struct uade_state *state_ = nullptr;
    struct uade_subsong_info subsongs_ = {0, 0, 0, 0};
    std::thread measurer_;
    /** What the host knew, zero-based from the first subsong; zero where it knew nothing. */
    std::vector<double> known_;
    /** Set once playback starts; before that nothing is measured, so a scan costs no emulator. */
    bool playing_ = false;
    std::atomic<double> measured_{0.0};
    std::atomic<bool> measuring_{false};
    std::atomic<bool> stop_{false};
    std::string scratch_;
    std::string modulePath_;
    std::vector<int16_t> chunk_ = std::vector<int16_t>(kChunkFrames * 2);
};
#endif  // PROTRACKTOR_WITH_UADE

}  // namespace

namespace protracktor {

namespace {

// CP437's upper half, 0x80 to 0xFF, as code points. Generated from Python's `cp437` codec rather
// than typed, so a transposed box-drawing character cannot hide in 128 hex numbers.
constexpr char32_t kCp437High[128] = {
    0x00C7, 0x00FC, 0x00E9, 0x00E2, 0x00E4, 0x00E0, 0x00E5, 0x00E7,
    0x00EA, 0x00EB, 0x00E8, 0x00EF, 0x00EE, 0x00EC, 0x00C4, 0x00C5,
    0x00C9, 0x00E6, 0x00C6, 0x00F4, 0x00F6, 0x00F2, 0x00FB, 0x00F9,
    0x00FF, 0x00D6, 0x00DC, 0x00A2, 0x00A3, 0x00A5, 0x20A7, 0x0192,
    0x00E1, 0x00ED, 0x00F3, 0x00FA, 0x00F1, 0x00D1, 0x00AA, 0x00BA,
    0x00BF, 0x2310, 0x00AC, 0x00BD, 0x00BC, 0x00A1, 0x00AB, 0x00BB,
    0x2591, 0x2592, 0x2593, 0x2502, 0x2524, 0x2561, 0x2562, 0x2556,
    0x2555, 0x2563, 0x2551, 0x2557, 0x255D, 0x255C, 0x255B, 0x2510,
    0x2514, 0x2534, 0x252C, 0x251C, 0x2500, 0x253C, 0x255E, 0x255F,
    0x255A, 0x2554, 0x2569, 0x2566, 0x2560, 0x2550, 0x256C, 0x2567,
    0x2568, 0x2564, 0x2565, 0x2559, 0x2558, 0x2552, 0x2553, 0x256B,
    0x256A, 0x2518, 0x250C, 0x2588, 0x2584, 0x258C, 0x2590, 0x2580,
    0x03B1, 0x00DF, 0x0393, 0x03C0, 0x03A3, 0x03C3, 0x00B5, 0x03C4,
    0x03A6, 0x0398, 0x03A9, 0x03B4, 0x221E, 0x03C6, 0x03B5, 0x2229,
    0x2261, 0x00B1, 0x2265, 0x2264, 0x2320, 0x2321, 0x00F7, 0x2248,
    0x00B0, 0x2219, 0x00B7, 0x221A, 0x207F, 0x00B2, 0x25A0, 0x00A0,
};

// Well-formed UTF-8 by RFC 3629: no overlong forms, no surrogates, nothing past U+10FFFF, no
// truncated sequence at the end.
bool isUtf8(const std::string &text) {
    const auto *p = reinterpret_cast<const unsigned char *>(text.data());
    const auto *end = p + text.size();
    while (p < end) {
        const unsigned char c = *p;
        if (c < 0x80) { ++p; continue; }
        int extra;
        char32_t minimum;
        char32_t cp;
        if ((c & 0xE0) == 0xC0) { extra = 1; minimum = 0x80; cp = c & 0x1F; }
        else if ((c & 0xF0) == 0xE0) { extra = 2; minimum = 0x800; cp = c & 0x0F; }
        else if ((c & 0xF8) == 0xF0) { extra = 3; minimum = 0x10000; cp = c & 0x07; }
        else return false;
        if (end - p <= extra) return false;
        for (int i = 1; i <= extra; ++i) {
            if ((p[i] & 0xC0) != 0x80) return false;
            cp = (cp << 6) | (p[i] & 0x3F);
        }
        if (cp < minimum || cp > 0x10FFFF || (cp >= 0xD800 && cp <= 0xDFFF)) return false;
        p += extra + 1;
    }
    return true;
}

void appendUtf8(std::string &out, char32_t cp) {
    if (cp < 0x80) {
        out += static_cast<char>(cp);
    } else if (cp < 0x800) {
        out += static_cast<char>(0xC0 | (cp >> 6));
        out += static_cast<char>(0x80 | (cp & 0x3F));
    } else {
        out += static_cast<char>(0xE0 | (cp >> 12));
        out += static_cast<char>(0x80 | ((cp >> 6) & 0x3F));
        out += static_cast<char>(0x80 | (cp & 0x3F));
    }
}

}  // namespace

std::string fileTextToUtf8(const std::string &raw) {
    if (isUtf8(raw)) return raw;
    const bool dos = std::any_of(raw.begin(), raw.end(), [](char c) {
        const auto b = static_cast<unsigned char>(c);
        return b >= 0x80 && b <= 0x9F;
    });
    std::string out;
    out.reserve(raw.size() * 2);
    for (const char c : raw) {
        const auto b = static_cast<unsigned char>(c);
        if (b < 0x80) out += c;
        else appendUtf8(out, dos ? kCp437High[b - 0x80] : static_cast<char32_t>(b));
    }
    return out;
}

std::string describeOf(const Backend &backend) {
    const std::string raw = backend.describe();
    std::string out;
    out.reserve(raw.size());
    std::size_t start = 0;
    while (start <= raw.size()) {
        const std::size_t newline = raw.find('\n', start);
        const std::size_t stop = newline == std::string::npos ? raw.size() : newline;
        out += fileTextToUtf8(raw.substr(start, stop - start));
        if (newline == std::string::npos) break;
        out += '\n';
        start = newline + 1;
    }
    return out;
}

std::string backendsFingerprint() {
    std::ostringstream o;
    o << "openmpt:" << openmpt::string::get("library_version")
      << ";sc68:" << sc68_versionstr()
      << ";asap:" << ASAPInfo_VERSION
      // game-music-emu publishes a packed integer rather than a string.
      << ";gme:" << ((GME_VERSION >> 16) & 0xff) << '.'
                 << ((GME_VERSION >> 8) & 0xff) << '.'
                 << (GME_VERSION & 0xff)
      << ";sidplayfp:" << LIBSIDPLAYFP_VERSION_MAJ << '.'
                       << LIBSIDPLAYFP_VERSION_MIN << '.'
                       << LIBSIDPLAYFP_VERSION_LEV
      // minimp3 publishes no version at all -- no macro, no function, no releases. The pin in
      // `scripts/fetch-native-deps.sh` is the version, so that is what this says.
      << ";minimp3:ea99364";
#if !PROTRACKTOR_WITH_ZXTUNE
    // Appended only when the decoder is absent, and that asymmetry is deliberate: this string is
    // what tells a stored index it was built by a different set, so adding anything to the Android
    // build's version would invalidate every index on every device at once.
    o << ";zxtune:none";
#endif
#if !PROTRACKTOR_WITH_UADE
    // The same rule for UADE, and here the absent side is the browser: it has no `fork`, so it can
    // never have this backend and its index has to know. The phone's fingerprint is unchanged by
    // UADE arriving, which is what keeps a stored index valid on the day it does.
    o << ";uade:none";
#endif
    return o.str();
}

void setSharedDataPath(const std::string &path) { Sc68Backend::setSharedDataPath(path); }

void setUadePaths(const std::string &coreFile, const std::string &baseDir,
                  const std::string &scratchDir) {
#if PROTRACKTOR_WITH_UADE
    UadeBackend::setPaths(coreFile, baseDir, scratchDir);
#else
    (void) coreFile;
    (void) baseDir;
    (void) scratchDir;
#endif
}

std::unique_ptr<Backend> openBackend(std::vector<char> bytes, const std::string &name,
                                     std::string &error, std::vector<Companion> companions) {
    error.clear();

    // **The first refusal is the one kept, and every `error =` below says so.**
    //
    // The backends are asked in order of how strongly they can claim a file -- by name, then by
    // magic, then by content, then by trying -- so the first one to refuse is by construction the
    // one that had the best claim, and its reason is the true one. Overwriting it with a later
    // backend's reason reports a decoder that had no business with the file: a `.sap` claimed by
    // name and refused by ASAP was described by game-music-emu on its way past
    // (`docs/STATUS.md` C55).

    // MP3 first when the name says so. It shares the reason ASAP goes early -- the name is the
    // only reliable thing about this format -- and nothing else here claims `.mp3`.
    if (Mp3Backend::claimsName(name)) {
        try {
            return std::make_unique<Mp3Backend>(bytes);
        } catch (const std::exception &e) {
            LOGE("minimp3 claimed the name but refused: %s", e.what());
            if (error.empty()) error = e.what();
        }
    }

    // ASAP first when the name is one of its fourteen: several of its formats are told apart by
    // extension rather than by any header, so nothing else can make that call.
    if (AsapBackend::claimsName(name)) {
        try {
            return std::make_unique<AsapBackend>(bytes, name);
        } catch (const std::exception &e) {
            LOGE("ASAP claimed the name but refused: %s", e.what());
            if (error.empty()) error = e.what();
        }
    }

    // SID first among the content-identified ones: its magic is four unambiguous bytes at offset
    // zero, which is as certain as identification gets.
    if (SidBackend::recognises(bytes)) {
        try {
            return std::make_unique<SidBackend>(bytes);
        } catch (const std::exception &e) {
            LOGE("libsidplayfp refused it: %s", e.what());
            if (error.empty()) error = e.what();
        }
    }

    // HivelyTracker next. "THX" and "HVL" plus a version byte at offset zero are as unambiguous as
    // SID's, and nothing else here loads either format, so a match cannot be a stray claim.
    if (HivelyBackend::recognises(bytes)) {
        try {
            return std::make_unique<HivelyBackend>(bytes);
        } catch (const std::exception &e) {
            LOGE("HivelyTracker recognised the header but refused: %s", e.what());
            if (error.empty()) error = e.what();
        }
    }

    // game-music-emu next, because it is the only backend that identifies by content rather than by
    // name or by trying: a header check that reads the bytes cannot claim something that is not its.
    if (GmeBackend::recognises(bytes)) {
        try {
            return std::make_unique<GmeBackend>(bytes);
        } catch (const std::exception &e) {
            LOGE("gme recognised the header but refused: %s", e.what());
            if (error.empty()) error = e.what();
        }
    }

    // ZXTune next, and by name rather than by content: `.stc` and `.pt2` carry no signature worth
    // testing, so ZXTune's decoders check structure and recognition *is* loading. That makes it a
    // backend to ask late -- after everything that can identify a file from a header -- and only
    // about names no other backend claims.
#if PROTRACKTOR_WITH_ZXTUNE
    if (ZxTuneBackend::worthTrying(bytes, name)) {
        try {
            return std::make_unique<ZxTuneBackend>(bytes);
        } catch (const std::exception &e) {
            LOGE("ZXTune refused it: %s", e.what());
            if (error.empty()) error = e.what();
        }
    }
#endif

    // sc68 asked first. Its answer is the load succeeding, not a verify -- see worthTrying. If it
    // refuses, we fall through to libopenmpt, whose format net is wide enough that letting it go
    // first would risk a stray claim on something sc68 should have had.
    if (Sc68Backend::worthTrying(bytes)) {
        try {
            return std::make_unique<Sc68Backend>(bytes);
        } catch (const std::exception &e) {
            LOGE("sc68 recognised but refused: %s", e.what());
            if (error.empty()) error = e.what();
        }
    }
    try {
        return std::make_unique<OpenmptBackend>(bytes);
    } catch (const std::exception &e) {
        LOGE("libopenmpt refused: %s", e.what());

        // **MP3 by content, and only here.** Everything above can prove what it is holding, and
        // libopenmpt's net is the widest of them -- so a file that has got this far is one nothing
        // recognised, and minimp3's guess costs nothing to try. Asked earlier it stole an Impulse
        // Tracker module from libopenmpt on the strength of a byte pattern in its samples
        // (`docs/STATUS.md` C32).
        if (Mp3Backend::recognises(bytes)) {
            try {
                return std::make_unique<Mp3Backend>(bytes);
            } catch (const std::exception &mp3) {
                LOGE("minimp3 thought it was an MP3 and refused: %s", mp3.what());
            }
        }

#if PROTRACKTOR_WITH_UADE
        // **UADE last of all, and only when it has been set up.**
        //
        // Last because everything above can say what it is holding and UADE mostly cannot: its
        // strongest claims are filename conventions, and libopenmpt already plays a good part of
        // what it would otherwise take -- OctaMED and Oktalyzer are 5,558 Modland files that are
        // *already* ours (`docs/PLAN_FORMATS.md` §4). Asking it here rather than earlier means
        // nothing that plays today plays differently.
        //
        // And only when set up, because the replay routines are downloaded rather than shipped. On
        // a phone that has not downloaded them this costs one `access` and the formats are simply
        // absent, which is what the index and Browse have to say as well.
        if (UadeBackend::available()) {
            try {
                return std::make_unique<UadeBackend>(bytes, name, companions);
            } catch (const std::exception &uade) {
                LOGE("UADE refused it: %s", uade.what());
                if (error.empty()) error = uade.what();
            }
        }
#else
        (void) companions;
#endif

        if (error.empty()) {
            // The last backend's reason, given the same shape as the other five. libopenmpt throws
            // its own exception, so unlike them the sentence cannot be written at the throw site --
            // and "libopenmpt: ..." on its own would be the only message here naming a library with
            // no platform beside it.
            //
            // "No backend recognised it" is the caller's conclusion to draw, not this one's:
            // `OpenFailure` draws it from whether the name was claimed at all, which it knows and
            // this does not.
            error = std::string("the tracker decoder (libopenmpt) refused it: ") + e.what();
        }
    }
    return nullptr;
}

}  // namespace protracktor
