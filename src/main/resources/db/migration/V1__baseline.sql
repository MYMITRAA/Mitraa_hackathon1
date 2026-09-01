CREATE TABLE users (
 id BIGINT AUTO_INCREMENT PRIMARY KEY, full_name VARCHAR(120) NOT NULL, email VARCHAR(190) NOT NULL,
 password_hash VARCHAR(255) NOT NULL, role VARCHAR(30) NOT NULL, enabled BOOLEAN NOT NULL DEFAULT TRUE,
 created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6), CONSTRAINT uk_users_email UNIQUE (email)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE registrations (
 id BIGINT AUTO_INCREMENT PRIMARY KEY, user_id BIGINT NOT NULL, registration_code VARCHAR(32) NOT NULL,
 date_of_birth DATE NOT NULL, phone VARCHAR(40) NOT NULL, country VARCHAR(80) NOT NULL, city VARCHAR(80) NOT NULL,
 participation_type VARCHAR(20) NOT NULL, domain VARCHAR(160) NOT NULL, status VARCHAR(30) NOT NULL,
 created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6), CONSTRAINT uk_registration_user UNIQUE (user_id),
 CONSTRAINT uk_registration_code UNIQUE (registration_code), CONSTRAINT fk_registration_user FOREIGN KEY (user_id) REFERENCES users(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE INDEX idx_registrations_status ON registrations(status);
CREATE INDEX idx_registrations_domain ON registrations(domain);
