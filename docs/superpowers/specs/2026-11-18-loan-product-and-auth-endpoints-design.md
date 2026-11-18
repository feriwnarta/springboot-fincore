# Loan Product and Auth Endpoints Design

## Goal

Expose two capabilities through the existing Kong gateway and Unified Swagger:

1. Create an active loan product through `POST /api/v1/loan-products`.
2. Obtain a Keycloak token through `POST /api/v1/auth/login` and return a stable DTO contract.

## Existing context

- `loan-service` already owns the `loan_products` table and currently exposes only `GET /api/v1/loan-products`.
- Keycloak is the source of truth for users, passwords, roles, issuer, audience, and JWT signing.
- The unified Swagger UI is served through Kong at `http://localhost:8000/docs`.
- API responses use the shared `ApiResponse` envelope and request metadata factory.
- Audit trail support already exists in the platform and loan database migrations.
- The repository is not a Git worktree, so the design and implementation changes cannot be committed from this workspace.

## Architecture

The product endpoint is implemented inside `loan-service`, following the existing API DTO, application service, domain port, and JDBC adapter boundaries. Product creation is a local PostgreSQL transaction, writes an audit record, and returns the created product using the standard response envelope. The endpoint is protected by a dedicated `LOAN_PRODUCT_ADMIN` role; the local Keycloak realm is extended with that role and a demo user.

The auth endpoint is a thin authentication facade in `loan-service`. It accepts username/password DTO input, calls Keycloak's password-token endpoint using a Spring HTTP client, validates the upstream response shape, and maps it to a response DTO. The facade never stores credentials, never logs passwords or tokens, and does not issue or verify JWTs itself. Keycloak remains responsible for authentication and token issuance.

## API contracts

### Create loan product

`POST /api/v1/loan-products`

Required role: `LOAN_PRODUCT_ADMIN`

Required headers:

- `Idempotency-Key`
- Optional `X-Correlation-Id` and `X-Request-Id`

Request DTO:

```json
{
  "code": "KTA-ANNUITY",
  "interestType": "ANNUITY",
  "annualRate": 0.12,
  "minAmount": 1000000,
  "maxAmount": 50000000,
  "active": true
}
```

Validation:

- `code` is required, trimmed, and max 50 characters.
- `interestType` is `FLAT` or `ANNUITY`.
- `annualRate` is non-negative and uses decimal fraction semantics (`0.12` means 12% per year).
- `minAmount` is greater than zero.
- `maxAmount` is greater than or equal to `minAmount`.
- `active` defaults to `true` when omitted.
- A duplicate product code returns the standard `409 CONFLICT` envelope.

Success: `201 Created` with `LoanProductResponse` in `data`.

### Login

`POST /api/v1/auth/login`

Public endpoint; no bearer token is required.

Request DTO:

```json
{
  "username": "customer1",
  "password": "customer1"
}
```

Success: `200 OK` with the standard response envelope:

```json
{
  "success": true,
  "data": {
    "accessToken": "<jwt>",
    "refreshToken": "<refresh-token>",
    "tokenType": "Bearer",
    "expiresIn": 300,
    "refreshExpiresIn": 1800,
    "scope": "openid profile email"
  },
  "error": null,
  "meta": {
    "apiVersion": "v1",
    "correlationId": "<uuid>",
    "requestId": "<uuid>",
    "timestamp": "<iso-8601>",
    "path": "/api/v1/auth/login"
  }
}
```

Invalid credentials or rejected Keycloak authentication return the standard `401 UNAUTHORIZED` envelope. Upstream
availability or malformed token response returns a safe `502 AUTH_PROVIDER_UNAVAILABLE` envelope without exposing
Keycloak response bodies or credentials.

## OpenAPI and gateway

- Both endpoints are declared in loan-service OpenAPI and appear in Unified Swagger.
- The login operation is marked public and the product-create operation requires `bearerAuth`.
- Kong already proxies `/api/v1/loan-products` to loan-service; `/api/v1/auth/login` is added to the same loan route.
- Swagger's `Authorize` button is used for product creation. Login itself is invoked without authorization, then the
  returned `accessToken` is pasted into `Authorize` for protected operations.

## Persistence and audit

- Add a Flyway migration only for Keycloak realm role/user fixture if necessary; the product endpoint persists through
  the application repository rather than direct SQL.
- Product creation uses a unique database constraint on `loan_products.code` as the final duplicate guard.
- The command is idempotent by caller scope and `Idempotency-Key`; replaying the same key and payload returns the same
  result, while reusing it with a different payload returns `409`.
- The audit record includes actor subject, role, operation, product code, correlation ID, request ID, outcome, and
  timestamp. Passwords, access tokens, and refresh tokens are excluded from logs and audit payloads.

## Error handling

- `400 VALIDATION_ERROR` for invalid DTO values.
- `401 UNAUTHORIZED` for missing/invalid credentials or bearer token.
- `403 FORBIDDEN` for a valid token without `LOAN_PRODUCT_ADMIN`.
- `409 CONFLICT` for duplicate code, idempotency conflict, or stale product state.
- `502 AUTH_PROVIDER_UNAVAILABLE` when Keycloak cannot be reached or returns an invalid token response.

## Testing

Tests must cover:

- Product DTO validation and successful creation.
- Duplicate product code and idempotency behavior.
- Product endpoint role authorization and response envelope.
- Login request mapping, successful Keycloak response mapping, invalid credential mapping, upstream failure, and secret
  redaction.
- OpenAPI security metadata and gateway reachability.
- A Compose smoke path: login, authorize Swagger, create product, list product, and quote using the new product.
