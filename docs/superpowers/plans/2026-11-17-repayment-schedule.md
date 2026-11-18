# Repayment Schedule Slice

## Outcome

Generate deterministic repayment schedules after a funding journal acknowledgement,
using the same flat-rate and annuity pricing policies as the quote flow.

## Decisions

- Money remains `BigDecimal` with scale 4 and `HALF_UP` rounding.
- Any principal or interest rounding remainder is applied to the final installment.
- The first due date is one calendar month after `FundingJournalPosted.occurredAt`.
- Schedule persistence uses `(loan_id, installment_no)` as an idempotent database key.
- Schedule generation runs in the loan transaction that consumes the durable funding acknowledgement.

## Evidence

- `RepaymentScheduleGeneratorTest` verifies final-period rounding for flat rate and
  decreasing interest for annuity.
- `RepaymentScheduleService` is called only after the loan ledger-posting projection
  is updated to `POSTED`.
- `JdbcLoanRepaymentScheduleRepository` uses `ON CONFLICT DO NOTHING` for replay safety.

## Follow-up

Payment intent, interest-first allocation, repayment journal posting, and
`RepaymentJournalPosted` acknowledgement remain the next M3 slices.
