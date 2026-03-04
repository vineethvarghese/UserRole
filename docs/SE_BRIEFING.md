# SE Implementation Briefing — UserRole Web Service

**From:** PE
**To:** SE (Senior Engineer)
**Date:** 2026-03-03
**Status:** READY FOR SE IMPLEMENTATION PLAN

> **IMPORTANT — Governance rule:**
> You must produce a written Implementation Plan and submit it for Owner approval
> before writing a single line of production code. The plan must cover all modules
> listed below, the build order, and any implementation decisions not already decided
> by the ADRs. Do NOT begin coding until the Owner approves the plan.

---

## 1. What You Are Building

A Spring Boot 3.4.3 REST web service (Java 21) that manages Users, Roles, and Entities
with JWT authentication, RBAC enforcement, and in-memory rate limiting.

Full use case details: `docs/USE_CASES.md`
Full architecture decisions: `docs/DESIGN.md`

---

## 2. Project Setup

| Item | Value |
|------|-------|
| Language | Java 21 |
| Build tool | Maven |
| Framework | Spring Boot 3.4.3 |
| Base package | `com.userrole` |
| Local database | H2 (embedded, in-process) |
| Schema management | Flyway |
| Auth | JWT via JJWT 0.12.6, Port & Adapter pattern |
| Rate limiting | Bucket4j 8.10.1, in-memory |

---

## 3. Approved Libraries (exact versions — do not deviate without PE approval)

| Group:Artifact | Version |
|---------------|---------|
| `org.springframework.boot:spring-boot-starter-web` | 3.4.3 |
| `org.springframework.boot:spring-boot-starter-security` | 3.4.3 |
| `org.springframework.boot:spring-boot-starter-data-jpa` | 3.4.3 |
| `org.springframework.boot:spring-boot-starter-validation` | 3.4.3 |
| `com.h2database:h2` | 2.3.232 |
| `org.flywaydb:flyway-core` | 10.20.1 |
| `io.jsonwebtoken:jjwt-api` | 0.12.6 |
| `io.jsonwebtoken:jjwt-impl` | 0.12.6 (runtime scope) |
| `io.jsonwebtoken:jjwt-jackson` | 0.12.6 (runtime scope) |
| `com.bucket4j:bucket4j-core` | 8.10.1 |

> Any additional library — even a test utility — must be proposed to PE and approved by Owner before use.

---

## 4. Package Structure (mandatory — do not reorganise)

```
com.userrole
├── auth
│   ├── spi/          AuthProvider (interface), TokenClaims (record/class)
│   ├── local/        LocalAuthProvider, JwtFilter, StoredToken, StoredTokenRepository
│   └── external/     (empty placeholder — do not delete)
├── user/             User, UserRole, UserRoleAuditLog, UserRepository,
│                     UserService (interface + impl), UserController
├── role/             Role, Permission, Action (enum), AuditAction (enum),
│                     RoleRepository, PermissionRepository,
│                     RoleService (interface + impl), PermissionChecker (interface + impl),
│                     RoleController
├── entity/           EntityType, EntityInstance,
│                     EntityTypeRepository, EntityInstanceRepository,
│                     EntityTypeService (interface + impl),
│                     EntityInstanceService (interface + impl),
│                     EntityTypeController, EntityInstanceController
├── ratelimit/        RateLimitInterceptor, RateLimitConfig
└── common/           ApiResponse, PageMeta, UserRoleException and subclasses,
                      GlobalExceptionHandler
```

---

## 5. Cross-Package Rules (mandatory)

1. No package may directly reference internal implementation classes of another package.
   Only interfaces may be referenced across package boundaries.
2. `TokenClaims` must be passed explicitly into service methods — do not call
   `SecurityContextHolder` inside service classes. This keeps services unit-testable.
3. `LocalAuthProvider` must only be referenced via `AuthProvider` outside `auth.local`.
4. `PermissionChecker` (interface in `role`) is the only way the `entity` module
   checks permissions — no direct `RoleRepository` access from `entity`.
5. JPQL queries only — no native SQL — to preserve database portability (ADR-0004).

---

## 6. Module Interfaces (implement exactly as specified)

All service method signatures, entity fields, REST paths, HTTP methods, and
response shapes are defined in `docs/DESIGN.md §3`. Reproduce them faithfully.
Do not add endpoints, fields, or methods not listed there without raising an ADR.

### Key data model notes
- `User.passwordHash` — never include in any API response DTO
- `UserRoleAuditLog.roleName` — snapshot at time of assign/revoke; do not update it when role name changes
- `Permission` — unique constraint on `(roleId, action, entityTypeId)`
- `UserRole` — composite primary key `(userId, roleId)`
- All entities use `Long` as the primary key type
- All timestamps use `Instant` mapped to UTC

### Key behavioural notes
- `deleteUser` — soft delete (`active = false`); also revoke all `StoredToken` rows for that user
- `deleteRole` — throw `ConflictException` if any `UserRole` row references this role
- `deleteEntityType` — throw `ConflictException` if any `EntityInstance` rows reference this type
- `assignRole` / `revokeRole` — must write a `UserRoleAuditLog` row atomically in the same transaction
- Rate limit: anonymous = 30 req/min per IP; authenticated = 120 req/min per userId

---

## 7. Flyway Migration Naming Convention

```
src/main/resources/db/migration/
  V1__create_users.sql
  V2__create_roles_and_permissions.sql
  V3__create_entity_types_and_instances.sql
  V4__create_stored_tokens.sql
  V5__create_user_role_audit_log.sql
```

Scripts must be written in standard SQL (no H2-specific syntax) to preserve portability.

---

## 8. API Conventions (ADR-0009)

All endpoints return:
```json
{ "status": "success", "data": { ... } }
{ "status": "success", "data": [ ... ], "meta": { "page": 0, "size": 20, "total": 100 } }
{ "status": "error", "code": "RESOURCE_NOT_FOUND", "message": "...", "timestamp": "..." }
```

HTTP status codes: 200 (GET/PUT), 201 (POST create), 204 (DELETE/logout),
400 (validation), 401 (unauthenticated), 403 (forbidden), 404 (not found),
409 (conflict), 429 (rate limit), 500 (server error).

---

## 9. Security Configuration Notes

- All `/api/v1/auth/login` and `/api/v1/auth/refresh` endpoints are public (no JWT required)
- All other `/api/v1/**` endpoints require a valid Bearer JWT
- Admin-only endpoints must check for an `ADMIN` role in `TokenClaims.roles`
- Self-access (user viewing/editing own profile) must compare `TokenClaims.userId` to path `{id}`
- Rate limit interceptor applies before security filter where possible

---

## 10. Your Deliverable

Produce an **Implementation Plan** covering:
1. Build order (which modules you implement first and why)
2. Any implementation decisions not already decided by the ADRs
3. How you will structure Flyway migrations
4. How you will wire Spring Security to the `AuthProvider` SPI
5. Test strategy at unit level (note: QE owns integration/end-to-end tests)

Submit the plan for **Owner approval before writing any code**.

---

*Issued by: PE | Date: 2026-03-03*
