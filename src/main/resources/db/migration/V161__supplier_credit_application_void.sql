ALTER TABLE supplier_credit_applications
    ADD COLUMN voided BIT NOT NULL DEFAULT 0,
    ADD COLUMN voided_at DATETIME(6) NULL,
    ADD COLUMN voided_by VARCHAR(120) NULL,
    ADD COLUMN void_reason VARCHAR(500) NULL;
