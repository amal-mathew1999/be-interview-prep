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
- Errors: throw domain exceptions (e.g. `BookNotFoundException`); map them to RFC 9457 `ProblemDetail` (`application/problem+json`, fields `type`, `title`, `status`, `detail`, `instance`, plus `errors` map for validation) in a feature-scoped `@RestControllerAdvice(basePackageClasses = <Feature>Controller.class)` named `<Feature>ExceptionHandler` that extends `ResponseEntityExceptionHandler`. No try/catch in controllers.
- Errors raised before a handler is selected (405, unmapped 404, eager multipart limits) never reach a feature-scoped advice; they are rendered as ProblemDetail by the shared `common/error` error controller (T06). Don't try to handle them in feature code.
- Unhandled exceptions → `500` ProblemDetail with a generic detail (never a stack trace or internal message).

## Feature isolation (parallel tasks)
Features are built concurrently in separate worktrees, so they must never share files or bean/entity names:
- Every class name is feature-prefixed and unique across the app (`LibraryExceptionHandler`, not `GlobalExceptionHandler`). Spring bean names and JPA entity names derive from simple class names — duplicates break the merged app.
- Feature config lives in `src/main/resources/<feature>.properties`, loaded by `@PropertySource("classpath:<feature>.properties")` on a `<Feature>Config` class in the feature package, bound via `@ConfigurationProperties(prefix = "<feature>")`. Do **not** edit `application.properties` or `pom.xml`.
- Feature-specific `@EnableAsync` / `@EnableScheduling` go on the feature's config class.
- Tests that start the full context (`@SpringBootTest`) must not depend on other features' beans or data.
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
