# IssueFlow — Architecture & Design

> **Living document.** Updated after every completed module.
> Last updated: Auth/security integration tests complete. 209/209 tests passing.

---

## Table of Contents

1. [Module Build Order](#module-build-order)
2. [Package Structure](#package-structure)
3. [Request Flow](#request-flow)
4. [Key Design Decisions](#key-design-decisions)
5. [Database Schema](#database-schema)
6. [Error Handling](#error-handling)
7. [Tech Stack](#tech-stack)

---

## Module Build Order

| # | Module | Status | Dependencies | Notes |
|---|---|---|---|---|
| 1 | Architecture | ✅ Done | — | This document |
| 2 | User | ✅ Done | — | Foundation; everything references it |
| 3 | Project | ✅ Done | User | Soft delete, restore, owner FK to User |
| 4 | Auth (JWT) | ✅ Done | User | Login, logout (blacklist), /me, BCrypt, JwtAuthFilter, SecurityConfig |
| 5 | Ticket | ✅ Done | Project, User | Soft delete, forward-only status flow, `isOverdue` persisted (set by scheduler), optimistic locking (`@Version`) |
| 6 | Comment | ✅ Done | Ticket, User | CRUD on ticket comments; `@Version` optimistic locking; `@PrePersist`/`@PreUpdate` timestamps |
| 7 | @Mentions | ✅ Done | Comment | `CommentMention` composite PK entity; regex parsing; diff on update; `GET /users/{id}/mentions` paginated; Flyway V1 migration |
| 8 | Audit Log | ✅ Done | All modules | Append-only; `REQUIRES_NEW` transaction isolation; `actor` auto-derived; `SecurityUtils` for caller ID; Flyway V2; `GET /audit-logs` with 5-way AND filter + pagination |
| 9 | Ticket Dependencies | ✅ Done | Ticket | Composite PK join entity; DONE-transition blocker check; `BusinessRuleException` → 422; Flyway V3 |
| 10 | Attachments | ✅ Done | Ticket | Filesystem storage (`uploads/{ticketId}/{uuid}.ext`); MIME + size validation; file rollback on DB failure; Flyway V4 |
| 11 | Soft Delete | ✅ Done | Project, Ticket | `boolean deleted` → `LocalDateTime deletedAt`; cascade with same timestamp; ADMIN-only restore; Flyway V5 |
| 12 | CSV Export/Import | ✅ Done | Ticket | Apache Commons CSV; JOIN FETCH on export; batch user pre-load on import; per-row transactions |
| 13 | Auto-Escalation | ✅ Done | Ticket | `@Scheduled` cron (configurable via `ticket.escalation.cron`); `isOverdue` persisted (Flyway V6); per-ticket `REQUIRES_NEW` transactions via self-injection; `AUTO_ESCALATE` audit entries; manual priority PATCH resets `isOverdue` |
| 14 | Auto-Assignment | ✅ Done | Ticket, User | Assign to least-loaded DEVELOPER in project; `GET /projects/{id}/workload`; native SQL projection to avoid circular package dependency |

---

## Package Structure

```
com.att.tdp.issueflow
│
├── IssueFlowApplication.java
│
├── common/
│   ├── SecurityUtils.java             (getCurrentUserId from SecurityContext)
│   └── exception/
│       ├── GlobalExceptionHandler.java
│       ├── ResourceNotFoundException.java
│       ├── ConflictException.java
│       ├── BadRequestException.java
│       ├── BusinessRuleException.java  (semantic violations → 422)
│       └── ErrorResponse.java
│
├── user/
│   ├── User.java
│   ├── UserRole.java                  (enum: ADMIN, DEVELOPER)
│   ├── UserRepository.java
│   ├── UserService.java
│   ├── UserServiceImpl.java
│   ├── UserController.java
│   ├── UserWorkloadView.java          (interface projection: getUserId, getUsername, getOpenTicketCount)
│   └── dto/
│       ├── CreateUserRequest.java
│       ├── UpdateUserRequest.java
│       └── UserResponse.java
│
├── auth/
│   ├── AuthController.java
│   ├── AuthService.java
│   ├── AuthServiceImpl.java
│   ├── JwtService.java
│   ├── JwtAuthFilter.java
│   ├── SecurityConfig.java
│   ├── TokenBlacklist.java
│   └── dto/
│       ├── LoginRequest.java
│       ├── LoginResponse.java
│       └── MeResponse.java
│
├── project/
│   ├── Project.java
│   ├── ProjectRepository.java
│   ├── ProjectService.java
│   ├── ProjectServiceImpl.java
│   ├── ProjectController.java         (GET /{projectId}/workload added)
│   └── dto/
│       ├── CreateProjectRequest.java
│       ├── UpdateProjectRequest.java
│       ├── ProjectResponse.java
│       └── WorkloadResponse.java      (userId, username, openTicketCount; factory from UserWorkloadView)
│
├── ticket/
│   ├── Ticket.java
│   ├── TicketStatus.java              (enum: TODO, IN_PROGRESS, DONE)
│   ├── TicketPriority.java            (enum: LOW, MEDIUM, HIGH, CRITICAL)
│   ├── TicketType.java                (enum: BUG, FEATURE, TASK)
│   ├── TicketRepository.java
│   ├── TicketService.java
│   ├── TicketServiceImpl.java
│   ├── TicketController.java
│   ├── TicketDependency.java          (@Entity; composite PK via @EmbeddedId)
│   ├── TicketDependencyId.java        (@Embeddable; ticketId + blockedById)
│   ├── TicketDependencyRepository.java
│   ├── TicketDependencyService.java
│   ├── TicketDependencyServiceImpl.java
│   ├── TicketDependencyController.java
│   ├── TicketEscalationScheduler.java   (@Scheduled; per-ticket REQUIRES_NEW; self-injection)
│   └── dto/
│       ├── CreateTicketRequest.java
│       ├── UpdateTicketRequest.java
│       ├── TicketResponse.java
│       ├── AddDependencyRequest.java
│       └── DependencyResponse.java
│
├── comment/
│   ├── Comment.java
│   ├── CommentMention.java            (@Entity; composite PK via @EmbeddedId)
│   ├── CommentMentionId.java          (@Embeddable; commentId + userId)
│   ├── CommentRepository.java
│   ├── CommentMentionRepository.java
│   ├── CommentService.java
│   ├── CommentServiceImpl.java
│   ├── CommentController.java
│   └── dto/
│       ├── CreateCommentRequest.java
│       ├── UpdateCommentRequest.java
│       └── CommentResponse.java       (includes mentionedUsers list)
│
├── auditlog/
│   ├── AuditLog.java
│   ├── AuditAction.java               (enum: CREATE, UPDATE, DELETE, RESTORE, AUTO_ASSIGN, AUTO_ESCALATE, LOGIN, LOGOUT)
│   ├── AuditEntityType.java           (enum: USER, PROJECT, TICKET, COMMENT, ATTACHMENT, DEPENDENCY, AUTH)
│   ├── AuditActor.java                (enum: USER, SYSTEM)
│   ├── AuditLogRepository.java        (extends Repository + JpaSpecificationExecutor — no delete/update exposed)
│   ├── AuditLogService.java
│   ├── AuditLogServiceImpl.java
│   ├── AuditLogController.java
│   └── dto/
│       ├── AuditLogResponse.java
│       └── AuditLogPageResponse.java  ({ data, total, page, pageSize })
│
├── attachment/
│   ├── Attachment.java
│   ├── AttachmentRepository.java
│   ├── AttachmentService.java
│   ├── AttachmentServiceImpl.java
│   ├── AttachmentController.java
│   └── dto/
│       └── AttachmentResponse.java    (id, ticketId, filename, contentType, fileSize — storagePath never exposed)
│
```

---

## Request Flow

```
Client Request
     │
     ▼
JwtAuthFilter (OncePerRequestFilter)
  - Extracts Bearer token from Authorization header
  - Validates token (signature, expiry, blacklist)
  - Loads UserDetails → sets SecurityContext
     │
     ▼
SecurityConfig (SecurityFilterChain)
  - Checks if endpoint requires authentication
  - Rejects with 401 if unauthenticated
     │
     ▼
Controller (thin layer)
  - Validates @RequestBody via @Valid
  - Delegates to Service
  - Returns ResponseEntity<DTO>
     │
     ▼
Service (all business logic lives here)
  - Validates business rules (uniqueness, ownership, state transitions)
  - Throws typed exceptions (ResourceNotFoundException, ConflictException, etc.)
  - Calls Repository
  - Maps Entity → Response DTO
     │
     ▼
Repository (Spring Data JPA)
  - Executes queries against PostgreSQL
     │
     ▼
PostgreSQL (via Docker)
```

### Error path

```
Exception thrown in Service
     │
     ▼
GlobalExceptionHandler (@RestControllerAdvice)
  - Maps exception type → HTTP status + ErrorResponse body
  - Returned to client as JSON
```

---

## Key Design Decisions

### DTOs at every boundary
- Entities are never serialized directly into responses
- `XxxResponse.from(entity)` static factory on every response DTO
- Request DTOs carry Jakarta validation annotations

### Soft Delete & Restore
- Tickets and Projects are never hard-deleted
- `deleted BOOLEAN` migrated to `deleted_at TIMESTAMP` (Flyway V5): `null` = active, non-null = soft-deleted
- Standard `GET` queries filter `WHERE deleted_at IS NULL`; deleted records appear only on `GET /xxx/deleted`
- All restore and deleted-list endpoints require `ROLE_ADMIN` via `@PreAuthorize` (enabled by `@EnableMethodSecurity` on `SecurityConfig`)
- **Cascade delete**: `ProjectServiceImpl.deleteProject` captures one `LocalDateTime now = LocalDateTime.now()`, sets all active tickets' `deletedAt = now`, then sets the project's `deletedAt = now` — a single shared timestamp
- **Cascade restore discrimination**: `restoreProject` calls `findCascadeDeletedByProjectIdAndDeletedAt(id, projectDeletedAt)` — JPQL custom query matching the exact shared timestamp. Tickets individually deleted before (different timestamp) are left alone
- **Ticket restore guard**: restoring an individual ticket whose parent project is still deleted returns 400 `"Cannot restore ticket: parent project is deleted"`
- Endpoints: `GET /tickets/deleted?projectId=`, `POST /tickets/{id}/restore`, `GET /projects/deleted`, `POST /projects/{id}/restore`
- `AccessDeniedException` (from `@PreAuthorize`) now handled explicitly in `GlobalExceptionHandler` → 403 (prevents catch-all `Exception.class` handler from swallowing it as 500)

### Audit Logging
- Every write operation across all services calls `auditLogService.record(...)` explicitly — no AOP, no Hibernate listeners
- `record()` runs in `@Transactional(propagation = REQUIRES_NEW)` — audit write commits independently; a rollback in the business transaction does not undo it, and an audit failure does not roll back the business operation
- `actor` is auto-derived: `performedBy == null → SYSTEM`, else `→ USER`
- `performedBy` is resolved via `SecurityUtils.getCurrentUserId()` (looks up username from `SecurityContextHolder`)
- Unauthenticated callers (e.g. `POST /users` which is `permitAll`) produce `actor=SYSTEM, performedBy=null`
- `performed_by` column has no FK constraint — the log survives user deletion
- `AuditLogRepository` extends `Repository + JpaSpecificationExecutor` only — `delete` and bulk-update methods are not exposed
- `GET /audit-logs` supports AND-combined filters: `action`, `entityType`, `entityId`, `actor`, `performedBy`; paginated, ordered `timestamp DESC`
- Response shape: `{ data: [...], total, page, pageSize }` — not Spring's `Page<T>` wrapper

### Password Storage
- Passwords hashed with `BCryptPasswordEncoder` (Spring Security)
- Raw password never stored or returned
- `password` field excluded from all `UserResponse` DTOs

### JWT Authentication
- Stateless tokens signed with HS256 + secret key (from `application.yaml`)
- Token lifetime: 1 hour
- Logout implemented via in-memory blacklist keyed on `jti` (JWT ID) claim
- `Authorization: Bearer <token>` header required for protected endpoints

### Ticket Dependencies
- Modelled as an explicit join entity `TicketDependency` with composite PK `(ticket_id, blocked_by_id)` — not a `@ManyToMany` join table
- Both tickets must belong to the same project; self-dependency is rejected; both validated as 404/400 before the insert
- Duplicate insert caught via `DataIntegrityViolationException` (from `saveAndFlush`) → re-thrown as `ConflictException` 409
- **DONE transition check**: before a ticket can move to `DONE`, `findUnresolvedBlockers` queries all `TicketDependency` rows for that ticket where `blockedBy.status != DONE` — **no deleted filter** (soft-deleted blockers still count as unresolved)
- Violation throws `BusinessRuleException("Ticket has unresolved blockers")` → 422 Unprocessable Entity
- `GET /tickets/{id}/dependencies` uses `JOIN FETCH blockedBy.project` to load all required fields in a single query (no N+1)
- All writes (add, remove) record an `AuditLog` entry with `entityType = DEPENDENCY`
- Flyway V3 migration creates the `ticket_dependencies` table with cascade-delete FKs and covering indexes

### Attachments
- Files stored on the **local filesystem** at `uploads/{ticketId}/{uuid}.{ext}` — not in the database (no BLOB column) and not in cloud storage
- Directory is created lazily with `Files.createDirectories()` on first upload to a ticket
- Original filename preserved in DB; internal UUID-based name used on disk to prevent collisions
- `storagePath` is an internal field — never included in any response DTO
- **Validation** (service layer, before any disk I/O): size > 10 MB → 400; MIME type not in `{image/png, image/jpeg, application/pdf, text/plain}` → 400
- **Upload atomicity**: file is written to disk first; DB insert uses `saveAndFlush`; if the insert throws, the file is deleted from disk before rethrowing
- **Delete**: DB record deleted first, then `Files.deleteIfExists` on the stored path — a missing file on disk is silently ignored (no error)
- Hard delete only — no `deleted` flag, no restore endpoint
- All writes (upload, delete) record an `AuditLog` entry with `entityType = ATTACHMENT`
- Flyway V4 migration creates the `attachments` table with cascade-delete FK on `ticket_id`

### CSV Export / Import

**Export** (`GET /tickets/export?projectId=`):
- Returns only active (`deletedAt IS NULL`) tickets for the given project
- Column order: `id, title, description, status, priority, type, assigneeId` — fixed, no projectId column
- `Content-Type: text/csv;charset=UTF-8`; `Content-Disposition: attachment; filename="tickets-{projectId}.csv"`
- Null values written as empty strings — never the string `"null"`
- Uses a single `LEFT JOIN FETCH t.assignee` query to avoid N+1; writes via `CSVPrinter`
- Project not found → 404; project with no active tickets → header row only
- No audit log

**Import** (`POST /tickets/import` — multipart: `file`, `projectId`):
- Parses with `CSVFormat.DEFAULT.withFirstRecordAsHeader().withIgnoreEmptyLines(true).withTrim(true)`
- `id` column always ignored — new ID generated for every row regardless of CSV content
- `projectId` always taken from form field — never from the CSV
- Required fields per row: `title`, `status`, `priority`, `type`; optional: `description`, `assigneeId`
- Enum validation: invalid value → `"Invalid {field} value: {raw}"` error on that row
- **Batch user pre-load**: collects all unique `assigneeId` values from the file, loads in a single `findAllByIdIn` query, looks up in a `Map<Long, User>` per row — no per-row DB query for user validation
- **Per-row transactions**: `importTickets` is NOT `@Transactional`; each `ticketService.createTicket()` call runs in its own transaction; a row failure does not block subsequent rows
- Returns `{ created, failed, errors: [{ row, message }] }` — row numbering starts at 1 (header excluded)
- Audit: calls `auditLogService.record(CREATE, TICKET, id, userId, "Ticket imported from CSV")` for each successful row

### Auto-Assignment
- Triggered only at ticket **creation** (`POST /tickets`); update and scheduler flows are never affected
- If `assigneeId` is provided in the request it is used directly — no auto-assignment runs
- **Candidate pool**: users with `role = DEVELOPER` only — ADMIN users are never candidates
- **Workload definition**: count of tickets assigned to the user in the **same project** where `status != 'DONE'` and `deleted_at IS NULL`; tickets in other projects are invisible to the query
- **Selection**: lowest workload count; tie-broken by `u.id ASC` (lower id = created earlier, stable deterministic order)
- **No candidates**: if no DEVELOPERs exist the ticket is saved with `assigneeId = null` and no audit entry is written
- **Native SQL query** (`findDevelopersForAssignment`) in `UserRepository` — chosen over JPQL to avoid a circular package dependency (`ticket` package would have to be imported into `user` package for `TicketStatus`); table/column names used as strings instead
- A second native query `findDeveloperWorkloadByProject` (same join, ordered by `u.id ASC`) powers the workload endpoint
- **Interface projection** `UserWorkloadView` (`getUserId()`, `getUsername()`, `getOpenTicketCount()`) maps the native query result without an extra entity class; Spring Data maps column aliases case-insensitively to getter names
- **Audit**: on auto-assignment, writes `action=AUTO_ASSIGN / entityType=TICKET / actor=SYSTEM / performedBy=null` with details `"Auto-assigned to user {username} (workload: {count} open tickets)"`; the CREATE audit log is written first, AUTO_ASSIGN second
- **Workload endpoint**: `GET /projects/{projectId}/workload` → `List<WorkloadResponse>` ordered by `u.id ASC`; 404 if project is missing or deleted; requires authentication; no role restriction

### Auto-Escalation (Scheduler)
- `TicketEscalationScheduler` — Spring `@Component`; `@EnableScheduling` on `IssueFlowApplication`
- Cron read from `ticket.escalation.cron` property (default: `0 * * * * *` — every minute); reconfigurable without code changes
- **Eligible tickets**: `dueDate IS NOT NULL AND dueDate < NOW() AND status != DONE AND deletedAt IS NULL` — single JPQL query, no N+1
- **Escalation rules** (priority only — status is never touched):
  - LOW → MEDIUM (`isOverdue` unchanged, remains false)
  - MEDIUM → HIGH (`isOverdue` unchanged, remains false)
  - HIGH → CRITICAL + `isOverdue = true`
  - CRITICAL (ceiling) → `isOverdue = true`, priority unchanged — idempotent
- **`isOverdue`** is a persisted `BOOLEAN` column (`is_overdue NOT NULL DEFAULT FALSE`, Flyway V6), not recomputed on read; `TicketResponse.from()` reads the stored value directly
- **Manual priority reset**: when `PATCH /tickets/{id}` explicitly includes a `priority` field, `isOverdue` is cleared to `false` immediately; the next scheduler cycle re-evaluates from the new priority
- **Transaction isolation**: `escalateOverdueTickets()` carries no `@Transactional`; each ticket is processed in `escalateSingleTicket()` under `@Transactional(REQUIRES_NEW)` via self-injection (`@Lazy @Autowired TicketEscalationScheduler self`) — one failure is logged and skipped, remaining tickets commit independently
- **Audit**: every escalation writes `action=AUTO_ESCALATE / entityType=TICKET / actor=SYSTEM / performedBy=null` with a human-readable `details` string (e.g. `"Priority escalated from HIGH to CRITICAL (overdue)"`, `"Ticket remains CRITICAL and overdue"`)

### @Mentions
- `@username` tokens parsed from comment content with regex `@([a-zA-Z0-9_]+)` (case-insensitive match against DB)
- Unknown usernames are silently ignored — no error thrown
- Stored as `CommentMention` rows with composite PK `(comment_id, user_id)` — not a join table via `@ManyToMany`
- **On create**: all valid mentions inserted after the comment is persisted
- **On update**: diff old vs new mention sets — insert added, delete removed, leave unchanged untouched (no delete-all-reinsert)
- `CommentResponse.mentionedUsers` always included, loaded via `JOIN FETCH` to avoid N+1
- `GET /users/{userId}/mentions` returns paginated comments (ordered by `createdAt DESC`) where the user was mentioned; requires authentication

### Optimistic Locking
- `@Version` field on `Ticket` and `Comment` entities to prevent lost updates under concurrent edits

### HTTP Status Conventions
| Situation | Status |
|---|---|
| Success (read/update/delete) | 200 OK |
| Validation failure | 400 Bad Request |
| Unauthenticated | 401 Unauthorized |
| Forbidden | 403 Forbidden |
| Resource not found | 404 Not Found |
| Duplicate (username, email) | 409 Conflict |
| Business rule violation (e.g. unresolved blockers) | 422 Unprocessable Entity |
| Server error | 500 Internal Server Error |

---

## Database Schema

> Hybrid schema management: Hibernate (`ddl-auto: update`) owns all tables except those created by Flyway migrations. Flyway-managed: `comment_mentions` (V1), `audit_logs` (V2), `ticket_dependencies` (V3), `attachments` (V4), `deleted_at` column migration (V5), `is_overdue` column (V6). Tables below reflect the full intended schema.

### `users`
| Column | Type | Constraints |
|---|---|---|
| id | BIGINT | PK, auto-increment |
| username | VARCHAR(50) | UNIQUE, NOT NULL |
| email | VARCHAR(100) | UNIQUE, NOT NULL |
| full_name | VARCHAR(100) | NOT NULL |
| password | VARCHAR(255) | NOT NULL (added in Auth module) |
| role | VARCHAR(20) | NOT NULL — ADMIN / DEVELOPER |

### `projects`
| Column | Type | Constraints |
|---|---|---|
| id | BIGINT | PK, auto-increment |
| name | VARCHAR(100) | NOT NULL |
| description | VARCHAR(500) | NOT NULL |
| owner_id | BIGINT | FK → users.id |
| deleted_at | TIMESTAMP | nullable — null = active _(Flyway V5)_ |

### `tickets`
| Column | Type | Constraints |
|---|---|---|
| id | BIGINT | PK, auto-increment |
| title | VARCHAR(255) | NOT NULL |
| description | TEXT | |
| status | VARCHAR(20) | NOT NULL — TODO / IN_PROGRESS / IN_REVIEW / DONE |
| priority | VARCHAR(20) | NOT NULL — LOW / MEDIUM / HIGH / CRITICAL |
| type | VARCHAR(20) | NOT NULL — BUG / FEATURE / TASK |
| project_id | BIGINT | FK → projects.id |
| assignee_id | BIGINT | FK → users.id, nullable |
| due_date | TIMESTAMP | nullable |
| is_overdue | BOOLEAN | NOT NULL DEFAULT FALSE — set by escalation scheduler _(Flyway V6)_ |
| deleted_at | TIMESTAMP | nullable — null = active _(Flyway V5)_ |
| version | BIGINT | Optimistic locking |

### `comments`
| Column | Type | Constraints |
|---|---|---|
| id | BIGINT | PK, auto-increment |
| ticket_id | BIGINT | FK → tickets.id, NOT NULL |
| author_id | BIGINT | FK → users.id, NOT NULL |
| content | TEXT | NOT NULL |
| created_at | TIMESTAMP | NOT NULL, immutable |
| updated_at | TIMESTAMP | NOT NULL |
| version | BIGINT | Optimistic locking |

### `comment_mentions` _(Flyway V1)_
| Column | Type | Constraints |
|---|---|---|
| comment_id | BIGINT | PK (composite), FK → comments.id ON DELETE CASCADE |
| user_id | BIGINT | PK (composite), FK → users.id ON DELETE CASCADE |

### `audit_logs` _(Flyway V2)_
| Column | Type | Constraints |
|---|---|---|
| id | BIGINT | PK, auto-increment |
| action | VARCHAR(20) | NOT NULL — CREATE / UPDATE / DELETE / RESTORE / AUTO_ASSIGN / AUTO_ESCALATE / LOGIN / LOGOUT |
| entity_type | VARCHAR(20) | NOT NULL — USER / PROJECT / TICKET / COMMENT / ATTACHMENT / DEPENDENCY / AUTH |
| entity_id | BIGINT | NOT NULL |
| actor | VARCHAR(10) | NOT NULL — USER / SYSTEM |
| performed_by | BIGINT | nullable, no FK (log survives user deletion) |
| details | TEXT | nullable, human-readable description |
| timestamp | TIMESTAMP | NOT NULL, immutable |

Indexes: `action`, `entity_type`, `entity_id`, `actor`, `performed_by`

### `ticket_dependencies` _(Flyway V3)_
| Column | Type | Constraints |
|---|---|---|
| ticket_id | BIGINT | PK (composite), FK → tickets.id ON DELETE CASCADE |
| blocked_by_id | BIGINT | PK (composite), FK → tickets.id ON DELETE CASCADE |

Indexes: `ticket_id`, `blocked_by_id`

### `attachments` _(Flyway V4)_
| Column | Type | Constraints |
|---|---|---|
| id | BIGINT | PK, auto-increment |
| ticket_id | BIGINT | NOT NULL, FK → tickets.id ON DELETE CASCADE |
| filename | VARCHAR(255) | NOT NULL — original client filename |
| content_type | VARCHAR(100) | NOT NULL — MIME type |
| file_size | BIGINT | NOT NULL — bytes |
| storage_path | VARCHAR(500) | NOT NULL — internal disk path, never exposed in API |
| created_at | TIMESTAMP | NOT NULL, immutable |

Index: `ticket_id`

---

## Error Handling

All errors return a unified `ErrorResponse`:

```json
{
  "status": 400,
  "message": "Validation failed",
  "timestamp": "2026-05-23T15:00:00Z",
  "fieldErrors": {
    "email": "Email must be a valid address",
    "username": "Username is required"
  }
}
```

`fieldErrors` is only present on validation failures (400).

Handled by `GlobalExceptionHandler`:
- `ResourceNotFoundException` → 404
- `ConflictException` → 409
- `BadRequestException` → 400
- `MethodArgumentNotValidException` → 400 + field errors
- `MethodArgumentTypeMismatchException` → 400 (invalid enum query param, e.g. `?action=BADVALUE`)
- `ObjectOptimisticLockingFailureException` → 409
- `BusinessRuleException` → 422 (semantic violations: e.g. ticket has unresolved blockers)
- `AccessDeniedException` → 403 (explicit handler prevents catch-all from swallowing it as 500)
- `Exception` (catch-all) → 500

---

## Tech Stack

| Layer | Technology |
|---|---|
| Language | Java 21 |
| Framework | Spring Boot 3.4.2 |
| Security | Spring Security + JJWT 0.12.6 |
| Persistence | Spring Data JPA (Hibernate 6) |
| Database | PostgreSQL 18 (Docker) |
| Test DB | H2 (in-memory) |
| Build | Maven (mvnw wrapper) |
| Validation | Jakarta Validation |
| Boilerplate | Lombok |
| Migrations | Flyway 10 (PostgreSQL extension) |
| File upload | Spring Multipart (max 10MB) |
