# SE Implementation Plan — UserRole Web Service

**Author:** SE (Senior Engineer)
**Date:** 2026-03-03
**Prepared For:** Owner Approval
**Reference Documents:** SE_BRIEFING.md, DESIGN.md, USE_CASES.md

---

## STATUS: PENDING OWNER APPROVAL

No production code will be written until this plan receives explicit Owner approval.

---

## Table of Contents

1. Project Directory Structure
2. Build Order and Rationale
3. Flyway Migration Structure
4. Spring Security Wiring
5. Implementation Decisions Not Covered by ADRs
6. DTO Strategy
7. Unit Test Strategy

---

## 1. Project Directory Structure

The full Maven project layout, following the mandatory package structure from SE_BRIEFING.md §4.

```
UserRole/
├── pom.xml
├── src/
│   ├── main/
│   │   ├── java/
│   │   │   └── com/
│   │   │       └── userrole/
│   │   │           ├── UserRoleApplication.java
│   │   │           │
│   │   │           ├── auth/
│   │   │           │   ├── spi/
│   │   │           │   │   ├── AuthProvider.java               (interface)
│   │   │           │   │   └── TokenClaims.java                (record)
│   │   │           │   ├── local/
│   │   │           │   │   ├── LocalAuthProvider.java          (implements AuthProvider)
│   │   │           │   │   ├── JwtFilter.java                  (OncePerRequestFilter)
│   │   │           │   │   ├── StoredToken.java                (@Entity)
│   │   │           │   │   └── StoredTokenRepository.java      (JpaRepository)
│   │   │           │   └── external/
│   │   │           │       └── .gitkeep                        (empty placeholder — do not delete)
│   │   │           │
│   │   │           ├── user/
│   │   │           │   ├── User.java                           (@Entity)
│   │   │           │   ├── UserRole.java                       (@Entity, composite PK)
│   │   │           │   ├── UserRoleId.java                     (@Embeddable composite key)
│   │   │           │   ├── UserRoleAuditLog.java               (@Entity)
│   │   │           │   ├── AuditAction.java                    (enum: ASSIGNED | REVOKED)
│   │   │           │   ├── UserRepository.java                 (JpaRepository)
│   │   │           │   ├── UserRoleRepository.java             (JpaRepository)
│   │   │           │   ├── UserRoleAuditLogRepository.java     (JpaRepository)
│   │   │           │   ├── UserService.java                    (interface)
│   │   │           │   ├── UserServiceImpl.java                (implements UserService)
│   │   │           │   ├── UserController.java                 (@RestController)
│   │   │           │   ├── dto/
│   │   │           │   │   ├── CreateUserRequest.java
│   │   │           │   │   ├── UpdateUserRequest.java
│   │   │           │   │   ├── UserResponse.java
│   │   │           │   │   ├── UserFilter.java
│   │   │           │   │   └── UserRoleAuditEntry.java
│   │   │           │   └── SecurityConfig.java                 (see §4 — wires JwtFilter)
│   │   │           │
│   │   │           ├── role/
│   │   │           │   ├── Role.java                           (@Entity)
│   │   │           │   ├── Permission.java                     (@Entity)
│   │   │           │   ├── Action.java                         (enum: CREATE | READ | UPDATE | DELETE)
│   │   │           │   ├── RoleRepository.java                 (JpaRepository)
│   │   │           │   ├── PermissionRepository.java           (JpaRepository)
│   │   │           │   ├── RoleService.java                    (interface)
│   │   │           │   ├── RoleServiceImpl.java                (implements RoleService)
│   │   │           │   ├── PermissionChecker.java              (interface)
│   │   │           │   ├── PermissionCheckerImpl.java          (implements PermissionChecker)
│   │   │           │   ├── RoleController.java                 (@RestController)
│   │   │           │   └── dto/
│   │   │           │       ├── CreateRoleRequest.java
│   │   │           │       ├── UpdateRoleRequest.java
│   │   │           │       ├── RoleResponse.java
│   │   │           │       ├── AddPermissionRequest.java
│   │   │           │       └── PermissionResponse.java
│   │   │           │
│   │   │           ├── entity/
│   │   │           │   ├── EntityType.java                     (@Entity)
│   │   │           │   ├── EntityInstance.java                 (@Entity)
│   │   │           │   ├── EntityTypeRepository.java           (JpaRepository)
│   │   │           │   ├── EntityInstanceRepository.java       (JpaRepository)
│   │   │           │   ├── EntityTypeService.java              (interface)
│   │   │           │   ├── EntityTypeServiceImpl.java          (implements EntityTypeService)
│   │   │           │   ├── EntityInstanceService.java          (interface)
│   │   │           │   ├── EntityInstanceServiceImpl.java      (implements EntityInstanceService)
│   │   │           │   ├── EntityTypeController.java           (@RestController)
│   │   │           │   ├── EntityInstanceController.java       (@RestController)
│   │   │           │   └── dto/
│   │   │           │       ├── CreateEntityTypeRequest.java
│   │   │           │       ├── UpdateEntityTypeRequest.java
│   │   │           │       ├── EntityTypeResponse.java
│   │   │           │       ├── CreateInstanceRequest.java
│   │   │           │       ├── UpdateInstanceRequest.java
│   │   │           │       └── EntityInstanceResponse.java
│   │   │           │
│   │   │           ├── ratelimit/
│   │   │           │   ├── RateLimitInterceptor.java           (HandlerInterceptor)
│   │   │           │   └── RateLimitConfig.java                (@Configuration — registers interceptor)
│   │   │           │
│   │   │           └── common/
│   │   │               ├── ApiResponse.java                    (generic envelope)
│   │   │               ├── PageMeta.java
│   │   │               ├── PageRequest.java
│   │   │               ├── PageResponse.java                   (data + meta combined)
│   │   │               ├── UserRoleException.java              (base unchecked)
│   │   │               ├── ResourceNotFoundException.java
│   │   │               ├── ConflictException.java
│   │   │               ├── ForbiddenException.java
│   │   │               ├── ValidationException.java
│   │   │               ├── RateLimitException.java
│   │   │               └── GlobalExceptionHandler.java         (@RestControllerAdvice)
│   │   │
│   │   └── resources/
│   │       ├── application.properties
│   │       └── db/
│   │           └── migration/
│   │               ├── V1__create_users.sql
│   │               ├── V2__create_roles_and_permissions.sql
│   │               ├── V3__create_entity_types_and_instances.sql
│   │               ├── V4__create_stored_tokens.sql
│   │               └── V5__create_user_role_audit_log.sql
│   │
│   └── test/
│       └── java/
│           └── com/
│               └── userrole/
│                   ├── auth/
│                   │   └── local/
│                   │       ├── LocalAuthProviderTest.java
│                   │       └── JwtFilterTest.java
│                   ├── user/
│                   │   └── UserServiceImplTest.java
│                   ├── role/
│                   │   ├── RoleServiceImplTest.java
│                   │   └── PermissionCheckerImplTest.java
│                   ├── entity/
│                   │   ├── EntityTypeServiceImplTest.java
│                   │   └── EntityInstanceServiceImplTest.java
│                   ├── ratelimit/
│                   │   └── RateLimitInterceptorTest.java
│                   └── common/
│                       └── GlobalExceptionHandlerTest.java
```

**Note on `SecurityConfig.java` placement:** The security configuration must wire `JwtFilter` (which lives in `auth.local`) into the Spring Security filter chain. To avoid violating the cross-package rule (no package outside `auth` may directly reference `auth.local` implementation classes), the `SecurityConfig` is placed in the `auth.local` package — not in `user` as shown structurally above. This is a correction to the tree above; the corrected placement is:

```
com/userrole/auth/local/SecurityConfig.java
```

This keeps all references to `LocalAuthProvider`, `JwtFilter`, and other `auth.local` internals entirely within the `auth.local` package boundary.

---

## 2. Build Order and Rationale

The build order strictly follows the dependency graph in DESIGN.md §3.7. Each wave may only begin after all modules in the prior wave are complete and their unit tests pass.

```
common       ←── all modules depend on common
auth.spi     ←── auth.local, entity, ratelimit
auth.local   ──→ auth.spi, common, user
user         ──→ common, auth.spi
role         ──→ common
entity       ──→ common, auth.spi, role (PermissionChecker)
ratelimit    ──→ common, auth.spi
```

### Wave 0 — Project Skeleton

Set up the Maven project structure, `pom.xml` with all approved dependencies, `application.properties`, and the Flyway migration directory. No business logic. Confirm the Spring Boot application starts successfully with an empty database.

**Why first:** Everything downstream needs a compilable project to build against.

---

### Wave 1 — `common`

Implement all shared types: `ApiResponse`, `PageMeta`, `PageRequest`, `PageResponse`, the full exception hierarchy, and `GlobalExceptionHandler`.

**Why before everything else:** Every other module depends on `common`. Exceptions, response envelopes, and pagination must be settled before any service interface can be meaningfully written. `GlobalExceptionHandler` can be unit-tested in isolation with no other modules present.

---

### Wave 2 — `auth.spi`

Implement `TokenClaims` (record) and `AuthProvider` (interface). No logic — these are pure contracts.

**Why second:** `auth.local`, `entity`, and `ratelimit` all depend on these types. They are zero-logic and zero-risk but are a compile-time dependency for three subsequent modules.

---

### Wave 3 — `role` (entities, repositories, service, controller)

Implement `Role`, `Permission`, `Action` enum, `RoleRepository`, `PermissionRepository`, `RoleService`/`RoleServiceImpl`, `PermissionChecker`/`PermissionCheckerImpl`, and `RoleController`.

Run Flyway migration V2 against the embedded H2 instance to confirm the schema is correct.

**Why before `user` and `entity`:** The `PermissionChecker` interface is consumed by `entity`. The `Role` entity and `RoleResponse` DTO are returned by `UserService.getRolesForUser()`. Building `role` early means both downstream modules can depend on it. There is no dependency from `role` back to `user` or `entity`.

---

### Wave 4 — `user` (entities, repositories, service, controller)

Implement `User`, `UserRole`, `UserRoleId`, `UserRoleAuditLog`, `AuditAction` enum, all three repositories, `UserService`/`UserServiceImpl`, and `UserController`.

Run Flyway migrations V1 and V5 against H2.

**Why after `role`:** `UserServiceImpl` calls `RoleService` (to look up role names for audit snapshots) and returns `RoleResponse` objects from `getRolesForUser()`. The `UserRole` join entity references `Role` via foreign key in the database. Both of these dependencies require `role` to exist first.

**Note on `auth.local` dependency at login:** `auth.local` calls `UserService.getRoleIdsForUser()` at login time to embed role names in the JWT. This is a dependency of `auth.local` on `user`, not the other way around. `user` has no dependency on `auth.local`. This ordering is safe.

---

### Wave 5 — `auth.local` (LocalAuthProvider, JwtFilter, StoredToken, SecurityConfig)

Implement `StoredToken`, `StoredTokenRepository`, `LocalAuthProvider`, `JwtFilter`, and `SecurityConfig`. Run Flyway migration V4.

**Why after `user`:** `LocalAuthProvider.authenticate()` calls `UserService.getRoleIdsForUser()` to embed the user's roles in the JWT claims at login time. It also calls `UserRepository` (via `UserService`) to validate credentials. This is the final auth wire-up, and it requires the `user` module to be fully in place.

---

### Wave 6 — `entity` (EntityType, EntityInstance, services, controllers)

Implement `EntityType`, `EntityInstance`, all four repositories, `EntityTypeService`/`EntityTypeServiceImpl`, `EntityInstanceService`/`EntityInstanceServiceImpl`, `EntityTypeController`, and `EntityInstanceController`. Run Flyway migrations V3.

**Why last among domain modules:** `EntityInstanceService` depends on both `auth.spi` (TokenClaims) and `role` (PermissionChecker). Both are now fully available. This module has the most complex authorisation logic (per-action permission checking) and should be implemented after all its dependencies are stable and tested.

---

### Wave 7 — `ratelimit`

Implement `RateLimitInterceptor` and `RateLimitConfig`.

**Why last:** Rate limiting is a cross-cutting concern that wraps the fully assembled application. It depends on `auth.spi` (to extract `userId` from a token for authenticated rate keys) and `common` (for `RateLimitException`). Implementing it last means the full application can be run and exercised end-to-end before adding this layer.

---

### Wave Summary Table

| Wave | Module(s) | Key Deliverables | Flyway Scripts Run |
|------|-----------|------------------|--------------------|
| 0 | Project skeleton | pom.xml, application.properties, directory tree | — |
| 1 | `common` | ApiResponse, exceptions, GlobalExceptionHandler | — |
| 2 | `auth.spi` | AuthProvider interface, TokenClaims record | — |
| 3 | `role` | Role, Permission, RoleService, PermissionChecker, RoleController | V2 |
| 4 | `user` | User, UserRole, UserRoleAuditLog, UserService, UserController | V1, V5 |
| 5 | `auth.local` | LocalAuthProvider, JwtFilter, StoredToken, SecurityConfig | V4 |
| 6 | `entity` | EntityType, EntityInstance, both services and controllers | V3 |
| 7 | `ratelimit` | RateLimitInterceptor, RateLimitConfig | — |

---

## 3. Flyway Migration Structure

All migration files reside in `src/main/resources/db/migration/`. Scripts are written in standard SQL only — no H2-specific syntax (ADR-0004). All column names, table names, and constraint names are lowercase with underscores.

### Ordering rationale

The migration order must respect foreign key dependencies. Tables referenced by foreign keys must exist before the tables that reference them.

| File | Creates | Depends On | Notes |
|------|---------|------------|-------|
| `V1__create_users.sql` | `users` | — | Foundation table. No FK dependencies. |
| `V2__create_roles_and_permissions.sql` | `roles`, `permissions` | — | `permissions.entity_type_id` is a forward reference to `entity_types`; it will be added as FK in V3 or declared as a non-enforced FK for portability. Decision: see §5.1. |
| `V3__create_entity_types_and_instances.sql` | `entity_types`, `entity_instances` | `users` (for `created_by`) | After `users` so FK on `created_by` is valid. |
| `V4__create_stored_tokens.sql` | `stored_tokens` | `users` | FK on `user_id` → `users`. |
| `V5__create_user_role_audit_log.sql` | `user_roles`, `user_role_audit_log` | `users`, `roles` | `user_roles` is the join table; audit log FK references both `users` and `roles`. |

### Detailed content specification per migration

**V1__create_users.sql**
- Table `users`: `id` (BIGINT PK auto-generated), `username` (VARCHAR NOT NULL UNIQUE), `password_hash` (VARCHAR NOT NULL), `full_name` (VARCHAR NOT NULL), `department` (VARCHAR), `email` (VARCHAR NOT NULL UNIQUE), `phone` (VARCHAR), `active` (BOOLEAN NOT NULL DEFAULT TRUE), `created_at` (TIMESTAMP WITH TIME ZONE NOT NULL), `updated_at` (TIMESTAMP WITH TIME ZONE NOT NULL)

**V2__create_roles_and_permissions.sql**
- Table `roles`: `id` (BIGINT PK auto-generated), `name` (VARCHAR NOT NULL UNIQUE), `description` (VARCHAR), `created_at` (TIMESTAMP WITH TIME ZONE NOT NULL), `updated_at` (TIMESTAMP WITH TIME ZONE NOT NULL)
- Table `permissions`: `id` (BIGINT PK auto-generated), `role_id` (BIGINT NOT NULL FK → `roles.id`), `action` (VARCHAR NOT NULL — stores enum name), `entity_type_id` (BIGINT NOT NULL — FK to `entity_types` added in V3 via ALTER TABLE), UNIQUE constraint on `(role_id, action, entity_type_id)`

**V3__create_entity_types_and_instances.sql**
- Table `entity_types`: `id` (BIGINT PK auto-generated), `name` (VARCHAR NOT NULL UNIQUE), `description` (VARCHAR), `created_at` (TIMESTAMP WITH TIME ZONE NOT NULL)
- Table `entity_instances`: `id` (BIGINT PK auto-generated), `entity_type_id` (BIGINT NOT NULL FK → `entity_types.id`), `name` (VARCHAR NOT NULL), `description` (VARCHAR), `created_by` (BIGINT NOT NULL FK → `users.id`), `created_at` (TIMESTAMP WITH TIME ZONE NOT NULL), `updated_at` (TIMESTAMP WITH TIME ZONE NOT NULL)
- ALTER TABLE `permissions` ADD CONSTRAINT `fk_permissions_entity_type` FOREIGN KEY (`entity_type_id`) REFERENCES `entity_types`(`id`)

**V4__create_stored_tokens.sql**
- Table `stored_tokens`: `id` (BIGINT PK auto-generated), `user_id` (BIGINT NOT NULL FK → `users.id`), `refresh_token` (VARCHAR NOT NULL), `expires_at` (TIMESTAMP WITH TIME ZONE NOT NULL), `revoked` (BOOLEAN NOT NULL DEFAULT FALSE)
- Index on `stored_tokens(user_id)` for efficient revocation on user soft-delete
- Index on `stored_tokens(refresh_token)` for fast refresh lookups

**V5__create_user_role_audit_log.sql**
- Table `user_roles`: `user_id` (BIGINT NOT NULL FK → `users.id`), `role_id` (BIGINT NOT NULL FK → `roles.id`), PRIMARY KEY `(user_id, role_id)`
- Table `user_role_audit_log`: `id` (BIGINT PK auto-generated), `user_id` (BIGINT NOT NULL FK → `users.id`), `role_id` (BIGINT NOT NULL FK → `roles.id`), `role_name` (VARCHAR NOT NULL — point-in-time snapshot), `action` (VARCHAR NOT NULL — ASSIGNED or REVOKED), `performed_by` (BIGINT NOT NULL FK → `users.id`), `performed_at` (TIMESTAMP WITH TIME ZONE NOT NULL)
- Index on `user_role_audit_log(user_id)` for efficient history queries

### Note on `permissions.entity_type_id` forward-reference

The FK from `permissions.entity_type_id` to `entity_types.id` cannot be declared in V2 because `entity_types` does not exist yet. The column is created without FK constraint in V2, and the constraint is added via `ALTER TABLE` in V3 after `entity_types` is created. This avoids creating `entity_types` prematurely in V2 and maintains a clean, single-responsibility migration file for each domain.

---

## 4. Spring Security Wiring

### 4.1 Overview

Spring Security 6.x (shipped with Spring Boot 3.4.3) uses a `SecurityFilterChain` bean rather than extending `WebSecurityConfigurerAdapter` (removed in Spring Security 6). The wiring is:

```
HTTP Request
    │
    ▼
RateLimitInterceptor  (HandlerInterceptor — applied before controllers)
    │
    ▼
Spring Security Filter Chain
    ├── JwtFilter  (OncePerRequestFilter — reads Bearer token, calls AuthProvider.validate())
    │       │
    │       └── On success: populates SecurityContextHolder with UsernamePasswordAuthenticationToken
    │
    └── SecurityContextHolder ──→ controllers / services
```

### 4.2 `SecurityConfig` (placed in `auth.local`)

A `@Configuration` + `@EnableWebSecurity` class in `com.userrole.auth.local` defines:

1. **`SecurityFilterChain` bean** — configured via `HttpSecurity`:
   - Disable CSRF (stateless JWT API — no session cookies)
   - Disable session creation (`SessionCreationPolicy.STATELESS`)
   - Permit all on: `POST /api/v1/auth/login`, `POST /api/v1/auth/refresh`
   - Require authentication on all other `GET /api/v1/**`, `POST /api/v1/**`, `PUT /api/v1/**`, `DELETE /api/v1/**` patterns
   - Add `JwtFilter` before `UsernamePasswordAuthenticationFilter` using `httpSecurity.addFilterBefore(...)`

2. **`BCryptPasswordEncoder` bean** — declared here; injected into `LocalAuthProvider` for password hashing and verification.

3. **`AuthProvider` bean** — `LocalAuthProvider` is annotated `@Component` and implements `AuthProvider`. It is injected into `JwtFilter` and into `AuthController` via the `AuthProvider` interface only — never via `LocalAuthProvider` directly.

### 4.3 `JwtFilter` behaviour

`JwtFilter` extends `OncePerRequestFilter`. Its logic per request:

1. Extract the `Authorization` header. If absent or not prefixed with `Bearer `, call `filterChain.doFilter(request, response)` and return (let the downstream security rules handle the missing credential).
2. Call `authProvider.validate(token)` which returns `TokenClaims` or throws an exception if the token is invalid/expired.
3. Construct a `UsernamePasswordAuthenticationToken` using `TokenClaims.userId()` as the principal and `TokenClaims.roles()` mapped to `GrantedAuthority` instances (prefixed `ROLE_` per Spring Security convention). Set credentials to null. Set `authenticated = true`.
4. Call `SecurityContextHolder.getContext().setAuthentication(authentication)`.
5. Continue the filter chain.

On `JwtException` or any validation failure, clear the context and return HTTP 401 directly using `HttpServletResponse.sendError()`.

### 4.4 Admin-only endpoint enforcement

Admin-only enforcement is **not** done via Spring Security's `.hasRole("ADMIN")` on the `HttpSecurity` matcher, because the Admin check is expressed in `TokenClaims.roles` (a list of role name strings), not in the `GrantedAuthority` list set by the filter.

Instead: the `GrantedAuthority` list set on the `Authentication` object by `JwtFilter` will include `ROLE_ADMIN` if `TokenClaims.roles()` contains `"ADMIN"`. Controllers for admin-only endpoints will check this via `@PreAuthorize("hasRole('ADMIN')")` using Spring Security's method-level security.

Enable method security with `@EnableMethodSecurity` on `SecurityConfig`.

Self-access checks (user viewing/editing own profile) are done inside the service layer: `UserServiceImpl` compares `TokenClaims.userId()` to the path `{id}` parameter and throws `ForbiddenException` if they do not match and the caller is not an Admin. This keeps the check close to the business rule and fully testable without the HTTP layer.

### 4.5 `AuthProvider` SPI integration point

`JwtFilter` depends on `AuthProvider` (the interface in `auth.spi`) via constructor injection. It does not know about `LocalAuthProvider`. When a future `ExternalAuthProvider` is implemented, only `SecurityConfig` changes (to swap the `@Primary` or `@Qualifier` annotation) — `JwtFilter` is untouched.

### 4.6 `AuthController`

A `@RestController` in `com.userrole.auth.local` (not a separate package, per the structure) handles the three auth endpoints:

- `POST /api/v1/auth/login` → delegates to `authProvider.authenticate()`
- `POST /api/v1/auth/refresh` → delegates to `authProvider.refresh()`
- `POST /api/v1/auth/logout` → delegates to `authProvider.invalidate()`; returns 204

The controller is in `auth.local` because it is the concrete implementation package. It does not belong in `auth.spi`.

---

## 5. Implementation Decisions Not Covered by ADRs

### 5.1 Forward FK constraint for `permissions.entity_type_id`

As described in §3, the FK from `permissions.entity_type_id` to `entity_types` is deferred to V3 via `ALTER TABLE`. This is the correct approach: it avoids out-of-order table creation, and both H2 and standard SQL support `ALTER TABLE ... ADD CONSTRAINT FOREIGN KEY`.

### 5.2 Admin role — seeded, not hardcoded

The design does not define a hardcoded `ADMIN` role. Decision: the `ADMIN` role is **seeded via Flyway** in a new migration file `V6__seed_admin_role.sql`. This migration inserts a row into `roles` with `name = 'ADMIN'` if it does not already exist (using a conditional insert or `INSERT ... WHERE NOT EXISTS` pattern in standard SQL). No hardcoded role ID is assumed anywhere in the application code — the role name string `"ADMIN"` is the only constant, defined as a named constant in a `RoleConstants` class in `common`.

Rationale: seeding via Flyway is repeatable, version-controlled, and testable. It does not require application-startup logic or `@EventListener` hooks. If the Admin role is accidentally deleted from the database, re-running from V6 (in a new environment) will restore it. The Admin user itself (the first administrator) is outside the scope of v1 — the service does not seed a default admin user.

**Updated migration list:**

| File | Purpose |
|------|---------|
| V1__create_users.sql | Create `users` table |
| V2__create_roles_and_permissions.sql | Create `roles` and `permissions` tables |
| V3__create_entity_types_and_instances.sql | Create `entity_types`, `entity_instances`; add FK on `permissions` |
| V4__create_stored_tokens.sql | Create `stored_tokens` table |
| V5__create_user_role_audit_log.sql | Create `user_roles` and `user_role_audit_log` tables |
| V6__seed_admin_role.sql | Seed the ADMIN role row into `roles` |

### 5.3 `PageResponse<T>` — how it is returned

`PageResponse<T>` is a class in `common` that holds a `List<T> data` and a `PageMeta meta`. Controllers wrap this inside `ApiResponse` when returning paginated results. The service layer returns `PageResponse<T>` directly; the controller wraps it into `ApiResponse<PageResponse<T>>`. This keeps services unaware of the HTTP response envelope while still giving the controller full control over the final JSON shape.

Spring Data's `Page<T>` is used internally in repository calls. The `PageResponse<T>` wrapper is populated from `Page<T>` in the service implementation. Spring Data's `Pageable` is constructed from the `PageRequest` DTO passed to service methods.

The service interface signatures use the `com.userrole.common.PageRequest` type (our own DTO), not `org.springframework.data.domain.Pageable`, to enforce the cross-package interface contract and keep services free of Spring Data coupling at the interface level.

### 5.4 Soft delete and token revocation in `deleteUser`

`UserServiceImpl.deleteUser()` performs two operations in a single `@Transactional` method:
1. Set `user.active = false` and save.
2. Call `StoredTokenRepository` (via a reference injected as a port) to revoke all tokens for that user.

The cross-package rule requires that `user` does not directly reference `auth.local` implementation classes. The solution: define a `TokenRevocationPort` interface in `auth.spi` (or `user`, since it is the consumer) with a single method `revokeAllTokensForUser(Long userId)`. `StoredTokenRepository`/`LocalAuthProvider` implements this port. `UserServiceImpl` depends on the port interface only.

Specifically: a `TokenRevocationPort` interface will be added to `auth.spi` (where it is accessible to both `user` and `auth.local`). `LocalAuthProvider` (or a dedicated `TokenRevocationService` in `auth.local`) implements it. `UserServiceImpl` receives it via constructor injection.

### 5.5 `UserRole` composite primary key

`UserRole` uses `@EmbeddedId` with a separate `UserRoleId` class annotated `@Embeddable`. This is the standard JPA pattern for composite PKs. `UserRoleId` holds `userId` (Long) and `roleId` (Long).

### 5.6 `Action` enum casing in the database

The `action` column in `permissions` and `action` column in `user_role_audit_log` are stored as `VARCHAR` containing the enum name (e.g., `"CREATE"`, `"READ"`). Hibernate's `@Enumerated(EnumType.STRING)` is used. This is both readable and safe across database engines.

### 5.7 Timestamp handling — `Instant` mapped to UTC

All `Instant` fields on JPA entities use the `@Column` annotation without a custom converter. Hibernate 6 (shipped with Spring Boot 3.4.3) natively maps `Instant` to `TIMESTAMP WITH TIME ZONE` when the dialect supports it, and to `TIMESTAMP` (stored in UTC) otherwise. No custom `AttributeConverter` is needed. The JVM timezone will be set to UTC at startup via `TimeZone.setDefault(TimeZone.getTimeZone("UTC"))` in `UserRoleApplication.main()` as a defensive measure.

### 5.8 `UserService.getRolesForUser` — return type

`getRolesForUser(userId)` returns `List<RoleResponse>`. This means `UserService` references the `RoleResponse` DTO from the `role` package. This is an intentional cross-package DTO reference — DTOs are not implementation classes, so referencing them across package boundaries is permitted under the cross-package rule (ADR-0005 blocks cross-boundary implementation class references, not DTO references). This is noted explicitly here to pre-empt review questions.

### 5.9 `deleteRole` — conflict check

`RoleServiceImpl.deleteRole(id)` queries `UserRoleRepository` (or a method on `UserService`) to check whether any `UserRole` row references this role before deleting. Since `role` must not directly call `user`'s repository (cross-package rule), the check is done via a JPQL count query on `UserRoleRepository` which is owned by the `user` package.

Resolution: `RoleService.deleteRole()` needs to verify no users are assigned. Rather than coupling `role` to `user`, a `UserRoleCountPort` interface will be placed in `common` (as it is a coordination concern, not domain-specific). `UserServiceImpl` implements it. `RoleServiceImpl` depends on it. Alternatively, the simplest approach that avoids an extra port is: let `UserRoleRepository` be queried directly by `RoleServiceImpl` via a dedicated query interface — but this violates the vertical-slice rule.

Decision: add a `RoleAssignmentQueryPort` interface to `common` with a single method `countUsersAssignedToRole(Long roleId): long`. The `user` module's `UserServiceImpl` (or a small adapter class in `user`) implements this port. `RoleServiceImpl` constructor-injects `RoleAssignmentQueryPort`. This keeps both modules isolated.

### 5.10 Rate limit key extraction before JWT validation

The `RateLimitInterceptor` runs as a `HandlerInterceptor`, which fires after the Spring Security filter chain. This means by the time `RateLimitInterceptor` runs, the `SecurityContextHolder` may already contain the validated `Authentication`. The interceptor extracts the `userId` from `SecurityContextHolder.getContext().getAuthentication()` if it is set and authenticated; otherwise it falls back to the client IP from `HttpServletRequest.getRemoteAddr()`.

SE_BRIEFING.md §9 states "rate limit interceptor applies before security filter where possible". Because `HandlerInterceptor` fires after filters in the Spring MVC lifecycle, it is not possible to place a `HandlerInterceptor` strictly before the security filter chain without converting it to a `Filter`. Decision: keep `RateLimitInterceptor` as a `HandlerInterceptor` for simplicity (it can still read the IP for unauthenticated requests and the user ID from the populated security context for authenticated ones). If the requirement to fire truly before the security chain becomes strict, this can be revisited by converting to a `javax.servlet.Filter` registered at a lower order than the security filter chain.

---

## 6. DTO Strategy

### 6.1 Principles

- Each module owns its own DTOs in a `dto/` sub-package.
- DTOs are plain Java records (Java 21) where the fields are all immutable (request and response types).
- Bean Validation annotations (`@NotBlank`, `@Email`, `@Size`, etc.) are placed on request DTOs only.
- Response DTOs do not contain JPA entity references — they are pure data carriers.
- No entity object is ever returned from a controller or service interface method. Mapping from entity to response DTO is done inside service implementations.

### 6.2 Request DTOs

| DTO | Validation rules |
|-----|-----------------|
| `CreateUserRequest` | `username` not blank, `passwordHash` not blank (raw password before hashing), `fullName` not blank, `email` not blank and valid format |
| `UpdateUserRequest` | All fields optional; `email` valid format if present |
| `CreateRoleRequest` | `name` not blank |
| `UpdateRoleRequest` | `name` not blank if provided |
| `AddPermissionRequest` | `action` not null (enum), `entityTypeId` not null |
| `CreateEntityTypeRequest` | `name` not blank |
| `UpdateEntityTypeRequest` | `name` not blank if provided |
| `CreateInstanceRequest` | `name` not blank |
| `UpdateInstanceRequest` | `name` not blank if provided |

### 6.3 Response DTOs

Response DTOs include only fields safe to expose. Notably:
- `UserResponse` — does not include `passwordHash` (enforced by never mapping that field)
- `UserRoleAuditEntry` — includes `userId`, `roleId`, `roleName` (snapshot), `action`, `performedBy`, `performedAt`

### 6.4 Mapping approach

Entity-to-DTO mapping is done manually in service implementations (no MapStruct or ModelMapper — no additional libraries approved). Static factory methods or constructors on response DTO records are the preferred pattern (e.g., `UserResponse.from(User user)`). This keeps mapping logic close to the DTO definition, is trivially unit-testable, and adds no library dependency.

---

## 7. Unit Test Strategy

### 7.1 Scope

SE owns unit tests only. QE owns integration tests (Spring context loaded, real DB) and end-to-end tests. Unit tests use JUnit 5 (included via `spring-boot-starter-test`) and Mockito (also included). No additional testing libraries are needed.

### 7.2 What is unit tested per module

All unit tests follow the same pattern: instantiate the class under test directly (no Spring context), inject mock dependencies via Mockito, assert behaviour.

---

#### `common`

| Class | What to test |
|-------|-------------|
| `GlobalExceptionHandler` | Each exception type maps to the correct HTTP status and error code in the envelope. Test with a mock `HttpServletRequest`. |
| Exception classes | Each subclass of `UserRoleException` carries the correct message and is unchecked. |
| `ApiResponse` | Static factory methods produce correct JSON-serialisable shape (fields present/absent as expected). |
| `PageResponse` | Holds data and meta correctly; total, page, size are reflected. |

---

#### `auth.local`

| Class | What to test |
|-------|-------------|
| `LocalAuthProvider.authenticate()` | Happy path: correct credentials → returns `TokenResponse` with non-null `accessToken` and `refreshToken`. Bad password → throws appropriate exception. Unknown username → throws appropriate exception. |
| `LocalAuthProvider.validate()` | Valid token → returns `TokenClaims` with correct fields. Expired token → throws exception. Tampered token → throws exception. |
| `LocalAuthProvider.refresh()` | Valid refresh token → returns new `TokenResponse`. Expired or revoked refresh token → throws exception. |
| `LocalAuthProvider.invalidate()` | Marks the `StoredToken` row as revoked. Subsequent `validate()` call on that token throws exception. |
| `JwtFilter.doFilterInternal()` | No `Authorization` header → filter passes through, no authentication set. Valid Bearer token → `SecurityContextHolder` populated. Invalid token → filter sends 401. `ROLE_ADMIN` included in authorities when `TokenClaims.roles` contains `"ADMIN"`. |

---

#### `user`

| Class | What to test |
|-------|-------------|
| `UserServiceImpl.createUser()` | Saves user with hashed password; returns `UserResponse` without `passwordHash`. Duplicate username → throws `ConflictException`. |
| `UserServiceImpl.getUserById()` | Happy path → returns correct `UserResponse`. Not found → throws `ResourceNotFoundException`. Self-access check: non-admin requesting another user's profile → `ForbiddenException`. |
| `UserServiceImpl.listUsers()` | Returns paginated results; filter by department and name applies correctly. |
| `UserServiceImpl.updateUser()` | Happy path updates mutable fields. Self-access: user updates own profile. Non-admin updating another user → `ForbiddenException`. |
| `UserServiceImpl.deleteUser()` | Sets `active = false`. Calls `TokenRevocationPort.revokeAllTokensForUser()`. |
| `UserServiceImpl.assignRole()` | Saves `UserRole` row. Writes `ASSIGNED` audit entry in same transaction. Already assigned → `ConflictException`. |
| `UserServiceImpl.revokeRole()` | Removes `UserRole` row. Writes `REVOKED` audit entry. Role not assigned → `ResourceNotFoundException`. |
| `UserServiceImpl.getRoleChangeHistory()` | Returns paginated `UserRoleAuditEntry` list for the given user. |

---

#### `role`

| Class | What to test |
|-------|-------------|
| `RoleServiceImpl.createRole()` | Happy path creates role. Duplicate name → `ConflictException`. |
| `RoleServiceImpl.deleteRole()` | No users assigned → deletes. Users assigned → `ConflictException` (via `RoleAssignmentQueryPort`). |
| `RoleServiceImpl.addPermission()` | Adds permission; duplicate (role+action+entityTypeId) → `ConflictException`. |
| `RoleServiceImpl.removePermission()` | Happy path; not found → `ResourceNotFoundException`. |
| `PermissionCheckerImpl.hasPermission()` | Returns true when a matching permission exists. Returns false when no matching permission. Tests cover each `Action` value. Test with multiple role names (at least one matching → true). |

---

#### `entity`

| Class | What to test |
|-------|-------------|
| `EntityTypeServiceImpl.createEntityType()` | Happy path. Duplicate name → `ConflictException`. |
| `EntityTypeServiceImpl.deleteEntityType()` | No instances → deletes. Instances exist → `ConflictException`. |
| `EntityInstanceServiceImpl.createInstance()` | Caller has CREATE permission → creates instance with `createdBy = TokenClaims.userId()`. Caller lacks CREATE permission → `ForbiddenException`. EntityType not found → `ResourceNotFoundException`. |
| `EntityInstanceServiceImpl.getInstanceById()` | Caller has READ permission → returns instance. Lacks permission → `ForbiddenException`. Instance not found → `ResourceNotFoundException`. Instance belongs to wrong type → `ResourceNotFoundException`. |
| `EntityInstanceServiceImpl.listInstances()` | Caller has READ permission → returns paginated list. Lacks permission → `ForbiddenException`. |
| `EntityInstanceServiceImpl.updateInstance()` | Caller has UPDATE permission → updates. Lacks permission → `ForbiddenException`. |
| `EntityInstanceServiceImpl.deleteInstance()` | Caller has DELETE permission → deletes. Lacks permission → `ForbiddenException`. |

---

#### `ratelimit`

| Class | What to test |
|-------|-------------|
| `RateLimitInterceptor.preHandle()` | Anonymous request (no authentication) within limit → passes through. Anonymous request over limit → throws `RateLimitException`. Authenticated request within limit → passes through (key is userId, not IP). Authenticated request over limit → throws `RateLimitException`. Confirm that two different IPs have independent buckets. Confirm that two different userIds have independent buckets. |

---

### 7.3 Coverage targets

- All public methods on all service implementations: 100% branch coverage for happy path and each documented exception path.
- `JwtFilter`: 100% branch coverage on the three major branches (no header, valid token, invalid token).
- `GlobalExceptionHandler`: one test per exception subtype.
- `PermissionCheckerImpl`: one test per Action value plus multi-role cases.

### 7.4 Test naming convention

Test method names follow: `methodName_condition_expectedOutcome`.
Example: `getUserById_whenUserNotFound_throwsResourceNotFoundException`.

### 7.5 Mocking strategy

All repository and port dependencies are mocked with Mockito `@Mock` / `when(...).thenReturn(...)`. No H2 database is used in unit tests. The `@InjectMocks` pattern is used for service implementations.

`TokenClaims` is constructed directly (it is a record) in tests — no mocking needed.

---

## 8. Open Questions (to be resolved before or during implementation)

| # | Question | Default assumption if not resolved before coding |
|---|----------|--------------------------------------------------|
| 1 | Should the system seed a default Admin user (not just the Admin role)? | No default user seeded in v1. The first user must be created via a direct database INSERT or a separate bootstrap script outside this service. |
| 2 | Should `RoleAssignmentQueryPort` and `TokenRevocationPort` be placed in `common` or in `auth.spi`? | `TokenRevocationPort` in `auth.spi`; `RoleAssignmentQueryPort` in `common`. This minimises `auth.spi` scope creep while giving `common` a coordination role. |
| 3 | Should the `AuthController` (login/refresh/logout) be in `auth.local` package or a new `auth.web` sub-package? | Place in `auth.local` for v1 — no reason to sub-package further until there are multiple auth controllers. |
| 4 | `listRoles()` returns `List<RoleResponse>` (not paginated). Confirm this is intentional — roles are expected to be a small, bounded set. | Confirmed by DESIGN.md §3.4 which specifies `listRoles(): List<RoleResponse>` without pagination. |

---

*Submitted by: SE | Date: 2026-03-03*
*Awaiting: Owner Approval*
