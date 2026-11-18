DROP TABLE IF EXISTS repayment_allocations;
DROP TABLE IF EXISTS repayments;
DROP TABLE IF EXISTS loans;

CREATE TABLE loans (
    id UUID PRIMARY KEY,
    customer_id VARCHAR(100) NOT NULL,
    product_code VARCHAR(50) NOT NULL,
    principal_amount NUMERIC(19, 4) NOT NULL,
    status VARCHAR(40) NOT NULL,
    funded_at TIMESTAMP WITH TIME ZONE,
    version BIGINT NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE TABLE repayments (
    id UUID PRIMARY KEY,
    loan_id UUID NOT NULL,
    amount NUMERIC(19, 4) NOT NULL,
    status VARCHAR(40) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    posted_at TIMESTAMP WITH TIME ZONE
);

CREATE TABLE repayment_allocations (
    id UUID PRIMARY KEY,
    repayment_id UUID NOT NULL,
    principal_amount NUMERIC(19, 4) NOT NULL,
    interest_amount NUMERIC(19, 4) NOT NULL
);

INSERT INTO loans (id, customer_id, product_code, principal_amount, status, funded_at, version, created_at, updated_at)
VALUES
    ('aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa', 'customer-001', 'PERSONAL_FLAT', 100.0000, 'FUNDED', '2026-01-02T00:00:00Z', 1, '2026-01-01T00:00:00Z', '2026-01-02T00:00:00Z'),
    ('bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb', 'customer-001', 'PERSONAL_FLAT', 200.0000, 'FUNDED', '2026-01-03T00:00:00Z', 1, '2026-01-01T00:00:00Z', '2026-01-03T00:00:00Z'),
    ('cccccccc-cccc-cccc-cccc-cccccccccccc', 'customer-002', 'PERSONAL_FLAT', 300.0000, 'FUNDED', '2026-01-04T00:00:00Z', 1, '2026-01-01T00:00:00Z', '2026-01-04T00:00:00Z'),
    ('dddddddd-dddd-dddd-dddd-dddddddddddd', 'customer-002', 'PERSONAL_ANNUITY', 50.0000, 'FUNDED', '2026-01-05T00:00:00Z', 1, '2026-01-01T00:00:00Z', '2026-01-05T00:00:00Z'),
    ('eeeeeeee-eeee-eeee-eeee-eeeeeeeeeeee', 'customer-003', 'PERSONAL_FLAT', 300.0000, 'FUNDED', '2026-01-06T00:00:00Z', 1, '2026-01-01T00:00:00Z', '2026-01-06T00:00:00Z'),
    ('ffffffff-ffff-ffff-ffff-ffffffffffff', 'customer-004', 'PERSONAL_FLAT', 999.0000, 'APPROVED', NULL, 1, '2026-01-01T00:00:00Z', '2026-01-01T00:00:00Z');

INSERT INTO repayments (id, loan_id, amount, status, created_at, posted_at)
VALUES
    ('11111111-1111-1111-1111-111111111111', 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa', 150.0000, 'POSTED', '2026-02-01T00:00:00Z', '2026-02-01T00:00:00Z'),
    ('22222222-2222-2222-2222-222222222222', 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa', 50.0000, 'POSTED', '2026-03-01T00:00:00Z', '2026-03-01T00:00:00Z'),
    ('33333333-3333-3333-3333-333333333333', 'cccccccc-cccc-cccc-cccc-cccccccccccc', 200.0000, 'POSTED', '2026-02-01T00:00:00Z', '2026-02-01T00:00:00Z'),
    ('44444444-4444-4444-4444-444444444444', 'bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb', 999.0000, 'CONFIRMED', '2026-02-01T00:00:00Z', NULL);

INSERT INTO repayment_allocations (id, repayment_id, principal_amount, interest_amount)
VALUES
    ('aaaaaaaa-1111-1111-1111-111111111111', '11111111-1111-1111-1111-111111111111', 100.0000, 50.0000),
    ('bbbbbbbb-2222-2222-2222-222222222222', '22222222-2222-2222-2222-222222222222', 30.0000, 20.0000),
    ('cccccccc-3333-3333-3333-333333333333', '33333333-3333-3333-3333-333333333333', 100.0000, 100.0000);
