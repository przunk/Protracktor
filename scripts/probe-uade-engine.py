#!/usr/bin/env python3
"""Play the formats the app offers through our own engine, on the host, before a phone is asked.

`probe-uade.py` measured **UADE**: 413 of 720, and that chose the format list. This measures **our
code around it** -- `UadeBackend` in `native/engine/engine.cpp`, compiled unchanged with the host
compiler and driven by `native/probe/engine/uade_drive.cpp` through the real `openBackend`. The
scratch directory, the render loop, zero-based subsongs, rewind, TFMX's companion and two emulators
at once are all ours, and without this they would be tried for the first time on a phone, where
"nothing plays" comes with no reason attached.

The data directory is laid out the way the app lays it out: UADE's three shipped files, and the
replay routines from **the same GitLab archive the app downloads**, pinned to the same revision.

Files come from the Modland index and the probe's own cache, seeded, so a second run plays the
same tunes.

Usage:  ./scripts/probe-uade-engine.py --drive PATH/uade-drive --core PATH/libuadecore.so [--per 4]
"""
from __future__ import annotations

import argparse
import collections
import os
import signal
import time
import importlib.util
import io
import pathlib
import random
import re
import shutil
import subprocess
import sys
import tarfile
import tempfile
import urllib.request

ROOT = pathlib.Path(__file__).resolve().parent.parent
WORK = pathlib.Path.home() / ".protracktor" / "uade-engine"
REVISION = "d40dcc7"  # the same pin as scripts/fetch-uade.py and UadePlayers.kt
ARCHIVE = (f"https://gitlab.com/uade-music-player/uade/-/archive/{REVISION}/"
           f"uade-{REVISION}.tar.gz?path=players")
SONG_CONF = "https://raw.githubusercontent.com/mvtiaine/audacious-uade/master/conf/song.conf"

spec = importlib.util.spec_from_file_location("probe", ROOT / "scripts" / "probe-uade.py")
probe = importlib.util.module_from_spec(spec)
spec.loader.exec_module(probe)


def offered() -> tuple[set[str], set[str]]:
    """The UADE names the app offers, read from `SupportedFormats.kt` rather than restated."""
    text = (ROOT / "app/src/main/kotlin/com/przunk/protracktor/player/SupportedFormats.kt").read_text()
    def block(name: str) -> set[str]:
        # Up to the first closing parenthesis outside a comment: the sets hold names, never calls.
        start = text.index(f"val {name}: Set<String> = setOf(") + len(f"val {name}: Set<String> = setOf(")
        body = re.sub(r"//.*", "", text[start:])
        return set(re.findall(r'"([a-z0-9.]+)"', body[:body.index(")")]))
    return block("uadeExtensions"), block("uadePrefixes")


def base_dir() -> pathlib.Path:
    base = WORK / "base"
    players = base / "players"
    if players.is_dir() and any(players.iterdir()):
        return base
    base.mkdir(parents=True, exist_ok=True)
    vendor = ROOT / "native" / "vendor" / "uade"
    shutil.copy(vendor / "amigasrc" / "score" / "score", base)
    shutil.copy(vendor / "uaerc", base)
    shutil.copy(vendor / "eagleplayer.conf", base)
    players.mkdir(exist_ok=True)
    print("⬇  replay routines, from the archive the app downloads")
    with urllib.request.urlopen(ARCHIVE, timeout=120) as response:
        data = response.read()
    with tarfile.open(fileobj=io.BytesIO(data), mode="r:gz") as tar:
        for member in tar.getmembers():
            # The same rule as UadePlayers.unpackPlayers: everything under `players/`, keeping
            # `ENV/EaglePlayer/`, and nothing that climbs out.
            relative = member.name.split("/players/", 1)[1] if "/players/" in member.name else ""
            parts = relative.split("/")
            if member.isfile() and relative and not any(p in ("", ".", "..") for p in parts):
                target = players / relative
                target.parent.mkdir(parents=True, exist_ok=True)
                target.write_bytes(tar.extractfile(member).read())
    try:
        with urllib.request.urlopen(SONG_CONF, timeout=60) as response:
            (base / "song.conf").write_bytes(response.read())
    except Exception:
        print("  ⚠️  no song.conf; Hippel and TFMX variants may be misidentified")
    print(f"   {sum(1 for p in players.iterdir() if p.is_file())} replay routines, {len(data):,} bytes downloaded")
    return base


def staged(path: str) -> pathlib.Path | None:
    """The tune under its Modland name -- the name is the identification for half these formats."""
    local = probe.download(path)
    if local is None:
        return None
    target = WORK / "files" / path
    target.parent.mkdir(parents=True, exist_ok=True)
    if not target.exists():
        shutil.copy(local, target)
    return target


def companion_of(path: str) -> str | None:
    directory, _, name = path.rpartition("/")
    head, dot, rest = name.partition(".")
    wanted = probe.COMPANIONS.get(head.lower())
    return f"{directory}/{wanted}{dot}{rest}" if wanted and dot else None


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--drive", required=True)
    parser.add_argument("--core", required=True)
    parser.add_argument("--per", type=int, default=4, help="files per offered name")
    parser.add_argument("--seed", type=int, default=19)
    args = parser.parse_args()

    extensions, prefixes = offered()
    print(f"The app offers {len(extensions)} UADE extensions and {len(prefixes)} prefixes.")
    # **Under a directory that may be passed through and not listed**, as `/data` is to an app on
    # Android. UADE's own file search walked the path from `/` listing each directory, which works
    # everywhere on a desktop and nowhere on a phone; TFMX's samples were "not found" beside the
    # tune there and nowhere else. Both the data directory and the scratch directory live under
    # this, so the host can no longer pass what a phone cannot.
    locked = WORK / "locked"
    if locked.exists():
        locked.chmod(0o755)
        shutil.rmtree(locked)
    inner = locked / "inner"
    shutil.copytree(base_dir(), inner / "base")
    base = inner / "base"
    scratch = inner / "scratch"
    scratch.mkdir()
    locked.chmod(0o111)
    # **An empty home directory, every run.** UADE remembers the length of every tune it has played
    # to the end, in `$HOME/.uade/contentdb`, and reports it from the first frame. Earlier runs had
    # filled that file, so a length arrived here without the app's measurement doing anything --
    # and the phone, with no such file, showed none.
    home = pathlib.Path(tempfile.mkdtemp(prefix="uade-home-"))
    env = {"UADE_CORE_FILE": args.core, "UADE_BASE_DIR": str(base), "UADE_SCRATCH_DIR": str(scratch),
           "PATH": "/usr/bin:/bin", "HOME": str(home)}

    by_marker: dict[str, list[str]] = collections.defaultdict(list)
    for _size, path in probe.modland_index():
        head, tail = probe.markers_of(path)
        if tail in extensions:
            by_marker["." + tail].append(path)
        elif head in prefixes:
            by_marker[head + "."].append(path)

    rng = random.Random(args.seed)
    tally = collections.Counter()
    failures = []
    played = []
    for marker in sorted(by_marker):
        paths = by_marker[marker]
        sample = rng.sample(paths, min(args.per, len(paths)))
        for path in sample:
            local = staged(path)
            if local is None:
                tally["not downloaded"] += 1
                continue
            command = [args.drive, "play", str(local)]
            companion = companion_of(path)
            if companion:
                extra = staged(companion)
                if extra is not None:
                    command.append(str(extra))
            try:
                out = subprocess.run(command, env=env, capture_output=True, text=True, errors="replace", timeout=120).stdout
            except subprocess.TimeoutExpired:
                out = "VERDICT fail timeout"
            verdict = next((l for l in reversed(out.splitlines()) if l.startswith("VERDICT")),
                           "VERDICT fail no-verdict")
            ok = verdict.startswith("VERDICT ok")
            tally["ok" if ok else verdict.split()[2]] += 1
            print(f"  {'✓' if ok else '✗'} {marker:<7} {path.rsplit('/', 1)[-1][:40]:<40} "
                  f"{verdict[8:][:120]}")
            if ok:
                played.append(local)
            else:
                failures.append((marker, path, verdict))

    print("\n=== two at once, as playback and a folder scan are")
    for a, b in zip(played[::2][:5], played[1::2][:5]):
        out = subprocess.run([args.drive, "pair", str(a), str(b)], env=env,
                             capture_output=True, text=True, errors="replace", timeout=120).stdout
        verdict = next((l for l in reversed(out.splitlines()) if l.startswith("VERDICT")), "VERDICT fail")
        tally["pair ok" if verdict.startswith("VERDICT ok") else "pair fail"] += 1
        print(f"  {a.name[:30]} + {b.name[:30]}: {verdict[8:]}")

    # **The emulator dying must not take the host with it** -- the whole reason UADE runs as its
    # own process. On the phone it did: libuade writes to uadecore over a socket, a write to a
    # dead peer raises SIGPIPE, and SIGPIPE ends the process. So uadecore is killed here at several
    # moments during a walk through the subsongs, and the driver must end by returning, never by a
    # signal.
    print("\n=== uadecore killed mid-tune")
    # A tune with seven subsongs, so the walk lasts long enough to be interrupted: the first
    # version picked any `cust.` file, which ended before the second kill and "passed" having
    # killed nothing. A kill that found no emulator is counted as a failure of the check.
    walker = next((p for p in played if p.name == "cust.paradroid"), None)
    if walker is None:
        walker = staged("Delitracker Custom/TSM/paradroid/cust.paradroid")
    if walker is None:
        failures.append(("kill", "cust.paradroid", "not available to walk"))
    else:
        for delay in (0.6, 1.5, 3.0):
            proc = subprocess.Popen([args.drive, "walk", str(walker)], env=env,
                                    stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
            time.sleep(delay)
            children = subprocess.run(["pgrep", "-P", str(proc.pid)], capture_output=True,
                                      text=True).stdout.split()
            for child in children:
                try:
                    os.kill(int(child), signal.SIGKILL)
                except ProcessLookupError:
                    pass
            code = proc.wait(timeout=120)
            if not children:
                tally["survives UNTESTED"] += 1
                failures.append(("kill", str(walker), f"nothing to kill after {delay}s"))
                print(f"  nothing to kill after {delay}s -- the check did not run")
                continue
            survived = code >= 0
            tally["survives ok" if survived else "survives FAIL"] += 1
            print(f"  killed {len(children)} after {delay}s: "
                  f"{'returned ' + str(code) if survived else 'died of ' + signal.Signals(-code).name}")
            if not survived:
                failures.append(("kill", str(walker), f"died of {signal.Signals(-code).name}"))

    leftovers = list(scratch.iterdir())
    print(f"\nscratch directory after everything: {len(leftovers)} entries")
    print("\n" + ", ".join(f"{k}×{v}" for k, v in tally.most_common()))
    locked.chmod(0o755)
    shutil.rmtree(locked, ignore_errors=True)
    shutil.rmtree(home, ignore_errors=True)
    return 0 if not leftovers and not failures else 1


if __name__ == "__main__":
    sys.exit(main())
