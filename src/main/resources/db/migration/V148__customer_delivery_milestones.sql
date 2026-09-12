CREATE TABLE customer_delivery_milestones (
    id INT NOT NULL AUTO_INCREMENT,
    order_id INT NOT NULL,
    from_status VARCHAR(30) NULL,
    to_status VARCHAR(30) NOT NULL,
    actor VARCHAR(120) NOT NULL,
    actor_type VARCHAR(20) NOT NULL,
    note VARCHAR(500) NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    KEY idx_cdm_order (order_id, id),
    CONSTRAINT fk_cdm_order FOREIGN KEY (order_id) REFERENCES customer_orders (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
