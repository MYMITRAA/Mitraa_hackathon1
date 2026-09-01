# MiTRAA Hackathons quality gate

This release preserves the existing registration, guardian, team, POC, payment,
invoice and administration workflows. The quality gate verifies presentation,
accessibility, security configuration and the existing business rules.

## Required checks

```bash
mvn clean verify
find src/main/resources/static -maxdepth 1 -name '*.js' -print0 | xargs -0 -n1 node --check
```

## Public-launch acceptance

- Test registration at ages 9, 10, 17, 18, 35 and 36.
- Test individual and teams containing 3, 4 and 5 total participants.
- Confirm guardian controls appear only for participants aged 10–17.
- Confirm final POC submission persists and unlocks payment.
- Confirm Razorpay webhook signatures before activating registrations.
- Confirm invoice download and payment-confirmation email delivery.
- Test participant/admin authorization in a private browser session.
- Complete keyboard, screen-reader and 200% zoom checks.
- Run mobile tests at 320, 375, 768 and 1024 CSS pixels.
- Run production load, backup restoration and provider sandbox tests.

Provider credentials remain environment variables and must never be committed.
