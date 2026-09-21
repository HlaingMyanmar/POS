-- Phase 1: booking availability settings + outdoor window columns (additive only).

-- A. Booking rules on singleton settings (no default_booking_capacity)
ALTER TABLE service_booking_settings
    ADD COLUMN max_advance_booking_days INT         NOT NULL DEFAULT 7
        AFTER outdoor_booking_disabled_reason,
    ADD COLUMN min_notice_hours         INT         NOT NULL DEFAULT 2
        AFTER max_advance_booking_days,
    ADD COLUMN allow_same_day_booking   TINYINT(1)  NOT NULL DEFAULT 1
        AFTER min_notice_hours,
    ADD COLUMN allow_emergency_request  TINYINT(1)  NOT NULL DEFAULT 1
        AFTER allow_same_day_booking;

ALTER TABLE service_booking_settings
    ADD CONSTRAINT chk_sbs_advance_days CHECK (max_advance_booking_days >= 0 AND max_advance_booking_days <= 90),
    ADD CONSTRAINT chk_sbs_notice_hours CHECK (min_notice_hours >= 0 AND min_notice_hours <= 168);

-- B. Multiple open periods per weekday (no UNIQUE on day_of_week)
CREATE TABLE service_booking_weekday_hours (
    id              INT            NOT NULL AUTO_INCREMENT,
    day_of_week     VARCHAR(10)    NOT NULL,
    start_time      TIME           NOT NULL,
    end_time        TIME           NOT NULL,
    active          TINYINT(1)     NOT NULL DEFAULT 1,
    display_order   INT            NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    KEY idx_sbwh_day_order (day_of_week, display_order),
    CONSTRAINT chk_sbwh_time CHECK (start_time < end_time)
);

-- Seed Mon–Sat 09:00–18:00; Sunday has no rows (closed)
INSERT INTO service_booking_weekday_hours (day_of_week, start_time, end_time, active, display_order) VALUES
    ('MONDAY',    '09:00:00', '18:00:00', 1, 1),
    ('TUESDAY',   '09:00:00', '18:00:00', 1, 1),
    ('WEDNESDAY', '09:00:00', '18:00:00', 1, 1),
    ('THURSDAY',  '09:00:00', '18:00:00', 1, 1),
    ('FRIDAY',    '09:00:00', '18:00:00', 1, 1),
    ('SATURDAY',  '09:00:00', '18:00:00', 1, 1);

-- C. Outdoor arrival windows (capacity always explicit, customer-request capacity)
CREATE TABLE service_booking_arrival_windows (
    id              INT            NOT NULL AUTO_INCREMENT,
    name            VARCHAR(80)    NOT NULL,
    start_time      TIME           NOT NULL,
    end_time        TIME           NOT NULL,
    max_capacity    INT            NOT NULL,
    active          TINYINT(1)     NOT NULL DEFAULT 1,
    display_order   INT            NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    KEY idx_sbaw_active_order (active, display_order),
    CONSTRAINT chk_sbaw_time CHECK (start_time < end_time),
    CONSTRAINT chk_sbaw_capacity CHECK (max_capacity >= 1)
);

INSERT INTO service_booking_arrival_windows (name, start_time, end_time, max_capacity, active, display_order) VALUES
    ('Morning',   '09:00:00', '12:00:00', 2, 1, 1),
    ('Afternoon', '13:00:00', '16:00:00', 2, 1, 2),
    ('Evening',   '16:00:00', '18:00:00', 1, 1, 3);

-- D. Date-specific exceptions (one row per calendar date)
CREATE TABLE service_booking_date_exceptions (
    id               INT            NOT NULL AUTO_INCREMENT,
    exception_date   DATE           NOT NULL,
    closed           TINYINT(1)     NOT NULL DEFAULT 0,
    opens_at         TIME           NULL,
    closes_at        TIME           NULL,
    reason           VARCHAR(500)   NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_sbde_date (exception_date),
    CONSTRAINT chk_sbde_hours CHECK (
        (closed = 1 AND opens_at IS NULL AND closes_at IS NULL)
        OR (closed = 0 AND ((opens_at IS NULL AND closes_at IS NULL) OR (opens_at < closes_at)))
    )
);

-- E. Disable specific arrival windows on an exception date
-- Deleting exception cascades join rows only; does not delete global windows.
CREATE TABLE service_booking_date_exception_windows (
    exception_id       INT NOT NULL,
    arrival_window_id  INT NOT NULL,
    PRIMARY KEY (exception_id, arrival_window_id),
    CONSTRAINT fk_sbdew_exception
        FOREIGN KEY (exception_id) REFERENCES service_booking_date_exceptions (id)
        ON DELETE CASCADE,
    CONSTRAINT fk_sbdew_window
        FOREIGN KEY (arrival_window_id) REFERENCES service_booking_arrival_windows (id)
        ON DELETE RESTRICT
);

-- F. Booking columns for outdoor window (nullable = backward compatible)
-- RESTRICT window delete when bookings still reference it (prefer active=false).
ALTER TABLE bookings
    ADD COLUMN service_date              DATE         NULL AFTER booking_datetime,
    ADD COLUMN arrival_window_id         INT          NULL AFTER service_date,
    ADD COLUMN preferred_time            TIME         NULL AFTER arrival_window_id,
    ADD COLUMN preferred_anytime         TINYINT(1)   NOT NULL DEFAULT 1 AFTER preferred_time,
    ADD COLUMN customer_preference_note  TEXT         NULL AFTER preferred_anytime,
    ADD KEY idx_bookings_service_window_status (service_date, arrival_window_id, status),
    ADD CONSTRAINT fk_bookings_arrival_window
        FOREIGN KEY (arrival_window_id) REFERENCES service_booking_arrival_windows (id)
        ON DELETE RESTRICT;
