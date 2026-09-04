#!/usr/bin/env python3
"""Measure the console formats, which were never measured.

`docs/PLAN_FORMATS.md` §2 records how game-music-emu was vendored and built and **not one number
about whether it plays anything**. sc68 has 30 of 30, libsidplayfp 30 of 30, ASAP 12 of 12; seven
console families covering roughly 80,000 Modland files went in on build notes alone — and they are
also the part of the app nobody has ever listened to, so they reach a store listing on trust.

Two questions per file, both from `GmeBackend`'s contract rather than from hope:

  is the first buffer full   -- the engine treats a short render as end-of-tune (`docs/review.md` R1)
  does the track ever end    -- `gme_set_fade` is what makes `gme_track_ended` true, and the backend
                                sets it once, from track 0's length

`.vgz` is gzip around a `.vgm`. The host has zlib at runtime but no headers, so the probe is built
without it and this unwraps them here instead. That measures the VGM decoder, which is the part in
question; the gzip layer is zlib's and the Android build links the NDK's — checked in its
`CMakeCache.txt`, not assumed.

Usage:  ./scripts/probe-gme.py [--files N] [--seed N]
"""
from __future__ import annotations

import argparse
import collections
import gzip
import importlib.util
import json
import pathlib
import random
import subprocess
import sys

ROOT = pathlib.Path(__file__).resolve().parent.parent
CACHE = pathlib.Path.home() / ".protracktor" / "uade-probe"
PROBE = ROOT / "native" / "probe" / "gme" / "build" / "probe-gme"

# What `SupportedFormats` sends to this backend. Ordered by how much of Modland each is worth.
EXTENSIONS = ["spc", "vgz", "vgm", "nsf", "nsfe", "gbs", "ay", "kss", "hes", "gym"]


def shared():
    spec = importlib.util.spec_from_file_location("probe_uade", ROOT / "scripts" / "probe-uade.py")
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


def staged(local: pathlib.Path, name: str) -> pathlib.Path:
    """The file under a name gme will accept, unwrapping gzip where that is the only wrapper."""
    out = CACHE / "gme-staged"
    out.mkdir(parents=True, exist_ok=True)
    target = out / name
    data = local.read_bytes()
    if name.lower().endswith(".vgz") or data[:2] == b"\x1f\x8b":
        try:
            data = gzip.decompress(data)
        except OSError:
            pass
    target.write_bytes(data)
    return target


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
    parser.add_argument("--files", type=int, default=20)
    parser.add_argument("--seed", type=int, default=68)
    args = parser.parse_args()

    if not PROBE.is_file():
        print(f"❌ {PROBE.name} is not built. Run ./scripts/build-gme-probe.sh first.")
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

    print("Modland holds, of the formats this backend claims:\n")
    for ext in EXTENSIONS:
        print(f"  .{ext:<5} {len(by_extension.get(ext, [])):>7,}")
    print()

    rng = random.Random(args.seed)
    results: dict[str, collections.Counter] = {}
    details: dict[str, list[str]] = {}
    for ext in EXTENSIONS:
        paths = by_extension.get(ext) or []
        if not paths:
            continue
        sample = rng.sample(sorted(paths), min(args.files, len(paths)))
        counts: collections.Counter = collections.Counter()
        lines: list[str] = []
        print(f"=== .{ext} — {len(sample)} of {len(paths):,}")
        for i, path in enumerate(sample, 1):
            got = s.download(path)
            if not got:
                counts["unfetchable"] += 1
                continue
            verdict = ask(staged(got, path.rsplit("/", 1)[-1]))
            counts[verdict.split(" ", 1)[0]] += 1
            lines.append(f"{path.rsplit('/', 1)[-1]}\t{verdict}")
            print(f"  [{i}/{len(sample)}] {verdict.split(' ', 1)[0]:<12} {path.rsplit('/', 1)[-1][:48]}")
        results[ext] = counts
        details[ext] = lines

    print("\n=== Result\n")
    grand: collections.Counter = collections.Counter()
    for ext, counts in results.items():
        grand.update(counts)
        n = sum(counts.values())
        print(f"  .{ext:<5} {counts.get('full', 0):>3}/{n:<3}  "
              + ", ".join(f"{k}×{v}" for k, v in counts.most_common()))
    n = sum(grand.values())
    print(f"\n  Overall: {grand.get('full', 0)}/{n} played with a full first buffer and sound.")

    # Whether a track ever ends is the other half, and it decides whether the playlist moves on.
    never_ends = 0
    total_lines = 0
    for lines in details.values():
        for line in lines:
            if "\tfull" in line:
                total_lines += 1
                if "ended=no" in line and "stated=0ms" in line:
                    never_ends += 1
    print(f"  Of those, {never_ends}/{total_lines} state no length and had not ended after 8s —")
    print("  the case where nothing but the engine's own fade can stop them.")

    (CACHE / "gme.json").write_text(json.dumps(
        {ext: dict(counts) for ext, counts in results.items()}, indent=2))
    return 0


if __name__ == "__main__":
    sys.exit(main())
