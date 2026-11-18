# Keycloak verification — 2026-11-18

Task 7 verification ran on macOS arm64 with Bash 3.2, curl 8.7.1, jq 1.7.1, Docker Desktop, Compose 5.1.4,
Java 21.0.9, and Maven 3.10.1. The local suite used the `fincore` demo realm.

## Scripts and rendered Compose

| Command | Observed result |
|---|---|
| `bash -n scripts/smoke.sh` and `bash -n scripts/keycloak-token.sh` | Both exited 0. |
| `python3 scripts/test-keycloak-token.py` | 9 passed, including URL-glob rejection, JWT-shape validation, error redaction and redirect handling. |
| `python3 <temporary-verification-dir>/test_smoke.py` | A loopback HTTP fixture failed against the original script because it acquired one token and made only two API calls. The updated script passed 9 scenarios covering the full call sequence, independent run keys, supplied tokens and a replay key, unsafe bearer input, redirect and unexpected 2xx rejection, mismatched envelope and header IDs, and error-body redaction. The fixture ran the script under `bash -x` and checked that its dummy bearer values stayed out of the output. |
| `docker compose config -q` | Exited 0. |
| `docker compose -f docker-compose.yml -f docker-compose.scale.yml --profile scale config -q` | Exited 0. |
| `python3 scripts/test-compose-network.py` | 2 passed for the base and scale configurations. |
| `python3 <temporary-verification-dir>/test_wiring.py` | Initially failed on the missing loan Keycloak dependency. After the edits, rendered JSON checks passed for service OAuth2 mode, issuer, audience and internal JWKS, Keycloak readiness dependencies, and PostgreSQL, Keycloak and Kafka loopback bindings in both configurations. |

## Maven and Testcontainers

The full reactor used `JAVA_HOME=$(/usr/libexec/java_home -v 21) mvn -q -am verify`. It exited 0. Counts come
from Surefire XML written during the run.

| Module | Tests | Failures | Errors | Skipped |
|---|---:|---:|---:|---:|
| `platform-support` | 34 | 0 | 0 | 0 |
| `loan-service` | 128 | 0 | 0 | 0 |
| `ledger-service` | 79 | 0 | 0 | 0 |
| `search-service` | 82 | 0 | 0 | 0 |
| **Total** | **323** | **0** | **0** | **0** |

The run covers loan submission ownership, staff-only decisions, funding and repayment, savings ownership and
idempotency, search query scoping, activity ownership, and propagation of verified JWT identity to audit fields.

The following integration commands ran with Java 21 and a 240-second outer deadline per command. Both exited 0.
Counts come from fresh Failsafe XML and exclude Surefire tests rerun by the integration commands.

```bash
mvn -q -pl services/ledger-service -am -Pintegration-tests \
  -Dit.test=SavingsServiceIT,LedgerOutboxRelaySchemaIT verify
mvn -q -pl services/loan-service -am -Pintegration-tests \
  -Dit.test=JdbcLoanOutboxRelayRepositoryIT,LoanOutboxRelaySchemaIT,PostgresLoanAnalyticsRepositoryIT,KafkaLoanEventPublisherIT,RedisCachedLoanProductRepositoryIT verify
```

| Integration suite | Tests | Failures | Errors | Skipped |
|---|---:|---:|---:|---:|
| Ledger PostgreSQL | 4 | 0 | 0 | 0 |
| Loan PostgreSQL, Kafka and Redis | 8 | 0 | 0 | 0 |
| **Total** | **12** | **0** | **0** | **0** |

## Compose startup and recovery

`docker compose -f docker-compose.yml -f docker-compose.scale.yml --profile scale up -d --wait --wait-timeout 180`
initially exited 1 because host port `127.0.0.1:5432` was occupied. The same command with `POSTGRES_HOST_PORT=15432`
exited 0 and all 11 containers reported healthy. This restored the previously used PostgreSQL host port and reused
the existing volumes.

On that stack, `./scripts/smoke.sh` returned `200`, `401`, `403`, and `200` for its first four checks, then
stopped when savings creation returned `403`. A claim-only inspection of freshly obtained tokens confirmed the
expected issuer, audience, subject presence, customer IDs, and roles. Inspection of the existing ledger image's
application JAR showed that its security configuration and savings service predated customer access. The stack
needed images rebuilt from the active workspace.

```bash
POSTGRES_HOST_PORT=15432 docker compose -f docker-compose.yml -f docker-compose.scale.yml \
  --profile scale up --build -d --wait --wait-timeout 180
docker compose -f docker-compose.yml -f docker-compose.scale.yml --profile scale ps -a \
  --format 'table {{.Service}}\t{{.Status}}'
docker compose exec -T kong kong health
```

The rebuild and startup command exited 0. Docker reported one Elasticsearch OOM exit during the build. Compose
started it again, it became healthy and search started afterwards. The first post-build smoke attempt got a
transient Kong `502` for search. Kong's error log showed connection refusal to the previous search container IP.
Once Kong and direct search both returned `401` for the anonymous probe, the complete smoke passed on retry.

The final `ps -a` and a runtime Docker inspection after the smoke showed 11 healthy containers. These were
PostgreSQL, Keycloak, Kafka, Redis, Elasticsearch, MongoDB, Kong, loan, the scale loan replica, ledger, and search.
Kong's health command also passed. Runtime inspection confirmed the four non-root Spring containers' expected
OAuth2 mode, issuer, audience and internal JWKS URL. PostgreSQL (`15432`), Keycloak (`8180`) and Kafka (`9092`) were
bound to IPv4 loopback, Elasticsearch and MongoDB published no host ports, and Kong published only port `8000`.

## Live authentication and ownership

`./scripts/smoke.sh` exited 0 against Kong on port `8000`. It acquired all three tokens from the local Keycloak
realm using the token helper. Every check validated the expected envelope and matching correlation and request IDs
in response headers and metadata.

| Method and route | Identity | Observed status |
|---|---|---:|
| `GET /api/v1/loan-products?limit=10` | Anonymous | 200 |
| `GET /api/v1/search/loans?limit=10` | Anonymous | 401 |
| `GET /api/v1/reports/loans/top-customers` | customer1 | 403 |
| `GET /api/v1/search/loans?limit=10` | loan-operator | 200 |
| `POST /api/v1/savings/accounts` | customer1 | 201 |
| `GET /api/v1/savings/accounts/{accountId}` | customer1 | 200 |
| `GET /api/v1/savings/accounts/{accountId}` | customer2 | 403 |

The temporary live verification harness ran as `python3 <temporary-verification-dir>/verify_live.py
<temporary-verification-dir>/smoke-rebuilt.log` and exited 0. Its bounded curl requests returned the following results:

- Direct loan (`8081`) returned `200` for public products, `401` for anonymous reports and `403` for customer reports.
- Direct search (`8083`) returned `401` for anonymous search and `200` for operator search.
- Direct ledger (`8082`) returned `200` for the owner's account read and `403` for the second customer's read.
- Kong returned `404` for `/__fincore_smoke_unmatched__`.
- Keycloak discovery on `8180` returned `200` with issuer `http://localhost:8180/realms/fincore`. A curl request
  from the search container to `http://keycloak:8080/realms/fincore/protocol/openid-connect/certs` returned signing keys.

The harness ran `./scripts/smoke.sh` again with pre-supplied tokens and the original `SMOKE_IDEMPOTENCY_KEY`. It
returned the same savings account UUID `c17d8643-031c-4cef-bb77-736ba4303bdd`.

## Audit identity

The live harness queried `ledger_audit_events` using `docker compose exec -T postgres psql -X -U fincore
-d fincore_ledger -Atqc` and filtered by the verified savings account UUID, aggregate type `SavingsAccount` and
action `OPEN`. Exactly one row existed after the original run and replay. Its `actor_id` matched customer1's JWT
`sub`, its `actor_role` was `CUSTOMER`, and its outcome was `SUCCESS`. The `correlation_id`
`4aa68c53-71ca-449d-854b-9be156857040` matched the smoke run. Its `causation_id`
`714b046a-05db-4e15-a4e8-133960d03983` matched the create request ID.

## Verification limits

The live run reused the existing local volumes. It left one zero-balance demo savings account for audit
inspection. The integration totals cover the selected loan and ledger Testcontainers suites. Loan and search
ownership coverage in the Maven run comes from the security, service and projection tests. The live smoke
exercises savings ownership and operator search. The full MVP acceptance suite and the final security review
remain pending.

Service-log and Kafka correlation/redaction acceptance is intentionally not marked complete in the implementation plan.
The checks above establish HTTP correlation headers, audit correlation/causation fields, and that the smoke harness does
not print bearer values; they do not constitute the representative AT-27/AT-30 service-log and Kafka evidence required
for final acceptance.
