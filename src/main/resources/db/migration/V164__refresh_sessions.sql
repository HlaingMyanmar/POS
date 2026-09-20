CREATE TABLE refresh_sessions (
    id              BIGINT       NOT NULL AUTO_INCREMENT,
    user_id         BIGINT       NOT NULL,
    jti             VARCHAR(36)  NOT NULL,
    token_hash      CHAR(64)     NOT NULL,
    family_id       VARCHAR(36)  NOT NULL,
    expires_at      DATETIME(6)  NOT NULL,
    revoked_at      DATETIME(6)  NULL,
    replaced_by_jti VARCHAR(36)  NULL,
    created_at      DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_refresh_sessions_jti UNIQUE (jti),
    CONSTRAINT uk_refresh_sessions_token_hash UNIQUE (token_hash),
    CONSTRAINT fk_refresh_sessions_user
        FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    INDEX idx_refresh_sessions_user_id (user_id),
    INDEX idx_refresh_sessions_family_id (family_id),
    INDEX idx_refresh_sessions_expires_at (expires_at)
);
