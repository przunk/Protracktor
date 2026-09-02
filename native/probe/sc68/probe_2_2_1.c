/* Does sc68 2.2.1 play this file? One verdict per run, on stdout.
 *
 * Mirrors Sc68Backend in native/engine/engine.cpp deliberately, including the worthTrying
 * pre-filter: a measurement of a different code path would answer a question nobody asked.
 *
 * Verdicts: plays | silent | loadfail | filtered | unreadable
 */
#include <api68/api68.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>

/* Two seconds is enough to tell a tune from silence and keeps thirty files quick. */
enum { RATE = 44100, FRAMES = 1024, PASSES = 86 };

static int worth_trying(const char *bytes, int size) {
    if (size < 16) return 0;
    if (api68_verify_mem(bytes, size) >= 0) return 1;
    if (!memcmp(bytes, "ICE!", 4)) return 1;
    if (!memcmp(bytes, "SC68", 4)) return 1;
    int window = size - 4 < 256 ? size - 4 : 256;
    for (int i = 0; i < window; ++i)
        if (!memcmp(bytes + i, "SNDH", 4)) return 1;
    return 0;
}

int main(int argc, char **argv) {
    if (argc < 2) return 2;
    FILE *f = fopen(argv[1], "rb");
    if (!f) { puts("unreadable"); return 0; }
    fseek(f, 0, SEEK_END);
    long size = ftell(f);
    fseek(f, 0, SEEK_SET);
    char *bytes = malloc(size ? size : 1);
    if (!bytes || fread(bytes, 1, size, f) != (size_t) size) { puts("unreadable"); return 0; }
    fclose(f);

    if (!worth_trying(bytes, (int) size)) { puts("filtered"); return 0; }

    api68_init_t init;
    memset(&init, 0, sizeof init);
    init.alloc = malloc;
    init.free = free;
    init.sampling_rate = RATE;
    init.shared_path = getenv("SC68_SHARED_PATH");

    api68_t *api = api68_init(&init);
    if (!api) { puts("loadfail"); return 0; }
    if (api68_load_mem(api, bytes, (int) size) < 0) { puts("loadfail"); return 0; }
    api68_play(api, 1);

    short buf[FRAMES * 2];
    long loudest = 0;
    for (int pass = 0; pass < PASSES; ++pass) {
        int code = api68_process(api, buf, FRAMES);
        for (int i = 0; i < FRAMES * 2; ++i) {
            long v = buf[i] < 0 ? -(long) buf[i] : buf[i];
            if (v > loudest) loudest = v;
        }
        if (code & API68_END) break;
    }
    api68_stop(api);
    api68_shutdown(api);

    /* Not zero: a tune can carry a DC offset or one stray sample and still be silence to a
     * listener. 64 of 32768 is about -54 dB. */
    puts(loudest > 64 ? "plays" : "silent");
    return 0;
}
