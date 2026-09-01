-- Backfill warehouse_id and opening lots. Does not change products.stock_qty.
-- Idempotent: opening lots keyed by batch_number OPENING-MIGRATION-{productId}.
-- Fails (CHECK) when blocker audit rows exist; does not auto-correct invalid data.

INSERT INTO warehouses (active, code, name, created_at, updated_at)
SELECT 1, 'MAIN', 'Main', NOW(6), NOW(6)
FROM DUAL
WHERE NOT EXISTS (
  SELECT 1 FROM warehouses w WHERE UPPER(TRIM(w.code)) = 'MAIN' OR UPPER(TRIM(w.name)) = 'MAIN'
);

INSERT INTO warehouses (active, code, name, created_at, updated_at)
SELECT 1,
       LEFT(UPPER(REPLACE(TRIM(src.wh), ' ', '-')), 40),
       TRIM(src.wh),
       NOW(6),
       NOW(6)
FROM (
  SELECT warehouse_name AS wh FROM stock_lots WHERE warehouse_name IS NOT NULL AND TRIM(warehouse_name) <> ''
  UNION
  SELECT warehouse_name FROM products WHERE warehouse_name IS NOT NULL AND TRIM(warehouse_name) <> ''
  UNION
  SELECT warehouse_name FROM purchases WHERE warehouse_name IS NOT NULL AND TRIM(warehouse_name) <> ''
  UNION
  SELECT warehouse_name FROM sales WHERE warehouse_name IS NOT NULL AND TRIM(warehouse_name) <> ''
) src
WHERE NOT EXISTS (
  SELECT 1 FROM warehouses w
  WHERE UPPER(TRIM(w.name)) = UPPER(TRIM(src.wh))
     OR UPPER(TRIM(w.code)) = UPPER(TRIM(src.wh))
);

INSERT INTO stock_migration_issues (issue_type, severity, warehouse_id, detail)
SELECT 'DUPLICATE_WAREHOUSE_NAME', 'WARN', MIN(id), GROUP_CONCAT(CONCAT(id, ':', name) SEPARATOR ', ')
FROM warehouses
GROUP BY UPPER(TRIM(name))
HAVING COUNT(*) > 1;

UPDATE stock_lots sl
SET sl.warehouse_id = (
  SELECT MIN(w.id) FROM warehouses w
  WHERE UPPER(TRIM(w.name)) = UPPER(TRIM(IFNULL(NULLIF(TRIM(sl.warehouse_name), ''), 'Main')))
     OR UPPER(TRIM(w.code)) = UPPER(TRIM(IFNULL(NULLIF(TRIM(sl.warehouse_name), ''), 'Main')))
)
WHERE sl.warehouse_id IS NULL;

UPDATE stock_lots sl
JOIN warehouses w ON UPPER(TRIM(w.code)) = 'MAIN'
SET sl.warehouse_id = w.id
WHERE sl.warehouse_id IS NULL;

UPDATE products p
SET p.warehouse_id = (
  SELECT MIN(w.id) FROM warehouses w
  WHERE UPPER(TRIM(w.name)) = UPPER(TRIM(IFNULL(NULLIF(TRIM(p.warehouse_name), ''), 'Main')))
     OR UPPER(TRIM(w.code)) = UPPER(TRIM(IFNULL(NULLIF(TRIM(p.warehouse_name), ''), 'Main')))
)
WHERE p.warehouse_id IS NULL;

UPDATE products p
JOIN warehouses w ON UPPER(TRIM(w.code)) = 'MAIN'
SET p.warehouse_id = w.id
WHERE p.warehouse_id IS NULL;

UPDATE purchases pu
SET pu.warehouse_id = (
  SELECT MIN(w.id) FROM warehouses w
  WHERE UPPER(TRIM(w.name)) = UPPER(TRIM(IFNULL(NULLIF(TRIM(pu.warehouse_name), ''), 'Main')))
     OR UPPER(TRIM(w.code)) = UPPER(TRIM(IFNULL(NULLIF(TRIM(pu.warehouse_name), ''), 'Main')))
)
WHERE pu.warehouse_id IS NULL;

UPDATE sales s
SET s.warehouse_id = (
  SELECT MIN(w.id) FROM warehouses w
  WHERE UPPER(TRIM(w.name)) = UPPER(TRIM(IFNULL(NULLIF(TRIM(s.warehouse_name), ''), 'Main')))
     OR UPPER(TRIM(w.code)) = UPPER(TRIM(IFNULL(NULLIF(TRIM(s.warehouse_name), ''), 'Main')))
)
WHERE s.warehouse_id IS NULL;

UPDATE stock_lots
SET source_type = 'PURCHASE',
    source_id = purchase_detail_id,
    created_at = IFNULL(created_at, received_at),
    updated_at = IFNULL(updated_at, received_at)
WHERE source_type IS NULL AND purchase_detail_id IS NOT NULL;

INSERT INTO stock_lots (
  batch_number, expiry_date, received_at, received_qty, remaining_qty, status,
  warehouse_name, product_id, purchase_detail_id, warehouse_id, source_type, source_id, created_at, updated_at
)
SELECT
  CONCAT('OPENING-MIGRATION-', p.id),
  NULL,
  NOW(6),
  IFNULL(p.stock_qty, 0) - IFNULL(l.lot_qty, 0),
  IFNULL(p.stock_qty, 0) - IFNULL(l.lot_qty, 0),
  'AVAILABLE',
  COALESCE(NULLIF(TRIM(p.warehouse_name), ''), main_wh.name, 'Main'),
  p.id,
  NULL,
  COALESCE(p.warehouse_id, main_wh.id),
  'OPENING',
  p.id,
  NOW(6),
  NOW(6)
FROM products p
LEFT JOIN (
  SELECT product_id, SUM(remaining_qty) AS lot_qty
  FROM stock_lots
  WHERE status IN ('AVAILABLE', 'DEPLETED')
  GROUP BY product_id
) l ON l.product_id = p.id
CROSS JOIN (
  SELECT MIN(id) AS id, MIN(name) AS name
  FROM warehouses
  WHERE UPPER(TRIM(code)) = 'MAIN' OR UPPER(TRIM(name)) = 'MAIN'
) main_wh
WHERE IFNULL(p.has_serial, 1) = 0
  AND IFNULL(p.stock_qty, 0) - IFNULL(l.lot_qty, 0) > 0
  AND NOT EXISTS (SELECT 1 FROM stock_migration_issues WHERE severity = 'BLOCKER')
  AND NOT EXISTS (
    SELECT 1 FROM stock_lots x
    WHERE x.product_id = p.id
      AND x.source_type = 'OPENING'
      AND x.batch_number = CONCAT('OPENING-MIGRATION-', p.id)
  );

INSERT INTO stock_migration_issues (issue_type, severity, product_id, detail)
SELECT 'POST_BACKFILL_QTY_MISMATCH', 'BLOCKER', p.id,
       CONCAT('stock_qty=', IFNULL(p.stock_qty, 0), ' lot_remaining=', IFNULL(l.lot_qty, 0))
FROM products p
LEFT JOIN (
  SELECT product_id, SUM(remaining_qty) AS lot_qty
  FROM stock_lots
  WHERE status IN ('AVAILABLE', 'DEPLETED')
  GROUP BY product_id
) l ON l.product_id = p.id
WHERE IFNULL(p.has_serial, 1) = 0
  AND IFNULL(p.stock_qty, 0) <> IFNULL(l.lot_qty, 0)
  AND NOT EXISTS (SELECT 1 FROM stock_migration_issues x WHERE x.severity = 'BLOCKER' AND x.issue_type <> 'POST_BACKFILL_QTY_MISMATCH');

-- Verify the deterministic backfill before Flyway records V6 as successful.
DELETE FROM stock_migration_guard;

INSERT INTO stock_migration_guard (ok)
SELECT IF(COUNT(*) = 0, 1, 0)
FROM stock_migration_issues
WHERE severity = 'BLOCKER';
