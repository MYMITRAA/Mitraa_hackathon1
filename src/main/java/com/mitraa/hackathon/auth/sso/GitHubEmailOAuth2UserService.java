package com.mitraa.hackathon.auth.sso;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserService;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

@Service
public class GitHubEmailOAuth2UserService implements OAuth2UserService<OAuth2UserRequest, OAuth2User> {
    private final DefaultOAuth2UserService delegate = new DefaultOAuth2UserService();
    private final RestClient restClient = RestClient.create();

    @Override
    public OAuth2User loadUser(OAuth2UserRequest request) throws OAuth2AuthenticationException {
        OAuth2User principal = delegate.loadUser(request);
        if (!"github".equals(request.getClientRegistration().getRegistrationId())) {
            return principal;
        }

        Map<String, Object> attributes = new LinkedHashMap<>(principal.getAttributes());
        if (attributes.get("email") == null) {
            verifiedGitHubEmail(request).ifPresent(email -> attributes.put("email", email));
        }
        attributes.put("email_verified", attributes.get("email") != null);
        return new DefaultOAuth2User(new ArrayList<>(principal.getAuthorities()), attributes, "id");
    }

    private java.util.Optional<String> verifiedGitHubEmail(OAuth2UserRequest request) {
        List<Map<String, Object>> emails;
        try {
            emails = restClient.get()
                    .uri("https://api.github.com/user/emails")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + request.getAccessToken().getTokenValue())
                    .header(HttpHeaders.ACCEPT, "application/vnd.github+json")
                    .retrieve()
                    .body(new ParameterizedTypeReference<>() {});
        } catch (org.springframework.web.client.RestClientException exception) {
            return java.util.Optional.empty();
        }
        if (emails == null) return java.util.Optional.empty();
        return emails.stream()
                .filter(item -> Boolean.TRUE.equals(item.get("verified")))
                .sorted((left, right) -> Boolean.compare(
                        Boolean.TRUE.equals(right.get("primary")), Boolean.TRUE.equals(left.get("primary"))))
                .map(item -> String.valueOf(item.get("email")))
                .filter(email -> !email.isBlank() && !"null".equals(email))
                .findFirst();
    }
}
