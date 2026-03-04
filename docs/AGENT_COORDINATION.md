# Agent Coordination Document — UserRole Web Service

**Author:** PE
**Last Updated:** 2026-03-04

---

## Governance Model

All decisions flow through the Principal Engineer (PE).
Owner approval is required at every defined gate before the next phase begins.
No agent writes code or tests without an approved plan.

---

## Agent Roles & Responsibilities

| Agent | Role | Does | Does NOT |
|-------|------|------|----------|
| **PE** | Principal Engineer | Architects, writes ADRs, defines modules, writes use cases, maintains this doc | Write any code |
| **SE** | Senior Engineer | Implements modules per approved design | Begin without Owner-approved implementation plan |
| **QE** | Quality Engineer | Writes and executes tests per approved test plan | Begin without Owner-approved test plan |
| **DE** | Documentation Engineer | Produces final project docs for future maintainers | Begin without Owner approval; document unstable modules |

---

## Approval Gates

```
[PE]  Use Case Document              → OWNER APPROVAL REQUIRED  ✅ APPROVED 2026-03-03
[PE]  ADR-0001 Build Tool            → OWNER APPROVAL REQUIRED  ✅ APPROVED 2026-03-03
[PE]  ADR-0002 Java Version          → OWNER APPROVAL REQUIRED  ✅ APPROVED 2026-03-03
[PE]  ADR-0003 Web Framework         → OWNER APPROVAL REQUIRED  ✅ APPROVED 2026-03-03
[PE]  ADR-0004 Database Strategy     → OWNER APPROVAL REQUIRED  ✅ APPROVED 2026-03-03
[PE]  ADR-0005 Package Structure     → OWNER APPROVAL REQUIRED  ✅ APPROVED 2026-03-03
[PE]  ADR-0006 Auth Provider Strategy→ OWNER APPROVAL REQUIRED  ✅ APPROVED 2026-03-03
[PE]  ADR-0007 JWT Library           → OWNER APPROVAL REQUIRED  ✅ APPROVED 2026-03-03
[PE]  ADR-0008 Rate Limiting         → OWNER APPROVAL REQUIRED  ✅ APPROVED 2026-03-03
[PE]  ADR-0009 API Response Conv.    → OWNER APPROVAL REQUIRED  ✅ APPROVED 2026-03-03
[PE]  ADR-0010 Error Handling        → OWNER APPROVAL REQUIRED  ✅ APPROVED 2026-03-03
[PE]  ADR-0011 Role Change Audit     → OWNER APPROVAL REQUIRED  ✅ APPROVED 2026-03-03
[PE]  Library List (versions)        → OWNER APPROVAL REQUIRED  ✅ APPROVED 2026-03-03
[PE]  Module Interface Specs         → OWNER APPROVAL REQUIRED  ✅ APPROVED 2026-03-03
[PE]  SE Implementation Briefing     → ISSUED                   ✅ DONE 2026-03-03
[SE]  Implementation Plan            → OWNER APPROVAL REQUIRED  ✅ APPROVED 2026-03-03
[SE]  Implementation (Waves 0–7)     → IN PROGRESS              ✅ COMPLETE 2026-03-04
[QE]  Test Plan                      → OWNER APPROVAL REQUIRED  ✅ APPROVED 2026-03-03
[QE]  Test Execution                 → IN PROGRESS              ✅ COMPLETE 2026-03-04
[PE]  ADR-0012 Optimistic Locking    → OWNER APPROVAL REQUIRED  ✅ APPROVED 2026-03-04
[DE]  Documentation Plan             → OWNER APPROVAL REQUIRED  ✅ APPROVED 2026-03-04
```

---

## Workflow Sequence

```
1.  PE  → Elicit requirements from Owner                        ✅ DONE
2.  PE  → Produce Use Case Document                             ✅ APPROVED
3.  PE  → Produce ADR Batch 1 (foundations)                     ✅ APPROVED
4.  PE  → Produce ADR Batch 2 (JWT, rate limiting, API conv.)  ✅ APPROVED
5.  PE  → Define Module Interface Specs (per module)            ✅ APPROVED
6.  PE  → Approve Library Versions                              ✅ APPROVED
7.  PE  → Produce SE Implementation Briefing                    ✅ DONE — docs/SE_BRIEFING.md
8.  SE  → Produce Implementation Plan                           ✅ APPROVED 2026-03-03
9.  SE  → Implement approved modules (Waves 0–7)               ✅ COMPLETE 2026-03-04
10. QE  → Produce Test Plan                                     ✅ APPROVED 2026-03-03
11. QE  → Execute tests against SE deliverables                 ✅ COMPLETE 2026-03-04
12. PE  → Scalability & concurrency review                      ✅ DONE 2026-03-04
13. QE  → Test coverage audit                                   ✅ DONE 2026-03-04
14. SE  → Fix filter-level error handling (ServletErrorWriter)  ✅ DONE 2026-03-04
15. PE  → ADR-0012 Optimistic Locking                           ✅ APPROVED 2026-03-04
16. QE  → Concurrency test enhancement (OptimisticLockingApiTest) ✅ COMPLETE 2026-03-04
17. SE  → Implement optimistic locking                          ✅ COMPLETE 2026-03-04
18. QE  → Final regression test run (292 tests passing)         ✅ COMPLETE 2026-03-04
19. DE  → Produce Documentation Plan                            ✅ APPROVED 2026-03-04
20. DE  → Document the project                                  ⏳ IN PROGRESS 2026-03-04
```

---

## Completed Work Log

### 2026-03-03 — Foundation & Implementation

- All ADRs (0001–0011) approved
- SE Implementation Plan approved; SE completed all 8 waves
- QE Test Plan approved; QE executed initial test suite (160 scenarios)

### 2026-03-04 — Fixes, Reviews & Follow-Up

| Item | Agent | Description | Status |
|------|-------|-------------|--------|
| IllegalArgumentException in validate() | SE | Added to catch block in LocalAuthProvider.validate() | ✅ DONE |
| Filter-level error handling | SE | Created `ServletErrorResponseWriter` in common; updated JwtFilter and SecurityConfig to produce standard envelope at filter layer | ✅ DONE |
| JwtFilterTest fix | SE | Added PrintWriter mock and updated assertions after sendError → writeError migration | ✅ DONE |
| PE scalability/concurrency review | PE | 22 issues identified across 4 tiers — documented in `docs/FOLLOW_UP.md` | ✅ DONE |
| QE test coverage audit | QE | 4 P1 gaps, 3 P2 gaps, 2 behavioural discrepancies — documented in `docs/FOLLOW_UP.md` | ✅ DONE |
| Run & Test Manual | SE | Created `docs/RUN_AND_TEST_MANUAL.md` — build, run, API reference, end-to-end walkthrough | ✅ DONE |
| ADR-0012 Optimistic Locking | PE | Approved 2026-03-04; detailed analysis complete | ✅ DONE |
| SE: Optimistic locking implementation | SE | @Version fields added to User, Role, EntityType, EntityInstance; version required on all PUT bodies | ✅ DONE 2026-03-04 |
| QE: OptimisticLockingApiTest (12 scenarios) | QE | Covers version increment, stale version 409, missing version 400; final regression: 292 tests passing | ✅ DONE 2026-03-04 |

---

## Outstanding Follow-Up Items

Full details in `docs/FOLLOW_UP.md`. Summary:

### Owner Decisions Required

| ID | Issue | Decision |
|----|-------|----------|
| SEC-009 | Soft-deleted user's token produces 404, not 401 | Accept 404 or change JwtFilter? |
| AUTH-003 | `login_withUnknownUsername` returns 404 (reveals existence) | Accept 404 or change to 401? |

### Tier 1 — Must Fix Before Load Test / Production

| Issue | Status |
|-------|--------|
| TOCTOU race conditions (create/update) → DataIntegrityViolationException handler | ⏳ PENDING |
| Unbounded ConcurrentHashMap in rate limiter → Caffeine cache | ⏳ PENDING |
| No max page size enforcement | ⏳ PENDING |
| **Optimistic locking (ADR-0012)** | ✅ DONE 2026-03-04 |

### QE P1 Test Gaps

| ID | Gap | Status |
|----|-----|--------|
| AUTH-014 | Logout then reuse access token | ⏳ PENDING |
| USER-021 | Login with soft-deleted user | ⏳ PENDING |
| ROLE-018 | Add permission with non-existent entityTypeId | ⏳ PENDING |
| ROLE-024 | Get permissions for non-existent role | ⏳ PENDING |

---

## Module Registry

| Module | Package | Status |
|--------|---------|--------|
| Common | `com.userrole.common` | ✅ IMPLEMENTED |
| Auth (SPI) | `com.userrole.auth.spi` | ✅ IMPLEMENTED |
| Auth (Local) | `com.userrole.auth.local` | ✅ IMPLEMENTED |
| Auth (External) | `com.userrole.auth.external` | v2+ placeholder |
| User | `com.userrole.user` | ✅ IMPLEMENTED |
| Role | `com.userrole.role` | ✅ IMPLEMENTED |
| Entity | `com.userrole.entity` | ✅ IMPLEMENTED |
| Rate Limit | `com.userrole.ratelimit` | ✅ IMPLEMENTED |

---

## Approved Libraries

| # | Library | Group:Artifact | Version | Status |
|---|---------|---------------|---------|--------|
| 1 | JJWT API | `io.jsonwebtoken:jjwt-api` | `0.12.6` | ✅ APPROVED |
| 2 | JJWT Impl | `io.jsonwebtoken:jjwt-impl` | `0.12.6` | ✅ APPROVED |
| 3 | JJWT Jackson | `io.jsonwebtoken:jjwt-jackson` | `0.12.6` | ✅ APPROVED |
| 4 | Bucket4j Core | `com.bucket4j:bucket4j-core` | `8.10.1` | ✅ APPROVED |
| 5 | Spring Boot Starter Web | `org.springframework.boot:spring-boot-starter-web` | `3.4.3` | ✅ APPROVED |
| 6 | Spring Boot Starter Security | `org.springframework.boot:spring-boot-starter-security` | `3.4.3` | ✅ APPROVED |
| 7 | Spring Boot Starter Data JPA | `org.springframework.boot:spring-boot-starter-data-jpa` | `3.4.3` | ✅ APPROVED |
| 8 | H2 Database | `com.h2database:h2` | `2.3.232` | ✅ APPROVED |
| 9 | Flyway Core | `org.flywaydb:flyway-core` | `10.20.1` | ✅ APPROVED |
| 10 | Spring Boot Starter Validation | `org.springframework.boot:spring-boot-starter-validation` | `3.4.3` | ✅ APPROVED |

No new libraries required for ADR-0012 (`@Version` is standard JPA/Jakarta Persistence).

---

## ADR Index

| ADR # | Title | Status |
|-------|-------|--------|
| ADR-0001 | Build Tool (Maven) | ✅ APPROVED |
| ADR-0002 | Java Version (Java 21) | ✅ APPROVED |
| ADR-0003 | Web Framework (Spring Boot 3.x) | ✅ APPROVED |
| ADR-0004 | Database Strategy (JPA + H2 + Flyway) | ✅ APPROVED |
| ADR-0005 | Package / Module Structure | ✅ APPROVED |
| ADR-0006 | Authentication Provider Strategy (Port & Adapter) | ✅ APPROVED |
| ADR-0007 | JWT Library (JJWT) | ✅ APPROVED |
| ADR-0008 | Rate Limiting Strategy (Bucket4j in-memory) | ✅ APPROVED |
| ADR-0009 | API Response Conventions | ✅ APPROVED |
| ADR-0010 | Error Handling Strategy | ✅ APPROVED |
| ADR-0011 | Role Change Audit Strategy | ✅ APPROVED |
| ADR-0012 | Optimistic Locking | ✅ APPROVED |

---

## SE Briefing

**Status:** ✅ ISSUED — `docs/SE_BRIEFING.md`
**Implementation Plan:** ✅ APPROVED — `docs/SE_IMPLEMENTATION_PLAN.md`

### Approved Implementation Decisions (from SE Plan)
- Build order: 8 waves (common → auth.spi → role → user → auth.local → entity → ratelimit)
- `TokenRevocationPort` interface in `auth.spi` — allows `user` to revoke tokens without coupling to `auth.local`
- `RoleAssignmentQueryPort` interface in `common` — allows `role` to check user assignments without coupling to `user`
- 6 Flyway migrations (V1–V6); V6 seeds the ADMIN role row
- No default Admin user seeded in v1; first admin created via direct DB insert
- DTOs are Java records with manual `from()` mapping — no MapStruct
- `SecurityConfig.java` lives in `auth.local`
- Admin enforcement via `@PreAuthorize("hasRole('ADMIN')")` + `@EnableMethodSecurity`
- Self-access checks in service layer (not filter chain)
- `RoleConstants` class in `common` holds the `"ADMIN"` role name string constant

### Post-Implementation Fixes (2026-03-04)
- `ServletErrorResponseWriter` added to `common` — centralised filter-layer error envelope writing
- JwtFilter and SecurityConfig migrated from `sendError()` to `ServletErrorResponseWriter.writeError()`
- `IllegalArgumentException` added to catch block in `LocalAuthProvider.validate()`

## QE Briefing

**Status:** ✅ ISSUED — `docs/QE_BRIEFING.md`
**Test Plan:** ✅ APPROVED — `docs/QE_TEST_PLAN.md`

### Approved Test Decisions
- 292 scenarios total (expanded from original 160; includes 12 optimistic locking scenarios in OptimisticLockingApiTest)
- One shared Spring context via `BaseApiTest`; `RateLimitApiTest` gets its own context (`@DirtiesContext`)
- H2 in PostgreSQL compatibility mode for tests (`MODE=PostgreSQL`)
- `@TestPropertySource` overrides rate limits to 3 (anon) / 5 (auth) — no real time delays
- `@Transactional` rollback for most tests; `reset_test_data.sql` truncation for cross-transaction tests
- Admin actor created programmatically in tests (no seeded admin user)
- `passwordHash` absence asserted on raw JSON of every user response
- Test method naming: `methodOrEndpoint_condition_expectedOutcome`
- Support classes: `BaseApiTest`, `TestDataFactory`, `JwtTestHelper`

---

## Key Documents

| Document | Path | Description |
|----------|------|-------------|
| Use Cases | `docs/USE_CASES.md` | Approved use case document |
| Design | `docs/DESIGN.md` | ADRs, module specs, library list (single source of truth) |
| SE Briefing | `docs/SE_BRIEFING.md` | SE implementation briefing |
| SE Implementation Plan | `docs/SE_IMPLEMENTATION_PLAN.md` | Approved wave-based implementation plan |
| QE Briefing | `docs/QE_BRIEFING.md` | QE test briefing |
| QE Test Plan | `docs/QE_TEST_PLAN.md` | Approved test plan (292 scenarios executed; 12 added for ADR-0012) |
| Follow-Up Items | `docs/FOLLOW_UP.md` | PE review + QE audit findings |
| Run & Test Manual | `docs/RUN_AND_TEST_MANUAL.md` | How to build, run, and test the application |
| Agent Coordination | `docs/AGENT_COORDINATION.md` | This document |

---

*Maintained by: PE | Last updated: 2026-03-04*
