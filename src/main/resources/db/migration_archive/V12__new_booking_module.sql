-- New slim booking intake module. The legacy booking tables were removed by V11.
CREATE TABLE bookings (
    id INT NOT NULL AUTO_INCREMENT,
    booking_no VARCHAR(20) NOT NULL,
    customer_id INT NOT NULL,
    booking_date DATE NOT NULL,
    booking_datetime DATETIME(6) NULL,
    complaint_note TEXT NULL,
    status VARCHAR(20) NOT NULL,
    remark TEXT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_bookings_booking_no (booking_no),
    KEY idx_bookings_customer (customer_id),
    KEY idx_bookings_date (booking_date),
    KEY idx_bookings_status (status),
    CONSTRAINT fk_bookings_customer FOREIGN KEY (customer_id) REFERENCES customer (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE booking_items (
    id INT NOT NULL AUTO_INCREMENT,
    booking_id INT NOT NULL,
    item_name VARCHAR(200) NOT NULL,
    device_type VARCHAR(80) NULL,
    serial_no VARCHAR(120) NULL,
    color VARCHAR(80) NULL,
    accessories TEXT NULL,
    problem_desc TEXT NULL,
    item_condition TEXT NULL,
    converted_job_id INT NULL,
    PRIMARY KEY (id),
    KEY idx_booking_items_booking (booking_id),
    KEY idx_booking_items_converted_job (converted_job_id),
    CONSTRAINT fk_booking_items_booking FOREIGN KEY (booking_id)
        REFERENCES bookings (id) ON DELETE CASCADE,
    CONSTRAINT fk_booking_items_job FOREIGN KEY (converted_job_id)
        REFERENCES service_jobs (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX idx_service_jobs_booking ON service_jobs (booking_id);
