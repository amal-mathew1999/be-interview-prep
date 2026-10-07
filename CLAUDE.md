# mock-retest — Claude Code harness

Spring Boot 4 / Java 17 / Maven Wrapper REST service. This file is the entry point for every agent session. Read it first.

## Commands (always use the wrapper — `mvn` is not installed)

| Purpose | Command |
|---|---|
| Full gate (format check + compile + tests) | `./mvnw -q -B verify` |
| Format code | `./mvnw -q -B spotless:apply` |
| Single test class | `./mvnw -q -B test -Dtest=ClassNameTest` |
| Run app | `./mvnw spring-boot:run` |

A change is **done** only when `./mvnw -q -B verify` exits 0.

## Harness map

| Piece | Path | Purpose |
|---|---|---|
| Conventions | `.claude/skills/java-conventions/SKILL.md` | Code, package, test, and commit rules. **Binding.** |
| Orchestration | `.claude/skills/parallel-tasks/SKILL.md` | Plan → worktrees → parallel implement → PR → blind review → fix → push |
| Blind review | `.claude/skills/blind-review/SKILL.md` | How a review is produced without author context |
| PR rules | `.claude/skills/open-pr/SKILL.md` | Branch and PR naming, body template |
| Agents | `.claude/agents/*.md` | `task-planner`, `java-implementer`, `blind-reviewer`, `bug-fixer` |
| Hooks | `.claude/hooks/*.py` + `.claude/settings.json` | Guardrails and auto-format |
| Git hooks | `.githooks/` (`core.hooksPath`) | pre-commit format check, pre-push verify |
| Scripts | `scripts/harness/*.sh` | `setup.sh` (run once after clone), worktree create/remove, blind diff export |
| Task specs | `.harness/tasks/T*.md` | One atomic task per file (committed) |
| Review output | `.harness/reviews/` | Diffs + findings (git-ignored, local only) |

## Golden rules

1. **One task = one worktree = one branch = one PR.** Never mix tasks.
2. Never commit product code (`src/`, `pom.xml`) to `main` — only harness/spec files; never force-push; never `--no-verify`. Hooks enforce this.
3. Tasks are **atomic**: each owns a disjoint set of files declared in its spec. Touching a file outside the task's `owns:` list requires that the task spec be updated first.
4. Reviews are **blind**: the reviewer sees only the task's acceptance criteria and a metadata-free diff — never the PR body, commit messages, author, or implementer's reasoning.
5. Every review finding with severity `blocker` or `major` must be fixed and re-reviewed before a PR is considered ready.

## Layout

```
src/main/java/com/example/mockretest/
  <feature>/            # package-by-feature: controller, service, repository, dto, entity
  common/               # cross-cutting: errors, config, utilities
src/test/java/...       # mirrors main
```
