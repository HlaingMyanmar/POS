-- Filter/sort helpers for customer catalog pages (size 1-40).
CREATE INDEX idx_catalog_active_category ON products (archived, category_id, name, id);
CREATE INDEX idx_catalog_active_brand ON products (archived, brand_id, name, id);
CREATE INDEX idx_catalog_active_type ON products (archived, product_type, name, id);
CREATE INDEX idx_catalog_active_newest ON products (archived, id);
CREATE INDEX idx_catalog_product_code ON products (product_code);
