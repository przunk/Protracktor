#!/usr/bin/env python3
"""Measure whether ZXTune plays the ZX Spectrum formats this build cannot.

`./scripts/probe-platforms.py` says ZX Spectrum holds **23,891 Modland files and 58 of them open**.
That is the largest dark platform in the archive, and the only large one that is a chiptune in the
same sense as everything else here: `.pt3`, `.pt2`, `.stc`, `.asc` and `.sqt` are trackers driving
an AY-3-8912, not console emulators.

The same two questions the other probes ask, from `Backend`'s contract rather than from hope:

  does it play    loads from a buffer, and is there sound in the first eight seconds
  does it end     `Information::Duration`, and whether `Renderer::Render` returns an empty chunk
                  within the window -- if the library states a length, these files arrive with a
                  seek bar the way AHX did, and nothing has to be stored to give them one

This is the second library measured for this platform. ayfly scored 46/48 and cannot be shipped
(`docs/PLAN_FORMATS.md` §7): no licence on its player headers, GPL-2-only on its Z80 emulator.
ZXTune is LGPL-3. The point of running the same corpus through both is that the comparison is then
about the libraries rather than about two different samples.

Sampled per extension and weighted by what Modland actually holds, so the headline number is about
the archive rather than about whichever format happened to be sampled.

Usage:  ./scripts/probe-zxtune.py [--files N] [--seed N]
"""
from __future__ import annotations

import argparse
import collections
import importlib.util
import json
import pathlib
import random
import subprocess
import sys

ROOT = pathlib.Path(__file__).resolve().parent.parent
CACHE = pathlib.Path.home() / ".protracktor" / "uade-probe"
PROBE = ROOT / "native" / "probe" / "zxtune" / "build" / "probe-zxtune"

# What zxtune's `players/` covers, ordered by how much of Modland's Spectrum tree each is worth.
# `.ym` and `.vtx` are register dumps rather than trackers and were added on 2026-09-09, when
# vendoring lhasa made ZXTune's `ym_vtx` decoder buildable. They are the whole point of that work:
# `docs/STATUS.md` C20 recorded 4,961 `.ym` files indexed and unopenable.
EXTENSIONS = ["pt3", "pt2", "stc", "asc", "sqt", "stp", "psm", "ftc", "gtr", "pt1", "ym", "vtx"]


def shared():
    spec = importlib.util.spec_from_file_location("probe_uade", ROOT / "scripts" / "probe-uade.py")
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


def ask(path: pathlib.Path) -> str:
    try:
        result = subprocess.run([str(PROBE), str(path)], capture_output=True, text=True, timeout=120)
        line = next((l[len("VERDICT "):].strip() for l in result.stdout.splitlines()
                     if l.startswith("VERDICT ")), "")
        return line or (f"exit{result.returncode}" if result.returncode else "crash")
    except subprocess.TimeoutExpired:
        return "timeout"
    except Exception as error:                                       # noqa: BLE001
        return f"error:{error}"


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--files", type=int, default=12)
    parser.add_argument("--seed", type=int, default=68)
    args = parser.parse_args()

    if not PROBE.is_file():
        print(f"❌ {PROBE.name} is not built. Run ./scripts/build-zxtune-probe.sh first.")
        return 1

    s = shared()
    entries = s.modland_index()
    by_extension: dict[str, list[str]] = collections.defaultdict(list)
    for _size, path in entries:
        name = path.rsplit("/", 1)[-1].lower()
        if "." in name and name.rsplit(".", 1)[-1] in EXTENSIONS:
            by_extension[name.rsplit(".", 1)[-1]].append(path)

    held = {ext: len(by_extension.get(ext, [])) for ext in EXTENSIONS}
    print("Modland holds, of the formats zxtune claims:\n")
    for ext in EXTENSIONS:
        print(f"  .{ext:<4} {held[ext]:>7,}")
    print(f"\n  {sum(held.values()):,} files in total\n")

    rng = random.Random(args.seed)
    results: dict[str, collections.Counter] = {}
    lengths: list[int] = []
    for ext in EXTENSIONS:
        paths = by_extension.get(ext) or []
        if not paths:
            continue
        sample = rng.sample(sorted(paths), min(args.files, len(paths)))
        counts: collections.Counter = collections.Counter()
        print(f"=== .{ext} — {len(sample)} of {len(paths):,}")
        for i, path in enumerate(sample, 1):
            got = s.download(path)
            if not got:
                counts["unfetchable"] += 1
                continue
            verdict = ask(got)
            counts[verdict.split(" ", 1)[0]] += 1
            if verdict.startswith("full") and "len=" in verdict:
                lengths.append(int(verdict.split("len=")[1].split("s")[0]))
            print(f"  [{i}/{len(sample)}] {verdict.split(' ', 1)[0]:<10} {path.rsplit('/', 1)[-1][:44]}")
        results[ext] = counts

    print("\n=== Result\n")
    grand: collections.Counter = collections.Counter()
    covered = 0
    played_weight = 0
    for ext, counts in results.items():
        grand.update(counts)
        n = sum(counts.values())
        full = counts.get("full", 0)
        covered += held[ext]
        played_weight += held[ext] * (full / n if n else 0)
        print(f"  .{ext:<4} {full:>3}/{n:<3}  " + ", ".join(f"{k}×{v}" for k, v in counts.most_common()))
    n = sum(grand.values())
    print(f"\n  Overall: {grand.get('full', 0)}/{n} loaded from a buffer and were audible.")
    if covered:
        print(f"  Weighted by what Modland holds: about {played_weight / covered * 100:.1f}% of "
              f"{covered:,} files.")

    stated = [x for x in lengths if x > 0]
    print(f"  {len(stated)}/{len(lengths)} state a length" +
          (f", median {sorted(stated)[len(stated) // 2]}s — a duration and a seek bar without a"
           "\n  song-length database." if stated else " — so a duration would have to come from elsewhere."))

    (CACHE / "zxtune.json").write_text(json.dumps(
        {ext: dict(counts) for ext, counts in results.items()}, indent=2))
    return 0


if __name__ == "__main__":
    sys.exit(main())
