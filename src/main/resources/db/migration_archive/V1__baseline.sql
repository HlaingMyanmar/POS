-- Canonical schema baseline generated from the fully migrated ser_db schema.
-- Future schema changes must use V2 and later migrations.


/*!40101 SET @OLD_CHARACTER_SET_CLIENT=@@CHARACTER_SET_CLIENT */;
/*!40101 SET @OLD_CHARACTER_SET_RESULTS=@@CHARACTER_SET_RESULTS */;
/*!40101 SET @OLD_COLLATION_CONNECTION=@@COLLATION_CONNECTION */;
/*!50503 SET NAMES utf8mb4 */;
/*!40103 SET @OLD_TIME_ZONE=@@TIME_ZONE */;
/*!40103 SET TIME_ZONE='+00:00' */;
/*!40014 SET @OLD_UNIQUE_CHECKS=@@UNIQUE_CHECKS, UNIQUE_CHECKS=0 */;
/*!40014 SET @OLD_FOREIGN_KEY_CHECKS=@@FOREIGN_KEY_CHECKS, FOREIGN_KEY_CHECKS=0 */;
/*!40101 SET @OLD_SQL_MODE=@@SQL_MODE, SQL_MODE='NO_AUTO_VALUE_ON_ZERO' */;
/*!40111 SET @OLD_SQL_NOTES=@@SQL_NOTES, SQL_NOTES=0 */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `account_balances` (
  `id` int NOT NULL AUTO_INCREMENT,
  `current_balance` decimal(38,2) DEFAULT NULL,
  `fiscal_year` varchar(255) DEFAULT NULL,
  `last_updated` datetime(6) DEFAULT NULL,
  `opening_balance` decimal(38,2) DEFAULT NULL,
  `account_id` int NOT NULL,
  PRIMARY KEY (`id`),
  KEY `FKsr84cosve7yv4rxu43o7fig64` (`account_id`),
  CONSTRAINT `FKsr84cosve7yv4rxu43o7fig64` FOREIGN KEY (`account_id`) REFERENCES `chart_of_accounts` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `accounting_period_locks` (
  `id` int NOT NULL AUTO_INCREMENT,
  `active` bit(1) NOT NULL,
  `date_from` date NOT NULL,
  `date_to` date NOT NULL,
  `locked_at` datetime(6) DEFAULT NULL,
  `locked_by` varchar(255) DEFAULT NULL,
  `reason` varchar(500) NOT NULL,
  `unlocked_at` datetime(6) DEFAULT NULL,
  `unlocked_by` varchar(255) DEFAULT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `app_version_settings` (
  `id` int NOT NULL AUTO_INCREMENT,
  `changelog` varchar(2000) DEFAULT NULL,
  `force_update` bit(1) NOT NULL,
  `version_code` int NOT NULL,
  `version_name` varchar(50) NOT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `audit_logs` (
  `id` int NOT NULL AUTO_INCREMENT,
  `action` varchar(20) NOT NULL,
  `actor` varchar(100) NOT NULL,
  `actor_role` varchar(100) DEFAULT NULL,
  `created_at` datetime(6) DEFAULT NULL,
  `description` varchar(500) DEFAULT NULL,
  `device_type` varchar(20) DEFAULT NULL,
  `ip_address` varchar(50) DEFAULT NULL,
  `module` varchar(60) NOT NULL,
  `resource_id` varchar(100) DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_audit_actor` (`actor`),
  KEY `idx_audit_action` (`action`),
  KEY `idx_audit_module` (`module`),
  KEY `idx_audit_created` (`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `backup_history` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `backup_type` enum('DAILY','MANUAL','MONTHLY','SAFETY','WEEKLY') NOT NULL,
  `completed_at` datetime(6) DEFAULT NULL,
  `error_message` text,
  `file_name` varchar(255) DEFAULT NULL,
  `file_path` varchar(1000) DEFAULT NULL,
  `file_size` bigint DEFAULT NULL,
  `started_at` datetime(6) NOT NULL,
  `status` enum('FAILED','RUNNING','SUCCESS') NOT NULL,
  `file_deleted` bit(1) NOT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_backup_history_started_at` (`started_at`),
  KEY `idx_backup_history_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `backup_settings` (
  `id` int NOT NULL AUTO_INCREMENT,
  `backup_dir` varchar(500) NOT NULL,
  `backup_time` time(6) NOT NULL,
  `day_value` int DEFAULT NULL,
  `enabled` bit(1) NOT NULL,
  `frequency` enum('DAILY','MONTHLY','WEEKLY','YEARLY') NOT NULL,
  `keep_days` int DEFAULT NULL,
  `month_value` int DEFAULT NULL,
  `mysqldump_path` varchar(500) DEFAULT NULL,
  `daily_enabled` bit(1) NOT NULL,
  `daily_time` time(6) DEFAULT NULL,
  `monthly_day` int DEFAULT NULL,
  `monthly_enabled` bit(1) NOT NULL,
  `monthly_time` time(6) DEFAULT NULL,
  `weekly_day` int DEFAULT NULL,
  `weekly_enabled` bit(1) NOT NULL,
  `weekly_time` time(6) DEFAULT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `barcode_label_preset` (
  `id` int NOT NULL AUTO_INCREMENT,
  `code_barcode_h` int DEFAULT NULL,
  `code_barcode_w` double DEFAULT NULL,
  `code_qr_px` int DEFAULT NULL,
  `code_type` varchar(10) DEFAULT NULL,
  `custom_cols` int DEFAULT NULL,
  `custom_h` double DEFAULT NULL,
  `custom_w` double DEFAULT NULL,
  `label_font_size` int DEFAULT NULL,
  `label_preset` varchar(20) DEFAULT NULL,
  `name` varchar(100) NOT NULL,
  `show_price` bit(1) DEFAULT NULL,
  `show_product_code` bit(1) DEFAULT NULL,
  `show_warranty` bit(1) DEFAULT NULL,
  `sub_label_font_size` int DEFAULT NULL,
  `custom_paper_h` double DEFAULT NULL,
  `custom_paper_w` double DEFAULT NULL,
  `margin_bottom` double DEFAULT NULL,
  `margin_left` double DEFAULT NULL,
  `margin_right` double DEFAULT NULL,
  `margin_top` double DEFAULT NULL,
  `paper_size_key` varchar(20) DEFAULT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `barcode_label_settings` (
  `id` int NOT NULL AUTO_INCREMENT,
  `code_barcode_h` int DEFAULT NULL,
  `code_barcode_w` double DEFAULT NULL,
  `code_qr_px` int DEFAULT NULL,
  `code_type` varchar(10) DEFAULT NULL,
  `custom_cols` int DEFAULT NULL,
  `custom_h` double DEFAULT NULL,
  `custom_w` double DEFAULT NULL,
  `label_font_size` int DEFAULT NULL,
  `label_preset` varchar(20) DEFAULT NULL,
  `show_price` bit(1) DEFAULT NULL,
  `show_product_code` bit(1) DEFAULT NULL,
  `show_warranty` bit(1) DEFAULT NULL,
  `sub_label_font_size` int DEFAULT NULL,
  `custom_paper_h` double DEFAULT NULL,
  `custom_paper_w` double DEFAULT NULL,
  `margin_bottom` double DEFAULT NULL,
  `margin_left` double DEFAULT NULL,
  `margin_right` double DEFAULT NULL,
  `margin_top` double DEFAULT NULL,
  `paper_size_key` varchar(20) DEFAULT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `booking_attachments` (
  `id` int NOT NULL AUTO_INCREMENT,
  `attachment_type` varchar(255) DEFAULT NULL,
  `content_type` varchar(255) DEFAULT NULL,
  `data_url` longtext,
  `file_name` varchar(255) DEFAULT NULL,
  `uploaded_at` datetime(6) DEFAULT NULL,
  `uploaded_by` varchar(255) DEFAULT NULL,
  `booking_id` int NOT NULL,
  PRIMARY KEY (`id`),
  KEY `FK14kv7dk09vnu5f6vx6ydoib24` (`booking_id`),
  CONSTRAINT `FK14kv7dk09vnu5f6vx6ydoib24` FOREIGN KEY (`booking_id`) REFERENCES `bookings` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `booking_details` (
  `id` int NOT NULL AUTO_INCREMENT,
  `price` decimal(10,2) NOT NULL,
  `qty` int DEFAULT NULL,
  `subtotal` decimal(10,2) NOT NULL,
  `booking_id` int NOT NULL,
  `service_id` int NOT NULL,
  `device_index` int DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `FKkbcan6ybv86uappnh0qtdmvas` (`booking_id`),
  KEY `FKi61h9unx7dsc9woog2e0jdp4d` (`service_id`),
  CONSTRAINT `FKi61h9unx7dsc9woog2e0jdp4d` FOREIGN KEY (`service_id`) REFERENCES `services` (`id`),
  CONSTRAINT `FKkbcan6ybv86uappnh0qtdmvas` FOREIGN KEY (`booking_id`) REFERENCES `bookings` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `booking_device_infos` (
  `id` int NOT NULL AUTO_INCREMENT,
  `description` text,
  `name` varchar(100) DEFAULT NULL,
  `notice` text,
  `status` varchar(20) DEFAULT NULL,
  `booking_id` int NOT NULL,
  PRIMARY KEY (`id`),
  KEY `FKt1dk20q1kes55bvxl4ckqcke8` (`booking_id`),
  CONSTRAINT `FKt1dk20q1kes55bvxl4ckqcke8` FOREIGN KEY (`booking_id`) REFERENCES `bookings` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `booking_devices` (
  `id` int NOT NULL AUTO_INCREMENT,
  `accessories` text,
  `brand` varchar(100) DEFAULT NULL,
  `color` varchar(50) DEFAULT NULL,
  `device_conditions` text,
  `device_type` varchar(50) DEFAULT NULL,
  `model` varchar(100) DEFAULT NULL,
  `problem_desc` text,
  `serial_number` varchar(100) DEFAULT NULL,
  `booking_id` int NOT NULL,
  `condition_checklist` text,
  `part_requests` text,
  PRIMARY KEY (`id`),
  KEY `FK9rvo2d63ro4pkai6a1aunjf6w` (`booking_id`),
  CONSTRAINT `FK9rvo2d63ro4pkai6a1aunjf6w` FOREIGN KEY (`booking_id`) REFERENCES `bookings` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `bookings` (
  `id` int NOT NULL AUTO_INCREMENT,
  `accessories` text,
  `appointment_date` datetime(6) DEFAULT NULL,
  `booking_date` datetime(6) DEFAULT NULL,
  `brand` varchar(100) DEFAULT NULL,
  `color` varchar(50) DEFAULT NULL,
  `device_type` varchar(50) DEFAULT NULL,
  `invoice_file_path` varchar(255) DEFAULT NULL,
  `invoice_no` varchar(20) NOT NULL,
  `model` varchar(100) DEFAULT NULL,
  `remark` text,
  `serial_number` varchar(100) DEFAULT NULL,
  `status` enum('Cancelled','Completed','Confirmed','Converted','IN_STORAGE','Pending') DEFAULT NULL,
  `total_amount` decimal(10,2) DEFAULT NULL,
  `customer_id` int NOT NULL,
  `payment_method_id` int DEFAULT NULL,
  `staff_id` int DEFAULT NULL,
  `shelf_location` varchar(100) DEFAULT NULL,
  `parent_service_job_id` int DEFAULT NULL,
  `is_rework_return` bit(1) DEFAULT NULL,
  `rework_type` enum('ADDITIONAL','REPLACEMENT','WARRANTY') DEFAULT NULL,
  `advance_payment_id` int DEFAULT NULL,
  `deposit_amount` decimal(15,2) DEFAULT NULL,
  `signature_data` longtext,
  `service_mode` enum('INDOOR','OUTDOOR') DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UK3pfjmiw87nswv3avrj4jfyl57` (`invoice_no`),
  KEY `idx_booking_date` (`booking_date`),
  KEY `idx_booking_status` (`status`),
  KEY `idx_booking_customer` (`customer_id`),
  KEY `FK1yq57a5orp2d7iaq2ya018pm4` (`payment_method_id`),
  KEY `FKs17arm200d80obkinfr6glrte` (`staff_id`),
  CONSTRAINT `FK1yq57a5orp2d7iaq2ya018pm4` FOREIGN KEY (`payment_method_id`) REFERENCES `payment_methods` (`id`),
  CONSTRAINT `FK8md4njs5a5njp63yv11k9sajw` FOREIGN KEY (`customer_id`) REFERENCES `customer` (`id`),
  CONSTRAINT `FKs17arm200d80obkinfr6glrte` FOREIGN KEY (`staff_id`) REFERENCES `staff` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `brands` (
  `id` int NOT NULL AUTO_INCREMENT,
  `is_active` bit(1) DEFAULT NULL,
  `name` varchar(100) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UKoce3937d2f4mpfqrycbr0l93m` (`name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `cash_drawer_movements` (
  `id` int NOT NULL AUTO_INCREMENT,
  `actor` varchar(100) NOT NULL,
  `amount` decimal(15,2) NOT NULL,
  `created_at` datetime(6) NOT NULL,
  `reason` text NOT NULL,
  `type` varchar(10) NOT NULL,
  `session_id` int NOT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_drawer_movement_session` (`session_id`),
  CONSTRAINT `FKlcne12kx3dc0cg25rfsghijbj` FOREIGN KEY (`session_id`) REFERENCES `cash_drawer_sessions` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `cash_drawer_sessions` (
  `id` int NOT NULL AUTO_INCREMENT,
  `cash_in` decimal(15,2) NOT NULL,
  `cash_out` decimal(15,2) NOT NULL,
  `cash_refunds` decimal(15,2) NOT NULL,
  `cash_sales` decimal(15,2) NOT NULL,
  `closed_at` datetime(6) DEFAULT NULL,
  `closed_by` varchar(100) DEFAULT NULL,
  `counted_cash` decimal(15,2) DEFAULT NULL,
  `difference_amount` decimal(15,2) DEFAULT NULL,
  `expected_cash` decimal(15,2) DEFAULT NULL,
  `note` text,
  `opened_at` datetime(6) NOT NULL,
  `opened_by` varchar(100) NOT NULL,
  `opening_cash` decimal(15,2) NOT NULL,
  `status` varchar(20) NOT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_drawer_status` (`status`),
  KEY `idx_drawer_opened_at` (`opened_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `categories` (
  `id` int NOT NULL AUTO_INCREMENT,
  `description` varchar(255) DEFAULT NULL,
  `is_active` bit(1) DEFAULT NULL,
  `name` varchar(100) DEFAULT NULL,
  `parent_id` int DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UKt8o6pivur7nn124jehx7cygw5` (`name`),
  KEY `FKsaok720gsu4u2wrgbk10b5n8d` (`parent_id`),
  CONSTRAINT `FKsaok720gsu4u2wrgbk10b5n8d` FOREIGN KEY (`parent_id`) REFERENCES `categories` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `chart_of_accounts` (
  `id` int NOT NULL AUTO_INCREMENT,
  `account_name` varchar(100) NOT NULL,
  `account_type` enum('Asset','Equity','Expense','Income','Liability') NOT NULL,
  `code` varchar(20) DEFAULT NULL,
  `parent_id` int DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UKl37yt5qox6v2qwhsgynbxlb4n` (`code`),
  KEY `FKt1046gd7mgo0v7rdnh6aa3per` (`parent_id`),
  CONSTRAINT `FKt1046gd7mgo0v7rdnh6aa3per` FOREIGN KEY (`parent_id`) REFERENCES `chart_of_accounts` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `chat_messages` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `content` text NOT NULL,
  `sender_name` varchar(100) DEFAULT NULL,
  `sender_role` varchar(50) DEFAULT NULL,
  `sender_username` varchar(50) NOT NULL,
  `sent_at` datetime(6) NOT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_chat_sent_at` (`sent_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `company_settings` (
  `id` int NOT NULL AUTO_INCREMENT,
  `company_address` varchar(1000) DEFAULT NULL,
  `company_email` varchar(150) DEFAULT NULL,
  `company_name` varchar(255) NOT NULL,
  `company_phone` varchar(100) DEFAULT NULL,
  `footer_note` varchar(500) DEFAULT NULL,
  `invoice_title` varchar(255) DEFAULT NULL,
  `logo_base64` longtext,
  `tagline_mm` varchar(255) DEFAULT NULL,
  `setup_complete` bit(1) DEFAULT NULL,
  `voucher_config_json` longtext,
  `booking_digits` int DEFAULT NULL,
  `booking_prefix` varchar(20) DEFAULT NULL,
  `purchase_digits` int DEFAULT NULL,
  `purchase_prefix` varchar(20) DEFAULT NULL,
  `sale_digits` int DEFAULT NULL,
  `sale_prefix` varchar(20) DEFAULT NULL,
  `po_digits` int DEFAULT NULL,
  `po_prefix` varchar(20) DEFAULT NULL,
  `purchase_return_digits` int DEFAULT NULL,
  `purchase_return_prefix` varchar(20) DEFAULT NULL,
  `po_final_approval_threshold` decimal(18,2) DEFAULT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `credit_alerts` (
  `id` int NOT NULL AUTO_INCREMENT,
  `alert_date` datetime(6) DEFAULT NULL,
  `alert_type` enum('Credit_Limit_Exceeded','Due_Soon','Large_Credit_Sale','Overdue') NOT NULL,
  `is_resolved` bit(1) DEFAULT NULL,
  `resolved_at` datetime(6) DEFAULT NULL,
  `customer_id` int NOT NULL,
  `sale_id` int DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `FKjynnocvs15qrltdci2g3fekgm` (`customer_id`),
  KEY `FKr1h1eoyulqlk4mfqsjdjoxjgj` (`sale_id`),
  CONSTRAINT `FKjynnocvs15qrltdci2g3fekgm` FOREIGN KEY (`customer_id`) REFERENCES `customer` (`id`),
  CONSTRAINT `FKr1h1eoyulqlk4mfqsjdjoxjgj` FOREIGN KEY (`sale_id`) REFERENCES `sales` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `credit_override_log` (
  `id` int NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) DEFAULT NULL,
  `note` text,
  `reason` text,
  `customer_id` int NOT NULL,
  `sale_id` int DEFAULT NULL,
  `staff_id` int DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `FKej4e9e4lkrrg4xe7j36nofam0` (`customer_id`),
  KEY `FK6ahpe6eyj7b881t0xyiqlr9id` (`sale_id`),
  KEY `FKc97t5t2ig3bqvqncvu49xhxv9` (`staff_id`),
  CONSTRAINT `FK6ahpe6eyj7b881t0xyiqlr9id` FOREIGN KEY (`sale_id`) REFERENCES `sales` (`id`),
  CONSTRAINT `FKc97t5t2ig3bqvqncvu49xhxv9` FOREIGN KEY (`staff_id`) REFERENCES `staff` (`id`),
  CONSTRAINT `FKej4e9e4lkrrg4xe7j36nofam0` FOREIGN KEY (`customer_id`) REFERENCES `customer` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `customer` (
  `id` int NOT NULL AUTO_INCREMENT,
  `address` text NOT NULL,
  `blacklist_reason` text,
  `blacklisted` bit(1) DEFAULT NULL,
  `credit_hold` bit(1) DEFAULT NULL,
  `credit_hold_reason` text,
  `name` varchar(50) NOT NULL,
  `phone` varchar(20) NOT NULL,
  `advance_balance` decimal(15,2) NOT NULL,
  `latitude` decimal(10,7) DEFAULT NULL,
  `location_accuracy` decimal(8,2) DEFAULT NULL,
  `location_captured_at` datetime(6) DEFAULT NULL,
  `location_captured_by` varchar(120) DEFAULT NULL,
  `location_source` varchar(20) DEFAULT NULL,
  `longitude` decimal(10,7) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UKo3uty20c6csmx5y4uk2tc5r4m` (`phone`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `customer_credit_applications` (
  `id` int NOT NULL AUTO_INCREMENT,
  `amount` decimal(15,2) NOT NULL,
  `application_no` varchar(50) NOT NULL,
  `applied_at` datetime(6) NOT NULL,
  `applied_by` varchar(120) DEFAULT NULL,
  `reason` varchar(500) DEFAULT NULL,
  `customer_id` int NOT NULL,
  `sale_id` int DEFAULT NULL,
  `service_job_id` int DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UK67mwquqrhxich7482siktytqk` (`application_no`),
  KEY `idx_cca_customer` (`customer_id`),
  KEY `idx_cca_sale` (`sale_id`),
  KEY `idx_cca_job` (`service_job_id`),
  CONSTRAINT `FKjo4n8n3t1cd5gs3v2eyp5bwmq` FOREIGN KEY (`customer_id`) REFERENCES `customer` (`id`),
  CONSTRAINT `FKpti4tcihh52rsbx2p78q661rc` FOREIGN KEY (`sale_id`) REFERENCES `sales` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `customer_credit_term_history` (
  `id` int NOT NULL AUTO_INCREMENT,
  `changed_at` datetime(6) DEFAULT NULL,
  `new_credit_allowed` bit(1) DEFAULT NULL,
  `new_credit_days` int DEFAULT NULL,
  `new_credit_limit` decimal(15,2) DEFAULT NULL,
  `old_credit_allowed` bit(1) DEFAULT NULL,
  `old_credit_days` int DEFAULT NULL,
  `old_credit_limit` decimal(15,2) DEFAULT NULL,
  `customer_id` int NOT NULL,
  PRIMARY KEY (`id`),
  KEY `FK5n8wdtcjmha58eeynpqs9ktbr` (`customer_id`),
  CONSTRAINT `FK5n8wdtcjmha58eeynpqs9ktbr` FOREIGN KEY (`customer_id`) REFERENCES `customer` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `customer_credit_terms` (
  `id` int NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) DEFAULT NULL,
  `is_credit_allowed` bit(1) DEFAULT NULL,
  `credit_days` int DEFAULT NULL,
  `credit_limit` decimal(15,2) DEFAULT NULL,
  `updated_at` datetime(6) DEFAULT NULL,
  `customer_id` int NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UKdjhaylymyx36br5j1qf7umres` (`customer_id`),
  CONSTRAINT `FK5jw6l4untj2wx6rvkv37c5jxf` FOREIGN KEY (`customer_id`) REFERENCES `customer` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `customer_payment_allocations` (
  `id` int NOT NULL AUTO_INCREMENT,
  `amount` decimal(15,2) NOT NULL,
  `customer_payment_id` int NOT NULL,
  `sale_id` int NOT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_cpa_payment` (`customer_payment_id`),
  KEY `idx_cpa_sale` (`sale_id`),
  CONSTRAINT `FKbvptqjvqdw33jc3r6wtbfuko3` FOREIGN KEY (`sale_id`) REFERENCES `sales` (`id`),
  CONSTRAINT `FKsuqath7ybn8wybdwag8ytjvrp` FOREIGN KEY (`customer_payment_id`) REFERENCES `customer_payments` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `customer_payments` (
  `id` int NOT NULL AUTO_INCREMENT,
  `amount` decimal(15,2) NOT NULL,
  `note` text,
  `payment_date` datetime(6) DEFAULT NULL,
  `transaction_no` varchar(100) DEFAULT NULL,
  `customer_id` int NOT NULL,
  `payment_method_id` int NOT NULL,
  `sale_id` int DEFAULT NULL,
  `staff_id` int NOT NULL,
  `advance_amount` decimal(15,2) DEFAULT NULL,
  `allocated_amount` decimal(15,2) DEFAULT NULL,
  `payment_no` varchar(50) DEFAULT NULL,
  `void_reason` text,
  `voided` bit(1) DEFAULT NULL,
  `voided_at` datetime(6) DEFAULT NULL,
  `voided_by` varchar(120) DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `FKh34yv8veqbjgardvcnqlkbv52` (`customer_id`),
  KEY `FK8mbqhald3vxkruuby5jjn8rdt` (`payment_method_id`),
  KEY `FKm2pmai36s1acile5p40tsn7i9` (`sale_id`),
  KEY `FKcdlpm4v34na58qqwkqd22xpat` (`staff_id`),
  KEY `idx_cp_payment_no` (`payment_no`),
  CONSTRAINT `FK8mbqhald3vxkruuby5jjn8rdt` FOREIGN KEY (`payment_method_id`) REFERENCES `payment_methods` (`id`),
  CONSTRAINT `FKcdlpm4v34na58qqwkqd22xpat` FOREIGN KEY (`staff_id`) REFERENCES `staff` (`id`),
  CONSTRAINT `FKh34yv8veqbjgardvcnqlkbv52` FOREIGN KEY (`customer_id`) REFERENCES `customer` (`id`),
  CONSTRAINT `FKm2pmai36s1acile5p40tsn7i9` FOREIGN KEY (`sale_id`) REFERENCES `sales` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `expenses` (
  `id` int NOT NULL AUTO_INCREMENT,
  `amount` decimal(15,2) NOT NULL,
  `description` text,
  `expense_code` varchar(50) NOT NULL,
  `expense_date` datetime DEFAULT NULL,
  `account_id` int NOT NULL,
  `payment_method_id` int NOT NULL,
  `staff_id` int NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UK8b047f7t6u869sya4xh6f8530` (`expense_code`),
  KEY `idx_expense_date` (`expense_date`),
  KEY `idx_expense_account` (`account_id`),
  KEY `FKe61223rol7yupgqm52d9eu1iw` (`payment_method_id`),
  KEY `FK3aqsc58rhg1mc8tadepkpdnjw` (`staff_id`),
  CONSTRAINT `FK3aqsc58rhg1mc8tadepkpdnjw` FOREIGN KEY (`staff_id`) REFERENCES `staff` (`id`),
  CONSTRAINT `FK41g9h45f0ywmifj04xjuaau0g` FOREIGN KEY (`account_id`) REFERENCES `chart_of_accounts` (`id`),
  CONSTRAINT `FKe61223rol7yupgqm52d9eu1iw` FOREIGN KEY (`payment_method_id`) REFERENCES `payment_methods` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `goods_receipt_lines` (
  `id` int NOT NULL AUTO_INCREMENT,
  `accepted_qty` int DEFAULT NULL,
  `damaged_qty` int DEFAULT NULL,
  `invoice_unit_cost` decimal(38,2) DEFAULT NULL,
  `ordered_qty` int DEFAULT NULL,
  `po_detail_id` int DEFAULT NULL,
  `po_unit_cost` decimal(38,2) DEFAULT NULL,
  `price_variance` decimal(38,2) DEFAULT NULL,
  `product_id` int DEFAULT NULL,
  `product_name` varchar(255) DEFAULT NULL,
  `rejected_qty` int DEFAULT NULL,
  `goods_receipt_id` int NOT NULL,
  PRIMARY KEY (`id`),
  KEY `FK911tv4c70yqo1u58rdfa0pf5w` (`goods_receipt_id`),
  CONSTRAINT `FK911tv4c70yqo1u58rdfa0pf5w` FOREIGN KEY (`goods_receipt_id`) REFERENCES `goods_receipts` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `goods_receipts` (
  `id` int NOT NULL AUTO_INCREMENT,
  `grn_code` varchar(255) NOT NULL,
  `match_status` varchar(255) NOT NULL,
  `purchase_id` int DEFAULT NULL,
  `received_at` datetime(6) NOT NULL,
  `received_by` varchar(255) DEFAULT NULL,
  `supplier_invoice_no` varchar(255) DEFAULT NULL,
  `variance_reason` varchar(500) DEFAULT NULL,
  `purchase_order_id` int NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UKkud91g06s413qoloxgll51bgc` (`grn_code`),
  KEY `idx_grn_po` (`purchase_order_id`),
  KEY `idx_grn_purchase` (`purchase_id`),
  CONSTRAINT `FK31kyyaqb354qfc4pssihmmry5` FOREIGN KEY (`purchase_order_id`) REFERENCES `purchase_orders` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `incomes` (
  `id` int NOT NULL AUTO_INCREMENT,
  `amount` decimal(15,2) NOT NULL,
  `description` text,
  `income_code` varchar(50) NOT NULL,
  `income_date` datetime(6) DEFAULT NULL,
  `account_id` int NOT NULL,
  `payment_method_id` int NOT NULL,
  `staff_id` int NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UK8a6gtnrrgtsb5hfy5vl7q6p0j` (`income_code`),
  KEY `FK6l3doo9r4ojbshb8favtb7g2l` (`account_id`),
  KEY `FKqu89fo1dqxv0usaduj01yrd0f` (`payment_method_id`),
  KEY `FKrf4dafliljvjkmqfi797q3g1t` (`staff_id`),
  CONSTRAINT `FK6l3doo9r4ojbshb8favtb7g2l` FOREIGN KEY (`account_id`) REFERENCES `chart_of_accounts` (`id`),
  CONSTRAINT `FKqu89fo1dqxv0usaduj01yrd0f` FOREIGN KEY (`payment_method_id`) REFERENCES `payment_methods` (`id`),
  CONSTRAINT `FKrf4dafliljvjkmqfi797q3g1t` FOREIGN KEY (`staff_id`) REFERENCES `staff` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `journal_details` (
  `id` int NOT NULL AUTO_INCREMENT,
  `credit` decimal(38,2) DEFAULT NULL,
  `debit` decimal(38,2) DEFAULT NULL,
  `account_id` int NOT NULL,
  `journal_id` int NOT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_jd_journal` (`journal_id`),
  KEY `idx_jd_account` (`account_id`),
  CONSTRAINT `FKcne5ds2p0er69f6u832cmru3` FOREIGN KEY (`journal_id`) REFERENCES `journal_entries` (`id`),
  CONSTRAINT `FKha2ylf3juvyr0dr0goxj4bvni` FOREIGN KEY (`account_id`) REFERENCES `chart_of_accounts` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `journal_entries` (
  `id` int NOT NULL AUTO_INCREMENT,
  `description` text,
  `entry_date` datetime(6) DEFAULT NULL,
  `reference_no` varchar(255) DEFAULT NULL,
  `staff_id` int DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_je_entry_date` (`entry_date`),
  KEY `idx_je_reference_no` (`reference_no`),
  KEY `idx_je_staff` (`staff_id`),
  CONSTRAINT `FKb3m1xlndil61iao1m04qi8hi5` FOREIGN KEY (`staff_id`) REFERENCES `staff` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `manufacturing_formula_items` (
  `id` int NOT NULL AUTO_INCREMENT,
  `product_id` int NOT NULL,
  `product_name` varchar(255) DEFAULT NULL,
  `qty` int DEFAULT NULL,
  `unit_cost` decimal(15,2) DEFAULT NULL,
  `formula_id` int NOT NULL,
  PRIMARY KEY (`id`),
  KEY `FK1kaldhxba9gq588kmmoppfa4f` (`formula_id`),
  CONSTRAINT `FK1kaldhxba9gq588kmmoppfa4f` FOREIGN KEY (`formula_id`) REFERENCES `manufacturing_formulas` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `manufacturing_formulas` (
  `id` int NOT NULL AUTO_INCREMENT,
  `description` text,
  `finished_product_brand_id` int DEFAULT NULL,
  `finished_product_category_id` int DEFAULT NULL,
  `finished_product_name` varchar(255) DEFAULT NULL,
  `finished_product_selling_price` decimal(15,2) DEFAULT NULL,
  `finished_product_type` varchar(20) DEFAULT NULL,
  `finished_product_unit_id` int DEFAULT NULL,
  `name` varchar(255) NOT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `manufacturing_order_items` (
  `id` int NOT NULL AUTO_INCREMENT,
  `product_id` int NOT NULL,
  `product_name` varchar(255) DEFAULT NULL,
  `qty` int DEFAULT NULL,
  `unit_cost` decimal(15,2) DEFAULT NULL,
  `order_id` int NOT NULL,
  PRIMARY KEY (`id`),
  KEY `FKc0u7nndut30g4pa0qr77vcbae` (`order_id`),
  CONSTRAINT `FKc0u7nndut30g4pa0qr77vcbae` FOREIGN KEY (`order_id`) REFERENCES `manufacturing_orders` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `manufacturing_orders` (
  `id` int NOT NULL AUTO_INCREMENT,
  `completed_at` datetime(6) DEFAULT NULL,
  `created_at` datetime(6) DEFAULT NULL,
  `finished_product_brand_id` int DEFAULT NULL,
  `finished_product_category_id` int DEFAULT NULL,
  `finished_product_id` int DEFAULT NULL,
  `finished_product_name` varchar(255) NOT NULL,
  `finished_product_selling_price` decimal(15,2) DEFAULT NULL,
  `finished_product_type` varchar(20) DEFAULT NULL,
  `finished_product_unit_id` int DEFAULT NULL,
  `labor_cost` decimal(15,2) DEFAULT NULL,
  `notes` text,
  `order_code` varchar(30) NOT NULL,
  `overhead_cost` decimal(15,2) DEFAULT NULL,
  `production_qty` int NOT NULL,
  `status` enum('CANCELLED','COMPLETED','DRAFT') NOT NULL,
  `waste_cost` decimal(15,2) DEFAULT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `mfg_order_item_serials` (
  `item_id` int NOT NULL,
  `serial_id` int DEFAULT NULL,
  KEY `FKffmi7qi46g41silyiapvisl4w` (`item_id`),
  CONSTRAINT `FKffmi7qi46g41silyiapvisl4w` FOREIGN KEY (`item_id`) REFERENCES `manufacturing_order_items` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `payment_methods` (
  `id` int NOT NULL AUTO_INCREMENT,
  `is_active` bit(1) DEFAULT NULL,
  `method_name` varchar(50) NOT NULL,
  `account_id` int NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UKhn6pcph0iahf37xw5rxpvtyjo` (`method_name`),
  KEY `FKj9dd4f9uib5v83yadxoyfmeqi` (`account_id`),
  CONSTRAINT `FKj9dd4f9uib5v83yadxoyfmeqi` FOREIGN KEY (`account_id`) REFERENCES `chart_of_accounts` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `payment_transactions` (
  `id` int NOT NULL AUTO_INCREMENT,
  `amount` decimal(38,2) DEFAULT NULL,
  `payment_date` datetime(6) DEFAULT NULL,
  `reference_id` int DEFAULT NULL,
  `reference_type` varchar(30) DEFAULT NULL,
  `transaction_no` varchar(255) DEFAULT NULL,
  `payment_method_id` int NOT NULL,
  `reversal_reason` text,
  `reversed` bit(1) NOT NULL,
  `reversed_at` datetime(6) DEFAULT NULL,
  `reversed_by` varchar(255) DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `FK42m55uk6j6tx4dkkx13xx6c7x` (`payment_method_id`),
  CONSTRAINT `FK42m55uk6j6tx4dkkx13xx6c7x` FOREIGN KEY (`payment_method_id`) REFERENCES `payment_methods` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `payroll_lines` (
  `id` int NOT NULL AUTO_INCREMENT,
  `absence_deduction` decimal(15,2) DEFAULT NULL,
  `advance_deduction` decimal(15,2) DEFAULT NULL,
  `basic_salary` decimal(15,2) DEFAULT NULL,
  `bonus_amount` decimal(15,2) DEFAULT NULL,
  `deduction_amount` decimal(15,2) DEFAULT NULL,
  `gross_amount` decimal(15,2) DEFAULT NULL,
  `meal_allowance` decimal(15,2) DEFAULT NULL,
  `net_amount` decimal(15,2) DEFAULT NULL,
  `other_allowance` decimal(15,2) DEFAULT NULL,
  `other_deduction` decimal(15,2) DEFAULT NULL,
  `overtime_pay` decimal(15,2) DEFAULT NULL,
  `tax_deduction` decimal(15,2) DEFAULT NULL,
  `transport_allowance` decimal(15,2) DEFAULT NULL,
  `payroll_run_id` int NOT NULL,
  `staff_id` int NOT NULL,
  PRIMARY KEY (`id`),
  KEY `FKn3xody1w5nx9y31n8d0or4a30` (`payroll_run_id`),
  KEY `FK2phdsabn0j8gt8mxkndmugwpy` (`staff_id`),
  CONSTRAINT `FK2phdsabn0j8gt8mxkndmugwpy` FOREIGN KEY (`staff_id`) REFERENCES `staff` (`id`),
  CONSTRAINT `FKn3xody1w5nx9y31n8d0or4a30` FOREIGN KEY (`payroll_run_id`) REFERENCES `payroll_runs` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `payroll_runs` (
  `id` int NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) DEFAULT NULL,
  `is_paid` bit(1) DEFAULT NULL,
  `pay_date` date NOT NULL,
  `payroll_code` varchar(255) NOT NULL,
  `period_from` date NOT NULL,
  `period_to` date NOT NULL,
  `remark` text,
  `total_deduction` decimal(15,2) DEFAULT NULL,
  `total_gross` decimal(15,2) DEFAULT NULL,
  `total_net` decimal(15,2) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UKdgbc8sqgtldwia9br7xvgn4s3` (`payroll_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `permissions` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `description` varchar(255) DEFAULT NULL,
  `name` varchar(50) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UKpnvtwliis6p05pn6i3ndjrqt2` (`name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `product_serials` (
  `id` int NOT NULL AUTO_INCREMENT,
  `serial_number` varchar(100) NOT NULL,
  `status` varchar(40) NOT NULL,
  `warranty_end_date` date DEFAULT NULL,
  `warranty_months` int DEFAULT NULL,
  `warranty_start_date` date DEFAULT NULL,
  `product_id` int NOT NULL,
  `photo_base64` text,
  `item_condition` varchar(500) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UK4lldmknreiu8b3c58kieu7vha` (`serial_number`),
  KEY `FK6s9py611xpchekuihuifhpkon` (`product_id`),
  CONSTRAINT `FK6s9py611xpchekuihuifhpkon` FOREIGN KEY (`product_id`) REFERENCES `products` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `products` (
  `id` int NOT NULL AUTO_INCREMENT,
  `cost_price` decimal(15,2) DEFAULT NULL,
  `has_serial` bit(1) DEFAULT NULL,
  `name` varchar(255) NOT NULL,
  `product_code` varchar(20) NOT NULL,
  `product_type` enum('New','Second') DEFAULT NULL,
  `remark` text,
  `reorder_level` int DEFAULT NULL,
  `selling_price` decimal(10,2) DEFAULT NULL,
  `stock_qty` int DEFAULT NULL,
  `warranty_months` int DEFAULT NULL,
  `brand_id` int DEFAULT NULL,
  `category_id` int DEFAULT NULL,
  `unit_id` int DEFAULT NULL,
  `warranty_terms` varchar(255) DEFAULT NULL,
  `photo_base64` longtext,
  `archived` bit(1) NOT NULL,
  `shelf_location` varchar(120) DEFAULT NULL,
  `warehouse_name` varchar(120) DEFAULT NULL,
  `last_purchase_cost` decimal(15,2) DEFAULT NULL,
  `version` bigint NOT NULL DEFAULT '0',
  `quarantined_qty` int NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UKo61fmio5yukmmiqgnxf8pnavn` (`name`),
  KEY `FKa3a4mpsfdf4d2y6r8ra3sc8mv` (`brand_id`),
  KEY `FKog2rp4qthbtt2lfyhfo32lsw9` (`category_id`),
  KEY `FKeex0i50vfsa5imebrfdiyhmp9` (`unit_id`),
  CONSTRAINT `FKa3a4mpsfdf4d2y6r8ra3sc8mv` FOREIGN KEY (`brand_id`) REFERENCES `brands` (`id`),
  CONSTRAINT `FKeex0i50vfsa5imebrfdiyhmp9` FOREIGN KEY (`unit_id`) REFERENCES `units` (`id`),
  CONSTRAINT `FKog2rp4qthbtt2lfyhfo32lsw9` FOREIGN KEY (`category_id`) REFERENCES `categories` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `purchase_budgets` (
  `id` int NOT NULL AUTO_INCREMENT,
  `active` bit(1) NOT NULL,
  `date_from` date NOT NULL,
  `date_to` date NOT NULL,
  `enforcement` varchar(10) NOT NULL,
  `limit_amount` decimal(18,2) NOT NULL,
  `name` varchar(120) NOT NULL,
  `category_id` int DEFAULT NULL,
  `supplier_id` int DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_purchase_budget_period` (`date_from`,`date_to`,`active`),
  KEY `idx_purchase_budget_category` (`category_id`),
  KEY `idx_purchase_budget_supplier` (`supplier_id`),
  CONSTRAINT `FK1lf0s9bhjfvhbhwpih6j3an4h` FOREIGN KEY (`supplier_id`) REFERENCES `suppliers` (`id`),
  CONSTRAINT `fk_purchase_budget_supplier` FOREIGN KEY (`supplier_id`) REFERENCES `suppliers` (`id`),
  CONSTRAINT `FKr7ivoem3s0av3njsm89n8hby5` FOREIGN KEY (`category_id`) REFERENCES `categories` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `purchase_detail_warranties` (
  `id` int NOT NULL AUTO_INCREMENT,
  `item_index` int NOT NULL,
  `serial_number` varchar(100) DEFAULT NULL,
  `warranty_end_date` date DEFAULT NULL,
  `warranty_months` int NOT NULL,
  `warranty_start_date` date DEFAULT NULL,
  `purchase_detail_id` int NOT NULL,
  PRIMARY KEY (`id`),
  KEY `FKb9ycjq2gtox3wkg9wrvt2cukv` (`purchase_detail_id`),
  CONSTRAINT `FKb9ycjq2gtox3wkg9wrvt2cukv` FOREIGN KEY (`purchase_detail_id`) REFERENCES `purchase_details` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `purchase_details` (
  `id` int NOT NULL AUTO_INCREMENT,
  `qty` int DEFAULT NULL,
  `subtotal` decimal(38,2) DEFAULT NULL,
  `unit_cost` decimal(38,2) DEFAULT NULL,
  `warranty_months` int DEFAULT NULL,
  `product_id` int NOT NULL,
  `purchase_id` int NOT NULL,
  `batch_number` varchar(100) DEFAULT NULL,
  `expiry_date` date DEFAULT NULL,
  `allocated_landed_cost` decimal(38,2) DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `FKcxl0u6f0whli7bhn1q8ja0ba3` (`product_id`),
  KEY `FK8nalokyn4ap9ebeo5hjjximls` (`purchase_id`),
  CONSTRAINT `FK8nalokyn4ap9ebeo5hjjximls` FOREIGN KEY (`purchase_id`) REFERENCES `purchases` (`id`),
  CONSTRAINT `FKcxl0u6f0whli7bhn1q8ja0ba3` FOREIGN KEY (`product_id`) REFERENCES `products` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `purchase_order_details` (
  `id` int NOT NULL AUTO_INCREMENT,
  `qty` int DEFAULT NULL,
  `received_qty` int DEFAULT NULL,
  `subtotal` decimal(38,2) DEFAULT NULL,
  `unit_cost` decimal(38,2) DEFAULT NULL,
  `product_id` int NOT NULL,
  `purchase_order_id` int NOT NULL,
  `damaged_qty` int DEFAULT NULL,
  `rejected_qty` int DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `FKpouou78xjt37rqmveufppyhtp` (`product_id`),
  KEY `FK7k5h72ashr7waatbffpug92ei` (`purchase_order_id`),
  CONSTRAINT `FK7k5h72ashr7waatbffpug92ei` FOREIGN KEY (`purchase_order_id`) REFERENCES `purchase_orders` (`id`),
  CONSTRAINT `FKpouou78xjt37rqmveufppyhtp` FOREIGN KEY (`product_id`) REFERENCES `products` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `purchase_orders` (
  `id` int NOT NULL AUTO_INCREMENT,
  `expected_date` date DEFAULT NULL,
  `order_date` datetime(6) DEFAULT NULL,
  `po_code` varchar(255) NOT NULL,
  `remark` text,
  `status` varchar(30) DEFAULT NULL,
  `total_amount` decimal(38,2) DEFAULT NULL,
  `staff_id` int NOT NULL,
  `supplier_id` int NOT NULL,
  `approved_at` datetime(6) DEFAULT NULL,
  `approved_by` varchar(255) DEFAULT NULL,
  `rejected_at` datetime(6) DEFAULT NULL,
  `rejected_by` varchar(255) DEFAULT NULL,
  `rejection_reason` varchar(500) DEFAULT NULL,
  `version` bigint NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `UK21mhvujy90elmcolyoyk02ruq` (`po_code`),
  KEY `idx_po_status` (`status`),
  KEY `idx_po_supplier` (`supplier_id`),
  KEY `FKrktnjbjmcifk2eymjl6fr5s2y` (`staff_id`),
  CONSTRAINT `FKrktnjbjmcifk2eymjl6fr5s2y` FOREIGN KEY (`staff_id`) REFERENCES `staff` (`id`),
  CONSTRAINT `FKrpdasmb8y8xs5tiy4369xpinq` FOREIGN KEY (`supplier_id`) REFERENCES `suppliers` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `purchase_return_activities` (
  `id` int NOT NULL AUTO_INCREMENT,
  `actor` varchar(255) DEFAULT NULL,
  `event_type` varchar(255) DEFAULT NULL,
  `from_status` varchar(255) DEFAULT NULL,
  `note` varchar(1000) DEFAULT NULL,
  `occurred_at` datetime(6) DEFAULT NULL,
  `to_status` varchar(255) DEFAULT NULL,
  `purchase_return_id` int NOT NULL,
  PRIMARY KEY (`id`),
  KEY `FKmjwtjtem91vrquwbjdlsl957a` (`purchase_return_id`),
  CONSTRAINT `FKmjwtjtem91vrquwbjdlsl957a` FOREIGN KEY (`purchase_return_id`) REFERENCES `purchase_returns` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `purchase_return_attachments` (
  `id` int NOT NULL AUTO_INCREMENT,
  `attachment_type` varchar(255) DEFAULT NULL,
  `content_type` varchar(255) DEFAULT NULL,
  `data_url` longtext,
  `file_name` varchar(255) DEFAULT NULL,
  `uploaded_at` datetime(6) DEFAULT NULL,
  `uploaded_by` varchar(255) DEFAULT NULL,
  `purchase_return_id` int NOT NULL,
  PRIMARY KEY (`id`),
  KEY `FKfr7rhh2wyy8es52i0lbrja6tc` (`purchase_return_id`),
  CONSTRAINT `FKfr7rhh2wyy8es52i0lbrja6tc` FOREIGN KEY (`purchase_return_id`) REFERENCES `purchase_returns` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `purchase_return_details` (
  `id` int NOT NULL AUTO_INCREMENT,
  `qty` int DEFAULT NULL,
  `serial_number` varchar(255) DEFAULT NULL,
  `subtotal` decimal(38,2) DEFAULT NULL,
  `unit_price` decimal(38,2) DEFAULT NULL,
  `product_id` int NOT NULL,
  `return_id` int NOT NULL,
  `dispatched_qty` int NOT NULL,
  `quarantined_qty` int NOT NULL,
  `reason_id` int NOT NULL,
  `allocated_shipping_cost` decimal(18,2) NOT NULL,
  PRIMARY KEY (`id`),
  KEY `FK7tta5uxl8ii1e0triierd1lwo` (`product_id`),
  KEY `FKjnyr7ac6abt7g489j0gb69num` (`return_id`),
  KEY `idx_pr_detail_reason` (`reason_id`),
  CONSTRAINT `FK7tta5uxl8ii1e0triierd1lwo` FOREIGN KEY (`product_id`) REFERENCES `products` (`id`),
  CONSTRAINT `fk_purchase_return_detail_reason` FOREIGN KEY (`reason_id`) REFERENCES `purchase_return_reasons` (`id`),
  CONSTRAINT `FKjnyr7ac6abt7g489j0gb69num` FOREIGN KEY (`return_id`) REFERENCES `purchase_returns` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `purchase_return_reasons` (
  `id` int NOT NULL AUTO_INCREMENT,
  `active` bit(1) NOT NULL,
  `code` varchar(40) NOT NULL,
  `description` varchar(500) DEFAULT NULL,
  `name` varchar(120) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UKp8mvso68tdytde4lck4pl5p0` (`code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `purchase_returns` (
  `id` int NOT NULL AUTO_INCREMENT,
  `reason` text,
  `return_date` datetime(6) DEFAULT NULL,
  `return_no` varchar(255) NOT NULL,
  `total_return_amount` decimal(38,2) DEFAULT NULL,
  `purchase_id` int DEFAULT NULL,
  `refund_amount` decimal(38,2) DEFAULT NULL,
  `status` varchar(30) DEFAULT NULL,
  `void_reason` text,
  `voided_at` datetime(6) DEFAULT NULL,
  `approval_note` varchar(500) DEFAULT NULL,
  `approved_at` datetime(6) DEFAULT NULL,
  `approved_by` varchar(120) DEFAULT NULL,
  `carrier` varchar(120) DEFAULT NULL,
  `credit_variance` decimal(18,2) DEFAULT NULL,
  `credit_variance_reason` varchar(500) DEFAULT NULL,
  `delivery_proof` longtext,
  `dispatched_at` datetime(6) DEFAULT NULL,
  `expected_credit_amount` decimal(18,2) DEFAULT NULL,
  `settled_at` datetime(6) DEFAULT NULL,
  `settlement_reference` varchar(120) DEFAULT NULL,
  `settlement_type` varchar(30) DEFAULT NULL,
  `submitted_at` datetime(6) DEFAULT NULL,
  `submitted_by` varchar(120) DEFAULT NULL,
  `supplier_credit_note_amount` decimal(18,2) DEFAULT NULL,
  `supplier_credit_note_no` varchar(120) DEFAULT NULL,
  `supplier_received_at` datetime(6) DEFAULT NULL,
  `tracking_no` varchar(120) DEFAULT NULL,
  `version` bigint NOT NULL DEFAULT '0',
  `company_shipping_portion` decimal(18,2) NOT NULL,
  `shipping_allocation_method` varchar(20) NOT NULL,
  `shipping_cost_amount` decimal(18,2) NOT NULL,
  `shipping_payer_responsibility` varchar(20) NOT NULL,
  `shipping_payment_method_id` int DEFAULT NULL,
  `shipping_posted_at` datetime(6) DEFAULT NULL,
  `shipping_transaction_reference` varchar(120) DEFAULT NULL,
  `supplier_shipping_portion` decimal(18,2) NOT NULL,
  `claim_date` datetime(6) DEFAULT NULL,
  `claim_status` varchar(30) DEFAULT NULL,
  `expected_resolution_date` datetime(6) DEFAULT NULL,
  `goods_receipt_id` int DEFAULT NULL,
  `rejected_at` datetime(6) DEFAULT NULL,
  `rejected_by` varchar(120) DEFAULT NULL,
  `rejection_reason` varchar(500) DEFAULT NULL,
  `replacement_expected_qty` int DEFAULT NULL,
  `replacement_received_qty` int DEFAULT NULL,
  `resolution_type` varchar(30) DEFAULT NULL,
  `rma_number` varchar(120) DEFAULT NULL,
  `supplier_contact` varchar(160) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UKgsljfg3a445fyb4kmnw2oc1y6` (`return_no`),
  KEY `FKf9or3ugv36oyvda32nnp6txai` (`purchase_id`),
  KEY `idx_pr_workflow_status` (`status`),
  KEY `idx_pr_shipping_payment_method` (`shipping_payment_method_id`),
  CONSTRAINT `fk_pr_shipping_payment_method` FOREIGN KEY (`shipping_payment_method_id`) REFERENCES `payment_methods` (`id`),
  CONSTRAINT `FKf9or3ugv36oyvda32nnp6txai` FOREIGN KEY (`purchase_id`) REFERENCES `purchases` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `purchases` (
  `id` int NOT NULL AUTO_INCREMENT,
  `due_amount` decimal(38,2) DEFAULT NULL,
  `due_date` date DEFAULT NULL,
  `paid_amount` decimal(38,2) DEFAULT NULL,
  `payment_status` varchar(20) DEFAULT NULL,
  `purchase_code` varchar(255) NOT NULL,
  `purchase_date` datetime(6) DEFAULT NULL,
  `remark` text,
  `total_amount` decimal(38,2) DEFAULT NULL,
  `staff_id` int NOT NULL,
  `supplier_id` int NOT NULL,
  `discount_amount` decimal(38,2) DEFAULT NULL,
  `net_amount` decimal(38,2) DEFAULT NULL,
  `refund_amount` decimal(38,2) DEFAULT NULL,
  `return_amount` decimal(38,2) DEFAULT NULL,
  `supplier_credit_amount` decimal(38,2) DEFAULT NULL,
  `attachment_data` longtext,
  `attachment_name` varchar(255) DEFAULT NULL,
  `other_charges` decimal(38,2) DEFAULT NULL,
  `po_id` int DEFAULT NULL,
  `voucher_status` enum('CANCELLED','CONFIRMED','DRAFT') DEFAULT NULL,
  `tax_amount` decimal(38,2) DEFAULT NULL,
  `payment_term_days` int DEFAULT NULL,
  `cancel_reason` text,
  `cancelled_at` datetime(6) DEFAULT NULL,
  `cancelled_by` varchar(100) DEFAULT NULL,
  `credit_limit_override` bit(1) DEFAULT NULL,
  `credit_override_at` datetime(6) DEFAULT NULL,
  `credit_override_by` varchar(100) DEFAULT NULL,
  `credit_override_reason` text,
  `currency_code` varchar(3) DEFAULT NULL,
  `exchange_rate` decimal(18,6) DEFAULT NULL,
  `foreign_net_amount` decimal(18,2) DEFAULT NULL,
  `landed_cost_allocation_method` varchar(20) DEFAULT NULL,
  `supplier_invoice_no` varchar(80) DEFAULT NULL,
  `tax_mode` varchar(20) DEFAULT NULL,
  `tax_rate` decimal(38,2) DEFAULT NULL,
  `warehouse_name` varchar(120) DEFAULT NULL,
  `withholding_tax_amount` decimal(38,2) DEFAULT NULL,
  `version` bigint NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `UKf0r2exkhc0bwcnw47cdneda99` (`purchase_code`),
  KEY `idx_purchase_date` (`purchase_date`),
  KEY `idx_purchase_supplier` (`supplier_id`),
  KEY `idx_purchase_payment_status` (`payment_status`),
  KEY `FK1uf4rjnbind4fuxbeqwqs2hnh` (`staff_id`),
  CONSTRAINT `FK1uf4rjnbind4fuxbeqwqs2hnh` FOREIGN KEY (`staff_id`) REFERENCES `staff` (`id`),
  CONSTRAINT `FK9ho3w23v5du4x0hrp6rqs1wmh` FOREIGN KEY (`supplier_id`) REFERENCES `suppliers` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `quotation_details` (
  `id` int NOT NULL AUTO_INCREMENT,
  `discount_amount` decimal(15,2) NOT NULL,
  `qty` int NOT NULL,
  `subtotal` decimal(15,2) NOT NULL,
  `unit_price` decimal(15,2) NOT NULL,
  `product_id` int NOT NULL,
  `quotation_id` int NOT NULL,
  PRIMARY KEY (`id`),
  KEY `FKaqydkynkh434ifin8tw1jehq6` (`product_id`),
  KEY `FKolxi2hah5a2praypvlyrxmiov` (`quotation_id`),
  CONSTRAINT `FKaqydkynkh434ifin8tw1jehq6` FOREIGN KEY (`product_id`) REFERENCES `products` (`id`),
  CONSTRAINT `FKolxi2hah5a2praypvlyrxmiov` FOREIGN KEY (`quotation_id`) REFERENCES `quotations` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `quotations` (
  `id` int NOT NULL AUTO_INCREMENT,
  `converted_at` datetime(6) DEFAULT NULL,
  `converted_by` varchar(100) DEFAULT NULL,
  `converted_sale_id` int DEFAULT NULL,
  `discount_amount` decimal(15,2) NOT NULL,
  `net_amount` decimal(15,2) NOT NULL,
  `quotation_code` varchar(50) NOT NULL,
  `quotation_date` datetime(6) NOT NULL,
  `remark` text,
  `status` enum('ACCEPTED','CANCELLED','CONVERTED_TO_SALE','DRAFT','EXPIRED','REJECTED','SENT') NOT NULL,
  `terms` text,
  `total_amount` decimal(15,2) NOT NULL,
  `valid_until` date NOT NULL,
  `customer_id` int NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UKembni47qrnsforfcdkke30w8s` (`quotation_code`),
  KEY `idx_quote_code` (`quotation_code`),
  KEY `idx_quote_status` (`status`),
  KEY `idx_quote_customer` (`customer_id`),
  CONSTRAINT `FKopxse0lfxbu412h9w1ybpuwxk` FOREIGN KEY (`customer_id`) REFERENCES `customer` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `rework_part_resolutions` (
  `id` int NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) DEFAULT NULL,
  `customer_charge` decimal(15,2) DEFAULT NULL,
  `old_part_disposition` enum('DAMAGED','QUARANTINE','REUSE','SUPPLIER_RETURN') DEFAULT NULL,
  `old_serial_numbers` text,
  `original_credit` decimal(15,2) DEFAULT NULL,
  `reason` text,
  `refund_amount` decimal(15,2) DEFAULT NULL,
  `replacement_price` decimal(15,2) DEFAULT NULL,
  `replacement_qty` int DEFAULT NULL,
  `replacement_serial_numbers` text,
  `resolution_mode` enum('REFUND','REPLACE_SAME','SERVICE_ONLY','UPGRADE') NOT NULL,
  `original_part_id` int DEFAULT NULL,
  `replacement_product_id` int DEFAULT NULL,
  `rework_job_id` int NOT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_rework_resolution_job` (`rework_job_id`),
  KEY `idx_rework_resolution_original_part` (`original_part_id`),
  KEY `FKlwjlquqji1qeumctm6kyy7hoh` (`replacement_product_id`),
  CONSTRAINT `FKj5jhqfp2wba3weca9pdxaw9b5` FOREIGN KEY (`rework_job_id`) REFERENCES `service_jobs` (`id`),
  CONSTRAINT `FKlwjlquqji1qeumctm6kyy7hoh` FOREIGN KEY (`replacement_product_id`) REFERENCES `products` (`id`),
  CONSTRAINT `FKnllgnc0mdc8abdw05ru3jyi47` FOREIGN KEY (`original_part_id`) REFERENCES `service_job_parts` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `roles` (
  `id` int NOT NULL AUTO_INCREMENT,
  `description` varchar(255) DEFAULT NULL,
  `name` varchar(50) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UKofx66keruapi6vyqpv6f2or37` (`name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `roles_permissions` (
  `role_id` int NOT NULL,
  `permission_id` bigint NOT NULL,
  PRIMARY KEY (`role_id`,`permission_id`),
  KEY `FKbx9r9uw77p58gsq4mus0mec0o` (`permission_id`),
  CONSTRAINT `FKbx9r9uw77p58gsq4mus0mec0o` FOREIGN KEY (`permission_id`) REFERENCES `permissions` (`id`),
  CONSTRAINT `FKqi9odri6c1o81vjox54eedwyh` FOREIGN KEY (`role_id`) REFERENCES `roles` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sale_details` (
  `id` int NOT NULL AUTO_INCREMENT,
  `cost_price_snapshot` decimal(15,2) DEFAULT NULL,
  `discount_amount` decimal(15,2) DEFAULT NULL,
  `is_foc` bit(1) DEFAULT NULL,
  `qty` int DEFAULT NULL,
  `serial_number` varchar(255) DEFAULT NULL,
  `subtotal` decimal(38,2) DEFAULT NULL,
  `unit_price` decimal(15,2) NOT NULL,
  `warranty_expiry_date` date DEFAULT NULL,
  `warranty_months` int DEFAULT NULL,
  `product_id` int NOT NULL,
  `sale_id` int NOT NULL,
  `custom_voucher_price` decimal(15,2) DEFAULT NULL,
  `customer_margin` decimal(15,2) DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `FKqvh82ispfukxa2yssnok0m12o` (`product_id`),
  KEY `FK6nruj5m7ntwhc29etigqnlk0m` (`sale_id`),
  CONSTRAINT `FK6nruj5m7ntwhc29etigqnlk0m` FOREIGN KEY (`sale_id`) REFERENCES `sales` (`id`),
  CONSTRAINT `FKqvh82ispfukxa2yssnok0m12o` FOREIGN KEY (`product_id`) REFERENCES `products` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sale_lot_allocations` (
  `id` int NOT NULL AUTO_INCREMENT,
  `allocated_qty` int NOT NULL,
  `returned_qty` int NOT NULL,
  `sale_detail_id` int NOT NULL,
  `stock_lot_id` int NOT NULL,
  PRIMARY KEY (`id`),
  KEY `FKqf4fwd27q8374im9q6m6affvx` (`stock_lot_id`),
  KEY `idx_sale_lot_detail` (`sale_detail_id`),
  CONSTRAINT `FKqf4fwd27q8374im9q6m6affvx` FOREIGN KEY (`stock_lot_id`) REFERENCES `stock_lots` (`id`),
  CONSTRAINT `FKsl5t45y86aqe2xqhc1nto2kx1` FOREIGN KEY (`sale_detail_id`) REFERENCES `sale_details` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sale_return_details` (
  `id` int NOT NULL AUTO_INCREMENT,
  `qty` int DEFAULT NULL,
  `serial_number` varchar(255) DEFAULT NULL,
  `subtotal` decimal(38,2) DEFAULT NULL,
  `unit_price` decimal(15,2) NOT NULL,
  `product_id` int NOT NULL,
  `return_id` int NOT NULL,
  `restock` bit(1) NOT NULL,
  `reason_id` int DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `FK6cv7g4chqvnbn0d08ken9am8u` (`product_id`),
  KEY `FKq3c5bendgsgiy4avug402exjb` (`return_id`),
  KEY `idx_sr_detail_reason` (`reason_id`),
  CONSTRAINT `FK6cv7g4chqvnbn0d08ken9am8u` FOREIGN KEY (`product_id`) REFERENCES `products` (`id`),
  CONSTRAINT `fk_sale_return_detail_reason` FOREIGN KEY (`reason_id`) REFERENCES `sale_return_reasons` (`id`),
  CONSTRAINT `FKe9307eo6ssphfvahnaxi9g927` FOREIGN KEY (`reason_id`) REFERENCES `sale_return_reasons` (`id`),
  CONSTRAINT `FKq3c5bendgsgiy4avug402exjb` FOREIGN KEY (`return_id`) REFERENCES `sale_returns` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sale_return_lot_allocations` (
  `id` int NOT NULL AUTO_INCREMENT,
  `qty` int NOT NULL,
  `sale_lot_allocation_id` int NOT NULL,
  `sale_return_detail_id` int NOT NULL,
  PRIMARY KEY (`id`),
  KEY `FK5jj1uhsvfdvcfiqdkjrpq6rxt` (`sale_lot_allocation_id`),
  KEY `idx_return_lot_detail` (`sale_return_detail_id`),
  CONSTRAINT `FK5jj1uhsvfdvcfiqdkjrpq6rxt` FOREIGN KEY (`sale_lot_allocation_id`) REFERENCES `sale_lot_allocations` (`id`),
  CONSTRAINT `FK8x7xhwrg7xd7l6kmifg89n4g2` FOREIGN KEY (`sale_return_detail_id`) REFERENCES `sale_return_details` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sale_return_reasons` (
  `id` int NOT NULL AUTO_INCREMENT,
  `active` bit(1) NOT NULL,
  `code` varchar(40) NOT NULL,
  `description` varchar(500) DEFAULT NULL,
  `name` varchar(120) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UK7nk7m1p62o9euc7r36wph73ta` (`code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sale_returns` (
  `id` int NOT NULL AUTO_INCREMENT,
  `deleted` bit(1) DEFAULT NULL,
  `reason` text,
  `refund_amount` decimal(15,2) DEFAULT NULL,
  `return_code` varchar(50) NOT NULL,
  `return_date` datetime(6) DEFAULT NULL,
  `total_return_amount` decimal(15,2) DEFAULT NULL,
  `transaction_no` varchar(100) DEFAULT NULL,
  `payment_method_id` int DEFAULT NULL,
  `sale_id` int NOT NULL,
  `staff_id` int DEFAULT NULL,
  `credit_note_no` varchar(50) DEFAULT NULL,
  `credit_posted_amount` decimal(15,2) DEFAULT NULL,
  `settlement_type` varchar(30) DEFAULT NULL,
  `status` varchar(30) DEFAULT NULL,
  `version` bigint NOT NULL DEFAULT '0',
  `void_reason` text,
  `voided_at` datetime(6) DEFAULT NULL,
  `voided_by` varchar(120) DEFAULT NULL,
  `warehouse_name` varchar(120) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UK7l1ikry9ke3t9mcqydywcss7q` (`return_code`),
  KEY `FK3mfds5xv34gg222t9ancrqdtq` (`payment_method_id`),
  KEY `FKl3sgec3y7ahl3lw37yubo5xad` (`sale_id`),
  KEY `FKq4us96wiv8fqitfm829md3d8e` (`staff_id`),
  KEY `idx_sr_status` (`status`),
  CONSTRAINT `FK3mfds5xv34gg222t9ancrqdtq` FOREIGN KEY (`payment_method_id`) REFERENCES `payment_methods` (`id`),
  CONSTRAINT `FKl3sgec3y7ahl3lw37yubo5xad` FOREIGN KEY (`sale_id`) REFERENCES `sales` (`id`),
  CONSTRAINT `FKq4us96wiv8fqitfm829md3d8e` FOREIGN KEY (`staff_id`) REFERENCES `staff` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sales` (
  `id` int NOT NULL AUTO_INCREMENT,
  `credit_status` enum('Active','Not_Credit','Overdue','Paid') DEFAULT NULL,
  `discount_amount` decimal(15,2) DEFAULT NULL,
  `due_amount` decimal(15,2) DEFAULT NULL,
  `due_date` date DEFAULT NULL,
  `is_foc` bit(1) DEFAULT NULL,
  `net_amount` decimal(15,2) DEFAULT NULL,
  `paid_amount` decimal(15,2) DEFAULT NULL,
  `payment_status` enum('Cancelled','Paid','Partial','Pending') DEFAULT NULL,
  `remark` text,
  `sale_code` varchar(50) NOT NULL,
  `sale_date` datetime(6) DEFAULT NULL,
  `total_amount` decimal(15,2) DEFAULT NULL,
  `customer_id` int NOT NULL,
  `staff_id` int DEFAULT NULL,
  `quotation_code` varchar(50) DEFAULT NULL,
  `quotation_id` int DEFAULT NULL,
  `void_reason` text,
  `is_voided` bit(1) NOT NULL,
  `voided_at` datetime(6) DEFAULT NULL,
  `voided_by` varchar(100) DEFAULT NULL,
  `tax_amount` decimal(15,2) DEFAULT NULL,
  `warehouse_name` varchar(120) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UKthsv1clvsbpuewmxr767j5xga` (`sale_code`),
  KEY `idx_sale_date` (`sale_date`),
  KEY `idx_sale_customer` (`customer_id`),
  KEY `idx_sale_payment_status` (`payment_status`),
  KEY `idx_sale_code` (`sale_code`),
  KEY `FK5197h2wh3a1prsmx1yxfoh36y` (`staff_id`),
  CONSTRAINT `FK5197h2wh3a1prsmx1yxfoh36y` FOREIGN KEY (`staff_id`) REFERENCES `staff` (`id`),
  CONSTRAINT `FK72ep16wuoj7nllumicmk2ie3s` FOREIGN KEY (`customer_id`) REFERENCES `customer` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `service_item_price_history` (
  `id` int NOT NULL AUTO_INCREMENT,
  `changed_at` datetime(6) NOT NULL,
  `changed_by` varchar(120) DEFAULT NULL,
  `new_cost` decimal(15,2) DEFAULT NULL,
  `new_price` decimal(15,2) DEFAULT NULL,
  `old_cost` decimal(15,2) DEFAULT NULL,
  `old_price` decimal(15,2) DEFAULT NULL,
  `service_item_id` int NOT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_service_price_history_item` (`service_item_id`),
  CONSTRAINT `FKrhg769qoaldrloe8jqv53hrt` FOREIGN KEY (`service_item_id`) REFERENCES `services` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `service_job_activities` (
  `id` int NOT NULL AUTO_INCREMENT,
  `actor` varchar(255) DEFAULT NULL,
  `event_type` varchar(255) DEFAULT NULL,
  `from_status` varchar(255) DEFAULT NULL,
  `note` varchar(1000) DEFAULT NULL,
  `occurred_at` datetime(6) DEFAULT NULL,
  `to_status` varchar(255) DEFAULT NULL,
  `service_job_id` int NOT NULL,
  PRIMARY KEY (`id`),
  KEY `FKpasmjueli2mqewdv51vakai1x` (`service_job_id`),
  CONSTRAINT `FKpasmjueli2mqewdv51vakai1x` FOREIGN KEY (`service_job_id`) REFERENCES `service_jobs` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `service_job_attachments` (
  `id` int NOT NULL AUTO_INCREMENT,
  `attachment_type` varchar(255) DEFAULT NULL,
  `content_type` varchar(255) DEFAULT NULL,
  `data_url` longtext,
  `file_name` varchar(255) DEFAULT NULL,
  `uploaded_at` datetime(6) DEFAULT NULL,
  `uploaded_by` varchar(255) DEFAULT NULL,
  `service_job_id` int NOT NULL,
  PRIMARY KEY (`id`),
  KEY `FK152u75ls7r943024jbmvbgbj3` (`service_job_id`),
  CONSTRAINT `FK152u75ls7r943024jbmvbgbj3` FOREIGN KEY (`service_job_id`) REFERENCES `service_jobs` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `service_job_lines` (
  `id` int NOT NULL AUTO_INCREMENT,
  `price` decimal(15,2) DEFAULT NULL,
  `qty` int DEFAULT NULL,
  `subtotal` decimal(15,2) DEFAULT NULL,
  `service_item_id` int NOT NULL,
  `service_job_id` int NOT NULL,
  `warranty_months` int DEFAULT NULL,
  `warranty_covered` bit(1) DEFAULT NULL,
  `confirmation_status` enum('COMPLETED','CUSTOMER_APPROVED','CUSTOMER_REJECTED','INSPECTING','IN_PROGRESS','RECOMMENDED') DEFAULT NULL,
  `approved_price` decimal(15,2) DEFAULT NULL,
  `billed_price` decimal(15,2) DEFAULT NULL,
  `catalog_price` decimal(15,2) DEFAULT NULL,
  `estimated_price` decimal(15,2) DEFAULT NULL,
  `max_price` decimal(15,2) DEFAULT NULL,
  `min_price` decimal(15,2) DEFAULT NULL,
  `price_change_reason` varchar(500) DEFAULT NULL,
  `price_override_approved` bit(1) DEFAULT NULL,
  `price_override_approved_by` varchar(120) DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `FKmjp09ltupaauw9yfu3ad5xwiw` (`service_item_id`),
  KEY `FK3jvh79d3v7ogseedrbldl99nl` (`service_job_id`),
  CONSTRAINT `FK3jvh79d3v7ogseedrbldl99nl` FOREIGN KEY (`service_job_id`) REFERENCES `service_jobs` (`id`),
  CONSTRAINT `FKmjp09ltupaauw9yfu3ad5xwiw` FOREIGN KEY (`service_item_id`) REFERENCES `services` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `service_job_notifications` (
  `id` int NOT NULL AUTO_INCREMENT,
  `actor` varchar(255) DEFAULT NULL,
  `channel` varchar(255) DEFAULT NULL,
  `note` varchar(1000) DEFAULT NULL,
  `notified_at` datetime(6) DEFAULT NULL,
  `service_job_id` int NOT NULL,
  PRIMARY KEY (`id`),
  KEY `FK38j44sg4v9r8ieitbvia2v0b9` (`service_job_id`),
  CONSTRAINT `FK38j44sg4v9r8ieitbvia2v0b9` FOREIGN KEY (`service_job_id`) REFERENCES `service_jobs` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `service_job_parts` (
  `id` int NOT NULL AUTO_INCREMENT,
  `qty` int DEFAULT NULL,
  `serial_numbers` text,
  `subtotal` decimal(15,2) DEFAULT NULL,
  `unit_price` decimal(15,2) DEFAULT NULL,
  `product_id` int NOT NULL,
  `service_job_id` int NOT NULL,
  `discount_amount` decimal(15,2) DEFAULT NULL,
  `warranty_covered` bit(1) DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `FK9dkhgklokgesu7dohvihto7mv` (`product_id`),
  KEY `FK4lcrfwiopxhw7yiyp7okveowj` (`service_job_id`),
  CONSTRAINT `FK4lcrfwiopxhw7yiyp7okveowj` FOREIGN KEY (`service_job_id`) REFERENCES `service_jobs` (`id`),
  CONSTRAINT `FK9dkhgklokgesu7dohvihto7mv` FOREIGN KEY (`product_id`) REFERENCES `products` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `service_jobs` (
  `id` int NOT NULL AUTO_INCREMENT,
  `booking_id` int DEFAULT NULL,
  `completed_date` datetime(6) DEFAULT NULL,
  `credit_status` enum('Active','Not_Credit','Overdue','Paid') DEFAULT NULL,
  `delivered_date` datetime(6) DEFAULT NULL,
  `diagnosis_notes` text,
  `discount_amount` decimal(15,2) DEFAULT NULL,
  `due_amount` decimal(15,2) DEFAULT NULL,
  `due_date` date DEFAULT NULL,
  `estimated_completion` datetime(6) DEFAULT NULL,
  `estimated_cost` decimal(15,2) DEFAULT NULL,
  `final_cost` decimal(15,2) DEFAULT NULL,
  `is_foc` bit(1) DEFAULT NULL,
  `item_condition` text,
  `item_name` varchar(200) DEFAULT NULL,
  `job_no` varchar(20) NOT NULL,
  `net_amount` decimal(15,2) DEFAULT NULL,
  `paid_amount` decimal(15,2) DEFAULT NULL,
  `parent_job_id` int DEFAULT NULL,
  `payment_status` enum('Cancelled','Paid','Partial','Pending') DEFAULT NULL,
  `problem_desc` text,
  `received_date` datetime(6) DEFAULT NULL,
  `remark` text,
  `is_rework` bit(1) DEFAULT NULL,
  `rework_type` enum('ADDITIONAL','REPLACEMENT','WARRANTY') DEFAULT NULL,
  `sale_id` int DEFAULT NULL,
  `status` enum('CANCELLED','COMPLETED','DELIVERED','INSPECTING','IN_PROGRESS','RECEIVED','WAITING_PARTS') DEFAULT NULL,
  `assigned_staff_id` int DEFAULT NULL,
  `customer_id` int NOT NULL,
  `payment_method_id` int DEFAULT NULL,
  `accessories_received` text,
  `device_conditions` text,
  `color` varchar(80) DEFAULT NULL,
  `serial_no` varchar(120) DEFAULT NULL,
  `shelf_location_id` int DEFAULT NULL,
  `replacement_item_name` varchar(200) DEFAULT NULL,
  `replacement_reason` text,
  `replacement_serial_no` varchar(120) DEFAULT NULL,
  `estimate_approved` bit(1) DEFAULT NULL,
  `estimate_approved_at` datetime(6) DEFAULT NULL,
  `estimate_approved_by` varchar(120) DEFAULT NULL,
  `hold_reason` varchar(500) DEFAULT NULL,
  `last_notified_at` datetime(6) DEFAULT NULL,
  `priority` varchar(20) DEFAULT NULL,
  `void_reason` text,
  `voided` bit(1) DEFAULT NULL,
  `voided_at` datetime(6) DEFAULT NULL,
  `voided_by` varchar(120) DEFAULT NULL,
  `work_started_at` datetime(6) DEFAULT NULL,
  `helper_staff_id` int DEFAULT NULL,
  `part_requests` text,
  `device_type` varchar(80) DEFAULT NULL,
  `modified_at` datetime(6) DEFAULT NULL,
  `modified_by` varchar(120) DEFAULT NULL,
  `service_mode` enum('INDOOR','OUTDOOR') DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UK933t0mfpv805p8wh915r9tkih` (`job_no`),
  KEY `idx_sj_status` (`status`),
  KEY `idx_sj_customer` (`customer_id`),
  KEY `idx_sj_received_date` (`received_date`),
  KEY `idx_sj_payment_status` (`payment_status`),
  KEY `FKfsf3lvhx43ygsai613cgtqriy` (`assigned_staff_id`),
  KEY `FK15rnwwlrseqwlolhj7sk05gew` (`payment_method_id`),
  KEY `FK42cqjmx3wjmqecfjoc7tiidmv` (`shelf_location_id`),
  KEY `FK7y477xxrr2vws6abrwouhs3q6` (`helper_staff_id`),
  KEY `idx_sj_priority` (`priority`),
  KEY `idx_sj_voided` (`voided`),
  CONSTRAINT `FK15rnwwlrseqwlolhj7sk05gew` FOREIGN KEY (`payment_method_id`) REFERENCES `payment_methods` (`id`),
  CONSTRAINT `FK42cqjmx3wjmqecfjoc7tiidmv` FOREIGN KEY (`shelf_location_id`) REFERENCES `shelf_locations` (`id`),
  CONSTRAINT `FK7y477xxrr2vws6abrwouhs3q6` FOREIGN KEY (`helper_staff_id`) REFERENCES `staff` (`id`),
  CONSTRAINT `FKa73qtrrnv34cbl73sfoubrjv7` FOREIGN KEY (`customer_id`) REFERENCES `customer` (`id`),
  CONSTRAINT `FKfsf3lvhx43ygsai613cgtqriy` FOREIGN KEY (`assigned_staff_id`) REFERENCES `staff` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `service_type` (
  `id` int NOT NULL AUTO_INCREMENT,
  `description` text,
  `is_active` bit(1) DEFAULT NULL,
  `name` varchar(150) NOT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `services` (
  `id` int NOT NULL AUTO_INCREMENT,
  `code` varchar(20) NOT NULL,
  `is_active` bit(1) DEFAULT NULL,
  `item` varchar(100) NOT NULL,
  `price` decimal(10,2) NOT NULL,
  `service_type_id` int NOT NULL,
  `sub_service_type_id` int DEFAULT NULL,
  `cost_price` decimal(15,2) DEFAULT NULL,
  `description` text,
  `duration_minutes` int DEFAULT NULL,
  `foc_default` bit(1) DEFAULT NULL,
  `skill_required` varchar(120) DEFAULT NULL,
  `tax_rate` decimal(7,2) DEFAULT NULL,
  `warranty_months` int DEFAULT NULL,
  `commission_percent` decimal(7,2) DEFAULT NULL,
  `default_required_parts` text,
  `max_price` decimal(15,2) DEFAULT NULL,
  `min_price` decimal(15,2) DEFAULT NULL,
  `supported_device_types` text,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UK3ff0vue74scb6a5dbic1yj2ip` (`code`),
  UNIQUE KEY `UKfhekms238f0k9rxesxuy4tk0b` (`item`),
  KEY `FKa79h7xirkkbbb0u98d7nl8dvt` (`service_type_id`),
  KEY `FKg1yo9q0pf1gtigl681x0etugc` (`sub_service_type_id`),
  CONSTRAINT `FKa79h7xirkkbbb0u98d7nl8dvt` FOREIGN KEY (`service_type_id`) REFERENCES `service_type` (`id`),
  CONSTRAINT `FKg1yo9q0pf1gtigl681x0etugc` FOREIGN KEY (`sub_service_type_id`) REFERENCES `sub_service_type` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `shelf_locations` (
  `id` int NOT NULL AUTO_INCREMENT,
  `is_active` bit(1) NOT NULL,
  `code` varchar(30) NOT NULL,
  `label` varchar(100) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UK63wnqoptvnt776o38y8plvbug` (`code`),
  KEY `idx_shelf_code` (`code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `staff` (
  `id` int NOT NULL AUTO_INCREMENT,
  `basic_salary` decimal(15,2) DEFAULT NULL,
  `is_active` bit(1) DEFAULT NULL,
  `name` varchar(50) NOT NULL,
  `phone` varchar(20) DEFAULT NULL,
  `role` varchar(50) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UKoqg5g7ejg0vk2ew0thf2asi4` (`phone`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `staff_live_location` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `accuracy` decimal(8,2) DEFAULT NULL,
  `latitude` decimal(10,7) DEFAULT NULL,
  `longitude` decimal(10,7) DEFAULT NULL,
  `recorded_at` datetime(6) DEFAULT NULL,
  `server_received_at` datetime(6) DEFAULT NULL,
  `active_visit_id` bigint DEFAULT NULL,
  `staff_id` int NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_live_staff` (`staff_id`),
  UNIQUE KEY `UKph2d2oe5vr7swulpq346ohmuo` (`active_visit_id`),
  CONSTRAINT `FK6q980c4lpcn8w5yp0qv34u23l` FOREIGN KEY (`active_visit_id`) REFERENCES `technician_visits` (`id`),
  CONSTRAINT `FKmjq53dd8ynourtavbieyfxf61` FOREIGN KEY (`staff_id`) REFERENCES `staff` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `stock_adjustments` (
  `id` int NOT NULL AUTO_INCREMENT,
  `adjustment_type` enum('CORRECTION','DAMAGE','FOUND','LOSS') NOT NULL,
  `created_at` datetime(6) DEFAULT NULL,
  `qty_after` int NOT NULL,
  `qty_before` int NOT NULL,
  `qty_change` int NOT NULL,
  `reason` text,
  `serial_numbers` text,
  `product_id` int NOT NULL,
  `staff_id` int NOT NULL,
  PRIMARY KEY (`id`),
  KEY `FKh0emjv0ifyv4bpghf1pfcaue4` (`product_id`),
  KEY `FKmw3gor5u1q3fbmggckn938x15` (`staff_id`),
  CONSTRAINT `FKh0emjv0ifyv4bpghf1pfcaue4` FOREIGN KEY (`product_id`) REFERENCES `products` (`id`),
  CONSTRAINT `FKmw3gor5u1q3fbmggckn938x15` FOREIGN KEY (`staff_id`) REFERENCES `staff` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `stock_lots` (
  `id` int NOT NULL AUTO_INCREMENT,
  `batch_number` varchar(100) DEFAULT NULL,
  `expiry_date` date DEFAULT NULL,
  `received_at` datetime(6) NOT NULL,
  `received_qty` int NOT NULL,
  `remaining_qty` int NOT NULL,
  `status` varchar(20) NOT NULL,
  `warehouse_name` varchar(120) DEFAULT NULL,
  `product_id` int NOT NULL,
  `purchase_detail_id` int NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UKgfkays101ns48t5s11c4f9op8` (`purchase_detail_id`),
  KEY `idx_stock_lot_fefo` (`product_id`,`status`,`expiry_date`,`received_at`),
  KEY `idx_stock_lot_expiry` (`status`,`expiry_date`,`remaining_qty`),
  CONSTRAINT `FKkfe8gmu4wo9fvgjfq39r3am6n` FOREIGN KEY (`product_id`) REFERENCES `products` (`id`),
  CONSTRAINT `FKm3261ocmvog7e59vcgunu9sli` FOREIGN KEY (`purchase_detail_id`) REFERENCES `purchase_details` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `stock_movements` (
  `id` int NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) DEFAULT NULL,
  `movement_type` enum('ADJUST','IN','OUT','RETURN') DEFAULT NULL,
  `qty` int DEFAULT NULL,
  `reference_id` int DEFAULT NULL,
  `reference_type` varchar(255) DEFAULT NULL,
  `product_id` int NOT NULL,
  `warehouse_name` varchar(120) DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_sm_product` (`product_id`),
  KEY `idx_sm_created_at` (`created_at`),
  KEY `idx_sm_ref` (`reference_type`,`reference_id`),
  CONSTRAINT `FKjcaag8ogfjxpwmqypi1wfdaog` FOREIGN KEY (`product_id`) REFERENCES `products` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sub_service_type` (
  `id` int NOT NULL AUTO_INCREMENT,
  `description` text,
  `is_active` bit(1) DEFAULT NULL,
  `name` varchar(150) NOT NULL,
  `service_type_id` int NOT NULL,
  PRIMARY KEY (`id`),
  KEY `FKqxbt794ndfyefe33acr2pkxl7` (`service_type_id`),
  CONSTRAINT `FKqxbt794ndfyefe33acr2pkxl7` FOREIGN KEY (`service_type_id`) REFERENCES `service_type` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `supplier_credit_applications` (
  `id` int NOT NULL AUTO_INCREMENT,
  `advance_used` decimal(38,2) DEFAULT NULL,
  `amount` decimal(38,2) DEFAULT NULL,
  `application_no` varchar(255) NOT NULL,
  `applied_at` datetime(6) DEFAULT NULL,
  `applied_by` varchar(255) DEFAULT NULL,
  `reason` varchar(255) DEFAULT NULL,
  `return_credit_used` decimal(38,2) DEFAULT NULL,
  `supplier_id` int NOT NULL,
  `target_purchase_id` int NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UKf5pjumed9ghsitqhm2mewnggi` (`application_no`),
  KEY `FK9kuggsr36jbbpmdcftymhtq2a` (`supplier_id`),
  KEY `FKqs96lc5ebcu8rtr6yy4o2wfof` (`target_purchase_id`),
  CONSTRAINT `FK9kuggsr36jbbpmdcftymhtq2a` FOREIGN KEY (`supplier_id`) REFERENCES `suppliers` (`id`),
  CONSTRAINT `FKqs96lc5ebcu8rtr6yy4o2wfof` FOREIGN KEY (`target_purchase_id`) REFERENCES `purchases` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `supplier_payment_allocations` (
  `id` int NOT NULL AUTO_INCREMENT,
  `amount` decimal(38,2) DEFAULT NULL,
  `purchase_id` int NOT NULL,
  `supplier_payment_id` int NOT NULL,
  PRIMARY KEY (`id`),
  KEY `FK4fc5td1563ctbs6in00rtch1u` (`purchase_id`),
  KEY `FK60ikfyvhm1uhm0i8yidfwq4u9` (`supplier_payment_id`),
  CONSTRAINT `FK4fc5td1563ctbs6in00rtch1u` FOREIGN KEY (`purchase_id`) REFERENCES `purchases` (`id`),
  CONSTRAINT `FK60ikfyvhm1uhm0i8yidfwq4u9` FOREIGN KEY (`supplier_payment_id`) REFERENCES `supplier_payments` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `supplier_payments` (
  `id` int NOT NULL AUTO_INCREMENT,
  `advance_amount` decimal(38,2) DEFAULT NULL,
  `allocated_amount` decimal(38,2) DEFAULT NULL,
  `paid_by` varchar(255) DEFAULT NULL,
  `payment_date` datetime(6) DEFAULT NULL,
  `payment_no` varchar(255) NOT NULL,
  `remark` varchar(255) DEFAULT NULL,
  `total_amount` decimal(38,2) DEFAULT NULL,
  `transaction_no` varchar(255) DEFAULT NULL,
  `payment_method_id` int NOT NULL,
  `supplier_id` int NOT NULL,
  `void_reason` varchar(255) DEFAULT NULL,
  `voided` bit(1) DEFAULT NULL,
  `voided_at` datetime(6) DEFAULT NULL,
  `voided_by` varchar(255) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UK2gsi3x126v5nk50mcuy0lyyil` (`payment_no`),
  KEY `FKblhwawd61xdhu2g3ey2n3y57s` (`payment_method_id`),
  KEY `FKdwv3fhnvnbuvd6h2ri8iuiw2q` (`supplier_id`),
  CONSTRAINT `FKblhwawd61xdhu2g3ey2n3y57s` FOREIGN KEY (`payment_method_id`) REFERENCES `payment_methods` (`id`),
  CONSTRAINT `FKdwv3fhnvnbuvd6h2ri8iuiw2q` FOREIGN KEY (`supplier_id`) REFERENCES `suppliers` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `suppliers` (
  `id` int NOT NULL AUTO_INCREMENT,
  `address` text,
  `code` varchar(20) NOT NULL,
  `current_balance` decimal(15,2) DEFAULT NULL,
  `name` varchar(150) NOT NULL,
  `opening_balance` decimal(15,2) DEFAULT NULL,
  `phone` varchar(20) DEFAULT NULL,
  `credit_limit` decimal(15,2) DEFAULT NULL,
  `default_credit_days` int DEFAULT NULL,
  `advance_balance` decimal(15,2) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UK8kh5crh75ye2imfi5yv37p61o` (`code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `technician_location_pings` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `accuracy` decimal(8,2) DEFAULT NULL,
  `client_ping_id` varchar(40) NOT NULL,
  `latitude` decimal(10,7) NOT NULL,
  `longitude` decimal(10,7) NOT NULL,
  `received_at` datetime(6) NOT NULL,
  `recorded_at` datetime(6) NOT NULL,
  `visit_id` bigint NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_ping_client_id` (`client_ping_id`),
  KEY `idx_ping_visit_time` (`visit_id`,`recorded_at`),
  CONSTRAINT `FK6yagodbtuyq9vxjkrgr6lklhh` FOREIGN KEY (`visit_id`) REFERENCES `technician_visits` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `technician_visit_events` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `event_type` enum('ARRIVED','CANCELLED','CUSTOMER_DEPARTED','ENDED','GPS_HISTORY_DELETED','LONG_STOP','NEAR_CUSTOMER','REASON_ADDED','RESUMED','STARTED','STOPPED') NOT NULL,
  `latitude` decimal(10,7) DEFAULT NULL,
  `longitude` decimal(10,7) DEFAULT NULL,
  `note` varchar(500) DEFAULT NULL,
  `occurred_at` datetime(6) NOT NULL,
  `reason_code` varchar(40) DEFAULT NULL,
  `visit_id` bigint NOT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_tve_visit_time` (`visit_id`,`occurred_at`),
  CONSTRAINT `FKerbddpbd9ypsyw8o55pjgf1om` FOREIGN KEY (`visit_id`) REFERENCES `technician_visits` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `technician_visits` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `arrive_latitude` decimal(10,7) DEFAULT NULL,
  `arrive_longitude` decimal(10,7) DEFAULT NULL,
  `arrived_at` datetime(6) DEFAULT NULL,
  `cancel_reason` varchar(500) DEFAULT NULL,
  `end_latitude` decimal(10,7) DEFAULT NULL,
  `end_longitude` decimal(10,7) DEFAULT NULL,
  `ended_at` datetime(6) DEFAULT NULL,
  `last_moved_at` datetime(6) DEFAULT NULL,
  `start_latitude` decimal(10,7) DEFAULT NULL,
  `start_longitude` decimal(10,7) DEFAULT NULL,
  `started_at` datetime(6) DEFAULT NULL,
  `status` enum('CANCELLED','COMPLETED','EN_ROUTE','ON_SITE','RETURNING') NOT NULL,
  `customer_id` int NOT NULL,
  `service_job_id` int NOT NULL,
  `staff_id` int NOT NULL,
  `departure_latitude` decimal(10,7) DEFAULT NULL,
  `departure_longitude` decimal(10,7) DEFAULT NULL,
  `left_customer_at` datetime(6) DEFAULT NULL,
  `outcome` enum('BROUGHT_TO_SHOP','CUSTOMER_UNAVAILABLE','FIXED_ON_SITE','OTHER','PARTS_REQUIRED','RESCHEDULED') DEFAULT NULL,
  `outcome_note` varchar(500) DEFAULT NULL,
  `purpose` enum('DELIVERY','FOLLOW_UP','PICKUP','SERVICE') DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_tv_staff_status` (`staff_id`,`status`),
  KEY `idx_tv_job` (`service_job_id`),
  KEY `FKs9921f30jbfw93d1r0ek704ku` (`customer_id`),
  CONSTRAINT `FK3ynxw73vtd3grg4yj25vj8xvs` FOREIGN KEY (`staff_id`) REFERENCES `staff` (`id`),
  CONSTRAINT `FKaf02sad2xgvb45iufa2n22tsi` FOREIGN KEY (`service_job_id`) REFERENCES `service_jobs` (`id`),
  CONSTRAINT `FKs9921f30jbfw93d1r0ek704ku` FOREIGN KEY (`customer_id`) REFERENCES `customer` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `units` (
  `id` int NOT NULL AUTO_INCREMENT,
  `description` varchar(100) DEFAULT NULL,
  `unit_name` varchar(50) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UKfwgaum8cj6wofnc1wpwdyplhm` (`description`),
  UNIQUE KEY `UK525csmemmgtoicjcfhcpf3pk0` (`unit_name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `users` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `auth_provider` varchar(20) DEFAULT NULL,
  `created_at` datetime(6) DEFAULT NULL,
  `email` varchar(100) NOT NULL,
  `is_active` bit(1) DEFAULT NULL,
  `password_hash` varchar(255) DEFAULT NULL,
  `provider_id` varchar(255) DEFAULT NULL,
  `username` varchar(50) DEFAULT NULL,
  `token_version` int NOT NULL,
  `name` varchar(100) DEFAULT NULL,
  `phone` varchar(30) DEFAULT NULL,
  `staff_id` int DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UK6dotkott2kjsp8vw4d0m25fb7` (`email`),
  KEY `FKbglg01rrxvu2s48rjyopyexkj` (`staff_id`),
  CONSTRAINT `FKbglg01rrxvu2s48rjyopyexkj` FOREIGN KEY (`staff_id`) REFERENCES `staff` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `users_roles` (
  `user_id` bigint NOT NULL,
  `role_id` int NOT NULL,
  PRIMARY KEY (`user_id`,`role_id`),
  KEY `FKj6m8fwv7oqv74fcehir1a9ffy` (`role_id`),
  CONSTRAINT `FK2o0jvgh89lemvvo17cbqvdxaa` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`),
  CONSTRAINT `FKj6m8fwv7oqv74fcehir1a9ffy` FOREIGN KEY (`role_id`) REFERENCES `roles` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `voucher_settings` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `cont_header_height_px` int DEFAULT NULL,
  `customer_notice` varchar(1000) DEFAULT NULL,
  `document_type` enum('BOOKING','PURCHASE','SALE','SERVICE_DONE','SERVICE_JOB') NOT NULL,
  `footer_height_px` int DEFAULT NULL,
  `footer_note` varchar(500) DEFAULT NULL,
  `header_height_px` int DEFAULT NULL,
  `info_blocks_height_px` int DEFAULT NULL,
  `margin_bottom_mm` int DEFAULT NULL,
  `margin_left_mm` int DEFAULT NULL,
  `margin_right_mm` int DEFAULT NULL,
  `margin_top_mm` int DEFAULT NULL,
  `paper_size` varchar(10) NOT NULL,
  `row_height_px` int DEFAULT NULL,
  `safety_margin_px` int DEFAULT NULL,
  `show_logo` bit(1) DEFAULT NULL,
  `show_payment_history` bit(1) DEFAULT NULL,
  `show_qr_code` bit(1) DEFAULT NULL,
  `show_serial` bit(1) DEFAULT NULL,
  `show_signatures` bit(1) DEFAULT NULL,
  `sign1_label` varchar(60) DEFAULT NULL,
  `sign2_label` varchar(60) DEFAULT NULL,
  `table_header_height_px` int DEFAULT NULL,
  `totals_area_height_px` int DEFAULT NULL,
  `updated_at` datetime(6) DEFAULT NULL,
  `updated_by` varchar(80) DEFAULT NULL,
  `voucher_title` varchar(80) DEFAULT NULL,
  `footer_font_family` varchar(100) DEFAULT NULL,
  `footer_font_size_px` int DEFAULT NULL,
  `header_font_family` varchar(100) DEFAULT NULL,
  `header_font_size_px` int DEFAULT NULL,
  `info_font_family` varchar(100) DEFAULT NULL,
  `info_font_size_px` int DEFAULT NULL,
  `notice_font_family` varchar(100) DEFAULT NULL,
  `notice_font_size_px` int DEFAULT NULL,
  `table_data_font_family` varchar(100) DEFAULT NULL,
  `table_data_font_size_px` int DEFAULT NULL,
  `table_header_font_family` varchar(100) DEFAULT NULL,
  `table_header_font_size_px` int DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_voucher_setting_type` (`document_type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `warehouse_transfers` (
  `id` int NOT NULL AUTO_INCREMENT,
  `qty` int NOT NULL,
  `remark` varchar(255) DEFAULT NULL,
  `transfer_no` varchar(40) NOT NULL,
  `transferred_at` datetime(6) DEFAULT NULL,
  `transferred_by` varchar(255) DEFAULT NULL,
  `from_warehouse_id` int NOT NULL,
  `product_id` int NOT NULL,
  `to_warehouse_id` int NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UKyx8acoogkcnjqqycioukoj3j` (`transfer_no`),
  KEY `idx_wt_product` (`product_id`),
  KEY `idx_wt_from` (`from_warehouse_id`),
  KEY `idx_wt_to` (`to_warehouse_id`),
  CONSTRAINT `FKhm0qkvrb76qr9jw0130oo2pa` FOREIGN KEY (`to_warehouse_id`) REFERENCES `warehouses` (`id`),
  CONSTRAINT `FKir71o1av8ec8bvqanvy1jygwp` FOREIGN KEY (`from_warehouse_id`) REFERENCES `warehouses` (`id`),
  CONSTRAINT `FKoc0gah64igrl47up9lrgk9kh2` FOREIGN KEY (`product_id`) REFERENCES `products` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `warehouses` (
  `id` int NOT NULL AUTO_INCREMENT,
  `active` bit(1) NOT NULL,
  `address` varchar(255) DEFAULT NULL,
  `code` varchar(40) NOT NULL,
  `name` varchar(120) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UK6herdbg4x5wp6gkor8epv73oc` (`code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40103 SET TIME_ZONE=@OLD_TIME_ZONE */;

/*!40101 SET SQL_MODE=@OLD_SQL_MODE */;
/*!40014 SET FOREIGN_KEY_CHECKS=@OLD_FOREIGN_KEY_CHECKS */;
/*!40014 SET UNIQUE_CHECKS=@OLD_UNIQUE_CHECKS */;
/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
/*!40111 SET SQL_NOTES=@OLD_SQL_NOTES */;

