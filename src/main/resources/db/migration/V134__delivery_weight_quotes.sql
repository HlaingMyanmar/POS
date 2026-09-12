CREATE TABLE delivery_pricing_policy (
 id INT PRIMARY KEY, included_kg DECIMAL(10,3) NULL, extra_per_kg DECIMAL(15,2) NULL,
 max_auto_kg DECIMAL(10,3) NULL, max_auto_qty INT NULL
);
INSERT INTO delivery_pricing_policy(id) VALUES (1);
CREATE TABLE product_shipping_profiles (
 product_id INT PRIMARY KEY, weight_kg DECIMAL(10,3) NULL,
 shipping_class VARCHAR(20) NOT NULL DEFAULT 'MANUAL', max_auto_qty INT NULL,
 CONSTRAINT fk_shipping_product FOREIGN KEY(product_id) REFERENCES products(id)
);
ALTER TABLE customer_orders
 ADD shipping_state VARCHAR(20) NOT NULL DEFAULT 'LEGACY',
 ADD shipping_version INT NOT NULL DEFAULT 0,
 ADD shipping_weight_kg DECIMAL(12,3) NULL,
 ADD shipping_reason VARCHAR(1000) NULL,
 ADD shipping_snapshot TEXT NULL;
CREATE TABLE customer_shipping_audit (
 id BIGINT AUTO_INCREMENT PRIMARY KEY, order_id INT NOT NULL, quote_version INT NOT NULL,
 action VARCHAR(30) NOT NULL, actor VARCHAR(255) NOT NULL, details TEXT NOT NULL,
 created_at DATETIME(6) NOT NULL,
 INDEX idx_shipping_audit_order(order_id,id),
 CONSTRAINT fk_shipping_audit_order FOREIGN KEY(order_id) REFERENCES customer_orders(id)
);
