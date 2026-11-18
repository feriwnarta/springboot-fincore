ALTER TABLE ledger_outbox_events
    ADD COLUMN claim_token UUID,
    ADD COLUMN claimed_at TIMESTAMPTZ;

ALTER TABLE ledger_outbox_events
    DROP CONSTRAINT ledger_outbox_events_status_check;

ALTER TABLE ledger_outbox_events
    ADD CONSTRAINT ledger_outbox_events_status_check
        CHECK (status IN ('PENDING', 'IN_FLIGHT', 'PUBLISHED', 'FAILED'));

CREATE INDEX idx_ledger_outbox_claimable
    ON ledger_outbox_events (created_at, event_id)
    WHERE status IN ('PENDING', 'FAILED', 'IN_FLIGHT');
