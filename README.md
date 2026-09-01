# MiTRAA Hackathons 2026 — Java 21 World Edition

Production-oriented Spring Boot foundation for MY MITRAA TECHNOLOGY PRIVATE LIMITED.

## Included

- Premium responsive gamified frontend with the company logo, selected hackathon logo and replaceable MAHIBOT asset.
- Public home, searchable 15-arena page, optional Signal Sync quest, India-first registration, login, functional participant quest dashboard and protected admin command center.
- Java 21, Spring Boot 3.5, Spring Security sessions, BCrypt (cost 12), Bean Validation, JPA, Flyway and Actuator.
- MySQL 8.4 for both local and production profiles; no H2 runtime.
- Ages 10–35 backend validation; guardian consent required under 18.
- Provider-hosted Sumsub age-assurance session and signed webhook adapter.
- Country-aware Razorpay orders: India uses INR (₹14.01 individual / ₹19.93 team); every other country uses USD ($2.70 individual / $5.80 team). Payment capture validates the signed webhook amount and currency.
- Email-only notification outbox with retry/backoff and admin failure counts; no mobile OTP or Twilio dependency.
- Auditable Terms, Privacy and Rules consent timestamps, plus separate Privacy, Terms, Rules, Code of Conduct, Refund and Child Safety pages.
- Persisted team creation (leader + 2 minimum), POC draft/final submission, deadline enforcement and live admin participant/POC metrics.
- SEO metadata, Event JSON-LD, sitemap, robots controls and reduced-motion support.
- Multi-stage Dockerfile, Docker Compose and Hostinger VPS deployment runbook.

## Local development

Requires Java 21 and Maven 3.9+.

```bash
mvn spring-boot:run
```

Open `http://localhost:8080`. Docker Compose stores MySQL data in the `mysql_data` volume.

For a local admin only:

```bash
export MITRAA_BOOTSTRAP_ADMIN=true
export MITRAA_ADMIN_EMAIL=admin@example.com
export MITRAA_ADMIN_PASSWORD='use-a-long-random-local-password'
mvn spring-boot:run
```

Never enable bootstrap admin in production.

## Production

```bash
cp .env.example .env
# Fill all credentials
docker compose build --pull
docker compose up -d
curl http://127.0.0.1:8080/actuator/health
```

For the currently selected Azure deployment path, use the Docker image with Azure App Service or Azure Container Apps, Azure Database for PostgreSQL, Key Vault and Application Insights. Configure the environment variables from `.env.example`, run migrations against staging, verify `/actuator/health`, then configure the payment and verification webhook URLs before enabling public registration.

## Security and truthfulness

- The browser never confirms a payment. Only a valid gateway webhook changes registration to `CONFIRMED`.
- eKYC/age state changes only after a valid provider webhook digest.
- Raw identity documents and biometric images are not stored in the application database.
- Missing provider credentials return `NOT_CONFIGURED`; the code does not simulate provider success.
- CSRF protection is cookie/token-based for authenticated state-changing requests; webhooks use provider signatures.

## Still required before public launch

The foundation does **not** claim that the following provider/advanced modules are complete: team invitations, POC binary object storage and malware scanning, jury assignment/scoring, rankings/Top 10, certificates with QR verification, password reset/email OTP, admin MFA/granular RBAC, refunds/disputes/reconciliation UI, full audit logging, CAPTCHA/rate limits, Redis sessions and localization. The included legal drafts require qualified legal review before launch.

Build these in the next controlled phase, then complete the launch blockers in `HOSTINGER_DEPLOYMENT.md`.
