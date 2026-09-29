#!/usr/bin/env bash
# SPDX-FileCopyrightText: 2026 Przunk
# SPDX-License-Identifier: GPL-3.0-or-later
#
# Builds `check-sap-measure.c` against the SAP measurement the phone compiles
# (`native/backends/asap/asap_measure.c`, which includes ASAP) and runs it. Needs a C compiler on the
# host; `test-protracktor.sh` skips it, and says so, where there is none.
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
OUT="$(mktemp -d)"
trap 'rm -rf "$OUT"' EXIT
cc -O2 -w -I"$ROOT/native/vendor/asap" -I"$ROOT/native/backends/asap" \
    -o "$OUT/check-sap-measure" "$ROOT/scripts/check-sap-measure.c" \
    "$ROOT/native/backends/asap/asap_measure.c" -lm
"$OUT/check-sap-measure"
