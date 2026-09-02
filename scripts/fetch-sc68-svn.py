#!/usr/bin/env python3
"""Fetch a pinned revision of the sc68 SVN tree over plain HTTP.

sc68 3.0.0b exists only in SourceForge SVN. There is no git mirror, the author's GitHub does not
carry it, and the snapshot zip 404s -- but SourceForge serves the repository as ordinary HTML
directory listings, and those walk. `svn` is not installed on this machine and this needs no more
than `urllib`.

Two things make it a tool rather than an experiment, which is what `GOAL.md` round 5 item 1 asks
for:

* **The revision is pinned.** Every URL carries `?p=713`, so a run tomorrow fetches what a run today
  fetched, whatever the author does upstream.
* **The content is verified.** Every file's SHA-256 goes into a manifest; a second run checks what
  is on disk against it and re-fetches only what differs. A truncated download cannot survive
  quietly into a build.

Most of the tree is ballast: `sc68-fb2k` alone is 348 MB of foobar2000 SDK binaries. The plugin and
documentation directories are skipped by name -- see `EXCLUDE`.

Usage:  ./scripts/fetch-sc68-svn.py [--force] [--check]
"""
from __future__ import annotations

import concurrent.futures
import hashlib
import html.parser
import pathlib
import sys
import urllib.error
import urllib.request

BASE = "https://svn.code.sf.net/p/sc68/code"

# Pinned deliberately. Bump it only with a measurement to justify the change, and say so in the
# commit: everything measured about this library was measured against this tree.
REVISION = 713

# Player plugins and their vendored SDKs. Nothing here is needed to build the library, and
# `sc68-fb2k` alone is roughly eight times the size of everything we do need.
EXCLUDE = {
    "sc68-fb2k", "sc68-dshow", "sc68-winamp", "sc68-vlc", "sc68-gst",
    "sc68-audacious", "sc68-doc", "sc68-nsi",
}

DEST = pathlib.Path(__file__).resolve().parent.parent / "native" / "vendor" / "sc68-3"
MANIFEST = DEST / ".manifest.sha256"
TIMEOUT = 60


class Listing(html.parser.HTMLParser):
    """Pulls the hrefs out of one SourceForge directory listing."""

    def __init__(self) -> None:
        super().__init__()
        self.entries: list[str] = []

    def handle_starttag(self, tag: str, attrs: list[tuple[str, str | None]]) -> None:
        if tag != "a":
            return
        for name, value in attrs:
            if name == "href" and value and not value.startswith("../"):
                # Every link carries the revision query; the name is what precedes it.
                self.entries.append(value.split("?", 1)[0])


def get(url: str) -> bytes:
    with urllib.request.urlopen(url, timeout=TIMEOUT) as response:
        return response.read()


def walk(path: str = "") -> list[str]:
    """Every file under `path`, relative to the repository root."""
    parser = Listing()
    parser.feed(get(f"{BASE}/{path}?p={REVISION}").decode("utf-8", "replace"))

    files: list[str] = []
    for entry in parser.entries:
        full = f"{path}{entry}"
        if entry.endswith("/"):
            if full.rstrip("/").split("/")[0] in EXCLUDE:
                continue
            files.extend(walk(full))
        else:
            files.append(full)
    return files


def digest(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest()


def read_manifest() -> dict[str, str]:
    if not MANIFEST.is_file():
        return {}
    out = {}
    for line in MANIFEST.read_text().splitlines():
        if line.strip():
            sha, name = line.split("  ", 1)
            out[name] = sha
    return out


def fetch_one(relative: str) -> tuple[str, str | None]:
    try:
        data = get(f"{BASE}/{relative}?p={REVISION}")
    except (urllib.error.URLError, urllib.error.HTTPError) as error:
        return relative, f"{error}"
    target = DEST / relative
    target.parent.mkdir(parents=True, exist_ok=True)
    # Through a temporary file: an interrupted write must not leave a truncated source that looks
    # valid to the next build.
    part = target.with_suffix(target.suffix + ".part")
    part.write_bytes(data)
    part.replace(target)
    return relative, None


def main() -> int:
    force = "--force" in sys.argv
    check_only = "--check" in sys.argv

    known = read_manifest()

    if check_only:
        if not known:
            print("❌ No manifest. Run without --check first.")
            return 1
        bad = [
            name for name, sha in known.items()
            if not (DEST / name).is_file() or digest((DEST / name).read_bytes()) != sha
        ]
        if bad:
            print(f"❌ {len(bad)} file(s) missing or changed since the fetch:")
            for name in bad[:10]:
                print(f"   {name}")
            return 1
        print(f"✅ {len(known)} files match the manifest (revision {REVISION})")
        return 0

    print(f"Walking sc68 revision {REVISION} (excluding {len(EXCLUDE)} plugin/doc directories)…")
    try:
        files = walk()
    except (urllib.error.URLError, urllib.error.HTTPError) as error:
        print(f"❌ Could not read the repository listing: {error}")
        return 1
    print(f"  {len(files)} files")

    wanted = [
        name for name in files
        if force or not (DEST / name).is_file() or known.get(name) != digest((DEST / name).read_bytes())
    ]
    print(f"  {len(wanted)} to fetch, {len(files) - len(wanted)} already correct")

    failures: list[tuple[str, str]] = []
    if wanted:
        with concurrent.futures.ThreadPoolExecutor(max_workers=8) as pool:
            for done, (name, error) in enumerate(pool.map(fetch_one, wanted), 1):
                if error:
                    failures.append((name, error))
                if done % 50 == 0:
                    print(f"  {done}/{len(wanted)}")

    if failures:
        print(f"❌ {len(failures)} file(s) failed:")
        for name, error in failures[:10]:
            print(f"   {name}: {error}")
        return 1

    DEST.mkdir(parents=True, exist_ok=True)
    lines = []
    total = 0
    for name in sorted(files):
        data = (DEST / name).read_bytes()
        total += len(data)
        lines.append(f"{digest(data)}  {name}")
    MANIFEST.write_text("\n".join(lines) + "\n")
    (DEST / ".protracktor-version").write_text(f"3.0.0b-svn{REVISION}\n")

    print(f"✅ sc68 3.0.0b (svn r{REVISION}): {len(files)} files, {total / 1_048_576:.1f} MB")
    print(f"   Manifest: {MANIFEST}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
