# FinCore Service Structure Standardization Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Standardize the package boundaries of all FinCore services while preserving existing REST, database, event, and test behavior.

**Architecture:** Use `loan-service` as the concrete template and align its classes to explicit API, application, domain, and infrastructure subpackages. Add the same boundary marker packages to `ledger-service` and `search-service` without inventing unfinished business logic.

**Tech Stack:** Java 21, Maven, Spring Boot 4.1.2, Spring MVC, Spring JDBC, Flyway, JUnit 5.

---

### Task 1: Publish the package standard

**Files:**
- Create: `docs/ARCHITECTURE_SERVICE_STRUCTURE.md`
- Create: `docs/superpowers/plans/2026-11-17-service-structure-standardization.md`

- [x] Define the target package tree and dependency rules.
- [x] Record that `loan-service` is the first migration template and that ledger/search remain behaviorally unchanged.

### Task 2: Move loan API classes into standard subpackages

**Files:**
- Move controllers to `services/loan-service/src/main/java/com/fincore/loan/api/controller/`
- Move mappers to `services/loan-service/src/main/java/com/fincore/loan/api/mapper/`
- Move request DTOs to `services/loan-service/src/main/java/com/fincore/loan/api/dto/request/`
- Move response DTOs to `services/loan-service/src/main/java/com/fincore/loan/api/dto/response/`
- Modify imports in loan production and test sources.

- [x] Move classes and update package declarations/imports.
- [x] Run `mvn -pl services/loan-service -am test`.
- [x] Confirm all loan API, application, and repository tests pass.

### Task 3: Move loan application/domain/infrastructure classes

**Files:**
- Move use-case services to `application/service/`.
- Move command records to `application/command/`.
- Move `LoanProductPage` to `application/query/`.
- Move domain records to `domain/model/`.
- Move domain enums to `domain/enum/`.
- Move pricing policies to `domain/service/pricing/`.
- Move JDBC adapters to `infrastructure/persistence/jdbc/`.
- Modify all package declarations/imports and mirrored test packages.

- [x] Move classes and update package declarations/imports.
- [x] Run `mvn -pl services/loan-service -am test`.
- [x] Run `mvn clean verify` and confirm all reactor modules pass.

### Task 4: Align ledger and search service skeletons

**Files:**
- Create package marker files under `services/ledger-service/src/main/java/com/fincore/ledger/`.
- Create package marker files under `services/search-service/src/main/java/com/fincore/search/`.
- Modify service documentation if a boundary is not yet used.

- [x] Add `package-info.java` markers for `config`, `api.controller`, `api.dto.request`, `api.dto.response`, `api.mapper`, `application.command`, `application.query`, `application.service`, `domain.model`, `domain.enums`, `domain.service`, `domain.port`, `infrastructure.persistence.jdbc`, `infrastructure.messaging`, `infrastructure.audit`, `infrastructure.search`, and `exception`.
- [x] Keep application classes and runtime behavior unchanged.
- [x] Run `mvn clean verify`.

### Task 5: Verify and document the result

**Files:**
- Modify: `README.md`
- Modify: `docs/FINCORE_IMPLEMENTATION_PLAN.md`

- [x] Document the standard service package tree and migration status.
- [x] Run `mvn clean verify` using Java 21.
- [x] Confirm no service is left running after verification.
