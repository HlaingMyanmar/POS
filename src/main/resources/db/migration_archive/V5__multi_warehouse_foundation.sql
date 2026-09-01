-- Multi-warehouse foundation. Additive only. Does not change stock quantities.

CREATE TABLE IF NOT EXISTS stock_migration_issues (
  id INT NOT NULL AUTO_INCREMENT,
  issue_type VARCHAR(60) NOT NULL,
  severity VARCHAR(20) NOT NULL DEFAULT 'INFO',
  product_id INT NULL,
  lot_id INT NULL,
  warehouse_id INT NULL,
  detail VARCHAR(1000) NULL,
  created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  PRIMARY KEY (id),
  KEY idx_smi_type (issue_type, severity),
  KEY idx_smi_product (product_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS stock_lot_audit (
  id INT NOT NULL AUTO_INCREMENT,
  lot_id INT NOT NULL,
  action VARCHAR(40) NOT NULL,
  detail TEXT NULL,
  changed_by VARCHAR(120) NULL,
  changed_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  PRIMARY KEY (id),
  KEY idx_sla_lot (lot_id),
  KEY idx_sla_changed (changed_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Run all blocker checks before changing operational tables. MySQL implicitly
-- commits DDL, so a guard after ALTER TABLE cannot safely roll changes back.
DELETE FROM stock_migration_issues;

INSERT INTO stock_migration_issues (issue_type, severity, product_id, detail)
SELECT 'NEGATIVE_OPENING_RESIDUAL', 'BLOCKER', p.id,
       CONCAT('stock_qty=', IFNULL(p.stock_qty, 0), ' lot_remaining=', IFNULL(l.lot_qty, 0))
FROM products p
LEFT JOIN (
  SELECT product_id, SUM(remaining_qty) AS lot_qty
  FROM stock_lots WHERE status IN ('AVAILABLE', 'DEPLETED') GROUP BY product_id
) l ON l.product_id = p.id
WHERE IFNULL(p.has_serial, 1) = 0
  AND IFNULL(p.stock_qty, 0) - IFNULL(l.lot_qty, 0) < 0;

INSERT INTO stock_migration_issues (issue_type, severity, product_id, detail)
SELECT 'NEGATIVE_STOCK_QTY', 'BLOCKER', id, CONCAT('stock_qty=', stock_qty)
FROM products WHERE stock_qty < 0;

INSERT INTO stock_migration_issues (issue_type, severity, lot_id, product_id, detail)
SELECT 'NEGATIVE_LOT_REMAINING', 'BLOCKER', id, product_id, CONCAT('remaining_qty=', remaining_qty)
FROM stock_lots WHERE remaining_qty < 0;

INSERT INTO stock_migration_issues (issue_type, severity, detail)
SELECT 'WAREHOUSE_CODE_COLLISION', 'BLOCKER',
       CONCAT(normalized_code, ': ', GROUP_CONCAT(legacy_name ORDER BY legacy_name SEPARATOR ', '))
FROM (
  SELECT DISTINCT TRIM(warehouse_name) AS legacy_name,
         LEFT(UPPER(REPLACE(TRIM(warehouse_name), ' ', '-')), 40) AS normalized_code
  FROM (
    SELECT warehouse_name FROM stock_lots
    UNION ALL SELECT warehouse_name FROM products
    UNION ALL SELECT warehouse_name FROM purchases
    UNION ALL SELECT warehouse_name FROM sales
  ) all_names
  WHERE warehouse_name IS NOT NULL AND TRIM(warehouse_name) <> ''
) normalized_names
GROUP BY normalized_code
HAVING COUNT(*) > 1;

INSERT INTO stock_migration_issues (issue_type, severity, warehouse_id, detail)
SELECT 'WAREHOUSE_EXISTING_CODE_CONFLICT', 'BLOCKER', MIN(w.id),
       CONCAT(n.normalized_code, ': existing=', MIN(w.name), ', legacy=', MIN(n.legacy_name))
FROM (
  SELECT DISTINCT TRIM(warehouse_name) AS legacy_name,
         LEFT(UPPER(REPLACE(TRIM(warehouse_name), ' ', '-')), 40) AS normalized_code
  FROM (
    SELECT warehouse_name FROM stock_lots
    UNION ALL SELECT warehouse_name FROM products
    UNION ALL SELECT warehouse_name FROM purchases
    UNION ALL SELECT warehouse_name FROM sales
  ) existing_names
  WHERE warehouse_name IS NOT NULL AND TRIM(warehouse_name) <> ''
) n
JOIN warehouses w ON UPPER(TRIM(w.code)) = n.normalized_code
WHERE UPPER(TRIM(w.name)) <> UPPER(TRIM(n.legacy_name))
GROUP BY n.normalized_code;

CREATE TABLE IF NOT EXISTS stock_migration_guard (
  ok TINYINT NOT NULL PRIMARY KEY,
  CONSTRAINT chk_stock_migration_clean CHECK (ok = 1)
) ENGINE=InnoDB;

DELETE FROM stock_migration_guard;
INSERT INTO stock_migration_guard (ok)
SELECT IF(COUNT(*) = 0, 1, 0)
FROM stock_migration_issues
WHERE severity = 'BLOCKER';

ALTER TABLE warehouses
  ADD COLUMN created_at DATETIME(6) NULL,
  ADD COLUMN updated_at DATETIME(6) NULL;

ALTER TABLE stock_lots
  ADD COLUMN warehouse_id INT NULL,
  ADD COLUMN source_type VARCHAR(20) NULL,
  ADD COLUMN source_id INT NULL,
  ADD COLUMN created_at DATETIME(6) NULL,
  ADD COLUMN updated_at DATETIME(6) NULL;

ALTER TABLE stock_lots
  MODIFY purchase_detail_id INT NULL;

ALTER TABLE products
  ADD COLUMN warehouse_id INT NULL;

ALTER TABLE purchases
  ADD COLUMN warehouse_id INT NULL;

ALTER TABLE sales
  ADD COLUMN warehouse_id INT NULL;

ALTER TABLE stock_movements
  ADD COLUMN warehouse_id INT NULL;

ALTER TABLE stock_adjustments
  ADD COLUMN warehouse_id INT NULL;

CREATE INDEX idx_stock_lot_warehouse ON stock_lots (warehouse_id, product_id, status);
CREATE INDEX idx_stock_lot_source ON stock_lots (source_type, source_id);
CREATE INDEX idx_products_warehouse_id ON products (warehouse_id);
CREATE INDEX idx_purchases_warehouse_id ON purchases (warehouse_id);
CREATE INDEX idx_sales_warehouse_id ON sales (warehouse_id);
CREATE INDEX idx_stock_lot_opening_batch ON stock_lots (product_id, source_type, batch_number);
