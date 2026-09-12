ALTER TABLE customer_order_ratings
    ADD COLUMN product_rating TINYINT NOT NULL DEFAULT 0 AFTER rating,
    ADD COLUMN service_rating TINYINT NOT NULL DEFAULT 0 AFTER product_rating,
    ADD COLUMN hidden TINYINT(1) NOT NULL DEFAULT 0 AFTER review,
    ADD COLUMN hidden_by VARCHAR(255) NULL AFTER hidden,
    ADD COLUMN hidden_at DATETIME(6) NULL AFTER hidden_by,
    ADD COLUMN hide_reason VARCHAR(1000) NULL AFTER hidden_at,
    ADD COLUMN editable_until DATETIME(6) NULL AFTER updated_at;

UPDATE customer_order_ratings
SET product_rating = IF(product_rating = 0, rating, product_rating),
    service_rating = IF(service_rating = 0, rating, service_rating),
    editable_until = DATE_ADD(created_at, INTERVAL 7 DAY)
WHERE product_rating = 0 OR service_rating = 0 OR editable_until IS NULL;

CREATE TABLE customer_order_rating_lines (
    id INT NOT NULL AUTO_INCREMENT,
    rating_id INT NOT NULL,
    product_id INT NOT NULL,
    product_name VARCHAR(255) NOT NULL,
    rating TINYINT NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_corl_rating_product (rating_id, product_id),
    KEY idx_corl_product (product_id),
    CONSTRAINT fk_corl_rating FOREIGN KEY (rating_id) REFERENCES customer_order_ratings (id) ON DELETE CASCADE,
    CONSTRAINT fk_corl_product FOREIGN KEY (product_id) REFERENCES products (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
