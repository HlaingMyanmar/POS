ALTER TABLE cash_drawer_movements
    ADD COLUMN reference_type VARCHAR(40) NULL,
    ADD COLUMN reference_id INT NULL,
    ADD COLUMN reversed TINYINT(1) NOT NULL DEFAULT 0;

CREATE INDEX idx_drawer_movement_ref
    ON cash_drawer_movements (type, reference_type, reference_id, reversed);
