/* Does a rewind come back to the subsong that was playing?
 *
 * `docs/STATUS.md` C13. `Sc68Backend::rewind()` called `sc68_play(sc68_, 1, ...)` with a literal
 * one, and repeat-one goes through `rewind()` -- so a multi-tune SNDH sitting on subsong five
 * repeated subsong one. It did repeat, which is why nothing looked broken; it repeated the wrong
 * thing, which is what the owner heard.
 *
 * The fix is to remember what `selectSubsong` chose. This is the demonstration, kept runnable
 * rather than argued, in the same spirit as `probe_render.c`: it asks sc68 which track is current
 * after a rewind, and both behaviours are here so the difference is visible rather than asserted.
 *
 * Prints, per file:  subsongs=N chose=K before=B after=A verdict
 *   where `before` is what the old code came back to and `after` is what the new code does.
 *
 * Build: ./scripts/build-sc68-probes.sh
 */
#include <sc68/sc68.h>
#include <sc68/file68_rsc.h>

#include <stdio.h>
#include <stdlib.h>
#include <string.h>

/**
 * Which track is playing, asked the only way that gives a true answer.
 *
 * `sc68_play` sets the *pending* track; the change takes effect when processing next runs. Asking
 * before rendering reports the track that was playing before, which is the same mechanism that
 * produced C11 -- a finished tune has no audio callback left, so a switch never lands. So this
 * renders a buffer first, and the render loop is `probe_render.c`'s because a short first pass is
 * normal for sc68 and treating it as an error would make this measure the wrong thing.
 */
static int currentTrack(sc68_t *sc68) {
    short scratch[1024 * 2];
    for (int pass = 0; pass < 8; pass++) {
        int count = 1024;
        const int code = sc68_process(sc68, scratch, &count);
        if (code == SC68_ERROR) break;
        if (count > 0) break;
        if (code & SC68_END) break;
    }
    sc68_music_info_t info;
    memset(&info, 0, sizeof info);
    if (sc68_music_info(sc68, &info, SC68_CUR_TRACK, NULL) < 0) return -1;
    return info.trk.track;
}

int main(int argc, char **argv) {
    if (argc < 2) { fprintf(stderr, "usage: probe-subsong-rewind FILE\n"); return 2; }

    sc68_init_t init;
    memset(&init, 0, sizeof init);
    init.flags.no_load_config = 1;
    init.flags.no_save_config = 1;
    sc68_init(&init);
    const char *share = getenv("SC68_SHARED_PATH");
    if (share && *share) rsc68_set_share(share);

    FILE *file = fopen(argv[1], "rb");
    if (!file) { puts("unreadable"); return 2; }
    fseek(file, 0, SEEK_END);
    const long length = ftell(file);
    fseek(file, 0, SEEK_SET);
    unsigned char *bytes = length > 0 ? malloc((size_t) length) : NULL;
    if (!bytes || fread(bytes, 1, (size_t) length, file) != (size_t) length) {
        fclose(file); free(bytes); puts("unreadable"); return 2;
    }
    fclose(file);

    sc68_t *sc68 = sc68_create(NULL);
    if (!sc68) { free(bytes); puts("nocreate"); return 2; }
    if (sc68_load_mem(sc68, bytes, (int) length) < 0) { free(bytes); puts("loadfail"); return 1; }
    free(bytes);

    sc68_music_info_t info;
    memset(&info, 0, sizeof info);
    sc68_music_info(sc68, &info, SC68_CUR_TRACK, NULL);
    const int subsongs = info.tracks;
    if (subsongs < 2) { printf("subsongs=%d single\n", subsongs); return 0; }

    /* Pick something that is neither the first nor the last, so an off-by-one cannot pass. */
    const int chose = subsongs / 2 + 1;
    if (sc68_play(sc68, chose, SC68_DEF_LOOP) < 0) { puts("playfail"); return 1; }
    const int selected = currentTrack(sc68);

    /* The old rewind: a literal one. */
    sc68_stop(sc68);
    sc68_play(sc68, 1, SC68_DEF_LOOP);
    const int before = currentTrack(sc68);

    /* The new rewind: the remembered track. */
    sc68_stop(sc68);
    sc68_play(sc68, chose, SC68_DEF_LOOP);
    const int after = currentTrack(sc68);

    printf("subsongs=%d chose=%d selected=%d before=%d after=%d %s\n",
           subsongs, chose, selected, before, after,
           (before != chose && after == chose) ? "DEMONSTRATED"
               : (after == chose ? "already-correct" : "UNEXPECTED"));

    sc68_stop(sc68);
    sc68_destroy(sc68);
    sc68_shutdown();
    return 0;
}
