---
id: T<NN>
title: <short imperative title>
branch: feature/q<N>-<slug>
depends_on: []
owns:
  - src/main/java/com/example/mockretest/<feature>/...
  - src/test/java/com/example/mockretest/<feature>/...
---

## Goal
One or two sentences: the behavior this task delivers.

## Acceptance criteria
- AC1: `POST /api/...` with valid body → `201`, `Location` header, body `{...}`.
- AC2: invalid body (<rule>) → `400` ProblemDetail listing the field.
- AC3: ...

## Out of scope
- What this task must NOT touch (belongs to another task).

## Implementation notes
Hints for the implementer only. **Never shown to the blind reviewer.**
