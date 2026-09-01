CREATE TABLE `videos` (
  `id` int NOT NULL AUTO_INCREMENT,
  `title` varchar(255) NOT NULL,
  `description` varchar(2000) DEFAULT NULL,
  `provider` varchar(32) NOT NULL DEFAULT 'YOUTUBE',
  `provider_video_id` varchar(64) NOT NULL,
  `source_url` varchar(500) NOT NULL,
  `thumbnail_url` varchar(500) DEFAULT NULL,
  `category` varchar(100) DEFAULT NULL,
  `target_audience` varchar(32) NOT NULL,
  `sort_order` int NOT NULL DEFAULT 0,
  `active` bit(1) NOT NULL DEFAULT b'1',
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_videos_audience_active` (`target_audience`, `active`),
  KEY `idx_videos_sort` (`sort_order`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
