ALTER TABLE `customer_app_account`
  ADD COLUMN `last_login_at` DATETIME(6) NULL AFTER `profile_complete`,
  ADD COLUMN `login_count` INT NOT NULL DEFAULT 0 AFTER `last_login_at`;

CREATE TABLE `customer_app_activity` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `account_id` INT NOT NULL,
  `customer_id` INT NOT NULL,
  `action` VARCHAR(40) NOT NULL,
  `detail` VARCHAR(500) NULL,
  `created_at` DATETIME(6) NOT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_caa_act_account` (`account_id`),
  KEY `idx_caa_act_customer` (`customer_id`),
  KEY `idx_caa_act_created` (`created_at`),
  CONSTRAINT `fk_caa_act_account` FOREIGN KEY (`account_id`) REFERENCES `customer_app_account` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_caa_act_customer` FOREIGN KEY (`customer_id`) REFERENCES `customer` (`id`)
);
