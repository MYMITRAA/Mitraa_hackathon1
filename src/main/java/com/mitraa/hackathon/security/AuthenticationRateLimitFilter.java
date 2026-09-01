package com.mitraa.hackathon.security;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.ConsumptionProbe;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.http.MediaType;
import org.springframework.http.HttpStatus;
import org.springframework.web.filter.OncePerRequestFilter;

public class AuthenticationRateLimitFilter extends OncePerRequestFilter {
    private final RateLimitProperties properties;
    private final Map<String, BucketHolder> buckets = new ConcurrentHashMap<>();
    private final AtomicLong requestCounter = new AtomicLong();

    public AuthenticationRateLimitFilter(RateLimitProperties properties) {
        this.properties = properties;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        RateLimitProperties.Policy policy = matchingPolicy(request);
        if (!properties.isEnabled() || policy == null) {
            chain.doFilter(request, response);
            return;
        }

        cleanupOccasionally();
        String key = policy.getPath() + ':' + clientAddress(request);
        BucketHolder holder = buckets.computeIfAbsent(key, ignored -> new BucketHolder(newBucket(policy)));
        holder.lastAccessEpochMillis = System.currentTimeMillis();
        ConsumptionProbe probe = holder.bucket.tryConsumeAndReturnRemaining(1);

        response.setHeader("X-RateLimit-Limit", Long.toString(policy.getCapacity()));
        response.setHeader("X-RateLimit-Remaining", Long.toString(probe.getRemainingTokens()));
        if (probe.isConsumed()) {
            chain.doFilter(request, response);
            return;
        }

        long retryAfterSeconds = Math.max(1,
                (long) Math.ceil(probe.getNanosToWaitForRefill() / 1_000_000_000.0));
        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setHeader("Retry-After", Long.toString(retryAfterSeconds));
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write("{\"status\":429,\"error\":\"Too Many Requests\","
                + "\"message\":\"Too many attempts. Please try again later.\","
                + "\"retryAfterSeconds\":" + retryAfterSeconds + "}");
    }

    private RateLimitProperties.Policy matchingPolicy(HttpServletRequest request) {
        if (!"POST".equalsIgnoreCase(request.getMethod())) return null;
        String path = request.getRequestURI();
        return properties.getPolicies().stream()
                .filter(policy -> policy.getPath() != null && policy.getPath().equals(path))
                .findFirst().orElse(null);
    }

    private Bucket newBucket(RateLimitProperties.Policy policy) {
        if (policy.getCapacity() < 1 || policy.getRefillPeriod() == null
                || policy.getRefillPeriod().isZero() || policy.getRefillPeriod().isNegative()) {
            throw new IllegalStateException("Invalid rate-limit policy for " + policy.getPath());
        }
        Bandwidth bandwidth = Bandwidth.builder()
                .capacity(policy.getCapacity())
                .refillGreedy(policy.getCapacity(), policy.getRefillPeriod())
                .build();
        return Bucket.builder().addLimit(bandwidth).build();
    }

    private String clientAddress(HttpServletRequest request) {
        String address = request.getRemoteAddr();
        return address == null || address.isBlank() ? "unknown" : address;
    }

    private void cleanupOccasionally() {
        if ((requestCounter.incrementAndGet() & 1023) != 0) return;
        Duration retention = properties.getBucketRetention();
        long cutoff = System.currentTimeMillis() - Math.max(60_000, retention.toMillis());
        buckets.entrySet().removeIf(entry -> entry.getValue().lastAccessEpochMillis < cutoff);
    }

    private static final class BucketHolder {
        private final Bucket bucket;
        private volatile long lastAccessEpochMillis = System.currentTimeMillis();
        private BucketHolder(Bucket bucket) { this.bucket = bucket; }
    }
}
