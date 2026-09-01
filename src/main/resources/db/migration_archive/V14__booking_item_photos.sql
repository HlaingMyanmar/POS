CREATE TABLE booking_item_photos (
    id INT NOT NULL AUTO_INCREMENT,
    booking_item_id INT NOT NULL,
    slot INT NOT NULL,
    file_name VARCHAR(255) NULL,
    content_type VARCHAR(120) NULL,
    data_url LONGTEXT NOT NULL,
    uploaded_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_booking_item_photo_slot (booking_item_id, slot),
    KEY idx_booking_item_photos_item (booking_item_id),
    CONSTRAINT fk_booking_item_photos_item FOREIGN KEY (booking_item_id)
        REFERENCES booking_items (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
