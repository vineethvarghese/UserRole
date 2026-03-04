# UserRole

A Java web service for centralised management of Users, Roles, and Entities with role-based access control (RBAC), JWT authentication, and per-client rate limiting.

---

## Table of Contents

- [Project Overview](#project-overview)
- [Key Features](#key-features)
- [Tech Stack](#tech-stack)
- [Architecture](#architecture)
- [RBAC Model](#rbac-model)
- [Getting Started](#getting-started)
- [API Overview](#api-overview)
- [Testing](#testing)
- [Documentation](#documentation)
- [Key Design Decisions](#key-design-decisions)

---

## Project Overview

UserRole provides a self-contained REST API for managing the lifecycle of users, roles, permissions, and typed entity instances. It enforces RBAC at the endpoint level using Spring Security method security, issues and validates JWTs, and tracks all role assignment changes in an immutable audit log.

The authentication layer is built on a Port and Adapter pattern so the local JWT implementation can be replaced by an external identity provider (Keycloak, Auth0, Okta) by swapping a single Spring bean, with zero changes to any other module.

---

## Key Features

- **RBAC** — Users are assigned one or more Roles. Each Role holds a set of Permissions. Each Permission is a combination of an Action (`CREATE`, `READ`, `UPDATE`, `DELETE`) and an EntityType.
- **JWT authentication** — Access tokens (15 minutes) and refresh tokens (7 days) issued at login. Token revocation on logout. Pluggable auth provider via the `auth.spi` interface.
- **Rate limiting** — Per-IP limiting for anonymous requests and per-user-ID limiting for authenticated requests. Limits are configurable via `application.properties`.
- **Optimistic locking** — A `@Version` column on every mutable entity prevents lost updates. PUT requests must include the current `version` value.
- **Role change audit trail** — Every role assignment and revocation is recorded in `UserRoleAuditLog` with a point-in-time snapshot of the role name, the acting admin, and a UTC timestamp.
- **Standardised API envelope** — All responses follow a consistent `ApiResponse` structure with separate success and error shapes.
- **Database portability** — Schema managed entirely by Flyway migrations. Switch from H2 to PostgreSQL or MySQL by changing the datasource configuration.

---

## Tech Stack

| Component | Choice |
|---|---|
| Language | Java 21 (LTS) |
| Build tool | Maven 3.9+ |
| Framework | Spring Boot 3.4.3 |
| Security | Spring Security (method security with `@PreAuthorize`) |
| Persistence | Spring Data JPA |
| Database (local) | H2 2.3.232 (in-memory, PostgreSQL compatibility mode) |
| Schema migrations | Flyway 10.20.1 |
| JWT signing | JJWT 0.12.6 (`jjwt-api`, `jjwt-impl`, `jjwt-jackson`) |
| Rate limiting | Bucket4j 8.10.1 (in-memory) |
| Validation | Spring Boot Starter Validation (Jakarta Bean Validation) |

---

## Architecture

The codebase uses a vertical-slice package structure. Each domain package owns its entities, repository, service interface, service implementation, controller, and DTOs. Cross-package calls are permitted only through defined interfaces — no package may reach directly into another package's implementation.

```
com.userrole
├── auth.spi      (AuthProvider interface, TokenClaims, TokenRevocationPort)
├── auth.local    (LocalAuthProvider — JWT signing, DB-backed credentials, SecurityConfig)
├── auth.external (placeholder for Keycloak / Auth0 / Okta adapter)
├── user          (User CRUD, role assignment, UserRoleAuditLog)
├── role          (Role CRUD, permissions, PermissionChecker interface)
├── entity        (EntityType + EntityInstance CRUD)
├── ratelimit     (HandlerInterceptor — per-IP and per-user Bucket4j buckets)
└── common        (ApiResponse envelope, exception hierarchy, GlobalExceptionHandler,
                   PageRequest / PageMeta / PageResponse, RoleAssignmentQueryPort,
                   RoleConstants)
```

**Module boundary rules:**

- `auth.local` calls `user` only through `RoleAssignmentQueryPort` (defined in `common`) to retrieve role IDs at login time and embed them in the JWT.
- `entity` consumes `PermissionChecker` (defined in `role`) to enforce access control on entity operations. It does not call `role` directly.
- `auth.spi` has no dependencies on any other module — it is the stable port through which all auth flows pass.

**Flyway migrations** (applied in order at startup):

| Version | Description |
|---|---|
| V1 | Create `users` table |
| V2 | Create `roles` and `permissions` tables |
| V3 | Create `entity_types` and `entity_instances` tables |
| V4 | Create `stored_tokens` table (refresh token persistence) |
| V5 | Create `user_role_audit_log` table |
| V6 | Seed the `ADMIN` role |
| V7 | Add `version` columns to all mutable entities (optimistic locking) |

---

## RBAC Model

```
User  →(many)→  Role  →(many)→  Permission( Action × EntityType )

Actions: CREATE | READ | UPDATE | DELETE
```

A user acquires permissions transitively through their assigned roles. The `PermissionChecker` interface in the `role` module evaluates whether a given user, identified by the role IDs embedded in their JWT claims, holds a specific Action on a specific EntityType. Permission is denied unless at least one assigned role grants it.

---

## Getting Started

**Prerequisites**

- Java 21
- Maven 3.9+

**Build**

```bash
mvn clean package -DskipTests
```

**Run**

```bash
mvn spring-boot:run
```

The application starts on `http://localhost:8080`.

**H2 console** (development only) is available at `http://localhost:8080/h2-console`:
- JDBC URL: `jdbc:h2:mem:userroledb`
- Username: `sa`
- Password: *(blank)*

For the complete build, run, and first-use walkthrough — including how to seed the initial admin user and obtain a JWT — see [docs/RUN_AND_TEST_MANUAL.md](docs/RUN_AND_TEST_MANUAL.md).

---

## API Overview

All endpoints are prefixed with `/api`. Every response is wrapped in the standard `ApiResponse` envelope.

| Group | Base path | Description |
|---|---|---|
| Auth | `/api/auth` | Login, token refresh, logout |
| Users | `/api/users` | Create, read, update, deactivate users; assign and revoke roles; view role history |
| Roles | `/api/roles` | Create, read, update, delete roles; add and remove permissions |
| Entity Types | `/api/entity-types` | Define named entity categories |
| Entity Instances | `/api/entity-instances` | Create and manage instances of a given entity type, subject to RBAC permission checks |

For the complete API reference with request/response schemas, example `curl` commands, and a full end-to-end walkthrough, see [docs/RUN_AND_TEST_MANUAL.md](docs/RUN_AND_TEST_MANUAL.md).

---

## Testing

The test suite contains **292 tests** across unit tests and full Spring Boot integration tests (`@SpringBootTest` with `MockMvc`).

```bash
# Run all tests
mvn test

# Run a single test class
mvn test -Dtest=AuthApiTest

# Run a single test method
mvn test -Dtest=AuthApiTest#login_withValidCredentials_returns200WithTokens
```

Tests use H2 in PostgreSQL compatibility mode. Rate limit thresholds are overridden per test class via `@TestPropertySource` to keep tests fast and deterministic. Shared test infrastructure lives in `com.userrole.support` (`BaseApiTest`, `TestDataFactory`, `JwtTestHelper`).

For the full test plan including scenario breakdown and coverage rationale, see [docs/QE_TEST_PLAN.md](docs/QE_TEST_PLAN.md).

---

## Documentation

| Document | Description |
|---|---|
| [docs/DESIGN.md](docs/DESIGN.md) | Architecture Decision Records (ADRs), module specs, approved library list — single source of truth |
| [docs/USE_CASES.md](docs/USE_CASES.md) | Approved use case document |
| [docs/SE_IMPLEMENTATION_PLAN.md](docs/SE_IMPLEMENTATION_PLAN.md) | Wave-based implementation plan |
| [docs/QE_TEST_PLAN.md](docs/QE_TEST_PLAN.md) | Test plan covering all 292 scenarios |
| [docs/RUN_AND_TEST_MANUAL.md](docs/RUN_AND_TEST_MANUAL.md) | Build, run, API reference, end-to-end walkthrough |
| [docs/FOLLOW_UP.md](docs/FOLLOW_UP.md) | Known issues and follow-up items |
| [docs/AGENT_COORDINATION.md](docs/AGENT_COORDINATION.md) | Agent workflow, approval gates, status tracker |

---

## Key Design Decisions

Full rationale for each decision is recorded in [docs/DESIGN.md](docs/DESIGN.md).

- **Port and Adapter auth (ADR-0006)** — The `AuthProvider` interface in `auth.spi` is the only contract the rest of the application depends on. Replacing `LocalAuthProvider` with a Keycloak or Auth0 adapter requires no changes outside `auth.local` and `auth.external`.
- **Database portability (ADR-0004)** — All schema changes are managed through Flyway migrations. Switching from H2 to PostgreSQL or MySQL requires only a datasource configuration change and the appropriate JDBC driver on the classpath.
- **Optimistic locking (ADR-0012)** — Every mutable entity carries a `@Version` column. PUT requests must supply the current `version` value; a mismatch returns HTTP 409, preventing lost updates under concurrent modification.
- **Standardised API envelope (ADR-0009)** — All responses — success and error — are wrapped in `ApiResponse`. Error responses include a machine-readable error code, a human-readable message, and a timestamp. This is enforced globally by `GlobalExceptionHandler`.
- **JWT secret (production note)** — The default `jwt.secret` in `application.properties` is a placeholder for local development. Replace it with a securely generated 256-bit Base64-encoded key before deploying to any non-local environment.
