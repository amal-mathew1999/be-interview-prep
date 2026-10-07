---
name: blind-review
description: Produce and run a blind code review — reviewer judges a metadata-free diff strictly against the task's acceptance criteria and repo conventions, with no knowledge of author, PR text, or commit messages.
---

# Blind review

## What "blind" means here
The reviewer must not be anchored by the author's framing. It receives **only**:
1. `criteria.md` — the task's acceptance criteria (copied from the spec; no implementation notes).
2. `diff.patch` — `git diff main...<branch>` with no commit hashes, messages, authors, or dates.
3. `files.txt` — list of changed files.
4. Read access to the worktree to inspect surrounding code.

It must **not** receive: PR number/URL/body, commit log, implementer transcript, previous review rounds. The `blind-reviewer` agent has no Bash tool, so it cannot run `git log` or `gh`.

## Producing the bundle
```bash
scripts/harness/blind-diff.sh T03      # → .harness/reviews/T03/round-<n>/{criteria.md,diff.patch,files.txt}
```

## Rubric (reviewer applies in this order)
1. **Correctness** — does the code meet every acceptance criterion? Logic errors, off-by-one, null handling, transaction boundaries, HTTP status codes.
2. **Tests** — is each criterion (including unhappy paths) covered by a test that would fail without the change?
3. **Security** — input validation, injection, mass assignment (entity exposed), leaked internals in error bodies.
4. **Conventions** — `java-conventions` skill violations.
5. **Scope** — changes outside the criteria = `major` (atomicity breach).

## Severity
- `blocker` — wrong behavior, build/test break, security hole, criterion unmet.
- `major` — missing test for a criterion, convention breach affecting API/architecture, scope creep.
- `minor` — readability, naming, small duplication.
- `nit` — optional.

Verdict: `REQUEST_CHANGES` if any `blocker` or `major`, else `APPROVE`.

## findings.md format
```markdown
# Verdict: APPROVE | REQUEST_CHANGES

| # | Severity | File:Line | Finding | Suggested fix |
|---|----------|-----------|---------|---------------|
| 1 | blocker  | src/.../TaskService.java:42 | ... | ... |

## Criteria checklist
- [x] AC1 — evidence: TaskControllerTest#createsTaskAndReturns201
- [ ] AC2 — not covered: ...
```
