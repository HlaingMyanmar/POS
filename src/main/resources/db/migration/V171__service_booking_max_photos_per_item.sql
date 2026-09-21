-- Configurable max device photos per booking item (staff intake).
ALTER TABLE service_booking_settings
    ADD COLUMN max_photos_per_item INT NOT NULL DEFAULT 50
        AFTER outdoor_transportation_fee;

UPDATE service_booking_settings
SET max_photos_per_item = 50
WHERE max_photos_per_item IS NULL OR max_photos_per_item < 1;
