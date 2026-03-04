# QE Test Plan — UserRole Web Service

**Author:** QE (Quality Engineer)
**Date:** 2026-03-03
**Reference Documents:** QE_BRIEFING.md, DESIGN.md, USE_CASES.md, SE_IMPLEMENTATION_PLAN.md
**Prepared For:** Owner Approval

---

## STATUS: PENDING OWNER APPROVAL

No test code will be written until this plan receives explicit Owner approval.

---

## Table of Contents

1. Test Scope — QE vs SE Ownership
2. Test Class Structure and Naming Conventions
3. Spring Context Strategy
4. Test Data Management
5. Test Execution Order (Risk-Based)
6. Rate Limiting Test Strategy
7. Security Test Strategy
8. Coverage Targets
9. Test Scenario Register

---

## 1. Test Scope — QE vs SE Ownership

### 1.1 What SE Owns (Do Not Duplicate)

SE's unit test suite (SE_IMPLEMENTATION_PLAN.md §7) covers the following with mocked dependencies and no Spring context:

| SE Test Class | SE Coverage |
|---|---|
| `GlobalExceptionHandlerTest` | Each exception subtype maps to correct HTTP status and error envelope |
| `LocalAuthProviderTest` | authenticate(), validate(), refresh(), invalidate() — all branches |
| `JwtFilterTest` | No-header passthrough, valid token populates context, invalid token sends 401 |
| `UserServiceImplTest` | All service methods including self-access checks and audit entry creation |
| `RoleServiceImplTest` | CRUD and conflict checks including RoleAssignmentQueryPort interaction |
| `PermissionCheckerImplTest` | All four Action values, multi-role scenarios |
| `EntityTypeServiceImplTest` | CRUD, conflict on existing instances |
| `EntityInstanceServiceImplTest` | All CRUD methods with permission checks |
| `RateLimitInterceptorTest` | Anonymous and authenticated bucket logic, independent bucket isolation |

QE must not re-implement any of the above as unit tests. Any scenario in this plan that overlaps in name with the SE list above is exercised at the integration or API level, which is a distinct test level even when it covers similar functional territory.

### 1.2 What QE Owns

QE owns all tests that require a Spring context, a real H2 database, or HTTP-level interactions. These fall into four categories:

| Level | Description | Tool |
|---|---|---|
| Integration tests | Spring context loaded, real H2 database, real Flyway migrations, no MockMvc | `@SpringBootTest(webEnvironment = NONE)` + `@Transactional` |
| API / contract tests | Full HTTP-level request/response validation against all endpoints | `@SpringBootTest(webEnvironment = RANDOM_PORT)` with MockMvc or TestRestTemplate |
| Security tests | JWT enforcement, Admin RBAC, self-access rules, token invalidation flows | MockMvc with Spring Security Test support (`SecurityMockMvcRequestPostProcessors`) |
| End-to-end RBAC flows | Multi-step flows spanning user, role, and entity modules in a single test | `@SpringBootTest` with TestRestTemplate |

### 1.3 Boundary Principle

The dividing line is simple: if a test instantiates a class directly with `new` and mocks its dependencies, it is an SE unit test. If a test depends on Spring's DI container, Flyway migrations, the security filter chain, the persistence layer, or HTTP routing, it is a QE test. There is no grey area.

---

## 2. Test Class Structure and Naming Conventions

### 2.1 Package Mirror

All QE test classes live under `src/test/java/com/userrole/` and mirror the production package structure. The root prefix for QE classes is the module name plus an `IT` (integration test) or `ApiTest` suffix, distinguishing them from SE's `*Test` unit test classes.

```
src/test/java/com/userrole/
├── auth/
│   └── local/
│       ├── AuthApiTest.java                  (API-level: login, refresh, logout endpoints)
│       └── JwtSecurityIT.java                (Integration: JWT filter, token invalidation, tamper scenarios)
├── user/
│   ├── UserApiTest.java                      (API-level: all /api/v1/users endpoints)
│   ├── UserRoleAssignmentApiTest.java         (API-level: role assign/revoke + audit log endpoints)
│   └── UserSelfAccessIT.java                 (Integration: self-access enforcement across user endpoints)
├── role/
│   ├── RoleApiTest.java                      (API-level: all /api/v1/roles endpoints)
│   └── RolePermissionApiTest.java            (API-level: permission add/remove/list on a role)
├── entity/
│   ├── EntityTypeApiTest.java                (API-level: all /api/v1/entity-types endpoints)
│   └── EntityInstanceApiTest.java            (API-level: all /api/v1/entity-types/{typeId}/instances endpoints)
├── ratelimit/
│   └── RateLimitApiTest.java                 (API-level: 429 behaviour, Retry-After header, independent buckets)
├── rbac/
│   └── RbacEndToEndIT.java                   (Full RBAC flows spanning user + role + entity modules)
├── common/
│   ├── ResponseEnvelopeIT.java               (Verifies ApiResponse envelope shape across all endpoints)
│   └── FlywayMigrationIT.java                (Smoke test: all V1–V6 migrations run cleanly against H2)
└── support/
    ├── BaseApiTest.java                      (Shared abstract base: context config, MockMvc setup, JWT helper)
    └── TestDataFactory.java                  (Builder/factory for User, Role, EntityType, EntityInstance fixtures)
```

### 2.2 Naming Conventions

**Class names:**
- `*ApiTest` — tests that exercise the HTTP layer via MockMvc or TestRestTemplate.
- `*IT` — integration tests that load the Spring context but may not use the full HTTP stack (e.g., testing service-to-service wiring with a real DB).

**Method names:** Follow SE's established convention: `methodOrEndpoint_condition_expectedOutcome`.

Examples:
- `login_withValidCredentials_returns200WithTokens`
- `createUser_whenDuplicateUsername_returns409`
- `getUser_whenNonAdminRequestsOtherProfile_returns403`
- `deleteUser_whenDeleted_subsequentLoginReturns401`

**Test annotations:**
- `@DisplayName` is used on each method to provide a human-readable description in test reports.
- `@Tag("security")` is applied to all tests in `JwtSecurityIT` and `RbacEndToEndIT` for selective execution in CI pipelines.
- `@Tag("smoke")` is applied to `FlywayMigrationIT` and the first happy-path test in each API test class.

### 2.3 One Class Per Module Concern

Each test class has a single clearly defined concern. No test class mixes concerns from multiple modules. The `RbacEndToEndIT` class is the deliberate exception — its purpose is explicitly cross-module integration, and it is clearly named and documented as such.

---

## 3. Spring Context Strategy

### 3.1 Context Loading Approach

QE uses a single shared application context across the entire test suite wherever possible, to avoid the cost of restarting the Spring container for every test class.

**Primary annotation:** `@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)`

A single base class, `BaseApiTest`, carries the `@SpringBootTest` annotation and all shared configuration. All `*ApiTest` and `*IT` classes extend `BaseApiTest`. Spring's test context caching mechanism reuses the same application context for all tests that share an identical context configuration (same annotations, same properties). This means Flyway runs once per test run, not once per test class.

**When context must be dirtied:** Only `RateLimitApiTest` requires modifying application properties (to override rate limit thresholds — see Section 6). This class uses `@DirtiesContext(classMode = ClassMode.AFTER_CLASS)` or, preferably, a dedicated `@TestPropertySource` profile that does not conflict with the shared context. See Section 6 for the full rate limit strategy.

### 3.2 H2 Configuration

The test `application.properties` (in `src/test/resources/`) overrides the datasource to use H2 in-memory mode:

```
spring.datasource.url=jdbc:h2:mem:userrole_test;DB_CLOSE_DELAY=-1;MODE=PostgreSQL
spring.datasource.driver-class-name=org.h2.Driver
spring.jpa.hibernate.ddl-auto=none
spring.flyway.enabled=true
```

`ddl-auto=none` is mandatory — schema management is owned entirely by Flyway, not by Hibernate's auto-DDL. This ensures the test schema matches exactly what production will receive.

`MODE=PostgreSQL` is used to catch potential H2 dialect divergence early. H2's PostgreSQL compatibility mode rejects several H2-isms that would otherwise silently succeed in pure H2 mode, making the portability smoke test more meaningful.

### 3.3 Flyway in Tests

Flyway runs automatically on Spring context startup in the test environment, executing all migrations V1 through V6 against the in-memory H2 database. This is the primary Flyway smoke test — the fact that the application context starts successfully confirms all migrations are valid.

`FlywayMigrationIT` provides an explicit, named test that asserts the Flyway migration count and the schema version, so a migration failure surfaces with a clear test failure rather than a vague context-startup error.

No test-specific Flyway migrations are used. All test data is seeded programmatically (see Section 4).

### 3.4 MockMvc Setup

MockMvc is configured in `BaseApiTest` using `MockMvcBuilders.webAppContextSetup(context).apply(SecurityMockMvcConfigurer.springSecurity()).build()`. This ensures the full Spring Security filter chain — including `JwtFilter` — is active during all API tests. Tests that want to bypass security for a specific scenario use `SecurityMockMvcRequestPostProcessors.anonymous()` or construct a valid JWT token via the `JwtTestHelper` utility (see Section 4).

---

## 4. Test Data Management

### 4.1 Isolation Strategy

**The fundamental rule:** No test class shares mutable state with any other test class.

Each test class that modifies the database is annotated with `@Transactional` at the class level. Spring's test transaction support rolls back every test method after it completes. This means:
- Each test method begins with only the Flyway-seeded baseline data (the ADMIN role from V6).
- No test method can pollute the state seen by another test method in the same class.
- There is no need for explicit `@BeforeEach` database cleanup for transactional test classes.

**Exception — `RbacEndToEndIT`:** End-to-end flows that span multiple HTTP requests cannot be rolled back mid-flow (each HTTP call is its own transaction boundary). These tests use `@Sql(scripts = "/sql/reset_test_data.sql", executionPhase = BEFORE_TEST_METHOD)` to truncate and re-seed relevant tables before each test. This script truncates in FK-safe order and re-inserts only the V6 seed data.

### 4.2 TestDataFactory

A `TestDataFactory` class in `com.userrole.support` provides static builder methods for constructing canonical test entities. The factory does not persist anything — it returns objects that the caller persists via the appropriate repository or API call. This keeps test data construction readable and avoids duplication.

Methods provided:
- `TestDataFactory.aUser()` — returns a populated `CreateUserRequest` with unique username/email (generated with a UUID suffix).
- `TestDataFactory.anAdminUser()` — same as above but the created user will have the ADMIN role assigned after creation.
- `TestDataFactory.aRole(String name)` — returns a `CreateRoleRequest` for a named role.
- `TestDataFactory.anEntityType(String name)` — returns a `CreateEntityTypeRequest`.
- `TestDataFactory.anInstance(String name)` — returns a `CreateInstanceRequest`.

### 4.3 JWT Test Tokens

A `JwtTestHelper` class in `com.userrole.support` provides methods to generate valid and invalid JWT tokens for test use without going through the login endpoint:
- `JwtTestHelper.tokenForAdmin()` — produces a signed token with the ADMIN role claim.
- `JwtTestHelper.tokenForUser(Long userId, String... roleNames)` — produces a signed token with given user ID and roles.
- `JwtTestHelper.expiredToken()` — produces a token with a past expiry.
- `JwtTestHelper.tamperedToken()` — produces a token with a modified payload and a now-invalid signature.

These tokens use the same signing key as the application (injected from `application.properties`) so they pass `LocalAuthProvider.validate()` exactly as real tokens do — or fail in the expected ways for negative cases.

### 4.4 No Shared SQL Fixture Files

With the exception of the `reset_test_data.sql` file used by `RbacEndToEndIT`, no SQL fixture files are used. All test data is created programmatically through the service layer or via API calls, which means:
- Test data creation exercises the same code paths as production.
- There is no risk of fixture files becoming stale with respect to schema changes.
- Constraint violations in test data construction are caught immediately.

### 4.5 Seeded Baseline Data

The only data present at the start of every test is what Flyway V6 seeds: the ADMIN role row in the `roles` table. There is no seeded Admin user. Tests that need an Admin user create one programmatically in their `@BeforeEach` setup and, because of `@Transactional`, this is rolled back after the test.

---

## 5. Test Execution Order (Risk-Based)

Tests are executed in the following priority order in CI. Higher-priority suites must be green before lower-priority suites run.

### Wave 1 — Infrastructure (P1, run first)

**`FlywayMigrationIT`** — Validates that all six Flyway migrations execute cleanly against H2. If this fails, all other tests are meaningless. This is the first test that must pass.

**Rationale:** Schema correctness is the foundation. A broken migration will cause spurious failures in every downstream test class, making failures difficult to diagnose.

### Wave 2 — Authentication (P1)

**`AuthApiTest`** and **`JwtSecurityIT`**

Authentication is the gating mechanism for the entire API. If login does not work, no protected endpoint can be tested. If JWT validation is broken, the security posture of the entire service is undefined.

**Rationale:** All other test waves depend on the ability to produce valid tokens and make authenticated requests. Auth bugs have the highest blast radius.

### Wave 3 — Core User and Role Management (P1)

**`UserApiTest`**, **`UserRoleAssignmentApiTest`**, **`RoleApiTest`**, **`RolePermissionApiTest`**

These two modules are the backbone of the RBAC model. The entity module cannot be tested meaningfully until roles and permissions exist. The audit log (UC-USER-009) is also tested here as it is produced during role assignment and revocation.

**Rationale:** User and role management must be stable before RBAC enforcement or entity access can be validated.

### Wave 4 — Entity Management (P1)

**`EntityTypeApiTest`**, **`EntityInstanceApiTest`**

Entity type and instance CRUD with permission enforcement. This is where the RBAC model is exercised at the HTTP level for the first time.

**Rationale:** Depends on stable user, role, and permission infrastructure from Wave 3.

### Wave 5 — RBAC End-to-End Flows (P1)

**`RbacEndToEndIT`**

Full multi-step scenarios: create user, assign role with permissions, exercise entity CRUD, revoke role, verify loss of access. These are the highest-confidence tests in the suite.

**Rationale:** These tests verify the entire system works together. They are P1 because they represent the core value proposition of the service. Any defect here is a production-blocking defect.

### Wave 6 — Security Hardening (P1/P2)

**`UserSelfAccessIT`**

Self-access boundary tests: a regular user may not access or modify another user's profile, roles, or audit history.

**Rationale:** These are security defects if they fail. P1 for the self-access enforcement scenarios; P2 for edge cases such as the userId matching path ID but the user being soft-deleted.

### Wave 7 — Response Envelope and API Conventions (P2)

**`ResponseEnvelopeIT`**

Verifies the ADR-0009 response envelope shape across a representative sample of endpoints: success, list (with meta), and each error condition.

**Rationale:** Client-breaking if wrong, but unlikely to vary between endpoints if the `GlobalExceptionHandler` and `ApiResponse` are correctly implemented. P2 because envelope failures will usually be caught earlier by the API tests in Waves 2–4.

### Wave 8 — Rate Limiting (P2)

**`RateLimitApiTest`**

Rate limiting correctness: 429 responses, Retry-After header, independent buckets per IP and per authenticated user.

**Rationale:** Important but lower risk than the functional correctness waves. Rate limiting bugs do not compromise data integrity. P2.

---

## 6. Rate Limiting Test Strategy

### 6.1 The Core Problem

The production rate limits are 30 requests per minute (anonymous) and 120 requests per minute (authenticated). Testing these limits by sending actual requests at those volumes introduces three problems:
- Tests become slow (real time must pass for bucket refill).
- Tests become brittle (timing sensitivity makes them flaky in CI).
- Sending 120 requests in a loop produces 120 real database operations.

None of these are acceptable in an automated test suite.

### 6.2 Solution: Configurable Limits via Test Properties

The rate limit values are already externalised into `application.properties` per DESIGN.md §3.6:

```
ratelimit.anonymous.requests-per-minute=30
ratelimit.authenticated.requests-per-minute=120
```

For `RateLimitApiTest`, a dedicated `@TestPropertySource` override sets the limits to very low values:

```
ratelimit.anonymous.requests-per-minute=3
ratelimit.authenticated.requests-per-minute=5
```

With a limit of 3 requests per minute for anonymous callers, the test need only send 4 requests to trigger a 429. This keeps the test fast and deterministic.

**Context isolation:** Because this `@TestPropertySource` override produces a different application context configuration from `BaseApiTest`, Spring's test context caching will create a separate context instance for `RateLimitApiTest`. The class is annotated with `@DirtiesContext(classMode = ClassMode.AFTER_CLASS)` to ensure the modified context is discarded after the rate limit tests complete, preventing it from contaminating subsequent tests.

### 6.3 What Must Be Made Configurable

The `RateLimitInterceptor` must read its thresholds from the injected `@ConfigurationProperties` bean at request time, not from compile-time constants. If the SE implementation hardcodes the threshold values rather than reading them from configuration, QE will raise a defect before writing rate limit tests. The implementation plan (SE_IMPLEMENTATION_PLAN.md §7) implies the interceptor is tested with direct property injection in unit tests, confirming the design supports this.

### 6.4 Bucket Reset Between Tests

Within `RateLimitApiTest`, the in-memory Bucket4j buckets accumulate state across test method calls (they are managed by a Spring singleton). To reset bucket state between test methods, one of two approaches is used:

- **Preferred:** Each test uses a distinct IP address (via `MockHttpServletRequest.setRemoteAddr()`) or a distinct user ID, ensuring each test operates against a fresh bucket that has never been touched.
- **Fallback:** If bucket isolation by distinct key is not achievable for all scenarios, `@DirtiesContext` is applied at the method level for tests that exhaust a bucket, forcing a fresh Spring context (and fresh buckets) for the next test. This approach is slow and used only as a last resort.

### 6.5 Scenarios to Cover

1. Anonymous caller sends (limit + 1) requests to any endpoint — the (limit + 1)th request returns 429.
2. The 429 response body conforms to the error envelope (ADR-0009): `status: "error"`, `code` field present.
3. The 429 response includes a `Retry-After` header with a positive integer value.
4. Two anonymous callers from different IP addresses each send `limit` requests without hitting 429 — their buckets are independent.
5. Authenticated caller sends (authenticated limit + 1) requests — the (limit + 1)th request returns 429.
6. Two authenticated users from the same IP each send `authenticated limit` requests without hitting 429 — their buckets are independent (keyed by userId, not IP).
7. After receiving a 429, a subsequent request from the same key also returns 429 (bucket is exhausted, not partially refilled).

---

## 7. Security Test Strategy

### 7.1 Scope

Security tests verify that the authentication and authorisation mechanisms work correctly at the HTTP level. They complement SE's unit tests of `LocalAuthProvider` and `JwtFilter` by exercising the full Spring Security filter chain and verifying the observable HTTP behaviour — not just the internal logic.

### 7.2 JWT Validation

Tested in `JwtSecurityIT`. Every protected endpoint must behave correctly when given a malformed, expired, tampered, or absent token.

**Scenarios:**

| Scenario | Expected HTTP Response |
|---|---|
| No `Authorization` header on a protected endpoint | 401 |
| `Authorization` header present but not prefixed with `Bearer ` | 401 |
| Valid access token on a protected endpoint | 200 (or the endpoint's normal response) |
| Expired access token | 401 |
| Token with payload modified (tampered) after signing | 401 |
| Token signed with a different key | 401 |
| Token is a refresh token used on a protected resource endpoint | 401 (refresh tokens are not access tokens) |
| Access token after the user has been soft-deleted | 401 (tokens must be revoked on delete) |

**Approach:** `JwtTestHelper` generates all malformed tokens programmatically. The test sends each to a representative protected endpoint (e.g., `GET /api/v1/users/{id}`) and asserts the response status and error envelope.

The "access token after user soft-delete" scenario is a cross-module security test: it creates a user, logs them in to obtain a real access token via the login endpoint, calls `DELETE /api/v1/users/{id}` as an Admin, then retries the original request with the same access token and expects 401. This validates the `TokenRevocationPort` integration across the `user` and `auth.local` modules.

### 7.3 Admin Enforcement

Tested across all `*ApiTest` classes for every Admin-only endpoint.

**Approach:** Each Admin-only endpoint is tested twice for the authorisation boundary:
1. With an Admin JWT token — expected: the endpoint's normal response (200, 201, or 204).
2. With a regular user JWT token — expected: 403.
3. With no token — expected: 401.

These three tests are present for every endpoint marked "Admin" in DESIGN.md §3.

The Admin check mechanism (`@PreAuthorize("hasRole('ADMIN')")`) is tested indirectly through the HTTP layer, which is the correct level for QE. SE's unit tests verify the `JwtFilter` correctly maps `TokenClaims.roles` to `GrantedAuthority` objects. QE's tests verify the full chain from HTTP request to enforced 403.

### 7.4 Self-Access Rules

Tested in `UserSelfAccessIT` for the following endpoints where self-access is permitted (DESIGN.md §3.3):

- `GET /api/v1/users/{id}` — User may GET their own profile; 403 for another user's profile.
- `PUT /api/v1/users/{id}` — User may UPDATE their own profile; 403 for another user's profile.
- `GET /api/v1/users/{id}/roles` — User may list their own roles; 403 for another user's roles.
- `GET /api/v1/users/{id}/roles/history` — User may view their own audit history; 403 for another user's history.

**Scenarios:**

| Test | Actor | Path {id} | Expected |
|---|---|---|---|
| Self-GET profile | Regular user A | A's ID | 200 |
| Cross-GET profile | Regular user A | User B's ID | 403 |
| Self-PUT profile | Regular user A | A's ID | 200 |
| Cross-PUT profile | Regular user A | User B's ID | 403 |
| Admin GET any profile | Admin | User B's ID | 200 |
| Self-GET roles | Regular user A | A's ID | 200 |
| Cross-GET roles | Regular user A | User B's ID | 403 |
| Admin GET any roles | Admin | User B's ID | 200 |
| Self-GET audit history | Regular user A | A's ID | 200 |
| Cross-GET audit history | Regular user A | User B's ID | 403 |

**Edge cases:**
- Regular user sends their own valid user ID but the ID in the path is cast to a string with leading zeros — verify the endpoint correctly normalises path ID parsing.
- Regular user who has been soft-deleted attempts to access their own profile — expect 401 (token should be revoked).

### 7.5 RBAC End-to-End Flows

Tested in `RbacEndToEndIT`. These are the most comprehensive security tests in the suite. Each test builds the full RBAC state from scratch and exercises both the allowed and denied paths.

**Core end-to-end scenarios (all P1):**

1. **Grant and exercise:** Create EntityTypeX → Create Role R with CREATE permission on EntityTypeX → Assign R to User U → User U creates an instance of EntityTypeX → Assert 201.
2. **Permission boundary:** User U (has CREATE on EntityTypeX) attempts READ on EntityTypeX → Assert 403 (READ not granted).
3. **Cross-type boundary:** User U (has CREATE on EntityTypeX) attempts CREATE on EntityTypeY → Assert 403 (no permission on EntityTypeY).
4. **Permission addition:** Admin adds READ permission on EntityTypeX to Role R → User U now GETs an instance of EntityTypeX → Assert 200.
5. **Role revocation:** Admin revokes Role R from User U → User U attempts CREATE on EntityTypeX → Assert 403.
6. **Role deletion:** Admin assigns Role R to User U → Admin deletes Role R → Assert 409 (role has assigned users) → Admin first revokes R from U → Admin now deletes R → Assert 204 → User U attempts CREATE → Assert 403.
7. **Entity type deletion blocked:** Create EntityType with instances → Attempt to DELETE entity type → Assert 409. Delete all instances → DELETE entity type → Assert 204.
8. **Password hash never returned:** Create a user via POST `/api/v1/users` → Assert the response body does not contain the string `passwordHash` or `password_hash` in any field at any nesting level.

### 7.6 Response Envelope Security

Tested in `ResponseEnvelopeIT`. The ADR-0009 envelope conventions are security-relevant in one specific way: the `passwordHash` field must never appear in any response.

The test strategy:
- After creating a user, call every user-returning endpoint (`GET /api/v1/users/{id}`, `GET /api/v1/users`, `PUT /api/v1/users/{id}`, `GET /api/v1/users/{id}/roles`) and assert that the raw JSON response string does not contain `"passwordHash"` or `"password_hash"`.
- This is a belt-and-suspenders check: even if the `UserResponse` DTO does not map `passwordHash`, a misconfigured Jackson serialiser or a DTO refactor could inadvertently expose it.

---

## 8. Coverage Targets

### 8.1 Functional Coverage

QE's sign-off requires that **all P1 scenarios in the test scenario register (Section 9) pass without failure.** No P1 scenario may be deferred or skipped.

P2 scenarios must also pass, with a maximum of zero known failures at sign-off. If a P2 scenario cannot be tested due to an implementation gap, QE raises a defect and the defect must be triaged by the Owner before sign-off.

P3 scenarios are best-effort. Known gaps in P3 coverage must be documented in the sign-off report with a rationale and a planned resolution date.

### 8.2 Line and Branch Coverage Targets

QE's tests are measured separately from SE's unit tests. The following targets apply to lines and branches exercised exclusively by QE's integration and API test classes (excluding SE's unit test classes from the measurement):

| Module | Line Coverage Target | Branch Coverage Target | Notes |
|---|---|---|---|
| `auth.local` (integration paths) | 85% | 80% | SE covers most branches; QE adds context-loaded filter chain paths |
| `user` (controller + service, integration paths) | 90% | 85% | High-risk module; self-access and audit paths must be covered |
| `role` (controller + service, integration paths) | 85% | 80% | |
| `entity` (controller + service, integration paths) | 90% | 85% | Permission enforcement has many branches |
| `ratelimit` (interceptor, integration paths) | 90% | 85% | Bucket behaviour; configurable limits must be tested |
| `common` (exception handler, integration paths) | 95% | 95% | Every HTTP error code must be exercised at least once |

### 8.3 Scenario Coverage Targets

The following categories from the test scenario register must achieve 100% scenario coverage (all scenarios exercised and passing) before QE sign-off:

- All Authentication scenarios (AUTH series).
- All JWT security scenarios (SEC series).
- All self-access enforcement scenarios (SA series).
- All RBAC end-to-end scenarios (E2E series).
- All "never expose passwordHash" scenarios.
- All 429 rate limit trigger scenarios.

The following categories must achieve 100% scenario coverage at P1 priority and 90% at P2 priority:

- User management scenarios (USER series).
- Role management scenarios (ROLE series).
- Entity management scenarios (ENT series).

### 8.4 QE Sign-Off Criteria

QE signs off on a build when all of the following are true:

1. All P1 scenarios in the scenario register pass.
2. All P2 scenarios pass (or each failure is a documented, Owner-triaged defect with an agreed resolution).
3. No `passwordHash` leak detected in any response.
4. All Flyway migrations V1–V6 execute cleanly against H2 on context start.
5. Coverage targets in Section 8.2 are met as reported by the Maven Surefire/Failsafe + JaCoCo report.
6. No test in the suite is marked `@Disabled` without an accompanying JIRA/GitHub issue reference in a comment.
7. The rate limit 429 response and `Retry-After` header are present and conformant.

---

## 9. Test Scenario Register

The following table is the authoritative list of all QE test scenarios. "Type" column values: H = Happy path, N = Negative/error path, S = Security, I = Integration/cross-module.

### 9.1 Authentication Scenarios

| ID | Module | Scenario Description | Type | Priority |
|---|---|---|---|---|
| AUTH-001 | auth | Login with valid username and password returns 200 with accessToken, refreshToken, and expiresIn | H | P1 |
| AUTH-002 | auth | Login with correct username but wrong password returns 401 with error envelope | N | P1 |
| AUTH-003 | auth | Login with unknown username returns 401 with error envelope | N | P1 |
| AUTH-004 | auth | Login with missing `username` field returns 400 with error envelope | N | P1 |
| AUTH-005 | auth | Login with missing `password` field returns 400 with error envelope | N | P1 |
| AUTH-006 | auth | Login with empty string username returns 400 | N | P2 |
| AUTH-007 | auth | Login with empty string password returns 400 | N | P2 |
| AUTH-008 | auth | Refresh with valid refresh token returns 200 with new accessToken | H | P1 |
| AUTH-009 | auth | Refresh with expired refresh token returns 401 | N | P1 |
| AUTH-010 | auth | Refresh with revoked refresh token returns 401 | N | P1 |
| AUTH-011 | auth | Refresh with malformed (non-JWT) refresh token returns 400 | N | P1 |
| AUTH-012 | auth | Refresh with missing `refreshToken` field returns 400 | N | P2 |
| AUTH-013 | auth | Logout with valid Bearer token returns 204 | H | P1 |
| AUTH-014 | auth | Subsequent use of a logged-out access token returns 401 | S | P1 |
| AUTH-015 | auth | Logout with no Authorization header returns 401 | N | P1 |
| AUTH-016 | auth | Login response does not contain passwordHash field at any nesting level | S | P1 |

### 9.2 JWT Security Scenarios

| ID | Module | Scenario Description | Type | Priority |
|---|---|---|---|---|
| SEC-001 | auth | Request to any protected endpoint with no Authorization header returns 401 | S | P1 |
| SEC-002 | auth | Request with `Authorization: Bearer ` (empty token) returns 401 | S | P1 |
| SEC-003 | auth | Request with Authorization header not prefixed `Bearer ` returns 401 | S | P1 |
| SEC-004 | auth | Request with valid access token to protected endpoint succeeds | H | P1 |
| SEC-005 | auth | Request with expired access token returns 401 | S | P1 |
| SEC-006 | auth | Request with payload-tampered token (signature invalid) returns 401 | S | P1 |
| SEC-007 | auth | Request with token signed by wrong key returns 401 | S | P1 |
| SEC-008 | auth | Access token is still valid after logout but refresh token is revoked — verify access is refused | S | P1 |
| SEC-009 | auth | Access token used after user soft-delete returns 401 (token revoked on delete) | S | P1 |
| SEC-010 | auth | Login endpoint is accessible without a token (public endpoint) | H | P1 |
| SEC-011 | auth | Refresh endpoint is accessible without a token (public endpoint) | H | P1 |

### 9.3 User Management — CRUD Scenarios

| ID | Module | Scenario Description | Type | Priority |
|---|---|---|---|---|
| USER-001 | user | Admin creates user with all valid fields returns 201 with UserResponse | H | P1 |
| USER-002 | user | UserResponse from create does not contain passwordHash field | S | P1 |
| USER-003 | user | Admin creates user with duplicate username returns 409 | N | P1 |
| USER-004 | user | Admin creates user with duplicate email returns 409 | N | P1 |
| USER-005 | user | Admin creates user with missing required field (username) returns 400 | N | P1 |
| USER-006 | user | Admin creates user with missing required field (email) returns 400 | N | P1 |
| USER-007 | user | Admin creates user with missing required field (fullName) returns 400 | N | P1 |
| USER-008 | user | Admin creates user with invalid email format returns 400 | N | P2 |
| USER-009 | user | Non-admin user attempts to create user returns 403 | S | P1 |
| USER-010 | user | Unauthenticated request to create user returns 401 | S | P1 |
| USER-011 | user | Admin gets any user by ID returns 200 with UserResponse | H | P1 |
| USER-012 | user | Admin gets non-existent user returns 404 with error envelope | N | P1 |
| USER-013 | user | Admin lists users returns 200 with paginated response including meta | H | P1 |
| USER-014 | user | List users response includes page, size, and total in meta | H | P1 |
| USER-015 | user | Admin lists users filtered by department returns only matching users | H | P2 |
| USER-016 | user | Admin lists users filtered by name (partial match) returns only matching users | H | P2 |
| USER-017 | user | Non-admin user requests list of all users returns 403 | S | P1 |
| USER-018 | user | Admin updates any user's mutable fields returns 200 | H | P1 |
| USER-019 | user | Admin updates user with duplicate email returns 409 | N | P2 |
| USER-020 | user | Admin soft-deletes user returns 204 | H | P1 |
| USER-021 | user | Soft-deleted user's subsequent login attempt returns 401 | S | P1 |
| USER-022 | user | Admin attempts to delete already-deleted user returns 404 | N | P1 |
| USER-023 | user | Non-admin attempts to delete user returns 403 | S | P1 |
| USER-024 | user | UserResponse never contains passwordHash field in any endpoint | S | P1 |

### 9.4 User Management — Self-Access Scenarios

| ID | Module | Scenario Description | Type | Priority |
|---|---|---|---|---|
| SA-001 | user | Regular user gets their own profile by ID returns 200 | H | P1 |
| SA-002 | user | Regular user gets another user's profile returns 403 | S | P1 |
| SA-003 | user | Admin gets any user's profile returns 200 | H | P1 |
| SA-004 | user | Regular user updates their own profile returns 200 | H | P1 |
| SA-005 | user | Regular user updates another user's profile returns 403 | S | P1 |
| SA-006 | user | Admin updates any user's profile returns 200 | H | P1 |
| SA-007 | user | Regular user gets their own role list returns 200 | H | P1 |
| SA-008 | user | Regular user gets another user's role list returns 403 | S | P1 |
| SA-009 | user | Admin gets any user's role list returns 200 | H | P1 |
| SA-010 | user | Regular user gets their own role history returns 200 | H | P1 |
| SA-011 | user | Regular user gets another user's role history returns 403 | S | P1 |
| SA-012 | user | Admin gets any user's role history returns 200 | H | P1 |

### 9.5 User Management — Role Assignment and Audit Log Scenarios

| ID | Module | Scenario Description | Type | Priority |
|---|---|---|---|---|
| ROLE-ASSIGN-001 | user | Admin assigns existing role to user returns 204 | H | P1 |
| ROLE-ASSIGN-002 | user | Assigning role creates an ASSIGNED audit log entry | I | P1 |
| ROLE-ASSIGN-003 | user | Audit log entry contains correct userId, roleId, roleName snapshot, performedBy, performedAt | I | P1 |
| ROLE-ASSIGN-004 | user | Admin assigns non-existent role to user returns 404 | N | P1 |
| ROLE-ASSIGN-005 | user | Admin assigns already-assigned role returns 409 | N | P1 |
| ROLE-ASSIGN-006 | user | Non-admin attempts to assign role returns 403 | S | P1 |
| ROLE-ASSIGN-007 | user | Admin revokes role from user returns 204 | H | P1 |
| ROLE-ASSIGN-008 | user | Revoking role creates a REVOKED audit log entry | I | P1 |
| ROLE-ASSIGN-009 | user | Admin revokes non-assigned role returns 404 | N | P1 |
| ROLE-ASSIGN-010 | user | Non-admin attempts to revoke role returns 403 | S | P1 |
| ROLE-ASSIGN-011 | user | Admin gets paginated role history for user — returns correct entries in order | H | P1 |
| ROLE-ASSIGN-012 | user | Role history roleName snapshot is preserved even if role is later renamed | I | P2 |
| ROLE-ASSIGN-013 | user | Role history contains both ASSIGNED and REVOKED entries after a full assign-then-revoke cycle | I | P1 |

### 9.6 Role Management Scenarios

| ID | Module | Scenario Description | Type | Priority |
|---|---|---|---|---|
| ROLE-001 | role | Admin creates role with valid name and description returns 201 | H | P1 |
| ROLE-002 | role | Admin creates role with duplicate name returns 409 | N | P1 |
| ROLE-003 | role | Non-admin creates role returns 403 | S | P1 |
| ROLE-004 | role | Admin creates role with missing name returns 400 | N | P1 |
| ROLE-005 | role | Admin gets role by ID returns 200 with role and its permissions | H | P1 |
| ROLE-006 | role | Admin gets non-existent role returns 404 | N | P1 |
| ROLE-007 | role | Non-admin gets role returns 403 | S | P1 |
| ROLE-008 | role | Admin lists all roles returns 200 with list | H | P1 |
| ROLE-009 | role | Non-admin lists roles returns 403 | S | P1 |
| ROLE-010 | role | Admin updates role name and description returns 200 | H | P1 |
| ROLE-011 | role | Admin updates role to duplicate name returns 409 | N | P2 |
| ROLE-012 | role | Admin updates non-existent role returns 404 | N | P1 |
| ROLE-013 | role | Admin deletes unassigned role returns 204 | H | P1 |
| ROLE-014 | role | Admin deletes role with assigned users returns 409 | N | P1 |
| ROLE-015 | role | Non-admin deletes role returns 403 | S | P1 |
| ROLE-016 | role | Admin adds permission (action + entityTypeId) to role returns 201 | H | P1 |
| ROLE-017 | role | Admin adds duplicate permission (same role + action + entityTypeId) returns 409 | N | P1 |
| ROLE-018 | role | Admin adds permission referencing non-existent entity type returns 404 | N | P1 |
| ROLE-019 | role | Non-admin adds permission returns 403 | S | P1 |
| ROLE-020 | role | Admin removes permission from role returns 204 | H | P1 |
| ROLE-021 | role | Admin removes non-existent permission returns 404 | N | P1 |
| ROLE-022 | role | Non-admin removes permission returns 403 | S | P1 |
| ROLE-023 | role | Admin gets permissions for role returns 200 with list | H | P1 |
| ROLE-024 | role | Admin gets permissions for non-existent role returns 404 | N | P1 |

### 9.7 Entity Type Scenarios

| ID | Module | Scenario Description | Type | Priority |
|---|---|---|---|---|
| ENT-001 | entity | Admin creates entity type with valid name returns 201 | H | P1 |
| ENT-002 | entity | Admin creates entity type with duplicate name returns 409 | N | P1 |
| ENT-003 | entity | Non-admin creates entity type returns 403 | S | P1 |
| ENT-004 | entity | Unauthenticated request to create entity type returns 401 | S | P1 |
| ENT-005 | entity | Authenticated user lists entity types returns 200 | H | P1 |
| ENT-006 | entity | Unauthenticated user lists entity types returns 401 | S | P1 |
| ENT-007 | entity | Authenticated user gets entity type by ID returns 200 | H | P1 |
| ENT-008 | entity | Authenticated user gets non-existent entity type returns 404 | N | P1 |
| ENT-009 | entity | Admin updates entity type name returns 200 | H | P1 |
| ENT-010 | entity | Admin updates entity type to duplicate name returns 409 | N | P2 |
| ENT-011 | entity | Non-admin updates entity type returns 403 | S | P1 |
| ENT-012 | entity | Admin deletes entity type with no instances returns 204 | H | P1 |
| ENT-013 | entity | Admin deletes entity type that has existing instances returns 409 | N | P1 |
| ENT-014 | entity | Non-admin deletes entity type returns 403 | S | P1 |

### 9.8 Entity Instance Scenarios

| ID | Module | Scenario Description | Type | Priority |
|---|---|---|---|---|
| INST-001 | entity | User with CREATE permission creates instance returns 201 | H | P1 |
| INST-002 | entity | Created instance has createdBy set to the caller's userId | I | P1 |
| INST-003 | entity | User without CREATE permission attempts to create instance returns 403 | S | P1 |
| INST-004 | entity | Create instance for non-existent entity type returns 404 | N | P1 |
| INST-005 | entity | User with READ permission gets instance by ID returns 200 | H | P1 |
| INST-006 | entity | User without READ permission gets instance returns 403 | S | P1 |
| INST-007 | entity | Get instance with non-existent ID returns 404 | N | P1 |
| INST-008 | entity | Get instance with mismatched typeId in path returns 404 | N | P1 |
| INST-009 | entity | User with READ permission lists instances returns 200 with pagination | H | P1 |
| INST-010 | entity | User without READ permission lists instances returns 403 | S | P1 |
| INST-011 | entity | User with UPDATE permission updates instance returns 200 | H | P1 |
| INST-012 | entity | User without UPDATE permission updates instance returns 403 | S | P1 |
| INST-013 | entity | User with DELETE permission deletes instance returns 204 | H | P1 |
| INST-014 | entity | User without DELETE permission deletes instance returns 403 | S | P1 |
| INST-015 | entity | Unauthenticated request to any instance endpoint returns 401 | S | P1 |
| INST-016 | entity | User has CREATE but not READ — creates instance, then cannot list it | S | P2 |

### 9.9 RBAC End-to-End Scenarios

| ID | Module | Scenario Description | Type | Priority |
|---|---|---|---|---|
| E2E-001 | rbac | Create user → assign role with CREATE on EntityTypeX → user creates instance → 201 | I | P1 |
| E2E-002 | rbac | Same user attempts CREATE on EntityTypeY (no permission on Y) → 403 | S | P1 |
| E2E-003 | rbac | Same user attempts READ on EntityTypeX (no READ granted) → 403 | S | P1 |
| E2E-004 | rbac | Admin adds READ permission to role → user can now GET instance → 200 | I | P1 |
| E2E-005 | rbac | Admin revokes role from user → user attempts CREATE on EntityTypeX → 403 | S | P1 |
| E2E-006 | rbac | Delete role blocked when user is assigned → revoke role → delete role → user loses access | I | P1 |
| E2E-007 | rbac | User with CREATE+UPDATE creates and updates instance; without DELETE cannot delete → 403 | I | P1 |
| E2E-008 | rbac | Role with all four permissions (CREATE, READ, UPDATE, DELETE) allows all operations | H | P1 |
| E2E-009 | rbac | Two users with different roles — each can only perform what their role permits | I | P1 |
| E2E-010 | rbac | Admin assigns role, views audit history entry, revokes role, views second audit entry | I | P1 |
| E2E-011 | rbac | Rename role — audit history roleName snapshot is not changed (point-in-time preservation) | I | P2 |
| E2E-012 | rbac | Password hash not present in any response across a full user lifecycle (create, get, update, list) | S | P1 |

### 9.10 Rate Limiting Scenarios

| ID | Module | Scenario Description | Type | Priority |
|---|---|---|---|---|
| RATE-001 | ratelimit | Anonymous caller sends (limit + 1) requests — (limit + 1)th returns 429 | N | P2 |
| RATE-002 | ratelimit | 429 response body conforms to ADR-0009 error envelope with status "error" and code field | N | P2 |
| RATE-003 | ratelimit | 429 response includes Retry-After header with a positive integer value | S | P1 |
| RATE-004 | ratelimit | Two anonymous callers from different IPs each send `limit` requests — neither hits 429 | I | P2 |
| RATE-005 | ratelimit | Authenticated caller sends (auth limit + 1) requests — (limit + 1)th returns 429 | N | P2 |
| RATE-006 | ratelimit | Two authenticated users from same IP each send `auth limit` requests — neither hits 429 (userId-keyed, not IP-keyed) | I | P2 |
| RATE-007 | ratelimit | After receiving 429, subsequent request from same key also returns 429 | N | P3 |

### 9.11 API Response Envelope and Convention Scenarios

| ID | Module | Scenario Description | Type | Priority |
|---|---|---|---|---|
| ENV-001 | common | All success responses are wrapped in `{ "status": "success", "data": ... }` envelope | H | P2 |
| ENV-002 | common | All paginated list responses include `{ "meta": { "page", "size", "total" } }` | H | P2 |
| ENV-003 | common | All error responses conform to `{ "status": "error", "code": "...", "message": "...", "timestamp": "..." }` envelope | N | P1 |
| ENV-004 | common | 400 response includes a meaningful `code` field and non-null `message` | N | P1 |
| ENV-005 | common | 401 response has correct envelope — not Spring's default whitepage | N | P1 |
| ENV-006 | common | 403 response has correct envelope — not Spring's default whitepage | N | P1 |
| ENV-007 | common | 404 response has correct envelope — not Spring's default whitepage | N | P1 |
| ENV-008 | common | 409 response has correct envelope with meaningful code | N | P1 |
| ENV-009 | common | 429 response has correct envelope — not Spring's default whitepage | N | P1 |
| ENV-010 | common | All timestamp fields in responses are ISO-8601 UTC formatted strings | H | P2 |
| ENV-011 | common | All ID fields in responses are numeric (Long), not string-encoded | H | P3 |
| ENV-012 | common | passwordHash field is absent from all UserResponse instances across all endpoints | S | P1 |

### 9.12 Database and Infrastructure Scenarios

| ID | Module | Scenario Description | Type | Priority |
|---|---|---|---|---|
| INFRA-001 | common | All Flyway migrations V1–V6 execute cleanly on Spring context startup against H2 | I | P1 |
| INFRA-002 | common | Flyway reports exactly 6 applied migrations after startup | I | P1 |
| INFRA-003 | common | Application context starts successfully with no bean wiring errors | I | P1 |
| INFRA-004 | entity | `permissions.entity_type_id` FK constraint (added via ALTER TABLE in V3) is enforced — insert permission with non-existent entity type ID at DB level fails | I | P2 |

---

### Scenario Count Summary

| Category | P1 | P2 | P3 | Total |
|---|---|---|---|---|
| Authentication (AUTH) | 14 | 2 | 0 | 16 |
| JWT Security (SEC) | 11 | 0 | 0 | 11 |
| User CRUD (USER) | 18 | 3 | 0 | 21 |
| Self-Access (SA) | 12 | 0 | 0 | 12 |
| Role Assignment and Audit (ROLE-ASSIGN) | 11 | 2 | 0 | 13 |
| Role Management (ROLE) | 20 | 2 | 0 | 22 |
| Entity Types (ENT) | 13 | 1 | 0 | 14 |
| Entity Instances (INST) | 15 | 1 | 0 | 16 |
| RBAC End-to-End (E2E) | 11 | 1 | 0 | 12 |
| Rate Limiting (RATE) | 1 | 5 | 1 | 7 |
| Response Envelope (ENV) | 8 | 3 | 1 | 12 |
| Infrastructure (INFRA) | 3 | 1 | 0 | 4 |
| **TOTAL** | **137** | **21** | **2** | **160** |

---

## Appendix A — Test Support Classes (Plan Only, No Code)

The following support classes will be created once Owner approval is granted. These are described here so the plan is complete and reviewable.

| Class | Package | Purpose |
|---|---|---|
| `BaseApiTest` | `com.userrole.support` | Abstract base class carrying `@SpringBootTest`, `@AutoConfigureMockMvc`, common MockMvc field, `@Transactional`. All `*ApiTest` classes extend this. |
| `TestDataFactory` | `com.userrole.support` | Static factory methods returning pre-populated request DTOs with unique test values. No persistence logic. |
| `JwtTestHelper` | `com.userrole.support` | Reads JWT signing key from test properties, provides methods to generate valid admin tokens, valid user tokens, expired tokens, and tampered tokens. |
| `reset_test_data.sql` | `src/test/resources/sql/` | Truncates all application tables in FK-safe order (instances → entity_types → user_role_audit_log → user_roles → stored_tokens → users → permissions → roles except ADMIN). Used only by `RbacEndToEndIT`. |

---

## Appendix B — Open Questions for Owner

The following questions must be resolved before QE begins writing tests. If the Owner does not provide explicit answers, the stated default assumption will be used.

| # | Question | Default Assumption |
|---|---|---|
| 1 | Should `RateLimitApiTest` use a separate Spring context profile (`@ActiveProfiles("ratelimit-test")`) or is `@TestPropertySource` annotation on the test class sufficient? | `@TestPropertySource` is sufficient; no separate profile needed unless there are other test-environment configuration differences. |
| 2 | The `RbacEndToEndIT` tests that require cross-transaction state cannot use `@Transactional` rollback. Is a `reset_test_data.sql` truncation script acceptable, or should a different strategy be used? | Truncation script is acceptable. |
| 3 | Does QE have permission to add the `reset_test_data.sql` file to `src/test/resources/sql/` without a separate approval, since it is a test resource and not production code? | Yes, test resources do not require separate approval beyond this plan. |
| 4 | The V6 migration seeds the ADMIN role but no ADMIN user. Tests that require an Admin actor must create one programmatically and assign the ADMIN role. Is this the intended test setup pattern? | Yes, confirmed by SE_IMPLEMENTATION_PLAN.md §8, Open Question 1. |
| 5 | Should `RateLimitApiTest` be excluded from the default Maven test run and only executed in a dedicated CI stage to avoid the context dirtying overhead? | RateLimitApiTest runs in the standard test phase; its `@DirtiesContext` overhead is acceptable. |

---

*Submitted by: QE | Date: 2026-03-03*
*Awaiting: Owner Approval*
