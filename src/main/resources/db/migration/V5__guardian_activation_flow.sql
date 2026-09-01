ALTER TABLE registrations ADD COLUMN guardian_name VARCHAR(120) NULL;
ALTER TABLE registrations ADD COLUMN guardian_email VARCHAR(190) NULL;
ALTER TABLE registrations ADD COLUMN activated_at TIMESTAMP(6) NULL;
CREATE TABLE guardian_consents (
 id BIGINT AUTO_INCREMENT PRIMARY KEY,
 registration_id BIGINT NOT NULL,
 code_hash VARCHAR(255) NOT NULL,
 expires_at TIMESTAMP(6) NOT NULL,
 attempts INTEGER NOT NULL DEFAULT 0,
 consumed_at TIMESTAMP(6) NULL,
 created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
 CONSTRAINT fk_guardian_registration FOREIGN KEY (registration_id) REFERENCES registrations(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE INDEX idx_guardian_consent_lookup ON guardian_consents(registration_id,consumed_at,created_at);
