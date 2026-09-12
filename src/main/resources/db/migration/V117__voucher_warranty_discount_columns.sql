-- Separate Warranty and line-discount columns on voucher tables
ALTER TABLE voucher_settings
    ADD COLUMN show_col_warranty       TINYINT(1) NOT NULL DEFAULT 1 AFTER show_col_amount,
    ADD COLUMN show_col_line_discount  TINYINT(1) NOT NULL DEFAULT 1 AFTER show_col_warranty;
