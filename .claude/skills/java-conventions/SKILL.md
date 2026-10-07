---
name: java-conventions
description: Binding Java/Spring coding, testing, and commit conventions for this repo. Load before writing or reviewing any Java code.
---

# Java conventions (binding)

## Language & style
- Java 17. Use `record` for DTOs and value objects; `final` fields; no Lombok.
- Formatting is owned by Spotless (Palantir Java Format). Never hand-format; run `./mvnw -q -B spotless:apply`.
- No wildcard imports, no unused imports, no `System.out`/`printStackTrace` — use SLF4J (`LoggerFactory.getLogger`).
- Names: classes `PascalCase`, methods/fields `camelCase`, constants `UPPER_SNAKE`. Test classes end in `Test` (unit) or `IT` (integration).
- No `null` returns from public methods — return `Optional<T>` for absent single values, empty collections for absent lists.
- Prefer constructor injection; no field `@Autowired`. Single-constructor beans need no annotation.

## Architecture
- Package-by-feature under `com.example.mockretest.<feature>`: `XController`, `XService`, `XRepository`, `X` (entity), `dto/` records.
- Controllers: thin. Validate with `@Valid` + Jakarta constraints, delegate to service, map to DTOs. Never return entities.
- Services: business rules + `@Transactional` boundaries (read-only where applicable).
- Errors: throw domain exceptions (e.g. `NotFoundException`); map to RFC 9457 `ProblemDetail` in `common/error/GlobalExceptionHandler`. No try/catch in controllers.
- REST: plural nouns (`/api/tasks`), `201 Created` + `Location` on create, `204` on delete, `404` for missing, `400` for validation.
- Config via `application.properties`; no hard-coded secrets, URLs, or magic numbers.

## Testing
- JUnit 5 + AssertJ + Mockito (all provided by Spring Boot test starters).
- Every public behavior added gets a test. Services: plain unit tests with mocks. Controllers: `@WebMvcTest` + `MockMvc`. Repositories with custom queries: `@DataJpaTest`.
- Test names describe behavior: `createsTaskAndReturns201`, `returns404WhenTaskMissing`.
- Cover the unhappy paths named in the task's acceptance criteria — reviewers check for this.
- No `Thread.sleep`, no test ordering dependencies, no shared mutable static state.

## Git
- Branch: `task/<TASK-ID>-<kebab-slug>` (e.g. `task/T01-task-entity`).
- Conventional Commits: `feat(tasks): add Task entity and repository`, `fix(tasks): ...`, `test: ...`, `chore: ...`. Subject ≤ 72 chars, imperative.
- Small commits; each commit must pass `./mvnw -q -B verify` (pre-commit hook checks formatting, pre-push runs verify).
