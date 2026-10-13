#!/usr/bin/env bash
# SPDX-FileCopyrightText: 2026 Przunk
# SPDX-License-Identifier: GPL-3.0-or-later
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
# Read the pinned JDK's JNI headers; do not load any decoder, audio service or library cache.
TASK_JDK="${PROTRACKTOR_JAVA_HOME:-/mnt/workspace/.tooling/jdk}"
TASK_OUT="$(mktemp -d /tmp/protracktor-audio-host.XXXXXX)"
trap 'rm -r -- "$TASK_OUT"' EXIT
TASK_EXTRA=()
if [ "${1:-}" = "--sanitize" ]; then
    TASK_EXTRA=(-fsanitize=address,undefined -fno-omit-frame-pointer)
elif [ "$#" -ne 0 ]; then
    echo "Usage: $0 [--sanitize]" >&2
    exit 2
fi
c++ -std=c++20 -O1 -g -Wall -Wextra -Werror -pthread \
    "${TASK_EXTRA[@]}" \
    -ffunction-sections -fdata-sections -Wl,--gc-sections \
    -I"$ROOT/scripts/audio-host-test" -I"$TASK_JDK/include" -I"$TASK_JDK/include/linux" \
    "$ROOT/scripts/audio-host-test/player_test.cpp" -o "$TASK_OUT/check-audio-host"
"$TASK_OUT/check-audio-host"
