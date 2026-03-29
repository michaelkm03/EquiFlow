SELECT
    CASE WHEN status = 'CANCELLED' THEN 'CANCELLED' ELSE 'NOT CANCELLED' END AS bucket,
    status,
    COUNT(*) AS count
FROM orders
GROUP BY bucket, status
ORDER BY bucket, status;
