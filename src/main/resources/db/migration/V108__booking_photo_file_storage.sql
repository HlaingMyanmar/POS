-- Store new booking photos as WebP files and keep only their paths in MySQL.
ALTER TABLE booking_item_photos
    MODIFY data_url LONGTEXT NULL,
    ADD COLUMN image_path VARCHAR(500) NULL,
    ADD COLUMN thumbnail_path VARCHAR(500) NULL;