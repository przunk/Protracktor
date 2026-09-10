/* Was the shared open-error string a data race, and is it gone?
 *
 * `docs/review.md` R2. Until round 5, `openBackend` cleared and wrote one process-wide
 * `std::string` and the caller collected it afterwards through JNI. That was safe while one thread
 * opened files at a time. Item 2 made library scanning run concurrently with playback on purpose,
 * which put three threads on that string: playback's `load`, the scan's `probe`, and background
 * metadata resolution.
 *
 * Two `std::string` operations racing is undefined behaviour, not merely a mixed-up message, so
 * this is kept as a runnable demonstration rather than an argument.
 *
 *   g++ -fsanitize=thread -g -O1 -o /tmp/openrace native/probe/engine/open_error_race.cc -lpthread
 *   setarch -R /tmp/openrace before   # expect: ThreadSanitizer reports a data race
 *   setarch -R /tmp/openrace after    # expect: clean
 *
 * `setarch -R` disables address-space randomisation, without which ThreadSanitizer refuses to start
 * under WSL2 ("unexpected memory mapping").
 */
#include <cstdio>
#include <cstring>
#include <string>
#include <thread>
#include <vector>

namespace before {
/* One string for the whole process, exactly as engine.cpp had it. */
std::string &lastOpenError() { static std::string reason; return reason; }

void openBackend(const char *why) {
    lastOpenError().clear();
    lastOpenError() = why;
}

void work(int thread) {
    for (int n = 0; n < 2000; ++n) {
        openBackend(thread == 0 ? "playback: no backend claimed it" : "scan: not a module");
        volatile std::size_t seen = lastOpenError().size();   // what the caller then read
        (void) seen;
    }
}
}  // namespace before

namespace after {
/* The reason is a local of the open call and goes back with it. Nothing is shared. */
void openBackend(const char *why, std::string &error) {
    error.clear();
    error = why;
}

void work(int thread) {
    for (int n = 0; n < 2000; ++n) {
        std::string error;
        openBackend(thread == 0 ? "playback: no backend claimed it" : "scan: not a module", error);
        volatile std::size_t seen = error.size();
        (void) seen;
    }
}
}  // namespace after

int main(int argc, char **argv) {
    const bool old = argc > 1 && !std::strcmp(argv[1], "before");
    std::vector<std::thread> threads;
    for (int i = 0; i < 3; ++i) {
        threads.emplace_back([i, old] { old ? before::work(i) : after::work(i); });
    }
    for (auto &t : threads) t.join();
    std::printf("%s: finished\n", old ? "before" : "after");
    return 0;
}
