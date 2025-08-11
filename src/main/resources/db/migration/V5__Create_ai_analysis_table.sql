-- AI analysis per user (exactly one row per user)
CREATE TABLE IF NOT EXISTS ai_analysis
(
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id     BIGINT        NOT NULL,
    average     DECIMAL(3, 1) NOT NULL,
    summary     TEXT          NOT NULL,
    suggestions JSON          NOT NULL,
    created_at  TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_ai_analysis_user
        FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,

    CONSTRAINT chk_ai_average CHECK (average >= 0.0 AND average <= 5.0),
    CONSTRAINT chk_ai_suggestions_json CHECK (JSON_VALID(suggestions)),

    -- key that enforces only ONE record per user
    UNIQUE KEY uniq_ai_user (user_id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;
