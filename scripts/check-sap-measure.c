// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-2.0-or-later
//
// The SAP length measurement (`docs/STATUS.md` C91), on two SAPs built here from a few dozen bytes
// of 6502 rather than stored: one that plays a tone for 512 frames and falls silent, one with a
// 1,024-frame intro and then a pattern repeating every 256 frames. Built and run on the host by
// `check-sap-measure.sh`; the phone compiles the same `asap_measure.c`.

#include <stdio.h>
#include <string.h>

#include "asap_measure.h"

static int failed = 0;
static bool always(void *context) { (void) context; return true; }
static void check(const char *what, int ok, const char *detail)
{
    if (ok) printf("✓ %s\n", what);
    else { failed++; printf("✗ %s\n    %s\n", what, detail); }
}

/** A type-B SAP: the header, then one binary block at $2000 with INIT at $2000 and PLAYER at $2010. */
static int sap(unsigned char *out, const unsigned char *code, int codeLength)
{
    const char *header = "SAP\r\nAUTHOR \"Check\"\r\nNAME \"Check\"\r\nTYPE B\r\nINIT 2000\r\nPLAYER 2010\r\n";
    int n = (int) strlen(header);
    memcpy(out, header, n);
    const int end = 0x2000 + codeLength - 1;
    unsigned char block[] = { 0xff, 0xff, 0x00, 0x20, (unsigned char) (end & 0xff), (unsigned char) (end >> 8) };
    memcpy(out + n, block, sizeof block);
    memcpy(out + n + sizeof block, code, codeLength);
    return n + (int) sizeof block + codeLength;
}

/* INIT: zero a 16-bit frame counter at $3000. PLAYER: count the frame, then the tune's own part. */
#define INIT 0xa9, 0x00, 0x8d, 0x00, 0x30, 0x8d, 0x01, 0x30, 0x60, 0, 0, 0, 0, 0, 0, 0
#define COUNT 0xee, 0x00, 0x30, 0xd0, 0x03, 0xee, 0x01, 0x30

/** A PAL frame, in milliseconds: 312 lines of 114 cycles at 1,773,447 Hz. */
static double framesToMs(int frames) { return frames * 1000.0 * 312 * 114 / 1773447; }

int main(void)
{
    static unsigned char file[256];
    char detail[160];

    // A tone until the counter's high byte reaches 2 -- 512 frames -- then silence.
    const unsigned char falls[] = {
        INIT, COUNT,
        0xad, 0x01, 0x30,       // LDA $3001
        0xc9, 0x02,             // CMP #2
        0xb0, 0x0b,             // BCS silent
        0xa9, 0x40, 0x8d, 0x00, 0xd2,   // AUDF1 = $40
        0xa9, 0xaf, 0x8d, 0x01, 0xd2,   // AUDC1 = pure tone, volume 15
        0x60,
        0xa9, 0x00, 0x8d, 0x01, 0xd2,   // silent: AUDC1 = 0
        0x60,
    };
    int length = sap(file, falls, sizeof falls);
    bool loop = true;
    int ms = ProtracktorAsap_MeasureMs("falls.sap", file, length, 0, 15 * 60, &loop, NULL, NULL);
    snprintf(detail, sizeof detail, "%d ms, loop %d; 512 frames are %.0f ms", ms, loop, framesToMs(512));
    check("a tune that falls silent ends where the silence starts, to a frame", !loop && ms > 0
          && ms >= framesToMs(510) && ms <= framesToMs(514), detail);

    // An intro of 1,024 frames at a lower volume, then AUDF1 following the counter's low byte: a
    // pattern that repeats every 256 frames (5.1 s, past the five-second minimum) for ever.
    const unsigned char loops[] = {
        INIT, COUNT,
        0xad, 0x00, 0x30, 0x8d, 0x00, 0xd2,     // AUDF1 = counter, low byte
        0xad, 0x01, 0x30,                       // LDA $3001
        0xc9, 0x04,                             // CMP #4
        0xb0, 0x06,                             // BCS loud
        0xa9, 0xa8, 0x8d, 0x01, 0xd2,           // intro: volume 8
        0x60,
        0xa9, 0xaf, 0x8d, 0x01, 0xd2,           // after it: volume 15
        0x60,
    };
    length = sap(file, loops, sizeof loops);
    loop = false;
    ms = ProtracktorAsap_MeasureMs("loops.sap", file, length, 0, 15 * 60, &loop, NULL, NULL);
    snprintf(detail, sizeof detail, "%d ms, loop %d; the intro and one pass are 1,280 frames, %.0f ms", ms, loop, framesToMs(1280));
    check("a tune that loops ends after its intro and one pass of the loop", loop
          && ms >= framesToMs(1278) && ms <= framesToMs(1282), detail);

    // Asked to stop, it stops, and says nothing rather than something half-measured.
    ms = ProtracktorAsap_MeasureMs("loops.sap", file, length, 0, 15 * 60, &loop, always, NULL);
    check("a cancelled measurement answers nothing", ms == -1, "it answered a length");

    // A file that is not a SAP is no answer, not a crash.
    ms = ProtracktorAsap_MeasureMs("junk.sap", (const unsigned char *) "SAP\r\nnonsense", 13, 0, 60, &loop, NULL, NULL);
    check("a file that is not a tune is no answer", ms == -1, "it answered a length");

    return failed ? 1 : 0;
}
