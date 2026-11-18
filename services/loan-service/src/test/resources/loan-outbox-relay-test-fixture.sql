TRUNCATE TABLE outbox_events;

INSERT INTO outbox_events (
    event_id, aggregate_id, aggregate_version, event_type, schema_version,
    business_reference, payload, status, created_at
)
VALUES
    ('00000000-0000-0000-0000-000000000001', '10000000-0000-0000-0000-000000000001', 1,
     'LoanSubmitted', 1, 'APP-1', '{"eventType":"LoanSubmitted"}'::jsonb, 'PENDING',
     '2026-01-01T00:00:01Z'),
    ('00000000-0000-0000-0000-000000000002', '10000000-0000-0000-0000-000000000002', 1,
     'LoanApproved', 1, 'APP-2', '{"eventType":"LoanApproved"}'::jsonb, 'PENDING',
     '2026-01-01T00:00:02Z'),
    ('00000000-0000-0000-0000-000000000003', '10000000-0000-0000-0000-000000000003', 1,
     'LoanFunded', 1, 'APP-3', '{"eventType":"LoanFunded"}'::jsonb, 'PENDING',
     '2026-01-01T00:00:03Z');
