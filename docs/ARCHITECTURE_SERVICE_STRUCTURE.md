# FinCore Service Structure Standard

Every FinCore service uses the same package boundary. A service may have fewer classes while it is still a skeleton, but new code must be placed under the corresponding boundary.

```text
com.fincore.<service>/
├── <Service>ServiceApplication.java
├── config/
├── api/
│   ├── controller/
│   ├── dto/
│   │   ├── request/
│   │   └── response/
│   ├── mapper/
│   └── advice/
├── application/
│   ├── command/
│   ├── port/
│   ├── query/
│   └── service/
├── domain/
│   ├── model/
│   ├── enums/
│   ├── service/
│   └── port/
├── infrastructure/
│   ├── persistence/
│   │   └── jdbc/
│   ├── messaging/
│   ├── audit/
│   └── search/
└── exception/
```

## Boundary rules

- Controllers depend on application services and transport DTOs only.
- Request/response DTOs never cross into domain or persistence code.
- Application services orchestrate use cases and transactions.
- Domain models, policies, and ports do not depend on Spring MVC, JDBC, Kafka, Redis, or Elasticsearch.
- Concrete domain policies are registered as beans from `config`; domain implementations themselves do not carry Spring stereotypes.
- Business-state ports are owned by the domain; query/read-model ports may be owned by the application layer when they return application projections. Adapters live under infrastructure.
- JDBC repositories, outbox, idempotency, and audit adapters belong under `infrastructure.persistence.jdbc` or `infrastructure.audit`.
- Kafka producers/consumers belong under `infrastructure.messaging`; event payload types belong under `application` or a dedicated event-contract package when versioning requires it.
- Elasticsearch and MongoDB projections belong under `infrastructure.search` and never become the system of record.
- Shared HTTP envelope, correlation IDs, request logging, and common exception handling remain in `platform-support`.
- Tests mirror production boundaries: `api`, `application`, `domain`, and `infrastructure`.

## Current migration template

`loan-service` is the first complete template. `ledger-service` and `search-service` start with the same package boundaries as their use cases are implemented. This standardization changes package organization only; it must not change API contracts, database schemas, transaction behavior, or event semantics.
