CREATE TABLE `customer_app_account` (
  `id` INT NOT NULL AUTO_INCREMENT,
  `customer_id` INT NOT NULL,
  `phone` VARCHAR(20) NOT NULL,
  `password_hash` VARCHAR(100) NOT NULL,
  `token_version` INT NOT NULL DEFAULT 0,
  `enabled` TINYINT(1) NOT NULL DEFAULT 1,
  `created_at` DATETIME(6) NOT NULL,
  `updated_at` DATETIME(6) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_caa_customer` (`customer_id`),
  UNIQUE KEY `uk_caa_phone` (`phone`),
  CONSTRAINT `fk_caa_customer` FOREIGN KEY (`customer_id`) REFERENCES `customer` (`id`)
);

CREATE TABLE `customer_orders` (
  `id` INT NOT NULL AUTO_INCREMENT,
  `order_no` VARCHAR(20) NOT NULL,
  `customer_id` INT NOT NULL,
  `status` VARCHAR(20) NOT NULL,
  `note` TEXT NULL,
  `created_at` DATETIME(6) NOT NULL,
  `updated_at` DATETIME(6) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_co_order_no` (`order_no`),
  KEY `idx_co_customer` (`customer_id`),
  KEY `idx_co_status` (`status`),
  CONSTRAINT `fk_co_customer` FOREIGN KEY (`customer_id`) REFERENCES `customer` (`id`)
);

CREATE TABLE `customer_order_lines` (
  `id` INT NOT NULL AUTO_INCREMENT,
  `order_id` INT NOT NULL,
  `product_id` INT NOT NULL,
  `product_name` VARCHAR(255) NOT NULL,
  `qty` INT NOT NULL,
  `unit_price` DECIMAL(12,2) NOT NULL,
  `subtotal` DECIMAL(12,2) NOT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_col_order` (`order_id`),
  CONSTRAINT `fk_col_order` FOREIGN KEY (`order_id`) REFERENCES `customer_orders` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_col_product` FOREIGN KEY (`product_id`) REFERENCES `products` (`id`)
);

ALTER TABLE `bookings`
  ADD COLUMN `source` VARCHAR(20) NULL AFTER `remark`;

ALTER TABLE `app_version_settings`
  ADD COLUMN `customer_version_code` INT NOT NULL DEFAULT 1,
  ADD COLUMN `customer_version_name` VARCHAR(50) NOT NULL DEFAULT '1.0.0',
  ADD COLUMN `customer_force_update` BIT(1) NOT NULL DEFAULT b'0',
  ADD COLUMN `customer_changelog` VARCHAR(2000) DEFAULT NULL;
