-- Remove legacy booking module tables. Service jobs keep booking_id column for future rewrite.

DROP TABLE IF EXISTS booking_attachments;
DROP TABLE IF EXISTS booking_details;
DROP TABLE IF EXISTS booking_device_infos;
DROP TABLE IF EXISTS booking_devices;
DROP TABLE IF EXISTS bookings;
