ALTER TABLE registrations ADD COLUMN guardian_relationship VARCHAR(40) NULL;
ALTER TABLE registrations ADD COLUMN guardian_phone VARCHAR(40) NULL;
ALTER TABLE registrations ADD COLUMN guardian_country VARCHAR(80) NULL;

ALTER TABLE guardian_consents ADD COLUMN consent_ip VARCHAR(64) NULL;
ALTER TABLE guardian_consents ADD COLUMN consent_user_agent VARCHAR(500) NULL;
ALTER TABLE guardian_consents ADD COLUMN legal_version VARCHAR(40) NULL;
ALTER TABLE guardian_consents ADD COLUMN consented_at TIMESTAMP(6) NULL;
