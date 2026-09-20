ALTER TABLE payment_transactions
    ADD COLUMN source_type VARCHAR(40) NULL,
    ADD COLUMN source_id INT NULL;

CREATE INDEX idx_payment_transactions_source
    ON payment_transactions (source_type, source_id);
