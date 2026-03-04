# Use Case Document — UserRole Web Service

**Version:** 1.0 (APPROVED)
**Author:** PE
**Date:** 2026-03-03
**Owner Approval:** [x] APPROVED

---

## 1. System Purpose

UserRole is a web service that provides centralised management of **Users**, **Roles**, and **Entities**.
It enforces role-based access control (RBAC): a Role defines which actions a User may perform on which Entities.
The service exposes a secured REST API protected by JWT authentication and rate limiting.
Authentication is provider-agnostic: local credential management is the v1 implementation, designed for zero-code migration to an external provider (e.g. Keycloak, Auth0, Okta, Azure AD).

---

## 2. Actors

| Actor | Description |
|-------|-------------|
| **Admin** | Full system access — manages users, roles, and entities |
| **Regular User** | Authenticated; capabilities constrained by assigned roles |
| **Anonymous** | Unauthenticated; can only reach public endpoints (login) |
| **API Client** | External system consuming the API programmatically via JWT |

---

## 3. Use Cases

### 3.1 Authentication (UC-AUTH)

| ID | Use Case | Actor | Description |
|----|----------|-------|-------------|
| UC-AUTH-001 | Login | Anonymous | Submit credentials; receive a signed JWT access token and refresh token. Delegated to the configured AuthProvider. |
| UC-AUTH-002 | Refresh Token | Authenticated | Exchange a valid refresh token for a new access token. |
| UC-AUTH-003 | Logout | Authenticated | Server-side token invalidation. |
| UC-AUTH-004 | Provider-agnostic auth handshake | System (internal) | System delegates all auth decisions to the configured AuthProvider (local or external). Callers and all downstream modules are unaware of which provider is active. |

---

### 3.2 User Management (UC-USER)

| ID | Use Case | Actor | Description |
|----|----------|-------|-------------|
| UC-USER-001 | Create User | Admin | Create a user with full name, department, contact info (email, phone), and initial credentials. |
| UC-USER-002 | Get User by ID | Admin / Self | Admin sees any user; regular user sees only their own profile. |
| UC-USER-003 | List Users | Admin | Paginated list; filterable by department and name. |
| UC-USER-004 | Update User | Admin / Self | Update name, department, contact info. Regular users may only update their own profile. |
| UC-USER-005 | Delete User | Admin | Soft-delete; revokes all active tokens for that user. |
| UC-USER-006 | Assign Role to User | Admin | Assign one or more roles to a user. Records an ASSIGNED audit entry. |
| UC-USER-007 | Revoke Role from User | Admin | Remove a role assignment from a user. Records a REVOKED audit entry. |
| UC-USER-008 | Get Roles for User | Admin / Self | List all roles currently assigned to a user. |
| UC-USER-009 | Get Role Change History for User | Admin / Self | Paginated audit log of all role assignments and revocations for a user, including who performed the change and when. |

---

### 3.3 Role Management (UC-ROLE)

| ID | Use Case | Actor | Description |
|----|----------|-------|-------------|
| UC-ROLE-001 | Create Role | Admin | Create a named role with an optional description. |
| UC-ROLE-002 | Get Role by ID | Admin | Retrieve a role's definition and its associated permissions. |
| UC-ROLE-003 | List Roles | Admin | Retrieve all defined roles. |
| UC-ROLE-004 | Update Role | Admin | Update a role's name or description. |
| UC-ROLE-005 | Delete Role | Admin | Delete a role. Blocked if users are still assigned to it. |
| UC-ROLE-006 | Add Permission to Role | Admin | Grant a role the ability to perform an Action (CREATE, READ, UPDATE, DELETE) on a specific Entity Type. |
| UC-ROLE-007 | Remove Permission from Role | Admin | Revoke a specific permission from a role. |
| UC-ROLE-008 | Get Permissions for Role | Admin | List all permissions associated with a role. |

---

### 3.4 Entity Management (UC-ENT)

> An **Entity** is a configurable domain object type managed by the system.
> Each Entity Type has its own CRUD endpoints for its instances.
> Access to entity instances is governed by Roles.

| ID | Use Case | Actor | Description |
|----|----------|-------|-------------|
| UC-ENT-001 | Create Entity Type | Admin | Define a new Entity Type with a name and description. |
| UC-ENT-002 | List Entity Types | Admin / User | Retrieve all registered Entity Types. |
| UC-ENT-003 | Get Entity Type by ID | Admin / User | Retrieve the definition of a specific Entity Type. |
| UC-ENT-004 | Update Entity Type | Admin | Update an Entity Type's name or description. |
| UC-ENT-005 | Delete Entity Type | Admin | Delete an Entity Type. Blocked if instances exist. |
| UC-ENT-006 | Create Entity Instance | Authorised User | Create an instance of a given Entity Type. Requires CREATE permission on that type. |
| UC-ENT-007 | Get Entity Instance | Authorised User | Retrieve an instance by ID. Requires READ permission on that type. |
| UC-ENT-008 | List Entity Instances | Authorised User | Paginated list of instances of an Entity Type. Requires READ permission. |
| UC-ENT-009 | Update Entity Instance | Authorised User | Update an entity instance. Requires UPDATE permission on that type. |
| UC-ENT-010 | Delete Entity Instance | Authorised User | Delete an entity instance. Requires DELETE permission on that type. |

---

### 3.5 Cross-Cutting: Authorisation (UC-AUTHZ)

| ID | Use Case | Actor | Description |
|----|----------|-------|-------------|
| UC-AUTHZ-001 | Permission Check | System (internal) | Before any protected action, check whether the authenticated user's roles grant the required Action on the target Entity Type. Returns allow or deny. |

---

### 3.6 Cross-Cutting: Rate Limiting (UC-RATE)

| ID | Use Case | Actor | Description |
|----|----------|-------|-------------|
| UC-RATE-001 | Rate Limit Enforcement | System (internal) | All API calls are subject to rate limits. Exceeded callers receive HTTP 429 with a Retry-After header. |

---

## 4. RBAC Model

```
User ──< UserRole >── Role ──< RolePermission >── Permission
                                                       │
                                              Action × EntityType
                                         (CREATE | READ | UPDATE | DELETE)
```

A request is **allowed** if the User holds at least one Role that grants the required Action on the target Entity Type.

---

## 5. Out of Scope (v1)

- Row-level / field-level security
- OAuth2 / SSO / LDAP (external provider support is structurally ready but not implemented)
- Multi-tenancy
- Audit log / event streaming
- Dynamic entity schemas (schema-on-write)

---

*Maintained by: PE | Last updated: 2026-03-03*
