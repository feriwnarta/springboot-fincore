# FinCore Clean Code Quality Gates Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox syntax for tracking.

**Goal:** Standardize dependency injection, clean code, architecture boundaries, logging, audit, and test quality across the FinCore services without changing business contracts.

**Architecture:** Keep the existing hexagonal boundaries. Required dependencies use Lombok constructor injection; domain remains framework-independent; quality changes are verified through focused tests and the full Maven reactor.

**Tech Stack:** Java 21, Spring Boot 4.1.2, Maven, Lombok, JUnit 5, Spring JDBC, Flyway, Spring Kafka.

---

### Task 1: Establish the quality rules

**Files:**
- Create: `docs/CLEAN_CODE_AND_BEST_PRACTICES.md`
- Modify: `README.md` with a link to the quality rules.

- [x] Document DI, boundaries, financial rules, logging/audit, testing, and review checklist.
- [x] Link the document from the root README.

### Task 2: Standardize constructor injection

**Files:**
- Modify: service, controller, repository, messaging, and configuration classes under `services/*/src/main/java`.
- Test: existing service context and unit tests.

- [x] Convert required dependency fields to `private final`.
- [x] Replace handwritten single constructors with Lombok `@RequiredArgsConstructor` where it improves readability.
- [x] Keep explicit constructors where constructor logic or multiple constructors makes Lombok less clear.
- [x] Remove unnecessary `@Autowired` annotations from single constructors.
- [x] Do not introduce Lombok setters for required dependencies.

### Task 3: Clean the current ledger slice

**Files:**
- Modify: `services/ledger-service/src/main/java/com/fincore/ledger/application/service/`
- Modify: `services/ledger-service/src/main/java/com/fincore/ledger/infrastructure/`
- Modify: `services/ledger-service/src/main/java/com/fincore/ledger/config/`
- Test: existing ledger domain, application, persistence, and parser tests.

- [x] Remove unused imports and fully-qualified annotations where imports improve clarity.
- [x] Keep parser invocation single-pass per Kafka message.
- [x] Keep event parsing, event handling, persistence, and logging in separate classes.
- [x] Keep transaction boundary on the application handler, not the listener.
- [x] Verify duplicate event behavior remains exactly once locally.

### Task 4: Add automated architecture checks

**Files:**
- Modify: root `pom.xml` or the relevant test module configuration.
- Create: architecture tests under the relevant service test source tree.

- [x] Verify API packages do not access infrastructure implementations directly.
- [x] Verify domain packages do not import Spring, JDBC, Kafka, Redis, Elasticsearch, or MongoDB types.
- [x] Verify controllers return DTO/envelope types rather than domain or persistence types.

### Task 5: Verify quality gates

- [x] Run focused tests for every changed component.
- [x] Run `mvn dependency:tree` and inspect duplicate/conflicting dependencies.
- [x] Run `mvn clean verify` with Java 21.
- [x] Record warnings separately from test failures; do not hide build failures.
- [x] Update this plan and the implementation plan with completed items.

### Verification notes

- `mvn clean verify` passes all reactor modules and tests with Java 21.
- `mvn dependency:tree` shows one managed SLF4J API (`2.0.20`) and one managed Logback line (`1.5.38`) in service runtime classpaths; no duplicate logging implementation was introduced.
- Non-blocking warnings remain from unauthorized Flyway repository metadata requests, deprecated APIs in existing tests, and Mockito's dynamic Java agent attachment.

## Known scope boundary

This plan improves implementation quality and does not by itself implement Kafka relay, DLQ, savings, repayment, Elasticsearch, MongoDB, Redis, or Testcontainers. Those remain separate vertical-slice plans.
