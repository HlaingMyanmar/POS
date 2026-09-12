ALTER TABLE `customer_app_account`
  ADD COLUMN `reset_token_hash` VARCHAR(64) NULL,
  ADD COLUMN `reset_token_expires_at` DATETIME(6) NULL,
  ADD KEY `idx_caa_reset_token` (`reset_token_hash`);
