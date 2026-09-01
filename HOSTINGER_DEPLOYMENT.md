# MiTRAA Hackathons — Hostinger Production Deployment

## Recommended target

Use a Hostinger VPS with Ubuntu, at least 4 vCPU / 8 GB RAM for launch, Docker Engine, Docker Compose, Nginx, PostgreSQL and automated backups. Managed Hostinger web hosting is suitable for Node.js apps, but this package is Java 21/Spring Boot; VPS deployment gives the required runtime and operational control. Scale vertically before registration opens and load-test at 10,000 registrations.

## 1. Accounts and approvals before deployment

- Hostinger VPS and `hackathons.mitratechgroup.com` DNS access.
- Razorpay international payments activated for the MiTRAA merchant account. Confirm USD acceptance and INR settlement with the gateway.
- Sumsub (or approved equivalent) age-assurance account and a configured `mitraa-age-assurance` level.
- Hostinger SMTP mailbox for `info@mitratechgroup.com`.
- Twilio or another legally approved SMS provider with international delivery.
- Legal review for participants aged 10–17, guardian consent, privacy notice, retention/deletion schedule and jurisdiction restrictions.

## 2. Prepare the VPS

```bash
ssh root@YOUR_SERVER_IP
apt update && apt upgrade -y
apt install -y ca-certificates curl nginx certbot python3-certbot-nginx ufw fail2ban
curl -fsSL https://get.docker.com | sh
systemctl enable --now docker nginx fail2ban
ufw allow OpenSSH
ufw allow 'Nginx Full'
ufw enable
```

Create a non-root deploy user and upload this project to `/opt/mitraa-hackathons`. Restrict the `.env` file to that user (`chmod 600 .env`). Never commit `.env`.

## 3. Configure secrets

```bash
cd /opt/mitraa-hackathons
cp .env.example .env
nano .env
```

Generate a strong database password. Fill Razorpay, Sumsub, SMTP and SMS credentials. Keep `MITRAA_BOOTSTRAP_ADMIN=false` in production; provision the initial admin through a controlled one-time process and rotate credentials immediately.

## 4. Build and start

```bash
docker compose build --pull
docker compose up -d
docker compose ps
curl http://127.0.0.1:8080/actuator/health
```

Flyway automatically applies `V1` and `V2` migrations. The application refuses to invent successful payments or verifications when provider credentials are absent.

## 5. Domain, Nginx and TLS

Create an A record for `hackathons.mitratechgroup.com` pointing to the VPS. Copy `deploy/nginx-mitraa.conf` to `/etc/nginx/sites-available/mitraa`, enable it, validate and issue TLS:

```bash
ln -s /etc/nginx/sites-available/mitraa /etc/nginx/sites-enabled/mitraa
nginx -t && systemctl reload nginx
certbot --nginx -d hackathons.mitratechgroup.com
```

After HTTPS works, enable HSTS and a conservative Content-Security-Policy. The dashboard currently loads Razorpay Checkout from `checkout.razorpay.com`; include only the gateway and verification SDK origins that are actually used.

## 6. Provider callbacks

- Razorpay webhook: `https://hackathons.mitratechgroup.com/api/payments/webhook`
- Sumsub webhook: `https://hackathons.mitratechgroup.com/api/verification/webhook`
- Subscribe Razorpay to `payment.captured`, refund and dispute events before launch.
- Configure and test the Sumsub payload digest secret. Use age estimation first and document verification only as a fallback when appropriate.

Registration confirmation is produced only by the signed Razorpay webhook. Verification approval is produced only by the signed verification webhook.

## 7. Mandatory notification acceptance test

Run a test participant through each event and verify that both EMAIL and SMS outbox rows reach `SENT`:

1. Registration created.
2. Verification started and decided.
3. Payment captured.
4. Deadline reminder.
5. POC submitted.
6. Top-10/final result published.

Any `FAILED` notification must appear in the Admin Command Center and be retried or escalated. The current code implements registration and payment messages; later event modules must call the same outbox service before launch.

## 8. Backups and operations

- Nightly encrypted PostgreSQL backups to a second region/provider; retain daily/weekly/monthly copies.
- Test restoration before launch and again before results day.
- Ship application and Nginx logs to external monitoring; alert on health failure, HTTP 5xx, payment webhook errors and notification backlog.
- Add Redis-backed sessions before running multiple application replicas.
- Put Cloudflare or equivalent WAF/CDN in front of Hostinger, with rate limits and bot protection on registration/login endpoints.
- Run OWASP dependency scanning, SAST, DAST and a load test representing 10,000 participants.

## 9. Updating the deployment

```bash
cd /opt/mitraa-hackathons
git pull --ff-only
docker compose build --pull
docker compose up -d
curl https://hackathons.mitratechgroup.com/actuator/health
```

Use a staging subdomain and database first. Database migrations must be backward compatible; take a backup before each production release.

## Launch blockers

Do not open public registration until all are complete: gateway live-mode order + signed webhook test; refund/reconciliation test; eKYC/age and minor-consent legal approval; SMTP and SMS delivery tests; admin MFA/RBAC; privacy/terms/refund pages; backup restore; load test; accessibility review; incident contacts; and production monitoring.

Official references: [Hostinger VPS](https://www.hostinger.com/vps-hosting), [Razorpay international payments](https://razorpay.com/docs/payments/international-payments/), [Razorpay currency conversion](https://razorpay.com/docs/payments/international-payments/currency-conversion/), [Sumsub age estimation](https://docs.sumsub.com/docs/age-estimation), [Sumsub consent requirements](https://docs.sumsub.com/docs/applicant-privacy-disclosures-and-consent-requirements).
