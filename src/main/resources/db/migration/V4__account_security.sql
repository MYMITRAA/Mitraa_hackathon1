ALTER TABLE users ADD COLUMN email_verified_at TIMESTAMP(6) NULL;
CREATE TABLE account_codes (
 id BIGINT AUTO_INCREMENT PRIMARY KEY, user_id BIGINT NOT NULL, purpose VARCHAR(30) NOT NULL,
 code_hash VARCHAR(255) NOT NULL, expires_at TIMESTAMP(6) NOT NULL, consumed_at TIMESTAMP(6) NULL,
 attempts INTEGER NOT NULL DEFAULT 0, created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
 CONSTRAINT fk_account_code_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE INDEX idx_account_codes_lookup ON account_codes(user_id,purpose,expires_at);
