#!/usr/bin/env bash
# One-time setup after cloning: enable repo git hooks and verify the toolchain.
set -euo pipefail

root="$(dirname "$(git rev-parse --path-format=absolute --git-common-dir)")"
git -C "$root" config core.hooksPath .githooks
mkdir -p "$root/.worktrees" "$root/.harness/reviews"
command -v python >/dev/null || echo "WARN: python not on PATH (Claude hooks need it)" >&2
command -v gh >/dev/null || echo "WARN: gh CLI not on PATH (PR creation needs it)" >&2
echo "Harness ready: hooksPath=$(git -C "$root" config core.hooksPath)"
