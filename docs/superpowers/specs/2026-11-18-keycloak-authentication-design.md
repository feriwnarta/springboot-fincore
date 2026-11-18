# Keycloak Authentication and Authorization Design

**Date:** 2026-11-18
**Status:** Approved design for implementation
**Scope:** Local Docker Compose authentication with a production-compatible OIDC contract.

## Goal

Replace the current synthetic `X-Actor-Id` and `X-Actor-Role` identity mechanism with Keycloak-issued JWT access tokens while preserving service-level authorization, DTO response contracts, audit metadata, and local developer ergonomics.

## Current state

- `loan-service` and `ledger-service` already include Spring Security.
- Compose currently enables a synthetic security filter that trusts `X-Actor-Id` and `X-Actor-Role`.
- `search-service` does not yet enforce service authentication.
- Kong is DB-less and performs routing only.
- Loan and savings records already contain customer identifiers needed for ownership checks.
- Audit records already capture actor, role, correlation, causation, event, and business-reference metadata.

The synthetic-header mode remains available only for isolated unit-test fixtures. Packaged services do not include the
synthetic filter and cannot enable it; Compose's normal local stack uses the Keycloak JWT mode.

## Architecture

```text
User or local CLI
        |
        | OIDC token request
        v
Keycloak realm: fincore
        |
        | JWT access token
        v
Kong :8000 ---------------> Spring service :808x
                                |
                                | issuer + signature + audience + role validation
                                v
                         Service authorization and ownership policy
```

Kong remains a routing boundary, not the only authorization boundary. Every Spring service validates the bearer token independently. This prevents a direct request to a service port from bypassing authentication and authorization.

## Keycloak model

### Realm and clients

- Realm: `fincore`.
- API audience/client: `fincore-api`.
- Local CLI client: `fincore-cli`, enabled for a local password grant only in the imported development realm.
- Production browser or mobile clients are external clients and use Authorization Code with PKCE; credentials are not stored in this repository.
- The local realm is imported from `docker/keycloak/realm-fincore.json`.
- Local admin credentials come from environment variables and `.env`; no production secret is committed.

### Roles

Roles are client roles under `fincore-api` and are mapped to Spring authorities:

| Keycloak role | Intended access |
|---|---|
| `CUSTOMER` | Own loan and savings resources |
| `LOAN_OPERATOR` | Loan operational writes and loan workflow actions |
| `LOAN_APPROVER` | Loan approval and rejection |
| `SAVINGS_OPERATOR` | Savings operational writes |

The JWT converter maps `resource_access.fincore-api.roles` to authorities with the `ROLE_` prefix. Service rules use `hasAuthority` or `hasAnyAuthority` consistently and do not trust role headers.

### Identity and ownership claims

- `sub` is the immutable actor identity and is used in audit records.
- `customer_id` is a protocol-mapped claim for customer users.
- Operator tokens do not require `customer_id`.
- A customer operation is authorized only when `customer_id` matches the target record's `customerId`.
- Missing or malformed ownership claims fail closed with `403 FORBIDDEN`.

## Spring Security design

Each service receives:

- `spring-boot-starter-oauth2-resource-server`.
- A JWT decoder configured with `issuer-uri` and audience validation.
- A shared-style JWT authority converter implemented in the service security configuration.
- A custom authentication entry point and access-denied handler that return the standard `ApiResponse` error envelope.
- A `CurrentIdentity` abstraction that exposes subject, roles, optional customer ID, and ownership checks without leaking Spring Security types into the domain layer.

Security modes:

```yaml
fincore:
  security:
    mode: oauth2
    issuer-uri: ${OIDC_ISSUER_URI:http://localhost:8180/realms/fincore}
    audience: ${OIDC_AUDIENCE:fincore-api}
```

- `oauth2`: default Compose mode; bearer JWT is mandatory for protected operations.
- `synthetic`: test-only compatibility mode; headers can create a synthetic identity.
- No production profile may use `synthetic`.

Public endpoints remain limited to actuator health/readiness and OpenAPI documentation. Product listing and quote endpoints may remain public only where the existing API contract explicitly defines them as public; all financial writes and customer data reads require a valid token.

## Authorization and ownership rules

### Loan service

- `LOAN_OPERATOR` may submit, fund, and request repayment on behalf of an authorized workflow.
- `LOAN_APPROVER` may approve or reject applications.
- `CUSTOMER` may submit an application only for its own `customer_id`.
- Customer loan and repayment reads/writes must verify the loan's stored `customer_id` before the operation proceeds.
- Reports and operational endpoints remain operator-only.

### Ledger service

- `SAVINGS_OPERATOR` may open accounts and perform deposits, withdrawals, and status transitions.
- `CUSTOMER` may open, read, and transact only on its own savings accounts.
- Journal queries are operator-only because they expose accounting evidence.
- Savings ownership is checked after loading the account and before returning or mutating it.

### Search service

- Search endpoints require authentication when they can expose customer-linked data.
- A customer query is constrained to its own `customer_id`.
- Operators may query according to their operational role.
- Search remains a projection and never becomes the financial authorization source of truth.

## Error and audit behavior

- Missing bearer token: HTTP `401`, standard `ApiResponse` with `UNAUTHORIZED`.
- Invalid, expired, wrong issuer, or wrong audience token: HTTP `401`, without exposing JWT validation details.
- Valid token with insufficient role or ownership: HTTP `403`, standard `ApiResponse` with `FORBIDDEN`.
- Authentication failures never log raw tokens or authorization headers.
- Audit actor identity comes from JWT `sub`; actor role comes from mapped authorities.
- Correlation and request IDs continue to be generated and propagated through HTTP, Kafka, and audit records.

## Compose and environment

Compose adds a pinned Keycloak image, a separate logical PostgreSQL database, realm import, health checks, and port `8180`. Services receive an issuer and audience through environment variables. The local issuer configuration is explicit and may be replaced by a production Keycloak, Auth0, Okta, or another OIDC provider without changing service domain code.

The local realm contains non-production demo users only. Production deployment must provision users, clients, credentials, TLS, and secret values outside the repository.

## Testing strategy

1. Unit-test JWT authority and identity conversion.
2. Test `401` for missing/invalid tokens.
3. Test `403` for invalid roles and ownership mismatch.
4. Test successful operator and customer access.
5. Test that audit entries use JWT identity rather than request headers.
6. Add a Compose smoke flow that obtains a local token from Keycloak and calls Kong.
7. Keep existing domain, Saga, outbox, Redis, Kafka Streams, Flyway, and Testcontainers tests green.

## Non-goals

- Implementing a custom login UI.
- Storing passwords in FinCore databases.
- Making Kong the sole authorization engine.
- Replacing Keycloak with a custom token issuer.
- Adding fine-grained policy administration beyond roles and customer ownership in this increment.

## Acceptance criteria

- `docker compose up --build -d` starts Keycloak and all services healthy.
- A local demo user can obtain a JWT from Keycloak.
- Kong accepts a valid bearer token and routes to the target service.
- Direct protected service requests without a token return standardized `401`.
- Wrong-role requests return standardized `403`.
- Customer ownership mismatch returns standardized `403`.
- Audit records identify the JWT subject and role.
- `mvn -q -am verify` and the relevant Testcontainers integration tests pass.
- No production code depends on `X-Actor-Id` or `X-Actor-Role` for identity.
