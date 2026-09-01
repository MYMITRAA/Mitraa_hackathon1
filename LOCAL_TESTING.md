# Local deployment and acceptance testing

## 1. Prerequisites

Install Java 21 and Maven 3.9+. Confirm with `java -version` and `mvn -version`.

## 2. Start locally

```bash
mvn clean test
mvn spring-boot:run
```

Open `http://localhost:8080`. Both profiles use MySQL 8; Docker Compose stores local data in the `mysql_data` volume.

## 3. Create a local administrator

```bash
export MITRAA_BOOTSTRAP_ADMIN=true
export MITRAA_ADMIN_EMAIL=admin@example.com
export MITRAA_ADMIN_PASSWORD='replace-with-a-long-random-password'
mvn spring-boot:run
```

Disable bootstrap immediately after the first local admin is created. Never enable it in production.

## 4. India acceptance test

1. Open `/register.html`; confirm India is preselected.
2. Confirm Individual displays ₹14.01 and Team displays ₹19.93.
3. Register an age 18–35 account and accept Terms, Privacy and Rules.
4. Register an age 10–17 test account; confirm guardian consent is required.
5. Log in and confirm the dashboard price remains INR.
6. For a team entry, create a leader + two-member squad.
7. Save a POC draft and confirm it appears after refresh.

## 5. International acceptance test

Change country to Norway or another non-India country. Confirm the browser displays $2.70 / $5.80 and that the server-created payment order is in USD. The server price is authoritative; never trust a browser-supplied amount.

## 6. Provider tests

Without Razorpay and Sumsub credentials, the APIs intentionally return `NOT_CONFIGURED`. For sandbox testing, set the credentials in environment variables, configure signed webhook secrets, use public HTTPS webhook endpoints, and verify that only a valid webhook can mark verification approved or payment confirmed.

## 7. Admin and operational checks

Open `/admin.html` as an admin and verify participant rows, INR/USD revenue, submission counts and notification failures. Check `/actuator/health`. Test SMTP delivery, guardian email OTP, retry behavior, duplicate webhooks and invalid signatures.

## 8. Public-launch gate

Run security scanning, accessibility testing, mobile/browser testing, backup/restore testing and a load test sized for 10,000 registrations. Obtain legal review of all policy pages, especially child consent, international transfers, refunds and prize/tax language.
