ALTER TABLE sale_details
  ADD COLUMN warranty_start_date DATE NULL AFTER warranty_months;

UPDATE sale_details d
INNER JOIN sales s ON s.id = d.sale_id
SET d.warranty_start_date = DATE(s.sale_date)
WHERE COALESCE(d.warranty_months, 0) > 0
  AND d.warranty_start_date IS NULL
  AND s.sale_date IS NOT NULL;

UPDATE sale_details d
INNER JOIN sales s ON s.id = d.sale_id
SET d.warranty_expiry_date = DATE_ADD(DATE(s.sale_date), INTERVAL d.warranty_months MONTH)
WHERE COALESCE(d.warranty_months, 0) > 0
  AND d.warranty_expiry_date IS NULL
  AND s.sale_date IS NOT NULL;

CREATE INDEX idx_sale_details_serial ON sale_details (serial_number);
CREATE INDEX idx_sale_details_warranty_end ON sale_details (warranty_expiry_date);
