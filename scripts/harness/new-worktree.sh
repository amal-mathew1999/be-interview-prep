#!/usr/bin/env bash
# Usage: scripts/harness/new-worktree.sh T01 q1-library
# Creates .worktrees/T01 on branch feature/q1-library from main. Prints the absolute worktree path.
set -euo pipefail

id="${1:?task id, e.g. T01}"
slug="${2:?branch slug, e.g. q1-library}"
root="$(dirname "$(git rev-parse --path-format=absolute --git-common-dir)")"
dir="$root/.worktrees/$id"
branch="feature/$slug"

if [[ -d "$dir" ]]; then
  echo "Worktree already exists: $dir" >&2
  echo "$dir"
  exit 0
fi

git -C "$root" worktree add -b "$branch" "$dir" main >&2
echo "$dir"
