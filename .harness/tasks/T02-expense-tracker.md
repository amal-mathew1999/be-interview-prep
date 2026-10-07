---
id: T02
title: Expense tracker with monthly summary
branch: feature/q2-expenses
depends_on: []
owns:
  - src/main/java/com/example/mockretest/expense/**
  - src/test/java/com/example/mockretest/expense/**
  - src/main/resources/expense.properties
---

## Goal
REST API to record expenses and report monthly spending per category with exact arithmetic.

## Acceptance criteria
- AC1: An expense has `amount` (required, > 0, at most 2 decimal places), `category` (required, one of `FOOD`, `TRAVEL`, `BILLS`, `OTHER`; case-insensitive input accepted), `date` (required, ISO `yyyy-MM-dd`) and optional `note` (max 500 chars).
- AC2: `POST /api/expenses` → `201` + `Location` + expense JSON with `id`. Amount `0`, negative, or `1.234` → `400`. Unknown category → `400`.
- AC3: `GET /api/expenses` lists expenses; optional filters `from`, `to` (inclusive dates) and `category`, combinable. `from` after `to` → `400`.
- AC4: `GET /api/expenses/{id}` → `200`; `PUT /api/expenses/{id}` → `200` with same validation; `DELETE /api/expenses/{id}` → `204`; unknown id → `404` for all three.
- AC5: `GET /api/expenses/summary?month=yyyy-MM` → `200` `{"month":"2026-03","totals":{"FOOD":..,"TRAVEL":..,"BILLS":..,"OTHER":..},"overall":..}`. Every category appears (zero if none). Missing/invalid `month` → `400`.
- AC6: Totals are exact decimal values serialized with 2 decimal places: expenses `0.10` + `0.20` sum to exactly `0.30`. No floating-point types anywhere in the money path (entity, DTO, DB column, aggregation).
- AC7: The summary includes expenses dated on the first and on the last day of the month, and excludes the last day of the previous month and the first day of the next month (including leap-year February).
- AC8: All errors use `application/problem+json` (`type`, `title`, `status`, `detail`, `instance`; validation errors add an `errors` field map), including malformed JSON and bad enum/date values.
- AC9: Automated tests prove AC6 and AC7 and cover the validation failures in AC2.

## Out of scope
- Currencies, users, any other feature package, `application.properties`, `pom.xml`.

## Implementation notes
- `BigDecimal` with `@Digits(integer = 10, fraction = 2)` + `@Positive`; column `precision = 12, scale = 2`. Table `expense_item`.
- Summary range: `date >= month.atDay(1) AND date <= month.atEndOfMonth()`; aggregate with a JPQL `SUM` grouped by category or in Java with `BigDecimal::add`.
- Serialize amounts with scale 2 (`setScale(2)`).
