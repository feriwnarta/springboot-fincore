SELECT
    id,
    product_code,
    funded_at,
    principal_amount,
    SUM(principal_amount) OVER (
        PARTITION BY product_code
        ORDER BY funded_at, id
        ROWS BETWEEN UNBOUNDED PRECEDING AND CURRENT ROW
    ) AS running_principal
FROM loans
WHERE status = 'FUNDED'
  AND funded_at >= :from_time
  AND funded_at < :to_time
ORDER BY product_code, funded_at, id;
