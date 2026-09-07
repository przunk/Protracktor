// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later

/* Does one render call fill the buffer?
 *
 * This mirrors Sc68Backend::render's *contract* rather than its purpose, which is the difference
 * that matters. probe-sc68.py asks "does this file produce audio", loops, and ignores a short pass
 * -- so it passed while every Atari ST track stopped on the first audio callback (docs/review.md
 * R1). The player treats a short render as end-of-tune: it silences the rest of the buffer and
 * stops the stream. So the question a probe has to ask is not "is there audio eventually" but
 * "is the FIRST buffer full".
 *
 * Prints one verdict per file: full | short:N | loadfail
 *
 * Build: ./scripts/build-sc68-probes.sh
 */
#include <sc68/sc68.h>
#include <sc68/file68_rsc.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>

enum { RATE = 44100, FRAMES = 1024, MAX_IDLE = 8 };

/* The same loop as Sc68Backend::render, deliberately. If they drift, this stops being a test of
 * anything. */
static size_t render(sc68_t *sc68, short *scratch, size_t frames, int *ended) {
    size_t produced = 0;
    int idle = 0;
    while (produced < frames) {
        int count = (int) (frames - produced);
        const int code = sc68_process(sc68, scratch, &count);
        if (code == SC68_ERROR) { *ended = 1; break; }
        if (count > 0) { produced += (size_t) count; idle = 0; }
        else if (++idle > MAX_IDLE) break;
        if (code & SC68_END) { *ended = 1; break; }
    }
    return produced;
}

int main(int argc, char **argv) {
    if (argc < 2) return 2;
    sc68_init_t init;
    memset(&init, 0, sizeof init);
    init.flags.no_load_config = 1;
    init.flags.no_save_config = 1;
    sc68_init(&init);
    const char *share = getenv("SC68_SHARED_PATH");
    if (share && *share) rsc68_set_share(share);

    FILE *f = fopen(argv[1], "rb");
    if (!f) { puts("loadfail"); return 0; }
    fseek(f, 0, SEEK_END);
    long size = ftell(f);
    fseek(f, 0, SEEK_SET);
    char *bytes = malloc(size ? size : 1);
    if (!bytes || fread(bytes, 1, size, f) != (size_t) size) { puts("loadfail"); return 0; }
    fclose(f);

    sc68_create_t create;
    memset(&create, 0, sizeof create);
    create.sampling_rate = RATE;
    sc68_t *sc68 = sc68_create(&create);
    if (!sc68 || sc68_load_mem(sc68, bytes, (int) size) < 0 ||
        sc68_play(sc68, 1, SC68_DEF_LOOP) < 0) {
        puts("loadfail");
        return 0;
    }

    short scratch[FRAMES * 2];
    int ended = 0;
    const size_t first = render(sc68, scratch, FRAMES, &ended);

    sc68_stop(sc68);
    sc68_destroy(sc68);
    sc68_shutdown();

    if (first == FRAMES) puts("full");
    else printf("short:%zu\n", first);
    return first == FRAMES ? 0 : 1;
}
