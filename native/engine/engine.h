// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later

// The part of the engine that is not Android's: what a decoder looks like, and how one is chosen.
//
// **This header exists because the audio host is not the only one there will be.** `engine.cpp`
// holds seven decoders, a registry and the dispatch that picks between them, and none of that knows
// what a phone is; `player_oboe.cpp` holds the one class that talks to Oboe and the fifteen JNI
// functions above it. Splitting them along this line was measured before it was done: of 1,868
// lines, the Android-bound part was one class and one `extern "C"` block.
//
// `docs/PLAN_WEB.md` §13 S0 is why it happened now. A second host -- an `AudioWorkletProcessor`
// with the same shape as `onAudioReady` -- can include this and link the same backends, and the
// alternative was forking the engine, which is a worse project.

#pragma once

#include <cstddef>
#include <memory>
#include <string>
#include <vector>

namespace protracktor {


/**
 * One decoder, seen the same way whatever it is underneath.
 *
 * Everything the player needs to know is asked, not assumed -- especially [canSeek]. libopenmpt can
 * move to a position; sc68 emulates a 68000 and cannot, because there is no way back except running
 * the machine again from the start. A UI that offers a control the backend cannot honour is a UI
 * that lies (docs/ARCHITECTURE.md §5).
 */
class Backend {
public:
    virtual ~Backend() = default;

    /** Renders interleaved stereo floats. Returns frames produced; fewer than asked means the end. */
    virtual std::size_t render(int sampleRate, std::size_t frames, float *out) = 0;

    virtual bool canSeek() const = 0;
    virtual void seek(double seconds) = 0;
    virtual void rewind() = 0;
    virtual double positionSeconds() const = 0;
    virtual double durationSeconds() const = 0;
    virtual std::string describe() const = 0;

    /** 0 means "whatever the device prefers". Non-zero backends are resampled by Oboe if need be. */
    virtual int preferredSampleRate() const { return 0; }

    /** How many tunes are inside. One for a format that holds one. */
    virtual int subsongCount() const { return 1; }

    /**
     * Plays subsong [index], counted from **zero**.
     *
     * Zero-based at this boundary whatever the library underneath does -- sc68 numbers its tracks
     * from one, and letting that leak upwards would put an off-by-one in every caller instead of in
     * one conversion here. Returns whether the switch happened.
     */
    virtual bool selectSubsong(int index) { return index == 0; }

    /**
     * Which subsong is playing, zero-based.
     *
     * Almost always zero, and `GmeBackend` is why this exists at all: HES and KSS files routinely
     * hold nothing at track 0, so it opens at the first track with sound in it and the rest of the
     * app has to be told where that was.
     */
    virtual int currentSubsong() const { return 0; }
};

/**
 * Opens [bytes] with whichever backend claims them, or returns null and fills [error].
 *
 * The order matters and is documented at the definition: name-claimed formats first, then content
 * magic, then the general trackers, because several formats are told apart only by extension and
 * several others only by their first four bytes.
 */
std::unique_ptr<Backend> openBackend(std::vector<char> bytes, const std::string &name,
                                     std::string &error);

/**
 * Which decoders this build carries, and at which versions.
 *
 * An index is filtered at build time to what the backends can play, so a stored index is only as
 * good as the set that produced it -- replacing sc68 2.2.1 with 3.0.0b took `.sndh` from 14 of 30
 * to 30 of 30, and every "cannot play this" the old set wrote down became wrong that day. Read from
 * the libraries themselves so a dependency bump cannot leave a hand-written string behind.
 *
 * **On this side of the line rather than the host's** -- the version of libopenmpt is a fact about
 * the decoders, and a second host would otherwise have to include seven headers to repeat it.
 */
std::string backendsFingerprint();

/**
 * Where sc68 should look for the replay routines the app does not ship (`docs/LICENSES.md`).
 *
 * Also an engine fact rather than a host one, though what it points at differs: a directory on
 * Android, and whatever a browser build decides to call its storage.
 */
void setSharedDataPath(const std::string &path);

}  // namespace protracktor
