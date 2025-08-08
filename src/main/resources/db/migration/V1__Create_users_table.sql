CREATE TABLE IF NOT EXISTS users
(
    id         BIGINT AUTO_INCREMENT PRIMARY KEY,
    first_name VARCHAR(100),
    last_name  VARCHAR(100),
    email      VARCHAR(255)          NOT NULL UNIQUE,
    password   VARCHAR(255),
    enabled    BOOLEAN               DEFAULT TRUE,
    role       ENUM ('USER', 'ADMIN') NOT NULL DEFAULT 'USER'
);
