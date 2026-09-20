-- Hibernate validates VARCHAR, not CHAR, for token_hash (SHA-256 hex).
-- Safe if V164 already created CHAR(64) on an earlier dry-run / IT database.
ALTER TABLE refresh_sessions
    MODIFY COLUMN token_hash VARCHAR(64) NOT NULL;
