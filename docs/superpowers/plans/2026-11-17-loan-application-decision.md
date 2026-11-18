# Loan Application Approval and Rejection Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add idempotent approve and reject commands for submitted loan applications with optimistic locking, audit trail, and transactional outbox events.

**Architecture:** Extend the existing loan application service with a command transition method. The service will claim an operation-specific idempotency key, atomically update the application using `id/version/status` guards, then append audit and outbox records in the same transaction. REST controllers will expose explicit command endpoints and map all transport data through DTOs.

**Tech Stack:** Java 21, Spring Boot 4.1.2, Spring MVC, Jakarta Validation, Spring JDBC, PostgreSQL, Flyway, JUnit 5, Mockito, MockMvc.

---

### Task 1: Define transition DTOs, commands, and persistence ports

**Files:**
- Create: `services/loan-service/src/main/java/com/fincore/loan/api/dto/RejectLoanApplicationRequest.java`
- Create: `services/loan-service/src/main/java/com/fincore/loan/application/LoanApplicationDecisionCommand.java`
- Create: `services/loan-service/src/main/java/com/fincore/loan/domain/port/LoanApplicationTransitionRepository.java`

- [x] **Step 1: Write failing domain/port compile tests**

Add a service test that constructs an approval and rejection decision command and expects the service to expose `approve` and `reject` methods. Add a repository contract test fixture that expects a transition method accepting application ID, expected version, target status, and transition timestamp.

- [x] **Step 2: Run the focused tests to verify they fail**

Run:

```bash
export JAVA_HOME=/opt/homebrew/Cellar/openjdk@21/21.0.9/libexec/openjdk.jdk/Contents/Home
export PATH="$JAVA_HOME/bin:$PATH"
mvn -pl services/loan-service -am -Dtest=LoanApplicationServiceTest test
```

Expected: compilation failure because the decision command and transition port do not exist.

- [x] **Step 3: Add the minimal types**

Create `RejectLoanApplicationRequest` with `@NotBlank String reasonCode` and `@NotBlank @Size(max=500) String reason`. Create `LoanApplicationDecisionCommand` with application ID, idempotency key, actor ID, actor role, correlation ID, request ID, reason code, and reason. Create `LoanApplicationTransitionRepository` with `boolean transition(UUID id, long expectedVersion, LoanApplicationStatus targetStatus, Instant transitionedAt)`.

Keep `LoanApplication` unchanged as the authoritative state model; rejection reason is event/audit metadata and is not persisted as a mutable application column in this slice.

- [x] **Step 4: Run compilation again**

Run the focused test command above. Expected: the new types compile, while behavior tests remain failing until the service is implemented.

### Task 2: Add service transition behavior with TDD

**Files:**
- Modify: `services/loan-service/src/test/java/com/fincore/loan/application/LoanApplicationServiceTest.java`
- Modify: `services/loan-service/src/main/java/com/fincore/loan/application/LoanApplicationService.java`

- [x] **Step 1: Add failing tests for approval and rejection**

Cover these behaviors individually:

1. `approve` reads a submitted application, transitions it to `APPROVED` version 2, writes `APPROVE` audit and `LoanApproved` outbox, and marks idempotency success.
2. `reject` reads a submitted application, transitions it to `REJECTED` version 2, writes `REJECT` audit containing reason metadata and `LoanRejected` outbox, and marks idempotency success.
3. Missing application throws `ResourceNotFoundException` without side effects.
4. An already approved/rejected application throws `ConflictException` without side effects.
5. Same decision key and fingerprint replays the application without duplicate audit/outbox.
6. Same decision key with changed reason or command identity throws `ConflictException`.

- [x] **Step 2: Run the service tests and verify the intended failures**

Run:

```bash
mvn -pl services/loan-service -am -Dtest=LoanApplicationServiceTest test
```

Expected: the new transition tests fail because no service methods or transition repository wiring exist; the existing submit tests remain green.

- [x] **Step 3: Implement the minimal service transition method**

Add operation constants `APPROVE_LOAN_APPLICATION` and `REJECT_LOAN_APPLICATION`. Implement `approve(LoanApplicationDecisionCommand)` and `reject(LoanApplicationDecisionCommand)` through one private `decide` method:

```java
@Transactional
public LoanApplication approve(LoanApplicationDecisionCommand command) {
    return decide(command, LoanApplicationStatus.APPROVED, "APPROVE", "LoanApproved");
}
```

The method must:

- Load by ID or throw `ResourceNotFoundException`.
- Require status `SUBMITTED` or throw `ConflictException`.
- Claim idempotency using caller scope `applicationId.toString()` and an operation-specific fingerprint.
- Update with expected version `application.version()` and fail as `ConflictException` if the guarded update returns false.
- Build the next application with version + 1, using `approvedAt = Instant.now()` for approval and `rejectedAt = Instant.now()` for rejection while preserving the other terminal timestamp as `null`.
- Append audit and outbox entries before marking idempotency success.
- Put rejection reason in sanitized JSON metadata and the rejection event payload; never log or return raw request bodies.
- Replay a successful identical decision by loading and returning the application without writing duplicate side effects.

- [x] **Step 4: Run all loan service tests**

Run:

```bash
mvn -pl services/loan-service -am test
```

Expected: all existing and new service tests pass.

### Task 3: Implement JDBC transition persistence

**Files:**
- Create: `services/loan-service/src/main/java/com/fincore/loan/infrastructure/JdbcLoanApplicationTransitionRepository.java`
- Modify: `services/loan-service/src/main/java/com/fincore/loan/infrastructure/JdbcLoanApplicationRepository.java`

- [x] **Step 1: Add a failing repository integration test**

Extend the H2 repository test or add a focused test to assert that a submitted row changes to `APPROVED` only when both ID, expected version, and `SUBMITTED` status match, and that a stale version returns false.

- [x] **Step 2: Run the repository test and verify it fails**

Run:

```bash
mvn -pl services/loan-service -am -Dtest=JdbcLoanApplicationRepositoryTest test
```

Expected: failure because the transition repository is not implemented.

- [x] **Step 3: Implement guarded PostgreSQL/H2-compatible SQL**

Use:

```sql
UPDATE loan_applications
SET status = ?, version = version + 1,
    approved_at = CASE WHEN ? = 'APPROVED' THEN ? ELSE approved_at END,
    rejected_at = CASE WHEN ? = 'REJECTED' THEN ? ELSE rejected_at END
WHERE id = ? AND version = ? AND status = 'SUBMITTED'
```

Bind timestamps as `java.sql.Timestamp` and return `updatedRows == 1`. Update the application row mapper if necessary so timestamps remain `Instant` values.

- [x] **Step 4: Run repository and loan tests**

Run:

```bash
mvn -pl services/loan-service -am test
```

Expected: all tests pass.

### Task 4: Add controller endpoints and contract tests

**Files:**
- Modify: `services/loan-service/src/main/java/com/fincore/loan/api/LoanApplicationController.java`
- Modify: `services/loan-service/src/test/java/com/fincore/loan/api/LoanApplicationControllerTest.java`

- [x] **Step 1: Add failing MockMvc tests**

Add tests for:

- `POST /{id}/approve` returning `200`, standard envelope, and `APPROVED` status.
- `POST /{id}/reject` accepting valid DTO and returning `200` with `REJECTED` status.
- Invalid reject body returning `400 VALIDATION_ERROR`.
- Missing `Idempotency-Key` returning `400 MISSING_HEADER`.

- [x] **Step 2: Run controller tests and verify failures**

Run:

```bash
mvn -pl services/loan-service -am -Dtest=LoanApplicationControllerTest test
```

Expected: 404/405 or compilation failures because the mappings do not exist.

- [x] **Step 3: Implement explicit command mappings**

Add `@PostMapping("/{applicationId}/approve")` with no request body and `@PostMapping("/{applicationId}/reject")` with `@Valid RejectLoanApplicationRequest`. Require `Idempotency-Key`, apply the same actor defaults as submit, create `LoanApplicationDecisionCommand`, and return `ApiResponse<LoanApplicationResponse>` with HTTP 200.

- [x] **Step 4: Run the full loan service test suite**

Run:

```bash
mvn -pl services/loan-service -am test
```

Expected: all tests pass.

### Task 5: Update documentation and verify runtime behavior

**Files:**
- Modify: `README.md`
- Modify: `docs/FINCORE_IMPLEMENTATION_PLAN.md`

- [x] **Step 1: Document approve/reject contracts and state rules**

Add request examples, required headers, response statuses, and the `SUBMITTED -> APPROVED/REJECTED` transition rules. Mark approval/rejection state guards and decision contract tests complete; keep Kafka relay and ledger Saga tasks pending.

- [x] **Step 2: Run full verification**

Run:

```bash
mvn clean verify
```

Expected: `BUILD SUCCESS` for all five reactor modules.

- [x] **Step 3: Run PostgreSQL smoke test**

Start loan service with Java 21 and `LOAN_DB_USERNAME=feriwinarta`. Submit a fresh application, approve it with a fresh key, and verify:

- HTTP 200 and `APPROVED`, version 2.
- Replaying the same approval key returns the same application without duplicate side effects.
- Approving again with a different key returns `409 CONFLICT`.
- PostgreSQL contains one approval audit event and one `LoanApproved` outbox event.

Repeat with a second fresh application and rejection body, verifying `REJECTED`, rejection reason in the outbox payload, and one rejection audit/outbox record.

- [x] **Step 4: Stop the local service and record evidence**

Stop the process, verify port 8081 is closed, and record test/build/smoke results in the final handoff.
