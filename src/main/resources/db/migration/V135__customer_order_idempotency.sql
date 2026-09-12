ALTER TABLE customer_orders
    ADD COLUMN idempotency_key VARCHAR(64) NULL AFTER payment_choice;

CREATE UNIQUE INDEX uk_customer_orders_customer_idempotency
    ON customer_orders (customer_id, idempotency_key);
