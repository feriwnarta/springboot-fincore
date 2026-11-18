TRUNCATE TABLE repayment_allocations, repayments, loans, loan_applications, loan_products CASCADE;

INSERT INTO loan_products
    (id, code, interest_type, annual_rate, min_amount, max_amount, active, version, updated_at)
VALUES
    ('11111111-1111-1111-1111-111111111111', 'PERSONAL_FLAT', 'FLAT', 0.120000, 1.0000, 100000000.0000, TRUE, 1, CURRENT_TIMESTAMP),
    ('22222222-2222-2222-2222-222222222222', 'PERSONAL_ANNUITY', 'ANNUITY', 0.150000, 1.0000, 100000000.0000, TRUE, 1, CURRENT_TIMESTAMP);

INSERT INTO loan_applications
    (id, customer_id, product_code, principal_amount, tenor_months, status, version, created_at, actor_id)
VALUES
    ('aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa', 'customer-001', 'PERSONAL_FLAT', 100.0000, 12, 'APPROVED', 1, '2026-01-01T00:00:00Z', 'test'),
    ('bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb', 'customer-001', 'PERSONAL_FLAT', 200.0000, 12, 'APPROVED', 1, '2026-01-01T00:00:00Z', 'test'),
    ('cccccccc-cccc-cccc-cccc-cccccccccccc', 'customer-002', 'PERSONAL_FLAT', 300.0000, 12, 'APPROVED', 1, '2026-01-01T00:00:00Z', 'test'),
    ('dddddddd-dddd-dddd-dddd-dddddddddddd', 'customer-002', 'PERSONAL_ANNUITY', 50.0000, 12, 'APPROVED', 1, '2026-01-01T00:00:00Z', 'test'),
    ('eeeeeeee-eeee-eeee-eeee-eeeeeeeeeeee', 'customer-003', 'PERSONAL_FLAT', 300.0000, 12, 'APPROVED', 1, '2026-01-01T00:00:00Z', 'test');

INSERT INTO loans
    (id, application_id, customer_id, product_code, principal_amount, status, funded_at, version, created_at, updated_at)
VALUES
    ('aaaaaaaa-0000-0000-0000-000000000001', 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa', 'customer-001', 'PERSONAL_FLAT', 100.0000, 'FUNDED', '2026-01-02T00:00:00Z', 1, '2026-01-01T00:00:00Z', '2026-01-02T00:00:00Z'),
    ('bbbbbbbb-0000-0000-0000-000000000002', 'bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb', 'customer-001', 'PERSONAL_FLAT', 200.0000, 'FUNDED', '2026-01-03T00:00:00Z', 1, '2026-01-01T00:00:00Z', '2026-01-03T00:00:00Z'),
    ('cccccccc-0000-0000-0000-000000000003', 'cccccccc-cccc-cccc-cccc-cccccccccccc', 'customer-002', 'PERSONAL_FLAT', 300.0000, 'FUNDED', '2026-01-04T00:00:00Z', 1, '2026-01-01T00:00:00Z', '2026-01-04T00:00:00Z'),
    ('dddddddd-0000-0000-0000-000000000004', 'dddddddd-dddd-dddd-dddd-dddddddddddd', 'customer-002', 'PERSONAL_ANNUITY', 50.0000, 'FUNDED', '2026-01-05T00:00:00Z', 1, '2026-01-01T00:00:00Z', '2026-01-05T00:00:00Z'),
    ('eeeeeeee-0000-0000-0000-000000000005', 'eeeeeeee-eeee-eeee-eeee-eeeeeeeeeeee', 'customer-003', 'PERSONAL_FLAT', 300.0000, 'FUNDED', '2026-01-06T00:00:00Z', 1, '2026-01-01T00:00:00Z', '2026-01-06T00:00:00Z');

INSERT INTO repayments
    (id, loan_id, business_ref, amount, status, idem_key, created_at, posted_at)
VALUES
    ('11111111-1111-1111-1111-111111111111', 'aaaaaaaa-0000-0000-0000-000000000001', 'REPAY-001', 150.0000, 'POSTED', 'idem-001', '2026-02-01T00:00:00Z', '2026-02-01T00:00:00Z'),
    ('22222222-2222-2222-2222-222222222222', 'aaaaaaaa-0000-0000-0000-000000000001', 'REPAY-002', 50.0000, 'POSTED', 'idem-002', '2026-03-01T00:00:00Z', '2026-03-01T00:00:00Z');

INSERT INTO repayment_schedules
    (id, loan_id, installment_no, due_date, principal_due, interest_due, status, version)
VALUES
    ('aaaaaaaa-2222-2222-2222-222222222222', 'aaaaaaaa-0000-0000-0000-000000000001', 1, '2026-02-01', 100.0000, 50.0000, 'SCHEDULED', 1),
    ('bbbbbbbb-3333-3333-3333-333333333333', 'aaaaaaaa-0000-0000-0000-000000000001', 2, '2026-03-01', 30.0000, 20.0000, 'SCHEDULED', 1);

INSERT INTO repayment_allocations
    (id, repayment_id, schedule_id, principal_amount, interest_amount)
VALUES
    ('aaaaaaaa-1111-1111-1111-111111111111', '11111111-1111-1111-1111-111111111111', 'aaaaaaaa-2222-2222-2222-222222222222', 100.0000, 50.0000),
    ('bbbbbbbb-2222-2222-2222-222222222222', '22222222-2222-2222-2222-222222222222', 'bbbbbbbb-3333-3333-3333-333333333333', 30.0000, 20.0000);
