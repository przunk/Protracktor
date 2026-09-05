/* Does HivelyTracker's replayer play the AHX and HVL files we removed from the app?
 *
 * `SupportedFormats.kt` claimed `ahx` and `hvl` until 2026-09-04, when they were removed because
 * nothing in the app could play them — libopenmpt does not, and neither does anything else we
 * vendor. That is roughly 1,433 Modland files the app used to list and now does not, and it is the
 * one gap in the format table that is our own doing rather than a decision about scope.
 *
 * HivelyTracker's `hvl2wav/` is a standalone replayer: `replay.c`, `replay.h`, `types.h`, BSD
 * 3-Clause. This probe asks the questions `Backend` will have to answer, not "does it make noise":
 *
 *   1. **Does it load from a buffer?** The app never has a path — SAF hands it bytes. `hvl_reset`
 *      is the buffer entry point and dispatches AHX ("THX") and HVL itself, so the header gate the
 *      backend needs is already there and is used here rather than restated.
 *   2. **How many subsongs?** `ht_SubsongNr`. The app's subsong model is the one part of the
 *      player that has been wrong twice, so the count is measured, not assumed.
 *   3. **Does the tune ever end?** `ht_SongEndReached`. Without it a file plays until the user
 *      stops it, which is what `gme` did before it got a fade.
 *   4. **Is it audible?** Peak sample over the render, because "loads" is not "plays" — that was
 *      the whole lesson of the gme probe, where track 0 of a KSS is often an empty slot.
 *
 * One further thing this build is looking for. `types.h` typedefs `uint32` to `unsigned long`,
 * which is 32 bits on the Amiga this came from and **64 bits on every target we ship**. If any of
 * the replayer's arithmetic depends on wrapping at 32 bits, it breaks there and not here — so the
 * probe is built both ways (see `build-hively-probe.sh`) and the verdicts compared.
 *
 * Prints one verdict per file:
 *   VERDICT full subsongs=N ends=yes|no len=NNNs peak=NNNNN version=N title="..."
 *   VERDICT silent subsongs=N ...  |  VERDICT reject:<reason>  |  VERDICT unsupported
 *
 * `len` is how far it got before `ht_SongEndReached`, or the whole window if it never did -- which
 * is the number that decides whether the app can show a duration or has to invent one.
 *
 * Build: ./scripts/build-hively-probe.sh
 */
#include <stdio.h>
#include <stdlib.h>
#include <string.h>

#include "types.h"
#include "replay.h"

/* The engine's rate. The replayer emits one PAL frame per call, so the buffer size is its choice,
 * not ours -- which is itself a finding: the backend will need a ring buffer, unlike the other five. */
enum { RATE = 44100, DEFAULT_SECONDS = 8 };

static int seconds = DEFAULT_SECONDS;

static int8 frame[(RATE * 2 * 2) / 50];

/** Renders one subsong for a moment and reports its loudest sample, or -1 if it will not start. */
static long peakOfSubsong(struct hvl_tune *ht, int subsong, int limit, int *ended, long *played) {
    if (!hvl_InitSubsong(ht, (uint32) subsong)) return -1;
    ht->ht_SongEndReached = 0;

    const long frames = (long) limit * 50;
    long peak = 0;
    long f = 0;
    for (; f < frames; f++) {
        if (ht->ht_SongEndReached) { if (ended) *ended = 1; break; }
        hvl_DecodeFrame(ht, frame, &frame[2], 4);
        const short *samples = (const short *) frame;
        for (size_t i = 0; i < sizeof(frame) / sizeof(short); i++) {
            const long v = samples[i] < 0 ? -(long) samples[i] : samples[i];
            if (v > peak) peak = v;
        }
    }
    if (played) *played = f;                      /* PAL frames, so 50 to the second */
    return peak;
}

/** The title with quotes and control characters removed, so one verdict stays one line. */
static void printTitle(const char *name) {
    for (const unsigned char *p = (const unsigned char *) name; *p && p - (const unsigned char *) name < 64; p++)
        putchar(*p < 0x20 || *p == '"' ? ' ' : *p);
}

int main(int argc, char **argv) {
    int allSubsongs = 0;
    while (argc > 1 && argv[1][0] == '-') {
        if (strcmp(argv[1], "--subsongs") == 0) { allSubsongs = 1; argv++; argc--; }
        else if (strcmp(argv[1], "--seconds") == 0 && argc > 2) { seconds = atoi(argv[2]); argv += 2; argc -= 2; }
        else { fprintf(stderr, "usage: probe-hively [--subsongs] [--seconds N] FILE\n"); return 2; }
    }
    if (argc < 2) { fprintf(stderr, "usage: probe-hively [--subsongs] [--seconds N] FILE\n"); return 2; }

    FILE *file = fopen(argv[1], "rb");
    if (!file) { puts("VERDICT unreadable"); return 2; }
    fseek(file, 0, SEEK_END);
    const long length = ftell(file);
    fseek(file, 0, SEEK_SET);
    uint8 *bytes = length > 0 ? malloc((size_t) length) : NULL;
    if (!bytes || fread(bytes, 1, (size_t) length, file) != (size_t) length) {
        fclose(file); free(bytes); puts("VERDICT unreadable"); return 2;
    }
    fclose(file);

    /* The same gate `hvl_reset` applies, checked here so a rejection is a verdict and not a
     * "Invalid file." on stdout from inside the library. */
    if (length < 16 ||
        !((bytes[0] == 'T' && bytes[1] == 'H' && bytes[2] == 'X' && bytes[3] < 3) ||
          (bytes[0] == 'H' && bytes[1] == 'V' && bytes[2] == 'L' && bytes[3] < 2))) {
        free(bytes);
        puts("VERDICT unsupported");
        return 1;
    }

    hvl_InitReplayer();

    /* freeit = 0: the buffer stays ours, which is the ownership the backend wants. */
    struct hvl_tune *ht = hvl_reset(bytes, (uint32) length, 4, RATE, 0);
    if (!ht) { free(bytes); puts("VERDICT reject:load"); return 1; }

    const int subsongs = (int) ht->ht_SubsongNr + 1;   /* ht_SubsongNr counts the extras */
    int ended = 0;
    long played = 0;
    const long peak = peakOfSubsong(ht, 0, seconds, &ended, &played);

    if (peak < 0) {
        printf("VERDICT reject:subsong0 subsongs=%d\n", subsongs);
    } else {
        printf("VERDICT %s subsongs=%d ends=%s len=%lds peak=%ld version=%d title=\"",
               peak > 0 ? "full" : "silent", subsongs, ended ? "yes" : "no",
               played / 50, peak, (int) ht->ht_Version);
        printTitle(ht->ht_Name);
        puts("\"");
    }

    if (allSubsongs) {
        for (int s = 0; s < subsongs; s++) {
            int subEnded = 0;
            long subPlayed = 0;
            const long p = peakOfSubsong(ht, s, 2, &subEnded, &subPlayed);
            printf("  subsong %d peak=%ld ends=%s\n", s, p, subEnded ? "yes" : "no");
        }
    }

    hvl_FreeTune(ht);
    free(bytes);
    return 0;
}
