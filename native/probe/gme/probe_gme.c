/* Does game-music-emu actually play what we claim it plays?
 *
 * Seven console families — NSF, GBS, SPC, VGM/VGZ, HES, AY, KSS — about 80,000 Modland files, and
 * `docs/PLAN_FORMATS.md` §2 records how the library was built and **not one measurement of whether
 * it works**. sc68 has 30 of 30, libsidplayfp 30 of 30, ASAP 12 of 12; this had build notes. It is
 * also the only part of the app nobody has ever heard, so it goes into a store listing on trust.
 *
 * This mirrors `GmeBackend`'s *contract* rather than its purpose, the way `probe_render.c` does for
 * sc68: the question is not "is there audio eventually" but the two things the player depends on —
 *
 *   1. **Is the first buffer full?** The engine treats a short render as end-of-tune, silences the
 *      rest and stops the stream. That is R1, which passed a probe that looped.
 *   2. **Does the track ever end?** `gme_set_fade` is what makes `gme_track_ended` become true, and
 *      the backend sets it once, from track 0's length. A file whose tracks differ, or that states
 *      no length, is the interesting case.
 *
 * Prints one verdict per file:
 *   full  tracks=N ends=yes|no|... peak=... fmt="..." — plays, first buffer full
 *   short:N | silent | reject:<reason> | unsupported
 *
 * Build: ./scripts/build-gme-probe.sh
 */
#include <gme/gme.h>

#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <time.h>

/* The engine's rate and buffer, so a short pass here is a short pass there. */
enum { RATE = 44100, FRAMES = 1024, SECONDS = 8 };

static short scratch[FRAMES * 2];

/**
 * Renders one track for a moment and reports its loudest sample.
 *
 * Used by `--tracks`, which exists because "silent" on its own is not a diagnosis. Many KSS and HES
 * files put a sound-effect bank or an empty slot at track 0, and the app starts every file there.
 * Knowing whether the *file* is silent or only its first track is the difference between "this
 * format does not work" and "we open these at the wrong place".
 */
static long peakOfTrack(Music_Emu *emu, int track, int seconds) {
    if (gme_start_track(emu, track)) return -1;
    gme_info_t *ti = NULL;
    gme_track_info(emu, &ti, track);
    if (ti && ti->play_length > 0) gme_set_fade(emu, ti->play_length);
    if (ti) gme_free_info(ti);

    long peak = 0;
    for (long f = 0; f < (long) RATE * seconds; f += FRAMES) {
        if (gme_track_ended(emu)) break;
        if (gme_play(emu, FRAMES * 2, scratch)) break;
        for (int i = 0; i < FRAMES * 2; i++) {
            const long v = scratch[i] < 0 ? -scratch[i] : scratch[i];
            if (v > peak) peak = v;
        }
    }
    return peak;
}

int main(int argc, char **argv) {
    if (argc < 2) { fprintf(stderr, "usage: probe-gme [--tracks] FILE\n"); return 2; }
    int allTracks = 0;
    if (strcmp(argv[1], "--tracks") == 0) { allTracks = 1; argv++; argc--; }
    if (argc < 2) { fprintf(stderr, "usage: probe-gme [--tracks] FILE\n"); return 2; }

    FILE *file = fopen(argv[1], "rb");
    if (!file) { puts("VERDICT unreadable"); return 2; }
    fseek(file, 0, SEEK_END);
    const long length = ftell(file);
    fseek(file, 0, SEEK_SET);
    char *bytes = length > 0 ? malloc((size_t) length) : NULL;
    if (!bytes || fread(bytes, 1, (size_t) length, file) != (size_t) length) {
        fclose(file); free(bytes); puts("VERDICT unreadable"); return 2;
    }
    fclose(file);

    /* The same gate the backend uses before it will even try. */
    if (length < 16 || !gme_identify_header(bytes)[0]) {
        free(bytes); puts("VERDICT unsupported"); return 1;
    }

    Music_Emu *emu = NULL;
    gme_err_t err = gme_open_data(bytes, length, &emu, RATE);
    free(bytes);
    if (err || !emu) { printf("VERDICT reject:%s\n", err ? err : "null"); return 1; }

    gme_info_t *info = NULL;
    gme_track_info(emu, &info, 0);
    if ((err = gme_start_track(emu, 0))) {
        printf("VERDICT reject:%s\n", err);
        if (info) gme_free_info(info);
        gme_delete(emu);
        return 1;
    }
    if (info && info->play_length > 0) gme_set_fade(emu, info->play_length);

    /* The same thing `GmeBackend::openAtSomethingAudible` does, because a probe that measures a
     * different backend from the one shipped measures nothing. HES and KSS routinely hold nothing
     * at track 0, so if the first fifth of a second is silent, look for a track that is not --
     * bounded at twelve, exactly as the backend bounds it. */
    int opened = 0;
    if (peakOfTrack(emu, 0, 1) == 0) {
        /* Bounded by time, exactly as the backend bounds it: a fixed track count was the first
         * version and it missed `aleste 2.kss`, whose first audible tune is number 47 of 256. */
        const int count = gme_track_count(emu);
        const clock_t deadline = clock() + (clock_t) (0.3 * CLOCKS_PER_SEC);
        for (int t = 1; t < count; t++) {
            if (clock() > deadline) break;
            if (peakOfTrack(emu, t, 1) > 0) { opened = t; break; }
        }
    }
    if (info) gme_free_info(info);
    info = NULL;
    gme_track_info(emu, &info, opened);
    if (gme_start_track(emu, opened)) { puts("VERDICT reject:restart"); gme_delete(emu); return 1; }
    if (info && info->play_length > 0) gme_set_fade(emu, info->play_length);

    /* Pass one alone, because it is the one the player's contract turns on. */
    int first = 1;
    size_t firstFrames = 0;
    long peak = 0;
    long renderedFrames = 0;
    int ended = 0;

    for (long f = 0; f < (long) RATE * SECONDS; f += FRAMES) {
        if (gme_track_ended(emu)) { ended = 1; break; }
        if (gme_play(emu, FRAMES * 2, scratch)) break;
        for (int i = 0; i < FRAMES * 2; i++) {
            const long s = scratch[i] < 0 ? -scratch[i] : scratch[i];
            if (s > peak) peak = s;
        }
        if (first) { firstFrames = FRAMES; first = 0; }
        renderedFrames += FRAMES;
    }

    const int tracks = gme_track_count(emu);
    const int stated = (info && info->play_length > 0) ? info->play_length : 0;

    if (allTracks) {
        int audible = 0, checked = 0, firstAudible = -1;
        const int cap = getenv("GME_TRACK_LIMIT") ? atoi(getenv("GME_TRACK_LIMIT")) : 24;
        const int limit = tracks < cap ? tracks : cap;
        for (int t = 0; t < limit; t++) {
            const long p = peakOfTrack(emu, t, 3);
            if (p < 0) continue;
            checked++;
            if (p > 0) {
                audible++;
                if (firstAudible < 0) firstAudible = t;
            }
        }
        printf("VERDICT tracks=%d checked=%d audible=%d first=%d\n",
               tracks, checked, audible, firstAudible);
        if (getenv("GME_LIST_AUDIBLE")) {
            printf("audible tracks:");
            for (int t = 0; t < limit; t++) {
                if (peakOfTrack(emu, t, 2) > 0) printf(" %d", t);
            }
            printf("\n");
        }
        if (info) gme_free_info(info);
        gme_delete(emu);
        return 0;
    }

    const char *verdict = firstFrames == 0 ? "short:0" : (peak == 0 ? "silent" : "full");
    printf("VERDICT %s tracks=%d opened=%d stated=%dms ended=%s peak=%ld seconds=%.1f fmt=\"%s\"\n",
           verdict, tracks, opened, stated, ended ? "yes" : "no", peak,
           (double) renderedFrames / RATE, info && info->system ? info->system : "");

    if (info) gme_free_info(info);
    gme_delete(emu);
    return 0;
}
