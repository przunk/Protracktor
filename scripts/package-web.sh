#!/usr/bin/env bash
# SPDX-FileCopyrightText: 2026 Przunk
# SPDX-License-Identifier: GPL-3.0-or-later
#
# Everything the web player needs, in one archive, for a machine that is not this one.
#
# Compile the engine on the development checkout; the receiving machine only needs Node.
set -euo pipefail
cd "$(dirname "$0")/.."

[[ -f web/vendor/engine.wasm ]] || { echo "❌ no engine. Run scripts/build-web-engine.sh first"; exit 1; }

# Staged again rather than trusted: the table or the policy may have changed since the engine was
# built, and an archive must not go out naming components it does not carry, or without notices it
# owes (docs/PLAN_WEB_PARITY.md W4).
node scripts/stage-web-legal.mjs
node scripts/stage-web-pwa.mjs

commit="$(git rev-parse HEAD)"
revision="$(git rev-parse --short HEAD)"
epoch="$(git show -s --format=%ct HEAD)"
dirty_suffix=""
if [[ -n "$(git status --porcelain --untracked-files=normal)" ]]; then dirty_suffix="-dirty"; fi
node --input-type=module - "$commit" "$dirty_suffix" <<'SOURCE'
import fs from 'node:fs';
const [commit, dirty] = process.argv.slice(2);
const build = JSON.parse(fs.readFileSync('web/vendor/legal/build.json', 'utf8'));
if (build.commit !== commit || build.dirty !== Boolean(dirty)) {
  console.error('❌ staged source identity does not match this checkout; no WEB package was made');
  process.exit(1);
}
SOURCE
OUT="$PWD/dist/protracktor-web-${revision}${dirty_suffix}-$(date +%Y%m%d-%H%M%S-%N).tar.gz"
[[ ! -e "$OUT" ]] || { echo "❌ artifact already exists: $OUT"; exit 1; }
mkdir -p dist
STAGE="$(mktemp -d)"
trap 'rm -rf "$STAGE"' EXIT

mkdir -p "$STAGE/protracktor-web/scripts"
# `--exclude` because a copy of the previous archive is often left in web/ so another machine can
# fetch it over the LAN, and packaging that would put the bundle inside the bundle. It doubled the
# size the first time it happened, which is exactly how it was noticed.
tar -cf - --exclude='*.tar.gz' --exclude='node_modules' web | tar -xf - -C "$STAGE/protracktor-web/"
cp scripts/serve-web.mjs "$STAGE/protracktor-web/scripts/"
cp scripts/stage-web-pwa.mjs scripts/web-service-worker.js "$STAGE/protracktor-web/scripts/"
cp docs/WEB_SERVER.md "$STAGE/protracktor-web/README.md"

# A `.mjs` is a module, not a program: running it needs `node` in front, and without this it fails
# as "Permission denied" and then "command not found" under sudo. One line of shell removes the
# question.
cat > "$STAGE/protracktor-web/run.sh" <<'RUN'
#!/usr/bin/env sh
# SPDX-FileCopyrightText: 2026 Przunk
# SPDX-License-Identifier: GPL-3.0-or-later
cd "$(dirname "$0")"
exec node scripts/serve-web.mjs "$@"
RUN
chmod +x "$STAGE/protracktor-web/run.sh"

# The engine and the page carry the licence of the decoders linked into them: GPL-3.0-or-later.
# Serving them is conveying them, so the offer of source travels in the archive (docs/LICENSES.md).
cp LICENSE "$STAGE/protracktor-web/"

# Build timestamps and checkout permissions do not identify source. Normalize archive metadata,
# including gzip's header, so identical inputs and source identity yield identical bytes.
find "$STAGE" -type d -exec chmod 755 {} +
find "$STAGE" -type f -exec chmod 644 {} +
chmod 755 "$STAGE/protracktor-web/run.sh"
LC_ALL=C tar --format=gnu --sort=name --mtime="@$epoch" --owner=0 --group=0 --numeric-owner \
    -cf - -C "$STAGE" protracktor-web | gzip -n > "$OUT"

echo "Source: ${commit}${dirty_suffix:+ (dirty)}"
echo "SHA-256: $(sha256sum "$OUT" | cut -d' ' -f1)"
echo "On the other machine:"
echo "    tar xzf $(basename "$OUT")"
echo "    cd protracktor-web && node scripts/serve-web.mjs"
echo "📦 $OUT ($(stat -c %s "$OUT") bytes)"
