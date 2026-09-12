ALTER TABLE service_jobs
    ADD COLUMN payment_discount_amount DECIMAL(15,2) NOT NULL DEFAULT 0,
    ADD COLUMN payment_discount_approved_by VARCHAR(120) NULL,
    ADD COLUMN payment_discount_approved_at DATETIME(6) NULL,
    ADD COLUMN payment_discount_approval_note TEXT NULL,
    ADD COLUMN due_delivery_approved_by VARCHAR(120) NULL,
    ADD COLUMN due_delivery_approved_at DATETIME(6) NULL,
    ADD COLUMN due_delivery_approval_reason TEXT NULL;
ALTER TABLE company_settings ADD COLUMN service_allow_delivery_with_due BIT NOT NULL DEFAULT 0;
ALTER TABLE journal_entries
    ADD COLUMN status VARCHAR(20) NOT NULL DEFAULT 'POSTED',
    ADD COLUMN reversal_of_id INT NULL,
    ADD COLUMN reversed_by VARCHAR(120) NULL,
    ADD COLUMN reversed_at DATETIME(6) NULL,
    ADD COLUMN reversal_reason TEXT NULL,
    ADD CONSTRAINT fk_journal_reversal_of FOREIGN KEY (reversal_of_id) REFERENCES journal_entries(id);

