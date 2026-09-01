CREATE TABLE `video_app_placements` (
  `id` int NOT NULL AUTO_INCREMENT,
  `video_id` int NOT NULL,
  `app_type` varchar(32) NOT NULL,
  `sort_order` int NOT NULL DEFAULT 1,
  `featured` bit(1) NOT NULL DEFAULT b'0',
  `active` bit(1) NOT NULL DEFAULT b'1',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_video_app_placement` (`video_id`, `app_type`),
  KEY `idx_video_placement_app_order` (`app_type`, `featured`, `sort_order`),
  CONSTRAINT `fk_video_app_placement_video` FOREIGN KEY (`video_id`) REFERENCES `videos` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

INSERT INTO `video_app_placements` (`video_id`, `app_type`, `sort_order`, `featured`, `active`)
SELECT `id`, 'TECHNICIAN', `sort_order`, b'0', b'1'
FROM `videos`
WHERE `target_audience` IN ('TECHNICIAN', 'BOTH');

INSERT INTO `video_app_placements` (`video_id`, `app_type`, `sort_order`, `featured`, `active`)
SELECT `id`, 'CLIENT', `sort_order`, b'0', b'1'
FROM `videos`
WHERE `target_audience` IN ('CLIENT', 'BOTH');
