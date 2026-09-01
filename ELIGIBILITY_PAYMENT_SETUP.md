# Eligibility, Guardian Consent and Payment Setup

The enforced server flow is:

1. Email OTP verification enables login.
2. The backend validates the submitted date of birth against the inclusive 10–35 eligibility rule.
3. Participants aged 10–17 enter complete guardian contact details at registration. The guardian receives a separate six-digit email code and must accept every required legal acknowledgement. The code is BCrypt-hashed in MySQL, expires in 10 minutes, permits five attempts and is single-use.
4. Payment order creation is blocked until age and required guardian checks pass.
5. The browser never activates a registration. Only a valid Razorpay `payment.captured` webhook with matching server amount and currency changes the status to `CONFIRMED` and records `activated_at`.

## Required environment variables

```env
RAZORPAY_KEY_ID=rzp_test_xxxxxxxxx
RAZORPAY_KEY_SECRET=
RAZORPAY_WEBHOOK_SECRET=

SMTP_HOST=smtp.gmail.com
SMTP_PORT=587
SMTP_USERNAME=
SMTP_PASSWORD=
NOTIFICATION_FROM_EMAIL=
```

No Twilio or mobile OTP configuration is used.

## Razorpay configuration

- Use test mode until acceptance testing is complete.
- Webhook URL: `https://YOUR_HOST/api/payments/webhook`.
- Subscribe to `payment.captured` and `payment.failed`.
- Use the same webhook secret in Razorpay and `RAZORPAY_WEBHOOK_SECRET`.
- Test INR individual/team orders, USD individual/team orders, wrong amount, wrong currency, duplicate callback and failed payment.

## Acceptance cases

| Participant | Expected sequence |
|---|---|
| Age 18–35 | DOB eligible → payment → active |
| Age 10–17 | DOB eligible → guardian email code and legal consent → payment → active |
| Below 10 or above 35 | Rejected/disqualified; payment remains locked |
| Invalid guardian code | Consent remains pending; payment locked |
| Browser checkout success without webhook | Registration remains pending |
| Valid captured webhook | Registration becomes confirmed and `activated_at` is stored |

Before public launch, obtain legal review of minor consent, child-data processing, retention, refunds and international participation.
