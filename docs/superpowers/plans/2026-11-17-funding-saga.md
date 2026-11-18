# Funding Saga End-to-End

## Objective

Implement the MVP funding flow across the independent `loan-service` and
`ledger-service` applications without cross-database writes:

```text
APPROVED
  -> request funding
  -> FUNDING_PENDING
  -> simulator success + loan transaction commits
  -> FUNDED + LoanFunded outbox
  -> ledger consumes LoanFunded through inbox dedupe
  -> balanced funding journal + FundingJournalPosted outbox
  -> loan consumes acknowledgement through inbox dedupe
  -> ledger_posting_status = POSTED
```

The flow must keep `FUNDING_PENDING` when the simulator does not confirm
success, must be safe to replay, and must not claim the ledger is posted before
the acknowledgement is durably applied to the loan database.

## Design decisions

- Funding is a deterministic local simulator for the MVP. It returns success
  only for the normal request and supports an explicit failure mode for tests.
- The public API uses DTOs and the existing `ApiResponse` envelope.
- `LoanFunded` is published on `fincore.loan.events.v1`.
- `FundingJournalPosted` is published on `fincore.ledger.events.v1`.
- Both directions use a database outbox relay. Consumers use an inbox table
  (`processed_events` / `loan_processed_events`) before applying effects.
- Currency is `IDR` for this MVP because multi-currency is explicitly deferred
  by the specification.
- `loans` remains the authoritative funding projection in `loan-service`;
  `loan_applications` remains the application lifecycle projection.

## Implementation checklist

- [x] Record the implementation plan and contract decisions.
- [x] Add the loan funding domain port, simulator service, DTO, endpoint, and
      funding projection repository.
- [x] Emit a versioned `LoanFunded` envelope only after `FUNDED` is persisted.
- [x] Add the ledger outbox relay and `fincore.ledger.events.v1` publisher.
- [x] Add the loan acknowledgement consumer with inbox dedupe and guarded
      `ledger_posting_status` update.
- [x] Add unit and integration coverage for success, failure, duplicates, and
      balanced journal output.
- [x] Update README and implementation plan with the runnable flow.
- [x] Run regular tests, PostgreSQL integration tests, Kafka integration tests,
      and the full reactor verification.

## Verification evidence required

1. Funding failure leaves the loan in `FUNDING_PENDING` or
   `FUNDING_FAILED` without a `LoanFunded` outbox event.
2. A successful funding request persists `FUNDED` and exactly one outbox row.
3. Duplicate `LoanFunded` delivery creates only one journal.
4. Funding journal has one debit and one credit for the same amount/currency.
5. Duplicate `FundingJournalPosted` delivery does not update the loan twice.
6. All API responses remain DTOs inside the standard envelope.
