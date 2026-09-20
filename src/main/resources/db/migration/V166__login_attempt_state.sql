CREATE TABLE login_attempt_state (
    id               BIGINT       NOT NULL AUTO_INCREMENT,
    login_key        VARCHAR(100) NOT NULL,
    failed_attempts  INT          NOT NULL DEFAULT 0,
    locked_until     DATETIME(6)  NULL,
    updated_at       DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_login_attempt_state_key UNIQUE (login_key),
    INDEX idx_login_attempt_state_locked_until (locked_until)
);
