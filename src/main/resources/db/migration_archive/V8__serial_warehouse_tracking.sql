-- Track the physical warehouse of every serialized product.
ALTER TABLE product_serials
  ADD COLUMN warehouse_id INT NULL;

UPDATE product_serials ps
JOIN products p ON p.id = ps.product_id
SET ps.warehouse_id = p.warehouse_id
WHERE ps.warehouse_id IS NULL AND p.warehouse_id IS NOT NULL;

UPDATE product_serials ps
JOIN (
  SELECT MIN(id) AS id FROM warehouses
  WHERE UPPER(TRIM(code)) = 'MAIN' OR UPPER(TRIM(name)) = 'MAIN'
) main_wh ON main_wh.id IS NOT NULL
SET ps.warehouse_id = main_wh.id
WHERE ps.warehouse_id IS NULL;

ALTER TABLE product_serials
  MODIFY warehouse_id INT NOT NULL,
  ADD CONSTRAINT fk_product_serials_warehouse
    FOREIGN KEY (warehouse_id) REFERENCES warehouses (id);

CREATE INDEX idx_product_serial_warehouse_status
  ON product_serials (warehouse_id, status, product_id);
