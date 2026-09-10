-- ============================================================
-- schema_update.sql
-- Run this against your online_banking database BEFORE
-- starting the application if you have an existing schema.
-- It enlarges the password column to hold BCrypt hashes
-- (60 characters) and ensures all required tables exist.
-- ============================================================

-- 1. Enlarge the password column to store BCrypt hashes (60 chars)
ALTER TABLE users
  MODIFY COLUMN password VARCHAR(60) NOT NULL;

-- 2. (Optional) Create tables from scratch if they don't exist yet
CREATE TABLE IF NOT EXISTS users (
    user_id        INT AUTO_INCREMENT PRIMARY KEY,
    username       VARCHAR(50)  NOT NULL UNIQUE,
    password       VARCHAR(60)  NOT NULL,          -- BCrypt hash
    account_number VARCHAR(20)  NOT NULL,
    balance        DOUBLE       NOT NULL DEFAULT 0.0
);

CREATE TABLE IF NOT EXISTS transactions (
    tx_id            INT AUTO_INCREMENT PRIMARY KEY,
    user_id          INT          NOT NULL,
    amount           DOUBLE       NOT NULL,
    transaction_type ENUM('deposit', 'withdrawal') NOT NULL,
    created_at       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_tx_user FOREIGN KEY (user_id) REFERENCES users(user_id)
);
