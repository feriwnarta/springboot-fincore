# OpenAPI Bearer Security Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Enable JWT authorization from Swagger UI for protected loan, ledger, and search operations.

**Architecture:** Each service declares the same local Springdoc `bearerAuth` scheme. Protected controllers opt into that scheme with `@SecurityRequirement`; public operations remain unlocked in the generated contract. A shell contract test validates the live `/v3/api-docs` documents.

**Tech Stack:** Spring Boot 4, Springdoc OpenAPI 3, Swagger annotations, Java 21, Bash, curl, jq.

---

### Task 1: Add the failing OpenAPI contract test

**Files:**
- Create: `scripts/test-openapi-security.sh`

- [ ] **Step 1: Write the failing test**

Create a read-only contract check that calls each service's `/v3/api-docs` endpoint and requires an HTTP Bearer JWT scheme.

- [ ] **Step 2: Run it to verify it fails**

Run `bash scripts/test-openapi-security.sh`. Before the implementation, each service currently returns an empty `components.securitySchemes`, so the check must fail.

### Task 2: Add shared OpenAPI metadata per service

**Files:**
- Create: `services/loan-service/src/main/java/com/fincore/loan/config/OpenApiConfiguration.java`
- Create: `services/ledger-service/src/main/java/com/fincore/ledger/config/OpenApiConfiguration.java`
- Create: `services/search-service/src/main/java/com/fincore/search/config/OpenApiConfiguration.java`

- [ ] **Step 1: Add `@SecurityScheme`**

Declare `bearerAuth` with `SecuritySchemeType.HTTP`, `SecuritySchemeIn.HEADER`, scheme `bearer`, and bearer format `JWT` in each service's existing component scan.

### Task 3: Mark protected API controllers

**Files:**
- Modify loan protected controllers under `services/loan-service/src/main/java/com/fincore/loan/api/controller/`
- Modify `services/ledger-service/src/main/java/com/fincore/ledger/api/controller/SavingsController.java`
- Modify `services/ledger-service/src/main/java/com/fincore/ledger/api/controller/JournalController.java`
- Modify `services/search-service/src/main/java/com/fincore/search/api/controller/LoanSearchController.java`

- [ ] **Step 1: Add `@SecurityRequirement(name = "bearerAuth")`**

Apply it to protected controllers only. Leave `LoanProductController` and `LoanQuoteController` public.

### Task 4: Verify

- [ ] **Step 1: Run the contract test**
- [ ] **Step 2: Run `JAVA_HOME=/opt/homebrew/Cellar/openjdk@21/21.0.9/libexec/openjdk.jdk/Contents/Home mvn -q -am verify`**
- [ ] **Step 3: Inspect generated OpenAPI and Swagger UI**

Expected: each document contains `bearerAuth.type=http`, `scheme=bearer`, and `bearerFormat=JWT`; Swagger UI shows `Authorize` and lock icons on protected operations.
