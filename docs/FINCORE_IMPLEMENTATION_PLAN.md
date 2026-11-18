# FinCore MVP Implementation Plan

**Status:** Baseline plan for execution  
**Date:** 2026-11-17  
**Primary specification:** [FINCORE_MVP_SPECIFICATION_v1.1.md](./FINCORE_MVP_SPECIFICATION_v1.1.md)  
**Current baseline:** Java 21, Maven 3.10.1, Spring Boot 4.1.2, three compilable Spring Boot services.  
**Gateway decision:** Kong Gateway as a third-party Docker infrastructure container; it does not become a fourth Spring Boot business service.

---

## 1. Target architecture

```text
Client
  |
  v
Kong Gateway :8000
  |-- /api/v1/loan-products, /api/v1/loan-quotes,
  |   /api/v1/loan-applications -> loan-service:8081
  |-- /api/v1/loans/**       -> loan-service:8081
  |-- /api/v1/reports/loans  -> loan-service:8081
  |-- /api/v1/savings/**     -> ledger-service:8082
  |-- /api/v1/journals/**    -> ledger-service:8082
  |-- /api/v1/search/**      -> search-service:8083
  `-- GET /api/v1/loans/{loanId}/activity -> search-service:8083

Keycloak :8180 -> issues JWTs that all three services validate
loan-service  -> loan_db PostgreSQL
ledger-service -> ledger_db PostgreSQL
search-service -> Elasticsearch + MongoDB
loan-service  <-> Redis shared cache
loan-service  -> Kafka -> ledger-service
ledger-service -> Kafka -> loan-service
Kafka -> search-service projections and Kafka Streams
```

Each Spring Boot service uses **Hexagonal Architecture**:

```text
api/controller -> application/use-case -> domain -> port -> infrastructure adapter
```

Rules:

- Controllers validate transport input and delegate; they do not own transactions.
- Application services own transaction boundaries and orchestration.
- Domain objects contain financial rules and use `BigDecimal`; they do not call Kafka, Redis, or databases.
- Infrastructure adapters implement ports for PostgreSQL, Kafka, Redis, Elasticsearch, MongoDB, and clock/security concerns.
- No service accesses another service’s database directly.
- Search stores and Redis are never authoritative for money, approval, funding status, repayment outstanding, or savings balance.
- Entity, domain model, persistence model, event payload, request DTO, and response DTO are different types. No JPA/document entity is returned directly from a controller.
- FE/BE API contracts are defined by DTOs and OpenAPI schemas, not inferred from database entities.

## 2. Dependency baseline

The root POM remains the version authority. All versions must be pinned through the Spring Boot BOM or an explicit root property; no dependency may use `latest`.

### 2.1 Dependencies by concern

| Concern | Dependency | Services | Timing |
|---|---|---|---|
| REST | `spring-boot-starter-web` | All | Already present |
| Validation | `spring-boot-starter-validation` | All | Already present |
| Health/metrics | `spring-boot-starter-actuator` | All | Already present |
| Lombok | `org.projectlombok:lombok` | All, optional | Foundation |
| OpenAPI/Swagger UI | `org.springdoc:springdoc-openapi-starter-webmvc-ui` 3.2.x | All | API foundation |
| SQL transaction/repository | `spring-boot-starter-jdbc`, `spring-boot-starter-data-jpa` | Loan, Ledger | Persistence |
| PostgreSQL | `org.postgresql:postgresql` | Loan, Ledger | Persistence |
| Migrations | `org.flywaydb:flyway-core` plus PostgreSQL support if required by the selected Flyway release | Loan, Ledger | Persistence |
| Kafka client | `org.springframework.boot:spring-boot-starter-kafka` | All | Event flow |
| Kafka Streams | `org.apache.kafka:kafka-streams` | Search | Analytics |
| Redis | `spring-boot-starter-data-redis` | Loan | Cache/data-grid |
| Elasticsearch | `spring-boot-starter-data-elasticsearch` | Search | Projection |
| MongoDB | `spring-boot-starter-data-mongodb` | Search | Activity projection |
| Unit/integration test | `spring-boot-starter-test` | All | Already present |
| Containers | `org.testcontainers:junit-jupiter`, PostgreSQL, Kafka, Redis, Elasticsearch, MongoDB modules | Relevant tests | Test phases |
| Kafka test | `org.springframework.kafka:spring-kafka-test` | Loan, Ledger, Search | Event phases |

Java Stream and `java.util.concurrent` are JDK 21 APIs, not Maven dependencies. SLF4J and Logback are already supplied transitively by Spring Boot starters. Logging will use those APIs plus MDC; JSON logging can be added later without changing business code.

Springdoc 3.x is selected because its compatibility matrix supports Spring Boot 4.x; the exact 3.x patch version must be pinned and verified in the build when the API foundation is implemented. Reference: [springdoc compatibility](https://springdoc.org/).

### 2.2 Dependency rules

- Use Lombok only for boilerplate such as `@Getter`, `@Builder`, and `@Slf4j`; do not hide domain behavior behind generated methods.
- Required Spring dependencies use constructor injection with `private final` fields; use `@RequiredArgsConstructor` for simple constructors and keep explicit constructors when they transform or validate dependencies.
- Prefer Java records for immutable API responses and event payloads where they improve clarity.
- Do not introduce a shared domain/entity module between services.
- Keep `spring-kafka-test` and Testcontainers test-scoped.
- Run `mvn dependency:tree` and check for duplicate or conflicting logging/Jackson versions after each dependency milestone.
- Domain packages remain framework-independent; Spring bean registration for domain policies belongs in each service's `config` package.

## 3. Cross-cutting logging design

### 3.1 Log contract

Every service must emit logs with these fields whenever available:

```text
timestamp
level
service
environment
logger
message
correlationId
causationId
requestId
actorId
actorRole
aggregateType
aggregateId
aggregateVersion
eventId
eventType
businessReference
operation
outcome
durationMs
```

Sensitive values must not be logged:

- `Authorization` header or token contents.
- Full request bodies for financial writes.
- Passwords, secrets, API keys, payment credentials, or raw PII.
- Complete financial payloads unless a deliberately redacted diagnostic field is required.

Amounts in logs are either omitted or represented by a controlled business reference and currency; the authoritative amount remains in the database and audit record.

### 3.2 Request logging

Create a shared implementation pattern in each service, without sharing service domain code:

- `CorrelationIdFilter`: read `X-Correlation-Id`, generate a UUID when absent, put it in MDC, and return it in the response.
- `RequestContext`: immutable request metadata for application services.
- `GlobalExceptionHandler`: map validation, authorization, conflict, not-found, and unexpected errors to the documented error response.
- `RequestLoggingFilter` or interceptor: log method, route template, status, actor, correlation ID, and duration; never log raw payloads.

For Kafka:

- Copy `correlationId`, `causationId`, `eventId`, and `businessReference` from the event envelope into MDC while handling a record.
- Clear MDC in a `finally` block so consumer threads cannot leak context to another message.
- Log retry, DLQ, replay, and duplicate decisions with event metadata only.

### 3.3 Logging configuration

Create a `logback-spring.xml` per service or a carefully scoped root logging configuration with:

- Console output for local development.
- Key-value fields from MDC.
- Configurable levels through environment variables.
- `INFO` for business state transitions and successful lifecycle events.
- `WARN` for retries, stale events, idempotent duplicates, cache fallback, and projection lag.
- `ERROR` for failed transactions, DLQ sends, invariant violations, and unexpected exceptions.
- `DEBUG` only for local diagnostics; never enable it by default for financial payloads.

Add tests that prove correlation IDs survive request handling and Kafka handling, and that secrets are absent from representative log messages.

## 3.4 DTO and API contract rules

DTOs are mandatory at every REST boundary. The package layout is:

```text
api/dto/
├── common/
│   ├── ApiResponse.java
│   ├── ApiErrorResponse.java
│   ├── ApiMeta.java
│   ├── PageMeta.java
│   └── FieldViolation.java
├── request/
├── response/
└── mapper/
```

Rules:

- `request` DTOs contain transport input and Jakarta Validation annotations.
- `response` DTOs contain only fields approved for the client contract.
- Domain objects, JPA entities, JDBC rows, Elasticsearch documents, and MongoDB documents never cross the controller boundary.
- Request DTOs and response DTOs are not reused as Kafka event payloads; event contracts have their own versioned types.
- Mapping is explicit through mapper classes or methods; do not rely on accidental JSON serialization of domain objects.
- Monetary values use decimal strings with exactly the documented scale, for example `"15000000.0000"`.
- Dates and timestamps use ISO-8601 strings with timezone/offset; IDs use UUID/string formats documented in OpenAPI.
- Enum values are uppercase stable contract values; renaming requires a schema version or compatibility migration.
- Fields that are not part of the contract are omitted, not leaked as internal database fields.
- Sensitive values are never returned in generic error details.

### 3.5 Standard success response

Every business REST endpoint, except Actuator endpoints, uses this envelope:

```json
{
  "success": true,
  "data": {
    "loanId": "loan-001",
    "status": "APPROVED"
  },
  "error": null,
  "meta": {
    "apiVersion": "v1",
    "correlationId": "corr-001",
    "requestId": "req-001",
    "timestamp": "2026-11-17T03:00:00Z",
    "path": "/api/v1/loans/loan-001"
  }
}
```

Rules:

- `success` is always present and boolean.
- Successful responses have `data`; `error` is `null`.
- `meta` is always present and contains correlation/request tracing metadata.
- `202 Accepted` responses include a resource ID, current asynchronous status, and a `statusUrl` inside `data`.
- `204 No Content` is reserved for endpoints that explicitly return no body; otherwise use the standard envelope.
- HTTP status codes remain meaningful; the envelope does not replace `2xx`, `4xx`, and `5xx` semantics.

### 3.6 Standard error response

Every client-visible error uses this envelope:

```json
{
  "success": false,
  "data": null,
  "error": {
    "code": "VALIDATION_ERROR",
    "message": "Request validation failed",
    "details": [
      {
        "field": "amount",
        "reason": "must be greater than zero"
      }
    ]
  },
  "meta": {
    "apiVersion": "v1",
    "correlationId": "corr-001",
    "requestId": "req-001",
    "timestamp": "2026-11-17T03:00:00Z",
    "path": "/api/v1/loans/loan-001/repayments"
  }
}
```

Required error codes include:

```text
VALIDATION_ERROR
MALFORMED_REQUEST
UNAUTHORIZED
FORBIDDEN
RESOURCE_NOT_FOUND
INVALID_STATE_TRANSITION
IDEMPOTENCY_CONFLICT
VERSION_CONFLICT
ACTIVE_OPERATION_CONFLICT
INSUFFICIENT_BALANCE
BUSINESS_RULE_VIOLATION
DEPENDENCY_UNAVAILABLE
PROJECTION_NOT_READY
INTERNAL_ERROR
```

Rules:

- `success` is always `false`.
- `data` is always `null`.
- `error.code` is stable and machine-readable; FE logic must branch on the code, not on the human message.
- `error.message` is safe for display/log correlation and contains no stack trace.
- `error.details` is optional and is used for field-level validation or safe diagnostic metadata only.
- `meta.correlationId` is returned to the client and included in server logs/audit records.
- `500` responses use `INTERNAL_ERROR` and never expose class names, SQL, stack traces, or secrets.

### 3.7 Pagination contract

List endpoints use a common response shape:

```json
{
  "success": true,
  "data": {
    "items": [],
    "page": {
      "nextCursor": "opaque-cursor",
      "hasNext": true,
      "limit": 50
    }
  },
  "error": null,
  "meta": {}
}
```

Cursor values are opaque to the FE. The backend owns ordering and uses stable keyset ordering such as `(created_at, id)` for statements and activity.

### 3.8 Contract verification

- Every request/response DTO is represented in OpenAPI.
- Controller tests assert HTTP status, envelope shape, field names, decimal string serialization, and error codes.
- Contract tests reject accidental entity serialization and unknown response fields where strict compatibility is required.
- FE examples and Postman/HTTP collections use the same DTO examples as OpenAPI.
- Contract changes require a versioning/compatibility review before implementation.

## 4. Audit trail design

Operational logging answers “what did the application observe?” Audit trail answers “what business action was committed, by whom, and under which reference?”. They must remain separate.

### 4.1 Authoritative audit records

`loan-service` migration:

```text
loan_audit_events(
  id UUID PRIMARY KEY,
  event_id UUID NOT NULL UNIQUE,
  aggregate_type VARCHAR NOT NULL,
  aggregate_id UUID NOT NULL,
  aggregate_version BIGINT NOT NULL,
  action VARCHAR NOT NULL,
  outcome VARCHAR NOT NULL,
  actor_id VARCHAR NOT NULL,
  actor_role VARCHAR NOT NULL,
  correlation_id UUID NOT NULL,
  causation_id UUID,
  business_reference VARCHAR,
  reason_code VARCHAR,
  metadata JSONB NOT NULL,
  occurred_at TIMESTAMPTZ NOT NULL,
  created_at TIMESTAMPTZ NOT NULL
)
```

`ledger-service` uses the same append-only audit shape for operator/account actions. Financial journal rows and `savings_transactions` remain the primary accounting and statement evidence; the audit table records the business action and actor context around them.

Required actions include:

```text
LOAN_SUBMITTED
LOAN_APPROVED
LOAN_REJECTED
FUNDING_REQUESTED
FUNDING_SIMULATION_CONFIRMED
FUNDING_SIMULATION_FAILED
FUNDING_JOURNAL_POSTED
REPAYMENT_REQUESTED
REPAYMENT_CONFIRMED
REPAYMENT_JOURNAL_POSTED
REPAYMENT_SETTLED
SAVINGS_ACCOUNT_OPENED
SAVINGS_DEPOSIT_POSTED
SAVINGS_WITHDRAWAL_POSTED
SAVINGS_WITHDRAWAL_REJECTED
ACCOUNT_FROZEN
ACCOUNT_UNFROZEN
```

### 4.2 Audit invariants

- State mutation, audit insert, and local outbox insert commit in the same local database transaction.
- If the business transaction rolls back, the audit record rolls back with it.
- Audit records are append-only: no update or delete repository methods, and PostgreSQL triggers reject direct `UPDATE`/`DELETE` attempts.
- Duplicate Kafka delivery cannot create duplicate audit records because `event_id` is unique.
- A correction is represented by a new reversal/correction event; historical audit rows are never edited.
- Audit metadata is sanitized and contains no secrets or unnecessary PII.
- Every cross-service action carries `correlationId`, `causationId`, `eventId`, `aggregateId`, `aggregateVersion`, and `businessReference`.
- Access to audit reads is operator-only and read-only; no client request may mutate an audit row.

### 4.3 Audit verification

Tests must cover:

- Audit row created for a committed approval, funding, repayment, deposit, and withdrawal.
- Audit row rolled back when state/outbox persistence fails.
- Duplicate event produces one state change and one audit record.
- Audit update/delete is rejected.
- Actor and correlation metadata are preserved across Saga steps.
- Rejected operations create an audit decision where the business policy requires traceability, without pretending that a rejected operation posted money.

## 5. Service architecture and ownership

### 5.1 `loan-service`

Owns products, pricing, applications, approval, funding simulation, repayment schedules, payment intents, allocation, and outstanding.

```text
com.fincore.loan
├── api/controller
├── api/dto
├── application/usecase
├── domain/model
├── domain/policy
├── domain/port
├── infrastructure/persistence/postgres
├── infrastructure/messaging/outbox
├── infrastructure/messaging/kafka
├── infrastructure/cache/redis
├── observability/logging
├── observability/audit
└── config
```

### 5.2 `ledger-service`

Owns journal headers/lines, idempotent event consumption, funding/repayment posting, savings accounts, atomic balance changes, and statements.

```text
com.fincore.ledger
├── api/controller
├── api/dto
├── application/usecase
├── domain/model
├── domain/policy
├── domain/port
├── infrastructure/persistence/postgres
├── infrastructure/messaging/inbox
├── infrastructure/messaging/outbox
├── observability/logging
├── observability/audit
└── config
```

### 5.3 `search-service`

Owns Elasticsearch search documents, MongoDB activity documents, Kafka consumers, and Kafka Streams analytics. It is a non-authoritative read model service.

```text
com.fincore.search
├── api/controller
├── api/dto
├── application/query
├── projection/elasticsearch
├── projection/mongodb
├── messaging/consumer
├── messaging/streams
├── infrastructure/repository
├── observability/logging
└── config
```

Search projections may record projection diagnostics, but they do not become the authoritative audit store for loan or ledger actions.

## 6. Saga and event contracts

### 6.1 Event envelope

All domain events use a versioned envelope:

```json
{
  "eventId": "uuid",
  "eventType": "LoanFunded",
  "schemaVersion": 1,
  "aggregateId": "loan-001",
  "aggregateVersion": 4,
  "businessReference": "FUND-001",
  "occurredAt": "2026-11-17T03:00:00Z",
  "correlationId": "uuid",
  "causationId": "uuid",
  "payload": {}
}
```

Money is serialized as a decimal string, never JSON floating point. No sensitive personal data is placed in events.

### 6.2 Funding Saga

```text
Loan request
  -> loan DB: FUNDING_PENDING + audit + outbox
  -> funding simulator
  -> loan DB: FUNDED + LoanFunded outbox
  -> ledger DB: inbox + balanced funding journal + ledger outbox
  -> loan DB: FundingJournalPosted acknowledgement + ledger status update
```

`loan.status` represents the loan lifecycle. Ledger posting status is a separate projection/state so `FUNDED` is never misreported as “ledger posted”. A simulator failure becomes `FUNDING_FAILED`; a ledger delivery failure remains pending/retryable and eventually enters controlled reconciliation or failure handling.

### 6.3 Repayment Saga

```text
Payment request
  -> loan DB: payment intent PENDING
  -> simulated receipt confirmation
  -> loan DB: CONFIRMED_AWAITING_LEDGER + RepaymentConfirmed outbox
  -> ledger DB: inbox + repayment journal + ledger outbox
  -> loan DB: RepaymentJournalPosted
  -> loan DB: settle allocation and outstanding exactly once
```

The allocation snapshot is stored before the event is emitted. Outstanding changes only after durable ledger acknowledgement. One active payment intent per loan is enforced by a database guard.

### 6.4 Failure and recovery policy

- Kafka unavailable: outbox remains pending; no business state is lost.
- Consumer crash after database commit: redelivery is safe through inbox uniqueness.
- Poison event: bounded retry, diagnostic DLQ, controlled replay.
- Unknown outcome from a simulator: retain pending state; query/reconcile instead of blind retry.
- Posted journal correction: reversal journal, never mutation of historical rows.
- Search outage: authoritative loan/ledger writes continue; projections catch up later.

## 7. Elasticsearch and MongoDB implementation

### 7.1 Elasticsearch

Index:

```text
fincore-loans-v1
```

Mapping:

- `loanId`, `customerId`, `productCode`, and `status`: `keyword`.
- `principalAmount`: numeric for search/analytics only.
- `submittedAt`, `fundedAt`, and activity timestamps: `date`.
- `description`: `text`.
- `aggregateVersion`: numeric.

Projection rules:

- `_id` is `loanId`.
- Event handlers validate event type and schema version.
- Stale aggregate versions are ignored.
- Duplicate event IDs do not produce duplicate effects.
- Failed indexing is retried; permanent failures go to a projection DLQ with event metadata.
- Search response always documents eventual consistency.
- Rebuild creates a new index version, replays available events, validates counts, then switches an alias; it does not mutate the authoritative loan database.

### 7.2 MongoDB

Collection:

```text
loan_activity_views
```

Rules:

- Unique index on `loanId`.
- Store a bounded activity list or paginated activity collection to prevent unbounded document growth.
- Deduplicate by `eventId`.
- Apply aggregate version guards.
- Project repayment activity, including journal acknowledgement, without becoming the source of repayment status.
- Provide indexed lookup for `GET /api/v1/loans/{id}/activity`.

## 8. Persistence and financial correctness

### 8.1 PostgreSQL ownership

`loan_db` tables:

```text
loan_products
loan_applications
loans
repayment_schedules
repayments
repayment_allocations
idempotency_records
outbox_events
loan_processed_events
loan_audit_events
```

`ledger_db` tables:

```text
journal_entries
journal_lines
processed_events
savings_accounts
savings_transactions
ledger_idempotency_records
ledger_outbox_events
ledger_audit_events
```

### 8.2 Concurrency rules

- Use `NUMERIC(19,4)` and `BigDecimal`; never `float` or `double` for money.
- Use `SELECT ... FOR UPDATE` or conditional versioned updates for approval, funding, repayment allocation, and savings withdrawal.
- Use a partial unique index or guarded status for one active repayment per loan.
- Use unique `businessReference` and `Idempotency-Key` constraints.
- Use atomic conditional balance debit in the ledger database; Redis is never the withdrawal guard.
- Return `409` for stale version or active-operation conflicts and `422` for invalid money/business input.

## 9. REST and OpenAPI work

Add Springdoc to each service and expose:

```text
/v3/api-docs
/swagger-ui.html
```

Create the common DTO envelope before implementing business controllers:

```text
ApiResponse<T>
ApiErrorResponse
ApiMeta
PageMeta
FieldViolation
```

Each endpoint must have separate request and response DTOs. For example:

```text
POST /api/v1/loan-applications
  SubmitLoanRequest
  -> ApiResponse<LoanApplicationResponse>

POST /api/v1/loans/{id}/repayments
  CreateRepaymentRequest
  -> ApiResponse<RepaymentResponse>

GET /api/v1/savings/accounts/{id}/transactions
  -> ApiResponse<StatementPageResponse>
```

Document:

- Request/response schemas and decimal-string money fields.
- `Idempotency-Key`, `X-Correlation-Id`, and authorization requirements.
- `400`, `401`, `403`, `404`, `409`, `422`, `500`, and `202` responses.
- Eventual-consistency semantics for search and asynchronous repayment/funding acknowledgement.
- Examples for approval, funding, repayment, savings deposit, savings withdrawal, and error responses.

Each service validates the bearer token and enforces role and ownership checks through Kong and on its direct port.

### 9.1 Keycloak authorization

Loan, ledger and search validate the JWT signature, token lifetime, issuer and audience. They map authorities from
`resource_access.fincore-api.roles` and use `sub` and the authorized role for audit identity. Protected endpoints
return `401` for a missing or invalid token and `403` for a role or ownership denial. Authentication and authorization
errors use the standard envelope and carry correlation and request IDs.

| Operation | Access |
|---|---|
| Health and readiness, OpenAPI, loan products and quotes | Public |
| Loan application submission | `CUSTOMER` for its own `customerId`, or `LOAN_OPERATOR` and `LOAN_APPROVER` across customers |
| Loan approval and rejection | `LOAN_APPROVER` |
| Loan funding, repayment and reports | `LOAN_OPERATOR`, `LOAN_APPROVER` |
| Savings opening, deposits and withdrawals | `CUSTOMER` for its own customer or account, plus `SAVINGS_OPERATOR`, `LOAN_OPERATOR`, `LOAN_APPROVER` |
| Savings account and statement reads | `CUSTOMER` for its own account, or `SAVINGS_OPERATOR` across customers |
| Savings status changes and journal reads | `SAVINGS_OPERATOR`, `LOAN_OPERATOR`, `LOAN_APPROVER` |
| Loan search and activity | `CUSTOMER` for its own projections, or `LOAN_OPERATOR` and `LOAN_APPROVER` across customers |

Customer ownership uses an exact `customer_id` match. Customer search excludes other customers' and unowned records.
Activity outside the caller's scope returns `404`. Savings account reads by another customer return `403`.

## 10. Kong Gateway

The declarative config is `docker/kong/kong.yml`. Kong runs DB-less on image `kong:3.9.0` and proxies the business
routes on host port `8000`. The Admin API listens on container loopback at `127.0.0.1:8001` with no host port.
Run `docker compose exec kong kong health` for local diagnostics.

Initial routes:

```text
/api/v1/loans/**       -> loan-service:8081
/api/v1/reports/loans  -> loan-service:8081
/api/v1/loan-products  -> loan-service:8081
/api/v1/loan-quotes    -> loan-service:8081
/api/v1/loan-applications -> loan-service:8081
/api/v1/savings/**     -> ledger-service:8082
/api/v1/journals/**    -> ledger-service:8082
/api/v1/search/**      -> search-service:8083
GET /api/v1/loans/{loanId}/activity -> search-service:8083
```

Gateway policies:

- Preserve or generate `X-Correlation-Id`.
- Do not enable automatic retries for financial write routes.
- Do not put financial state or idempotency logic in gateway plugins.
- Add CORS/rate limiting/authentication only after their policy and test cases are defined.
- Run the authentication smoke through `http://localhost:8000`. Actuator and OpenAPI use direct service ports.
- Check an unmatched route separately for a gateway `404`.

## 11. Docker Compose and infrastructure

The root `docker-compose.yml` contains:

- PostgreSQL with separate logical databases for loan, ledger and Keycloak. Keycloak has a dedicated database login.
- Keycloak `26.8.2` with readiness checks and a fixture import on first startup.
- Kafka and its required broker/controller configuration.
- Redis.
- Elasticsearch.
- MongoDB.
- `loan-service`, `ledger-service`, `search-service`.
- Kong Gateway.
- Health checks and readiness dependencies.

Elasticsearch and MongoDB have no published host ports. Client access to projections goes through the authenticated
search service. Kafka uses `kafka:19092` inside Compose and publishes the host listener on `127.0.0.1:9092`.
Keycloak and PostgreSQL are loopback-bound. The local Compose stack publishes the Kong proxy, Spring service ports
and Redis for development. Keep backend and management access restricted in shared deployments.

The optional `docker-compose.scale.yml` overlay adds the second loan instance on port `8084` for shared-cache
verification. All Spring instances wait for Keycloak readiness and receive the OAuth2 settings.

### 11.1 Keycloak startup, recovery and smoke

```bash
cp -n .env.example .env
docker compose up --build -d --wait --wait-timeout 180
docker compose ps
./scripts/smoke.sh
```

Keycloak serves the `fincore` realm on `http://localhost:8180`. The local issuer is
`http://localhost:8180/realms/fincore`, the audience is `fincore-api`, and services fetch keys at
`http://keycloak:8080/realms/fincore/protocol/openid-connect/certs` from inside Compose. Use
`FINCORE_SECURITY_MODE=oauth2`, `OIDC_ISSUER_URI`, `OIDC_AUDIENCE` and `OIDC_JWK_SET_URI` for service configuration.
A host-run service defaults to the issuer's certs endpoint.

The demo usernames are `customer1` (`CUST-001`), `customer2` (`CUST-002`), `loan-operator`, `loan-approver` and
`savings-operator`. Local demo passwords match the usernames. The [README](../README.md#local-keycloak) lists the
development admin and database defaults and realm recovery steps. The fixture uses non-temporary demo passwords.
The local password-grant client is `fincore-cli`. Run `set +x` before capturing
`token=$(./scripts/keycloak-token.sh)` to keep the token in the shell. The
[smoke instructions](../README.md#authentication-smoke) document credential and token overrides and idempotent replay.

Use `docker compose logs --tail=100 keycloak` to diagnose startup. If port `5432` is occupied, set
`POSTGRES_HOST_PORT` to a free loopback port in `.env`. For an older PostgreSQL volume, run the idempotent
provisioning SQL with Keycloak stopped, then wait for it and the services to be healthy again:

```bash
docker compose stop keycloak
docker compose exec -T postgres psql -X -v ON_ERROR_STOP=1 -U fincore -d postgres \
  < docker/postgres/init/01-create-databases.sql
docker compose up -d --wait --wait-timeout 180 keycloak
docker compose up -d --wait --wait-timeout 180
```

Startup imports the fixture when the realm is absent. Apply fixture changes to an existing realm in the admin
console. For production, use an HTTPS issuer, the provider's reachable JWKS URL and API audience, and emit
`sub`, `customer_id` and the expected API client roles. Browser and mobile clients use Authorization Code with PKCE
and public clients; service-to-service integrations use confidential clients with Client Credentials and rotated
secrets. Replace local credentials with managed secrets and disable the demo password grant in production.

Current authentication results are recorded in the [Keycloak verification evidence](evidence/KEYCLOAK_VERIFICATION_2026-11-18.md).

## 12. Testing strategy

### 12.1 Unit tests

- Interest strategies: zero interest, invalid tenor, rounding, residual period.
- State guards: approve, reject, fund, repayment transitions.
- Java Stream reports: empty input, duplicate customer, ties, deterministic ordering, BigDecimal precision.
- Repayment allocation: interest before principal, partial payment, full settlement, overpayment rejection.
- Ledger invariant: total debit equals total credit.
- Savings policy: active/frozen account, sufficient balance, non-negative balance.
- Audit event construction and metadata redaction.

### 12.2 Integration tests

Use Testcontainers with separated suites:

- PostgreSQL migration, constraints, native SQL, optimistic concurrency, and rollback.
- Kafka outbox/inbox, redelivery, DLQ, ordering, and event version guards.
- Redis cache miss/hit/invalidation/fallback and two-instance shared cache.
- Elasticsearch mapping, version guard, stable sort, reindex, and projection recovery.
- MongoDB indexed activity lookup, duplicate event suppression, and bounded activity.
- Kong route proxying and unmatched route behavior.

### 12.3 Kafka Streams tests

Use `TopologyTestDriver` for:

- Five-minute event-time window.
- Product grouping and exact decimal aggregation.
- Duplicate event deduplication within retention.
- Grace period and late-event policy.
- Output topic schema.

### 12.4 Logging and audit tests

- Request without correlation ID receives and logs a generated ID.
- Existing correlation ID is preserved through REST and Kafka.
- Log output excludes Authorization, secrets, and raw sensitive payloads.
- Business state, audit row, and outbox row commit together.
- Failure before commit leaves no audit row or outbox row.
- Duplicate events produce one state change, one audit row, and one journal effect.
- Audit update/delete is rejected.
- Saga causation/correlation fields can trace funding and repayment end-to-end.

## 13. Acceptance and evidence

All original AT-01 through AT-25 remain mandatory. Add these gateway/observability checks:

| ID | Scenario | Pass criteria |
|---|---|---|
| AT-26 | Kong routing | Public routes reach the correct service; unmatched route returns controlled 404 |
| AT-27 | Correlation propagation | One correlation ID is visible in gateway response, service logs, Kafka event, and audit record |
| AT-28 | Audit atomicity | State, audit, and outbox commit/rollback together |
| AT-29 | Audit immutability | Audit update/delete is rejected and correction uses a new event |
| AT-30 | Sensitive logging | Representative logs contain no token, secret, or raw sensitive request body |
| AT-31 | Saga traceability | Funding and repayment can be followed using correlation/causation/business references |

Evidence must be stored under:

```text
docs/evidence/
├── test-results.md
├── explain-plans/
├── kafka-events/
├── dlq-replay/
├── logging-redaction/
├── audit-trail/
├── elasticsearch/
├── mongodb/
├── kong-routing/
└── compose-health/
```

## 14. Execution milestones

### M0 — Foundation and contracts

- [x] Add Lombok, OpenAPI, and the `platform-support` technical library.
- [x] Standardize service package boundaries across API, application, domain, and infrastructure layers.
- [x] Define package boundaries and naming conventions for API, application, domain, infrastructure, and observability code.
- [x] Define error response, correlation headers, request IDs, and event envelope.
- [x] Define common request/response DTO envelopes, error codes, pagination shape, decimal/date/enum serialization, and OpenAPI naming rules.
- [x] Add request context/MDC logging and global exception handling.
- [x] Standardize SLF4J API usage, Logback configuration, MDC fields, and constructor-injected service dependencies.
- [x] Add automated architecture boundary checks for domain and controller packages.
- [x] Add audit event conventions, append-only PostgreSQL migration helpers, and audit unit tests.
- [x] Verify `mvn clean verify`.
- [x] Add JDBC/Flyway runtime wiring and PostgreSQL migration execution; Testcontainers and JPA remain deferred until their concrete use cases are implemented.

**Exit:** dependency tree is clean; three services start; REST/event/audit contracts and logging conventions are tested. Database migration execution is verified in M1 against PostgreSQL.

### M1 — Loan domain and PostgreSQL

- [x] Add JDBC, PostgreSQL, Flyway, and the PostgreSQL Flyway adapter; enable migrations in loan and ledger service profiles.
- [x] Add Testcontainers coverage for isolated PostgreSQL integration tests.
- [x] Implement the loan product catalog API with active-product filtering, cursor pagination, DTO mapping, and JDBC native SQL.
- [x] Implement flat/annuity pricing strategies and the quote API with rounded installment schedules.
- [x] Implement loan application submission with request/response DTOs, idempotency, audit trail, and atomic outbox write.
- [x] Implement approval/rejection transitions and state guards with optimistic locking.
- [x] Add Flyway loan and ledger core schemas and indexes.
- [x] Implement ledger journal domain invariants, JDBC persistence, audit/outbox ports, and business-reference verification query.
- [x] Implement idempotency records and audit writes in the loan submission transaction.
- [x] Persist the loan outbox event atomically with the application state.
- [x] Implement Java Stream bounded reports with deterministic Top-5 customer and posted-repayment ranking.
- [x] Implement native SQL Top-3, running total, and repayment aggregate query fixtures.
- [x] Add bounded report DTOs, explicit mappers, and `/api/v1/reports/loans` endpoints.
- [x] Add request/response DTOs, explicit mappers, OpenAPI metadata, and controller/repository contract tests for loan products, quotes, application submission, and approval/rejection decisions.

**Exit:** AT-01 through AT-05 and financial calculation tests pass.

Report SQL design and index rationale are documented in [sql-performance.md](sql-performance.md). Testcontainers coverage now verifies Flyway migrations and native report execution against PostgreSQL; PostgreSQL `EXPLAIN (ANALYZE, BUFFERS)` evidence remains pending a representative-dataset capture.

### M2 — Kafka foundation and funding Saga

- [x] Implement the transactional loan outbox write.
- [x] Implement the loan outbox relay with bounded polling, PostgreSQL lease claims, retry metadata, and guarded result updates.
- [x] Implement Kafka topic configuration and string-key/string-payload event publishing.
- [x] Implement ledger Kafka consumer configuration, `LoanFunded` envelope parsing, and `processed_events` inbox deduplication.
- [x] Connect balanced funding journal posting to Kafka consumption and ledger acknowledgement delivery.
- [x] Implement loan acknowledgement consumer and funding ledger status.
- [x] Add bounded consumer retry, diagnostic DLQ routing, replay runbook, and audit/correlation propagation.

The funding command is available at `POST /api/v1/loans/{applicationId}/fund`.
It uses the deterministic sandbox simulator, persists `FUNDED` before emitting
`LoanFunded`, and consumes `FundingJournalPosted` to update the durable loan
ledger-posting projection. Both services have lease-based outbox relays and
inbox deduplication. Currency is fixed to `IDR` in this MVP because
multi-currency is deferred.

Consumer failures use a bounded `DefaultErrorHandler` with configurable backoff;
after the retry budget is exhausted, Spring Kafka publishes the original record to
`<source-topic>.DLQ` with diagnostic headers. The DLQ record is retained for
controlled replay by explicit topic/partition/offset selection; replay is safe only
because the downstream inbox and business-reference constraints are idempotent.

**Exit:** AT-06 through AT-10, AT-18, and AT-19 pass.

### M3 — Repayment lifecycle

- [x] Implement deterministic flat/annuity repayment schedule generation with final-period rounding.
- [x] Implement payment intent and simulated receipt confirmation.
- [x] Implement interest-first then principal allocation.
- [x] Implement one-active-payment guard and allocation snapshot.
- [x] Implement repayment journal and acknowledgement settlement.
- [x] Add repayment SQL joins and conditional aggregation.

Repayment confirmation creates a durable allocation snapshot and emits
`RepaymentConfirmed`; ledger posts a balanced three-line journal and emits
`RepaymentJournalPosted`. The loan inbox applies the snapshot only after the
acknowledgement and transitions the loan to `PAID_OFF` when every schedule is paid.
- [x] Add repayment audit events and replay/deduplication tests.

**Exit:** AT-20 through AT-22 pass with no duplicate outstanding updates.

### M4 — Savings and ledger concurrency

- [x] Implement synthetic savings account opening.
- [x] Implement atomic deposit and withdrawal with double-entry journals.
- [x] Implement frozen-account and insufficient-balance rules.
- [x] Implement statement keyset pagination.
- [x] Implement savings idempotency, audit trail, and outbox events.
- [x] Test two concurrent withdrawals and Redis outage/shared-cache behavior.

**Exit:** AT-23 through AT-25 pass; balance and journal reconciliation is green.

### M5 — Search read models and Kafka Streams

- [x] Add Elasticsearch mapping and index lifecycle.
- [x] Implement idempotent version-guarded loan projection.
- [x] Add MongoDB activity projection with indexed lookup and bounded activity.
- [x] Implement Kafka Streams five-minute event-time aggregation.
- [x] Add late-event, duplicate, replay, and rebuild/deduplication tests.
- [x] Add repayment activity projection.

**Exit:** AT-11 and AT-15 through AT-17 pass.

### M6 — Redis cache and data-grid

- [x] Implement product cache-aside with versioned keys, TTL jitter, and eviction.
- [x] Implement timeout-bounded PostgreSQL fallback.
- [x] Implement bounded single-flight on cache miss.
- [x] Implement two-instance shared-cache test.
- [x] Implement atomic non-financial counter demonstration through Redis primitives/configuration boundary.
- [x] Document Redis consistency, eviction, failure, and non-authoritative role.

**Exit:** AT-12 through AT-14 pass.

### M7 — Kong and Compose packaging

- [x] Add Kong DB-less declarative configuration with pinned image tag.
- [x] Add Kong service, health checks, and route smoke tests to Compose.
- [x] Add all infrastructure containers and readiness dependencies.
- [x] Add non-root service images and environment-based configuration.
- [x] Add two-instance loan profile for Redis demonstration.
- [x] Add `scripts/smoke.sh` for Kong API routing, Keycloak access and savings ownership.

**Exit:** clean checkout starts with Compose and AT-26 passes.

### M8 — Hardening and final acceptance

- [x] Add Keycloak JWT authentication and service-level customer and operator authorization.
- [ ] Verify ownership checks on loan and savings reads/writes.
- [ ] Verify logs are redacted and correlation fields are complete (representative Kafka/service-log evidence pending).
- [ ] Verify audit immutability and reconciliation evidence.
- [ ] Run the full AT-01 through AT-31 suite.
- [x] Store evidence under `docs/evidence/`.
- [x] Update README with setup, routes, failure demonstrations, and recovery commands.

**Exit:** `docker compose up --build -d` plus `scripts/smoke.sh` succeeds from a clean checkout, with all required evidence present.

## 15. Definition of done

The MVP implementation is complete only when:

- Java 21 and Maven builds are reproducible.
- Three Spring Boot services and Kong start through Compose.
- Loan, funding, repayment, savings, ledger, search, and analytics flows work according to the specification.
- Every financial write is idempotent and uses local transaction boundaries.
- Every cross-service flow is traceable through correlation, causation, business reference, event, and audit records.
- Logs are useful for operations and contain no secrets or raw sensitive payloads.
- Audit trail is append-only, transactionally consistent, and queryable by authorized operators.
- Elasticsearch and MongoDB are recoverable read models, not sources of financial truth.
- Original AT-01 through AT-25 and added AT-26 through AT-31 pass.
- Failure, retry, replay, DLQ, concurrency, and projection-rebuild evidence is checked into `docs/evidence/`.

