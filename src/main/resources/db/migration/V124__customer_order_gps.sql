-- Order-time GPS snapshot (separate from customer profile GPS)
ALTER TABLE customer_orders
    ADD COLUMN order_latitude DECIMAL(10, 7) NULL,
    ADD COLUMN order_longitude DECIMAL(10, 7) NULL,
    ADD COLUMN order_location_accuracy DOUBLE NULL,
    ADD COLUMN order_location_at DATETIME(6) NULL,
    ADD COLUMN order_location_source VARCHAR(20) NULL;
