# Bucket4j rate limiting

Rate limiting is enabled by default for sensitive unauthenticated POST endpoints. Buckets are keyed by endpoint and client IP address.

| Endpoint | Capacity | Refill period |
| --- | ---: | ---: |
| `/api/auth/login` | 5 | 1 minute |
| `/api/auth/register` | 3 | 10 minutes |
| `/api/auth/resend-otp` | 3 | 10 minutes |
| `/api/auth/verify-email` | 10 | 10 minutes |
| `/api/auth/forgot-password` | 3 | 15 minutes |
| `/api/auth/reset-password` | 5 | 15 minutes |
| `/api/auth/change-password` | 5 | 15 minutes |

Rejected requests return HTTP `429`, `Retry-After`, `X-RateLimit-Limit`, and `X-RateLimit-Remaining`. Limits can be adjusted in `application.yml`, and can be disabled locally with `RATE_LIMIT_ENABLED=false`.

The current implementation stores buckets in application memory. This is suitable for one application instance. Before running multiple application replicas, use Bucket4j with a shared Redis-compatible backend so every replica enforces one combined limit.

When deploying behind Azure Front Door, Application Gateway, Nginx, or another proxy, restrict direct access to the application origin and configure trusted forwarded headers. This prevents clients from spoofing the address used for rate-limit keys.
