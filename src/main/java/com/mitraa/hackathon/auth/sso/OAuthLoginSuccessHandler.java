package com.mitraa.hackathon.auth.sso;

import com.mitraa.hackathon.user.User;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.stereotype.Component;

@Component
public class OAuthLoginSuccessHandler implements AuthenticationSuccessHandler {
    private final OAuthAccountService accounts;
    private final HttpSessionSecurityContextRepository contexts =
            new HttpSessionSecurityContextRepository();

    public OAuthLoginSuccessHandler(OAuthAccountService accounts) {
        this.accounts = accounts;
    }

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
                                        Authentication authentication) throws IOException, ServletException {
        OAuth2AuthenticationToken token = (OAuth2AuthenticationToken) authentication;
        OAuth2User principal = token.getPrincipal();
        Map<String, Object> attributes = principal.getAttributes();
        String provider = token.getAuthorizedClientRegistrationId();
        String subject = "google".equals(provider)
                ? string(attributes.get("sub")) : string(attributes.get("id"));
        String email = string(attributes.get("email"));
        boolean verified = principal instanceof OidcUser oidc
                ? Boolean.TRUE.equals(oidc.getEmailVerified())
                : Boolean.TRUE.equals(attributes.get("email_verified"));

        User user;
        try {
            user = accounts.resolve(provider, subject, email, verified);
        } catch (org.springframework.security.oauth2.core.OAuth2AuthenticationException exception) {
            SecurityContextHolder.clearContext();
            if (request.getSession(false) != null) request.getSession(false).invalidate();
            response.sendRedirect("/login.html?ssoError=true");
            return;
        }
        Authentication local = org.springframework.security.authentication.UsernamePasswordAuthenticationToken
                .authenticated(user.getEmail(), null,
                        List.of(new SimpleGrantedAuthority("ROLE_" + user.getRole().name())));
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(local);
        SecurityContextHolder.setContext(context);
        contexts.saveContext(context, request, response);

        boolean administrator = user.getRole().name().equals("ADMIN")
                || user.getRole().name().equals("SUPER_ADMIN");
        response.sendRedirect(administrator ? "/admin.html" : "/dashboard.html");
    }

    private String string(Object value) {
        return value == null ? null : String.valueOf(value);
    }
}
