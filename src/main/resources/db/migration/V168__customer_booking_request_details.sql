ALTER TABLE bookings
    ADD COLUMN requested_service_name VARCHAR(200) NULL,
    ADD COLUMN request_type VARCHAR(30) NULL,
    ADD COLUMN device_category VARCHAR(80) NULL,
    ADD COLUMN device_name VARCHAR(200) NULL,
    ADD COLUMN requested_service_mode VARCHAR(20) NULL,
    ADD COLUMN service_address TEXT NULL,
    ADD COLUMN urgency VARCHAR(20) NULL,
    ADD COLUMN contact_preference VARCHAR(20) NULL;

CREATE TABLE booking_request_photos (
    id INT NOT NULL AUTO_INCREMENT,
    booking_id INT NOT NULL,
    slot INT NOT NULL,
    file_name VARCHAR(255) NULL,
    content_type VARCHAR(120) NULL,
    image_path VARCHAR(500) NOT NULL,
    thumbnail_path VARCHAR(500) NOT NULL,
    uploaded_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_booking_request_photo_slot (booking_id, slot),
    KEY idx_booking_request_photos_booking (booking_id),
    CONSTRAINT fk_booking_request_photos_booking FOREIGN KEY (booking_id)
        REFERENCES bookings (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
