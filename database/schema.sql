-- Bolt OTP Checkout - PostgreSQL Schema
--
-- This file is the authoritative database definition for the application.
-- Spring Boot runs with spring.jpa.hibernate.ddl-auto=validate, so the JPA
-- entities are checked against this schema on startup and any drift fails fast.
--
-- Executable on a clean PostgreSQL database (verified on PostgreSQL 16).

-- ---------------------------------------------------------------------------
-- users
-- otp_hash stores a BCrypt hash. The plain 6-digit code is never persisted.
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS users (
    id BIGSERIAL PRIMARY KEY,
    email VARCHAR(255) NOT NULL,
    first_name VARCHAR(100) NOT NULL,
    last_name VARCHAR(100) NOT NULL,
    otp_hash VARCHAR(255) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_users_email UNIQUE (email)
);

-- Recognition lookups hit users by email on every debounced keystroke.
CREATE INDEX IF NOT EXISTS idx_users_email ON users (email);

-- ---------------------------------------------------------------------------
-- checkout_records
-- user_id is nullable for guest checkout. ON DELETE SET NULL preserves the
-- historical order record if the user account is ever removed.
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS checkout_records (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT,
    email VARCHAR(255) NOT NULL,
    phone VARCHAR(50) NOT NULL,
    shipping_address TEXT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_checkout_user
        FOREIGN KEY (user_id)
        REFERENCES users (id)
        ON DELETE SET NULL
);

CREATE INDEX IF NOT EXISTS idx_checkout_email ON checkout_records (email);
CREATE INDEX IF NOT EXISTS idx_checkout_user_id ON checkout_records (user_id);
