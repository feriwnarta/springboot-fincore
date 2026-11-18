# Spring Boot and Maven Bootstrap Design

**Date:** 2026-11-17  
**Status:** Approved baseline design  
**Scope:** Project bootstrap only; business flows and infrastructure integrations remain later milestones.

## Goal

Create a compilable Maven multi-module Spring Boot baseline using Java 21, with three independently runnable services required by the FinCore specification: `loan-service`, `ledger-service`, and `search-service`.

## Decisions

- Use the Java 21 LTS toolchain already available on this machine.
- Install Maven through Homebrew because `mvn` is not currently on `PATH`.
- Pin the Spring Boot parent version in the root `pom.xml`; do not use a floating `latest` version.
- Use one root Maven parent and three child modules under `services/`.
- Keep each service independently executable with its own `main` class, application configuration, health endpoint, and smoke-level context-load test.
- Add only the Spring Boot dependencies needed for the baseline: web, actuator, validation, and test support. Kafka, Redis, SQL, Elasticsearch, and MongoDB dependencies will be introduced in their implementation milestones.
- Keep the initial setup free of business behavior and external-service startup requirements so all three modules can compile and test locally before infrastructure is added.

## Target structure

```text
spring-boot-fincore/
├── pom.xml
├── .mvn/
│   └── jvm.config
├── services/
│   ├── loan-service/
│   │   ├── pom.xml
│   │   └── src/main/java/.../LoanServiceApplication.java
│   ├── ledger-service/
│   │   ├── pom.xml
│   │   └── src/main/java/.../LedgerServiceApplication.java
│   └── search-service/
│       ├── pom.xml
│       └── src/main/java/.../SearchServiceApplication.java
└── docs/superpowers/
    ├── specs/...
    └── plans/...
```

## Verification

The bootstrap is complete when:

1. `java -version` reports Java 21 for the project shell.
2. `mvn -version` reports Maven and Java 21.
3. `mvn test` succeeds from the repository root.
4. Each service context-load test succeeds independently.
5. Each service exposes Actuator health on its own port without requiring PostgreSQL, Kafka, Redis, Elasticsearch, or MongoDB.

## Out of scope

- Loan, ledger, repayment, and savings domain logic.
- Docker Compose and infrastructure containers.
- Kafka topics, outbox/inbox, Redis caching, SQL migrations, Elasticsearch mappings, and MongoDB projections.
- Production security and deployment configuration.

