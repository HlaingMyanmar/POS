-- Store product photos as WebP files and keep only their paths in MySQL.
ALTER TABLE products
    ADD COLUMN image_path VARCHAR(500) NULL,
    ADD COLUMN thumbnail_path VARCHAR(500) NULL,
    ADD COLUMN image_mime_type VARCHAR(100) NULL,
    ADD COLUMN original_file_name VARCHAR(255) NULL,
    ADD COLUMN image_width INT NULL,
    ADD COLUMN image_height INT NULL;
