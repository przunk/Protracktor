/* The same question asked of sc68 3.0.0b, whose API is not the old one.
 *
 * api68_* is gone entirely in 3.x -- sc68_create/sc68_load_mem/sc68_play/sc68_process replace it,
 * with no compatibility header. That is worth knowing before reading the numbers: adopting 3.0.0b
 * means rewriting Sc68Backend, not swapping a library.
 *
 * The pre-filter cannot be the same either, because api68_verify_mem does not exist. It is reduced
 * to the magic checks, which is the part that was doing the work anyway -- verify_mem was already
 * distrusted for rejecting ICE-packed SNDH that loads perfectly.
 *
 * Verdicts: plays | silent | loadfail | filtered | unreadable
 */
#include <sc68/sc68.h>
#include <sc68/file68_rsc.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>

enum { RATE = 44100, FRAMES = 1024, PASSES = 86 };

static int worth_trying(const char *bytes, int size) {
    if (size < 16) return 0;
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

    sc68_init_t init;
    memset(&init, 0, sizeof init);
    /* Do not read or write the user's sc68 config: the measurement must not depend on, or leave
     * behind, state from a previous run. */
    init.flags.no_load_config = 1;
    init.flags.no_save_config = 1;
    if (sc68_init(&init) < 0) { puts("loadfail"); return 0; }

    /* 3.x has no shared_path in sc68_init_t the way 2.2.1 had it in api68_init_t; the replay
     * binaries are found through rsc68 instead. Without this, SNDH loads and plays silence -- the
     * exact failure that cost a day on 2.2.1 -- and the comparison would be measuring a missing
     * path rather than a library. */
    {
        const char *share = getenv("SC68_SHARED_PATH");
        if (share && *share) rsc68_set_share(share);
    }

    sc68_create_t create;
    memset(&create, 0, sizeof create);
    create.sampling_rate = RATE;

    sc68_t *sc68 = sc68_create(&create);
    if (!sc68) { puts("loadfail"); return 0; }
    if (sc68_load_mem(sc68, bytes, (int) size) < 0) { puts("loadfail"); return 0; }
    if (sc68_play(sc68, 1, 1) < 0) { puts("loadfail"); return 0; }

    short buf[FRAMES * 2];
    long loudest = 0;
    for (int pass = 0; pass < PASSES; ++pass) {
        int n = FRAMES;
        int code = sc68_process(sc68, buf, &n);
        /* `SC68_ERROR` is ~0 -- every bit set -- so `code & SC68_ERROR` is true of any non-zero
         * status, including the perfectly ordinary SC68_IDLE|SC68_CHANGE returned on the first
         * pass. Testing it that way made every file render silence and cost a whole measurement
         * run. It is a failure only when it IS the value. */
        if (code == SC68_ERROR) break;
        for (int i = 0; i < n * 2; ++i) {
            long v = buf[i] < 0 ? -(long) buf[i] : buf[i];
            if (v > loudest) loudest = v;
        }
        if (code & SC68_END) break;
    }
    sc68_stop(sc68);
    sc68_destroy(sc68);
    sc68_shutdown();

    puts(loudest > 64 ? "plays" : "silent");
    return 0;
}
