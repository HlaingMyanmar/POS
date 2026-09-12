-- Ward-level delivery charges (same township can have different prices per ရပ်ကွက်)
CREATE TABLE customer_delivery_wards (
    id INT AUTO_INCREMENT PRIMARY KEY,
    township_id INT NOT NULL,
    name VARCHAR(120) NOT NULL,
    delivery_charge DECIMAL(15, 2) NOT NULL DEFAULT 0.00,
    active TINYINT(1) NOT NULL DEFAULT 1,
    sort_order INT NOT NULL DEFAULT 0,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    UNIQUE KEY uk_customer_delivery_ward_township_name (township_id, name),
    CONSTRAINT fk_customer_delivery_ward_township
        FOREIGN KEY (township_id) REFERENCES customer_delivery_townships (id)
);

CREATE INDEX idx_customer_delivery_wards_township ON customer_delivery_wards (township_id, active, sort_order);

ALTER TABLE customer_orders
    ADD COLUMN ward_id INT NULL AFTER township_name,
    ADD COLUMN ward_name VARCHAR(120) NULL AFTER ward_id,
    MODIFY COLUMN township_name VARCHAR(255) NULL;
