#!/usr/bin/env bash
# Usage: scripts/harness/blind-diff.sh T01
# Exports a blind review bundle: acceptance criteria + metadata-free diff + changed file list.
# Output dir (printed): .harness/reviews/T01/round-<n>/
set -euo pipefail

id="${1:?task id}"
root="$(dirname "$(git rev-parse --path-format=absolute --git-common-dir)")"
wt="$root/.worktrees/$id"
[[ -d "$wt" ]] || { echo "No worktree for $id at $wt" >&2; exit 1; }
spec="$(ls "$root"/.harness/tasks/"$id"-*.md 2>/dev/null | head -1)"
[[ -n "$spec" ]] || { echo "No spec for $id" >&2; exit 1; }

branch="$(git -C "$wt" branch --show-current)"
base="$(git -C "$wt" merge-base main "$branch")"

n=1
while [[ -d "$root/.harness/reviews/$id/round-$n" ]]; do n=$((n + 1)); done
out="$root/.harness/reviews/$id/round-$n"
mkdir -p "$out"

# Only the acceptance-criteria section of the spec; implementation notes are withheld.
awk '/^## Acceptance criteria/{p=1} /^## / && !/^## Acceptance criteria/{p=0} p' "$spec" > "$out/criteria.md"

# Diff without commit metadata (no hashes, messages, authors, dates; blob index lines stripped).
git -C "$wt" diff --no-color --no-ext-diff "$base" "$branch" | grep -v '^index ' > "$out/diff.patch"
git -C "$wt" diff --name-status "$base" "$branch" > "$out/files.txt"

echo "$out"
