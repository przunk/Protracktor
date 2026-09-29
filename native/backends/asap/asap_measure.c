// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-2.0-or-later
//
// ASAP, plus one function: measuring a SAP that states no `TIME` (`docs/STATUS.md` C91).
//
// **This file is compiled in `asap.c`'s place, and includes it.** `asapscan`, the tool ASAP's and
// ASMA's maintainers set `TIME` lines with, reads POKEY's registers and steps the 6502 a frame at a
// time -- internals `asap.h` does not expose, which `asapscan` gets from a build of ASAP generated
// for it. Including the one transpiled file makes them visible to the function below without
// copying or changing a line of ASAP, and one translation unit defines everything exactly once.
//
// The detection is `asapscan.c`'s `scan_song` (ASAP 7, GPL-2.0-or-later, Piotr Fusik), with its
// globals made local so two measurements can run at once, and the printing, fingerprinting and
// feature listing left out.

#include "asap.c"

#include "asap_measure.h"

#include <stdlib.h>
#include <string.h>

#define MEASURE_HASH_BITS 8
#define MEASURE_SILENCE_SECONDS 5
#define MEASURE_LOOP_CHECK_SECONDS (3 * 60)
#define MEASURE_LOOP_MIN_SECONDS 5

typedef struct {
    ASAP *asap;
    unsigned char *registers;   /* 18 bytes a frame: both POKEYs' AUDF, AUDC and AUDCTL */
    int *hashNext;
    int hashFirst[1 << MEASURE_HASH_BITS];
    int hashLast[1 << MEASURE_HASH_BITS];
    int loopCheckFrames;
} Measure;

static int Measure_CyclesPerFrame(const Measure *m) { return m->asap->moduleInfo.ntsc ? 262 * 114 : 312 * 114; }
static int Measure_MainClock(const Measure *m) { return m->asap->moduleInfo.ntsc ? 1789772 : 1773447; }

static int Measure_SecondsToFrames(const Measure *m, int seconds)
{
    return (int) ((double) seconds * Measure_MainClock(m) / Measure_CyclesPerFrame(m));
}

static int Measure_FramesToMs(const Measure *m, int frames)
{
    return (int) ceil(frames * 1000.0 * Measure_CyclesPerFrame(m) / Measure_MainClock(m));
}

static bool Measure_StorePokey(unsigned char *p, const Pokey *pokey)
{
    bool silence = true;
    for (int i = 0; i < 4; i++) {
        if ((pokey->channels[i].audc & 0xf) != 0) {
            silence = false;
            p[i * 2] = (unsigned char) pokey->channels[i].audf;
            p[i * 2 + 1] = (unsigned char) pokey->channels[i].audc;
        }
        else {
            p[i * 2] = 0;
            p[i * 2 + 1] = 0;
        }
    }
    p[8] = (unsigned char) pokey->audctl;
    return silence;
}

static bool Measure_StorePokeys(Measure *m, int frame)
{
    unsigned char *p = m->registers + 18 * frame;
    bool silence = Measure_StorePokey(p, &m->asap->pokeys.basePokey);
    silence &= Measure_StorePokey(p + 9, &m->asap->pokeys.extraPokey);
    return silence;
}

static bool Measure_HasLoopAt(const Measure *m, int first, int second)
{
    return memcmp(m->registers + 18 * first, m->registers + 18 * second, 18 * (size_t) m->loopCheckFrames) == 0;
}

static int Measure_Hash(Measure *m, int frame)
{
    unsigned char *r = m->registers + 18 * frame;
    for (int i = 1; i < 9; i += 2) {
        if ((r[i] & 0xe0) == 0xe0)
            r[i] &= 0xbf;
        if ((r[i + 9] & 0xe0) == 0xe0)
            r[i + 9] &= 0xbf;
    }
    int hash = 0;
    for (int i = 0; i < 18; i++)
        hash += r[i];
    return hash;
}

int ProtracktorAsap_MeasureMs(const char *filename, const uint8_t *module, int moduleLen, int song,
                              int scanSeconds, bool *loop, bool (*cancelled)(void *), void *context)
{
    if (loop != NULL)
        *loop = false;
    Measure m;
    memset(&m, 0, sizeof m);
    m.asap = ASAP_New();
    if (m.asap == NULL)
        return -1;
    int result = -1;
    if (!ASAP_Load(m.asap, filename, module, moduleLen) || !ASAP_PlaySong(m.asap, song, -1)) {
        ASAP_Delete(m.asap);
        return -1;
    }
    const int scanFrames = Measure_SecondsToFrames(&m, scanSeconds);
    const int silenceFrames = Measure_SecondsToFrames(&m, MEASURE_SILENCE_SECONDS);
    const int loopMinFrames = Measure_SecondsToFrames(&m, MEASURE_LOOP_MIN_SECONDS);
    m.loopCheckFrames = Measure_SecondsToFrames(&m, MEASURE_LOOP_CHECK_SECONDS);
    m.registers = malloc((size_t) scanFrames * 18);
    m.hashNext = malloc((size_t) scanFrames * sizeof m.hashNext[0]);
    if (m.registers == NULL || m.hashNext == NULL)
        goto done;
    for (int i = 0; i < 1 << MEASURE_HASH_BITS; i++)
        m.hashFirst[i] = -1;

    int silenceRun = 0;
    int runningHash = 0;
    for (int frame = 0; frame < scanFrames; frame++) {
        if (cancelled != NULL && (frame & 255) == 0 && cancelled(context))
            goto done;
        ASAP_Do6502Frame(m.asap);
        if (Measure_StorePokeys(&m, frame)) {
            silenceRun++;
            /* not at the initial silence */
            if (silenceRun >= silenceFrames && silenceRun < frame) {
                result = Measure_FramesToMs(&m, frame + 1 - silenceRun);
                goto done;
            }
        }
        else
            silenceRun = 0;
        if (frame >= m.loopCheckFrames) {
            const int second = frame - m.loopCheckFrames;
            runningHash &= (1 << MEASURE_HASH_BITS) - 1;
            for (int first = m.hashFirst[runningHash]; first >= 0; first = m.hashNext[first]) {
                if (Measure_HasLoopAt(&m, first, second)) {
                    const int loopLength = second - first;
                    if (loopLength >= loopMinFrames) {
                        result = Measure_FramesToMs(&m, second);
                        if (loop != NULL)
                            *loop = true;
                        goto done;
                    }
                    if (loopLength == 1) {
                        /* POKEY registers do not change -- probably an ultrasound. `asapscan`
                           reports where they stopped, nearly always the start; across ASMA that
                           was under a second for 205 subsongs whose TIME says minutes, so here it
                           is no answer rather than a wrong one. */
                        goto done;
                    }
                }
            }
            if (m.hashFirst[runningHash] >= 0)
                m.hashNext[m.hashLast[runningHash]] = second;
            else
                m.hashFirst[runningHash] = second;
            m.hashNext[second] = -1;
            m.hashLast[runningHash] = second;
            runningHash -= Measure_Hash(&m, second);
        }
        runningHash += Measure_Hash(&m, frame);
    }

done:
    free(m.registers);
    free(m.hashNext);
    ASAP_Delete(m.asap);
    return result;
}

bool ProtracktorAsap_LengthStillComing(int measuredSubsong, int measuredMs, int playing)
{
    return measuredSubsong != playing || measuredMs > 0;
}
