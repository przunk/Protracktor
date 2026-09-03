/* Can UADE play this, and does it fill the first buffer?
 *
 * `GOAL.md` round 6 item 1 asks for a pass rate on a real corpus before anything is integrated.
 * That has caught something every time: sc68 2.2.1's half-broken SNDH, the SidMon executables
 * hiding among Modland's `.sid` files, and R1.
 *
 * **R1 is why this asks two questions rather than one.** `probe-sc68.py` asked "does this file
 * produce audio", looped, and ignored a short first pass -- so it passed while every Atari ST
 * track stopped on its first audio callback, because the player treats a short render as
 * end-of-tune. So the contract, not the hope: is the FIRST buffer full, and is there sound in it.
 *
 * **It plays from a path, not from a buffer.** `uade_play_from_buffer` says in its own header that
 * it does not work with multifile songs, and a large part of what UADE is for is multifile: TFMX
 * is `mdat.name` beside `smpl.name`, and asking about the `mdat` alone reports "unsupported" for
 * the single biggest Amiga custom format in Modland. That was measured the wrong way once already.
 *
 * **The verdict line is tagged.** libuade writes its own warnings to stdout ahead of anything this
 * prints -- "Song ended prematurely due to error: module check failed" is a real one -- so a reader
 * that takes the first line of output can mistake a warning for a verdict and quietly misclassify
 * the file. Every verdict here starts with `VERDICT ` and the reader looks for that.
 *
 * Prints one verdict per file, on one line:
 *
 *   full  peak=N subsongs=A..B fmt="…" player="…"    -- plays, first buffer full
 *   short:N  …                                       -- plays, first buffer underfilled
 *   silent  …                                        -- renders, but every sample is zero
 *   unsupported                                      -- UADE does not claim the file
 *   fatal                                            -- uade_play_from_buffer returned -1
 *
 * Build: ./scripts/build-uade-probe.sh
 */
#include <uade/uade.h>

#include <stdio.h>
#include <stdlib.h>
#include <string.h>

/* 1024 frames is the buffer the Android engine renders into, so a short first pass here is a
 * short first pass there. */
enum { RATE = 44100, FRAMES = 1024, SECONDS = 5 };

static void *slurp(const char *path, size_t *size) {
    FILE *file = fopen(path, "rb");
    if (!file) return NULL;
    fseek(file, 0, SEEK_END);
    const long length = ftell(file);
    fseek(file, 0, SEEK_SET);
    if (length <= 0) { fclose(file); return NULL; }
    void *bytes = malloc((size_t) length);
    if (!bytes) { fclose(file); return NULL; }
    const size_t got = fread(bytes, 1, (size_t) length, file);
    fclose(file);
    if (got != (size_t) length) { free(bytes); return NULL; }
    *size = got;
    return bytes;
}

int main(int argc, char **argv) {
    if (argc < 2) { fprintf(stderr, "usage: probe-uade FILE\n"); return 2; }

    const char *base = getenv("UADE_BASE_DIR");
    const char *core = getenv("UADE_CORE_FILE");
    if (!base) { printf("VERDICT nobasedir\n"); return 2; }

    /* Readability is checked here so an unreadable file is not reported as an unplayable one. */
    size_t size = 0;
    void *bytes = slurp(argv[1], &size);
    if (!bytes) { printf("VERDICT unreadable\n"); return 2; }
    free(bytes);

    struct uade_config *config = uade_new_config();
    if (!config) { printf("VERDICT noconfig\n"); return 2; }
    uade_config_set_option(config, UC_BASE_DIR, base);
    if (core) uade_config_set_option(config, UC_UADECORE_FILE, core);
    uade_config_set_option(config, UC_FREQUENCY, "44100");
    /* Content detection, because Modland's directory says what a file is and its name often does
     * not -- the same reason the app stopped trusting extensions (docs/BACKLOG.md A6). */
    uade_config_set_option(config, UC_CONTENT_DETECTION, NULL);
    /* Without a bound, a tune that plays forever holds the probe forever. These are the timeouts
     * uade123 uses by default, stated rather than inherited. */
    uade_config_set_option(config, UC_TIMEOUT_VALUE, "20");
    uade_config_set_option(config, UC_SUBSONG_TIMEOUT_VALUE, "20");
    uade_config_set_option(config, UC_SILENCE_TIMEOUT_VALUE, "10");

    struct uade_state *state = uade_new_state(config);
    free(config);
    if (!state) { printf("VERDICT nostate\n"); return 2; }

    /* By path, so a multifile song can reach its companion, and because the `PREFIX.` filename
     * convention is the whole of the identification for several formats. */
    const int claimed = uade_play(argv[1], -1, state);
    if (claimed < 0) { printf("VERDICT fatal\n"); uade_cleanup_state(state); return 1; }
    if (claimed == 0) { printf("VERDICT unsupported\n"); uade_cleanup_state(state); return 1; }

    int16_t buffer[FRAMES * 2];
    const size_t wanted = sizeof buffer;

    /* Pass one, alone, because it is the one the player's contract turns on. */
    const ssize_t first = uade_read(buffer, wanted, state);
    size_t firstBytes = first > 0 ? (size_t) first : 0;

    int peak = 0;
    size_t rendered = firstBytes;
    for (size_t i = 0; i < firstBytes / sizeof(int16_t); i++) {
        const int sample = buffer[i] < 0 ? -buffer[i] : buffer[i];
        if (sample > peak) peak = sample;
    }

    const size_t target = (size_t) RATE * SECONDS * 2 * sizeof(int16_t);
    while (rendered < target) {
        const ssize_t got = uade_read(buffer, wanted, state);
        if (got <= 0) break;
        for (size_t i = 0; i < (size_t) got / sizeof(int16_t); i++) {
            const int sample = buffer[i] < 0 ? -buffer[i] : buffer[i];
            if (sample > peak) peak = sample;
        }
        rendered += (size_t) got;
    }

    const struct uade_song_info *info = uade_get_song_info(state);
    char fmt[256] = "", player[256] = "";
    int lo = 0, hi = 0;
    if (info) {
        snprintf(fmt, sizeof fmt, "%s", info->formatname);
        snprintf(player, sizeof player, "%s", info->playername);
        lo = info->subsongs.min;
        hi = info->subsongs.max;
    }

    const char *verdict;
    char shortfall[32];
    if (firstBytes == 0) {
        verdict = "short:0";
    } else if (firstBytes < wanted) {
        snprintf(shortfall, sizeof shortfall, "short:%zu", firstBytes / (2 * sizeof(int16_t)));
        verdict = shortfall;
    } else if (peak == 0) {
        verdict = "silent";
    } else {
        verdict = "full";
    }

    printf("VERDICT %s peak=%d subsongs=%d..%d seconds=%.1f fmt=\"%s\" player=\"%s\"\n",
           verdict, peak, lo, hi,
           (double) rendered / (double) (RATE * 2 * sizeof(int16_t)), fmt, player);

    uade_stop(state);
    uade_cleanup_state(state);
    return 0;
}
