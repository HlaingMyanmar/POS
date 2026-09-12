CREATE TABLE customer_wishlists (
    id INT NOT NULL AUTO_INCREMENT,
    customer_id INT NOT NULL,
    product_id INT NOT NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_wishlist_customer_product (customer_id, product_id),
    KEY idx_wishlist_customer (customer_id, created_at),
    KEY idx_wishlist_product (product_id),
    CONSTRAINT fk_wishlist_customer FOREIGN KEY (customer_id) REFERENCES customer (id) ON DELETE CASCADE,
    CONSTRAINT fk_wishlist_product FOREIGN KEY (product_id) REFERENCES products (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
