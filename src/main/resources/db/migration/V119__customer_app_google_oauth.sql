ALTER TABLE `customer`
  ADD COLUMN `email` VARCHAR(190) NULL AFTER `phone`,
  ADD UNIQUE KEY `uk_customer_email` (`email`);

ALTER TABLE `customer_app_account`
  MODIFY COLUMN `phone` VARCHAR(20) NULL,
  MODIFY COLUMN `password_hash` VARCHAR(100) NULL,
  ADD COLUMN `email` VARCHAR(190) NULL AFTER `phone`,
  ADD COLUMN `google_sub` VARCHAR(64) NULL AFTER `email`,
  ADD COLUMN `profile_complete` TINYINT(1) NOT NULL DEFAULT 1 AFTER `enabled`,
  ADD UNIQUE KEY `uk_caa_email` (`email`),
  ADD UNIQUE KEY `uk_caa_google_sub` (`google_sub`);
