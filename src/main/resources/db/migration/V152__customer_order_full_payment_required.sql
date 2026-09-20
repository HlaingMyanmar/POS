ALTER TABLE customer_orders
    ADD COLUMN full_payment_required BOOLEAN NOT NULL DEFAULT FALSE AFTER delivery_handler;
