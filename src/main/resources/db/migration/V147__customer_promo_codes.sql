CREATE TABLE IF NOT EXISTS customer_promo_codes (
    id INT NOT NULL AUTO_INCREMENT,
    code VARCHAR(40) NOT NULL,
    name VARCHAR(120) NOT NULL,
    description VARCHAR(500) NULL,
    active TINYINT(1) NOT NULL DEFAULT 1,
    starts_at DATETIME(6) NOT NULL,
    ends_at DATETIME(6) NOT NULL,
    discount_type VARCHAR(20) NOT NULL,
    discount_value DECIMAL(15, 2) NOT NULL,
    max_discount DECIMAL(15, 2) NULL,
    min_order_amount DECIMAL(15, 2) NULL,
    usage_limit INT NULL,
    per_customer_limit INT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_customer_promo_code (code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS customer_promo_code_products (
    promo_id INT NOT NULL,
    product_id INT NOT NULL,
    PRIMARY KEY (promo_id, product_id),
    CONSTRAINT fk_cpp_promo FOREIGN KEY (promo_id) REFERENCES customer_promo_codes (id) ON DELETE CASCADE,
    CONSTRAINT fk_cpp_product FOREIGN KEY (product_id) REFERENCES products (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS customer_promo_code_categories (
    promo_id INT NOT NULL,
    category_id INT NOT NULL,
    PRIMARY KEY (promo_id, category_id),
    CONSTRAINT fk_cpc_promo FOREIGN KEY (promo_id) REFERENCES customer_promo_codes (id) ON DELETE CASCADE,
    CONSTRAINT fk_cpc_category FOREIGN KEY (category_id) REFERENCES categories (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS customer_promo_redemptions (
    id INT NOT NULL AUTO_INCREMENT,
    promo_id INT NOT NULL,
    customer_id INT NOT NULL,
    order_id INT NOT NULL,
    discount_amount DECIMAL(15, 2) NOT NULL,
    released TINYINT(1) NOT NULL DEFAULT 0,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_cpredeem_order (order_id),
    KEY idx_cpredeem_promo_active (promo_id, released),
    KEY idx_cpredeem_customer (promo_id, customer_id, released),
    CONSTRAINT fk_cpredeem_promo FOREIGN KEY (promo_id) REFERENCES customer_promo_codes (id),
    CONSTRAINT fk_cpredeem_customer FOREIGN KEY (customer_id) REFERENCES customer (id),
    CONSTRAINT fk_cpredeem_order FOREIGN KEY (order_id) REFERENCES customer_orders (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

SET @promo_col_exists = (
    SELECT COUNT(*)
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'customer_orders'
      AND COLUMN_NAME = 'promo_id'
);
SET @promo_alter_sql = IF(
    @promo_col_exists = 0,
    'ALTER TABLE customer_orders
        ADD COLUMN promo_id INT NULL AFTER items_total,
        ADD COLUMN promo_code VARCHAR(40) NULL AFTER promo_id,
        ADD COLUMN discount_type VARCHAR(20) NULL AFTER promo_code,
        ADD COLUMN discount_value DECIMAL(15, 2) NULL AFTER discount_type,
        ADD COLUMN discount_amount DECIMAL(15, 2) NULL AFTER discount_value,
        ADD COLUMN discount_max DECIMAL(15, 2) NULL AFTER discount_amount,
        ADD COLUMN eligible_subtotal DECIMAL(15, 2) NULL AFTER discount_max,
        ADD COLUMN promo_snapshot TEXT NULL AFTER eligible_subtotal,
        ADD KEY idx_customer_orders_promo (promo_id),
        ADD CONSTRAINT fk_customer_orders_promo FOREIGN KEY (promo_id) REFERENCES customer_promo_codes (id)',
    'SELECT 1'
);
PREPARE promo_alter_stmt FROM @promo_alter_sql;
EXECUTE promo_alter_stmt;
DEALLOCATE PREPARE promo_alter_stmt;
