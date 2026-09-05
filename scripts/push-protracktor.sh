#!/usr/bin/env bash
#
# Pushes to GitHub, asking for the token when it runs and keeping it nowhere.
#
# The secret is typed, used, and gone when the process ends. It is never written to .git/config,
# never baked into the remote URL (where `git remote -v` and every error message would show it),
# and never left in a credential store.
#
# Non-interactive use reads PROTRACKTOR_GITHUB_USER and PROTRACKTOR_GITHUB_TOKEN from the
# environment.
#
# Usage:  ./scripts/push-protracktor.sh [branch…]      (default: the current branch)
#
# PROTRACKTOR_FORCE_PUSH=1 overwrites what is on the remote, for the one case that needs it:
# history that was deliberately rewritten locally. It uses --force-with-lease, which refuses if the
# remote has moved since the last fetch — so it can overwrite your own rewrite, but not somebody
# else's work that arrived in the meantime.
#
# PROTRACKTOR_FORCE_PUSH=hard uses a plain --force instead, and exists because a lease is not always
# possible. `git filter-repo` deletes the remote and its tracking refs by design, so after a rewrite
# there is nothing for the lease to compare against and git refuses with "stale info". A lease you
# cannot take is not a safety net — this says so out loud rather than letting the safer-looking
# option fail in a way that invites guessing.
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR/.."

REMOTE="${PROTRACKTOR_GIT_REMOTE:-origin}"
branches=("$@")
if [ ${#branches[@]} -eq 0 ]; then
    branches=("$(git rev-parse --abbrev-ref HEAD)")
fi

git remote get-url "$REMOTE" >/dev/null 2>&1 ||
    { echo "❌ No remote '$REMOTE'. Add it with: git remote add $REMOTE <url>"; exit 1; }

push_flags=(--set-upstream)
case "${PROTRACKTOR_FORCE_PUSH:-0}" in
    1) push_flags+=(--force-with-lease) ;;
    hard) push_flags+=(--force) ;;
esac

echo "🌐 $(git remote get-url "$REMOTE")"
echo "   branches: ${branches[*]}"
[ "${PROTRACKTOR_FORCE_PUSH:-0}" = "1" ] && echo "   ⚠️  force (--force-with-lease): the remote history will be replaced"
[ "${PROTRACKTOR_FORCE_PUSH:-0}" = "hard" ] && echo "   ⚠️  force (--force, no lease): the remote history will be replaced with no check"

if [ -t 0 ]; then
    current="${PROTRACKTOR_GITHUB_USER:-przunk}"
    read -r -p "GitHub user [$current]: " entered
    PROTRACKTOR_GITHUB_USER="${entered:-$current}"
    read -r -s -p "Token (personal access token, not your password): " PROTRACKTOR_GITHUB_TOKEN
    echo
fi

[ -n "${PROTRACKTOR_GITHUB_USER:-}" ] || { echo "❌ no GitHub user given"; exit 1; }
[ -n "${PROTRACKTOR_GITHUB_TOKEN:-}" ] || { echo "❌ no token given"; exit 1; }

export PROTRACKTOR_GITHUB_USER PROTRACKTOR_GITHUB_TOKEN
export GIT_ASKPASS="$SCRIPT_DIR/internal/git-askpass.sh"
# Without this git would still try the terminal first on some setups, defeating the point.
export GIT_TERMINAL_PROMPT=0

echo
if git push "${push_flags[@]}" "$REMOTE" "${branches[@]}"; then
    echo
    echo "✅ Pushed ${branches[*]} to $REMOTE"
else
    echo
    echo "❌ Push failed."
    echo "   A 403 usually means the token lacks write access to this repository."
    echo "   Classic token: needs the 'repo' scope."
    echo "   Fine-grained:  needs Contents → Read and write on przunk/protracktor."
    echo "   A rejected non-fast-forward means the history was rewritten:"
    echo "     PROTRACKTOR_FORCE_PUSH=1 ./scripts/push-protracktor.sh ${branches[*]}"
    exit 1
fi
