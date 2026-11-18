# Loan reporting SQL performance

The loan reporting adapter uses PostgreSQL native SQL with bound parameters. H2 fixtures provide deterministic result tests, while the Testcontainers suite verifies Flyway migrations and query execution against PostgreSQL. PostgreSQL `EXPLAIN (ANALYZE, BUFFERS)` remains the performance evidence source.

## Query/index mapping

| Query | Main predicates/order | Supporting index |
|---|---|---|
| Top three funded loans per customer | `status`, `funded_at`, partition by `customer_id`, amount/id ranking | `idx_loans_funded_customer_amount` and the existing funded-time access path |
| Running funded total per product | `status`, `funded_at`, partition/order by product and time | `idx_loans_funded_product_time` |
| Posted repayment aggregate | `status`, `posted_at`, join by repayment ID | `idx_repayments_posted_time`, `idx_repayments_posted_loan_time` |

The indexes are partial because report queries only include `FUNDED` loans or `POSTED` repayments. The application never interpolates time values or dynamic order clauses into SQL.

## Reproducible PostgreSQL plan capture

Run against a representative synthetic dataset after PostgreSQL is available:

```sql
EXPLAIN (ANALYZE, BUFFERS)
WITH ranked_loans AS (
    SELECT id, customer_id, principal_amount, funded_at,
           ROW_NUMBER() OVER (
               PARTITION BY customer_id
               ORDER BY principal_amount DESC, id ASC
           ) AS rn
    FROM loans
    WHERE status = 'FUNDED'
      AND funded_at >= TIMESTAMPTZ '2026-01-01T00:00:00Z'
      AND funded_at < TIMESTAMPTZ '2027-01-01T00:00:00Z'
)
SELECT id, customer_id, principal_amount, funded_at, rn
FROM ranked_loans
WHERE rn <= 3
ORDER BY customer_id, rn;
```

The actual plan output is intentionally not claimed here until it is captured from PostgreSQL with representative row counts. H2 proves deterministic query results and duplicate-safe repayment aggregation; Testcontainers proves PostgreSQL migration/query compatibility, but neither substitutes for planner evidence.
