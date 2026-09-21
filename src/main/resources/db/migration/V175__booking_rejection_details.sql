-- Per-booking rejection audit and customer-visible reason.
ALTER TABLE bookings
    ADD COLUMN rejection_reason TEXT NULL AFTER remark,
    ADD COLUMN rejected_at DATETIME NULL AFTER rejection_reason,
    ADD COLUMN rejected_by VARCHAR(100) NULL AFTER rejected_at;
