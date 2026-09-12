-- Catalog pages use stable name/price ordering; serial availability is batched by page.
CREATE INDEX idx_catalog_active_name ON products (archived, name, id);
CREATE INDEX idx_catalog_active_price ON products (archived, selling_price, id);
CREATE INDEX idx_catalog_serial_status ON product_serials (product_id, status);
