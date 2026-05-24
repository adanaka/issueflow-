# IssueFlow — AI-Assisted Development Prompts

## AI Model

All prompts in this project were sent to **Claude Sonnet 4.6** via **Claude Code**.

---

## Development Approach

Prompts were written incrementally, one module at a time, following a controller → service → repository pattern. Each prompt targeted a single feature and included the full data model, validation rules, and business logic for that module. Architecture decisions — including entity relationships, exception strategy, and transaction boundaries — were made before writing each prompt, so the AI could implement to a known design rather than guess at structure.

---

## Main Prompts by Module

---

### 1. User Module

**Goal:** Create the foundational `User` entity with role-based access, BCrypt password hashing, and full CRUD endpoints.

> Create a User module for a Spring Boot 3 project.
>
> Entity fields: `id` (BIGINT PK auto), `username` (VARCHAR 50, unique, not null), `email` (VARCHAR 100, unique, not null), `fullName` (VARCHAR 100, not null), `password` (VARCHAR 255, not null), `role` (enum: ADMIN, DEVELOPER).
>
> Use Lombok (`@Builder`, `@Data`, `@NoArgsConstructor`, `@AllArgsConstructor`). Hash passwords with `BCryptPasswordEncoder` — never store raw passwords, never return the password field in any response.
>
> Endpoints:
> - `POST /users` — create user (public, no auth required)
> - `GET /users` — list all users
> - `GET /users/{id}` — get user by id
> - `PUT /users/{id}` — update user (username, email, fullName, password)
> - `DELETE /users/{id}` — delete user
>
> Use a `UserResponse` DTO with a static `from(User)` factory. Use `UserService` interface + `UserServiceImpl`. Throw `ResourceNotFoundException` (→ 404) when a user is not found, `ConflictException` (→ 409) on duplicate username or email. Use Jakarta validation on request DTOs. Include a `GlobalExceptionHandler` with `@RestControllerAdvice` that maps each exception type to the correct HTTP status and returns a consistent `ErrorResponse` body.

---

### 2. Authentication Module

**Goal:** Add stateless JWT authentication with login, logout via token blacklist, and a `/me` endpoint.

> Add JWT authentication to the Spring Boot project.
>
> Use JJWT 0.12.6. Token lifetime: 1 hour. Sign with HS256 using a secret key from `application.yaml`. Include a `jti` (JWT ID) claim for blacklisting.
>
> Create:
> - `JwtService` — generate, validate, extract claims
> - `JwtAuthFilter extends OncePerRequestFilter` — reads `Authorization: Bearer <token>`, validates, sets `SecurityContext`
> - `TokenBlacklist` — in-memory set keyed on `jti`; `logout()` adds the token's jti; `isBlacklisted(jti)` checks it
> - `SecurityConfig` — permit `POST /auth/login` and `POST /users` without auth; require auth on all other endpoints
> - `AuthController` with endpoints:
>   - `POST /auth/login` — accepts `{ username, password }`, returns `{ accessToken }`; throws 401 if credentials invalid; writes an audit log entry
>   - `POST /auth/logout` — requires auth; blacklists the current token; writes an audit log entry
>   - `GET /auth/me` — requires auth; returns `{ id, username, email, fullName, role }`
>
> Load users via `UserDetailsService` backed by `UserRepository`. Encode passwords with `BCryptPasswordEncoder`.

---

### 3. Project Module

**Goal:** Add project management with CRUD endpoints and ADMIN-only restrictions on create and delete.

> Create a Project module.
>
> Entity fields: `id` (BIGINT PK auto), `name` (VARCHAR 100, not null), `description` (VARCHAR 500, not null), `owner` (ManyToOne → User, not null).
>
> Endpoints:
> - `POST /projects` — create project; ADMIN only; `owner` is the authenticated user
> - `GET /projects` — list all projects
> - `GET /projects/{id}` — get project by id
> - `PUT /projects/{id}` — update name/description; any authenticated user
> - `DELETE /projects/{id}` — delete project; ADMIN only
>
> Use `ProjectResponse` DTO with a `from(Project)` factory. Use `ProjectService` interface + `ProjectServiceImpl`. Throw `ResourceNotFoundException` on missing project. Use `@PreAuthorize("hasRole('ADMIN')")` for create and delete. Enable `@EnableMethodSecurity` on `SecurityConfig`. Write an audit log entry (`CREATE`, `UPDATE`, `DELETE`) on every write operation.

---

### 4. Ticket Module

**Goal:** Add ticket management with a forward-only status machine, optimistic locking, due dates, and assignee support.

> Create a Ticket module.
>
> Entity fields: `id`, `title` (not null), `description` (TEXT), `status` (enum: TODO, IN_PROGRESS, IN_REVIEW, DONE), `priority` (enum: LOW, MEDIUM, HIGH, CRITICAL), `type` (enum: BUG, FEATURE, TASK), `project` (ManyToOne), `assignee` (ManyToOne → User, nullable), `dueDate` (LocalDateTime, nullable), `deletedAt` (LocalDateTime, nullable), `version` (Long, `@Version` for optimistic locking).
>
> Status transition rules: forward-only, one step at a time — TODO → IN_PROGRESS → IN_REVIEW → DONE. Reject any other transition with `BadRequestException`.
>
> Endpoints:
> - `POST /tickets` — create ticket in a project; auto-assign to least-loaded developer if `assigneeId` not provided
> - `GET /tickets?projectId=` — list active tickets for a project
> - `GET /tickets/{id}` — get ticket by id
> - `PUT /tickets/{id}` — update ticket fields including status (enforce status machine)
> - `DELETE /tickets/{id}` — soft delete (set `deletedAt`)
>
> Use `TicketResponse` DTO. Write audit log entries on create, update, delete. Throw `ResourceNotFoundException` for missing ticket or project.

---

### 5. Comment Module

**Goal:** Add ticket comments with ownership rules restricting edits and deletes to the author or an ADMIN.

> Create a Comment module.
>
> Entity fields: `id`, `ticket` (ManyToOne, not null), `author` (ManyToOne → User, not null), `content` (TEXT, not null), `createdAt` (LocalDateTime, immutable, set by `@PrePersist`), `updatedAt` (LocalDateTime, set by `@PreUpdate`), `version` (Long, `@Version`).
>
> Endpoints:
> - `POST /tickets/{ticketId}/comments` — create comment; author is the authenticated user
> - `GET /tickets/{ticketId}/comments` — list all comments for a ticket
> - `PUT /tickets/{ticketId}/comments/{commentId}` — update content; only the author or an ADMIN may update; throw 403 otherwise
> - `DELETE /tickets/{ticketId}/comments/{commentId}` — delete comment; only the author or an ADMIN may delete; throw 403 otherwise
>
> Use `CommentResponse` DTO. Write audit log entries on create, update, delete. Throw `ResourceNotFoundException` when ticket or comment is not found.

---

### 6. @Mention Feature

**Goal:** Parse `@username` mentions from comment content, store them as a dedicated entity, and expose a paginated mentions feed per user.

> Add @mention support to comments.
>
> Create a `CommentMention` entity with composite PK `(commentId, userId)` using `@EmbeddedId` and `CommentMentionId`. FK: `comment_id → comments.id ON DELETE CASCADE`, `user_id → users.id ON DELETE CASCADE`. Manage via a Flyway V1 migration (do not let Hibernate create this table).
>
> Mention parsing: extract `@username` tokens from comment content using regex `@([a-zA-Z0-9_]+)`. Look up each username in the DB case-insensitively. Silently ignore unknown usernames — do not throw errors.
>
> On **create**: insert `CommentMention` rows for all valid mentions after the comment is persisted.
> On **update**: diff old vs new mention sets — insert newly added mentions, delete removed mentions, leave unchanged ones alone (do not delete-all-reinsert).
>
> `CommentResponse` must always include a `mentionedUsers` list. Load via `JOIN FETCH` to avoid N+1.
>
> Add `GET /users/{userId}/mentions` — returns paginated comments (ordered by `createdAt DESC`) where the user was mentioned; requires authentication.

---

### 7. Audit Log Module

**Goal:** Add an append-only audit log with independent transaction isolation and a queryable, paginated read endpoint.

> Create an Audit Log module.
>
> Entity fields: `id`, `action` (enum: CREATE, UPDATE, DELETE, RESTORE, AUTO_ASSIGN, AUTO_ESCALATE, LOGIN, LOGOUT), `entityType` (enum: USER, PROJECT, TICKET, COMMENT, ATTACHMENT, DEPENDENCY, AUTH), `entityId` (Long), `actor` (enum: USER, SYSTEM), `performedBy` (Long, nullable — no FK; the log must survive user deletion), `details` (TEXT, nullable), `timestamp` (LocalDateTime, immutable).
>
> `AuditLogService.record()` must run in `@Transactional(propagation = REQUIRES_NEW)` so the audit write commits independently of the calling transaction. `actor` is auto-derived: if `performedBy` is null → SYSTEM, otherwise → USER.
>
> Create `SecurityUtils.getCurrentUserId()` to resolve the current user's ID from `SecurityContextHolder`. Return null for unauthenticated callers.
>
> `AuditLogRepository` must extend only `Repository` and `JpaSpecificationExecutor` — do not expose delete or bulk-update methods.
>
> Manage the `audit_logs` table via Flyway V2 migration. Add indexes on `action`, `entity_type`, `entity_id`, `actor`, `performed_by`.
>
> `GET /audit-logs` — paginated, ordered by `timestamp DESC`. Supports AND-combined query params: `action`, `entityType`, `entityId`, `actor`, `performedBy`. Response shape: `{ data: [...], total, page, pageSize }` — do not use Spring's `Page<T>` wrapper. Requires authentication.

---

### 8. Ticket Dependencies Module

**Goal:** Model blocker relationships between tickets with a composite PK join entity and enforce the rule that a ticket cannot move to DONE while it has unresolved blockers.

> Create a Ticket Dependencies module.
>
> Model the dependency as an explicit join entity `TicketDependency` with composite PK `(ticket_id, blocked_by_id)` using `@EmbeddedId` — not a `@ManyToMany`. Both FK columns cascade delete. Create via Flyway V3 migration.
>
> Validation rules for adding a dependency:
> - A ticket cannot depend on itself → 400
> - Both tickets must exist (not soft-deleted) → 404 if either missing
> - Both tickets must belong to the same project → 400
> - Duplicate dependency → 409
>
> DONE transition check: before a ticket status changes to DONE, query all `TicketDependency` rows for that ticket where `blockedBy.status != DONE`. Do NOT filter by `deletedAt` — soft-deleted blockers that are not yet DONE still count as unresolved. If any unresolved blockers exist, throw `BusinessRuleException("Ticket has unresolved blockers")` → 422.
>
> Endpoints:
> - `POST /tickets/{ticketId}/dependencies` — body: `{ blockedById }` — add dependency
> - `GET /tickets/{ticketId}/dependencies` — list all blockers for a ticket; use `JOIN FETCH` to avoid N+1
> - `DELETE /tickets/{ticketId}/dependencies/{blockedById}` — remove dependency; 404 if not found
>
> Write audit log entries (CREATE, DELETE) with `entityType = DEPENDENCY` on every write.

---

### 9. File Attachments Module

**Goal:** Allow file uploads to be associated with tickets, stored on the local filesystem with UUID-based naming, with strict MIME type and size validation.

> Create a File Attachments module.
>
> Entity fields: `id`, `ticket` (ManyToOne, not null), `filename` (VARCHAR 255 — original client filename), `contentType` (VARCHAR 100), `fileSize` (BIGINT, bytes), `storagePath` (VARCHAR 500 — internal disk path, never returned in any response DTO), `createdAt` (LocalDateTime, immutable). Create via Flyway V4 migration with `ON DELETE CASCADE` FK on `ticket_id`.
>
> Store files at `uploads/{ticketId}/{uuid}.{ext}` relative to the working directory. Create the directory lazily with `Files.createDirectories()` on first upload. Use a UUID for the filename on disk — never use the original filename.
>
> Validation (service layer, before any disk I/O):
> - File size > 10 MB → 400 with message containing "10 MB"
> - MIME type not in `{image/png, image/jpeg, application/pdf, text/plain}` → 400 with message containing "File type not allowed"
>
> Upload atomicity: write file to disk first, then `saveAndFlush` to DB. If the DB insert throws, delete the file from disk before rethrowing.
>
> Delete: remove DB record first, then `Files.deleteIfExists` on the stored path. A missing file on disk is silently ignored — always return 200.
>
> Endpoints:
> - `POST /tickets/{ticketId}/attachments` — multipart upload; `file` field
> - `DELETE /tickets/{ticketId}/attachments/{attachmentId}` — delete attachment; 404 if id not found or does not belong to the given ticket
>
> `AttachmentResponse` DTO: `id, ticketId, filename, contentType, fileSize` — do not include `storagePath`. Write audit log entries with `entityType = ATTACHMENT` on upload and delete.

---

### 10. Soft Delete & Restore Module

**Goal:** Migrate from a boolean deleted flag to a timestamp-based soft delete, implement cascade delete from project to tickets, and add ADMIN-only restore with discrimination by shared timestamp.

> Migrate soft delete from `boolean deleted` to `LocalDateTime deletedAt` for both `Project` and `Ticket`. Null means active; non-null means deleted. Update all repository queries to filter `WHERE deleted_at IS NULL`. Create Flyway V5 migration.
>
> Cascade delete: when deleting a project, capture `LocalDateTime now = LocalDateTime.now()`, set all active tickets' `deletedAt = now`, then set the project's `deletedAt = now`. Use the same shared timestamp for both.
>
> Cascade restore: `POST /projects/{id}/restore` (ADMIN only) restores the project, then finds tickets deleted with that exact same timestamp via `findCascadeDeletedByProjectIdAndDeletedAt(id, projectDeletedAt)` — a custom JPQL query matching `deletedAt = :timestamp`. Tickets individually deleted before (with a different timestamp) are left alone.
>
> Ticket restore guard: if a ticket's parent project is still deleted, restoring the ticket individually returns 400 `"Cannot restore ticket: parent project is deleted"`.
>
> Add endpoints:
> - `GET /tickets/deleted?projectId=` — list soft-deleted tickets for a project; ADMIN only
> - `POST /tickets/{id}/restore` — restore a soft-deleted ticket; ADMIN only
> - `GET /projects/deleted` — list soft-deleted projects; ADMIN only
> - `POST /projects/{id}/restore` — restore a soft-deleted project + cascade restore; ADMIN only
>
> Write RESTORE audit log entries on restore. Handle `AccessDeniedException` explicitly in `GlobalExceptionHandler` → 403 (prevent it being swallowed as 500 by the catch-all handler).

---

### 11. CSV Export & Import Module

**Goal:** Allow bulk export of tickets to CSV and import from CSV, with per-row transactions so a single bad row does not abort the whole batch.

> Add CSV export and import for tickets using Apache Commons CSV.
>
> **Export** `GET /tickets/export?projectId=`:
> - Return only active (`deletedAt IS NULL`) tickets for the project
> - Column order: `id, title, description, status, priority, type, assigneeId` — no projectId column
> - Use a single `LEFT JOIN FETCH t.assignee` query to avoid N+1
> - Write null values as empty strings, never as the string `"null"`
> - `Content-Type: text/csv;charset=UTF-8`, `Content-Disposition: attachment; filename="tickets-{projectId}.csv"`
> - 404 if project not found; empty CSV with header row only if project has no active tickets
>
> **Import** `POST /tickets/import` (multipart: `file`, `projectId`):
> - Parse with `CSVFormat.DEFAULT.withFirstRecordAsHeader().withIgnoreEmptyLines(true).withTrim(true)`
> - Always ignore the `id` column — generate a new ID for every row
> - Always use the `projectId` form field — never read projectId from CSV rows
> - Required per row: `title`, `status`, `priority`, `type`; optional: `description`, `assigneeId`
> - Batch pre-load all unique `assigneeId` values from the file in a single `findAllByIdIn` query before processing rows — do not query per row
> - `importTickets` must NOT be `@Transactional`; each `createTicket()` call runs in its own transaction so a row failure does not affect subsequent rows
> - Return `{ created, failed, errors: [{ row, message }] }` — row numbers start at 1 (header excluded)
> - Write `CREATE` audit log entries with details `"Ticket imported from CSV"` for each successful row

---

### 12. Auto-Escalation Scheduler

**Goal:** Automatically escalate overdue tickets' priorities on a cron schedule, with per-ticket transaction isolation via self-injection to ensure one failure does not abort others.

> Create a `TicketEscalationScheduler` Spring component. Enable `@EnableScheduling` on `IssueFlowApplication`.
>
> Read the cron expression from property `ticket.escalation.cron` (default: `0 * * * * *` — every minute).
>
> Eligible tickets: `dueDate IS NOT NULL AND dueDate < NOW() AND status != DONE AND deletedAt IS NULL` — single JPQL query, no N+1.
>
> Escalation rules (priority only — never touch status):
> - LOW → MEDIUM
> - MEDIUM → HIGH
> - HIGH → CRITICAL + set `isOverdue = true`
> - CRITICAL (ceiling) → `isOverdue = true`, priority unchanged (idempotent)
>
> `isOverdue` must be a persisted `BOOLEAN NOT NULL DEFAULT FALSE` column (Flyway V6 migration), not a computed property. `TicketResponse` reads the stored value directly.
>
> When `PUT /tickets/{id}` explicitly includes a `priority` field, reset `isOverdue` to `false` immediately — the scheduler will re-evaluate from the new priority on the next cycle.
>
> Transaction isolation: `escalateOverdueTickets()` carries no `@Transactional`. Each ticket is processed in a separate `escalateSingleTicket()` method annotated `@Transactional(propagation = REQUIRES_NEW)`. Use self-injection (`@Lazy @Autowired TicketEscalationScheduler self`) to ensure the proxy is invoked so `REQUIRES_NEW` takes effect. A failure on one ticket is logged and skipped; remaining tickets commit independently.
>
> Write `AUTO_ESCALATE` audit log entries with `actor = SYSTEM` and `performedBy = null` for every escalated ticket. Include a human-readable `details` string (e.g. `"Priority escalated from HIGH to CRITICAL (overdue)"`).

---

### 13. Auto-Assignment by Workload

**Goal:** Automatically assign new tickets to the least-loaded developer in the project, using a native SQL query to avoid a circular package dependency.

> Add auto-assignment to `POST /tickets`.
>
> If `assigneeId` is provided in the request, use it directly. If not, auto-assign to the DEVELOPER in the same project with the fewest open tickets (status != DONE, deletedAt IS NULL). Break ties by `u.id ASC`. If no DEVELOPERs exist in the project, leave `assigneeId = null` and do not write an AUTO_ASSIGN audit entry.
>
> Use a **native SQL query** `findDevelopersForAssignment` in `UserRepository` to avoid importing `TicketStatus` into the user package. The query joins `users` and `tickets` using column names as strings.
>
> Use an **interface projection** `UserWorkloadView` with methods `getUserId()`, `getUsername()`, `getOpenTicketCount()` to map the native query result. Spring Data maps column aliases case-insensitively to getter names.
>
> Write two audit entries on auto-assignment: first the CREATE entry, then an AUTO_ASSIGN entry with `actor = SYSTEM`, `performedBy = null`, and details `"Auto-assigned to user {username} (workload: {count} open tickets)"`.
>
> Add `GET /projects/{projectId}/workload` → `List<WorkloadResponse>` ordered by `u.id ASC`. Use a second native query `findDeveloperWorkloadByProject` for this. 404 if project is missing or deleted. Requires authentication; no role restriction. `WorkloadResponse` fields: `userId`, `username`, `openTicketCount`.

---

### 14. Integration Test Infrastructure

**Goal:** Set up a shared integration test base class that runs against a real PostgreSQL container and cleans all data between tests.

> Set up integration test infrastructure for the project.
>
> Create `BaseIntegrationTest`:
> - `@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)`
> - `@AutoConfigureMockMvc`
> - `@BeforeEach cleanDatabase()` using `JdbcTemplate` with explicit `DELETE` statements in FK-safe order: `ticket_dependencies`, `attachments`, `comment_mentions`, `audit_logs`, `comments`, `tickets`, `projects`, `users`
> - Do NOT use `@Transactional` on the base class — RANDOM_PORT runs a real HTTP server; MockMvc requests commit independently in separate threads, and `REQUIRES_NEW` sub-transactions (in `AuditLogService`) conflict with a surrounding test transaction
>
> Create `src/test/resources/application.yaml`:
> - Point at compose.yml PostgreSQL: `localhost:5432/issueflow`
> - `spring.jpa.hibernate.ddl-auto: none` with Flyway enabled (schema managed by migrations)
> - Set `ticket.escalation.cron: "0 0 0 29 2 ?"` (impossible date so the scheduler never fires during tests)
>
> All integration test classes extend `BaseIntegrationTest`. Each test class sets up its own entities in `@BeforeEach` via repositories or by calling API endpoints directly through MockMvc.

---

## Testing Prompts

---

### Audit Log Integration Tests

> Write integration tests for the Audit Log module. Extend `BaseIntegrationTest`.
>
> Setup: save an admin and a developer user via repository; save a project and a ticket via repository; login as admin to get a JWT token.
>
> Assertion rules: every test that checks an audit log entry must assert all 5 fields exactly — `action`, `entityType`, `entityId`, `actor`, `performedBy`. Always filter by `entityId` when querying `/audit-logs` to isolate the specific entry. For timestamp: assert non-null, parse as `LocalDateTime` (ISO-8601 string), assert within the last 10 seconds.
>
> Test groups:
> - **User**: CREATE audit when `POST /users` is called with auth (actor=USER, performedBy=adminId); CREATE audit when called without auth (actor=SYSTEM, performedBy=null); UPDATE audit on `PUT /users/{id}`; DELETE audit on `DELETE /users/{id}`
> - **Project**: CREATE, UPDATE, DELETE, RESTORE audit entries
> - **Ticket**: CREATE, UPDATE, DELETE, RESTORE audit entries
> - **Comment**: CREATE, UPDATE, DELETE audit entries
> - **Auth**: LOGIN audit on `POST /auth/login`; LOGOUT audit on `POST /auth/logout`; actor=USER and performedBy=userId for both
> - **AUTO_ASSIGN**: verify SYSTEM actor and null performedBy when a ticket is auto-assigned
> - **Filter tests**: filter by `action`; by `entityType`; by `entityId`; by `actor=SYSTEM`; by `performedBy`; combined multi-filter
> - **Pagination**: page 0 and page 1 return expected subsets
> - **Security**: `GET /audit-logs` without auth returns 401
> - **Response shape**: verify `{ data, total, page, pageSize }` structure

---

### Ticket Dependencies Integration Tests

> Write integration tests for the Ticket Dependencies module. Extend `BaseIntegrationTest`.
>
> Setup: save one admin user; create two projects (`project`, `otherProject`); create `ticketA` and `ticketB` both in `project` with status TODO; create `ticketC` in `otherProject`. Login as admin.
>
> Test groups:
>
> **Add dependency:**
> - Success: `POST /tickets/{ticketA}/dependencies` with `{ blockedById: ticketB }` → 200; assert `DependencyResponse` shape
> - 404 when ticket does not exist
> - 404 when blocker does not exist
> - 400 when ticket depends on itself
> - 400 when tickets belong to different projects
> - 409 when dependency already exists
>
> **Get dependencies:**
> - Returns list of blockers with correct shape after adding a dependency
> - Returns empty list when no dependencies exist
> - 404 when ticket does not exist
>
> **Remove dependency:**
> - 200 on successful removal; verify dependency is gone via GET
> - 404 when dependency does not exist
>
> **DONE transition blocked by dependencies:**
> - Add ticketB as blocker of ticketA; advance ticketA to IN_REVIEW; attempt to advance to DONE → 422 with message containing "unresolved blockers"
> - After advancing ticketB to DONE: ticketA can now advance to DONE → 200
> - Soft-delete ticketB (DELETE /tickets/{ticketB}) without completing it; attempt to advance ticketA to DONE → 422 (soft-deleted blocker with status TODO still blocks). This is the most critical test — soft delete does not remove the blocker constraint.
>
> **Audit log:**
> - Adding a dependency writes a CREATE audit entry with entityType=DEPENDENCY and entityId=ticketA
> - Removing a dependency writes a DELETE audit entry
>
> **Auth:** all write endpoints return 401 without a token

---

### Attachments Integration Tests

> Write integration tests for the Attachments module. Extend `BaseIntegrationTest`.
>
> Setup: save admin user; save project; save two tickets (`ticket`, `otherTicket`) in the project. Login as admin.
>
> Add `@AfterEach cleanUploads()`: delete the entire `uploads/` directory recursively using `Files.walkFileTree` with `SimpleFileVisitor`.
>
> Test groups:
>
> **Upload — allowed types:**
> - PNG upload returns 200 with correct response shape: `id` (number), `ticketId`, `filename`, `contentType`, `fileSize`; `storagePath` must NOT appear in the response (`jsonPath("$.storagePath").doesNotExist()`)
> - JPEG, PDF, and text/plain uploads each return 200 with correct contentType
>
> **Upload — rejections:**
> - `application/octet-stream` → 400 with message containing "File type not allowed"
> - `text/html` → 400 with message containing "File type not allowed"
> - File size > 10 MB → 400 with message containing "10 MB"
> - Non-existent ticketId → 404
> - No auth token → 401
>
> **File stored on disk:**
> - After upload, use JDBC (`SELECT storage_path FROM attachments WHERE id = ?`) to get the stored path
> - Assert the file exists on disk
> - Assert the filename on disk is NOT the original filename
> - Assert the filename matches UUID pattern: `[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\\.ext`
>
> **Delete:**
> - Upload then delete → 200; assert file no longer exists on disk
> - Non-existent attachmentId → 404
> - Attachment exists but DELETE uses wrong ticketId (`otherTicket`) → 404
> - Manually delete file from disk, then call DELETE → still 200 (missing file is acceptable)
> - No auth token → 401
>
> **Audit log:**
> - Upload writes a CREATE audit entry with entityType=ATTACHMENT and performedBy=adminId
> - Delete writes a DELETE audit entry with entityType=ATTACHMENT and performedBy=adminId
