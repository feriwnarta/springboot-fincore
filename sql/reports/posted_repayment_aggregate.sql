WITH repayment_totals AS (
    SELECT
        r.id AS repayment_id,
        r.loan_id,
        r.amount AS repayment_amount,
        COALESCE(SUM(ra.principal_amount), 0) AS posted_principal,
        COALESCE(SUM(ra.interest_amount), 0) AS posted_interest
    FROM repayments r
    LEFT JOIN repayment_allocations ra ON ra.repayment_id = r.id
    WHERE r.status = 'POSTED'
      AND r.posted_at >= :from_time
      AND r.posted_at < :to_time
    GROUP BY r.id, r.loan_id, r.amount
)
SELECT
    loan_id,
    SUM(posted_principal) AS posted_principal,
    SUM(posted_interest) AS posted_interest,
    SUM(repayment_amount) AS posted_total
FROM repayment_totals
GROUP BY loan_id
ORDER BY loan_id;
