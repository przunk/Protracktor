#!/usr/bin/env python3
"""How much of Modland the platform table actually accounts for.

`Platforms.kt` decides what the search filter offers, and `docs/WISHLIST.md` B23 says the table can
be wrong in two directions: a platform nobody picks is a wasted chip, and a format filed under the
wrong platform **hides music**. The second is the one worth measuring, and the only honest way to do
it is against the archive rather than against memory.

Reports three things:

  coverage    what share of Modland's files land on some platform at all
  gaps        the biggest directories that land on none, largest first -- the table's to-do list
  per platform what each holds, and how much of it this build can actually play, so a chip that will
              be drawn greyed out is greyed for a reason somebody has seen

Usage:  ./scripts/probe-platforms.py [--gaps N]
"""
from __future__ import annotations

import argparse
import collections
import importlib.util
import pathlib
import re
import sys

ROOT = pathlib.Path(__file__).resolve().parent.parent
TABLE = ROOT / "app" / "src" / "main" / "kotlin" / "com" / "przunk" / "protracktor" / "player" / "Platforms.kt"


def shared():
    spec = importlib.util.spec_from_file_location("probe_uade", ROOT / "scripts" / "probe-uade.py")
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


def platforms() -> dict[str, dict[str, set[str]]]:
    """The table, read from the Kotlin rather than restated here.

    Comments are stripped first. An earlier probe in this project parsed a Kotlin list by splitting
    on the first `)` and silently took 28 of 90 names, because the comments contained brackets --
    so this refuses a result that looks too small rather than reporting a number built on half a
    table.
    """
    text = TABLE.read_text()
    text = re.sub(r"/\*.*?\*/", "", text, flags=re.S)
    text = re.sub(r"//[^\n]*", "", text)

    out: dict[str, dict[str, set[str]]] = {}
    for block in re.finditer(
        r'Platform\(\s*id\s*=\s*"([^"]+)",\s*name\s*=\s*"([^"]+)",\s*'
        r"catalogueFormats\s*=\s*(setOf\(.*?\)|emptySet\(\)),\s*"
        r"names\s*=\s*(setOf\(.*?\)|emptySet\(\)),?\s*\)",
        text,
        flags=re.S,
    ):
        pid, name, formats, names = block.groups()
        out[pid] = {
            "name": name,
            "formats": set(re.findall(r'"([^"]+)"', formats)),
            "names": set(re.findall(r'"([^"]+)"', names)),
        }
    if len(out) < 5:
        raise SystemExit(f"only {len(out)} platforms parsed from Platforms.kt -- the parser has "
                         "drifted from the file, fix it rather than trusting this")
    return out


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--gaps", type=int, default=15)
    args = parser.parse_args()

    s = shared()
    table = platforms()
    by_format = {f: pid for pid, p in table.items() for f in p["formats"]}

    extensions = s.supported_extensions()
    entries = s.modland_index()

    held: collections.Counter = collections.Counter()
    playable: collections.Counter = collections.Counter()
    unmapped: collections.Counter = collections.Counter()
    total = len(entries)
    mapped = 0
    for _size, path in entries:
        directory = path.split("/")[0].strip().lower()
        pid = by_format.get(directory)
        if pid is None:
            unmapped[path.split("/")[0]] += 1
            continue
        mapped += 1
        held[pid] += 1
        if s.claimed_today(path, extensions):
            playable[pid] += 1

    print(f"Modland: {total:,} files\n")
    print(f"  mapped to a platform: {mapped:,} ({mapped / total * 100:.1f}%)")
    print(f"  unmapped:             {total - mapped:,} in {len(unmapped)} directories\n")

    print("Per platform — held, and how much this build plays:\n")
    for pid, p in sorted(table.items(), key=lambda kv: -held[kv[0]]):
        n = held[pid]
        share = f"{playable[pid] / n * 100:5.1f}%" if n else "    — "
        flag = "" if playable[pid] else "   ← would be drawn greyed out"
        print(f"  {p['name']:<14} {n:>7,}  plays {playable[pid]:>7,} {share}{flag}")

    print(f"\nBiggest unmapped directories — the table's to-do list:\n")
    for name, n in unmapped.most_common(args.gaps):
        print(f"  {n:>7,}  {name}")
    tail = sum(n for _n, n in [(k, v) for k, v in unmapped.items()][args.gaps:])
    print(f"\n  {sum(unmapped.values()) - sum(n for _, n in unmapped.most_common(args.gaps)):,} "
          f"more in the remaining {max(len(unmapped) - args.gaps, 0)} directories")
    return 0


if __name__ == "__main__":
    sys.exit(main())
