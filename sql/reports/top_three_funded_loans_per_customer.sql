WITH ranked_loans AS (
    SELECT
        id,
        customer_id,
        principal_amount,
        funded_at,
        ROW_NUMBER() OVER (
            PARTITION BY customer_id
            ORDER BY principal_amount DESC, id ASC
        ) AS rn
    FROM loans
    WHERE status = 'FUNDED'
      AND funded_at >= :from_time
      AND funded_at < :to_time
)
SELECT id, customer_id, principal_amount, funded_at, rn
FROM ranked_loans
WHERE rn <= 3
ORDER BY customer_id, rn;
