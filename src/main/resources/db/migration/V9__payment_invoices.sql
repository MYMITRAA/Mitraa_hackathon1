ALTER TABLE notification_outbox MODIFY COLUMN body TEXT NOT NULL;
ALTER TABLE payments ADD CONSTRAINT uk_payment_gateway_payment UNIQUE (gateway_payment_id);

CREATE TABLE invoices (
    id BIGINT NOT NULL AUTO_INCREMENT,
    payment_id BIGINT NOT NULL,
    invoice_number VARCHAR(40) NULL,
    player_id VARCHAR(32) NOT NULL,
    participant_name VARCHAR(120) NOT NULL,
    participant_email VARCHAR(190) NOT NULL,
    entry_type VARCHAR(20) NOT NULL,
    amount_minor BIGINT NOT NULL,
    currency VARCHAR(3) NOT NULL,
    gateway_order_id VARCHAR(80) NOT NULL,
    gateway_payment_id VARCHAR(80) NOT NULL,
    payment_date TIMESTAMP(6) NOT NULL,
    payment_status VARCHAR(20) NOT NULL,
    created_at TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_invoice_payment UNIQUE (payment_id),
    CONSTRAINT uk_invoice_number UNIQUE (invoice_number),
    CONSTRAINT fk_invoice_payment FOREIGN KEY (payment_id) REFERENCES payments (id)
);
