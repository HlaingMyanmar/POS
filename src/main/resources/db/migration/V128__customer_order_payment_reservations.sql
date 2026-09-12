-- Customer order payment reservations + available stock holds
ALTER TABLE products
    ADD COLUMN customer_reserved_qty INT NOT NULL DEFAULT 0;

ALTER TABLE customer_orders
    ADD COLUMN payment_state VARCHAR(30) NOT NULL DEFAULT 'NONE',
    ADD COLUMN payment_choice VARCHAR(30) NOT NULL DEFAULT 'TRANSFER',
    ADD COLUMN reservation_active BIT NOT NULL DEFAULT 0,
    ADD COLUMN reservation_expires_at DATETIME(6) NULL,
    ADD COLUMN payment_method_id INT NULL,
    ADD COLUMN payment_instructions VARCHAR(2000) NULL,
    ADD COLUMN payment_review_note VARCHAR(1000) NULL,
    ADD COLUMN payment_verified_by VARCHAR(255) NULL,
    ADD COLUMN payment_verified_at DATETIME(6) NULL,
    ADD COLUMN latest_proof_id INT NULL,
    ADD COLUMN completed_sale_id INT NULL,
    ADD UNIQUE KEY uk_customer_order_sale (completed_sale_id),
    ADD INDEX idx_customer_order_expiry (reservation_active, payment_state, reservation_expires_at);

CREATE TABLE customer_order_payment_proofs (
    id INT AUTO_INCREMENT PRIMARY KEY,
    order_id INT NOT NULL,
    payment_method_id INT NOT NULL,
    transaction_reference VARCHAR(120) NOT NULL,
    amount DECIMAL(15, 2) NOT NULL,
    image_data MEDIUMBLOB NOT NULL,
    image_type VARCHAR(30) NOT NULL,
    submitted_at DATETIME(6) NOT NULL,
    review_state VARCHAR(30) NOT NULL,
    reviewed_by VARCHAR(255) NULL,
    review_note VARCHAR(1000) NULL,
    UNIQUE KEY uk_order_payment_reference (payment_method_id, transaction_reference),
    CONSTRAINT fk_order_payment_proof_order FOREIGN KEY (order_id) REFERENCES customer_orders (id)
);

ALTER TABLE sales
    ADD COLUMN delivery_charge DECIMAL(15, 2) NOT NULL DEFAULT 0;

-- Payee account details on Payment Channels (customer-facing transfer info; separate from COA ledger)
ALTER TABLE payment_methods
    ADD COLUMN payee_name VARCHAR(120) NULL,
    ADD COLUMN payee_account_no VARCHAR(80) NULL,
    ADD COLUMN payee_hint VARCHAR(255) NULL,
    ADD COLUMN show_on_customer_app TINYINT(1) NOT NULL DEFAULT 1;
