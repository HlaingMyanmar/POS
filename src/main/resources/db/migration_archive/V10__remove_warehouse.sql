-- Remove multi-warehouse columns and tables. Stock is tracked via products.stock_qty only.

DROP TABLE IF EXISTS warehouse_transfers;

ALTER TABLE product_serials DROP FOREIGN KEY fk_product_serials_warehouse;
ALTER TABLE stock_lots DROP FOREIGN KEY fk_stock_lots_warehouse;
ALTER TABLE products DROP FOREIGN KEY fk_products_warehouse;
ALTER TABLE purchases DROP FOREIGN KEY fk_purchases_warehouse;
ALTER TABLE sales DROP FOREIGN KEY fk_sales_warehouse;
ALTER TABLE stock_movements DROP FOREIGN KEY fk_stock_movements_warehouse;
ALTER TABLE stock_adjustments DROP FOREIGN KEY fk_stock_adjustments_warehouse;

ALTER TABLE product_serials DROP INDEX idx_product_serial_warehouse_status;
ALTER TABLE products DROP INDEX idx_products_warehouse_id;
ALTER TABLE purchases DROP INDEX idx_purchases_warehouse_id;
ALTER TABLE sales DROP INDEX idx_sales_warehouse_id;
ALTER TABLE stock_lots DROP INDEX idx_stock_lot_warehouse;

ALTER TABLE product_serials DROP COLUMN warehouse_id;
ALTER TABLE products DROP COLUMN warehouse_id, DROP COLUMN warehouse_name;
ALTER TABLE purchases DROP COLUMN warehouse_id, DROP COLUMN warehouse_name;
ALTER TABLE sales DROP COLUMN warehouse_id, DROP COLUMN warehouse_name;
ALTER TABLE sale_returns DROP COLUMN warehouse_name;
ALTER TABLE stock_movements DROP COLUMN warehouse_id, DROP COLUMN warehouse_name;
ALTER TABLE stock_adjustments DROP COLUMN warehouse_id;
ALTER TABLE stock_lots DROP COLUMN warehouse_id, DROP COLUMN warehouse_name;

ALTER TABLE stock_migration_issues DROP COLUMN warehouse_id;

DROP TABLE IF EXISTS warehouses;
