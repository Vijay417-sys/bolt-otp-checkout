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
-- otp_expires_at     codes are short-lived; verification fails after this instant.
-- otp_failed_attempts  consecutive failures; reset to 0 on a successful login.
-- otp_locked_until    set once otp_failed_attempts reaches the limit; cleared on
--                     a successful login or when the lock window has passed.
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
    otp_expires_at DATETIME NOT NULL,
    otp_failed_attempts INT NOT NULL DEFAULT 0,
    otp_locked_until DATETIME NULL,
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

-- ---------------------------------------------------------------------------
-- audit_logs
-- Append-only record of security-relevant events: registration, OTP
-- verification attempts, and checkouts.
--
-- Deliberately NOT a foreign key to users. Audit history must survive the
-- deletion of the account it describes, so user_id is a loose reference that
-- is nulled rather than cascaded.
--
-- ip_address is VARCHAR(45) to hold an IPv6 address.
-- detail is deliberately short: audit rows must never contain the OTP, a
-- password hash, or a full session token.
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS audit_logs (
    id BIGINT NOT NULL AUTO_INCREMENT,
    event VARCHAR(64) NOT NULL,
    outcome VARCHAR(32) NOT NULL,
    email VARCHAR(255) NULL,
    user_id BIGINT NULL,
    detail VARCHAR(500) NULL,
    ip_address VARCHAR(45) NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    INDEX idx_audit_created_at (created_at),
    INDEX idx_audit_email (email),
    INDEX idx_audit_event (event)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

-- ---------------------------------------------------------------------------
-- Upgrading an existing database
--
-- The CREATE TABLE statements above use IF NOT EXISTS, so re-running this file
-- against a database created before the audit/OTP-hardening columns were added
-- is a no-op rather than an error - which means those columns would silently
-- be missing. `ddl-auto=validate` then refuses to start the backend, so the
-- failure is loud rather than silent, but the ALTERs still have to be applied:
--
--   ALTER TABLE users
--       ADD COLUMN otp_expires_at DATETIME NULL AFTER otp_hash,
--       ADD COLUMN otp_failed_attempts INT NOT NULL DEFAULT 0 AFTER otp_expires_at,
--       ADD COLUMN otp_locked_until DATETIME NULL AFTER otp_failed_attempts;
--
--   UPDATE users SET otp_expires_at = CURRENT_TIMESTAMP WHERE otp_expires_at IS NULL;
--
--   ALTER TABLE users MODIFY COLUMN otp_expires_at DATETIME NOT NULL;
--
-- A fresh `CREATE DATABASE bolt_checkout` needs none of this. Once more than one
-- environment exists, replace this file with Flyway or Liquibase migrations -
-- see "Production Improvements" in the README.
-- ---------------------------------------------------------------------------
