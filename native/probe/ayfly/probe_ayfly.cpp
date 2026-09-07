/* Does ayfly play the ZX Spectrum tracker formats we claim nothing for?
 *
 * `./scripts/probe-platforms.py` says ZX Spectrum holds **23,891 Modland files and this build plays
 * 58 of them** -- the largest platform in the archive that is dark, and the only large one that is
 * a chiptune in the same sense as the rest of the app: `.pt3`, `.pt2`, `.stc`, `.asc` and `.sqt`
 * are trackers driving an AY-3-8912, not console emulators.
 *
 * ayfly is GPL-2.0-or-later (read from the sources, not from a LICENSE file -- the third time in
 * this project that mattered), about 900 KB, and its `players/` covers every one of those names.
 *
 * Four questions, all from what `Backend` will need rather than from "is there noise":
 *
 *   1. **Does it load from a buffer?** `ay_initsongindirect` takes bytes and a length and works out
 *      the format itself. The app never has a path -- SAF hands it bytes.
 *   2. **Does it render into a buffer?** `ay_rendersongbuffer` does, in interleaved 16-bit stereo,
 *      taking a byte count. So unlike HivelyTracker this one needs no ring buffer: it fills
 *      whatever we ask for.
 *   3. **Does it know how long the tune is?** `ay_getsonglength`. If it does, these files arrive
 *      with a seek bar, the way AHX did.
 *   4. **Is it audible?** Peak sample over the render. "Loads" is not "plays", which is the whole
 *      lesson of the gme probe.
 *
 * Prints one verdict per file:
 *   VERDICT full len=NNNs peak=NNNNN name="..." author="..."
 *   VERDICT silent ... | VERDICT reject:load | VERDICT unreadable
 *
 * Build: ./scripts/build-ayfly-probe.sh
 */
#include <cstdio>
#include <cstdlib>
#include <cstring>
#include <vector>

#include "ayfly.h"

/* The engine's rate. ayfly resamples internally, so this is a choice rather than a constraint. */
enum { RATE = 44100, SECONDS = 8, BLOCK_FRAMES = 1024 };

/** The name with quotes and control characters removed, so one verdict stays one line. */
static void printField(const char *text) {
    if (!text) return;
    for (const unsigned char *p = (const unsigned char *) text; *p && p - (const unsigned char *) text < 64; p++)
        putchar(*p < 0x20 || *p == '"' ? ' ' : *p);
}

int main(int argc, char **argv) {
    if (argc < 2) { fprintf(stderr, "usage: probe-ayfly FILE\n"); return 2; }

    FILE *file = fopen(argv[1], "rb");
    if (!file) { puts("VERDICT unreadable"); return 2; }
    fseek(file, 0, SEEK_END);
    const long length = ftell(file);
    fseek(file, 0, SEEK_SET);
    std::vector<unsigned char> bytes(length > 0 ? (size_t) length : 0);
    if (bytes.empty() || fread(bytes.data(), 1, bytes.size(), file) != bytes.size()) {
        fclose(file); puts("VERDICT unreadable"); return 2;
    }
    fclose(file);

    void *info = ay_initsongindirect(bytes.data(), RATE, (unsigned long) bytes.size(), 0);
    if (!info) { puts("VERDICT reject:load"); return 1; }

    const unsigned long stated = ay_getsonglength(info);

    /* **Not `ay_startsong`.** It dereferences the song's `player` without checking it, while
     * `ay_songstarted` two lines below it in the same file does check -- so with the null player
     * this needs (there is no audio device here, and there will not be one on Android either) it
     * segfaults on every file. Rendering does not need it: `ay_rendersongbuffer` drives the chip
     * directly. Worth knowing before the backend is written rather than after. */

    /* Interleaved 16-bit stereo: `ay_rendersongbuffer` takes a byte count and writes len/2 shorts,
     * two per frame. Asked for a block at a time, the way Oboe will ask. */
    std::vector<short> block((size_t) BLOCK_FRAMES * 2);
    long peak = 0;
    long frames = 0;
    for (long f = 0; f < (long) RATE * SECONDS; f += BLOCK_FRAMES) {
        const unsigned long wrote = ay_rendersongbuffer(
            info, (unsigned char *) block.data(), (unsigned long) (block.size() * sizeof(short)));
        if (wrote == 0) break;
        const size_t samples = wrote / sizeof(short);
        for (size_t i = 0; i < samples && i < block.size(); i++) {
            const long v = block[i] < 0 ? -(long) block[i] : block[i];
            if (v > peak) peak = v;
        }
        frames += (long) (samples / 2);
        /* `ay_songstarted` asks the audio device, which does not exist here, so it is always false.
         * The end of the tune is the stated length instead, when there is one. */
        if (stated > 0 && frames >= (long) (stated / 50) * RATE) break;
    }

    /* `Length` is in PAL frames -- fiftieths of a second -- which `ayfly.h` says on the field and
     * nothing else repeats. Read as milliseconds it makes every tune about six seconds long, which
     * is plausible enough to be believed and wrong. */
    printf("VERDICT %s len=%lus rendered=%lds peak=%ld name=\"",
           peak > 0 ? "full" : "silent", stated / 50, frames / RATE, peak);
    printField(ay_getsongname(info));
    printf("\" author=\"");
    printField(ay_getsongauthor(info));
    puts("\"");

    ay_closesong(&info);
    return 0;
}
