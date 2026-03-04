# QE Test Briefing — UserRole Web Service

**From:** PE
**To:** QE (Quality Engineer)
**Date:** 2026-03-03
**Status:** READY FOR QE TEST PLAN

> **IMPORTANT — Governance rule:**
> You must produce a written Test Plan and submit it for Owner approval
> before writing a single test. Do NOT begin testing until the Owner approves the plan.

---

## 1. Scope of QE Testing

SE owns unit tests (pure logic, no Spring context, mocked dependencies).
QE owns everything above unit level:

| Level | Description | Tools |
|-------|-------------|-------|
| Integration tests | Spring context loaded, real H2 DB, real Flyway migrations | Spring Boot Test + `@SpringBootTest` |
| API / contract tests | HTTP-level tests against all endpoints | MockMvc or `@SpringBootTest` with `TestRestTemplate` |
| Security tests | Auth, RBAC enforcement, rate limiting behaviour | MockMvc with Spring Security test support |
| Edge case / negative tests | All documented error paths, constraint violations | As above |

---

## 2. Reference Documents

Read these in full before writing your plan:

- `docs/USE_CASES.md` — full use case register (UC-AUTH, UC-USER, UC-ROLE, UC-ENT, UC-AUTHZ, UC-RATE)
- `docs/DESIGN.md §3` — all module interface specs, endpoint definitions, data models
- `docs/SE_IMPLEMENTATION_PLAN.md §7` — SE's unit test coverage (do not duplicate; complement it)

---

## 3. Test Coverage Requirements

### 3.1 Authentication (UC-AUTH)

| Test Area | What to verify |
|-----------|---------------|
| UC-AUTH-001 Login | Valid credentials → 200 + access token + refresh token. Invalid password → 401. Unknown username → 401. Missing fields → 400. |
| UC-AUTH-002 Refresh | Valid refresh token → 200 + new access token. Expired refresh token → 401. Revoked refresh token → 401. Malformed token → 400. |
| UC-AUTH-003 Logout | Valid token → 204 + subsequent use of that token → 401. |
| JWT validation | Tampered token → 401. Expired access token → 401. Request with no token to protected endpoint → 401. |

---

### 3.2 User Management (UC-USER)

| Test Area | What to verify |
|-----------|---------------|
| UC-USER-001 Create | Admin creates user → 201 + correct response (no passwordHash in response). Duplicate username → 409. Duplicate email → 409. Missing required fields → 400. Non-admin attempts create → 403. |
| UC-USER-002 Get by ID | Admin gets any user → 200. User gets own profile → 200. User gets another user's profile → 403. Non-existent user → 404. |
| UC-USER-003 List | Admin lists users → 200 + pagination meta. Filter by department. Filter by name. Non-admin → 403. |
| UC-USER-004 Update | Admin updates any user → 200. User updates own profile → 200. User updates another's profile → 403. Duplicate email → 409. |
| UC-USER-005 Delete | Admin soft-deletes user → 204. Deleted user's token no longer works → 401. Non-admin → 403. Already deleted user → 404. |
| UC-USER-006/007 Role assign/revoke | Assign role → 204 + audit log entry written. Revoke role → 204 + audit log entry written. Assign non-existent role → 404. Assign already-assigned role → 409. Revoke non-assigned role → 404. Non-admin → 403. |
| UC-USER-008 Get roles | Admin gets roles for any user → 200. User gets own roles → 200. User gets another's roles → 403. |
| UC-USER-009 Role history | Admin gets audit log → 200 + paginated. Self gets own audit log → 200. Other user's audit log → 403. Log contains correct ASSIGNED/REVOKED entries with roleName snapshot. |

---

### 3.3 Role Management (UC-ROLE)

| Test Area | What to verify |
|-----------|---------------|
| UC-ROLE-001 Create | Admin creates role → 201. Duplicate name → 409. Non-admin → 403. |
| UC-ROLE-002 Get | Admin gets role with permissions → 200. Non-existent → 404. |
| UC-ROLE-003 List | Admin lists roles → 200. Non-admin → 403. |
| UC-ROLE-004 Update | Admin updates role → 200. Duplicate name → 409. Non-existent → 404. |
| UC-ROLE-005 Delete | Admin deletes unassigned role → 204. Delete role with assigned users → 409. Non-admin → 403. |
| UC-ROLE-006 Add permission | Admin adds permission → 201. Duplicate (role+action+entityTypeId) → 409. Non-existent entity type → 404. Non-admin → 403. |
| UC-ROLE-007 Remove permission | Admin removes permission → 204. Non-existent permission → 404. Non-admin → 403. |
| UC-ROLE-008 Get permissions | Admin gets permissions for role → 200. Non-existent role → 404. |

---

### 3.4 Entity Management (UC-ENT)

| Test Area | What to verify |
|-----------|---------------|
| UC-ENT-001 Create type | Admin creates entity type → 201. Duplicate name → 409. Non-admin → 403. |
| UC-ENT-002/003 List/Get type | Authenticated user sees types → 200. Non-existent → 404. Unauthenticated → 401. |
| UC-ENT-004 Update type | Admin updates → 200. Duplicate name → 409. Non-admin → 403. |
| UC-ENT-005 Delete type | Admin deletes type with no instances → 204. Delete type with instances → 409. Non-admin → 403. |
| UC-ENT-006 Create instance | User with CREATE permission → 201 + createdBy set correctly. User without CREATE permission → 403. Unknown entity type → 404. |
| UC-ENT-007 Get instance | User with READ permission → 200. Without READ → 403. Wrong type in path → 404. |
| UC-ENT-008 List instances | User with READ permission → 200 + paginated. Without READ → 403. |
| UC-ENT-009 Update instance | User with UPDATE permission → 200. Without UPDATE → 403. |
| UC-ENT-010 Delete instance | User with DELETE permission → 204. Without DELETE → 403. |

---

### 3.5 RBAC Integration (UC-AUTHZ-001)

End-to-end permission scenarios — these span user, role, and entity modules:

| Scenario | Expected outcome |
|----------|-----------------|
| Create user → assign role → role has CREATE on EntityTypeX → user creates instance of EntityTypeX | 201 |
| Same user attempts CREATE on EntityTypeY (no permission) | 403 |
| Same user attempts READ on EntityTypeX (no READ permission) | 403 |
| Admin adds READ permission to role → user can now GET instances | 200 |
| Admin revokes role from user → user can no longer CREATE instances | 403 |
| Role is deleted → user can no longer perform actions granted by that role | 403 |

---

### 3.6 Rate Limiting (UC-RATE-001)

| Scenario | Expected outcome |
|----------|-----------------|
| Anonymous caller exceeds 30 req/min on any endpoint | HTTP 429 + `Retry-After` header present |
| Authenticated user exceeds 120 req/min | HTTP 429 + `Retry-After` header present |
| Two different IPs have independent rate buckets | Each can send up to 30 req/min |
| Two different authenticated users have independent buckets | Each can send up to 120 req/min |

---

### 3.7 API Response Convention (ADR-0009)

Verify across all endpoints:
- Success responses: envelope `{ "status": "success", "data": ... }`
- List responses: envelope includes `"meta": { "page", "size", "total" }`
- Error responses: envelope `{ "status": "error", "code": "...", "message": "...", "timestamp": "..." }`
- No `passwordHash` field ever appears in any response
- All timestamps are ISO-8601 UTC
- HTTP status codes match the contract (200/201/204/400/401/403/404/409/429/500)

---

### 3.8 Database Portability Smoke Test

- Verify that all Flyway migrations (V1–V6) execute cleanly against H2 on application startup
- Verify that no native SQL functions are used in any JPQL query (this can be a code review check rather than a runtime test)

---

## 4. Test Data Strategy

- Use a fresh H2 in-memory database per test class (`@Transactional` + rollback or `@DirtiesContext`)
- No shared state between test classes
- Seed test data programmatically inside each test (no shared SQL fixtures except Flyway migrations)
- Use a helper builder/factory class to construct common test entities (User, Role, EntityType, etc.) cleanly

---

## 5. Tools

All tools are already on the classpath via approved dependencies — no additional libraries needed:

| Tool | Source |
|------|--------|
| JUnit 5 | `spring-boot-starter-test` |
| Mockito | `spring-boot-starter-test` |
| MockMvc | `spring-boot-starter-test` |
| Spring Security Test | `spring-boot-starter-test` |
| H2 (test DB) | `com.h2database:h2` (already approved) |

> Any additional testing library must be proposed to PE and approved by Owner before use.

---

## 6. Your Deliverable

Produce a **Test Plan** covering:
1. Test class structure and naming conventions
2. How you will handle Spring context loading (one context vs per-module)
3. How you will seed test data and manage DB state between tests
4. Which test scenarios you will prioritise (risk-based ordering)
5. How you will test rate limiting without real time delays
6. Coverage targets for integration and API tests

Submit the plan for **Owner approval before writing any test code.**

---

*Issued by: PE | Date: 2026-03-03*
