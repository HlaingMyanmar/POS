-- Apply FKs only after V6 guard passed (no blocker audit rows). Does not change stock quantities.

ALTER TABLE stock_lots
  ADD CONSTRAINT fk_stock_lots_warehouse
  FOREIGN KEY (warehouse_id) REFERENCES warehouses (id);

ALTER TABLE products
  ADD CONSTRAINT fk_products_warehouse
  FOREIGN KEY (warehouse_id) REFERENCES warehouses (id);

ALTER TABLE purchases
  ADD CONSTRAINT fk_purchases_warehouse
  FOREIGN KEY (warehouse_id) REFERENCES warehouses (id);

ALTER TABLE sales
  ADD CONSTRAINT fk_sales_warehouse
  FOREIGN KEY (warehouse_id) REFERENCES warehouses (id);

ALTER TABLE stock_movements
  ADD CONSTRAINT fk_stock_movements_warehouse
  FOREIGN KEY (warehouse_id) REFERENCES warehouses (id);

ALTER TABLE stock_adjustments
  ADD CONSTRAINT fk_stock_adjustments_warehouse
  FOREIGN KEY (warehouse_id) REFERENCES warehouses (id);
