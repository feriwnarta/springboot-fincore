# Search Read Model Migration Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Serve product, loan application, and savings account list reads from `search-service` and Elasticsearch while preserving existing write contracts and ownership rules.

**Architecture:** Owner services remain system of record and publish versioned outbox events. Search Service consumes loan and ledger topics into three Elasticsearch read models and exposes the GET list contracts through Kong. Existing command endpoints stay on Loan/Ledger services.

**Tech Stack:** Spring Boot 4, Java 21, Maven, PostgreSQL/Flyway, Kafka, Elasticsearch, MongoDB activity projection, Kong, JUnit, Mockito, Testcontainers.

---

### Task 1: Define search read-model contracts and documents

**Files:**
- Create: `services/search-service/src/main/java/com/fincore/search/api/dto/response/LoanProductSearchResponse.java`
- Create: `services/search-service/src/main/java/com/fincore/search/api/dto/response/SavingsAccountSearchResponse.java`
- Create: `services/search-service/src/main/java/com/fincore/search/infrastructure/search/LoanProductSearchDocument.java`
- Create: `services/search-service/src/main/java/com/fincore/search/infrastructure/search/SavingsAccountSearchDocument.java`
- Create: `services/search-service/src/main/java/com/fincore/search/infrastructure/search/LoanProductSearchDocumentRepository.java`
- Create: `services/search-service/src/main/java/com/fincore/search/infrastructure/search/SavingsAccountSearchDocumentRepository.java`
- Test: `services/search-service/src/test/java/com/fincore/search/infrastructure/search/*ProjectionTest.java`

- [x] **Step 1: Write failing document/projection tests** for product and account mapping.
- [x] **Step 2: Run focused search tests** and confirm the new types were initially missing.
- [x] **Step 3: Implement the Elasticsearch documents, repositories, response DTOs, and version guards.**
- [x] **Step 4: Run focused tests** and confirm they pass.

### Task 2: Publish missing product and savings account lifecycle events

**Files:**
- Modify: `services/loan-service/src/main/java/com/fincore/loan/application/service/LoanProductService.java`
- Modify: `services/ledger-service/src/main/java/com/fincore/ledger/application/service/SavingsService.java`
- Modify: relevant service tests and event serializer/parser tests.

- [x] **Step 1: Add failing event projection tests.**
- [x] **Step 2: Run the focused loan/ledger service tests.**
- [x] **Step 3: Append events in the same transaction** as the source mutation.
- [x] **Step 4: Run focused tests and confirm outbox compatibility.**

### Task 3: Extend Search Service event parsing and projection

**Files:**
- Modify: `services/search-service/src/main/java/com/fincore/search/infrastructure/messaging/LoanEventProjectionParser.java` for loan and product events.
- Create: `services/search-service/src/main/java/com/fincore/search/infrastructure/messaging/SavingsAccountEventProjectionParser.java` for ledger account events.
- Create: `services/search-service/src/main/java/com/fincore/search/application/service/SearchCatalogProjectionService.java`.
- Modify: `services/search-service/src/main/java/com/fincore/search/infrastructure/messaging/LoanEventKafkaListener.java` to dispatch loan/product events and ledger account events.
- Test: parser, consumer, and projection service tests under `services/search-service/src/test/java/com/fincore/search/`.

- [x] **Step 1: Add failing parser tests** for product and savings event payloads.
- [x] **Step 2: Run parser/projection tests** and confirm failure.
- [x] **Step 3: Implement idempotent event parsing and projection updates** for products, applications, and accounts.
- [x] **Step 4: Run focused tests** and confirm event-to-document behavior.

### Task 4: Add Search Service list endpoints

**Files:**
- Modify: `services/search-service/src/main/java/com/fincore/search/api/controller/LoanSearchController.java`
- Create: `services/search-service/src/main/java/com/fincore/search/api/controller/SavingsAccountSearchController.java`
- Modify: `services/search-service/src/main/java/com/fincore/search/application/service/LoanSearchQueryService.java`
- Create: `services/search-service/src/main/java/com/fincore/search/application/service/SavingsAccountSearchQueryService.java`
- Create: `services/search-service/src/main/java/com/fincore/search/application/query/SearchCursor.java`
- Test: controller/service/security tests under `services/search-service/src/test/java/com/fincore/search/`.

- [x] **Step 1: Add failing controller/service tests** for:
  - `GET /api/v1/loan-products`
  - `GET /api/v1/loan-applications`
  - `GET /api/v1/savings/accounts`
  - customer ownership filtering
  - staff cross-customer filtering
  - opaque cursor pagination and standard `ApiResponse<PageData<...>>` envelopes.
- [x] **Step 2: Run focused tests** and confirm failure.
- [x] **Step 3: Implement endpoints** using Elasticsearch repositories only.
- [x] **Step 4: Run focused tests** and confirm endpoint/security behavior passes.

### Task 5: Remove owner-service GET list handlers and update Kong routing

**Files:**
- Delete: `services/loan-service/src/main/java/com/fincore/loan/api/controller/LoanSearchController.java` after its GET handlers are moved.
- Modify: `services/ledger-service/src/main/java/com/fincore/ledger/api/controller/SavingsController.java` only if a list handler is added during migration; retain account detail and all writes.
- Modify: `docker/kong/kong.yml` with method-specific GET routes to `search-service` and existing POST routes to owner services.
- Modify: OpenAPI/security tests and `scripts/test-openapi-security.sh`/`scripts/test-unified-swagger.sh` assertions.

- [x] **Step 1: Add route/OpenAPI assertions** for the three GET paths.
- [x] **Step 2: Run route/OpenAPI checks** and confirm the initial route gap.
- [x] **Step 3: Update controllers, Kong methods, and Swagger metadata** without changing write URLs.
- [x] **Step 4: Run route/OpenAPI checks** and confirm the contracts are correct.

### Task 6: Rebuild projections and verify end-to-end

**Files:**
- Create: `scripts/replay-search-projections.sh` to reset the local Search Service consumer group to the earliest retained Kafka offsets and report the three index document counts.
- Modify: `README.md` with the new Search Service URLs, eventual-consistency note, and curl examples.

- [x] **Step 1: Add replay/rebuild verification** in `scripts/replay-search-projections.sh`.
- [x] **Step 2: Run full Maven verification** with `mvn -o -DskipITs verify`.
- [x] **Step 3: Build and restart affected Docker services** with the local PostgreSQL port override.
- [x] **Step 4: Verify through Kong** all three GET lists, ownership, write flow, and Search OpenAPI.
- [x] **Step 5: Run OpenAPI and Compose health checks**; live smoke flow returned 200 for products/applications/accounts.
