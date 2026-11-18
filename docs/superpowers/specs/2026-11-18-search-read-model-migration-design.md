# Search Read Model Migration Design

## Goal

Move list/read endpoints for loan products, loan applications, and savings accounts to `search-service` backed by Elasticsearch, while keeping write commands and transactional source-of-truth data in their owning services.

## Scope

The Search Service will expose these read contracts through Kong:

- `GET /api/v1/loan-products`
- `GET /api/v1/loan-applications`
- `GET /api/v1/savings/accounts`

Existing write endpoints remain unchanged:

- Loan product/application writes remain in `loan-service`.
- Savings account/deposit/withdrawal/status writes remain in `ledger-service`.
- Existing loan search and loan activity endpoints remain available.

## Architecture

Loan and ledger databases remain the system of record. Their transactional commands append versioned outbox events in the same local transaction. Kafka relays those events to `search-service`, which maintains Elasticsearch read models. Search endpoints never query the transactional databases and therefore are eventually consistent.

The three Elasticsearch projections are:

- `fincore-loan-products-v1`: active/inactive product catalog fields.
- `fincore-loans-v1`: loan application fields and lifecycle status; extend the existing loan projection instead of creating a second loan index.
- `fincore-savings-accounts-v1`: account ownership, account number, status, currency, balance, and version.

Projection consumers must be idempotent and ignore stale aggregate versions. Existing loan activity projection behavior remains unchanged.

## API and authorization

The response envelope and pagination metadata remain consistent with the current APIs. Cursors are opaque page tokens for
the bounded Elasticsearch page query.

- Loan products are public reads and return active products by default.
- Loan applications require `CUSTOMER`, `LOAN_OPERATOR`, or `LOAN_APPROVER`. Customers are always constrained to their JWT `customer_id`; staff may use status, customer, and product filters.
- Savings accounts require `CUSTOMER` or staff roles. Customers are constrained to their JWT `customer_id`; `SAVINGS_OPERATOR`, `LOAN_OPERATOR`, and `LOAN_APPROVER` may search across customers.
- Invalid cursors, limits, and filters use the existing standard error envelope.
- If a valid resource has not reached Elasticsearch, list responses may be empty; detail projection misses continue to use `PROJECTION_NOT_READY`.

## Event contracts

The existing loan topic carries loan lifecycle events. Add only the missing product event needed for the product projection. The ledger topic carries savings account events. Required projection events are:

- Loan: `LoanSubmitted`, `LoanApproved`, `LoanRejected`, `LoanFunded`.
- Product: `LoanProductCreated` containing the current `active` value. Product update commands are not part of the current API, so no separate update event is added in this migration.
- Savings: `SavingsAccountOpened`, `SavingsDepositPosted`, `SavingsWithdrawalPosted`, and `SavingsAccountStatusChanged`.

Each event contains `eventId`, `eventType`, `schemaVersion`, `aggregateId`, `aggregateVersion`, `occurredAt` or relay timestamp, correlation metadata, and the projection payload. Existing outbox relay and processed-event patterns are reused.

## Routing and Swagger

Kong routes GET requests for the three list paths to `search-service`, while POST routes remain owned by loan/ledger services. Search OpenAPI documents the three list endpoints. Loan and Ledger OpenAPI documents document only their command/detail contracts for these resources.

## Delivery and verification

Implementation is incremental and test-first:

1. Add failing projection/parser/service/controller tests.
2. Add minimal event publication and Elasticsearch projection code.
3. Add Search Service endpoints and ownership/pagination tests.
4. Update Kong routes and OpenAPI assertions.
5. Backfill current local products, applications, and accounts by replaying outbox events or rebuilding projections without changing source data.
6. Run Maven verification, Testcontainers/integration tests, OpenAPI checks, and live gateway checks.

Out of scope for this migration: replacing Elasticsearch with another store, changing command contracts, adding a new event platform, or adding advanced ranking/search features.
