# Follow-Up Items

Identified 2026-03-04 via PE scalability/concurrency review and QE test coverage audit.

---

## PE Review: Scalability & Concurrency Issues

### Tier 1 — Must fix before any load test or production deployment

| # | Severity | Issue | Files | Fix |
|---|----------|-------|-------|-----|
| 5,6,7 | HIGH | TOCTOU race conditions in all create/update methods — concurrent duplicate requests produce 500 instead of 409 | `UserServiceImpl`, `RoleServiceImpl`, `EntityTypeServiceImpl` | Add `DataIntegrityViolationException` handler to `GlobalExceptionHandler` mapping to 409 |
| 9 | HIGH | Unbounded `ConcurrentHashMap` in rate limiter — memory leak under attack | `RateLimitInterceptor.java:31` | Replace with Caffeine cache (TTL + max size) |
| 12 | MEDIUM | No max page size enforcement — client can request `?size=1000000` | `PageRequest.java:13-15` | Cap size to 100 in constructor |
| ADR-0012 | HIGH | ~~Optimistic locking — lost-update race conditions on PUT endpoints~~ | `User`, `Role`, `EntityType`, `EntityInstance` | ✅ DONE 2026-03-04 — `@Version` added to all four entities; `version` required on all PUT bodies; stale → 409, missing → 400 |

### Tier 2 — Should fix before beta

| # | Severity | Issue | Files | Fix |
|---|----------|-------|-------|-----|
| 1,2,13 | HIGH | N+1 queries on login/refresh — loads full Role+Permissions when only role names needed | `UserServiceImpl.java:161-170`, `RoleResponse.java:17-19`, `LocalAuthProvider.java:60-62` | Add single join query returning role names only |
| 8 | HIGH | No refresh token rotation — same token reusable for 7 days; replay attacks possible | `LocalAuthProvider.java:76-98` | Rotate refresh token on each use; add reuse detection |
| 14 | MEDIUM | Missing index on `user_roles.role_id` — `countByRoleId()` requires full table scan | `V5` migration | New migration: `CREATE INDEX idx_user_roles_role_id ON user_roles(role_id)` |
| 18 | MEDIUM | Expired stored tokens never cleaned up — `stored_tokens` table grows monotonically | `StoredTokenRepository` | Add `@Scheduled` cleanup job deleting expired rows |
| 19 | MEDIUM | H2 console exposed without profile guard | `SecurityConfig.java:56-57`, `application.properties:16-17` | Move to `application-local.properties`; profile-gate security rule |

### Tier 3 — Must address before multi-instance deployment

| # | Severity | Issue | Files | Fix |
|---|----------|-------|-------|-----|
| 10 | CRITICAL | Rate limits per-instance only — defeated by horizontal scaling | `RateLimitInterceptor.java:31` | Swap Bucket4j to Redis-backed `ProxyManager` |
| 11 | CRITICAL | Access tokens not checked against revocation — logout/delete ineffective for up to 15 min; no cross-instance invalidation | `LocalAuthProvider.java:122-143` | Redis token blocklist, or reduce TTL to 2-5 min and document the window |

### Tier 4 — Address when convenient

| # | Severity | Issue | Files | Fix |
|---|----------|-------|-------|-----|
| 3 | MEDIUM | `listRoles()` unpaginated | `RoleServiceImpl.java:49-53` | Add pagination support |
| 4 | MEDIUM | `listEntityTypes()` unpaginated | `EntityTypeServiceImpl.java:44-49` | Add pagination support |
| 16 | MEDIUM | Stale role data in JWT for up to 15 min after role change | `LocalAuthProvider.java:154-163` | Reduce TTL or revoke tokens on role change |
| 17 | MEDIUM | `deleteRole()` cascade N+1 deletes for permissions | `RoleServiceImpl.java:76-86` | Add bulk `DELETE FROM Permission WHERE role_id = ?` |
| 15 | LOW | Missing index on `permissions.entity_type_id` | `V2` migration | New migration with index |
| 20 | LOW | `ServletErrorResponseWriter` JSON string interpolation — fragile if future callers pass user input | `ServletErrorResponseWriter.java:26-28` | Use Jackson `ObjectMapper` instead of `String.formatted()` |
| 21 | LOW | `@Lazy` masking circular dependency (`LocalAuthProvider` <-> `UserServiceImpl`) | `UserServiceImpl.java:34` | Extract `TokenRevocationPort` impl into separate `@Component` |
| 22 | LOW | `UserServiceImpl` directly depends on `RoleRepository` — cross-package violation (ADR-0005) | `UserServiceImpl.java:6,25` | Route through `RoleService` interface instead |

---

## QE Audit: Test Coverage Gaps

### P1 Gaps

| ID | Gap | Use Case | Action |
|----|-----|----------|--------|
| AUTH-014 | Logout then reuse access token — not tested end-to-end | UC-AUTH-003 | Add API test: logout, then hit protected endpoint with same token, assert 401 |
| USER-021 | Login with soft-deleted user credentials — not tested | UC-USER-005 | Add API test: create user, delete, attempt login, assert rejection |
| ROLE-018 | Add permission with non-existent entityTypeId — not tested | UC-ROLE-006 | Add `addPermission_withNonExistentEntityTypeId_returns404()` to `RoleApiTest` |
| ROLE-024 | Get permissions for non-existent role — not tested | UC-ROLE-008 | Add `getPermissionsForRole_withNonExistentRoleId_returns404()` to `RoleApiTest` |

### P2 Gaps

| ID | Gap | Use Case | Action |
|----|-----|----------|--------|
| USER-016 | List users filtered by name — not tested | UC-USER-003 | Add `listUsers_withNameFilter_returnsMatchingUsers()` to `UserApiTest` |
| ENT-010 | Update entity type to duplicate name — not tested | UC-ENT-004 | Add `updateEntityType_withDuplicateName_returns409()` to `EntityTypeApiTest` |
| INST-008 | Mismatched typeId/instanceId path — only tested at service level | UC-ENT-007 | Add API-level test to `EntityInstanceApiTest` |

### Behavioral Discrepancies (Owner Decision Required)

| ID | Issue | Decision Needed |
|----|-------|-----------------|
| SEC-009 | Soft-deleted user's token produces 404 (service layer), not 401 (test plan expectation). JwtFilter doesn't check revocation on every request. | Accept 404 and update plan, OR change JwtFilter to consult revocation store |
| AUTH-003 | `login_withUnknownUsername` returns 404 (reveals user existence). Test plan says 401. | Accept 404, OR change to 401 for security (don't reveal user existence) |

### Missing Test Classes (from QE_TEST_PLAN.md)

| Planned Class | Status |
|---|---|
| `FlywayMigrationIT` | Absent — no dedicated migration smoke test |
| `JwtSecurityIT` | Absent — scenarios folded into `AuthApiTest` + `SelfAccessTest` |

---

## Status

- [ ] Owner to review and prioritize
- [ ] Owner to decide on SEC-009 and AUTH-003 behavioral discrepancies
- [ ] SE to implement approved Tier 1 fixes
- [ ] QE to implement approved P1 test gaps
