ALTER TABLE customer_product_returns
    ADD COLUMN delivery_status VARCHAR(30) NOT NULL DEFAULT 'PICKUP_REQUESTED',
    ADD COLUMN delivery_note VARCHAR(500) NULL,
    ADD COLUMN delivery_updated_at DATETIME(6) NULL,
    ADD COLUMN delivery_updated_by VARCHAR(255) NULL,
    ADD COLUMN inventory_applied BIT(1) NOT NULL DEFAULT b'0',
    ADD COLUMN accounting_posted BIT(1) NOT NULL DEFAULT b'0',
    ADD KEY idx_cpr_delivery_status (delivery_status);

ALTER TABLE customer_product_return_lines
    ADD COLUMN replacement_product_id INT NULL,
    ADD COLUMN replacement_serial_number VARCHAR(120) NULL,
    ADD CONSTRAINT fk_cprl_replacement_product FOREIGN KEY (replacement_product_id) REFERENCES products (id);
