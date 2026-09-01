ALTER TABLE `app_version_settings`
  ADD COLUMN `technician_version_code` int NOT NULL DEFAULT 1,
  ADD COLUMN `technician_version_name` varchar(50) NOT NULL DEFAULT '1.0.0',
  ADD COLUMN `technician_force_update` bit(1) NOT NULL DEFAULT b'0',
  ADD COLUMN `technician_changelog` varchar(2000) DEFAULT NULL;
