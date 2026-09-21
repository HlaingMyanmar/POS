-- Snapshot of customer-visible catalog service charge at booking time.
ALTER TABLE bookings
    ADD COLUMN requested_service_id INT NULL AFTER requested_service_name,
    ADD COLUMN service_price_snapshot DECIMAL(15, 2) NULL AFTER requested_service_id,
    ADD COLUMN service_price_type VARCHAR(30) NULL AFTER service_price_snapshot,
    ADD COLUMN estimate_approval_status VARCHAR(30) NULL AFTER service_price_type,
    ADD KEY idx_bookings_requested_service (requested_service_id),
    ADD CONSTRAINT fk_bookings_requested_service
        FOREIGN KEY (requested_service_id) REFERENCES services (id)
        ON DELETE SET NULL;
