#!/usr/bin/env bash
# Usage: scripts/harness/new-worktree.sh T01 task-entity
# Creates .worktrees/T01 on branch task/T01-task-entity from main. Prints the absolute worktree path.
set -euo pipefail

id="${1:?task id, e.g. T01}"
slug="${2:?kebab-case slug}"
root="$(dirname "$(git rev-parse --path-format=absolute --git-common-dir)")"
dir="$root/.worktrees/$id"
branch="task/$id-$slug"

if [[ -d "$dir" ]]; then
  echo "Worktree already exists: $dir" >&2
  echo "$dir"
  exit 0
fi

git -C "$root" worktree add -b "$branch" "$dir" main >&2
echo "$dir"
