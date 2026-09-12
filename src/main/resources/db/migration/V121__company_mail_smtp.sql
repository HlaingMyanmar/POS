ALTER TABLE `company_settings`
  ADD COLUMN `mail_smtp_host` VARCHAR(150) NULL,
  ADD COLUMN `mail_smtp_port` INT NULL,
  ADD COLUMN `mail_smtp_username` VARCHAR(190) NULL,
  ADD COLUMN `mail_smtp_password` VARCHAR(255) NULL,
  ADD COLUMN `mail_smtp_from` VARCHAR(190) NULL,
  ADD COLUMN `mail_smtp_auth` TINYINT(1) NOT NULL DEFAULT 1,
  ADD COLUMN `mail_smtp_starttls` TINYINT(1) NOT NULL DEFAULT 1;
