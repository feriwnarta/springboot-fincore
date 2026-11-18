CREATE TABLE journal_entries (
    id UUID PRIMARY KEY,
    business_tx_ref VARCHAR(150) NOT NULL UNIQUE,
    status VARCHAR(30) NOT NULL CHECK (status IN ('PENDING', 'POSTED', 'REVERSED')),
    currency CHAR(3) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    posted_at TIMESTAMPTZ
);

CREATE TABLE journal_lines (
    id UUID PRIMARY KEY,
    entry_id UUID NOT NULL REFERENCES journal_entries (id),
    account_code VARCHAR(100) NOT NULL,
    side VARCHAR(10) NOT NULL CHECK (side IN ('DEBIT', 'CREDIT')),
    amount NUMERIC(19, 4) NOT NULL CHECK (amount > 0),
    currency CHAR(3) NOT NULL
);

CREATE INDEX idx_journal_lines_entry
    ON journal_lines (entry_id);

CREATE TABLE processed_events (
    event_id UUID PRIMARY KEY,
    event_type VARCHAR(150) NOT NULL,
    processed_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE savings_accounts (
    id UUID PRIMARY KEY,
    account_no VARCHAR(50) NOT NULL UNIQUE,
    customer_id VARCHAR(100) NOT NULL,
    currency CHAR(3) NOT NULL DEFAULT 'IDR',
    status VARCHAR(30) NOT NULL CHECK (status IN ('ACTIVE', 'FROZEN', 'CLOSED')),
    current_balance NUMERIC(19, 4) NOT NULL DEFAULT 0 CHECK (current_balance >= 0),
    version BIGINT NOT NULL DEFAULT 1 CHECK (version > 0),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_savings_accounts_customer
    ON savings_accounts (customer_id, status);

CREATE TABLE savings_transactions (
    id UUID PRIMARY KEY,
    account_id UUID NOT NULL REFERENCES savings_accounts (id),
    business_ref VARCHAR(150) NOT NULL UNIQUE,
    operation_type VARCHAR(30) NOT NULL CHECK (operation_type IN ('DEPOSIT', 'WITHDRAWAL')),
    amount NUMERIC(19, 4) NOT NULL CHECK (amount > 0),
    balance_after NUMERIC(19, 4) NOT NULL CHECK (balance_after >= 0),
    journal_id UUID NOT NULL UNIQUE REFERENCES journal_entries (id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE ledger_idempotency_records (
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

CREATE TABLE ledger_outbox_events (
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

CREATE INDEX idx_ledger_outbox_pending
    ON ledger_outbox_events (created_at, event_id)
    WHERE status IN ('PENDING', 'FAILED');
