CREATE TABLE IF NOT EXISTS mood_entries
(
    id         BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id    BIGINT  NOT NULL,
    entry_date DATE    NOT NULL,
    mood_score TINYINT NOT NULL,
    note       VARCHAR(1000),

    CONSTRAINT fk_mood_user
        FOREIGN KEY (user_id) REFERENCES users (id)
            ON DELETE CASCADE,

    CONSTRAINT uq_user_date UNIQUE (user_id, entry_date)
);
