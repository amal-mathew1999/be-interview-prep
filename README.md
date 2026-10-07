# mock-retest

A Spring Boot 4 / Java 17 REST service with five independent feature APIs:

| Feature | Base path | What it does |
|---|---|---|
| Library | `/api/books` | Manage books; members borrow and return them |
| Expenses | `/api/expenses` | Record expenses; exact monthly per-category summary |
| File upload | `/api/files` | Upload / list / download / delete JPEG, PNG and PDF files (≤ 5 MB) |
| Rate limiting | `/api/quotes` | Random-quote endpoint limited to 10 requests/minute per API key |
| Appointment booking | `/api/doctors`, `/api/bookings` | 30-minute slots; hold → confirm → cancel, no double booking |

Every error from every endpoint uses one JSON format: [RFC 9457 problem details](#error-format).

---

## Requirements

- **JDK 17+** (`java -version` must report 17 or later).
- **No Maven install needed.** The repository ships the Maven Wrapper (`mvnw` / `mvnw.cmd`). It downloads the right Maven version on first use.
- Internet access on the first build, to download Maven and the dependencies.

> **Windows:** use `mvnw.cmd` in `cmd`/PowerShell, or `./mvnw` in Git Bash. The commands below use `./mvnw`.

---

## Run the app

### Option 1 — from source (development)

```bash
./mvnw spring-boot:run
```

### Option 2 — as a packaged jar

```bash
./mvnw -DskipTests package
java -jar target/mockretest-0.0.1-SNAPSHOT.jar
```

The app starts on **http://localhost:8080**. Check that it is up:

```bash
curl http://localhost:8080/actuator/health
# {"groups":["liveness","readiness"],"status":"UP"}
```

Stop it with `Ctrl+C`.

### Data storage

- Records are stored in an **in-memory H2 database**, so all data is lost when the app stops. No database setup is needed.
- Uploaded file contents are written to `./data/files` by default (git-ignored). See [Configuration](#configuration) to change this.

### Configuration

Each feature's settings live in its own file under `src/main/resources/`. Any setting can be overridden **without code changes**, either with a command-line flag or an environment variable:

```bash
java -jar target/mockretest-0.0.1-SNAPSHOT.jar --server.port=9090 --ratelimit.limit=20
# or
SERVER_PORT=9090 RATELIMIT_LIMIT=20 RATELIMIT_WINDOW=30s java -jar target/mockretest-0.0.1-SNAPSHOT.jar
```

| Setting | Default | File | Meaning |
|---|---|---|---|
| `server.port` | `8080` | — | HTTP port |
| `ratelimit.limit` | `10` | `ratelimit.properties` | Requests allowed per API key per window |
| `ratelimit.window` | `1m` | `ratelimit.properties` | Window length (`30s`, `1m`, …) |
| `ratelimit.max-api-key-length` | `128` | `ratelimit.properties` | Longer `X-API-Key` values are rejected |
| `ratelimit.max-tracked-keys` | `100000` | `ratelimit.properties` | Memory cap on tracked keys |
| `files.storage-dir` | `./data/files` | `files.properties` | Where uploaded bytes are stored |
| `files.max-size` | `5MB` | `files.properties` | Maximum upload size. `spring.servlet.multipart.*` in `application.properties` must stay above it; startup fails otherwise |
| `booking.workday-start` / `booking.workday-end` | `09:00` / `17:00` | `booking.properties` | Clinic working hours |
| `booking.slot-duration` | `30m` | `booking.properties` | Slot length |
| `booking.hold-timeout` | `5m` | `booking.properties` | How long an unconfirmed hold lasts |
| `booking.zone` | `UTC` | `booking.properties` | Clinic time zone (slot times are local to it) |
| `library.zone-id` | `UTC` | `library.properties` | Zone used for "current year" and loan timestamps |

---

## Run the tests

```bash
./mvnw verify
```

This is the full quality gate, and CI runs exactly the same command. It does three things:

1. Checks code formatting with Spotless (Palantir Java Format).
2. Compiles the code.
3. Runs the whole test suite: ~320 tests, unit tests plus full-application tests on an embedded server.

A change is only done when this passes.

### Useful variations

| Goal | Command |
|---|---|
| Tests only (skip the format check) | `./mvnw test` |
| One test class | `./mvnw test -Dtest=LibraryControllerTest` |
| One test method | `./mvnw test -Dtest='LibraryControllerTest#createsBookAndReturns201WithLocation'` |
| One feature's tests | `./mvnw test -Dtest='com.example.mockretest.booking.**'` |
| Quieter output | add `-q` |
| Fix formatting (when `verify` reports Spotless violations) | `./mvnw spotless:apply` |

Test reports are written to `target/surefire-reports/`.

### What the tests prove (highlights)

| Feature | Key tests |
|---|---|
| Library | Borrowing an unavailable book returns `409`. Concurrent borrows and duplicate ISBNs: exactly one request wins (`LibraryConcurrencyIntegrationTest`, `LibraryApiIntegrationTest`) |
| Expenses | `0.10 + 0.20 = 0.30` exactly. The summary includes the first and last day of the month, including leap-year February (`ExpenseApiEndToEndTest`) |
| File upload | An `.exe` renamed to `.png` is rejected (`415`). Oversized files get `413`. `../` file names can't escape the storage directory. Exactly 5 MB is accepted over real HTTP (`FilesApiIntegrationTest`) |
| Rate limiting | The 11th request in a minute gets `429` with `Retry-After`. API keys don't affect each other. A concurrent burst for one key yields exactly 10 successes (`RateLimitIntegrationTest`, `RateLimitServiceTest`) |
| Booking | 20 patients hold the same slot at once and exactly 1 succeeds. An expired hold frees the slot. The notification is sent only after commit (`BookingConcurrencyTest`, `BookingFlowIntegrationTest`, `BookingNotificationTest`) |
| Errors | 405, unmapped 404, oversized uploads and 500s all come back as problem+json (`CommonProblemDetailErrorControllerIntegrationTest`) |

Tests use controllable clocks rather than `sleep`. Run-time-sensitive tests (holds, rate windows) are deterministic.

---

## API reference

All request and response bodies are JSON unless noted otherwise. The examples assume `http://localhost:8080`.

### Library — `/api/books`

| Method & path | Body | Success |
|---|---|---|
| `POST /api/books` | `{"title","author","isbn","publishedYear"}` | `201` + `Location` |
| `GET /api/books?title=&author=` | — | `200` list (case-insensitive substring search; both filters optional) |
| `GET /api/books/{id}` | — | `200` |
| `PUT /api/books/{id}` | same as create | `200` |
| `DELETE /api/books/{id}` | — | `204`, or `409` if currently borrowed |
| `POST /api/books/{id}/borrow` | `{"memberId"}` | `200` loan, or `409` if already borrowed |
| `POST /api/books/{id}/return` | — | `200`, or `409` if not borrowed |

Validation rules:
- `title`, `author` and `isbn` are required.
- `isbn` must be unique (`409` on a duplicate).
- `publishedYear` is optional, but must not be in the future.

```bash
curl -i -H 'Content-Type: application/json' \
  -d '{"title":"Dune","author":"Frank Herbert","isbn":"9780441013593","publishedYear":1965}' \
  http://localhost:8080/api/books
curl -H 'Content-Type: application/json' -d '{"memberId":"m-1"}' http://localhost:8080/api/books/1/borrow
curl -H 'Content-Type: application/json' -d '{"memberId":"m-2"}' http://localhost:8080/api/books/1/borrow
# {"type":"urn:problem-type:library:conflict","title":"Conflict","status":409,
#  "detail":"Book 1 is currently borrowed.","instance":"/api/books/1/borrow"}
```

### Expenses — `/api/expenses`

| Method & path | Body | Success |
|---|---|---|
| `POST /api/expenses` | `{"amount","category","date","note"}` | `201` + `Location` |
| `GET /api/expenses?from=&to=&category=` | — | `200` list (filters optional and combinable; dates inclusive) |
| `GET /api/expenses/{id}` | — | `200` |
| `PUT /api/expenses/{id}` | same as create | `200` |
| `DELETE /api/expenses/{id}` | — | `204` |
| `GET /api/expenses/summary?month=yyyy-MM` | — | `200` per-category totals + overall total |

Validation rules:
- `amount` must be greater than 0, with at most 2 decimal places.
- `category` is one of `FOOD`, `TRAVEL`, `BILLS`, `OTHER` (case-insensitive).
- `date` must be strict `yyyy-MM-dd`.
- `note` is optional, at most 500 characters.

Amounts use exact decimal arithmetic (`BigDecimal`).

```bash
curl -H 'Content-Type: application/json' -d '{"amount":"0.10","category":"FOOD","date":"2026-03-01"}' http://localhost:8080/api/expenses
curl -H 'Content-Type: application/json' -d '{"amount":"0.20","category":"food","date":"2026-03-31","note":"lunch"}' http://localhost:8080/api/expenses
curl 'http://localhost:8080/api/expenses/summary?month=2026-03'
# {"month":"2026-03","totals":{"FOOD":0.30,"TRAVEL":0.00,"BILLS":0.00,"OTHER":0.00},"overall":0.30}
```

### File upload — `/api/files`

| Method & path | Body | Success |
|---|---|---|
| `POST /api/files` | `multipart/form-data`, part name `file` | `201` + record (`id`, `originalName`, `contentType`, `size`, `uploadedAt`) |
| `GET /api/files` | — | `200` list of records |
| `GET /api/files/{id}` | — | `200` file bytes, `Content-Disposition: attachment` with the original name |
| `DELETE /api/files/{id}` | — | `204` (removes both the bytes and the record) |

Rules:
- Only JPEG, PNG and PDF files are accepted. The type is detected from the file's **content**, not from its name or the `Content-Type` header, so a renamed executable gets `415`.
- Files over 5 MB get `413`, and empty files get `400`.
- Stored files get server-generated names, so a name like `../../etc/passwd` can never read or write outside the storage directory.

```bash
curl -F file=@photo.png http://localhost:8080/api/files
curl -OJ http://localhost:8080/api/files/<id>      # saves with the original file name
```

### Rate-limited quotes — `/api/quotes`

| Method & path | Headers | Success |
|---|---|---|
| `GET /api/quotes/random` | `X-API-Key: <any key>` (required) | `200` `{"text","author"}` |

How the limit works:
- Each API key may make `ratelimit.limit` requests per `ratelimit.window`: by default 10 per minute.
- Successful responses include `X-RateLimit-Limit` and `X-RateLimit-Remaining`.
- Requests over the limit get `429` with a `Retry-After` header (seconds to wait).
- A missing or blank key gets `401`.
- Keys are independent of each other.

```bash
for i in $(seq 1 11); do curl -s -o /dev/null -w '%{http_code} ' -H 'X-API-Key: demo' http://localhost:8080/api/quotes/random; done
# 200 200 200 200 200 200 200 200 200 200 429
```

### Appointment booking — `/api/doctors`, `/api/bookings`

| Method & path | Body | Success |
|---|---|---|
| `POST /api/doctors` | `{"name"}` | `201` + `Location` |
| `GET /api/doctors/{doctorId}` | — | `200` |
| `GET /api/doctors/{doctorId}/slots?date=yyyy-MM-dd` | — | `200` list of available slot start times |
| `POST /api/bookings/hold` | `{"doctorId","patientId","start"}` | `201` `status: HELD` with `expiresAt` |
| `POST /api/bookings/{bookingId}/confirm` | `{"patientId"}` | `200` `status: CONFIRMED` |
| `POST /api/bookings/{bookingId}/cancel` | `{"patientId"}` | `200` `status: CANCELLED` (frees the slot) |

How booking works:
- **Slots:** each slot is 30 minutes, within the clinic's working hours.
- **Holds:** a hold expires after 5 minutes if it isn't confirmed, and the slot becomes available again.
- **No double booking:** a slot can never be double-booked, even under concurrent requests. The database enforces this.
- **Status codes:**
  - The wrong `patientId` gets `403`.
  - A slot that is taken, or a hold that has expired, gets `409`.
  - A start time that is misaligned, outside working hours or in the past gets `400`.
- **Notifications:** a confirmation notification is logged asynchronously, only after the booking has been saved.

```bash
curl -H 'Content-Type: application/json' -d '{"name":"Dr. Rao"}' http://localhost:8080/api/doctors
curl 'http://localhost:8080/api/doctors/1/slots?date=2030-01-15'
curl -H 'Content-Type: application/json' -d '{"doctorId":1,"patientId":"p-1","start":"2030-01-15T09:00:00"}' http://localhost:8080/api/bookings/hold
curl -H 'Content-Type: application/json' -d '{"patientId":"p-1"}' http://localhost:8080/api/bookings/1/confirm
```

---

## Error format

Every error uses `Content-Type: application/problem+json` ([RFC 9457](https://www.rfc-editor.org/rfc/rfc9457)) with the same five fields:

```json
{
  "type": "urn:problem-type:library:conflict",
  "title": "Conflict",
  "status": 409,
  "detail": "Book 1 is currently borrowed.",
  "instance": "/api/books/1/borrow"
}
```

How the format behaves:
- **Validation errors** (`400`) also include an `errors` object that maps each field to its message.
- **Server errors** (`5xx`) never expose exception messages or stack traces.
- **Coverage:** wrong-method (`405`) and unknown-path (`404`) requests get the same format, rendered by a shared fallback in `common/error`.

---

## Project structure

```
src/main/java/com/example/mockretest/
  library/      books and lending          (Q1)
  expense/      expenses and summary       (Q2)
  files/        file upload service        (Q3)
  ratelimit/    per-key rate limiter       (Q4)
  quotes/       the protected quotes API   (Q4)
  booking/      clinic appointment booking (Q5)
  common/error/ shared problem+json error fallback
src/main/resources/
  application.properties     app-wide settings
  <feature>.properties       per-feature settings
src/test/java/...            mirrors main; *Test = unit/slice, *IntegrationTest = full application
```

Each feature is self-contained. It uses its own package, feature-prefixed class names, its own exception handler and its own properties file, so the features don't collide in the merged application.

## Continuous integration

GitHub Actions (`.github/workflows/ci.yml`) runs `./mvnw -B verify` on JDK 17 for every pull request and every push to `main`.
