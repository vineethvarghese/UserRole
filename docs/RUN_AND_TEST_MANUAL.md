# UserRole — Run & Test Manual

**Version:** 1.0
**Date:** 2026-03-04

---

## 1. Prerequisites

- Java 21 (LTS)
- Maven 3.9+

---

## 2. Build & Run

```bash
# Build (skip tests for faster startup)
mvn clean package -DskipTests

# Run
mvn spring-boot:run
```

The application starts on **http://localhost:8080**.

---

## 3. Run Tests

```bash
# All tests
mvn test

# Single test class
mvn test -Dtest=AuthApiTest

# Single test method
mvn test -Dtest=AuthApiTest#login_withValidCredentials_returns200WithTokens
```

---

## 4. H2 Console (Development)

- URL: http://localhost:8080/h2-console
- JDBC URL: `jdbc:h2:mem:userroledb`
- Username: `sa`
- Password: *(blank)*

---

## 5. Seed Admin User

The V6 migration seeds the ADMIN role but **not** an admin user. Insert one manually via the H2 console:

```sql
-- Generate a password hash first (see Section 5.1), then:
INSERT INTO users (username, password_hash, full_name, department, email, phone, active, created_at, updated_at)
VALUES ('admin', '<bcrypt_hash_here>', 'System Admin', 'IT', 'admin@example.com', '000-0000', true, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

-- Assign the ADMIN role (role ID 1 from V6 seed)
INSERT INTO user_roles (user_id, role_id, created_at)
VALUES (1, 1, CURRENT_TIMESTAMP);
```

### 5.1 Generate a BCrypt Hash

```bash
# From the project root — uses Spring's BCryptPasswordEncoder
cat > /tmp/HashGen.java << 'EOF'
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
public class HashGen {
    public static void main(String[] args) {
        String password = args.length > 0 ? args[0] : "Password1!";
        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
        System.out.println(encoder.encode(password));
    }
}
EOF
CP=$(mvn -q dependency:build-classpath -Dmdep.outputFile=/dev/stdout 2>/dev/null)
java -cp "$CP" /tmp/HashGen.java "Password1!"
```

Copy the output hash into the SQL INSERT above.

---

## 6. API Reference

All endpoints return a standard envelope:

```json
{
  "status": "success",
  "data": { ... },
  "meta": null,
  "code": null,
  "message": null,
  "timestamp": "2026-03-04T10:00:00Z"
}
```

Error responses:

```json
{
  "status": "error",
  "data": null,
  "meta": null,
  "code": "NOT_FOUND",
  "message": "User not found with id: 99",
  "timestamp": "2026-03-04T10:00:00Z"
}
```

### 6.1 Authentication

#### POST `/api/v1/auth/login`

Login and receive tokens. No auth required.

```bash
curl -s -X POST http://localhost:8080/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{
    "username": "admin",
    "password": "Password1!"
  }'
```

**Response `data`:**

| Field | Type | Description |
|-------|------|-------------|
| accessToken | string | JWT access token (15 min TTL) |
| refreshToken | string | Refresh token (7 day TTL) |
| expiresIn | long | Access token TTL in seconds |

---

#### POST `/api/v1/auth/refresh`

Exchange a refresh token for a new access token. No auth required.

```bash
curl -s -X POST http://localhost:8080/api/v1/auth/refresh \
  -H "Content-Type: application/json" \
  -d '{
    "refreshToken": "<refresh_token>"
  }'
```

---

#### POST `/api/v1/auth/logout`

Invalidate the current session. Requires Bearer token.

```bash
curl -s -X POST http://localhost:8080/api/v1/auth/logout \
  -H "Authorization: Bearer <access_token>"
```

> **Note:** Logout uses the Authorization header only — no request body needed.

---

### Optimistic Locking — `version` Field (Required on all PUT requests)

All `PUT` endpoints require a `version` field in the request body. The `version` value is returned in every GET and POST response for resources that support it (User, Role, EntityType, EntityInstance). You must echo the current `version` back in your PUT body.

- If the version matches the server's current value, the update succeeds and the response returns the incremented version.
- If the version is stale (another update occurred between your GET and PUT), the server returns **HTTP 409** (`CONFLICT`).
- If `version` is absent from the request body, the server returns **HTTP 400** (`VALIDATION_ERROR`).

---

### 6.2 User Management (Admin only, except self-access)

For all endpoints below, include: `-H "Authorization: Bearer <token>"`

#### POST `/api/v1/users` — Create User

```bash
curl -s -X POST http://localhost:8080/api/v1/users \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "username": "jdoe",
    "password": "Secret123!",
    "fullName": "John Doe",
    "department": "Engineering",
    "email": "jdoe@example.com",
    "phone": "555-1234"
  }'
```

#### GET `/api/v1/users/{id}` — Get User

```bash
curl -s http://localhost:8080/api/v1/users/1 \
  -H "Authorization: Bearer $TOKEN"
```

> Regular users can only access their own profile (self-access).

#### GET `/api/v1/users` — List Users (Paginated)

```bash
# Basic pagination
curl -s "http://localhost:8080/api/v1/users?page=0&size=20" \
  -H "Authorization: Bearer $TOKEN"

# Filter by department
curl -s "http://localhost:8080/api/v1/users?page=0&size=20&department=Engineering" \
  -H "Authorization: Bearer $TOKEN"

# Filter by name
curl -s "http://localhost:8080/api/v1/users?page=0&size=20&name=john" \
  -H "Authorization: Bearer $TOKEN"
```

**Response `meta`:**

| Field | Type | Description |
|-------|------|-------------|
| page | int | Current page (0-based) |
| size | int | Page size |
| total | long | Total matching records |

#### PUT `/api/v1/users/{id}` — Update User

```bash
curl -s -X PUT http://localhost:8080/api/v1/users/2 \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "fullName": "Jane Doe",
    "department": "Product",
    "email": "jane@example.com",
    "phone": "555-5678",
    "version": 0
  }'
```

#### DELETE `/api/v1/users/{id}` — Soft-Delete User

```bash
curl -s -X DELETE http://localhost:8080/api/v1/users/2 \
  -H "Authorization: Bearer $TOKEN"
```

> Soft-deletes the user and revokes all active tokens.

#### POST `/api/v1/users/{id}/roles/{roleId}` — Assign Role

```bash
curl -s -X POST http://localhost:8080/api/v1/users/2/roles/1 \
  -H "Authorization: Bearer $TOKEN"
```

#### DELETE `/api/v1/users/{id}/roles/{roleId}` — Revoke Role

```bash
curl -s -X DELETE http://localhost:8080/api/v1/users/2/roles/1 \
  -H "Authorization: Bearer $TOKEN"
```

#### GET `/api/v1/users/{id}/roles` — Get User's Roles

```bash
curl -s http://localhost:8080/api/v1/users/2/roles \
  -H "Authorization: Bearer $TOKEN"
```

#### GET `/api/v1/users/{id}/roles/history` — Role Change Audit Log

```bash
curl -s "http://localhost:8080/api/v1/users/2/roles/history?page=0&size=20" \
  -H "Authorization: Bearer $TOKEN"
```

**Audit entry fields:**

| Field | Type | Description |
|-------|------|-------------|
| id | long | Audit entry ID |
| userId | long | Target user |
| roleId | long | Role involved |
| roleName | string | Role name snapshot at time of action |
| action | string | `ASSIGNED` or `REVOKED` |
| performedBy | long | Admin user ID who performed the change |
| performedAt | instant | Timestamp |

---

### 6.3 Role Management (Admin only)

#### POST `/api/v1/roles` — Create Role

```bash
curl -s -X POST http://localhost:8080/api/v1/roles \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "name": "EDITOR",
    "description": "Can edit documents"
  }'
```

#### GET `/api/v1/roles/{id}` — Get Role

```bash
curl -s http://localhost:8080/api/v1/roles/1 \
  -H "Authorization: Bearer $TOKEN"
```

#### GET `/api/v1/roles` — List All Roles

```bash
curl -s http://localhost:8080/api/v1/roles \
  -H "Authorization: Bearer $TOKEN"
```

#### PUT `/api/v1/roles/{id}` — Update Role

```bash
curl -s -X PUT http://localhost:8080/api/v1/roles/1 \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "name": "ADMIN",
    "description": "Updated description",
    "version": 0
  }'
```

#### DELETE `/api/v1/roles/{id}` — Delete Role

```bash
curl -s -X DELETE http://localhost:8080/api/v1/roles/2 \
  -H "Authorization: Bearer $TOKEN"
```

> Blocked with 409 if users are still assigned to the role.

#### POST `/api/v1/roles/{id}/permissions` — Add Permission

```bash
curl -s -X POST http://localhost:8080/api/v1/roles/2/permissions \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "entityTypeId": 1,
    "action": "READ"
  }'
```

**Valid actions:** `CREATE`, `READ`, `UPDATE`, `DELETE`

#### DELETE `/api/v1/roles/{id}/permissions/{permId}` — Remove Permission

```bash
curl -s -X DELETE http://localhost:8080/api/v1/roles/2/permissions/1 \
  -H "Authorization: Bearer $TOKEN"
```

#### GET `/api/v1/roles/{id}/permissions` — List Permissions

```bash
curl -s http://localhost:8080/api/v1/roles/2/permissions \
  -H "Authorization: Bearer $TOKEN"
```

---

### 6.4 Entity Type Management

#### POST `/api/v1/entity-types` — Create Entity Type (Admin)

```bash
curl -s -X POST http://localhost:8080/api/v1/entity-types \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "name": "Document",
    "description": "A document entity"
  }'
```

#### GET `/api/v1/entity-types` — List Entity Types

```bash
curl -s http://localhost:8080/api/v1/entity-types \
  -H "Authorization: Bearer $TOKEN"
```

#### GET `/api/v1/entity-types/{typeId}` — Get Entity Type

```bash
curl -s http://localhost:8080/api/v1/entity-types/1 \
  -H "Authorization: Bearer $TOKEN"
```

#### PUT `/api/v1/entity-types/{typeId}` — Update Entity Type (Admin)

```bash
curl -s -X PUT http://localhost:8080/api/v1/entity-types/1 \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "name": "Report",
    "description": "A report entity",
    "version": 0
  }'
```

#### DELETE `/api/v1/entity-types/{typeId}` — Delete Entity Type (Admin)

```bash
curl -s -X DELETE http://localhost:8080/api/v1/entity-types/1 \
  -H "Authorization: Bearer $TOKEN"
```

> Blocked with 409 if instances of this type exist.

---

### 6.5 Entity Instance Management (Permission-based)

All instance endpoints are nested under an entity type: `/api/v1/entity-types/{typeId}/instances`

Access requires the matching permission (CREATE/READ/UPDATE/DELETE) on the entity type, granted through the user's roles.

#### POST `.../instances` — Create Instance (requires CREATE)

```bash
curl -s -X POST http://localhost:8080/api/v1/entity-types/1/instances \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "name": "Q1 Report",
    "description": "First quarter report"
  }'
```

#### GET `.../instances` — List Instances (requires READ, paginated)

```bash
curl -s "http://localhost:8080/api/v1/entity-types/1/instances?page=0&size=20" \
  -H "Authorization: Bearer $TOKEN"
```

#### GET `.../instances/{id}` — Get Instance (requires READ)

```bash
curl -s http://localhost:8080/api/v1/entity-types/1/instances/1 \
  -H "Authorization: Bearer $TOKEN"
```

#### PUT `.../instances/{id}` — Update Instance (requires UPDATE)

```bash
curl -s -X PUT http://localhost:8080/api/v1/entity-types/1/instances/1 \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "name": "Q1 Report (Revised)",
    "description": "Updated first quarter report",
    "version": 0
  }'
```

#### DELETE `.../instances/{id}` — Delete Instance (requires DELETE)

```bash
curl -s -X DELETE http://localhost:8080/api/v1/entity-types/1/instances/1 \
  -H "Authorization: Bearer $TOKEN"
```

---

## 7. Rate Limits

| Caller Type | Limit | Header |
|-------------|-------|--------|
| Anonymous | 30 requests/minute | Per IP |
| Authenticated | 120 requests/minute | Per user ID |

When exceeded, the API returns **HTTP 429** with a `Retry-After` header.

---

## 8. End-to-End Walkthrough

A complete session exercising all major features:

```bash
# -------------------------------------------------------
# Step 1: Login as admin
# -------------------------------------------------------
LOGIN=$(curl -s -X POST http://localhost:8080/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"Password1!"}')

echo "$LOGIN" | jq

TOKEN=$(echo "$LOGIN" | jq -r '.data.accessToken')
REFRESH=$(echo "$LOGIN" | jq -r '.data.refreshToken')

# -------------------------------------------------------
# Step 2: Create a role
# -------------------------------------------------------
curl -s -X POST http://localhost:8080/api/v1/roles \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"name":"EDITOR","description":"Can manage documents"}' | jq

# -------------------------------------------------------
# Step 3: Create an entity type
# -------------------------------------------------------
curl -s -X POST http://localhost:8080/api/v1/entity-types \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"name":"Document","description":"A document entity"}' | jq

# -------------------------------------------------------
# Step 4: Grant EDITOR full CRUD on Document
# -------------------------------------------------------
for ACTION in CREATE READ UPDATE DELETE; do
  curl -s -X POST http://localhost:8080/api/v1/roles/2/permissions \
    -H "Authorization: Bearer $TOKEN" \
    -H "Content-Type: application/json" \
    -d "{\"entityTypeId\":1,\"action\":\"$ACTION\"}" | jq .status
done

# -------------------------------------------------------
# Step 5: Create a regular user
# -------------------------------------------------------
curl -s -X POST http://localhost:8080/api/v1/users \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "username": "editor1",
    "password": "Edit0r!Pass",
    "fullName": "Ed Itor",
    "department": "Content",
    "email": "ed@example.com",
    "phone": "555-0001"
  }' | jq

# -------------------------------------------------------
# Step 6: Assign EDITOR role to user
# -------------------------------------------------------
curl -s -X POST http://localhost:8080/api/v1/users/2/roles/2 \
  -H "Authorization: Bearer $TOKEN" | jq

# -------------------------------------------------------
# Step 7: Login as the new user
# -------------------------------------------------------
ED_LOGIN=$(curl -s -X POST http://localhost:8080/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"editor1","password":"Edit0r!Pass"}')

ED_TOKEN=$(echo "$ED_LOGIN" | jq -r '.data.accessToken')

# -------------------------------------------------------
# Step 8: Create a document instance (as editor)
# -------------------------------------------------------
curl -s -X POST http://localhost:8080/api/v1/entity-types/1/instances \
  -H "Authorization: Bearer $ED_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"name":"Q1 Report","description":"First quarter report"}' | jq

# -------------------------------------------------------
# Step 9: List document instances
# -------------------------------------------------------
curl -s "http://localhost:8080/api/v1/entity-types/1/instances?page=0&size=10" \
  -H "Authorization: Bearer $ED_TOKEN" | jq

# -------------------------------------------------------
# Step 10: View audit log (as admin)
# -------------------------------------------------------
curl -s "http://localhost:8080/api/v1/users/2/roles/history?page=0&size=10" \
  -H "Authorization: Bearer $TOKEN" | jq

# -------------------------------------------------------
# Step 11: Refresh token
# -------------------------------------------------------
curl -s -X POST http://localhost:8080/api/v1/auth/refresh \
  -H "Content-Type: application/json" \
  -d "{\"refreshToken\":\"$REFRESH\"}" | jq

# -------------------------------------------------------
# Step 12: Logout
# -------------------------------------------------------
curl -s -X POST http://localhost:8080/api/v1/auth/logout \
  -H "Authorization: Bearer $TOKEN" | jq
```

---

## 9. Common Error Codes

| HTTP | Code | Meaning |
|------|------|---------|
| 400 | `VALIDATION_ERROR` | Invalid request body or parameters |
| 401 | `UNAUTHORIZED` | Missing, invalid, or expired token |
| 403 | `FORBIDDEN` | Insufficient permissions |
| 404 | `NOT_FOUND` | Resource not found |
| 409 | `CONFLICT` | Duplicate name, blocked deletion, or stale `version` on optimistic locking conflict |
| 429 | `RATE_LIMITED` | Rate limit exceeded |

---

## 10. Configuration

Key properties in `application.properties`:

| Property | Default | Description |
|----------|---------|-------------|
| `server.port` | 8080 | Server port |
| `jwt.access-token-expiry-seconds` | 900 | Access token TTL (15 min) |
| `jwt.refresh-token-expiry-seconds` | 604800 | Refresh token TTL (7 days) |
| `ratelimit.anonymous.requests-per-minute` | 30 | Anonymous rate limit |
| `ratelimit.authenticated.requests-per-minute` | 120 | Authenticated rate limit |
| `spring.h2.console.enabled` | true | H2 web console |

---

*Maintained by: SE | Last updated: 2026-03-04*
