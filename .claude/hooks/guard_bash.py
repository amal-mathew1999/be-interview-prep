"""PreToolUse(Bash) guard: blocks destructive or policy-violating shell commands.

Exit 2 = block; stderr is shown to Claude as the reason.
"""

import json
import re
import sys

RULES = [
    (r"\bgit\s+push\b[^|;&]*(\s--force\b|\s-f\b|\s--force-with-lease\b|\s\+\S+)",
     "Force-push is forbidden. Add a new commit and push normally."),
    (r"\bgit\s+(commit|push|merge|rebase)\b[^|;&\n]*\s--no-verify\b"
     r"|\bgit\s+commit\b[^|;&\n]*\s-[a-zA-Z]*n[a-zA-Z]*\b",
     "Skipping git hooks (--no-verify / -n) is forbidden. Fix the failing check instead."),
    (r"\bgit\s+commit\b[^|;&]*--amend\b",
     "Amending is forbidden on shared task branches. Make a new commit."),
    (r"\bgit\s+reset\s+--hard\b", "git reset --hard discards work. Ask the user first."),
    (r"\bgit\s+(checkout|restore)\s+(--\s+)?\.(\s|$)", "Discarding all working-tree changes is forbidden."),
    (r"\bgit\s+clean\s+-[a-zA-Z]*f", "git clean -f deletes untracked files. Ask the user first."),
    (r"\bgit\s+branch\s+-D\s+main\b", "Deleting main is forbidden."),
    (r"\bgit\s+config\b[^|;&]*core\.hooksPath", "Changing core.hooksPath disables repo git hooks."),
    (r"\bgh\s+pr\s+merge\b", "Merging PRs requires explicit user approval. Report and stop."),
    (r"\bgh\s+repo\s+delete\b", "Deleting repositories is forbidden."),
    (r"\brm\s+-[a-zA-Z]*r[a-zA-Z]*f?\s+(/|~|\.|\*|\$HOME|\.git)(\s|/?$)",
     "Recursive delete of a root/home/repo path is forbidden."),
]


def main() -> int:
    try:
        data = json.load(sys.stdin)
    except json.JSONDecodeError:
        return 0
    cmd = (data.get("tool_input") or {}).get("command", "")
    for pattern, reason in RULES:
        if re.search(pattern, cmd):
            print(f"[harness guard] BLOCKED: {reason}\nCommand: {cmd}", file=sys.stderr)
            return 2
    return 0


if __name__ == "__main__":
    sys.exit(main())
