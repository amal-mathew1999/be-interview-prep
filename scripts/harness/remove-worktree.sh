#!/usr/bin/env bash
# Usage: scripts/harness/remove-worktree.sh T01
# Removes a task worktree (refuses if it has uncommitted changes). Branch is kept.
set -euo pipefail

id="${1:?task id}"
root="$(dirname "$(git rev-parse --path-format=absolute --git-common-dir)")"
git -C "$root" worktree remove "$root/.worktrees/$id"
git -C "$root" worktree prune
