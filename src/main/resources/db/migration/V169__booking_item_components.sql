CREATE TABLE booking_item_components (
    id INT NOT NULL AUTO_INCREMENT,
    booking_item_id INT NOT NULL,
    component_type VARCHAR(40) NOT NULL,
    brand VARCHAR(120) NULL,
    model VARCHAR(160) NULL,
    specification VARCHAR(255) NULL,
    serial_no VARCHAR(160) NULL,
    quantity INT NOT NULL DEFAULT 1,
    condition_note TEXT NULL,
    PRIMARY KEY (id),
    KEY idx_booking_item_components_item (booking_item_id),
    KEY idx_booking_item_components_type (component_type),
    CONSTRAINT fk_booking_item_components_item FOREIGN KEY (booking_item_id)
        REFERENCES booking_items (id) ON DELETE CASCADE,
    CONSTRAINT chk_booking_item_components_qty CHECK (quantity > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
