ALTER TABLE delivery_pricing_policy
    ADD COLUMN delivery_enabled TINYINT(1) NOT NULL DEFAULT 1 AFTER max_auto_qty;

ALTER TABLE customer_orders
    ADD COLUMN requested_delivery_at DATETIME(6) NULL AFTER delivery_scheduled_at;
