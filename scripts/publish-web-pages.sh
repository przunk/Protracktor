#!/usr/bin/env bash
# SPDX-FileCopyrightText: 2026 Przunk
# SPDX-License-Identifier: GPL-3.0-or-later
#
# The public copy of the web player, for GitHub Pages: https://przunk.github.io/Protracktor/
#
# **The page is an add-on to the app, and anyone can run it themselves** (the owner, 2026-09-22);
# `package-web.sh` is that. This is one more copy of it, at a permanent address, without a server
# of ours: GitHub Pages serves static files, which is all the page, the engine and the catalogues
# need. **Pairing is not there** -- it needs a running process (`serve-web.mjs`) -- and the page
# says so in its pairing sheet. So nothing a phone sends ever reaches this copy.
#
# What it does, stopping at the first thing wrong:
#
#   1. refuses a tree with uncommitted changes, because the page's Source code link names the
#      commit it was built from, and that commit must be the code that was built;
#   2. refuses an engine older than the last change to the code it is built from;
#   3. stages the notices and the privacy policy beside the engine (`stage-web-legal.mjs`);
#   4. assembles the static site -- `src/`, `lib/`, `vendor/`, a root page that forwards to `src/`,
#      and `.nojekyll` so GitHub serves every file as it is;
#   5. commits it to the `gh-pages` branch, in a worktree of its own, never touching this checkout.
#
# **It does not push.** The last lines say what to run, and, the first time, what to switch on in
# GitHub's settings.
#
# Usage:  ./scripts/publish-web-pages.sh
set -euo pipefail
cd "$(dirname "$0")/.."
ROOT="$PWD"

fail() { echo "❌ $*"; exit 1; }

# --- 1. the source is what was committed -----------------------------------------------------------
git diff --quiet HEAD -- || fail "uncommitted changes to tracked files — commit them first, so Source code names the code that was built"
commit="$(git rev-parse HEAD)"
short="$(git rev-parse --short HEAD)"

# --- 2. the engine is not older than its code ---------------------------------------------------------
[[ -f web/vendor/engine.wasm ]] || fail "no engine. Run ./scripts/build-web-engine.sh first"
engine_built="$(stat -c %Y web/vendor/engine.wasm)"
code_changed="$(git log -1 --format=%ct -- native/engine native/backends native/CMakeLists.txt native/patches scripts/build-web-engine.sh)"
if [[ -n "$code_changed" && "$code_changed" -gt "$engine_built" ]]; then
    fail "the engine is older than the last change to its code ($(git log -1 --format='%h %s' -- native/engine native/backends native/CMakeLists.txt native/patches scripts/build-web-engine.sh)). Run ./scripts/build-web-engine.sh"
fi

# --- 3. notices and policy ---------------------------------------------------------------------------
node scripts/stage-web-legal.mjs

# --- 4. the site ---------------------------------------------------------------------------------------
SITE="$(mktemp -d)"
WORKTREE="$(mktemp -d)"
cleanup() { git worktree remove --force "$WORKTREE" >/dev/null 2>&1 || true; rm -rf "$SITE" "$WORKTREE"; }
trap cleanup EXIT

mkdir -p "$SITE/src" "$SITE/lib" "$SITE/vendor"
cp -r web/src/. "$SITE/src/"
cp -r web/lib/. "$SITE/lib/"
cp web/vendor/engine.mjs web/vendor/engine.wasm "$SITE/vendor/"
cp -r web/vendor/notices web/vendor/legal "$SITE/vendor/"
cp LICENSE "$SITE/"
touch "$SITE/.nojekyll"
# The page lives in `src/` and reaches `../vendor` and `../lib` from there, exactly as it does when
# `serve-web.mjs` serves it; the root only forwards, as that server's `/` does.
cat > "$SITE/index.html" <<HTML
<!doctype html>
<!-- SPDX-FileCopyrightText: 2026 Przunk -->
<!-- SPDX-License-Identifier: GPL-3.0-or-later -->
<html lang="en">
<meta charset="utf-8">
<title>Protracktor</title>
<meta http-equiv="refresh" content="0; url=src/">
<link rel="canonical" href="src/">
<p><a href="src/">Protracktor — the web player</a></p>
</html>
HTML

files="$(find "$SITE" -type f | wc -l)"
size="$(du -sh "$SITE" | cut -f1)"

# --- 5. the branch ---------------------------------------------------------------------------------------
if git rev-parse -q --verify refs/heads/gh-pages >/dev/null; then
    git worktree add -q "$WORKTREE" gh-pages
else
    # The first publication: a branch with no history of its own, so the site never carries the
    # project's history and the project never carries the site's.
    git worktree add -q --detach "$WORKTREE"
    git -C "$WORKTREE" checkout -q --orphan gh-pages
fi
git -C "$WORKTREE" rm -rfq --ignore-unmatch . >/dev/null
cp -r "$SITE/." "$WORKTREE/"
git -C "$WORKTREE" add -A
if git -C "$WORKTREE" diff --cached --quiet; then
    echo "ℹ️  gh-pages already holds exactly this; nothing to publish."
    exit 0
fi
git -C "$WORKTREE" commit -q -m "Publish the web player from $short" \
    -m "Built from $commit. The page's Source code link points there."

echo "📦 gh-pages: $files files, $size, from $short"
echo
echo "What is yours:"
# The page's Source code link names this commit; GitHub has to have it, or the link is dead.
if [[ -z "$(git branch -r --contains "$commit" 2>/dev/null)" ]]; then
    echo "   0. First push the code the page was built from — its Source code link points at $short:"
    echo "          git push origin $(git rev-parse --abbrev-ref HEAD)"
fi
echo "   1. Push:   git push origin gh-pages"
echo "   2. The first time only, on GitHub: Settings → Pages → Build and deployment →"
echo "      Source: Deploy from a branch → Branch: gh-pages, folder / (root) → Save."
echo "   3. A minute later: https://przunk.github.io/Protracktor/"
