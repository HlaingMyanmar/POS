ALTER TABLE customer_orders
    ADD COLUMN shipping_renegotiated TINYINT(1) NOT NULL DEFAULT 0,
    ADD COLUMN settlement_action VARCHAR(20) NULL,
    ADD COLUMN settlement_amount DECIMAL(15, 2) NULL,
    ADD COLUMN settlement_kept_amount DECIMAL(15, 2) NULL,
    ADD COLUMN settlement_payment_method_id INT NULL,
    ADD COLUMN settlement_reference VARCHAR(120) NULL,
    ADD COLUMN settlement_at DATETIME(6) NULL,
    ADD COLUMN settlement_recorded_by VARCHAR(255) NULL;
