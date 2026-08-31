#!/usr/bin/env bash
#
# Answers git's credential questions from the environment.
#
# Git asks on stdin by default, which means the token would land in the terminal and in the shell's
# scrollback. Pointed at by GIT_ASKPASS, this hands the answer over without it being echoed, stored
# in .git/config, or written to a credential file.
case "$1" in
    *[Uu]sername*) printf '%s\n' "${PROTRACKTOR_GITHUB_USER:-}" ;;
    *) printf '%s\n' "${PROTRACKTOR_GITHUB_TOKEN:-}" ;;
esac
