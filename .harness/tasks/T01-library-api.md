---
id: T01
title: Library API — books and lending
branch: feature/q1-library
depends_on: []
owns:
  - src/main/java/com/example/mockretest/library/**
  - src/test/java/com/example/mockretest/library/**
  - src/main/resources/library.properties
---

## Goal
REST API for a small library to manage books and lend them to members.

## Acceptance criteria
- AC1: A book has `title` (required, non-blank), `author` (required, non-blank), `isbn` (required, non-blank, unique) and `publishedYear` (optional; must not be in the future relative to the current year).
- AC2: `POST /api/books` → `201` + `Location: /api/books/{id}` + book JSON (includes `id`, all fields, and `available` boolean).
- AC3: `GET /api/books` lists books; optional `?title=` and/or `?author=` filter by case-insensitive substring match.
- AC4: `GET /api/books/{id}` → `200` book; unknown id → `404`.
- AC5: `PUT /api/books/{id}` replaces the editable fields with the same validation as create → `200`; unknown id → `404`.
- AC6: `DELETE /api/books/{id}` → `204`; unknown id → `404`; currently borrowed book → `409`.
- AC7: Creating or updating with an ISBN already used by another book → `409`. This must hold even under concurrent requests (enforced at the database level, not only by a pre-check).
- AC8: `POST /api/books/{id}/borrow` with body `{"memberId": "<non-blank>"}` → `200` with the loan (`loanId`, `bookId`, `memberId`, `borrowedAt`). Missing/blank `memberId` → `400`.
- AC9: Borrowing a book that is already borrowed → `409` with a clear `detail` stating the book is currently borrowed. Two concurrent borrow requests for the same available book: exactly one succeeds.
- AC10: `POST /api/books/{id}/return` → `200`; book becomes available again; returning a book that is not borrowed → `409`.
- AC11: Every error (400/404/409/500, including malformed JSON and type mismatches like `/api/books/abc`) returns one consistent `application/problem+json` body (`type`, `title`, `status`, `detail`, `instance`); validation errors additionally carry an `errors` object mapping field → message.
- AC12: Automated tests cover at least: create happy path, validation failures (blank title, future year), duplicate ISBN, borrow-unavailable `409`, delete-while-borrowed `409`, return flow, and search.

## Out of scope
- Member management (members are identified only by `memberId` string).
- Any other feature package, `application.properties`, `pom.xml`.

## Implementation notes
- Package `com.example.mockretest.library`. Entities `Book`, `Loan` (use `@Table(name = "library_book")` / `library_loan`).
- Unique constraint on ISBN; translate `DataIntegrityViolationException` to 409.
- Borrow concurrency: `@Version` optimistic locking on `Book` or a pessimistic lock on read; map `ObjectOptimisticLockingFailureException` → 409.
- Use `java.time.Year.now(clock)` with an injectable `Clock` bean defined in `LibraryConfig` (name it `libraryClock`) for the future-year rule.
