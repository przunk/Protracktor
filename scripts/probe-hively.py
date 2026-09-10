#!/usr/bin/env python3
"""Measure whether HivelyTracker's replayer plays the AHX and HVL files the app dropped.

`ahx` and `hvl` were in `SupportedFormats.kt` until 2026-09-04 and were removed because nothing we
vendor plays them — the list was claiming files the app would then fail to open. That is the only
gap in the format table that is our own doing, so it is the one worth closing, and closing it starts
the way the other five did: a number on this machine before a line of app code.

Two things are measured, not one:

  **does it play**   loads from a buffer, first subsong audible, and whether the tune ever ends
                     -- and if it does, how long it is, which is a duration the app would otherwise
                     have to invent. The render is roughly 2000x realtime, so the window is ten
                     minutes rather than the eight seconds this started with; at eight, almost
                     nothing had ended yet and the answer looked like "it never ends".
  **is it portable** the same files through a second build whose `uint32` is 32 bits rather than
                     the host's 64. Upstream's `types.h` says `unsigned long`, which was 32 bits on
                     the Amiga this replayer comes from. If the two builds ever disagree, the
                     replayer depends on 32-bit wrapping and the app must carry the narrow
                     typedefs; if they never do, that is one less thing to find out on a phone.

Modland names these both ways round — `AHX.title` and `title.ahx` — which is why the survey counts
prefixes as well as extensions. Getting that wrong is what made UADE's reach look like 807 files
when it was 29,127.

Usage:  ./scripts/probe-hively.py [--files N] [--seed N]
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
BUILD = ROOT / "native" / "probe" / "hively" / "build"
BUILDS = {"native": BUILD / "probe-hively-native", "stdint": BUILD / "probe-hively-stdint"}

# What the app would claim again. Both are the replayer's own two header magics, THX and HVL.
NAMES = ["ahx", "hvl"]


def shared():
    spec = importlib.util.spec_from_file_location("probe_uade", ROOT / "scripts" / "probe-uade.py")
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


def marker(path: str) -> str | None:
    """Which of the names this file carries, at either end. Modland uses both conventions."""
    name = path.rsplit("/", 1)[-1].lower()
    if "." not in name:
        return None
    head, tail = name.split(".", 1)[0], name.rsplit(".", 1)[-1]
    if tail in NAMES:
        return tail
    if head in NAMES:
        return head
    return None


def ask(binary: pathlib.Path, path: pathlib.Path, seconds: int) -> str:
    try:
        result = subprocess.run([str(binary), "--seconds", str(seconds), str(path)],
                                capture_output=True, text=True, timeout=300)
        line = next((l[len("VERDICT "):].strip() for l in result.stdout.splitlines()
                     if l.startswith("VERDICT ")), "")
        return line or (f"exit{result.returncode}" if result.returncode else "crash")
    except subprocess.TimeoutExpired:
        return "timeout"
    except Exception as error:                                       # noqa: BLE001
        return f"error:{error}"


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--files", type=int, default=30)
    parser.add_argument("--seed", type=int, default=68)
    parser.add_argument("--seconds", type=int, default=600,
                        help="how long to render before giving up on a song end (hvl2wav uses 600)")
    args = parser.parse_args()

    missing = [name for name, path in BUILDS.items() if not path.is_file()]
    if missing:
        print(f"❌ not built: {', '.join(missing)}. Run ./scripts/build-hively-probe.sh first.")
        return 1

    s = shared()
    entries = s.modland_index()
    by_name: dict[str, list[str]] = collections.defaultdict(list)
    conventions: collections.Counter = collections.Counter()
    for _size, path in entries:
        found = marker(path)
        if found:
            by_name[found].append(path)
            leaf = path.rsplit("/", 1)[-1].lower()
            conventions[f"{found}:{'suffix' if leaf.rsplit('.', 1)[-1] == found else 'prefix'}"] += 1

    total = sum(len(v) for v in by_name.values())
    print(f"Modland holds {total:,} files these two names claim:\n")
    for name in NAMES:
        print(f"  {name:<5} {len(by_name.get(name, [])):>7,}")
    print("\n  by convention: " + ", ".join(f"{k}×{v:,}" for k, v in sorted(conventions.items())))
    print()

    rng = random.Random(args.seed)
    results: dict[str, collections.Counter] = {}
    details: dict[str, list[tuple[str, str, str]]] = {}
    for name in NAMES:
        paths = by_name.get(name) or []
        if not paths:
            continue
        sample = rng.sample(sorted(paths), min(args.files, len(paths)))
        counts: collections.Counter = collections.Counter()
        rows: list[tuple[str, str, str]] = []
        print(f"=== {name} — {len(sample)} of {len(paths):,}")
        for i, path in enumerate(sample, 1):
            got = s.download(path)
            if not got:
                counts["unfetchable"] += 1
                continue
            verdicts = {kind: ask(binary, got, args.seconds) for kind, binary in BUILDS.items()}
            counts[verdicts["native"].split(" ", 1)[0]] += 1
            leaf = path.rsplit("/", 1)[-1]
            rows.append((leaf, verdicts["native"], verdicts["stdint"]))
            flag = "" if verdicts["native"] == verdicts["stdint"] else "   ⚠ builds disagree"
            print(f"  [{i}/{len(sample)}] {verdicts['native'].split(' ', 1)[0]:<10} {leaf[:46]}{flag}")
        results[name] = counts
        details[name] = rows

    print("\n=== Result\n")
    grand: collections.Counter = collections.Counter()
    for name, counts in results.items():
        grand.update(counts)
        n = sum(counts.values())
        print(f"  {name:<5} {counts.get('full', 0):>3}/{n:<3}  "
              + ", ".join(f"{k}×{v}" for k, v in counts.most_common()))
    n = sum(grand.values())
    print(f"\n  Overall: {grand.get('full', 0)}/{n} loaded from a buffer and were audible.")

    played = [row for rows in details.values() for row in rows if row[1].startswith("full")]
    ended = [row for row in played if "ends=yes" in row[1]]
    multi = sum(1 for row in played if "subsongs=" in row[1]
                and int(row[1].split("subsongs=")[1].split()[0]) > 1)
    print(f"  Of those, {len(ended)}/{len(played)} reached a song end within {args.seconds}s, "
          f"and {multi} have more than one subsong.")
    if ended:
        lengths = sorted(int(row[1].split("len=")[1].split("s")[0]) for row in ended)
        mid = lengths[len(lengths) // 2]
        print(f"  Their lengths run {lengths[0]}s to {lengths[-1]}s, median {mid}s — a duration the"
              "\n  app can show without a song-length database.")

    def shape(verdict: str) -> str:
        """Everything the app depends on, with the peak left out.

        The peak is the loudest sample of a render; two builds whose noise generator wraps at
        different widths will differ there by a few counts and agree about the music. What must not
        differ is the verdict itself, the subsong count, whether it ends and how long it is."""
        return " ".join(field for field in verdict.split() if not field.startswith("peak="))

    hard = [row for rows in details.values() for row in rows if shape(row[1]) != shape(row[2])]
    soft = [row for rows in details.values() for row in rows
            if row[1] != row[2] and shape(row[1]) == shape(row[2])]
    print()
    if hard:
        print(f"  ⚠ {len(hard)} files disagree between the 64-bit and 32-bit builds about something"
              "\n    the app depends on. armeabi-v7a is one of our three ABIs, so this ships:")
        for leaf, native, stdint in hard[:10]:
            print(f"      {leaf}\n        long   {native}\n        int32  {stdint}")
    else:
        print("  The 64-bit and 32-bit builds agree on every file about what plays, how many"
              "\n  subsongs it has, whether it ends and how long it is.")
    if soft:
        print(f"  {len(soft)} of them render a slightly different peak — the two widths do not wrap"
              "\n  the same way — so arm64 and armeabi-v7a will not be sample-identical.")

    (CACHE / "hively.json").write_text(json.dumps(
        {name: dict(counts) for name, counts in results.items()}, indent=2))
    return 0


if __name__ == "__main__":
    sys.exit(main())
