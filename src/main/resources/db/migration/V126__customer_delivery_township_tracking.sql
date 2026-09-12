-- Township-based delivery charges + order delivery tracking
CREATE TABLE customer_delivery_townships (
    id INT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(120) NOT NULL,
    delivery_charge DECIMAL(15, 2) NOT NULL DEFAULT 0,
    active TINYINT(1) NOT NULL DEFAULT 1,
    sort_order INT NOT NULL DEFAULT 0,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    UNIQUE KEY uk_customer_delivery_township_name (name)
);

INSERT INTO customer_delivery_townships (name, delivery_charge, active, sort_order, created_at, updated_at) VALUES
('တောင်ဒဂုံ', 3000.00, 1, 1, NOW(6), NOW(6)),
('မြောက်ဒဂုံ', 3000.00, 1, 2, NOW(6), NOW(6)),
('သင်္ဃန်းကျွန်း', 2500.00, 1, 3, NOW(6), NOW(6)),
('ရန်ကင်း', 2500.00, 1, 4, NOW(6), NOW(6)),
('ဗဟန်း', 2000.00, 1, 5, NOW(6), NOW(6)),
('လသာ', 2000.00, 1, 6, NOW(6), NOW(6));

ALTER TABLE customer_orders
    ADD COLUMN township_id INT NULL,
    ADD COLUMN township_name VARCHAR(120) NULL,
    ADD COLUMN delivery_charge DECIMAL(15, 2) NULL,
    ADD COLUMN items_total DECIMAL(15, 2) NULL,
    ADD COLUMN delivery_status VARCHAR(30) NULL,
    ADD COLUMN delivery_current_location VARCHAR(255) NULL,
    ADD COLUMN delivery_scheduled_at DATETIME(6) NULL,
    ADD COLUMN delivery_person_phone VARCHAR(40) NULL,
    ADD COLUMN delivered_at DATETIME(6) NULL,
    ADD CONSTRAINT fk_customer_orders_township
        FOREIGN KEY (township_id) REFERENCES customer_delivery_townships (id);
