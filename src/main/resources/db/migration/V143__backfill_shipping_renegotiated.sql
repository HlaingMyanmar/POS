UPDATE customer_orders o
SET shipping_renegotiated = 1
WHERE EXISTS (
    SELECT 1 FROM customer_shipping_audit a
    WHERE a.order_id = o.id AND a.action = 'DECLINED'
);
