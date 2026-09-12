ALTER TABLE customer_orders
    ADD COLUMN customer_receipt_state VARCHAR(20) NOT NULL DEFAULT 'NONE' AFTER delivered_at,
    ADD COLUMN customer_received_at DATETIME(6) NULL AFTER customer_receipt_state,
    ADD COLUMN customer_receipt_note VARCHAR(500) NULL AFTER customer_received_at;
