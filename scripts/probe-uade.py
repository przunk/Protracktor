#!/usr/bin/env python3
"""Measure what UADE adds, on a real Modland corpus.

`GOAL.md` round 6 item 1 asks for a pass rate before anything is integrated. The question it asks
is deliberately narrower than "what can UADE play": libopenmpt already handles AHX, Future
Composer, Puma, TCB and a dozen other Amiga formats, so UADE's worth is **what it adds to what we
already play**, not what it can play in total.

So this runs in two passes:

  reach   -- count, over the **whole** index rather than a sample, how many files Protracktor
             cannot play today carry a name that UADE's own `eagleplayer.conf` claims. Both ends
             of the name count: UADE declares Amiga prefixes (`dw.title`) and Modland stores some
             formats that way and others reversed (`title.dw`).
  play    -- sample from that set and actually render it, for a pass rate worth quoting.

**Multifile is why this downloads companions.** TFMX is `mdat.name` beside `smpl.name`, and a
`mdat` handed over alone reports "unsupported" -- which is how the first run of this measurement
made Modland's largest Amiga custom format look unplayable.

Everything is cached and seeded, so a second run measures the same files.

Usage:  ./scripts/probe-uade.py [--survey N] [--deep N] [--formats N] [--seed N]
"""
from __future__ import annotations

import argparse
import collections
import hashlib
import io
import json
import os
import pathlib
import random
import re
import subprocess
import sys
import urllib.parse
import urllib.request
import zipfile

ROOT = pathlib.Path(__file__).resolve().parent.parent
CACHE = pathlib.Path.home() / ".protracktor" / "uade-probe"
SHARED_INDEX = pathlib.Path.home() / ".protracktor" / "sc68-probe" / "allmods.zip"
INDEX_URL = "https://modland.com/allmods.zip"
FILE_BASE = "https://modland.com/pub/modules/"
PROBE = ROOT / "native" / "probe" / "uade" / "build" / "probe-uade"
TIMEOUT = 90


def supported_extensions() -> set[str]:
    """Read them out of the app rather than restating them.

    A copy of this list in a script is a copy that goes stale the day a backend lands, and the
    whole point of the measurement is "what we cannot play *today*".
    """
    source = (ROOT / "app/src/main/kotlin/com/przunk/protracktor/player/SupportedFormats.kt").read_text()
    tail = source.split("val extensions: Set<String> = setOf(", 1)[1]
    # Comments first, then the closing bracket. Splitting on the first ")" read the list as ending
    # inside a comment the moment one of them cited a document in parentheses, and returned 28
    # extensions instead of 85 -- silently, which is the part worth guarding against.
    lines = []
    for line in tail.splitlines():
        stripped = line.strip()
        if stripped.startswith("//"):
            continue
        if stripped.startswith(")"):
            break
        lines.append(line)
    found = {m.lower() for m in re.findall(r'"([^"]+)"', "\n".join(lines))}
    if len(found) < 40:
        raise SystemExit(f"only {len(found)} extensions parsed from SupportedFormats.kt -- "
                         "the parser has drifted from the file, fix it rather than trusting this")
    return found


def modland_index() -> list[tuple[int, str]]:
    """(size, path) for everything Modland lists."""
    archive = CACHE / "allmods.zip"
    if not archive.is_file():
        CACHE.mkdir(parents=True, exist_ok=True)
        if SHARED_INDEX.is_file():
            archive.write_bytes(SHARED_INDEX.read_bytes())
        else:
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


def uade_prefixes() -> dict[str, str]:
    """prefix -> eagleplayer name, from UADE's own table.

    Read from the installed `eagleplayer.conf` rather than restated here, for the same reason the
    app's extension list is read from the app: a second copy is a copy that goes stale.
    """
    base = pathlib.Path(os.environ["UADE_BASE_DIR"]) / "eagleplayer.conf"
    out: dict[str, str] = {}
    for line in base.read_text(errors="replace").splitlines():
        if not line.strip() or line.lstrip().startswith("#"):
            continue
        fields = line.split()
        name = fields[0]
        for field in fields[1:]:
            if field.startswith("prefixes="):
                for prefix in field[len("prefixes="):].split(","):
                    if prefix:
                        out[prefix.lower()] = name
    return out


# The companion half of a multifile song, keyed by the prefix of the half Modland indexes.
COMPANIONS = {"mdat": "smpl", "tfmx": "smpl", "tfmx1.5": "smpl", "tfmx7v": "smpl"}


def claimed_today(path: str, extensions: set[str]) -> bool:
    """The same two rules the app uses: the extension, or the prefix before the first dot.

    Modland names ProTracker files `mod.title`, so the prefix rule is not a curiosity.
    """
    name = path.rsplit("/", 1)[-1].lower()
    return (name.rsplit(".", 1)[-1] if "." in name else "") in extensions \
        or (name.split(".", 1)[0] if "." in name else "") in extensions


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


def markers_of(path: str) -> tuple[str, str]:
    """Both ends of the name, because Modland uses both conventions.

    UADE declares `dw`, `hip`, `mdat` as *prefixes* -- the Amiga convention, `dw.alfred chicken`.
    Modland stores TFMX that way (`mdat.unicorn`) but stores David Whittaker and Hippel the other
    way round (`alfred chicken.dw`, `5th gear.hip`). Checking only the prefix counted 807 files
    where the truth is several times that, and would have argued this backend was not worth
    building.
    """
    name = path.rsplit("/", 1)[-1].lower()
    if "." not in name:
        return "", ""
    return name.split(".", 1)[0], name.rsplit(".", 1)[-1]


def prefix_of(path: str) -> str:
    return markers_of(path)[0]


def marker_in(path: str, prefixes: dict[str, str]) -> str | None:
    """The eagleplayer that claims this name, from either end."""
    head, tail = markers_of(path)
    return prefixes.get(head) or prefixes.get(tail)


def ask(local: pathlib.Path, original: str) -> str:
    """One verdict from the probe.

    The file is presented under its **Modland name**, not its cache name: several Amiga formats are
    recognised by a `PREFIX.` filename convention and by nothing in the bytes, so a hashed name
    would measure the naming, not the library.
    """
    staged = CACHE / "staged"
    staged.mkdir(parents=True, exist_ok=True)
    named = staged / original.rsplit("/", 1)[-1]
    written = [named]
    try:
        named.write_bytes(local.read_bytes())
        # The other half of a multifile song, beside it, under its real name -- uadecore asks the
        # loader for it by name and gets nothing if it is not there.
        companion = COMPANIONS.get(prefix_of(original))
        if companion:
            folder, base = original.rsplit("/", 1)
            other = f"{folder}/{companion}.{base.split('.', 1)[1]}"
            fetched = download(other)
            if fetched:
                beside = staged / other.rsplit("/", 1)[-1]
                beside.write_bytes(fetched.read_bytes())
                written.append(beside)
    except OSError:
        return "unstageable"
    try:
        result = subprocess.run(
            [str(PROBE), str(named)], capture_output=True, text=True, timeout=120,
            env=dict(os.environ),
        )
        # The tagged line, not the first line: libuade prints its own warnings to stdout ahead of
        # the verdict, and reading the first line would file a warning as a result.
        verdict = next((l[len("VERDICT "):].strip() for l in result.stdout.splitlines()
                        if l.startswith("VERDICT ")), "")
        return verdict or (f"exit{result.returncode}" if result.returncode else "crash")
    except subprocess.TimeoutExpired:
        return "timeout"
    except Exception as error:                                       # noqa: BLE001
        return f"error:{error}"
    finally:
        for f in written:
            f.unlink(missing_ok=True)


def verdict_of(line: str) -> str:
    """The first word is the verdict; everything after it is detail."""
    return line.split(" ", 1)[0] if line else "empty"


def measure(sample: list[str], label: str) -> dict[str, str]:
    results: dict[str, str] = {}
    for i, path in enumerate(sample, 1):
        got = download(path)
        if not got:
            results[path] = "unfetchable"
            continue
        results[path] = ask(got, path)
        print(f"    [{i}/{len(sample)}] {verdict_of(results[path]):<12} {path.rsplit('/', 1)[-1][:52]}")
    return results


def tally(results: dict[str, str]) -> collections.Counter:
    return collections.Counter(verdict_of(v) for v in results.values())


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--play", type=int, default=20, help="files per format in the play pass")
    parser.add_argument("--formats", type=int, default=25, help="how many claimed formats to play")
    parser.add_argument("--seed", type=int, default=68)
    parser.add_argument("--reach-only", action="store_true")
    args = parser.parse_args()

    if not PROBE.is_file():
        print(f"❌ {PROBE.name} is not built. Run ./scripts/build-uade-probe.sh first.")
        return 1
    if not os.environ.get("UADE_BASE_DIR"):
        print("❌ UADE_BASE_DIR is not set. ./scripts/build-uade-probe.sh prints what to export.")
        return 1

    extensions = supported_extensions()
    prefixes = uade_prefixes()
    print(f"Reading the Modland index…")
    print(f"  the app claims {len(extensions)} extensions; UADE declares {len(prefixes)} prefixes "
          f"across {len(set(prefixes.values()))} players.\n")
    entries = modland_index()

    total = len(entries)
    claimed_count = 0
    reachable: dict[str, list[str]] = collections.defaultdict(list)
    unreachable = 0
    for _size, path in entries:
        if claimed_today(path, extensions):
            claimed_count += 1
        elif marker_in(path, prefixes):
            reachable[path.split("/")[0]].append(path)
        else:
            unreachable += 1

    reach_total = sum(len(v) for v in reachable.values())
    print(f"=== Reach — counted over all {total:,} files Modland lists, not sampled\n")
    print(f"  {claimed_count:>7,} ({claimed_count * 100 / total:4.1f}%)  a backend we already have claims it")
    print(f"  {reach_total:>7,} ({reach_total * 100 / total:4.1f}%)  we cannot play it and UADE declares its prefix")
    print(f"  {unreachable:>7,} ({unreachable * 100 / total:4.1f}%)  we cannot play it and UADE does not claim it either\n")

    ranked = sorted(reachable.items(), key=lambda kv: -len(kv[1]))
    print(f"  UADE reaches into {len(ranked)} of Modland's format directories. The largest:\n")
    for fmt, paths in ranked[:25]:
        players = collections.Counter(marker_in(p, prefixes) for p in paths)
        print(f"    {fmt:<32} {len(paths):>6,}  {', '.join(n for n, _ in players.most_common(3))}")

    (CACHE / "reach.json").write_text(json.dumps(
        {fmt: len(paths) for fmt, paths in ranked}, indent=2))

    if args.reach_only:
        return 0

    chosen = ranked[:args.formats]
    print(f"\n=== Play — {args.play} files from each of the {len(chosen)} largest, actually rendered\n")
    rng = random.Random(args.seed)
    played: dict[str, collections.Counter] = {}
    for fmt, paths in chosen:
        sample = rng.sample(sorted(paths), min(args.play, len(paths)))
        print(f"  {fmt}  ({len(paths):,} files)")
        played[fmt] = tally(measure(sample, fmt))

    print("\n=== Play result\n")
    grand = collections.Counter()
    covered = 0
    for fmt, paths in chosen:
        counts = played[fmt]
        grand.update(counts)
        n = sum(counts.values())
        good = counts.get("full", 0)
        if good:
            covered += len(paths)
        print(f"  {fmt:<32} {good:>3}/{n:<3}  "
              + ", ".join(f"{k}×{v}" for k, v in counts.most_common()))
    n = sum(grand.values())
    print(f"\n  Overall: {grand.get('full', 0)}/{n} rendered a full first buffer with sound.")
    print("  " + ", ".join(f"{k}×{v}" for k, v in grand.most_common()))
    print(f"\n  Formats with at least one success cover {covered:,} Modland files "
          f"that nothing here plays today.")

    (CACHE / "play.json").write_text(json.dumps(
        {fmt: dict(counts) for fmt, counts in played.items()}, indent=2))
    return 0


if __name__ == "__main__":
    sys.exit(main())
