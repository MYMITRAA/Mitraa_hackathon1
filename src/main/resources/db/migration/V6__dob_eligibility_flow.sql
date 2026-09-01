UPDATE registrations
SET age_verification_status = 'DOB_VERIFIED',
    status = CASE
      WHEN guardian_consent_required = TRUE AND guardian_consent_received = FALSE
        THEN 'PENDING_GUARDIAN'
      ELSE 'PENDING_PAYMENT'
    END
WHERE status = 'PENDING_VERIFICATION'
  AND date_of_birth IS NOT NULL
  AND TIMESTAMPDIFF(YEAR, date_of_birth, UTC_DATE()) BETWEEN 10 AND 35;
