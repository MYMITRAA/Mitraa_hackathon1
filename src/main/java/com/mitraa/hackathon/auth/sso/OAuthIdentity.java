package com.mitraa.hackathon.auth.sso;

import com.mitraa.hackathon.user.User;
import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "oauth_identities", uniqueConstraints = @UniqueConstraint(
        name = "uk_oauth_provider_subject", columnNames = {"provider", "provider_subject"}))
public class OAuthIdentity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;
    @Column(nullable = false, length = 30)
    private String provider;
    @Column(name = "provider_subject", nullable = false, length = 190)
    private String providerSubject;
    @Column(name = "provider_email", nullable = false, length = 190)
    private String providerEmail;
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();
    @Column(name = "last_login_at", nullable = false)
    private Instant lastLoginAt = Instant.now();

    public Long getId() { return id; }
    public User getUser() { return user; }
    public void setUser(User user) { this.user = user; }
    public String getProvider() { return provider; }
    public void setProvider(String provider) { this.provider = provider; }
    public String getProviderSubject() { return providerSubject; }
    public void setProviderSubject(String providerSubject) { this.providerSubject = providerSubject; }
    public String getProviderEmail() { return providerEmail; }
    public void setProviderEmail(String providerEmail) { this.providerEmail = providerEmail; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getLastLoginAt() { return lastLoginAt; }
    public void setLastLoginAt(Instant lastLoginAt) { this.lastLoginAt = lastLoginAt; }
}
