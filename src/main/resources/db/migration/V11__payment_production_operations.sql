ALTER TABLE payments
    ADD COLUMN captured_at TIMESTAMP(6) NULL,
    ADD COLUMN updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    ADD COLUMN failure_code VARCHAR(80) NULL,
    ADD COLUMN failure_description VARCHAR(500) NULL,
    ADD COLUMN retry_count INT NOT NULL DEFAULT 0,
    ADD COLUMN last_retry_at TIMESTAMP(6) NULL,
    ADD COLUMN refunded_amount_minor BIGINT NOT NULL DEFAULT 0,
    ADD COLUMN reconciliation_status VARCHAR(30) NOT NULL DEFAULT 'PENDING',
    ADD COLUMN reconciled_at TIMESTAMP(6) NULL,
    ADD COLUMN reconciliation_note VARCHAR(500) NULL;

UPDATE payments SET captured_at = paid_at WHERE status = 'PAID' AND captured_at IS NULL;

CREATE TABLE payment_webhook_events (
    id BIGINT NOT NULL AUTO_INCREMENT,
    event_key VARCHAR(160) NOT NULL,
    gateway_event_id VARCHAR(120) NULL,
    event_type VARCHAR(80) NOT NULL,
    payload_sha256 VARCHAR(64) NOT NULL,
    status VARCHAR(20) NOT NULL,
    processing_error VARCHAR(500) NULL,
    received_at TIMESTAMP(6) NOT NULL,
    processed_at TIMESTAMP(6) NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_payment_webhook_event_key UNIQUE (event_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE INDEX idx_payment_webhook_status ON payment_webhook_events(status, received_at);

CREATE TABLE payment_refunds (
    id BIGINT NOT NULL AUTO_INCREMENT,
    payment_id BIGINT NOT NULL,
    gateway_refund_id VARCHAR(80) NOT NULL,
    amount_minor BIGINT NOT NULL,
    currency VARCHAR(3) NOT NULL,
    status VARCHAR(20) NOT NULL,
    reason VARCHAR(300) NULL,
    created_at TIMESTAMP(6) NOT NULL,
    processed_at TIMESTAMP(6) NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_payment_refund_gateway UNIQUE (gateway_refund_id),
    CONSTRAINT fk_payment_refund_payment FOREIGN KEY (payment_id) REFERENCES payments(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE INDEX idx_payment_refund_status ON payment_refunds(status, created_at);
