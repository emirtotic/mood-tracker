CREATE TABLE IF NOT EXISTS auth_tokens
(
    id         BIGINT AUTO_INCREMENT PRIMARY KEY,
    token_hash VARCHAR(64) NOT NULL UNIQUE,
    jti        VARCHAR(36) NOT NULL UNIQUE,
    user_id    BIGINT      NOT NULL,
    expires_at DATETIME(6) NOT NULL,
    revoked    BOOLEAN     NOT NULL DEFAULT FALSE,
    CONSTRAINT fk_auth_tokens_user
        FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    INDEX idx_auth_tokens_expires_at (expires_at),
    INDEX idx_auth_tokens_user_id (user_id)
);
