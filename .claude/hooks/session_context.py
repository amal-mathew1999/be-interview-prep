"""SessionStart: inject current branch, active worktrees, and task specs as context."""

import glob
import os
import subprocess


def git(*args: str) -> str:
    try:
        return subprocess.run(["git", *args], capture_output=True, text=True, timeout=10).stdout.strip()
    except Exception:
        return ""


root = os.environ.get("CLAUDE_PROJECT_DIR", ".")
os.chdir(root)
specs = sorted(os.path.basename(p) for p in glob.glob(".harness/tasks/T*.md"))
print("## Harness context")
print(f"- Branch: {git('branch', '--show-current') or '(none)'}")
print("- Worktrees:\n" + "\n".join("  " + line for line in git("worktree", "list").splitlines()))
print(f"- Task specs: {', '.join(specs) or 'none'}")
print("- Pipeline: .claude/skills/parallel-tasks/SKILL.md | Conventions: .claude/skills/java-conventions/SKILL.md")
