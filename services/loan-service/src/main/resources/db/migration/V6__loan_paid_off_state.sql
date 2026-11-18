ALTER TABLE loans DROP CONSTRAINT loans_status_check;

ALTER TABLE loans
    ADD CONSTRAINT loans_status_check
        CHECK (status IN ('APPROVED', 'FUNDING_PENDING', 'FUNDED', 'FUNDING_FAILED', 'PAID_OFF'));
