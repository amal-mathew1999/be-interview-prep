---
id: T04
title: Per-API-key rate limiting
branch: feature/q4-rate-limit
depends_on: []
owns:
  - src/main/java/com/example/mockretest/ratelimit/**
  - src/main/java/com/example/mockretest/quotes/**
  - src/test/java/com/example/mockretest/ratelimit/**
  - src/test/java/com/example/mockretest/quotes/**
  - src/main/resources/ratelimit.properties
---

## Goal
Protect an API from clients sending too many requests, per API key.

## Acceptance criteria
- AC1: `GET /api/quotes/random` → `200` `{"text": "...", "author": "..."}` from a fixed in-memory list.
- AC2: Clients are identified by the `X-API-Key` header. Requests to protected endpoints (`/api/quotes/**`) without the header (or blank) → `401` problem+json.
- AC3: Each API key may make at most N requests per window (default N = 10, window = 1 minute). Request N+1 within the window → `429` problem+json whose `detail` states the wait, plus a `Retry-After` header with the whole seconds (rounded up, ≥ 1) until the client may retry.
- AC4: Allowed responses include `X-RateLimit-Limit` and `X-RateLimit-Remaining` headers.
- AC5: Limit and window are configurable without code changes (properties/env vars).
- AC6: The limit holds under concurrency: when many threads send requests for the same key at the same moment, exactly N succeed within a window. No lost updates, no global lock that serializes different keys.
- AC7: Different API keys don't affect each other.
- AC8: After the window passes, the key may make requests again (verified with a controllable clock, not `Thread.sleep`).
- AC9: Automated tests prove: the 11th request in a minute is rejected with `429` + `Retry-After`; two keys are independent; concurrent burst for one key yields exactly N successes; reset after the window; missing key `401`.
- AC10: Memory doesn't grow unbounded with stale keys (expired windows are evictable).

## Out of scope
- Distributed (multi-instance) limiting, any other feature package, `application.properties`, `pom.xml`.

## Implementation notes
- Fixed or sliding window per key in a `ConcurrentHashMap<String, Window>` updated with `compute(...)` (atomic per key) — or a token bucket with CAS.
- `HandlerInterceptor` registered via a `WebMvcConfigurer` in `RateLimitConfig` for `/api/quotes/**`. Write problem+json directly from the interceptor (or throw and handle in `RateLimitExceptionHandler`).
- Inject a `Clock` bean named `rateLimitClock`.
- `ratelimit.properties`: `ratelimit.limit=10`, `ratelimit.window=1m`.
