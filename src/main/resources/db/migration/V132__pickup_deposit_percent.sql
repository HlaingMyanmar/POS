ALTER TABLE company_settings
    ADD COLUMN pickup_deposit_percent DECIMAL(5, 2) NOT NULL DEFAULT 30.00;

ALTER TABLE customer_orders
    ADD COLUMN deposit_percent DECIMAL(5, 2) NULL AFTER payment_choice,
    ADD COLUMN deposit_amount DECIMAL(15, 2) NULL AFTER deposit_percent;
