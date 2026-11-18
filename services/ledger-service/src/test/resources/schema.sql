CREATE TABLE journal_entries (
    id UUID PRIMARY KEY,
    business_tx_ref VARCHAR(150) NOT NULL UNIQUE,
    status VARCHAR(30) NOT NULL,
    currency CHAR(3) NOT NULL
);

CREATE TABLE journal_lines (
    id UUID PRIMARY KEY,
    entry_id UUID NOT NULL,
    account_code VARCHAR(100) NOT NULL,
    side VARCHAR(10) NOT NULL,
    amount NUMERIC(19, 4) NOT NULL,
    currency CHAR(3) NOT NULL
);

CREATE TABLE processed_events (
    event_id UUID PRIMARY KEY,
    event_type VARCHAR(150) NOT NULL
);
