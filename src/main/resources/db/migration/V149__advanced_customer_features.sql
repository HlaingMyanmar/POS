ALTER TABLE customer
    ADD COLUMN loyalty_points INT NOT NULL DEFAULT 0,
    ADD COLUMN loyalty_total_earned INT NOT NULL DEFAULT 0;

ALTER TABLE customer_orders
    ADD COLUMN loyalty_awarded BIT(1) NOT NULL DEFAULT b'0';

CREATE TABLE IF NOT EXISTS chat_messages (
    id BIGINT NOT NULL AUTO_INCREMENT,
    sender_username VARCHAR(50) NOT NULL,
    sender_name VARCHAR(100) NULL,
    sender_role VARCHAR(50) NULL,
    content TEXT NOT NULL,
    sent_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    KEY idx_chat_sent_at (sent_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

ALTER TABLE chat_messages
    ADD COLUMN customer_id INT NULL,
    ADD KEY idx_chat_customer_sent (customer_id, sent_at),
    ADD CONSTRAINT fk_chat_customer FOREIGN KEY (customer_id) REFERENCES customer (id);
