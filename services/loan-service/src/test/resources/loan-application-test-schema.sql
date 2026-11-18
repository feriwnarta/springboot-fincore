DROP TABLE IF EXISTS loan_applications;

CREATE TABLE loan_applications (
    id UUID PRIMARY KEY,
    customer_id VARCHAR(100) NOT NULL,
    product_code VARCHAR(50) NOT NULL,
    principal_amount NUMERIC(19, 4) NOT NULL,
    tenor_months INTEGER NOT NULL,
    status VARCHAR(30) NOT NULL,
    version BIGINT NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    approved_at TIMESTAMP WITH TIME ZONE,
    rejected_at TIMESTAMP WITH TIME ZONE,
    actor_id VARCHAR(100) NOT NULL
);

INSERT INTO loan_applications
    (id, customer_id, product_code, principal_amount, tenor_months, status,
     version, created_at, approved_at, rejected_at, actor_id)
VALUES
    ('aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa', 'customer-001', 'PERSONAL_FLAT',
     1000000.0000, 12, 'SUBMITTED', 1, CURRENT_TIMESTAMP, NULL, NULL, 'submitter-001');
