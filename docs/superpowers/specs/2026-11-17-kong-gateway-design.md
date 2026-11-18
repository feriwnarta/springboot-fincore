# Kong Gateway Integration Design

**Date:** 2026-11-17  
**Status:** Approved direction  
**Scope:** Add a third-party API gateway without adding a fourth Spring Boot business service.

## Goal

Expose the three FinCore Spring Boot services through one local entry point using Kong Gateway, while preserving the existing service ownership and direct service ports for development and diagnostics.

## Architecture

```text
API client
    |
    v
Kong Gateway proxy :8080
    |-- /api/v1/loans/**     -> loan-service:8081
    |-- /api/v1/savings/**   -> ledger-service:8082
    |-- /api/v1/journals/**  -> ledger-service:8082
    |-- /api/v1/search/**    -> search-service:8083
    `-- /api/v1/analytics/** -> search-service:8083

Direct service ports remain available for local debugging:
loan-service :8081, ledger-service :8082, search-service :8083.
```

Kong is an infrastructure container, not a Spring Boot application. The project therefore still has exactly three Spring Boot business services.

## Kong deployment mode

- Use Kong DB-less mode for the local MVP.
- Store the complete gateway state in `infrastructure/kong/kong.yml`.
- Mount the declarative file read-only into the Kong container.
- Expose the proxy on host port `8080`.
- Bind the Admin API to localhost-only development access, or omit its host port mapping in the default Compose profile.
- Pin the Kong image tag instead of using `latest`.

DB-less mode avoids adding a Kong database to the MVP stack and makes gateway configuration reviewable in source control. It is suitable for this single-node local setup; production multi-node coordination will require a separate deployment decision.

## Routing rules

Routes preserve the public API path when forwarding to the service. Kong does not become an owner of business endpoints and does not rewrite domain semantics. Upstream service names resolve through the Docker Compose network.

Initial public paths:

- `/api/v1/loans` and `/api/v1/loans/**` to `loan-service:8081`.
- `/api/v1/reports/loans/**` to `loan-service:8081`.
- `/api/v1/savings/**` and `/api/v1/journals/**` to `ledger-service:8082`.
- `/api/v1/search/**` and `/api/v1/loans/*/activity` to `search-service:8083`.
- `/api/v1/analytics/**` to `search-service:8083`.

Kong should add or preserve a correlation ID for requests, while authentication and authorization remain enforced by the services until a centralized identity design is explicitly added.

## Security and failure boundaries

- Do not expose the Kong Admin API publicly.
- Do not put financial state, balances, or idempotency decisions in Kong plugins.
- Kong proxy failure is an edge availability failure; it must not change the authoritative state guarantees of the three services.
- Direct service access remains a development-only path and should be restricted or removed in a production deployment.
- Rate limiting, JWT/OIDC, CORS, TLS termination, and upstream retries are follow-up gateway policies. They will not be enabled blindly because retries on financial write requests can violate idempotency expectations.

## Verification

The change is complete when:

1. `docker compose config` validates the Kong service and mounted declarative configuration.
2. Kong starts and reports a healthy proxy/Admin status.
3. Requests through `http://localhost:8080` reach the intended service route.
4. Unmatched routes return a controlled `404` from Kong.
5. The three existing services still pass `mvn clean verify` and their direct health endpoints remain available.
6. The main MVP specification records Kong as an optional infrastructure gateway and no longer implies that adding Kong creates a fourth Spring Boot service.

## Out of scope

- Kong Enterprise/Konnect control plane.
- Multi-node gateway clustering.
- Centralized identity provider.
- Gateway-owned business authorization.
- Automatic retries for non-idempotent financial writes.

