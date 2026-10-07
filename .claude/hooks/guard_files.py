"""PreToolUse(Edit|Write|NotebookEdit) guard.

- Product code (src/**, pom.xml) may only be edited inside a task worktree (.worktrees/<ID>/...).
- Protected files (git internals, Maven wrapper, secrets) may never be edited.
"""

import json
import os
import re
import sys

PROTECTED = [
    (r"(^|/)\.git(/|$)", "git internals"),
    (r"(^|/)mvnw(\.cmd)?$", "Maven wrapper"),
    (r"(^|/)\.mvn/wrapper/", "Maven wrapper config"),
    (r"(^|/)\.env(\.|$)|\.(pem|key|p12|jks)$", "secrets/keystores"),
]


def norm(p: str) -> str:
    return os.path.normcase(os.path.abspath(p)).replace("\\", "/")


def main() -> int:
    try:
        data = json.load(sys.stdin)
    except json.JSONDecodeError:
        return 0
    tool_input = data.get("tool_input") or {}
    path = tool_input.get("file_path") or tool_input.get("notebook_path")
    if not path:
        return 0
    target = norm(path)
    project = norm(os.environ.get("CLAUDE_PROJECT_DIR") or data.get("cwd") or ".")

    for pattern, what in PROTECTED:
        if re.search(pattern, target):
            print(f"[harness guard] BLOCKED: {path} is protected ({what}).", file=sys.stderr)
            return 2

    if target.startswith(project + "/"):
        rel = target[len(project) + 1:]
        if not rel.startswith(".worktrees/") and (rel.startswith("src/") or rel == "pom.xml"):
            print(
                "[harness guard] BLOCKED: product code must be changed inside a task worktree, "
                "not the main checkout. Run scripts/harness/new-worktree.sh <ID> <slug> and edit "
                f".worktrees/<ID>/{rel} instead.",
                file=sys.stderr,
            )
            return 2
    return 0


if __name__ == "__main__":
    sys.exit(main())
