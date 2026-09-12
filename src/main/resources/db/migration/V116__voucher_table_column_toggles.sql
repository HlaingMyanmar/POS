-- Voucher print: per-column visibility toggles for line-item tables
ALTER TABLE voucher_settings
    ADD COLUMN show_col_row_no      TINYINT(1) NOT NULL DEFAULT 1 AFTER show_serial,
    ADD COLUMN show_col_item        TINYINT(1) NOT NULL DEFAULT 1 AFTER show_col_row_no,
    ADD COLUMN show_col_qty         TINYINT(1) NOT NULL DEFAULT 1 AFTER show_col_item,
    ADD COLUMN show_col_unit_price  TINYINT(1) NOT NULL DEFAULT 1 AFTER show_col_qty,
    ADD COLUMN show_col_amount      TINYINT(1) NOT NULL DEFAULT 1 AFTER show_col_unit_price;
