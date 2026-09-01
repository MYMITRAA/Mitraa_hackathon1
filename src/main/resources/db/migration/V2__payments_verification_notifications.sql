ALTER TABLE registrations ADD COLUMN guardian_consent_required BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE registrations ADD COLUMN guardian_consent_received BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE registrations ADD COLUMN age_verification_status VARCHAR(30) NOT NULL DEFAULT 'NOT_STARTED';
ALTER TABLE registrations ADD COLUMN verification_applicant_id VARCHAR(120);
CREATE TABLE payments (id BIGINT AUTO_INCREMENT PRIMARY KEY, registration_id BIGINT NOT NULL, amount_minor BIGINT NOT NULL, currency VARCHAR(3) NOT NULL, gateway_order_id VARCHAR(80) NOT NULL, gateway_payment_id VARCHAR(80), status VARCHAR(20) NOT NULL, created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6), CONSTRAINT uk_payment_order UNIQUE (gateway_order_id), CONSTRAINT fk_payment_registration FOREIGN KEY (registration_id) REFERENCES registrations(id)) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE INDEX idx_payments_status ON payments(status);
CREATE TABLE notification_outbox (id BIGINT AUTO_INCREMENT PRIMARY KEY, channel VARCHAR(10) NOT NULL, recipient VARCHAR(190) NOT NULL, subject VARCHAR(160) NOT NULL, body VARCHAR(4000) NOT NULL, event_type VARCHAR(60) NOT NULL, reference_id VARCHAR(80) NOT NULL, status VARCHAR(20) NOT NULL, attempts INTEGER NOT NULL DEFAULT 0, next_attempt_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6), created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6), last_error VARCHAR(500)) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE INDEX idx_notification_status ON notification_outbox(status,next_attempt_at);
