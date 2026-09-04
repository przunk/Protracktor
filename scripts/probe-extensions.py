#!/usr/bin/env python3
"""What we could already play, if the app ever looked at the file.

This came out of measuring UADE and turned out to matter more than the thing it came from.

`SupportedFormats.extensions` decides whether a file is scanned, indexed or offered at all. It lists
`med`; Modland stores OctaMED as `.mmd0`…`.mmd3`. libopenmpt identifies MED by an "MMD" magic in the
header and never looks at the name -- so those files play perfectly and the app simply never hands
them over. That is not a missing backend. It is a missing line in a list.

So this asks, for every Modland format directory the app does not claim today: does the backend we
already ship play it anyway? Anything that comes back `full` is free.

Usage:  ./scripts/probe-extensions.py [--files N] [--formats N] [--seed N]
"""
from __future__ import annotations

import argparse
import collections
import json
import os
import pathlib
import subprocess
import sys

sys.path.insert(0, str(pathlib.Path(__file__).resolve().parent))

ROOT = pathlib.Path(__file__).resolve().parent.parent
CACHE = pathlib.Path.home() / ".protracktor" / "uade-probe"
PROBE = ROOT / "native" / "probe" / "openmpt" / "build" / "probe-openmpt"


def _load_shared():
    """Reuse probe-uade.py's index and download plumbing rather than copying it."""
    import importlib.util
    spec = importlib.util.spec_from_file_location("probe_uade", ROOT / "scripts" / "probe-uade.py")
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


def ask(local: pathlib.Path) -> str:
    """One verdict. No filename is passed on: the question is whether the content is enough."""
    try:
        result = subprocess.run([str(PROBE), str(local)], capture_output=True, text=True, timeout=90)
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
    parser.add_argument("--files", type=int, default=6)
    parser.add_argument("--formats", type=int, default=60)
    parser.add_argument("--seed", type=int, default=68)
    args = parser.parse_args()

    if not PROBE.is_file():
        print(f"❌ {PROBE.name} is not built.")
        print("   cd native/vendor/libopenmpt && make -j CONFIG=gcc NO_ZLIB=1 NO_MPG123=1 …")
        return 1

    shared = _load_shared()
    extensions = shared.supported_extensions()
    entries = shared.modland_index()

    unclaimed: dict[str, list[str]] = collections.defaultdict(list)
    for _size, path in entries:
        if not shared.claimed_today(path, extensions):
            unclaimed[path.split("/")[0]].append(path)

    ranked = sorted(unclaimed.items(), key=lambda kv: -len(kv[1]))[:args.formats]
    print(f"Asking libopenmpt about {args.files} files from each of the {len(ranked)} largest "
          f"formats the app does not claim.\n")

    import random
    rng = random.Random(args.seed)
    results: dict[str, collections.Counter] = {}
    for fmt, paths in ranked:
        sample = rng.sample(sorted(paths), min(args.files, len(paths)))
        counts: collections.Counter = collections.Counter()
        detail = []
        for path in sample:
            got = shared.download(path)
            if not got:
                counts["unfetchable"] += 1
                continue
            line = ask(got)
            counts[line.split(" ", 1)[0]] += 1
            if line.startswith("full") and 'fmt="' in line:
                detail.append(line.split('fmt="', 1)[1].split('"', 1)[0])
        results[fmt] = counts
        good = counts.get("full", 0)
        mark = "✅" if good else "  "
        named = collections.Counter(detail).most_common(1)
        print(f"  {mark} {fmt:<34} {good}/{sum(counts.values())}  "
              f"{', '.join(f'{k}×{v}' for k, v in counts.most_common(3))}"
              f"{'   → ' + named[0][0] if named else ''}")

    print("\n=== Free wins — formats libopenmpt plays that the app never offers\n")
    free = [(fmt, len(unclaimed[fmt])) for fmt, counts in results.items() if counts.get("full")]
    free.sort(key=lambda kv: -kv[1])
    for fmt, n in free:
        counts = results[fmt]
        print(f"  {fmt:<34} {n:>7,} files   {counts.get('full')}/{sum(counts.values())} sampled play")
    print(f"\n  {len(free)} formats, {sum(n for _f, n in free):,} Modland files, no new backend.")

    (CACHE / "extensions.json").write_text(json.dumps(
        {fmt: dict(counts) for fmt, counts in results.items()}, indent=2))
    return 0


if __name__ == "__main__":
    sys.exit(main())
