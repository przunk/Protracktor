#!/usr/bin/env bash
#
# Environment for every Protracktor build. Source it, do not execute it:
#
#     source scripts/use-tooling.sh
#
# Everything comes from the shared /mnt/workspace/.tooling install. Nothing here writes to that
# directory, and every mutable path (Gradle home, temp, build output) is namespaced to Protracktor
# so a build here can never disturb another project in the workspace.

TOOLING_ROOT="${PROTRACKTOR_TOOLING_ROOT:-/mnt/workspace/.tooling}"

export JAVA_HOME="${PROTRACKTOR_JAVA_HOME:-$TOOLING_ROOT/jdk}"
export ANDROID_HOME="$TOOLING_ROOT/android-sdk"
export ANDROID_SDK_ROOT="$ANDROID_HOME"

# Pinned rather than "whatever is newest". A native build that silently changes toolchain between
# runs is a build whose failures cannot be reproduced.
export PROTRACKTOR_NDK_VERSION="29.0.14206865"
export PROTRACKTOR_CMAKE_VERSION="3.31.6"
export ANDROID_NDK_HOME="$ANDROID_HOME/ndk/$PROTRACKTOR_NDK_VERSION"
export ANDROID_NDK_ROOT="$ANDROID_NDK_HOME"

# Gradle's native services and the Android build both fail on the 9p mount that backs
# /mnt/workspace: it rejects chmod and utimensat. Keep everything mutable on the Linux filesystem.
export GRADLE_USER_HOME="${PROTRACKTOR_GRADLE_USER_HOME:-/tmp/protracktor-gradle-home}"
export PROTRACKTOR_BUILD_DIR_ROOT="${PROTRACKTOR_BUILD_DIR_ROOT:-$HOME/.protracktor/build}"
export JAVA_OPTS="-Djava.io.tmpdir=/tmp/protracktor-gradle-tmp ${JAVA_OPTS:-}"
export GRADLE_OPTS="-Dorg.gradle.cache.internal.locklistener=false -Djava.io.tmpdir=/tmp/protracktor-gradle-tmp ${GRADLE_OPTS:-}"

mkdir -p "$GRADLE_USER_HOME" /tmp/protracktor-gradle-tmp "$PROTRACKTOR_BUILD_DIR_ROOT"

export PATH="$ANDROID_HOME/cmake/$PROTRACKTOR_CMAKE_VERSION/bin:$ANDROID_HOME/cmdline-tools/latest/bin:$ANDROID_HOME/platform-tools:$JAVA_HOME/bin:$PATH"
