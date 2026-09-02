/* Can sc68 3.x be used from several threads at once?
 *
 * This matters because of a documented limitation: sc68 2.2.1 kept its 68000 emulator in global
 * state, so opening a second instance while one played clobbered it -- which is why background
 * metadata resolution in this app waits for playback to stop, and why probing a whole library by
 * content looked blocked before it was started.
 *
 * 3.x replaced api68 with an instance-based API, so the question is worth asking again rather than
 * inheriting the old answer. A sequential test is not enough: it proves the instances are separate,
 * not that the library is thread-safe. This runs N threads, each creating, loading, playing,
 * rendering and destroying its own player twenty times over.
 *
 * Result on 2026-09-03, four threads, sc68 3.0.0b SVN r713: no failures, every thread producing
 * audio. The 2.2.1 limitation does not apply to 3.x.
 *
 * Build: ./scripts/build-sc68-probes.sh
 * Run:   SC68_SHARED_PATH=native/vendor/sc68-3/file68/data68 \
 *          native/probe/sc68/probe-concurrency FILE...
 */
#define _GNU_SOURCE
#include <sc68/sc68.h>
#include <sc68/file68_rsc.h>
#include <pthread.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>

typedef struct { const char *path; long loud; int fail; int passes; } job_t;

static char *slurp(const char *p, long *n) {
    FILE *f = fopen(p, "rb"); if (!f) return 0;
    fseek(f,0,SEEK_END); *n = ftell(f); fseek(f,0,SEEK_SET);
    char *b = malloc(*n); if (fread(b,1,*n,f) != (size_t)*n) { free(b); return 0; }
    fclose(f); return b;
}

static void *worker(void *arg) {
    job_t *j = arg;
    long n; char *b = slurp(j->path, &n);
    if (!b) { j->fail = 1; return 0; }
    sc68_create_t c; memset(&c,0,sizeof c); c.sampling_rate = 44100;
    for (int round = 0; round < 20; ++round) {
        sc68_t *s = sc68_create(&c);
        if (!s || sc68_load_mem(s, b, (int)n) < 0 || sc68_play(s, 1, SC68_DEF_LOOP) < 0) { j->fail++; if (s) sc68_destroy(s); continue; }
        short buf[2048];
        for (int i = 0; i < j->passes; ++i) {
            int cnt = 1024, code = sc68_process(s, buf, &cnt);
            if (code == SC68_ERROR) { j->fail++; break; }
            for (int k = 0; k < cnt*2; ++k) { long v = buf[k]<0?-(long)buf[k]:buf[k]; if (v > j->loud) j->loud = v; }
            if (code & SC68_END) break;
        }
        sc68_stop(s); sc68_destroy(s);
    }
    return 0;
}

int main(int argc, char **argv) {
    sc68_init_t init; memset(&init,0,sizeof init);
    init.flags.no_load_config = 1; init.flags.no_save_config = 1;
    sc68_init(&init);
    const char *share = getenv("SC68_SHARED_PATH"); if (share) rsc68_set_share(share);

    int n = argc - 1; if (n > 8) n = 8;
    job_t jobs[8]; pthread_t threads[8];
    for (int i = 0; i < n; ++i) { jobs[i] = (job_t){ argv[i+1], 0, 0, 10 }; }
    for (int i = 0; i < n; ++i) pthread_create(&threads[i], 0, worker, &jobs[i]);
    for (int i = 0; i < n; ++i) pthread_join(threads[i], 0);

    int bad = 0;
    for (int i = 0; i < n; ++i) {
        printf("thread %d: loudest=%6ld failures=%d\n", i, jobs[i].loud, jobs[i].fail);
        if (jobs[i].fail || jobs[i].loud <= 64) bad++;
    }
    printf("%s\n", bad ? "THREADED BROKEN" : "THREADED OK");
    return bad ? 1 : 0;
}
