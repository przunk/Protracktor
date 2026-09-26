// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-2.0-or-later
#ifndef PROTRACKTOR_ASAP_MEASURE_H
#define PROTRACKTOR_ASAP_MEASURE_H

#include <stdbool.h>
#include <stdint.h>

#ifdef __cplusplus
extern "C" {
#endif

/**
 * Where subsong [song] of a SAP really ends, found as ASAP's own `asapscan -t` finds it: by running
 * the tune without sound and watching POKEY -- five seconds of silence end it, and three minutes of
 * registers repeating what was already played end one pass of a loop. At most [scanSeconds] are run.
 *
 * Answers the length in milliseconds, or -1 when neither was found within the scan, when the
 * registers stand still (`asapscan`'s "ultrasound": music played between frames, which this cannot
 * see, and which it would report as a length of nothing), when [cancelled] says stop, or when the
 * file would not load. [loop] says which of the two it was. Thread-safe: every call has its own
 * ASAP and its own buffers.
 *
 * [cancelled] is asked every few hundred frames, with [context]; null never cancels. A measurement
 * runs for seconds on a phone, and a tune skipped meanwhile must not wait for it to finish.
 */
int ProtracktorAsap_MeasureMs(const char *filename, const uint8_t *module, int moduleLen, int song,
                              int scanSeconds, bool *loop, bool (*cancelled)(void *), void *context);

/**
 * Whether the host should go on asking for a length, given what the measurement has said so far:
 * [measuredSubsong] is the subsong last measured (-1 before any), [measuredMs] what it found (0 for
 * nothing), [playing] the subsong playing now.
 *
 * **Yes until the host has taken a length that exists.** The host asks only while this says yes,
 * and stops once it holds a length -- so saying no the moment a measurement lands, as the first
 * version did, stopped the asking exactly when there was something to take, and the length never
 * reached the screen (the owner, 2026-09-27: Bonio.sap still showed `~`). No only when the playing
 * subsong was measured and nothing was found.
 */
bool ProtracktorAsap_LengthStillComing(int measuredSubsong, int measuredMs, int playing);

#ifdef __cplusplus
}
#endif

#endif
