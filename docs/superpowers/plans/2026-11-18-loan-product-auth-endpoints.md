# Loan Product and Auth Endpoints Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task with verification checkpoints.

**Goal:** Add a secured loan-product creation API and a Keycloak-backed login API that are visible and usable through Kong Unified Swagger.

**Architecture:** Keep product ownership in `loan-service` using its existing DTO/application/domain-port/JDBC-adapter boundaries. Add a public auth facade in `loan-service` that delegates token issuance to Keycloak through a small provider port; Keycloak remains the identity and JWT authority. Both APIs return the shared `ApiResponse` envelope and preserve correlation/request metadata.

**Tech Stack:** Spring Boot 4.1.2, Java 21, Maven, Spring Security OAuth2 resource server, Spring `RestClient`, PostgreSQL/Flyway, Redis cache-aside, Keycloak, Kong, JUnit 5, MockMvc, Mockito, Testcontainers.

---

### Task 1: Add failing API and application tests

**Files:**
- Create: `services/loan-service/src/test/java/com/fincore/loan/api/controller/AuthControllerTest.java`
- Modify: `services/loan-service/src/test/java/com/fincore/loan/api/controller/LoanProductControllerTest.java`
- Modify: `services/loan-service/src/test/java/com/fincore/loan/application/service/LoanProductServiceTest.java`
- Modify: `services/loan-service/src/test/java/com/fincore/loan/config/SecurityConfigurationTest.java`

- [ ] **Step 1: Add a failing controller test for login envelope and validation.**
  - Mock an auth application service that returns a token response.
  - Assert `POST /api/v1/auth/login` returns `200`, `success=true`, `data.accessToken`, and request metadata.
  - Assert blank username/password returns `400 VALIDATION_ERROR`.

- [ ] **Step 2: Add a failing controller test for product creation.**
  - Mock the product service and current identity.
  - Send a valid request with `Idempotency-Key` and assert `201`, `data.code`, and `data.interestType`.
  - Send a request without the idempotency header and assert `400 MISSING_HEADER`.

- [ ] **Step 3: Add a failing service test for product creation.**
  - Assert the service creates a product with a UUID, version `1`, and current timestamp.
  - Assert it persists the product, writes one audit entry, and marks idempotency successful.
  - Assert a duplicate code throws `ConflictException`.

- [ ] **Step 4: Add failing security tests.**
  - Assert `/api/v1/auth/login` is public.
  - Assert `POST /api/v1/loan-products` rejects an unauthenticated caller and a token without `LOAN_PRODUCT_ADMIN`.
  - Assert a token with `LOAN_PRODUCT_ADMIN` reaches the product controller.

- [ ] **Step 5: Run only the new tests and verify they fail for missing production behavior.**

Run:

```bash
mvn -o -pl services/loan-service -am -Dtest=AuthControllerTest,LoanProductControllerTest,LoanProductServiceTest,SecurityConfigurationTest test
```

Expected: compilation/test failure because the new DTOs, endpoints, and service methods do not exist yet.

### Task 2: Implement loan-product creation through the existing persistence boundaries

**Files:**
- Create: `services/loan-service/src/main/java/com/fincore/loan/api/dto/request/CreateLoanProductRequest.java`
- Create: `services/loan-service/src/main/java/com/fincore/loan/application/command/CreateLoanProductCommand.java`
- Modify: `services/loan-service/src/main/java/com/fincore/loan/domain/port/LoanProductRepository.java`
- Modify: `services/loan-service/src/main/java/com/fincore/loan/infrastructure/persistence/jdbc/JdbcLoanProductRepository.java`
- Modify: `services/loan-service/src/main/java/com/fincore/loan/infrastructure/cache/redis/RedisCachedLoanProductRepository.java`
- Modify: `services/loan-service/src/main/java/com/fincore/loan/application/service/LoanProductService.java`
- Modify: `services/loan-service/src/main/java/com/fincore/loan/api/controller/LoanProductController.java`

- [ ] **Step 1: Define the request DTO with Bean Validation.**
  - Fields: `code`, `interestType`, `annualRate`, `minAmount`, `maxAmount`, optional `active`.
  - Use decimal validation matching the existing database precision.
  - Normalize the product code to trimmed uppercase in the application service.

- [ ] **Step 2: Extend the product repository.**
  - Add `Optional<LoanProduct> findByCode(String code)` and `void save(LoanProduct product)`.
  - Implement native SQL in `JdbcLoanProductRepository`.
  - Implement delegation and cache eviction in `RedisCachedLoanProductRepository` so a new product is immediately visible and stale cached keys are removed.

- [ ] **Step 3: Implement the transactional service command.**
  - Require `LOAN_PRODUCT_ADMIN` through `AuditActor.from`.
  - Use operation `CREATE_LOAN_PRODUCT` and the existing idempotency repository.
  - Replay a successful request by loading the stored product; reject a same-key/different-payload request with `ConflictException`.
  - Reject an existing product code with `ConflictException`.
  - Save the product and append a `LoanAuditEntry` containing actor, role, correlation ID, request ID, code, and outcome; do not include credentials or tokens.

- [ ] **Step 4: Add `POST /api/v1/loan-products` to the controller.**
  - Require `bearerAuth` in OpenAPI.
  - Require `Idempotency-Key`.
  - Return `201 Created` with `ApiResponse<LoanProductResponse>` and `ApiMetaFactory.from(request)`.

- [ ] **Step 5: Run the Task 1 tests and confirm they pass.**

### Task 3: Implement the Keycloak login facade

**Files:**
- Create: `services/loan-service/src/main/java/com/fincore/loan/api/controller/AuthController.java`
- Create: `services/loan-service/src/main/java/com/fincore/loan/api/dto/request/LoginRequest.java`
- Create: `services/loan-service/src/main/java/com/fincore/loan/api/dto/response/LoginResponse.java`
- Create: `services/loan-service/src/main/java/com/fincore/loan/application/port/TokenProvider.java`
- Create: `services/loan-service/src/main/java/com/fincore/loan/application/service/AuthService.java`
- Create: `services/loan-service/src/main/java/com/fincore/loan/infrastructure/auth/KeycloakTokenProvider.java`
- Modify: `services/loan-service/src/main/java/com/fincore/loan/config/LoanAuthConfiguration.java`
- Modify: `services/loan-service/src/main/resources/application.yml`

- [ ] **Step 1: Define login request and response DTOs.**
  - Validate username and password as non-blank.
  - Return access token, refresh token, token type, expiry values, and scope.

- [ ] **Step 2: Add a provider port and Keycloak HTTP adapter.**
  - POST form data to `${KEYCLOAK_TOKEN_URL}` using client ID `fincore-cli`.
  - Parse the upstream response into an internal token result.
  - Map HTTP 400/401 to a safe authentication failure without logging the response body, password, or token.
  - Map timeout, connection, malformed JSON, and unexpected status to a safe provider-unavailable exception.

- [ ] **Step 3: Add configuration.**
  - Configure `fincore.auth.keycloak-token-url` from `KEYCLOAK_TOKEN_URL`, defaulting to the local Keycloak realm token endpoint.
  - Configure `fincore.auth.client-id` from `KEYCLOAK_AUTH_CLIENT_ID`, defaulting to `fincore-cli`.
  - Configure bounded connect/read timeouts for the HTTP client.

- [ ] **Step 4: Add `POST /api/v1/auth/login`.**
  - Mark the operation public in security configuration.
  - Return `ApiResponse<LoginResponse>` using the shared metadata factory.
  - Add `@Operation` and request/response schema annotations so the operation is clear in Swagger.

- [ ] **Step 5: Run auth tests and verify they pass.**

### Task 4: Add Keycloak role/user fixture and gateway/OpenAPI integration

**Files:**
- Modify: `docker/keycloak/realm-fincore.json`
- Modify: `docker/kong/kong.yml`
- Modify: `docker-compose.yml`
- Modify: `services/loan-service/src/main/java/com/fincore/loan/config/SecurityConfiguration.java`
- Modify: `services/loan-service/src/main/java/com/fincore/loan/config/OpenApiConfiguration.java`
- Modify: `README.md`

- [ ] **Step 1: Add `LOAN_PRODUCT_ADMIN` client role and local demo user.**
  - Use username `product-admin`, password `product-admin`, and client role `LOAN_PRODUCT_ADMIN`.
  - Keep the fixture explicitly local-development-only.

- [ ] **Step 2: Permit only the login endpoint anonymously.**
  - Add `POST /api/v1/auth/login` to `permitAll`.
  - Require `LOAN_PRODUCT_ADMIN` for `POST /api/v1/loan-products` while keeping `GET` public.

- [ ] **Step 3: Ensure Kong proxies `/api/v1/auth` and the existing product route.**
  - Keep the route pointing to `loan-service:8081` with `strip_path: false`.

- [ ] **Step 4: Ensure unified OpenAPI shows the public login operation and bearer-protected create operation.**

- [ ] **Step 5: Update README with Swagger steps and curl examples.**

### Task 5: Verify end to end

**Files:**
- Modify: `scripts/test-unified-swagger.sh` if the new route needs an explicit assertion.
- Modify: `scripts/smoke.sh` if a non-destructive login/product contract check is added.

- [ ] **Step 1: Run formatting/syntax checks and focused tests.**

```bash
bash -n scripts/*.sh
mvn -o -pl services/loan-service -am -DskipITs verify
```

- [ ] **Step 2: Rebuild and start the changed service.**

```bash
docker compose build --pull=false loan-service
docker compose up -d --wait --wait-timeout 180
```

- [ ] **Step 3: Verify OpenAPI through Kong.**

```bash
bash scripts/test-unified-swagger.sh
curl --fail http://localhost:8000/v3/api-docs | jq '.paths | {auth: .["/api/v1/auth/login"], products: .["/api/v1/loan-products"]}'
```

- [ ] **Step 4: Verify live login and product creation.**
  - Login as `product-admin` through Swagger or curl.
  - Use `data.accessToken` in Swagger `Authorize`.
  - Create a unique product with `POST /api/v1/loan-products`.
  - List it with `GET /api/v1/loan-products` and request a quote using its code.

- [ ] **Step 5: Verify negative security paths.**
  - No token on create returns `401`.
  - `customer1` token on create returns `403`.
  - Duplicate product code returns `409`.
  - Invalid login returns `401` without password/token leakage.
