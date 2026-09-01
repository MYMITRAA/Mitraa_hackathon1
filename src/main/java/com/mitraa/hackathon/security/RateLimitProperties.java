package com.mitraa.hackathon.security;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "app.security.rate-limit")
public class RateLimitProperties {
    private boolean enabled = true;
    private Duration bucketRetention = Duration.ofHours(1);
    private List<Policy> policies = new ArrayList<>();

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public Duration getBucketRetention() { return bucketRetention; }
    public void setBucketRetention(Duration bucketRetention) { this.bucketRetention = bucketRetention; }
    public List<Policy> getPolicies() { return policies; }
    public void setPolicies(List<Policy> policies) { this.policies = policies; }

    public static class Policy {
        private String path;
        private long capacity;
        private Duration refillPeriod;

        public String getPath() { return path; }
        public void setPath(String path) { this.path = path; }
        public long getCapacity() { return capacity; }
        public void setCapacity(long capacity) { this.capacity = capacity; }
        public Duration getRefillPeriod() { return refillPeriod; }
        public void setRefillPeriod(Duration refillPeriod) { this.refillPeriod = refillPeriod; }
    }
}
