ALTER TABLE repayments DROP CONSTRAINT IF EXISTS repayments_status_check;

ALTER TABLE repayments
    ADD CONSTRAINT repayments_status_check
    CHECK (status IN ('PENDING', 'CONFIRMED', 'POSTED', 'FAILED'));

CREATE INDEX idx_loans_funded_customer_amount
    ON loans (customer_id, principal_amount DESC, id)
    WHERE status = 'FUNDED';

CREATE INDEX idx_loans_funded_product_time
    ON loans (product_code, funded_at, id)
    WHERE status = 'FUNDED';

CREATE INDEX idx_repayments_posted_loan_time
    ON repayments (loan_id, posted_at)
    WHERE status = 'POSTED';

CREATE INDEX idx_repayments_posted_time
    ON repayments (posted_at, loan_id)
    WHERE status = 'POSTED';
