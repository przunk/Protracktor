#!/usr/bin/env bash
#
# One release, from the owner's own terminal: checks, version, tag, Play bundle, evidence, master.
#
#   ./scripts/release.sh 0.7.0            do it
#   ./scripts/release.sh 0.7.0 --check    only check that it could be done; change nothing
#
# **Every step that writes stops the run if it fails**, and the order is the one the release
# checklist gives (`store/play-console/release-checklist.md` §3–4) and 0.4.0 to 0.6.0 were done in
# by hand:
#
#   1. on `develop`, nothing uncommitted, the tag not taken, the version newer than the last;
#   2. release notes present in both languages and within Play's limits;
#   3. the web engine rebuilt and the whole suite run -- the web build is an artifact that does not
#      follow a branch switch, and a stale one has failed a run twice already;
#   4. `versionName` set and committed, and that commit tagged `v<version>`;
#   5. the Play bundle built from the tag by `build-bundle.sh`, **which asks for the keystore
#      passwords itself and stores nothing** -- this script never sees them;
#   6. the evidence entry written to `store/play-console/releases.md` from the bundle itself:
#      size, SHA-256, versionCode, signer. Written *after* the tag, as 0.6.0's was, so the tagged
#      commit is the one the bundle was built from;
#   7. `master` brought level with `develop` by a merge commit.
#
# **It does not push and does not upload.** Both are the owner's, and the last lines say what to
# run and what to upload where.
set -euo pipefail

cd "$(dirname "${BASH_SOURCE[0]}")/.."
ROOT="$PWD"

red() { printf '\033[31m%s\033[0m\n' "$*"; }
step() { printf '\n\033[1m== %s\033[0m\n' "$*"; }
fail() { red "❌ $*"; exit 1; }

version="${1:-}"
mode="${2:-}"
[[ "$version" =~ ^[0-9]+\.[0-9]+\.[0-9]+$ ]] || fail "usage: ./scripts/release.sh X.Y.Z [--check]"
tag="v$version"

# --- 1. where we stand ---------------------------------------------------------------------------
step "1. The tree"
branch="$(git rev-parse --abbrev-ref HEAD)"
[ "$branch" = "develop" ] || fail "releases are made from develop; this is $branch"
[ -z "$(git status --porcelain)" ] || fail "uncommitted changes -- commit or stash them first"
git rev-parse -q --verify "refs/tags/$tag" >/dev/null && fail "tag $tag already exists"

current="$(sed -n 's/.*versionName = "\(.*\)".*/\1/p' app/build.gradle.kts | head -n 1)"
newest="$(printf '%s\n%s\n' "$current" "$version" | sort -V | tail -n 1)"
[ "$version" != "$current" ] && [ "$newest" = "$version" ] ||
    fail "$version is not newer than the current versionName $current"
previous_tag="$(git describe --tags --abbrev=0 --match 'v*' 2>/dev/null || true)"
echo "   develop, clean; $current → $version; last tag ${previous_tag:-none}"

# --- 2. release notes ----------------------------------------------------------------------------
step "2. Release notes"
for locale in en-US pl-PL; do
    notes="store/listing/$locale/release-notes/default.txt"
    [ -s "$notes" ] || fail "no release notes at $notes"
done
./scripts/check-store-metadata.sh
echo "   Have they been rewritten for $version? They are pasted into Play as they stand:"
for locale in en-US pl-PL; do echo; echo "   [$locale]"; sed 's/^/   | /' "store/listing/$locale/release-notes/default.txt"; done

# --- 3. checks -----------------------------------------------------------------------------------
step "3. Web engine and the full suite"
./scripts/build-web-engine.sh > /dev/null
./scripts/test-protracktor.sh

if [ "$mode" = "--check" ]; then
    step "Checked. Nothing was changed."
    echo "   Run without --check to make the release."
    exit 0
fi

echo
read -r -p "Everything above is right, and the notes are for $version? Type the version to go on: " answer
[ "$answer" = "$version" ] || fail "stopped; nothing was changed"

# --- 4. version and tag --------------------------------------------------------------------------
step "4. versionName $version, commit and tag"
sed -i "s/versionName = \"$current\"/versionName = \"$version\"/" app/build.gradle.kts
grep -q "versionName = \"$version\"" app/build.gradle.kts || fail "could not set versionName"
git add app/build.gradle.kts
git commit -q -m "$version" -m "$(cat store/listing/en-US/release-notes/default.txt)"
git tag -a "$tag" -m "Protracktor $version" -m "$(cat store/listing/en-US/release-notes/default.txt)"
commit="$(git rev-parse --short HEAD)"
version_code="$(git rev-list --count HEAD)"
echo "   $commit tagged $tag; versionCode $version_code"

# --- 5. the Play bundle --------------------------------------------------------------------------
step "5. Play bundle (the keystore passwords are asked for by build-bundle.sh, and kept nowhere)"
./scripts/build-bundle.sh
artifact="$ROOT/dist/protracktor-$version-$version_code.aab"
[ -f "$artifact" ] || fail "no bundle at $artifact"

# --- 6. evidence ---------------------------------------------------------------------------------
step "6. Evidence in store/play-console/releases.md"
source scripts/use-tooling.sh
size="$(stat -c %s "$artifact")"
sha="$(sha256sum "$artifact" | cut -d' ' -f1)"
fingerprint="$("$JAVA_HOME/bin/keytool" -printcert -jarfile "$artifact" 2>/dev/null \
    | sed -n 's/^[[:space:]]*SHA256: //p' | head -n 1)"
[ -n "$fingerprint" ] || fail "could not read the signer from the bundle"

entry="$(cat <<ENTRY
## $version — versionCode $version_code

Closed testing. Notes as uploaded: \`store/listing/*/release-notes/default.txt\` at \`$tag\`.

| | |
| --- | --- |
| Artifact | \`dist/$(basename "$artifact")\` |
| Size | $(printf "%'d" "$size") bytes |
| SHA-256 | \`$sha\` |
| versionCode | $version_code — \`git rev-list --count HEAD\`, not written by hand |
| versionName | $version |
| Commit | \`$commit\` |
| Tag | \`$tag\` |
| Built | $(date +%F) |

**Signer**, read from the bundle by \`release.sh\`: \`$fingerprint\`.

**What changed since ${previous_tag:-the previous release}**: \`git log --oneline ${previous_tag:+$previous_tag..}$tag\`, and
\`docs/STATUS.md\` for the defects by number.

ENTRY
)"
# The blank line after the entry is explicit: \$(…) strips trailing newlines, and without it the
# previous release's heading sticks to this entry's last line -- as 0.6.0's did under 0.7.0's.
python3 - "$entry" <<'PY'
import pathlib, sys
path = pathlib.Path("store/play-console/releases.md")
text = path.read_text(encoding="utf-8")
first = text.index("\n## ") + 1
path.write_text(text[:first] + sys.argv[1].rstrip("\n") + "\n\n" + text[first:], encoding="utf-8")
PY
git add store/play-console/releases.md
git commit -q -m "Record the evidence for versionCode $version_code" \
    -m "The bundle for $tag: $size bytes, SHA-256 $sha."
echo "   recorded"

# --- 7. master -----------------------------------------------------------------------------------
step "7. master level with develop"
git checkout -q master
git merge -q --no-ff develop -m "Merge develop into master — release $version"
git checkout -q develop
echo "   merged"

# --- what is the owner's -------------------------------------------------------------------------
step "Done. What is yours:"
cat <<DONE
   1. Upload   $artifact
      Play Console → Protracktor → Test and release → Testing → Closed testing → Create new release.
   2. Paste the release notes from store/listing/en-US and store/listing/pl-PL (release-notes/default.txt).
   3. Push:    git push origin master develop $tag
   The SHA-256 Play shows for the upload should be $sha.
DONE
