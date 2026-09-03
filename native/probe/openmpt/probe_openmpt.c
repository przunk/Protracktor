/* Would libopenmpt play this, if the app ever handed it over?
 *
 * A side question that came out of measuring UADE (`GOAL.md` round 6 item 1) and turned out to
 * matter on its own. `SupportedFormats.extensions` is what decides whether a file is scanned or
 * indexed at all, and it lists `med` -- while Modland stores OctaMED as `.mmd0`…`.mmd3`. libopenmpt
 * identifies MED by an "MMD" magic in the header and does not care about the extension, so those
 * files would very likely play. They are simply never offered.
 *
 * "Very likely" is not a measurement, which is what this is for. It asks libopenmpt directly,
 * exactly as the app's own backend does: from a memory buffer, no filename involved.
 *
 * Prints one verdict per file: full | short:N | silent | unsupported
 *
 * Build: see ./scripts/probe-extensions.py, which builds it if needed.
 */
#include <libopenmpt/libopenmpt.h>

#include <stdio.h>
#include <stdlib.h>
#include <string.h>

enum { RATE = 48000, FRAMES = 1024, SECONDS = 3 };

int main(int argc, char **argv) {
    if (argc < 2) { fprintf(stderr, "usage: probe-openmpt FILE\n"); return 2; }

    FILE *file = fopen(argv[1], "rb");
    if (!file) { printf("unreadable\n"); return 2; }
    fseek(file, 0, SEEK_END);
    const long length = ftell(file);
    fseek(file, 0, SEEK_SET);
    if (length <= 0) { fclose(file); printf("empty\n"); return 2; }
    void *bytes = malloc((size_t) length);
    if (!bytes || fread(bytes, 1, (size_t) length, file) != (size_t) length) {
        fclose(file); free(bytes); printf("unreadable\n"); return 2;
    }
    fclose(file);

    /* No filename, deliberately: the whole question is whether the content is enough. */
    openmpt_module *module = openmpt_module_create_from_memory2(
        bytes, (size_t) length, NULL, NULL, NULL, NULL, NULL, NULL, NULL);
    free(bytes);
    if (!module) { printf("unsupported\n"); return 1; }

    float buffer[FRAMES * 2];
    size_t first = openmpt_module_read_interleaved_float_stereo(module, RATE, FRAMES, buffer);

    float peak = 0.0f;
    size_t rendered = first;
    for (size_t i = 0; i < first * 2; i++) {
        const float sample = buffer[i] < 0 ? -buffer[i] : buffer[i];
        if (sample > peak) peak = sample;
    }
    while (rendered < (size_t) RATE * SECONDS) {
        const size_t got = openmpt_module_read_interleaved_float_stereo(module, RATE, FRAMES, buffer);
        if (got == 0) break;
        for (size_t i = 0; i < got * 2; i++) {
            const float sample = buffer[i] < 0 ? -buffer[i] : buffer[i];
            if (sample > peak) peak = sample;
        }
        rendered += got;
    }

    const char *type = openmpt_module_get_metadata(module, "type_long");
    const char *title = openmpt_module_get_metadata(module, "title");

    char verdict[32];
    if (first == 0) snprintf(verdict, sizeof verdict, "short:0");
    else if (first < FRAMES) snprintf(verdict, sizeof verdict, "short:%zu", first);
    else if (peak == 0.0f) snprintf(verdict, sizeof verdict, "silent");
    else snprintf(verdict, sizeof verdict, "full");

    printf("%s peak=%.3f seconds=%.1f fmt=\"%s\" title=\"%s\"\n",
           verdict, (double) peak, (double) rendered / RATE,
           type ? type : "", title ? title : "");

    openmpt_free_string(type);
    openmpt_free_string(title);
    openmpt_module_destroy(module);
    return 0;
}
