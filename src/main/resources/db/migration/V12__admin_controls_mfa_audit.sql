ALTER TABLE users
    ADD COLUMN admin_mfa_secret VARCHAR(600) NULL,
    ADD COLUMN admin_mfa_enabled BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN admin_mfa_enabled_at TIMESTAMP(6) NULL;

CREATE TABLE admin_action_audit (
    id BIGINT NOT NULL AUTO_INCREMENT,
    actor_user_id BIGINT NULL,
    actor_email VARCHAR(190) NOT NULL,
    actor_role VARCHAR(30) NOT NULL,
    action VARCHAR(80) NOT NULL,
    resource_type VARCHAR(80) NOT NULL,
    resource_id VARCHAR(120) NULL,
    request_method VARCHAR(10) NOT NULL,
    request_path VARCHAR(300) NOT NULL,
    outcome VARCHAR(20) NOT NULL,
    response_status INT NOT NULL,
    ip_address VARCHAR(64) NULL,
    user_agent VARCHAR(500) NULL,
    justification VARCHAR(500) NULL,
    created_at TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_admin_audit_actor FOREIGN KEY (actor_user_id) REFERENCES users(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE INDEX idx_admin_audit_actor_date ON admin_action_audit(actor_user_id, created_at);
CREATE INDEX idx_admin_audit_resource ON admin_action_audit(resource_type, resource_id, created_at);
