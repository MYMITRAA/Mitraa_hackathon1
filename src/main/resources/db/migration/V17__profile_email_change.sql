ALTER TABLE users
    ADD COLUMN pending_email VARCHAR(190) NULL AFTER email,
    ADD COLUMN pending_email_requested_at TIMESTAMP(6) NULL AFTER pending_email;

CREATE INDEX ix_users_pending_email ON users (pending_email);
