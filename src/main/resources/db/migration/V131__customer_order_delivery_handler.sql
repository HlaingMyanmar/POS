ALTER TABLE customer_orders
    ADD COLUMN delivery_handler VARCHAR(20) NULL AFTER delivery_charge,
    ADD COLUMN quoted_delivery_charge DECIMAL(15, 2) NULL AFTER delivery_handler;
