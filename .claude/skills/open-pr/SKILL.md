---
name: open-pr
description: Push a task branch and open its GitHub PR with the repo's title/body conventions.
---

# Open a PR for a task

Run from inside the task's worktree.

```bash
./mvnw -q -B verify                       # must pass
git push -u origin HEAD
gh pr create --base main --head "$(git branch --show-current)" \
  --title "<type>(<scope>): <summary> [T<NN>]" \
  --body-file <(cat <<'EOF'
## Task
T<NN> — <title> (spec: `.harness/tasks/T<NN>-<slug>.md`)

## Changes
- ...

## Acceptance criteria
- [x] AC1 ...

## Verification
`./mvnw -q -B verify` ✅

🤖 Generated with [Claude Code](https://claude.com/claude-code)
EOF
)
```

Rules:
- One PR per task; base is always `main`.
- Title follows Conventional Commits and ends with the task ID.
- If a PR for the branch already exists (`gh pr view --json number`), push only — don't create a second one.
- Print the PR URL as the last line of your output.
