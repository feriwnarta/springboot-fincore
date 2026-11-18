# Keycloak Authentication Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task with verification checkpoints.

**Goal:** Replace synthetic actor headers with Keycloak-issued JWT authentication, service-level role authorization, and customer ownership checks while keeping the local Compose flow production-compatible.

**Architecture:** Keycloak issues JWTs for the `fincore` realm. Kong routes requests. Each Spring service validates issuer, signature, and audience independently, maps Keycloak client roles to authorities, and applies ownership rules using the JWT `customer_id` claim. Synthetic identities are confined to test fixtures.

**Tech Stack:** Keycloak 26.8.2, Spring Boot 4.1.2, Spring Security OAuth2 Resource Server, JWT/JWK, Docker Compose, PostgreSQL, Kong 3.9.0, JUnit 6, MockMvc, Testcontainers.

---

### Task 1: Add shared security identity and JWT support

**Files:**
- Modify: `libraries/platform-support/pom.xml`
- Create: `libraries/platform-support/src/main/java/com/fincore/platform/security/CurrentIdentity.java`
- Create: `libraries/platform-support/src/main/java/com/fincore/platform/security/SpringCurrentIdentity.java`
- Create: `libraries/platform-support/src/main/java/com/fincore/platform/security/KeycloakJwtGrantedAuthoritiesConverter.java`
- Create: `libraries/platform-support/src/main/java/com/fincore/platform/security/AudienceValidator.java`
- Create: `libraries/platform-support/src/main/java/com/fincore/platform/security/SecurityFailureResponseWriter.java`
- Test: `libraries/platform-support/src/test/java/com/fincore/platform/security/KeycloakJwtGrantedAuthoritiesConverterTest.java`
- Test: `libraries/platform-support/src/test/java/com/fincore/platform/security/SpringCurrentIdentityTest.java`

- [ ] **Step 1: Add the resource-server dependencies.**

Add the Spring Security core and OAuth2 resource-server/Jose dependencies to `platform-support` so the shared security types are available to every service through the existing platform-support dependency:

```xml
<dependency>
    <groupId>org.springframework.security</groupId>
    <artifactId>spring-security-core</artifactId>
</dependency>
<dependency>
    <groupId>org.springframework.security</groupId>
    <artifactId>spring-security-oauth2-resource-server</artifactId>
</dependency>
<dependency>
    <groupId>org.springframework.security</groupId>
    <artifactId>spring-security-oauth2-jose</artifactId>
</dependency>
```

- [ ] **Step 2: Write the failing authority-converter test.**

The test must construct a JWT with:

```json
{
  "resource_access": {
    "fincore-api": {
      "roles": ["CUSTOMER", "SAVINGS_OPERATOR"]
    }
  }
}
```

and assert that conversion returns `ROLE_CUSTOMER` and `ROLE_SAVINGS_OPERATOR`, ignoring roles from another client.

Run:

```bash
export JAVA_HOME=/opt/homebrew/Cellar/openjdk@21/21.0.9/libexec/openjdk.jdk/Contents/Home
export PATH="$JAVA_HOME/bin:$PATH"
mvn -q -pl libraries/platform-support -Dtest=KeycloakJwtGrantedAuthoritiesConverterTest test
```

Expected: FAIL because the converter does not exist.

- [ ] **Step 3: Implement the identity contract and converter.**

Use this contract:

```java
public interface CurrentIdentity {
    String subject();
    Optional<String> customerId();
    boolean hasRole(String role);
    void requireCustomerAccess(String customerId);
}
```

`KeycloakJwtGrantedAuthoritiesConverter` must read only `resource_access.fincore-api.roles` and emit `ROLE_`-prefixed authorities. `SpringCurrentIdentity` must read the current `Authentication` from `SecurityContextHolder`, derive `subject` from `Authentication.getName()`, read `customer_id` only from a `JwtAuthenticationToken`, and throw the shared authorization exception when a customer identity accesses another customer.

`SecurityFailureResponseWriter` must serialize the existing `ApiResponse.failure(...)` envelope with `UNAUTHORIZED` or `FORBIDDEN`, preserve/generate correlation and request IDs, and never include token validation details.

- [ ] **Step 4: Add identity and failure-writer tests.**

Cover a JWT customer identity, an operator identity without `customer_id`, matching ownership, mismatched ownership, and the standard error envelope.

Run:

```bash
mvn -q -pl libraries/platform-support test
```

Expected: PASS.

- [ ] **Step 5: Run all existing tests before service changes.**

```bash
mvn -q -am test
```

Expected: PASS.

### Task 2: Add Keycloak realm and Compose configuration

**Files:**
- Modify: `docker/postgres/init/01-create-databases.sql`
- Create: `docker/keycloak/realm-fincore.json`
- Create: `.env.example`
- Modify: `docker-compose.yml`
- Modify: `README.md`
- Test: `scripts/keycloak-token.sh` shell syntax and live token request

- [ ] **Step 1: Define the local realm fixture.**

Create `docker/keycloak/realm-fincore.json` with:

- realm `fincore` enabled;
- client `fincore-api` with client roles `CUSTOMER`, `LOAN_OPERATOR`, `LOAN_APPROVER`, `SAVINGS_OPERATOR`;
- client `fincore-cli` for local-only password-grant smoke testing;
- audience mapper that adds `fincore-api` to the `aud` claim;
- `customer_id` user-attribute mapper;
- the `basic` default client scope on `fincore-cli` to provide the subject claim
- demo users `customer1` (`CUST-001`), `customer2` (`CUST-002`), `loan-operator`, `loan-approver` and `savings-operator`
- development-only passwords with `temporary: false` for the demo password grant

The realm file must contain no production credentials. The fixture passwords are documented as local-only demo values and are overridden or removed in production.

- [ ] **Step 2: Write a failing token smoke script.**

Create `scripts/keycloak-token.sh` with strict shell flags. It must accept `KEYCLOAK_URL`, `KEYCLOAK_REALM`, `KEYCLOAK_CLIENT_ID`, `KEYCLOAK_USERNAME`, and `KEYCLOAK_PASSWORD`, call the token endpoint, fail on HTTP errors, and print only the access token. The first test run must fail because Keycloak is not in Compose yet.

- [ ] **Step 3: Add the Keycloak database and service.**

The PostgreSQL init SQL provisions `fincore_keycloak` with its dedicated non-superuser owner. Compose starts
`quay.io/keycloak/keycloak:26.8.2` with `start-dev --import-realm`, takes database and bootstrap credentials from
the local Compose settings, and mounts the realm fixture read-only. It sets the external hostname to
`http://localhost:8180` and binds the container's port `8080` to `127.0.0.1:8180`. Readiness uses the
container's management endpoint on port `9000`.

- [ ] **Step 4: Add `.env.example` and documentation.**

The local OAuth2 settings are:

```dotenv
OIDC_ISSUER_URI=http://localhost:8180/realms/fincore
OIDC_JWK_SET_URI=http://keycloak:8080/realms/fincore/protocol/openid-connect/certs
OIDC_AUDIENCE=fincore-api
```

Document `cp .env.example .env`, the local-only nature of the demo realm, the Keycloak admin console at port `8180`, and the token script.

- [ ] **Step 5: Run Compose validation and token smoke.**

```bash
docker compose config -q
docker compose up -d --wait --wait-timeout 180 keycloak
set +x
token=$(./scripts/keycloak-token.sh) && test -n "$token"
unset token
```

Expected: Compose config passes and a non-empty JWT is returned.

### Task 3: Convert loan and ledger security to JWT resource servers

**Files:**
- Modify: `services/loan-service/pom.xml`
- Modify: `services/ledger-service/pom.xml`
- Modify: `services/loan-service/src/main/resources/application.yml`
- Modify: `services/ledger-service/src/main/resources/application.yml`
- Modify: `services/loan-service/src/main/java/com/fincore/loan/config/SecurityConfiguration.java`
- Modify: `services/ledger-service/src/main/java/com/fincore/ledger/config/SecurityConfiguration.java`
- Create: `services/loan-service/src/test/java/com/fincore/loan/config/SecurityConfigurationTest.java`
- Create: `services/ledger-service/src/test/java/com/fincore/ledger/config/SecurityConfigurationTest.java`

- [x] **Step 1: Add resource-server dependencies and security mode properties.**

Add `spring-boot-starter-oauth2-resource-server` to loan and ledger. Use the Spring JWT settings for issuer and signing keys:

```yaml
fincore:
  security:
    mode: ${FINCORE_SECURITY_MODE:oauth2}
    audience: ${OIDC_AUDIENCE:fincore-api}
spring:
  security:
    oauth2:
      resourceserver:
        jwt:
          issuer-uri: ${OIDC_ISSUER_URI:http://localhost:8180/realms/fincore}
          jwk-set-uri: ${OIDC_JWK_SET_URI:${spring.security.oauth2.resourceserver.jwt.issuer-uri}/protocol/openid-connect/certs}
```

Compose sets `FINCORE_SECURITY_MODE=oauth2`. Tests use imported identity fixtures or JWT test support.

- [x] **Step 2: Write failing MockMvc security tests.**

For each service, add tests for:

```java
mockMvc.perform(post("/api/v1/loan-applications/{applicationId}/approve", applicationId))
       .andExpect(status().isUnauthorized());

mockMvc.perform(post("/api/v1/loan-applications/{id}/approve", id)
        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_CUSTOMER"))))
       .andExpect(status().isForbidden());
```

Also assert a `ROLE_LOAN_APPROVER` token reaches the controller and that the response has the standard `success/data/error/meta` shape.

Run each targeted test and confirm RED before implementation.

- [x] **Step 3: Implement JWT validation and role mapping.**

In OAuth2 mode configure:

- `oauth2ResourceServer().jwt(...)` with the shared converter;
- issuer validation from `issuer-uri`;
- audience validation for `fincore-api`;
- stateless sessions;
- CSRF disabled for the JSON API;
- actuator health, readiness and OpenAPI public;
- loan approval/rejection restricted to `ROLE_LOAN_APPROVER`;
- loan funding and repayment restricted to `ROLE_LOAN_OPERATOR` or `ROLE_LOAN_APPROVER`;
- loan submission and savings role and ownership checks described in Tasks 5 and 6;
- custom `AuthenticationEntryPoint` and `AccessDeniedHandler` using `SecurityFailureResponseWriter`.

Synthetic identity support lives in the test fixtures.

- [x] **Step 4: Run targeted security tests and existing tests.**

```bash
mvn -q -pl services/loan-service -am -Dtest=SecurityConfigurationTest test
mvn -q -pl services/ledger-service -am -Dtest=SecurityConfigurationTest test
mvn -q -am test
```

Expected: PASS.

### Task 4: Add JWT security to search-service

**Files:**
- Modify: `services/search-service/pom.xml`
- Modify: `services/search-service/src/main/resources/application.yml`
- Create: `services/search-service/src/main/java/com/fincore/search/config/SecurityConfiguration.java`
- Create: `services/search-service/src/test/java/com/fincore/search/config/SecurityConfigurationTest.java`

- [x] **Step 1: Add the resource-server dependency and write RED tests.**

Assert that loan search and activity return `401` without a token and `403` with an ineligible role. Keep actuator
health, readiness and OpenAPI documentation public. Task 6 defines customer-scoped access.

- [x] **Step 2: Implement the same issuer, audience, authority, and failure-envelope rules as loan and ledger.**

Use the shared `CurrentIdentity` and JWT converter. Configure the container JWKS URL separately from the canonical
issuer. Loan staff have cross-customer search and activity access, and customer roles use the ownership checks in Task 6.

- [x] **Step 3: Run search security and full unit tests.**

```bash
mvn -q -pl services/search-service -am -Dtest=SecurityConfigurationTest -Dsurefire.failIfNoSpecifiedTests=false test
mvn -q -am test
```

Expected: PASS.

### Task 5: Enforce loan customer ownership

**Files:**
- Modify: `services/loan-service/src/main/java/com/fincore/loan/application/service/LoanApplicationService.java`
- Modify: `services/loan-service/src/main/java/com/fincore/loan/application/service/LoanFundingService.java`
- Modify: `services/loan-service/src/main/java/com/fincore/loan/application/service/LoanRepaymentService.java`
- Modify: `services/loan-service/src/main/java/com/fincore/loan/api/controller/LoanApplicationController.java`
- Modify: `services/loan-service/src/main/java/com/fincore/loan/api/controller/LoanFundingController.java`
- Modify: `services/loan-service/src/main/java/com/fincore/loan/api/controller/LoanRepaymentController.java`
- Test: `services/loan-service/src/test/java/com/fincore/loan/application/service/LoanApplicationServiceTest.java`
- Test: `services/loan-service/src/test/java/com/fincore/loan/application/service/LoanFundingServiceTest.java`
- Test: `services/loan-service/src/test/java/com/fincore/loan/application/service/LoanRepaymentServiceTest.java`

- [x] **Step 1: Cover loan ownership and staff-only operations.**

Cover customer submission for the exact `customer_id` and a `403` for another customer. Verify staff submissions across
customers, approver-only decisions, and staff-only funding and repayment even when the customer owns the application.

- [x] **Step 2: Use `CurrentIdentity` at the application-service boundary.**

Check the authorized role and customer scope before mutation or replay. Submission permits customers with an exact
claim match and loan staff across customers. Controllers and application services derive actor ID and role for audit
records from the authenticated identity.

- [x] **Step 3: Run ownership tests and the existing Saga tests.**

The Maven reactor verification covered the loan security, submission, funding and repayment suites. See the
[verification evidence](../../evidence/KEYCLOAK_VERIFICATION_2026-11-18.md) for the commands and totals.

### Task 6: Enforce savings and search ownership

**Files:**
- Modify: `services/ledger-service/src/main/java/com/fincore/ledger/application/service/SavingsService.java`
- Modify: `services/ledger-service/src/main/java/com/fincore/ledger/api/controller/SavingsController.java`
- Modify: `services/ledger-service/src/test/java/com/fincore/ledger/application/service/SavingsServiceIT.java`
- Modify: `services/search-service/src/main/java/com/fincore/search/infrastructure/search/LoanActivityDocument.java`
- Modify: `services/search-service/src/main/java/com/fincore/search/infrastructure/messaging/LoanEventProjectionParser.java`
- Modify: `services/search-service/src/main/java/com/fincore/search/application/service/LoanProjectionService.java`
- Modify: `services/search-service/src/main/java/com/fincore/search/application/service/LoanSearchQueryService.java`
- Modify: `services/search-service/src/main/java/com/fincore/search/infrastructure/search/LoanSearchDocumentRepository.java`
- Modify: `services/search-service/src/main/java/com/fincore/search/infrastructure/search/LoanActivityDocumentRepository.java`
- Test: `services/ledger-service/src/test/java/com/fincore/ledger/application/service/SavingsOwnershipTest.java`
- Test: `services/search-service/src/test/java/com/fincore/search/application/service/LoanSearchQueryServiceTest.java`

- [x] **Step 1: Cover savings ownership in regression tests.**

Cover account open, account read, statement, deposit, withdrawal, and status change. A customer with a different `customer_id` must receive `403`; operators retain role-based access.

- [x] **Step 2: Implement savings ownership checks.**

Load the account before returning or mutating it, call `CurrentIdentity.requireCustomerAccess(account.customerId())`, and ensure the check happens before balance mutation, journal creation, audit append, or outbox append.

- [x] **Step 3: Add customer ID to the Mongo activity projection.**

Store `customerId` in `LoanActivityDocument` when processing loan events. Add repository methods that constrain activity lookup by both loan ID and customer ID. Existing documents without a customer ID must not be returned to customer identities.

- [x] **Step 4: Add customer-constrained Elasticsearch queries.**

Add repository methods for customer-filtered combinations of query, status, and product code. `LoanSearchQueryService` must automatically constrain customer searches to the current identity's `customer_id`; operator searches retain existing filters. Activity lookup must enforce the same identity check.

- [x] **Step 5: Run savings, search, and integration tests.**

```bash
mvn -q -am verify
mvn -q -pl services/ledger-service -am -Pintegration-tests -Dit.test=SavingsServiceIT,LedgerOutboxRelaySchemaIT verify
```

Both commands passed. See the [verification evidence](../../evidence/KEYCLOAK_VERIFICATION_2026-11-18.md).

### Task 7: Add Keycloak-aware smoke testing and documentation

**Files:**
- Modify: `scripts/smoke.sh`
- Verify: `scripts/keycloak-token.sh`
- Modify: `docker-compose.yml`
- Modify: `docker-compose.scale.yml`
- Modify: `README.md`
- Modify: `docs/FINCORE_IMPLEMENTATION_PLAN.md`
- Create: `docs/evidence/KEYCLOAK_VERIFICATION_2026-11-18.md`

- [x] **Step 1: Extend smoke test to obtain and use tokens.**

The script must:

1. obtain tokens for both demo customers;
2. obtain an operator token;
3. call a public endpoint;
4. call a protected endpoint without a token and assert `401`;
5. call a protected endpoint with the wrong role and assert `403`;
6. call a valid operator endpoint and assert a successful standard envelope;
7. open a savings account as customer1 using an idempotency key, then verify customer1 can read it;
8. read that account as customer2 and assert `403`.

Do not print access tokens.

- [x] **Step 2: Add Compose dependencies and service environment.**

Set `FINCORE_SECURITY_MODE=oauth2`, `OIDC_ISSUER_URI`, `OIDC_JWK_SET_URI` and `OIDC_AUDIENCE` on all three
Spring services and the optional loan replica. Each instance waits for Keycloak readiness.

- [x] **Step 3: Update README and implementation plan.**

Document Keycloak startup, demo users, token acquisition, role and ownership behavior, direct and Kong access,
production issuer and secrets, internal stores and recovery. Record Task 7 verification separately from final
security acceptance in Task 8.

- [x] **Step 4: Run the Task 7 verification set.**

```bash
docker compose config -q
docker compose -f docker-compose.yml -f docker-compose.scale.yml --profile scale config -q
docker compose -f docker-compose.yml -f docker-compose.scale.yml --profile scale up --build -d --wait --wait-timeout 180
mvn -q -am verify
mvn -q -pl services/ledger-service -am -Pintegration-tests -Dit.test=SavingsServiceIT,LedgerOutboxRelaySchemaIT verify
./scripts/smoke.sh
docker compose -f docker-compose.yml -f docker-compose.scale.yml --profile scale ps -a
```

The live smoke passed after rebuilding local images. Base and scale configuration checks and Maven verification
passed. See the [verification evidence](../../evidence/KEYCLOAK_VERIFICATION_2026-11-18.md) for outcomes and recovery.

### Task 8: Final security review

**Files:**
- Review: `docs/superpowers/specs/2026-11-18-keycloak-authentication-design.md`
- Review: `docs/superpowers/plans/2026-11-18-keycloak-authentication.md`
- Review: `README.md`
- Review: `docs/FINCORE_IMPLEMENTATION_PLAN.md`

- [ ] **Step 1: Search for forbidden identity paths.**

Run:

```bash
rg -n "X-Actor-Id|X-Actor-Role|Authorization|access_token|client_secret|password" services docker scripts README.md
```

Confirm that `X-Actor-*` appears only in synthetic test compatibility/documented migration notes, tokens are never logged or printed, and passwords exist only in local fixture/example configuration.

- [ ] **Step 2: Verify the final evidence.**

Confirm the evidence file records `401`, `403`, ownership mismatch, valid token flow, audit identity, Compose health, and Maven/Testcontainers results.

- [ ] **Step 3: Run the final verification command.**

```bash
mvn -q -am verify
```

Expected: PASS with no failing tests.
