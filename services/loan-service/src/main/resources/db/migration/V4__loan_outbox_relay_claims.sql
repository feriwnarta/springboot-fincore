ALTER TABLE outbox_events DROP CONSTRAINT IF EXISTS outbox_events_status_check;

ALTER TABLE outbox_events
    ADD CONSTRAINT outbox_events_status_check
    CHECK (status IN ('PENDING', 'IN_FLIGHT', 'PUBLISHED', 'FAILED'));

ALTER TABLE outbox_events ADD COLUMN claim_token UUID;
ALTER TABLE outbox_events ADD COLUMN claimed_at TIMESTAMPTZ;

CREATE INDEX idx_loan_outbox_in_flight_lease
    ON outbox_events (claimed_at, event_id)
    WHERE status = 'IN_FLIGHT';
