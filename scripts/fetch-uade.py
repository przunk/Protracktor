#!/usr/bin/env python3
"""Fetch UADE and prepare the parts of it a cross-compiler cannot make for itself.

UADE plays the Amiga custom formats by emulating an Amiga and running the original replay
routines. It is the only decoder here that arrives as **three repositories and a code generator**,
and none of the three has a release tarball, so this is a script rather than three more `fetch`
lines in `fetch-native-deps.sh` -- the same reason `fetch-sc68-svn.py` and `fetch-zxtune.py` exist.

Everything is pinned. `docs/PLAN_FORMATS.md` §4 records a measurement made against exactly these
revisions, and `./scripts/build-uade-probe.sh` builds the host probe from the same ones: an
integration built from a different UADE than the one that was measured is an integration nobody
measured.

**Three things happen here that a `git clone` does not do**, and each is a file upstream's
`configure` would have written:

1. The generated configuration headers (`src/sysconfig.h` and two of libzakalwe's) are answers
   about the machine the build runs on. Cross-compiled they would describe this Linux box rather
   than the phone, so the answers live in `native/backends/uade/config/` as reviewable files and
   are copied in. Their own comments say what each answer is and why it holds for bionic.

2. `src/sd-sound.c` and `src/sd-sound.h` are one-line files naming the audio sink. Upstream writes
   them at configure time because it has several to choose from; uadecore has exactly one that is
   not a sound card -- `sd-sound-generic`, which renders into the IPC socket -- and that is the one
   this app wants, so the two lines are written here.

3. **The 68000 emulator does not exist as source.** `build68k` reads `table68k` and writes
   `cpudefs.c`; `gencpu` then writes `cpuemu.c`, `cpustbl.c` and `cputbl.h`, some 3 MB of switch
   statements. Both are *host* programs -- they run during the build, they do not go in the APK --
   so they are built with the host compiler here, once, and the NDK compiles what they produce for
   each ABI. Their output is a function of `table68k` alone, so generating it on the host and
   compiling it for ARM is not a shortcut: it is what upstream's Makefile does when it is asked to
   cross-compile, with `NATIVECC` and `CC` differing.

Usage:  ./scripts/fetch-uade.py [--force]
"""
from __future__ import annotations

import argparse
import os
import pathlib
import shutil
import subprocess
import sys

ROOT = pathlib.Path(__file__).resolve().parent.parent
VENDOR = ROOT / "native" / "vendor"
CONFIG = ROOT / "native" / "backends" / "uade" / "config"

# name -> (repository, revision, what it is)
SOURCES = {
    # UADE 3.05. UAE code under the GPL with no version stated, libuade under the LGPL
    # (`docs/LICENSES.md`); the replay binaries under `players/` are *not* shipped and are
    # downloaded by the app from the page upstream publishes for them (`docs/LICENSES.md`).
    "uade": (
        "https://gitlab.com/uade-music-player/uade.git",
        "d40dcc7",
        "the emulator and the library in front of it",
    ),
    # BSD-2-Clause (and BSD-3-Clause for the part from Codeville). libuade reads RMC containers,
    # which are bencoded. Read from its LICENSE files on 2026-09-21; an earlier note here said LGPL.
    "bencodetools": (
        "https://gitlab.com/heikkiorsila/bencodetools.git",
        "5fa73d3",
        "bencode, for RMC containers",
    ),
    # BSD-2-Clause-style, from its COPYING (read 2026-09-21; an earlier note here said LGPL).
    # UADE's author's support library: arrays, strings, files.
    "libzakalwe": (
        "https://gitlab.com/hors/libzakalwe.git",
        "080b054",
        "support library",
    ),
}

# Which of the generated files must exist afterwards for the build to have a chance.
GENERATED = ["src/cpudefs.c", "src/cpuemu.c", "src/cpustbl.c", "src/cputbl.h",
             "src/sd-sound.c", "src/sd-sound.h", "src/sysconfig.h",
             "src/frontends/common/ossupport.c",
             "src/frontends/include/uade/ossupport.h",
             "src/frontends/include/uade/sysincludes.h",
             "src/frontends/include/uade/compilersupport.h",
             "src/frontends/include/uade/options.h"]


def run(*args: str, cwd: pathlib.Path | None = None, quiet: bool = True, **kwargs) -> None:
    subprocess.run(args, cwd=cwd, check=True,
                   stdout=subprocess.DEVNULL if quiet else None,
                   stderr=subprocess.DEVNULL if quiet else None, **kwargs)


def clone(name: str) -> bool:
    url, revision, what = SOURCES[name]
    dest = VENDOR / name
    print(f"  ⬇  {name} {revision} — {what}")
    if dest.exists():
        shutil.rmtree(dest)
    try:
        run("git", "clone", "--quiet", url, str(dest))
        run("git", "checkout", "--quiet", revision, cwd=dest)
    except subprocess.CalledProcessError:
        print(f"  ❌ {name} could not be fetched from {url}")
        return False
    head = subprocess.run(["git", "rev-parse", "--short", "HEAD"], cwd=dest,
                          capture_output=True, text=True).stdout.strip()
    if not head.startswith(revision[:7]) and not revision.startswith(head):
        print(f"  ❌ {name}: checked out {head}, expected {revision}")
        return False
    return True


def place_configuration() -> None:
    """Copies the four hand-written answers into the two trees that expect generated ones."""
    pairs = [
        (CONFIG / "sysconfig.h", VENDOR / "uade" / "src" / "sysconfig.h"),
        (CONFIG / "options.h",
         VENDOR / "uade" / "src" / "frontends" / "include" / "uade" / "options.h"),
        (CONFIG / "zakalwe-config.h",
         VENDOR / "libzakalwe" / "include" / "zakalwe" / "config.h"),
        (CONFIG / "zakalwe-tree.h", VENDOR / "libzakalwe" / "include" / "zakalwe" / "tree.h"),
    ]
    for source, target in pairs:
        target.parent.mkdir(parents=True, exist_ok=True)
        shutil.copyfile(source, target)

    # The audio sink, as configure would have named it.
    src = VENDOR / "uade" / "src"
    (src / "sd-sound.c").write_text('#include "sd-sound-generic.c"\n')
    (src / "sd-sound.h").write_text('#include "sd-sound-generic.h"\n')

    write_os_support(src)


def write_os_support(src: pathlib.Path) -> None:
    """Assembles `ossupport.c`, `ossupport.h` and `compilersupport.h`.

    Upstream's configure builds these three by concatenating its own files, choosing what to append
    by compiling three one-line programs. The three were compiled here with the NDK's
    `aarch64-linux-android29-clang` on 2026-09-19 rather than reasoned about:

        memmem                   compiles -- bionic has it, no replacement
        canonicalize_file_name   does NOT -- a GNU extension glibc has and bionic does not,
                                 so upstream's own replacement is appended, as it is on every
                                 platform that lacks it
        __builtin_expect         compiles -- likely()/unlikely() mean something

    The pieces are upstream's, taken from `compat/` at build time. Nothing is copied into this
    repository by hand: a copy would be a fork of two files nobody would remember to update.
    """
    common = src / "frontends" / "common"
    include = src / "frontends" / "include" / "uade"
    compat = src.parent / "compat"

    (common / "ossupport.c").write_text(
        "#include <uade/ossupport.h>\n\n"
        + (common / "unixsupport.c").read_text()
        + (compat / "canonrep.c").read_text()
    )
    (include / "ossupport.h").write_text(
        "#ifndef _UADE_OSSUPPORT_H_\n"
        "#define _UADE_OSSUPPORT_H_\n\n"
        "#include <uade/unixsupport.h>\n"
        "#include <zakalwe/string.h>\n\n"
        + (compat / "canonrep.h").read_text()
        + "\n#endif\n"
    )
    # Which system headers the IPC needs. Upstream writes the UNIX pair for everything but
    # FreeBSD, and bionic is a UNIX in this respect.
    (include / "sysincludes.h").write_text(
        "#include <netinet/in.h>\n"
        "#include <sys/select.h>\n"
    )
    (include / "compilersupport.h").write_text(
        "#ifndef _UADE_COMPILER_SUPPORT_H_\n"
        "#define _UADE_COMPILER_SUPPORT_H_\n"
        "#define likely(x)\t__builtin_expect(!!(x), 1)\n"
        "#define unlikely(x)\t__builtin_expect(!!(x), 0)\n"
        "#endif\n"
    )


def generate_cpu() -> bool:
    """Builds `build68k` and `gencpu` for the host and runs them.

    Failure here is reported with the compiler's own output rather than swallowed: the whole of the
    68000 emulator depends on it, and a silent miss would surface later as three hundred undefined
    symbols with nothing to connect them to this step.
    """
    src = VENDOR / "uade" / "src"
    cc = os.environ.get("CC_FOR_BUILD") or os.environ.get("HOSTCC") or shutil.which("cc") \
        or shutil.which("gcc") or shutil.which("clang")
    if cc is None:
        print("  ❌ no host C compiler found (looked for cc, gcc, clang; or set CC_FOR_BUILD)")
        return False

    includes = ["-I.", "-Iinclude", "-Ifrontends/include"]
    # -Wno-... because upstream's generators are 1995 C and warn freely; the warnings are not ours
    # to fix and a wall of them hides a real failure.
    common = ["-O2", "-w", *includes]

    try:
        print("  ⚙  generating the 68000 emulator (host compiler: "
              f"{pathlib.Path(cc).name})")
        run(cc, *common, "-o", "build68k", "build68k.c", cwd=src, quiet=False)
        with (src / "table68k").open("rb") as table, (src / "cpudefs.c").open("wb") as defs:
            subprocess.run([str(src / "build68k")], cwd=src, stdin=table, stdout=defs, check=True)
        run(cc, *common, "-o", "gencpu", "gencpu.c", "readcpu.c", "cpudefs.c", "missing.c",
            cwd=src, quiet=False)
        run(str(src / "gencpu"), cwd=src)
    except subprocess.CalledProcessError as error:
        print(f"  ❌ the 68000 emulator could not be generated: {error}")
        return False
    finally:
        for tool in ("build68k", "gencpu"):
            (src / tool).unlink(missing_ok=True)
    return True


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--force", action="store_true")
    args = parser.parse_args()

    revisions = " ".join(f"{name}={SOURCES[name][1]}" for name in sorted(SOURCES))
    stamp = VENDOR / "uade" / ".protracktor-version"
    if stamp.is_file() and stamp.read_text().strip() == revisions and not args.force:
        missing = [f for f in GENERATED if not (VENDOR / "uade" / f).is_file()]
        if not missing:
            print(f"  ✅ uade {SOURCES['uade'][1]} (already present)")
            return 0
        print(f"  ⚠️  uade is present but {len(missing)} generated file(s) are not; redoing")

    VENDOR.mkdir(parents=True, exist_ok=True)
    for name in SOURCES:
        if not clone(name):
            return 1

    place_configuration()
    if not generate_cpu():
        return 1

    missing = [f for f in GENERATED if not (VENDOR / "uade" / f).is_file()]
    if missing:
        print(f"  ❌ uade: these should have been generated and were not: {', '.join(missing)}")
        return 1

    stamp.write_text(revisions + "\n")
    lines = sum(1 for _ in (VENDOR / "uade" / "src" / "cpuemu.c").open(errors="ignore"))
    print(f"  ✅ uade {SOURCES['uade'][1]} (68000 emulator generated, {lines:,} lines)")
    return 0


if __name__ == "__main__":
    sys.exit(main())
