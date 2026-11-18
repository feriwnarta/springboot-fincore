CREATE TABLE ledger_audit_events (
    id UUID PRIMARY KEY,
    event_id UUID NOT NULL UNIQUE,
    aggregate_type VARCHAR(100) NOT NULL,
    aggregate_id VARCHAR(100) NOT NULL,
    aggregate_version BIGINT NOT NULL CHECK (aggregate_version > 0),
    action VARCHAR(100) NOT NULL,
    outcome VARCHAR(50) NOT NULL,
    actor_id VARCHAR(100) NOT NULL,
    actor_role VARCHAR(100) NOT NULL,
    correlation_id VARCHAR(100) NOT NULL,
    causation_id VARCHAR(100),
    business_reference VARCHAR(150) NOT NULL,
    reason_code VARCHAR(100),
    metadata JSONB NOT NULL DEFAULT '{}'::jsonb,
    occurred_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_ledger_audit_aggregate
    ON ledger_audit_events (aggregate_type, aggregate_id, aggregate_version);

CREATE INDEX idx_ledger_audit_correlation
    ON ledger_audit_events (correlation_id, occurred_at);

CREATE OR REPLACE FUNCTION reject_ledger_audit_mutation()
RETURNS TRIGGER AS $$
BEGIN
    RAISE EXCEPTION 'ledger audit events are append-only';
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER ledger_audit_events_append_only
BEFORE UPDATE OR DELETE ON ledger_audit_events
FOR EACH ROW EXECUTE FUNCTION reject_ledger_audit_mutation();
