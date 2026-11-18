# FinCore final verification

Environment: macOS, Docker Desktop, Java 21, Maven, Spring Boot 4.1.2.

## Automated checks

- `mvn -q -am test` — passed.
- `mvn -q -am verify` — passed.
- `mvn -q -pl services/ledger-service -am -Pintegration-tests -Dit.test=LedgerOutboxRelaySchemaIT,SavingsServiceIT verify` — passed.
- `docker compose config -q` — passed.
- `./scripts/smoke.sh` — passed through Kong on port `8000`.

## Runtime checks

- PostgreSQL, Kafka, Redis, Elasticsearch 9.5.6, MongoDB, loan, ledger, search, and Kong reported healthy in Compose.
- Elasticsearch index `fincore-loans-v1` was created with green status.
- Kong preserved `/api/v1/...` paths when proxying to the services.
- Ledger outbox relay claim returned `aggregate_id` correctly after the SQL fix.

## Scope notes

- Customer ownership enforcement and the complete AT-01 through AT-31 matrix remain explicit follow-up items because the current local API contract exposes operator-facing endpoints and does not yet define a customer authentication/ownership provider.
- Audit tables are append-only by PostgreSQL trigger; the existing integration suite verifies migration and relay behavior, while a dedicated mutation-rejection acceptance test is still recommended before production rollout.
