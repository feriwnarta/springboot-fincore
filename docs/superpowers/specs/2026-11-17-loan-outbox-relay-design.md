# Loan Outbox Relay Design

## Goal

Deliver pending loan-service outbox events to Kafka reliably without coupling the loan transaction to Kafka availability. The first slice covers the relay and its delivery contract; ledger acknowledgement remains the next M2 slice.

## Scope

In scope:

- Poll `outbox_events` in bounded batches using PostgreSQL row locking with `FOR UPDATE SKIP LOCKED`.
- Claim rows with a short lease (`IN_FLIGHT`, `claim_token`, and `claimed_at`) before publishing so another relay instance cannot publish the same active claim concurrently.
- Publish the stored JSON envelope to the configured Kafka loan topic using `event_id` as the Kafka message key.
- Mark an event `PUBLISHED` only after Kafka acknowledges the send.
- Keep failed events retryable with incremented `retry_count` and bounded `last_error` text.
- Preserve the existing event envelope, decimal-string money representation, correlation ID, causation ID, and business reference.
- Add unit tests for selection, success, failure, and bounded error persistence.
- Add Kafka integration coverage using Testcontainers or the existing Kafka test support when the broker test slice is introduced.

Out of scope for this slice:

- Funding simulator behavior.
- Ledger journal posting and acknowledgement events.
- DLQ/replay commands and operational dashboards.
- Repayment events and the ledger-service outbox relay.

## Architecture

`LoanOutboxRelay` is an application-level scheduled use case. It depends on an application port for claiming and updating outbox rows and on a publisher port for Kafka delivery. JDBC and Spring Kafka remain infrastructure adapters; the application service does not depend directly on `JdbcTemplate`, Kafka producer classes, or serialization details.

The existing `outbox_events` table remains the source of truth. A relay attempt is at-least-once: a process crash after broker acknowledgement but before the database update may publish the same `event_id` again. Consumers must therefore remain idempotent through their inbox/processed-event uniqueness constraints.

Migration V4 extends `outbox_events.status` with `IN_FLIGHT` and adds nullable `claim_token UUID` and `claimed_at TIMESTAMPTZ`. The pending index continues to cover `PENDING` and `FAILED`; a separate index covers `IN_FLIGHT, claimed_at` for lease reclamation. Claim and result updates match both `event_id` and `claim_token`.

## Delivery flow

1. A scheduled trigger invokes the relay with a configured batch size.
2. The JDBC adapter selects rows with status `PENDING` or retryable `FAILED`, ordered by `created_at, event_id`, and locks them with `FOR UPDATE SKIP LOCKED`.
3. Each row is published using `event_id` as the Kafka key and the stored `payload` as the value.
4. On broker acknowledgement, the row is updated to `PUBLISHED` with `published_at` and its retry metadata preserved.
5. On a synchronous publish failure, the row remains retryable, `retry_count` is incremented, and the sanitized exception message is truncated to the database limit.
6. A failed row must not block later rows in the same batch.

The database claim/update transaction must not remain open while waiting for Kafka. The implementation therefore claims a bounded batch with a short transaction, publishes outside that transaction, and performs one short update transaction per result. `IN_FLIGHT` claims have a lease timeout; a crashed relay's expired claims are eligible for reclamation. This permits multiple relay instances to share work without holding database locks across network calls.

## Configuration contract

The loan service exposes configuration through `fincore.kafka` properties:

- `bootstrap-servers`: Kafka bootstrap address.
- `loan-topic`: default `fincore.loan.events.v1`.
- `outbox-relay.enabled`: default `false` for local/test safety until Kafka is configured.
- `outbox-relay.fixed-delay`: default `1000ms`.
- `outbox-relay.batch-size`: default `50`, with validation between `1` and `500`.
- `outbox-relay.claim-lease`: default `30s`, with a positive duration required.

No credentials, authorization headers, or raw request bodies may be copied into `last_error` or log messages.

## Failure and observability rules

- Kafka unavailable: leave the business state and outbox row intact; retry on the next schedule.
- Relay crash or lease expiry: reclaim `IN_FLIGHT` rows after `claim-lease` and publish them again safely.
- Serialization/configuration failure: record a bounded error and emit an error log with `eventId`, `eventType`, `businessReference`, and correlation ID only.
- Duplicate delivery: accepted and handled by consumer idempotency; the relay does not attempt distributed coordination with the consumer.
- Empty batch: return without logging at INFO level.
- Each attempt uses SLF4J and MDC context, and clears relay-specific MDC fields in `finally`.

## Testing strategy

- Unit test the application relay with fake ports:
  - no rows produces no publish;
  - successful publish marks the event `PUBLISHED`;
  - failed publish updates retry metadata and continues to the next event;
  - error text is truncated and does not contain sensitive payload content.
- JDBC repository test against H2 verifies parameter binding, ordering, status filtering, and update statements.
- PostgreSQL/Testcontainers coverage verifies `FOR UPDATE SKIP LOCKED`, JSONB payload retrieval, lease reclamation, and concurrent claim behavior.
- Kafka integration coverage verifies the topic/key/value contract and broker acknowledgement path.

## Acceptance criteria

- A committed loan outbox row is eventually published when Kafka becomes available.
- Kafka downtime does not roll back or lose the loan business transaction.
- Two relay instances do not claim the same pending row concurrently in the same database transaction.
- A broker acknowledgement followed by a process crash is safe because event consumers deduplicate by `event_id`.
- Unit, JDBC, PostgreSQL, and Kafka tests document the behavior and pass in their respective Maven profiles.
