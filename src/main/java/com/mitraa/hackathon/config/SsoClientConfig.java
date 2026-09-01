package com.mitraa.hackathon.config;

import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.config.oauth2.client.CommonOAuth2Provider;
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;

@Configuration
public class SsoClientConfig {
    @Bean
    @ConditionalOnProperty(name = "app.sso.enabled", havingValue = "true")
    ClientRegistrationRepository ssoClientRegistrationRepository(
            @Value("${app.sso.google-client-id:}") String googleId,
            @Value("${app.sso.google-client-secret:}") String googleSecret,
            @Value("${app.sso.github-client-id:}") String githubId,
            @Value("${app.sso.github-client-secret:}") String githubSecret) {
        requireCredential("GOOGLE_CLIENT_ID", googleId);
        requireCredential("GOOGLE_CLIENT_SECRET", googleSecret);
        requireCredential("GITHUB_CLIENT_ID", githubId);
        requireCredential("GITHUB_CLIENT_SECRET", githubSecret);

        ClientRegistration google = CommonOAuth2Provider.GOOGLE.getBuilder("google")
                .clientId(googleId).clientSecret(googleSecret)
                .scope("openid", "profile", "email").build();
        ClientRegistration github = CommonOAuth2Provider.GITHUB.getBuilder("github")
                .clientId(githubId).clientSecret(githubSecret)
                .scope("read:user", "user:email").build();
        return new InMemoryClientRegistrationRepository(List.of(google, github));
    }

    private void requireCredential(String name, String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(name + " is required when SSO_ENABLED=true");
        }
    }
}
