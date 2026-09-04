#!/usr/bin/env python3
"""Measure the backend that carries most of the library, and had never been measured.

sc68 has 30 of 30, libsidplayfp 30 of 30, ASAP 12 of 12, game-music-emu 116 of 141 since
2026-09-04. **libopenmpt had no number at all** — and it is claimed for about 314,000 of Modland's
515,509 files, sixty-one per cent of everything the app says it can play. Measuring the four
backends that carry the remaining third while leaving this one on trust is exactly backwards.

The question is `OpenmptBackend`'s contract, not "does it load":

  is the first buffer full   -- the engine treats a short render as end-of-tune (`docs/review.md` R1)
  is there sound in it       -- a module that loads and plays silence is a module that failed

Sampled per extension, weighted by what Modland actually holds, and seeded so a second run measures
the same files.

Usage:  ./scripts/probe-openmpt.py [--files N] [--seed N]
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
PROBE = ROOT / "native" / "probe" / "openmpt" / "build" / "probe-openmpt"

# What the app hands this backend, biggest first. The long tail below `okt` is a handful of files
# each and is covered by `--files` naturally.
EXTENSIONS = [
    "mod", "xm", "it", "s3m", "ftm", "ahx", "dbm", "sfx", "smod", "symmod",
    "digi", "dsym", "fc", "med", "mptm", "gmc", "hvl", "puma", "tcb", "okt",
    "stk", "ice", "unic", "kris", "fc13", "fc14", "etx",
    "far", "gdm", "imf", "mdl", "mtm", "ptm", "stm", "ult", "669", "amf",
    "dsm", "dtm", "j2b", "mt2", "psm", "rtm", "mo3", "umx", "mmd0", "mmd1", "okta",
]


def shared():
    spec = importlib.util.spec_from_file_location("probe_uade", ROOT / "scripts" / "probe-uade.py")
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


def ask(path: pathlib.Path) -> str:
    try:
        result = subprocess.run([str(PROBE), str(path)], capture_output=True, text=True, timeout=120)
        # The tagged line, not the first: libopenmpt prints load errors to stdout ahead of it,
        # so reading line one files "openmpt: openmpt_module_create..." as a verdict.
        verdict = next((l[len("VERDICT "):].strip() for l in result.stdout.splitlines()
                        if l.startswith("VERDICT ")), "")
        return verdict or (f"exit{result.returncode}" if result.returncode else "crash")
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
        print(f"❌ {PROBE.name} is not built.")
        print("   cd native/vendor/libopenmpt && make -j CONFIG=gcc NO_ZLIB=1 NO_MPG123=1 …")
        return 1

    s = shared()
    entries = s.modland_index()
    by_extension: dict[str, list[str]] = collections.defaultdict(list)
    for _size, path in entries:
        name = path.rsplit("/", 1)[-1].lower()
        if "." in name:
            tail = name.rsplit(".", 1)[-1]
            if tail in EXTENSIONS:
                by_extension[tail].append(path)

    rng = random.Random(args.seed)
    results: dict[str, collections.Counter] = {}
    failures: list[str] = []

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
            head = verdict.split(" ", 1)[0]
            counts[head] += 1
            if head != "full":
                failures.append(f".{ext}\t{head}\t{path}")
            print(f"  [{i}/{len(sample)}] {head:<12} {path.rsplit('/', 1)[-1][:48]}")
        results[ext] = counts

    print("\n=== Result\n")
    grand: collections.Counter = collections.Counter()
    weighted_ok = weighted_total = 0
    for ext, counts in results.items():
        grand.update(counts)
        n = sum(counts.values())
        held = len(by_extension[ext])
        weighted_total += held
        if n:
            weighted_ok += held * counts.get("full", 0) / n
        flag = "  " if counts.get("full", 0) == n else "⚠️"
        print(f"  {flag} .{ext:<7} {counts.get('full', 0):>3}/{n:<3}  {held:>7,} files   "
              + ", ".join(f"{k}×{v}" for k, v in counts.most_common()))
    n = sum(grand.values())
    print(f"\n  Overall: {grand.get('full', 0)}/{n} played with a full first buffer and sound.")
    print(f"  Weighted by what Modland holds: about {weighted_ok / weighted_total * 100:.1f}% "
          f"of {weighted_total:,} files.")

    if failures:
        print(f"\n=== What failed ({len(failures)})\n")
        for line in failures:
            print("  " + line.replace("\t", "  "))

    (CACHE / "openmpt.json").write_text(json.dumps(
        {ext: dict(counts) for ext, counts in results.items()}, indent=2))
    return 0


if __name__ == "__main__":
    sys.exit(main())
