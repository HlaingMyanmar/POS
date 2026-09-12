CREATE TABLE IF NOT EXISTS `customer_push_devices` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `customer_id` INT NOT NULL,
    `token` VARCHAR(512) NOT NULL,
    `platform` VARCHAR(20) NOT NULL DEFAULT 'ANDROID',
    `active` TINYINT(1) NOT NULL DEFAULT 1,
    `created_at` DATETIME(6) NOT NULL,
    `updated_at` DATETIME(6) NOT NULL,
    `last_seen_at` DATETIME(6) NOT NULL,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_customer_push_token` (`token`),
    KEY `idx_customer_push_active` (`customer_id`, `active`),
    CONSTRAINT `fk_customer_push_customer` FOREIGN KEY (`customer_id`) REFERENCES `customer` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
