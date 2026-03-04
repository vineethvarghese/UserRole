# Design Document — UserRole Web Service

**Author:** PE
**Last Updated:** 2026-03-03

> This document is the single source of architectural truth.
> Maintained exclusively by the Principal Engineer (PE).
> No implementation or testing begins without decisions recorded and approved here.

---

## 1. System Overview

UserRole is a Java web service providing centralised management of Users, Roles, and Entities via a REST API.
It enforces RBAC: a Role defines which Actions a User may perform on which Entity Types.
The API is secured by JWT authentication and protected by rate limiting.
Authentication is provider-agnostic by design (local in v1, migratable to external with zero code changes).

---

## 2. Module Boundaries

```
com.userrole
├── auth
│   ├── spi/          ← AuthProvider interface + token claims contract (the Port)
│   ├── local/        ← LocalAuthProvider: DB credentials + local JWT issuance (v1)
│   └── external/     ← Placeholder for future external provider (e.g. Keycloak, Auth0)
├── user              ← User entity, profile, CRUD (repo → service → controller)
├── role              ← Role entity, permissions, CRUD (repo → service → controller)
├── entity            ← Entity Type + Instance CRUD (repo → service → controller)
├── ratelimit         ← Rate limiting configuration and filter
└── common            ← Shared: API response wrapper, exception handling, pagination, constants
```

**Cross-package rule:** No package may directly reference internal classes of another package.
All inter-module communication is via defined service interfaces only.

---

## 3. Module Interface Specifications

**Status:** APPROVED 2026-03-03
**API versioning convention:** All endpoints prefixed `/api/v1/`

---

### 3.1 Module: `common`

No REST endpoints. Provides shared types used by all modules.

**Types:**
```
ApiResponse<T>           { status: String, data: T, meta?: PageMeta }
PageMeta                 { page: int, size: int, total: long }
PageRequest              { page: int, size: int }

UserRoleException        (base, unchecked)
 ├── ResourceNotFoundException   → HTTP 404
 ├── ConflictException           → HTTP 409
 ├── ForbiddenException          → HTTP 403
 ├── ValidationException         → HTTP 400
 └── RateLimitException          → HTTP 429

GlobalExceptionHandler   (@RestControllerAdvice — maps all exceptions to error envelope per ADR-0009)
```

---

### 3.2 Module: `auth`

**SPI types (`auth.spi`):**
```
TokenClaims {
  userId:      Long
  username:    String
  roles:       List<String>
  issuedAt:    Instant
  expiresAt:   Instant
}

AuthProvider (interface) {
  authenticate(username: String, password: String): TokenResponse
  refresh(refreshToken: String): TokenResponse
  invalidate(accessToken: String): void
  validate(accessToken: String): TokenClaims
}
```

**Local types (`auth.local`):**
```
StoredToken {
  id:           Long
  userId:       Long
  refreshToken: String
  expiresAt:    Instant
  revoked:      Boolean
}
```

**REST Endpoints:**

| Method | Path | Request Body | Response | Auth Required |
|--------|------|-------------|----------|---------------|
| POST | `/api/v1/auth/login` | `{ username, password }` | `{ accessToken, refreshToken, expiresIn }` | No |
| POST | `/api/v1/auth/refresh` | `{ refreshToken }` | `{ accessToken, expiresIn }` | No |
| POST | `/api/v1/auth/logout` | — | 204 | Yes (Bearer) |

---

### 3.3 Module: `user`

**Domain entities:**
```
User {
  id:           Long     (PK, generated)
  username:     String   (unique, not null)
  passwordHash: String   (not null — never returned in API responses)
  fullName:     String   (not null)
  department:   String
  email:        String   (unique, not null)
  phone:        String
  active:       Boolean  (default true — false = soft deleted)
  version:      Long     (@Version — optimistic locking, ADR-0012)
  createdAt:    Instant
  updatedAt:    Instant
}

UserRole {
  userId:  Long  (FK → User, composite PK)
  roleId:  Long  (FK → Role, composite PK)
}

UserRoleAuditLog {
  id:          Long        (PK, generated)
  userId:      Long        (FK → User)
  roleId:      Long        (FK → Role)
  roleName:    String      (point-in-time snapshot)
  action:      AuditAction (ASSIGNED | REVOKED)
  performedBy: Long        (FK → User)
  performedAt: Instant
}
```

**Service interface (`UserService`):**
```
createUser(request: CreateUserRequest): UserResponse
getUserById(id: Long): UserResponse
listUsers(filter: UserFilter, page: PageRequest): PageResponse<UserResponse>
updateUser(id: Long, request: UpdateUserRequest): UserResponse
deleteUser(id: Long): void                                              // soft delete; revokes tokens
assignRole(userId: Long, roleId: Long, performedBy: Long): void        // writes ASSIGNED audit entry
revokeRole(userId: Long, roleId: Long, performedBy: Long): void        // writes REVOKED audit entry
getRolesForUser(userId: Long): List<RoleResponse>
getRoleIdsForUser(userId: Long): Set<Long>                             // used by auth.local at login
getRoleChangeHistory(userId: Long, page: PageRequest): PageResponse<UserRoleAuditEntry>
```

**REST Endpoints:**

| Method | Path | Request | Response | Who |
|--------|------|---------|----------|-----|
| POST | `/api/v1/users` | CreateUserRequest | 201 UserResponse | Admin |
| GET | `/api/v1/users/{id}` | — | UserResponse | Admin / Self |
| GET | `/api/v1/users` | `?dept=&name=&page=&size=` | PageResponse | Admin |
| PUT | `/api/v1/users/{id}` | UpdateUserRequest | UserResponse | Admin / Self |
| DELETE | `/api/v1/users/{id}` | — | 204 | Admin |
| POST | `/api/v1/users/{id}/roles/{roleId}` | — | 204 | Admin |
| DELETE | `/api/v1/users/{id}/roles/{roleId}` | — | 204 | Admin |
| GET | `/api/v1/users/{id}/roles` | — | List\<RoleResponse\> | Admin / Self |
| GET | `/api/v1/users/{id}/roles/history` | `?page=&size=` | PageResponse\<UserRoleAuditEntry\> | Admin / Self |

---

### 3.4 Module: `role`

**Domain entities:**
```
Role {
  id:          Long    (PK, generated)
  name:        String  (unique, not null)
  description: String
  version:     Long    (@Version — optimistic locking, ADR-0012)
  createdAt:   Instant
  updatedAt:   Instant
}

Permission {
  id:           Long    (PK, generated)
  role:         Role    (FK)
  action:       Action  (enum: CREATE | READ | UPDATE | DELETE)
  entityTypeId: Long    (FK → EntityType)
  (unique constraint: role + action + entityTypeId)
}
```

**Service interface (`RoleService`):**
```
createRole(request: CreateRoleRequest): RoleResponse
getRoleById(id: Long): RoleResponse
listRoles(): List<RoleResponse>
updateRole(id: Long, request: UpdateRoleRequest): RoleResponse
deleteRole(id: Long): void                                        // blocked if users assigned
addPermission(roleId: Long, action: Action, entityTypeId: Long): PermissionResponse
removePermission(roleId: Long, permissionId: Long): void
getPermissionsForRole(roleId: Long): List<PermissionResponse>
```

**Permission checker interface (`PermissionChecker`) — consumed by `entity` module:**
```
hasPermission(roleNames: List<String>, action: Action, entityTypeId: Long): boolean
```

**REST Endpoints:**

| Method | Path | Request | Response | Who |
|--------|------|---------|----------|-----|
| POST | `/api/v1/roles` | CreateRoleRequest | 201 RoleResponse | Admin |
| GET | `/api/v1/roles/{id}` | — | RoleResponse | Admin |
| GET | `/api/v1/roles` | — | List\<RoleResponse\> | Admin |
| PUT | `/api/v1/roles/{id}` | UpdateRoleRequest | RoleResponse | Admin |
| DELETE | `/api/v1/roles/{id}` | — | 204 | Admin |
| POST | `/api/v1/roles/{id}/permissions` | `{ action, entityTypeId }` | 201 PermissionResponse | Admin |
| DELETE | `/api/v1/roles/{id}/permissions/{permId}` | — | 204 | Admin |
| GET | `/api/v1/roles/{id}/permissions` | — | List\<PermissionResponse\> | Admin |

---

### 3.5 Module: `entity`

**Domain entities:**
```
EntityType {
  id:          Long    (PK, generated)
  name:        String  (unique, not null)
  description: String
  version:     Long    (@Version — optimistic locking, ADR-0012)
  createdAt:   Instant
  updatedAt:   Instant  (added in ADR-0012 for consistency)
}

EntityInstance {
  id:           Long       (PK, generated)
  entityType:   EntityType (FK, not null)
  name:         String     (not null)
  description:  String
  createdBy:    Long       (FK → User.id)
  version:      Long       (@Version — optimistic locking, ADR-0012)
  createdAt:    Instant
  updatedAt:    Instant
}
```

**Service interfaces:**
```
EntityTypeService {
  createEntityType(request: CreateEntityTypeRequest): EntityTypeResponse
  getEntityTypeById(id: Long): EntityTypeResponse
  listEntityTypes(): List<EntityTypeResponse>
  updateEntityType(id: Long, request: UpdateEntityTypeRequest): EntityTypeResponse
  deleteEntityType(id: Long): void        // blocked if instances exist
}

EntityInstanceService {
  createInstance(typeId: Long, request: CreateInstanceRequest, claims: TokenClaims): EntityInstanceResponse
  getInstanceById(typeId: Long, id: Long, claims: TokenClaims): EntityInstanceResponse
  listInstances(typeId: Long, page: PageRequest, claims: TokenClaims): PageResponse<EntityInstanceResponse>
  updateInstance(typeId: Long, id: Long, request: UpdateInstanceRequest, claims: TokenClaims): EntityInstanceResponse
  deleteInstance(typeId: Long, id: Long, claims: TokenClaims): void
}
```

> `TokenClaims` is passed explicitly so services can call `PermissionChecker` without coupling to the security context. This keeps services fully testable in isolation.

**REST Endpoints:**

| Method | Path | Request | Response | Auth |
|--------|------|---------|----------|------|
| POST | `/api/v1/entity-types` | CreateEntityTypeRequest | 201 EntityTypeResponse | Admin |
| GET | `/api/v1/entity-types` | — | List\<EntityTypeResponse\> | Any authenticated |
| GET | `/api/v1/entity-types/{typeId}` | — | EntityTypeResponse | Any authenticated |
| PUT | `/api/v1/entity-types/{typeId}` | UpdateEntityTypeRequest | EntityTypeResponse | Admin |
| DELETE | `/api/v1/entity-types/{typeId}` | — | 204 | Admin |
| POST | `/api/v1/entity-types/{typeId}/instances` | CreateInstanceRequest | 201 EntityInstanceResponse | Authorised User |
| GET | `/api/v1/entity-types/{typeId}/instances` | `?page=&size=` | PageResponse | Authorised User |
| GET | `/api/v1/entity-types/{typeId}/instances/{id}` | — | EntityInstanceResponse | Authorised User |
| PUT | `/api/v1/entity-types/{typeId}/instances/{id}` | UpdateInstanceRequest | EntityInstanceResponse | Authorised User |
| DELETE | `/api/v1/entity-types/{typeId}/instances/{id}` | — | 204 | Authorised User |

---

### 3.6 Module: `ratelimit`

No REST endpoints. Applied as a Spring `HandlerInterceptor` across all requests.

**Configuration (application.properties):**
```
ratelimit.anonymous.requests-per-minute=30
ratelimit.authenticated.requests-per-minute=120
```

**Behaviour:**
- Anonymous requests keyed by client IP
- Authenticated requests keyed by `userId` from `TokenClaims`
- On breach: throws `RateLimitException` → `GlobalExceptionHandler` → HTTP 429 + `Retry-After` header

---

### 3.9 Test Environment Decisions (from QE Test Plan — approved 2026-03-03)

| Decision | Value |
|----------|-------|
| H2 test mode | PostgreSQL compatibility mode (`MODE=PostgreSQL`) — surfaces dialect issues early |
| Rate limit overrides in tests | `@TestPropertySource`: anon = 3 req/min, auth = 5 req/min — no real time delays needed |
| Spring context strategy | One shared context (`BaseApiTest`); `RateLimitApiTest` isolated with `@DirtiesContext` |
| DB cleanup | `@Transactional` rollback by default; `reset_test_data.sql` truncation for cross-transaction tests |
| Admin test actor | Created programmatically per test run — no seeded admin user |

---

### 3.8 Approved Port Interfaces (from SE Implementation Plan — approved 2026-03-03)

Two additional interfaces were identified during implementation planning to preserve cross-package rules.

| Interface | Package | Method | Implemented By | Consumed By |
|-----------|---------|--------|---------------|-------------|
| `TokenRevocationPort` | `auth.spi` | `revokeAllTokensForUser(Long userId): void` | `auth.local` (via `LocalAuthProvider` or dedicated adapter) | `user` (in `deleteUser()`) |
| `RoleAssignmentQueryPort` | `common` | `countUsersAssignedToRole(Long roleId): long` | `user` (via adapter in `UserServiceImpl`) | `role` (in `deleteRole()`) |

**Also approved:**
- Flyway migration V6 (`V6__seed_admin_role.sql`) — seeds the `ADMIN` role row into `roles` table
- `RoleConstants` class in `common` — holds the `"ADMIN"` string constant; no magic strings in code
- No default Admin user seeded in v1

---

### 3.7 Module Dependency Graph

```
common       ←── all modules depend on common
auth.spi     ←── auth.local, entity, ratelimit
auth.local   ──→ auth.spi, common, user (getRoleIdsForUser at login to embed roles in JWT)
user         ──→ common, auth.spi
role         ──→ common
entity       ──→ common, auth.spi, role (PermissionChecker)
ratelimit    ──→ common, auth.spi
```

No circular dependencies.

---

## 4. Architecture Decision Records (ADRs)

---

### ADR-0001: Build Tool

**Date:** 2026-03-03
**Status:** APPROVED

**Context:**
A Java web service needs a build and dependency management tool.

**Options Considered:**
1. **Maven** — Wide enterprise adoption, mature ecosystem, XML is explicit; verbose, slower than Gradle
2. **Gradle** — Faster builds, Groovy/Kotlin DSL, highly flexible; more complex, steeper curve

**Decision:** Maven
Predictable, widely understood in enterprise Java, simpler CI integration, lower onboarding cost.

**Consequences:**
- Positive: Explicit dependency management, easy CI integration
- Negative: More verbose than Gradle; slower incremental builds
- Risk: None significant

**Owner Approval:** [x] APPROVED — 2026-03-03

---

### ADR-0002: Java Version

**Date:** 2026-03-03
**Status:** APPROVED

**Context:**
Long-term support version required for a production web service.

**Options Considered:**
1. **Java 21 (LTS)** — Latest LTS, virtual threads (Project Loom), records, sealed classes; requires team familiarity
2. **Java 17 (LTS)** — Very stable, widely deployed; missing Loom, older

**Decision:** Java 21 (LTS)
Current LTS, virtual threads benefit web service concurrency, modern language features reduce boilerplate.

**Consequences:**
- Positive: Future-proof, better concurrency model, modern APIs
- Negative: Team must be comfortable with Java 21 features
- Risk: Minor — Java 21 is well-established

**Owner Approval:** [x] APPROVED — 2026-03-03

---

### ADR-0003: Web Framework

**Date:** 2026-03-03
**Status:** APPROVED

**Context:**
Need a production-grade Java web framework for REST APIs with JWT and RBAC support.

**Options Considered:**
1. **Spring Boot 3.x** — Industry standard, Spring Security for JWT, Spring Data JPA for DB portability; opinionated, slower startup
2. **Quarkus** — Fast startup, native compilation; smaller ecosystem
3. **Micronaut** — Low memory, compile-time DI; less mature

**Decision:** Spring Boot 3.x
Best fit for the JWT + JPA + RBAC combination. Well-understood, lowest risk, richest ecosystem for this use case.

**Consequences:**
- Positive: Vast library support, strong security primitives, Spring Data covers DB portability
- Negative: Higher baseline memory footprint than Quarkus/Micronaut
- Risk: None significant

**Owner Approval:** [x] APPROVED — 2026-03-03

---

### ADR-0004: Database Strategy

**Date:** 2026-03-03
**Status:** APPROVED

**Context:**
Local database required now; must be portable to any SQL database later without code changes.

**Options Considered:**
1. **Spring Data JPA + H2 (local) + Flyway migrations** — JPA abstracts SQL dialect; H2 runs in-process; swap datasource URL to move to Postgres/MySQL; Flyway ensures schema versioning
2. **JDBC + raw SQL** — Full control; not portable (SQL dialect tied to DB)

**Decision:** Spring Data JPA with H2 for local development; Flyway for schema migrations.
Switching database = change datasource configuration only. Zero code changes required.

**Consequences:**
- Positive: True database portability; schema versioning from day one
- Negative: H2 has minor dialect differences from production DBs; JPA adds abstraction layer
- Risk: JPQL queries must avoid DB-specific functions to preserve portability

**Owner Approval:** [x] APPROVED — 2026-03-03

---

### ADR-0005: Package / Module Structure

**Date:** 2026-03-03
**Status:** APPROVED

**Context:**
System must be divided into independently testable modules with clear interfaces.
Packages must not have circular dependencies.

**Options Considered:**
1. **Vertical slice by domain** (chosen) — each package owns its full stack (entity → repo → service → controller)
2. **Horizontal layer** — all controllers together, all services together; encourages cross-domain coupling

**Decision:** Vertical slice by domain with a shared `common` package.

```
com.userrole
├── auth
│   ├── spi/          ← AuthProvider interface + token claims contract
│   ├── local/        ← LocalAuthProvider (v1)
│   └── external/     ← Placeholder (v2+)
├── user
├── role
├── entity
├── ratelimit
└── common
```

**Inter-package contract:** Packages interact only through service interfaces. No direct class-to-class calls across package boundaries.

**Consequences:**
- Positive: Each package is independently testable; clear ownership; low coupling
- Negative: Some duplication of structural code across packages acceptable by design
- Risk: Discipline required to enforce the no-cross-boundary rule; to be enforced in code review

**Owner Approval:** [x] APPROVED — 2026-03-03

---

### ADR-0006: Authentication Provider Strategy

**Date:** 2026-03-03
**Status:** APPROVED

**Context:**
Authentication must work locally in v1 (DB credentials, JWT issued internally) but must be migratable to an external provider (Keycloak, Auth0, Okta, Azure AD) without touching any code outside the `auth` package.

**Options Considered:**
1. **Port & Adapter pattern** — Define `AuthProvider` interface (the Port); `LocalAuthProvider` implements it in v1; `ExternalAuthProvider` drops in later. Rest of system sees only the interface.
2. **Hard-coded local auth** — Simple now; full rewrite required on migration.
3. **Spring Security abstraction only** — Spring's `AuthenticationManager` is provider-agnostic at the security layer but does not encapsulate JWT issuance strategy differences at the application level.

**Decision:** Port & Adapter (Option 1)

```
auth/
├── spi/
│   ├── AuthProvider          ← interface (the Port)
│   └── TokenClaims           ← provider-neutral claims contract
├── local/
│   └── LocalAuthProvider     ← implements AuthProvider using DB + local JWT signing
└── external/                 ← empty placeholder in v1
```

**Rules enforced by this ADR:**
1. No code outside `auth` may reference `LocalAuthProvider` directly — only `AuthProvider`
2. JWT validation logic is encapsulated in `AuthProvider` — callers only ask "is this token valid?"
3. Migrating to an external provider = implement `ExternalAuthProvider` + update Spring config; zero changes to `user`, `role`, `entity`, `ratelimit`, or `common`
4. Token claims contract (`TokenClaims`) is defined once in `auth/spi` and is provider-neutral

**Consequences:**
- Positive: Clean migration path; external provider adoption is isolated to one package
- Negative: Slightly more upfront structure in the `auth` package
- Risk: Claims contract differences between local and external JWT may require a mapping layer — acceptable, isolated in `auth/external`

**Owner Approval:** [x] APPROVED — 2026-03-03

---

### ADR-0007: JWT Library

**Date:** 2026-03-03
**Status:** APPROVED

**Context:**
Need a Java library to sign and verify JWTs within `auth.local`. Must be lightweight and compatible with Spring Boot 3.x / Spring Security 6.x. Signing logic is isolated in `auth.local` per ADR-0006.

**Options Considered:**
1. **JJWT (io.jsonwebtoken)** — Most widely used, fluent API, actively maintained; slightly verbose builder API
2. **Nimbus JOSE + JWT** — Used internally by Spring Security, very complete; more complex API surface
3. **Spring Security OAuth2 Resource Server (built-in)** — Zero extra dependency for validation; opinionated toward external JWKS, not local signing

**Decision:** JJWT (`io.jsonwebtoken:jjwt-api` + `jjwt-impl` + `jjwt-jackson`)
Best fit for local JWT signing in v1. Widely understood. Easy to discard when migrating to external provider since all signing logic is isolated in `auth.local`.

**Consequences:**
- Positive: Simple, well-documented API; isolated to auth.local; easy to remove on provider migration
- Negative: Extra dependency not needed if migrating early to external provider
- Risk: None significant

**Owner Approval:** [x] APPROVED — 2026-03-03

---

### ADR-0008: Rate Limiting Strategy

**Date:** 2026-03-03
**Status:** APPROVED

**Context:**
All API endpoints must be rate-limited. No external infrastructure (Redis, etc.) in v1. Must be upgradeable to distributed rate limiting without API changes.

**Options Considered:**
1. **Bucket4j (in-memory)** — Token bucket algorithm, Spring Boot integration, can back with Redis/Hazelcast later without API changes; limits are per-instance if scaled horizontally
2. **Resilience4j RateLimiter** — Good alongside circuit breakers; less flexible for per-user limits
3. **Spring Cloud Gateway** — Full gateway-level rate limiting; overkill, adds infrastructure dependency

**Decision:** Bucket4j with in-memory backing.
Rate limits applied per IP and per authenticated user. Upgrading to distributed = swap backing store; no API changes.

**Consequences:**
- Positive: No external infra required in v1; clean upgrade path to distributed
- Negative: Per-instance limits only if horizontally scaled in v1
- Risk: Acceptable for v1; documented as known limitation

**Owner Approval:** [x] APPROVED — 2026-03-03

---

### ADR-0009: API Response Conventions

**Date:** 2026-03-03
**Status:** APPROVED

**Context:**
All endpoints must return a consistent response envelope for reliable client parsing.

**Decision:** Standardised envelope defined in `common` package.

Success response:
```json
{
  "status": "success",
  "data": { },
  "meta": { "page": 1, "size": 20, "total": 100 }
}
```
(`meta` present only on paginated list endpoints)

Error response:
```json
{
  "status": "error",
  "code": "USER_NOT_FOUND",
  "message": "User with id 42 does not exist",
  "timestamp": "2026-03-03T10:00:00Z"
}
```

**Additional conventions:**
- All timestamps: ISO-8601 UTC
- All IDs: `Long` in JSON
- HTTP status codes used correctly: 200, 201, 204, 400, 401, 403, 404, 409, 429, 500

**Consequences:**
- Positive: Consistent client experience; error codes enable programmatic handling
- Negative: Slight overhead of wrapping all responses
- Risk: None significant

**Owner Approval:** [x] APPROVED — 2026-03-03

---

### ADR-0010: Error Handling Strategy

**Date:** 2026-03-03
**Status:** APPROVED

**Context:**
Errors must be handled centrally, not scattered across controllers or services.

**Decision:** Single `@RestControllerAdvice` in the `common` package maps all exceptions to the error envelope (ADR-0009). Domain exceptions defined per module, handled centrally. No try/catch in controllers or services unless unavoidable.

**Exception hierarchy (all unchecked):**
```
UserRoleException  (base)
├── ResourceNotFoundException   → HTTP 404
├── ConflictException           → HTTP 409
├── ForbiddenException          → HTTP 403
├── ValidationException         → HTTP 400
└── RateLimitException          → HTTP 429
```

**Consequences:**
- Positive: Single place to maintain error mapping; consistent error responses
- Negative: All modules depend on `common` exception types — acceptable by design
- Risk: None significant

**Owner Approval:** [x] APPROVED — 2026-03-03

---

### ADR-0011: Role Change Audit Strategy

**Date:** 2026-03-03
**Status:** APPROVED

**Context:**
Every assignment or revocation of a role on a user must be recorded for traceability. The system must answer: what changed, when, and who made the change.

**Options Considered:**
1. **Audit table in `user` module** — Simple, self-contained, queryable per user; audit scope limited to role changes only
2. **Dedicated `audit` module** — Extensible to all system events; overkill for v1
3. **Event-driven audit** — Decoupled, scalable; significant infrastructure overhead for v1

**Decision:** Option 1 — `UserRoleAuditLog` table owned by the `user` module.
If audit scope grows beyond role changes in future, this can be migrated to a dedicated `audit` module at that time.

**New Data Model (`user` module):**
```
UserRoleAuditLog {
  id:           Long        (PK, generated)
  userId:       Long        (FK → User — subject of the change)
  roleId:       Long        (FK → Role)
  roleName:     String      (snapshot at time of change — preserved if role is later renamed/deleted)
  action:       AuditAction (enum: ASSIGNED | REVOKED)
  performedBy:  Long        (FK → User — the admin who made the change)
  performedAt:  Instant
}
```

**Behaviour:**
- `assignRole()` automatically appends an `ASSIGNED` audit entry — no separate API call required
- `revokeRole()` automatically appends a `REVOKED` audit entry — no separate API call required
- `roleName` is a point-in-time snapshot; it does not update if the role is later renamed

**New service method:** `getRoleChangeHistory(userId, page) → PageResponse<UserRoleAuditEntry>`

**New endpoint:** `GET /api/v1/users/{id}/roles/history` — paginated; accessible by Admin or Self

**Consequences:**
- Positive: Full traceability of role changes; zero extra API calls to write; snapshot protects against role renaming/deletion
- Negative: Audit records are append-only (intentional); no edit/delete on audit log
- Risk: If audit volume is very high, table may grow large — acceptable in v1; partitioning/archival is a v2 concern

**Owner Approval:** [x] APPROVED — 2026-03-03

---

### ADR-0012: Optimistic Locking

**Date:** 2026-03-04
**Status:** APPROVED

**Context:**
No JPA entities have `@Version`. Concurrent update requests follow a read-modify-write pattern that silently overwrites changes (last-write-wins). This is a lost-update vulnerability affecting all PUT endpoints. This is distinct from the TOCTOU issues on CREATE (FOLLOW_UP.md items 5/6/7) which require a `DataIntegrityViolationException` handler — both fixes are needed, neither alone is sufficient.

**Options Considered:**
1. **`@Version` with client-visible version** (chosen) — Standard JPA optimistic locking; version exposed in response DTOs and required in update request DTOs; clients can detect and handle conflicts
2. **Server-side only `@Version`** — Version not exposed to clients; only catches intra-transaction races; does NOT prevent the primary lost-update scenario between client GET and PUT
3. **Pessimistic locking (`SELECT ... FOR UPDATE`)** — Blocks concurrent reads; poor scalability; overkill for this use case
4. **Do nothing** — Silent data loss continues

**Decision:** Option 1 — `@Version` with client-visible version field.

**Entities in scope:**

| Entity | Reason |
|--------|--------|
| `User` | Concurrent admin/self updates; soft-delete reversal risk |
| `Role` | Multiple admins can silently overwrite |
| `EntityType` | Consistency; also adding missing `updatedAt` |
| `EntityInstance` | Only non-admin-writable entity; highest contention |

**Entities excluded:** `UserRole` (insert/delete only), `UserRoleAuditLog` (append-only), `Permission` (never updated), `StoredToken` (one-way flip; revisit if token rotation added)

**Implementation:**
- Flyway `V7__add_version_columns.sql` — adds `version BIGINT NOT NULL DEFAULT 0` to 4 tables; adds `updated_at` to `entity_types`
- `@Version private Long version` on 4 entity classes
- `Long version` added to 4 response DTOs
- `@NotNull Long version` added to 4 update request DTOs
- Service `update*()` methods compare request version vs loaded version; throw `ConflictException` on mismatch
- `ObjectOptimisticLockingFailureException` handler in `GlobalExceptionHandler` → HTTP 409 (safety net)

**Breaking API change:** PUT endpoints require `version` in request body. GET responses include `version` (additive).

**No new libraries required.** `@Version` is standard JPA (`jakarta.persistence.Version`), already on the classpath.

**Consequences:**
- Positive: Eliminates silent lost updates; clients can detect and handle conflicts; defence-in-depth
- Negative: Breaking change for PUT endpoints; existing tests must be updated
- Risk: Low — trivial implementation cost; `@Version` is well-understood JPA behaviour

**Owner Approval:** [x] APPROVED — 2026-03-04

---

## 5. Approved Java Libraries

| # | Library | Group:Artifact | Version | Justification | Approved |
|---|---------|---------------|---------|---------------|---------|
| 1 | JJWT API | `io.jsonwebtoken:jjwt-api` | `0.12.6` | JWT signing/verification in auth.local (ADR-0007) | ✅ APPROVED 2026-03-03 |
| 2 | JJWT Impl | `io.jsonwebtoken:jjwt-impl` | `0.12.6` | JJWT runtime implementation (ADR-0007) | ✅ APPROVED 2026-03-03 |
| 3 | JJWT Jackson | `io.jsonwebtoken:jjwt-jackson` | `0.12.6` | JJWT JSON serialisation (ADR-0007) | ✅ APPROVED 2026-03-03 |
| 4 | Bucket4j Core | `com.bucket4j:bucket4j-core` | `8.10.1` | In-memory rate limiting (ADR-0008) | ✅ APPROVED 2026-03-03 |
| 5 | Spring Boot Starter Web | `org.springframework.boot:spring-boot-starter-web` | `3.4.3` | REST API framework (ADR-0003) | ✅ APPROVED 2026-03-03 |
| 6 | Spring Boot Starter Security | `org.springframework.boot:spring-boot-starter-security` | `3.4.3` | Security filter chain, JWT filter integration (ADR-0003) | ✅ APPROVED 2026-03-03 |
| 7 | Spring Boot Starter Data JPA | `org.springframework.boot:spring-boot-starter-data-jpa` | `3.4.3` | JPA + Hibernate ORM (ADR-0004) | ✅ APPROVED 2026-03-03 |
| 8 | H2 Database | `com.h2database:h2` | `2.3.232` | Local embedded SQL database (ADR-0004) | ✅ APPROVED 2026-03-03 |
| 9 | Flyway Core | `org.flywaydb:flyway-core` | `10.20.1` | Schema migration versioning (ADR-0004) | ✅ APPROVED 2026-03-03 |
| 10 | Spring Boot Starter Validation | `org.springframework.boot:spring-boot-starter-validation` | `3.4.3` | Bean Validation (JSR-380) for request DTOs | ✅ APPROVED 2026-03-03 |

---

*Maintained by: PE | Last updated: 2026-03-04*
