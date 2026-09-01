-- Payment is the final activation step. Existing unpaid registrations without a
-- final POC return to the submission stage. Paid/confirmed records are untouched.
UPDATE registrations r
LEFT JOIN submissions s ON s.registration_id = r.id
SET r.status = 'PENDING_SUBMISSION'
WHERE r.status = 'PENDING_PAYMENT'
  AND (s.id IS NULL OR s.status = 'DRAFT');

UPDATE registrations r
JOIN submissions s ON s.registration_id = r.id
SET r.status = 'PENDING_PAYMENT'
WHERE r.status = 'PENDING_SUBMISSION'
  AND s.status IN ('SUBMITTED', 'UNDER_REVIEW', 'FINALIST', 'NOT_SELECTED', 'WINNER');
