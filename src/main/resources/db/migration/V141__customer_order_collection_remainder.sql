ALTER TABLE customer_orders
    ADD COLUMN collection_payment_method_id INT NULL,
    ADD COLUMN collection_amount DECIMAL(15, 2) NULL,
    ADD COLUMN collection_at DATETIME(6) NULL,
    ADD COLUMN collection_recorded_by VARCHAR(255) NULL;
