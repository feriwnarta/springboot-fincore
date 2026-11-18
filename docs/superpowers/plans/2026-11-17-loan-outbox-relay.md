# Loan Outbox Relay Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task with verification checkpoints.

**Goal:** Publish committed loan outbox envelopes to Kafka with bounded polling, lease-based claims, at-least-once delivery, retry metadata, and test evidence against PostgreSQL.

**Architecture:** Keep the existing domain `LoanOutboxRepository` for atomic append operations. Add application ports for relay claiming/updating and publishing so the use case remains independent of JDBC and Spring Kafka. A PostgreSQL V4 migration adds `IN_FLIGHT` lease fields; the relay claims rows in a short transaction, publishes outside the transaction, and updates each result using `event_id + claim_token`.

**Tech Stack:** Java 21, Spring Boot 4.1.2, Spring JDBC, Spring Kafka, PostgreSQL/Flyway, SLF4J/MDC, JUnit 5, Testcontainers PostgreSQL/Kafka.

---

### Task 1: Add relay dependencies, configuration, and schema lease fields

**Files:**
- Modify: `services/loan-service/pom.xml`
- Modify: `services/loan-service/src/main/resources/application.yml`
- Modify: `services/loan-service/src/main/resources/application-test.yml`
- Create: `services/loan-service/src/main/resources/db/migration/V4__loan_outbox_relay_claims.sql`

- [x] **Step 1: Add the failing schema/config expectations**

Create `services/loan-service/src/test/java/com/fincore/loan/infrastructure/persistence/jdbc/LoanOutboxRelaySchemaIT.java` with a PostgreSQL Testcontainers database and assertions that `outbox_events` exposes `IN_FLIGHT`, `claim_token`, and `claimed_at` after Flyway runs.

Run:

```bash
mvn -pl services/loan-service -am -Pintegration-tests -Dit.test=LoanOutboxRelaySchemaIT -Dfailsafe.failIfNoSpecifiedTests=false verify
```

Expected: FAIL because V4 and the Kafka/Testcontainers dependencies are not present yet.

- [x] **Step 2: Add dependencies and migration**

Add test-scoped `org.testcontainers:testcontainers-kafka` and main `org.springframework.kafka:spring-kafka` to `services/loan-service/pom.xml`. Add V4 with:

```sql
ALTER TABLE outbox_events DROP CONSTRAINT IF EXISTS outbox_events_status_check;
ALTER TABLE outbox_events
    ADD CONSTRAINT outbox_events_status_check
    CHECK (status IN ('PENDING', 'IN_FLIGHT', 'PUBLISHED', 'FAILED'));

ALTER TABLE outbox_events ADD COLUMN claim_token UUID;
ALTER TABLE outbox_events ADD COLUMN claimed_at TIMESTAMPTZ;

CREATE INDEX idx_loan_outbox_in_flight_lease
    ON outbox_events (claimed_at, event_id)
    WHERE status = 'IN_FLIGHT';
```

Add loan-service properties with safe defaults:

```yaml
spring:
  kafka:
    bootstrap-servers: ${KAFKA_BOOTSTRAP_SERVERS:localhost:9092}
    producer:
      key-serializer: org.apache.kafka.common.serialization.StringSerializer
      value-serializer: org.apache.kafka.common.serialization.StringSerializer

fincore:
  kafka:
    loan-topic: ${FINCORE_LOAN_EVENTS_TOPIC:fincore.loan.events.v1}
    outbox-relay:
      enabled: ${FINCORE_OUTBOX_RELAY_ENABLED:false}
      fixed-delay: ${FINCORE_OUTBOX_RELAY_FIXED_DELAY:1000ms}
      batch-size: ${FINCORE_OUTBOX_RELAY_BATCH_SIZE:50}
      claim-lease: ${FINCORE_OUTBOX_RELAY_CLAIM_LEASE:30s}
      send-timeout: ${FINCORE_OUTBOX_RELAY_SEND_TIMEOUT:5s}
```

Set `spring.kafka.bootstrap-servers` to a harmless test value in `application-test.yml`; the scheduler remains disabled there.

- [x] **Step 3: Run the schema test**

Run:

```bash
mvn -pl services/loan-service -am -Pintegration-tests -Dit.test=LoanOutboxRelaySchemaIT -Dfailsafe.failIfNoSpecifiedTests=false verify
```

Expected: PASS once Docker is available; Flyway reports V1 through V4.

### Task 2: Define relay application ports and message model

**Files:**
- Create: `services/loan-service/src/main/java/com/fincore/loan/application/query/LoanOutboxMessage.java`
- Create: `services/loan-service/src/main/java/com/fincore/loan/application/port/LoanOutboxRelayRepository.java`
- Create: `services/loan-service/src/main/java/com/fincore/loan/application/port/LoanEventPublisher.java`
- Create: `services/loan-service/src/main/java/com/fincore/loan/application/service/LoanOutboxRelay.java`
- Create: `services/loan-service/src/main/java/com/fincore/loan/config/LoanOutboxRelayProperties.java`
- Create: `services/loan-service/src/test/java/com/fincore/loan/application/service/LoanOutboxRelayTest.java`

- [x] **Step 1: Write unit tests for the use case**

Use fake ports and fixed UUID/Instant values to cover:

```java
@Test
void publishesClaimedEventsAndMarksThemPublished() { /* assert publish and markPublished */ }

@Test
void recordsFailureAndContinuesWithTheNextEvent() { /* first publish fails; second succeeds */ }

@Test
void doesNothingWhenNoEventIsClaimed() { /* assert no publisher call */ }
```

Run:

```bash
mvn -pl services/loan-service -Dtest=LoanOutboxRelayTest test
```

Expected: FAIL because the ports and relay do not exist.

- [x] **Step 2: Define framework-independent types and ports**

`LoanOutboxMessage` must contain `eventId`, `eventType`, `businessReference`, `payloadJson`, `claimToken`, and `claimedAt`. The repository port must expose:

```java
List<LoanOutboxMessage> claimBatch(int limit, Instant now, Duration lease, UUID claimToken);
void markPublished(UUID eventId, UUID claimToken, Instant publishedAt);
void markFailed(UUID eventId, UUID claimToken, Instant failedAt, String errorMessage);
```

The publisher port must expose one synchronous contract without Spring types:

```java
void publish(LoanOutboxMessage message) throws Exception;
```

`LoanOutboxRelayProperties` validates batch size `1..500`, positive lease, positive send timeout, and exposes the enabled flag.

- [x] **Step 3: Implement relay orchestration**

`LoanOutboxRelay.runOnce()` must:

1. Return immediately when disabled.
2. Generate one claim token and call `claimBatch`.
3. Publish each message independently.
4. Mark success with the current `Instant`.
5. On exception, sanitize/truncate the message to 1,000 characters, log only event metadata, and call `markFailed`.
6. Continue after an individual failure.

Use SLF4J with MDC fields for event ID, event type, business reference, and correlation ID when the envelope provides one; clear fields in `finally`.

- [x] **Step 4: Run unit tests**

```bash
mvn -pl services/loan-service -Dtest=LoanOutboxRelayTest test
```

Expected: PASS for all relay behavior tests.

### Task 3: Implement PostgreSQL claim and result adapter

**Files:**
- Create: `services/loan-service/src/main/java/com/fincore/loan/infrastructure/persistence/jdbc/JdbcLoanOutboxRelayRepository.java`
- Create: `services/loan-service/src/test/java/com/fincore/loan/infrastructure/persistence/jdbc/JdbcLoanOutboxRelayRepositoryIT.java`
- Modify: `services/loan-service/src/test/resources/loan-postgres-report-fixture.sql` only if the shared fixture needs an outbox row.

- [x] **Step 1: Write PostgreSQL integration tests**

Cover these cases:

```java
@Test
void claimsPendingRowsInCreatedOrderAndSetsLease() { /* assert IN_FLIGHT + token */ }

@Test
void reclaimsExpiredInFlightRows() { /* assert expired claim becomes claimable */ }

@Test
void staleClaimCannotMarkAnotherRelayAttemptPublished() { /* update count is zero */ }
```

Add a two-thread test where both repositories claim the same pending row and assert exactly one receives it.

- [x] **Step 2: Implement atomic PostgreSQL claim SQL**

Use one `UPDATE ... FROM (SELECT ... FOR UPDATE SKIP LOCKED LIMIT ?)` statement that:

- selects `PENDING`, `FAILED`, or expired `IN_FLIGHT` rows;
- orders by `created_at, event_id`;
- sets `status='IN_FLIGHT'`, `claim_token=?`, and `claimed_at=?`;
- returns the selected event columns.

Use `NamedParameterJdbcTemplate` or positional `JdbcTemplate` parameters only; never interpolate batch size or timestamps into SQL text.

- [x] **Step 3: Implement guarded result updates**

Use `WHERE event_id = ? AND claim_token = ? AND status = 'IN_FLIGHT'` for both success and failure updates. Success sets `PUBLISHED`, `published_at`, and clears claim fields. Failure sets `FAILED`, increments `retry_count`, stores a maximum 1,000-character error, and clears claim fields.

- [x] **Step 4: Run the PostgreSQL adapter tests**

```bash
mvn -pl services/loan-service -am -Pintegration-tests -Dit.test=JdbcLoanOutboxRelayRepositoryIT -Dfailsafe.failIfNoSpecifiedTests=false verify
```

Expected: PASS against PostgreSQL with Flyway V1 through V4.

### Task 4: Implement Kafka publisher and scheduler

**Files:**
- Create: `services/loan-service/src/main/java/com/fincore/loan/infrastructure/messaging/kafka/KafkaLoanEventPublisher.java`
- Create: `services/loan-service/src/main/java/com/fincore/loan/config/LoanKafkaProperties.java`
- Create: `services/loan-service/src/main/java/com/fincore/loan/infrastructure/messaging/kafka/LoanOutboxRelayScheduler.java`
- Create: `services/loan-service/src/test/java/com/fincore/loan/infrastructure/messaging/kafka/KafkaLoanEventPublisherIT.java`

- [x] **Step 1: Write the publisher contract test**

Use a Kafka Testcontainers broker and assert that the publisher sends:

- topic `fincore.loan.events.v1`;
- key equal to `eventId.toString()`;
- value equal to the persisted `payloadJson` without reserialization or mutation.

- [x] **Step 2: Implement Kafka configuration**

Register `KafkaTemplate<String, String>` with string serializers. `KafkaLoanEventPublisher` sends to the configured topic and waits up to `send-timeout` for the broker acknowledgement. It must propagate the failure to the relay and must not mark the outbox row itself.

- [x] **Step 3: Implement the scheduled trigger**

Use `@ConditionalOnProperty(name = "fincore.kafka.outbox-relay.enabled", havingValue = "true")` and `@Scheduled(fixedDelayString = "${fincore.kafka.outbox-relay.fixed-delay:1000ms}")`. The scheduler invokes `LoanOutboxRelay.runOnce()` and does not contain persistence or Kafka logic.

- [x] **Step 4: Run publisher integration tests**

```bash
mvn -pl services/loan-service -am -Pintegration-tests -Dit.test=KafkaLoanEventPublisherIT -Dfailsafe.failIfNoSpecifiedTests=false verify
```

Expected: PASS with one consumed message matching topic, key, and payload.

### Task 5: Wire the relay and update documentation

**Files:**
- Modify: `services/loan-service/src/main/java/com/fincore/loan/LoanServiceApplication.java` only if component scanning/config import requires it.
- Modify: `README.md`
- Modify: `docs/FINCORE_IMPLEMENTATION_PLAN.md`
- Modify: `docs/sql-performance.md` only if the test command reference needs a cross-link.

- [x] **Step 1: Verify application context wiring**

Run the normal loan application context test with the relay disabled and assert startup does not require Kafka:

```bash
mvn -pl services/loan-service -Dtest=LoanServiceApplicationTests test
```

- [x] **Step 2: Document operation**

Add the configuration keys, the at-least-once/idempotency behavior, and the command for the Kafka integration profile to `README.md`. Mark only the completed M2.1 checklist items in `docs/FINCORE_IMPLEMENTATION_PLAN.md`; leave acknowledgement, DLQ, and replay items unchecked.

- [x] **Step 3: Run all verification**

```bash
mvn -am test
mvn clean verify
mvn -pl services/loan-service -am -Pintegration-tests verify
```

Expected: regular reactor tests and package verification pass without Kafka; the integration profile passes with Docker and the Kafka Testcontainers image available.
