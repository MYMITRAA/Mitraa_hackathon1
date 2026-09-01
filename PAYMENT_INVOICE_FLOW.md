# Payment confirmation and invoice flow

## Runtime sequence

1. The participant creates a Razorpay order through `POST /api/payments/create-order`.
2. Razorpay sends `payment.captured` to `POST /api/payments/webhook`.
3. The backend verifies `X-Razorpay-Signature` using `RAZORPAY_WEBHOOK_SECRET`.
4. The backend locks the payment row and validates captured amount and currency against the server-created order.
5. The payment becomes `PAID`, the registration becomes `CONFIRMED`, and its activation time is stored.
6. A single invoice snapshot is persisted for the payment.
7. A `PAYMENT_CONFIRMED` email is queued in the notification outbox.
8. The dispatcher generates the invoice PDF, attaches it, and sends the professional confirmation email.

Razorpay webhook retries are safe: the payment row lock, paid-state check, one-invoice-per-payment constraint, and outbox duplicate check prevent duplicate processing.

## Required environment variables

```env
RAZORPAY_KEY_ID=rzp_test_xxxxxxxxx
RAZORPAY_KEY_SECRET=replace_with_test_key_secret
RAZORPAY_WEBHOOK_SECRET=replace_with_your_webhook_secret

SMTP_HOST=smtp.gmail.com
SMTP_PORT=587
SMTP_USERNAME=your-sender@example.com
SMTP_PASSWORD=replace_with_an_app_password
NOTIFICATION_FROM_EMAIL=your-sender@example.com

APP_PUBLIC_BASE_URL=http://localhost:8080
```

For production, set `APP_PUBLIC_BASE_URL` to the public HTTPS origin. Never commit actual credentials.

## Razorpay test webhook

Configure the webhook URL as:

```text
https://YOUR_PUBLIC_HOST/api/payments/webhook
```

Subscribe to `payment.captured` and `payment.failed`. Use Razorpay test-mode keys until acceptance testing is complete.

## Verification

After a captured test payment, confirm:

- `payments.status` is `PAID` and `paid_at` is populated.
- `registrations.status` is `CONFIRMED` and `activated_at` is populated.
- One row exists in `invoices` for the payment.
- One `PAYMENT_CONFIRMED` outbox row exists and becomes `SENT`.
- The received email contains the PDF attachment.
- The signed-in participant can download `/api/invoices/{invoiceNumber}/download`.
- A different participant receives HTTP 403 for that invoice.
