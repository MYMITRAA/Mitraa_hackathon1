package com.mitraa.hackathon.auth.sso;

import com.mitraa.hackathon.user.User;
import com.mitraa.hackathon.user.UserRepository;
import java.time.Instant;
import java.util.Locale;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OAuthAccountService {
    private final OAuthIdentityRepository identities;
    private final UserRepository users;

    public OAuthAccountService(OAuthIdentityRepository identities, UserRepository users) {
        this.identities = identities;
        this.users = users;
    }

    @Transactional
    public User resolve(String provider, String subject, String email, boolean emailVerified) {
        String normalizedProvider = required(provider, "provider").toUpperCase(Locale.ROOT);
        String normalizedSubject = required(subject, "provider subject");
        String normalizedEmail = required(email, "verified email").trim().toLowerCase(Locale.ROOT);

        OAuthIdentity existing = identities
                .findByProviderAndProviderSubject(normalizedProvider, normalizedSubject)
                .orElse(null);
        if (existing != null) {
            User user = existing.getUser();
            ensureEnabled(user);
            existing.setLastLoginAt(Instant.now());
            existing.setProviderEmail(normalizedEmail);
            return user;
        }

        if (!emailVerified) {
            throw failure("email_not_verified", "The provider email must be verified.");
        }

        User user = users.findByEmailIgnoreCase(normalizedEmail)
                .orElseThrow(() -> failure("registration_required",
                        "Create and verify a MiTRAA account before using social sign-in."));
        ensureEnabled(user);
        if (user.getEmailVerifiedAt() == null) {
            throw failure("mitraa_email_not_verified", "Verify your MiTRAA email first.");
        }

        OAuthIdentity identity = new OAuthIdentity();
        identity.setUser(user);
        identity.setProvider(normalizedProvider);
        identity.setProviderSubject(normalizedSubject);
        identity.setProviderEmail(normalizedEmail);
        identities.save(identity);
        return user;
    }

    private void ensureEnabled(User user) {
        if (!user.isEnabled()) {
            throw failure("account_disabled", "This MiTRAA account is disabled.");
        }
    }

    private String required(String value, String field) {
        if (value == null || value.isBlank()) {
            throw failure("missing_claim", "The provider did not return a " + field + ".");
        }
        return value;
    }

    private OAuth2AuthenticationException failure(String code, String description) {
        return new OAuth2AuthenticationException(new OAuth2Error(code, description, null));
    }
}
