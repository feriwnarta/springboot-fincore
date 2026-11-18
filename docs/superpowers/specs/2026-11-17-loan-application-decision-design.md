# Loan Application Approval and Rejection Design

## Scope

Add explicit approval and rejection commands for a submitted loan application. The feature extends the existing loan application workflow without introducing cross-service coordination yet; Kafka/Saga delivery remains the next milestone.

## API contract

### Approve

`POST /api/v1/loan-applications/{applicationId}/approve`

Headers:

- `Idempotency-Key` required.
- `X-Actor-Id` and `X-Actor-Role` optional with the same defaults as submission.
- Correlation and request IDs use the shared platform filters.

The request body is empty. A successful command returns HTTP `200` and the standard `ApiResponse<LoanApplicationResponse>` envelope.

### Reject

`POST /api/v1/loan-applications/{applicationId}/reject`

Headers are the same as approval. The request body is a DTO with:

```json
{
  "reasonCode": "CREDIT_POLICY",
  "reason": "Debt service ratio is above the approved threshold"
}
```

Both fields are required, and the response is the same standard application response with HTTP `200`.

## State and concurrency rules

- Only `SUBMITTED` applications can be approved or rejected.
- Approval changes status to `APPROVED`, sets `approved_at`, increments `version`, and leaves `rejected_at` null.
- Rejection changes status to `REJECTED`, sets `rejected_at`, increments `version`, and leaves `approved_at` null.
- A missing application returns `404 RESOURCE_NOT_FOUND`.
- Any non-`SUBMITTED` transition returns `409 CONFLICT`.
- Persistence uses an atomic update guarded by `id` and the expected current `version`; a zero-row update is treated as a conflict.

## Transactional side effects

Each command runs in one local transaction:

1. Claim or replay the operation through `idempotency_records`.
2. Update the application state with optimistic locking.
3. Append an audit record (`APPROVE` or `REJECT`).
4. Append `LoanApproved` or `LoanRejected` to `outbox_events`.
5. Mark idempotency as `SUCCEEDED` with the application ID.

The event payload contains only contract-safe fields: application ID, event type, schema version, aggregate version, business reference, product code, principal amount, tenor, and rejection reason fields when applicable. Customer identifiers and raw request bodies are excluded.

## Testing strategy

- Service tests cover successful approval, successful rejection, missing application, invalid state, idempotency replay, and idempotency fingerprint conflict.
- Controller tests cover DTO validation, required idempotency header, HTTP status, and standard response envelope.
- Repository tests cover optimistic-lock SQL behavior using the existing repository test style.
- Full Maven verification and a PostgreSQL smoke test verify Flyway compatibility and one-row audit/outbox/idempotency effects per command.

## Explicit non-goals

- No Kafka producer or consumer is added in this slice.
- No funding or ledger state is changed by approval/rejection.
- No generic `PATCH status` endpoint is introduced; command endpoints preserve business intent and audit semantics.
