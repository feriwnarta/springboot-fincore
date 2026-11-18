ALTER TABLE repayments DROP CONSTRAINT repayments_status_check;

ALTER TABLE repayments
    ADD CONSTRAINT repayments_status_check
        CHECK (status IN ('PENDING_SIMULATION', 'CONFIRMED_AWAITING_LEDGER', 'POSTED', 'FAILED'));

DROP INDEX IF EXISTS uq_repayments_one_pending_per_loan;

CREATE UNIQUE INDEX uq_repayments_one_active_per_loan
    ON repayments (loan_id)
    WHERE status IN ('PENDING_SIMULATION', 'CONFIRMED_AWAITING_LEDGER');
