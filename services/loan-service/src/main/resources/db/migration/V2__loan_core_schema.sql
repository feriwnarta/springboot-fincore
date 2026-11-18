CREATE TABLE loan_products (
    id UUID PRIMARY KEY,
    code VARCHAR(50) NOT NULL UNIQUE,
    interest_type VARCHAR(30) NOT NULL,
    annual_rate NUMERIC(9, 6) NOT NULL CHECK (annual_rate >= 0),
    min_amount NUMERIC(19, 4) NOT NULL CHECK (min_amount > 0),
    max_amount NUMERIC(19, 4) NOT NULL CHECK (max_amount >= min_amount),
    active BOOLEAN NOT NULL DEFAULT TRUE,
    version BIGINT NOT NULL DEFAULT 1 CHECK (version > 0),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE loan_applications (
    id UUID PRIMARY KEY,
    customer_id VARCHAR(100) NOT NULL,
    product_code VARCHAR(50) NOT NULL REFERENCES loan_products (code),
    principal_amount NUMERIC(19, 4) NOT NULL CHECK (principal_amount > 0),
    tenor_months INTEGER NOT NULL CHECK (tenor_months > 0),
    status VARCHAR(30) NOT NULL CHECK (status IN ('SUBMITTED', 'APPROVED', 'REJECTED')),
    version BIGINT NOT NULL DEFAULT 1 CHECK (version > 0),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    approved_at TIMESTAMPTZ,
    rejected_at TIMESTAMPTZ,
    actor_id VARCHAR(100) NOT NULL
);

CREATE INDEX idx_loan_applications_customer
    ON loan_applications (customer_id, created_at DESC);

CREATE TABLE loans (
    id UUID PRIMARY KEY,
    application_id UUID NOT NULL UNIQUE REFERENCES loan_applications (id),
    customer_id VARCHAR(100) NOT NULL,
    product_code VARCHAR(50) NOT NULL REFERENCES loan_products (code),
    principal_amount NUMERIC(19, 4) NOT NULL CHECK (principal_amount > 0),
    status VARCHAR(40) NOT NULL CHECK (status IN ('APPROVED', 'FUNDING_PENDING', 'FUNDED', 'FUNDING_FAILED')),
    ledger_posting_status VARCHAR(40) NOT NULL DEFAULT 'PENDING'
        CHECK (ledger_posting_status IN ('PENDING', 'POSTED', 'FAILED', 'RECONCILIATION_REQUIRED')),
    funded_at TIMESTAMPTZ,
    version BIGINT NOT NULL DEFAULT 1 CHECK (version > 0),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE idempotency_records (
    id UUID PRIMARY KEY,
    caller_scope VARCHAR(100) NOT NULL,
    operation VARCHAR(100) NOT NULL,
    idem_key VARCHAR(200) NOT NULL,
    request_fingerprint VARCHAR(128) NOT NULL,
    result_ref VARCHAR(150),
    status VARCHAR(30) NOT NULL CHECK (status IN ('IN_PROGRESS', 'SUCCEEDED', 'FAILED')),
    response_code INTEGER,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    completed_at TIMESTAMPTZ,
    UNIQUE (caller_scope, operation, idem_key)
);

CREATE TABLE outbox_events (
    event_id UUID PRIMARY KEY,
    aggregate_id UUID NOT NULL,
    aggregate_version BIGINT NOT NULL CHECK (aggregate_version > 0),
    event_type VARCHAR(150) NOT NULL,
    schema_version INTEGER NOT NULL DEFAULT 1 CHECK (schema_version > 0),
    business_reference VARCHAR(150) NOT NULL,
    payload JSONB NOT NULL,
    status VARCHAR(30) NOT NULL DEFAULT 'PENDING'
        CHECK (status IN ('PENDING', 'PUBLISHED', 'FAILED')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    published_at TIMESTAMPTZ,
    retry_count INTEGER NOT NULL DEFAULT 0 CHECK (retry_count >= 0),
    last_error VARCHAR(1000)
);

CREATE INDEX idx_loan_outbox_pending
    ON outbox_events (created_at, event_id)
    WHERE status IN ('PENDING', 'FAILED');

CREATE TABLE repayment_schedules (
    id UUID PRIMARY KEY,
    loan_id UUID NOT NULL REFERENCES loans (id),
    installment_no INTEGER NOT NULL CHECK (installment_no > 0),
    due_date DATE NOT NULL,
    principal_due NUMERIC(19, 4) NOT NULL CHECK (principal_due >= 0),
    interest_due NUMERIC(19, 4) NOT NULL CHECK (interest_due >= 0),
    principal_paid NUMERIC(19, 4) NOT NULL DEFAULT 0 CHECK (principal_paid >= 0),
    interest_paid NUMERIC(19, 4) NOT NULL DEFAULT 0 CHECK (interest_paid >= 0),
    status VARCHAR(30) NOT NULL CHECK (status IN ('SCHEDULED', 'PARTIALLY_PAID', 'PAID', 'OVERDUE')),
    version BIGINT NOT NULL DEFAULT 1 CHECK (version > 0),
    UNIQUE (loan_id, installment_no)
);

CREATE TABLE repayments (
    id UUID PRIMARY KEY,
    loan_id UUID NOT NULL REFERENCES loans (id),
    business_ref VARCHAR(150) NOT NULL UNIQUE,
    amount NUMERIC(19, 4) NOT NULL CHECK (amount > 0),
    status VARCHAR(40) NOT NULL CHECK (status IN ('PENDING', 'CONFIRMED', 'FAILED')),
    simulated_receipt_ref VARCHAR(150) UNIQUE,
    ledger_ref VARCHAR(150),
    idem_key VARCHAR(200) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    posted_at TIMESTAMPTZ
);

CREATE UNIQUE INDEX uq_repayments_one_pending_per_loan
    ON repayments (loan_id)
    WHERE status = 'PENDING';

CREATE TABLE repayment_allocations (
    id UUID PRIMARY KEY,
    repayment_id UUID NOT NULL REFERENCES repayments (id),
    schedule_id UUID NOT NULL REFERENCES repayment_schedules (id),
    principal_amount NUMERIC(19, 4) NOT NULL CHECK (principal_amount >= 0),
    interest_amount NUMERIC(19, 4) NOT NULL CHECK (interest_amount >= 0),
    UNIQUE (repayment_id, schedule_id),
    CHECK (principal_amount + interest_amount > 0)
);

CREATE TABLE loan_processed_events (
    event_id UUID PRIMARY KEY,
    event_type VARCHAR(150) NOT NULL,
    processed_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
