-- Bolt OTP Checkout - MySQL schema
--
-- This file is the authoritative database definition for the application.
-- Spring Boot runs with spring.jpa.hibernate.ddl-auto=validate, so the JPA
-- entities are checked against this schema on startup and any drift fails fast.
--
-- Executable on a clean MySQL 8 database (verified on MySQL 8.0).
--
-- Indexes are declared inline inside CREATE TABLE rather than as separate
-- CREATE INDEX statements. MySQL has no `CREATE INDEX IF NOT EXISTS`, so
-- separate statements are not idempotent and a second run would fail. Keeping
-- them inline means the `IF NOT EXISTS` guard covers the indexes too.

-- ---------------------------------------------------------------------------
-- users
-- otp_hash stores a BCrypt hash. The plain 6-digit code is never persisted.
--
-- The UNIQUE key on email is also the index used by the recognition endpoint,
-- so no additional index on email is needed.
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS users (
    id BIGINT NOT NULL AUTO_INCREMENT,
    email VARCHAR(255) NOT NULL,
    first_name VARCHAR(100) NOT NULL,
    last_name VARCHAR(100) NOT NULL,
    otp_hash VARCHAR(255) NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    CONSTRAINT uq_users_email UNIQUE (email)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

-- ---------------------------------------------------------------------------
-- checkout_records
-- user_id is nullable for guest checkout. ON DELETE SET NULL preserves the
-- historical order record if the user account is ever removed.
--
-- idx_checkout_email serves order-history lookups; idx_checkout_user_id backs
-- the foreign key and "all orders for this user" queries.
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS checkout_records (
    id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NULL,
    email VARCHAR(255) NOT NULL,
    phone VARCHAR(50) NOT NULL,
    shipping_address VARCHAR(1000) NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    INDEX idx_checkout_email (email),
    INDEX idx_checkout_user_id (user_id),
    CONSTRAINT fk_checkout_user
        FOREIGN KEY (user_id)
        REFERENCES users (id)
        ON DELETE SET NULL
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;
