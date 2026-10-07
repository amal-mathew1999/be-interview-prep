---
name: parallel-tasks
description: Orchestrate a feature request end-to-end — split into atomic tasks, run each in its own git worktree in parallel, open one PR per task, blind-review each, fix findings, push, and re-review until clean.
---

# Parallel task pipeline

The orchestrator (main session) runs these phases. Subagents do the work; the orchestrator never edits feature code itself.

## Phase 0 — Preconditions
- On `main`, clean tree, `origin` set, `gh auth status` OK, `./mvnw -q -B verify` green.

## Phase 1 — Plan (agent: `task-planner`)
- Input: the user's feature list.
- Output: `.harness/tasks/T<NN>-<slug>.md` per task (template: `.harness/tasks/_TEMPLATE.md`).
- **Atomicity check** (orchestrator verifies before continuing):
  - `owns:` file sets are pairwise disjoint.
  - Each task builds and tests on top of `main` alone (no task depends on another unmerged task). If a dependency is unavoidable, put the shared piece in a `T00` foundation task, merge it first, then fan out.
- Commit the specs to `main` (`chore(harness): add task specs`) and push.

## Phase 2 — Worktrees
For each task: `scripts/harness/new-worktree.sh T<NN> <slug>` → creates `.worktrees/T<NN>` on branch `task/T<NN>-<slug>` from `main`.

## Phase 3 — Implement in parallel (agent: `java-implementer`, one per task)
- Launch all implementers **in a single message** so they run concurrently.
- Each gets: worktree absolute path + spec path. Nothing else.
- Each one: implements → `./mvnw -q -B verify` → commits → pushes → opens PR (per `open-pr` skill) → returns PR number.

## Phase 4 — Blind review in parallel (agent: `blind-reviewer`, one per PR)
- Orchestrator runs `scripts/harness/blind-diff.sh T<NN>` for each task (exports metadata-free diff + acceptance criteria to `.harness/reviews/T<NN>/round-<k>/`).
- Launch reviewers in a single message. Each gets **only** the review bundle directory and the worktree path (read-only use). Never pass PR numbers, PR bodies, or implementer output.
- Each reviewer writes `findings.md` into the bundle and returns a verdict: `APPROVE` or `REQUEST_CHANGES`.

## Phase 5 — Fix (agent: `bug-fixer`, one per task with findings)
- Input: worktree path + `findings.md` path.
- Fixes every `blocker`/`major`, and `minor` where cheap; verifies; commits `fix(...)`; pushes to the same branch (PR updates automatically).
- Posts a PR comment summarising fixes (fixer may see the PR; only the reviewer is blind).

## Phase 6 — Re-review loop
- Repeat Phase 4 → 5 with `round-<k+1>` until `APPROVE`, max 3 rounds. If still failing after round 3, stop and escalate to the user.
- Reviewers in later rounds are fresh agents — they don't see earlier findings (stays blind).

## Phase 7 — Report
- Table: task, branch, PR URL, rounds, final verdict, CI status (`gh pr checks`).
- Do **not** merge unless the user asks. Remove worktrees only after merge: `scripts/harness/remove-worktree.sh T<NN>`.
