---
id: T06
title: Global problem+json fallback for errors outside feature handlers
branch: task/T06-problem-error-fallback
depends_on: []
owns:
  - src/main/java/com/example/mockretest/common/error/**
  - src/test/java/com/example/mockretest/common/error/**
---

## Goal
Every error response from the application uses the same RFC 9457 `application/problem+json` format, including errors that occur before a controller is selected (so feature-scoped exception handlers cannot see them).

## Acceptance criteria
- AC1: A request with an unsupported HTTP method on an existing path (e.g. `DELETE /actuator/health`, or any mapped path with a wrong method) → `405` with `application/problem+json` body containing `type`, `title`, `status`, `detail`, `instance`, and an `Allow` header listing supported methods.
- AC2: A request to an unmapped path (e.g. `GET /api/does-not-exist`) → `404` problem+json with all five fields; `instance` is the original request path (not `/error`).
- AC3: A multipart request exceeding the container's `spring.servlet.multipart.max-file-size` / `max-request-size`, rejected before any controller is selected → `413` problem+json with a clear `detail`.
- AC4: Any other error reaching the container error path (uncaught exception, `response.sendError`) → problem+json with the original status; for `5xx` the `detail` is generic and never contains exception messages, class names, or stack traces.
- AC5: Errors already handled by feature exception handlers are unaffected (their bodies/status are unchanged).
- AC6: `type` is always present in the JSON (not omitted as `about:blank`).
- AC7: Automated tests run against a real embedded server (random port), not MockMvc only, and cover AC1–AC4 and AC6.

## Out of scope
- Changing any feature package, `application.properties`, `pom.xml`.

## Implementation notes
- Replace Boot's `BasicErrorController` by defining a bean implementing `org.springframework.boot.webmvc.error.ErrorController` (check package in Boot 4) — e.g. `ProblemDetailErrorController` mapped to `${server.error.path:${error.path:/error}}`, building a `ProblemDetail` from `RequestDispatcher.ERROR_STATUS_CODE`, `ERROR_REQUEST_URI`, `ERROR_EXCEPTION`, and `ERROR_MESSAGE` (use message only for 4xx, and only if safe).
- `Allow` header for 405: the original response header may already be set by `DefaultHandlerExceptionResolver`; preserve it.
- Unique class names (`CommonProblemTypes`, etc.) — no clash with feature handlers.
