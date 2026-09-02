#!/usr/bin/env python3
"""Measure sc68 2.2.1 against 3.0.0b on the same files.

`GOAL.md` round 5 item 1 asks whether sc68 3.0.0b is worth integrating, and says to decide on
evidence. The baseline it names -- 16/30 SNDH, 8/10 `.sc68` -- was measured on a set of files nobody
wrote down, so this does not compare against that number. It picks a set **now**, deterministically,
and runs **both** libraries over it. An A/B on one corpus is worth more than a B against a
remembered A.

Everything is cached and seeded, so a second run measures the same files and can be compared with
the first.

Usage:  ./scripts/probe-sc68.py [--sndh N] [--sc68 N] [--seed N] [--refetch]
"""
from __future__ import annotations

import argparse
import hashlib
import io
import os
import pathlib
import random
import subprocess
import sys
import urllib.parse
import urllib.request
import zipfile

ROOT = pathlib.Path(__file__).resolve().parent.parent
CACHE = pathlib.Path.home() / ".protracktor" / "sc68-probe"
INDEX_URL = "https://modland.com/allmods.zip"
FILE_BASE = "https://modland.com/pub/modules/"
TIMEOUT = 90


def modland_index() -> list[tuple[int, str]]:
    """(size, path) for everything Modland lists. Cached: it is 5.75 MB."""
    archive = CACHE / "allmods.zip"
    if not archive.is_file():
        CACHE.mkdir(parents=True, exist_ok=True)
        print("  downloading the Modland index…")
        with urllib.request.urlopen(INDEX_URL, timeout=TIMEOUT) as response:
            archive.write_bytes(response.read())

    entries: list[tuple[int, str]] = []
    with zipfile.ZipFile(archive) as zf:
        with zf.open(zf.namelist()[0]) as handle:
            for line in io.TextIOWrapper(handle, encoding="utf-8", errors="replace"):
                tab = line.find("\t")
                if tab <= 0:
                    continue
                try:
                    entries.append((int(line[:tab]), line[tab + 1:].rstrip("\n")))
                except ValueError:
                    continue
    return entries


def pick(entries: list[tuple[int, str]], suffix: str, count: int, seed: int) -> list[tuple[int, str]]:
    """A deterministic sample. Sorted first, so the seed alone decides the outcome."""
    matching = sorted((size, path) for size, path in entries if path.lower().endswith(suffix))
    return random.Random(seed).sample(matching, min(count, len(matching)))


def download(path: str) -> pathlib.Path | None:
    """One tune, cached under a hash of its path so odd filenames cannot collide or escape."""
    target = CACHE / "files" / hashlib.sha256(path.encode()).hexdigest()[:24]
    if target.is_file() and target.stat().st_size > 0:
        return target
    target.parent.mkdir(parents=True, exist_ok=True)
    url = FILE_BASE + "/".join(
        urllib.parse.quote(segment, safe="") for segment in path.split("/")
    ).replace("+", "%20")
    try:
        with urllib.request.urlopen(url, timeout=TIMEOUT) as response:
            target.write_bytes(response.read())
    except Exception:
        return None
    return target if target.stat().st_size > 0 else None


# Each library ships its own replay binaries, and each must be given its own. sc68 does not carry
# these routines inside the tunes: without the path, SNDH loads and plays silence, which would make
# the comparison a measurement of a missing directory rather than of a library.
SHARED = {
    "probe-2.2.1": ROOT / "native" / "vendor" / "sc68" / "data",
    "probe-3.0.0b": ROOT / "native" / "vendor" / "sc68-3" / "file68" / "data68",
}


def run_probe(binary: pathlib.Path, files: list[tuple[str, pathlib.Path]]) -> dict[str, str]:
    """Ask one probe binary about every file. It prints one verdict on stdout."""
    environment = dict(os.environ, SC68_SHARED_PATH=str(SHARED[binary.name]))
    out: dict[str, str] = {}
    for name, local in files:
        try:
            result = subprocess.run(
                [str(binary), str(local)], capture_output=True, text=True, timeout=60,
                env=environment,
            )
            verdict = (result.stdout.strip().split("\n") or [""])[0].strip() or "crash"
            if result.returncode != 0 and not verdict:
                verdict = f"exit{result.returncode}"
        except subprocess.TimeoutExpired:
            verdict = "timeout"
        except Exception as error:                                   # noqa: BLE001
            verdict = f"error:{error}"
        out[name] = verdict
    return out


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--sndh", type=int, default=30)
    parser.add_argument("--sc68", type=int, default=10)
    parser.add_argument("--seed", type=int, default=68)
    args = parser.parse_args()

    old = ROOT / "native" / "probe" / "sc68" / "probe-2.2.1"
    new = ROOT / "native" / "probe" / "sc68" / "probe-3.0.0b"
    for binary in (old, new):
        if not binary.is_file():
            print(f"❌ {binary.name} is not built. Run ./scripts/build-sc68-probes.sh first.")
            return 1

    print("Picking a corpus from the Modland index…")
    entries = modland_index()
    chosen = [(".sndh", pick(entries, ".sndh", args.sndh, args.seed)),
              (".sc68", pick(entries, ".sc68", args.sc68, args.seed))]

    for suffix, sample in chosen:
        print(f"\n=== {suffix} — {len(sample)} files (seed {args.seed})")
        local: list[tuple[str, pathlib.Path]] = []
        for _size, path in sample:
            got = download(path)
            if got:
                local.append((path, got))
        if len(local) < len(sample):
            print(f"  ({len(sample) - len(local)} could not be downloaded and are excluded)")

        before = run_probe(old, local)
        after = run_probe(new, local)

        def tally(results: dict[str, str]) -> dict[str, int]:
            counts: dict[str, int] = {}
            for verdict in results.values():
                counts[verdict] = counts.get(verdict, 0) + 1
            return counts

        print(f"  {'verdict':<12} {'2.2.1':>7} {'3.0.0b':>8}")
        for verdict in sorted(set(before.values()) | set(after.values())):
            print(f"  {verdict:<12} {tally(before).get(verdict, 0):>7} {tally(after).get(verdict, 0):>8}")

        changed = [(n, before[n], after[n]) for n in before if before[n] != after[n]]
        print(f"  changed verdict: {len(changed)} of {len(local)}")
        for name, was, now in changed[:12]:
            print(f"    {was:>8} → {now:<8} {name.split('/')[-1][:48]}")

    return 0


if __name__ == "__main__":
    sys.exit(main())
