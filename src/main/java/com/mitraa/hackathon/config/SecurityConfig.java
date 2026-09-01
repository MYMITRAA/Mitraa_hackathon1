package com.mitraa.hackathon.config;

import com.mitraa.hackathon.auth.sso.GitHubEmailOAuth2UserService;
import com.mitraa.hackathon.auth.sso.OAuthLoginSuccessHandler;
import com.mitraa.hackathon.security.AuthenticationRateLimitFilter;
import com.mitraa.hackathon.security.RateLimitProperties;
import com.mitraa.hackathon.user.UserRepository;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;

@Configuration
public class SecurityConfig {

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }

    @Bean
    UserDetailsService userDetailsService(UserRepository users) {
        return username -> users.findByEmailIgnoreCase(username)
                .map(user -> User.withUsername(user.getEmail())
                        .password(user.getPasswordHash())
                        .roles(user.getRole().name())
                        .disabled(!user.isEnabled())
                        .build())
                .orElseThrow(() ->
                        new UsernameNotFoundException("User not found"));
    }

    @Bean
    SecurityFilterChain security(
            HttpSecurity http,
            ObjectProvider<ClientRegistrationRepository> clientRegistrations,
            GitHubEmailOAuth2UserService oauth2UserService,
            OAuthLoginSuccessHandler oauthSuccessHandler,
            RateLimitProperties rateLimits) throws Exception {
        http
                .authorizeHttpRequests(auth -> auth

                        // Public pages and files
                        .requestMatchers(
                                "/",
                                "/index.html",
                                "/arenas.html",
                                "/mission.html",
                                "/quest.html",
                                "/register.html",
                                "/login.html",
                                "/verify-email.html",
                                "/forgot-password.html",
                                "/privacy.html",
                                "/terms.html",
                                "/rules.html",
                                "/code-of-conduct.html",
                                "/refund-policy.html",
                                "/child-safety.html",
                                "/styles.css",
                                "/enhancements.css",
                                "/mission.css",
                                "/arena-polish.css",
                                "/account.css",
                                "/responsive.css",
                                "/social-links.css",
                                "/team-builder.css",
                                "/quality.css",
                                "/app.js",
                                "/portal.js",
                                "/sso-login.js",
                                "/security-api.js",
                                "/quality.js",
                                "/assets/**",
                                "/robots.txt",
                                "/sitemap.xml"
                        ).permitAll()

                        // Public authentication APIs
                        .requestMatchers(
                                "/api/auth/register",
                                "/api/auth/verify-email",
                                "/api/auth/resend-otp",
                                "/api/auth/forgot-password",
                                "/api/auth/reset-password"
                                ,"/api/auth/sso/providers"
                        ).permitAll()

                        .requestMatchers("/oauth2/**", "/login/oauth2/**").permitAll()

                        // Provider webhooks
                        .requestMatchers(
                                "/api/verification/webhook",
                                "/api/payments/webhook"
                        ).permitAll()

                        // Public health endpoint
                        .requestMatchers("/actuator/health")
                        .permitAll()

                        // Admin and Super Admin access
                        .requestMatchers(
                                "/admin.html",
                                "/admin.js",
                                "/admin.css",
                                "/api/admin/**"
                        ).hasAnyRole("ADMIN", "SUPER_ADMIN")

                        // Participant-only access
                        .requestMatchers(
                                "/dashboard.html",
                                "/api/payments/**",
                                "/api/verification/**",
                                "/api/guardian/**",
                                "/api/teams/**",
                                "/api/submissions/**"
                        ).hasRole("PARTICIPANT")

                        // Available to every authenticated account
                        .requestMatchers(
                                "/api/auth/me",
                                "/api/auth/csrf",
                                "/api/invoices/**"
                        ).authenticated()

                        .anyRequest().authenticated()
                )

                .csrf(csrf -> csrf
                        .csrfTokenRepository(
                                CookieCsrfTokenRepository.withHttpOnlyFalse()
                        )
                        .csrfTokenRequestHandler(
                                new CsrfTokenRequestAttributeHandler()
                        )
                        .ignoringRequestMatchers(
                                "/api/auth/register",
                                "/api/auth/login",
                                "/api/auth/logout",
                                "/api/auth/verify-email",
                                "/api/auth/resend-otp",
                                "/api/auth/forgot-password",
                                "/api/auth/reset-password",
                                "/api/payments/webhook",
                                "/api/verification/webhook"
                        )
                )

                .formLogin(form -> form
                        .loginPage("/login.html")
                        .loginProcessingUrl("/api/auth/login")
                        .usernameParameter("email")
                        .passwordParameter("password")
                        .successHandler((request, response, authentication) -> {
                            boolean administrator =
                                    authentication.getAuthorities()
                                            .stream()
                                            .anyMatch(authority ->
                                                    "ROLE_ADMIN".equals(
                                                            authority.getAuthority()
                                                    )
                                                    || "ROLE_SUPER_ADMIN".equals(
                                                            authority.getAuthority()
                                                    )
                                            );

                            if (administrator) {
                                response.sendRedirect("/admin.html");
                            } else {
                                response.sendRedirect("/dashboard.html");
                            }
                        })
                        .failureUrl("/login.html?error=true")
                        .permitAll()
                )

                .logout(logout -> logout
                        .logoutUrl("/api/auth/logout")
                        .logoutSuccessUrl("/index.html")
                        .invalidateHttpSession(true)
                        .deleteCookies("JSESSIONID")
                )

                .sessionManagement(session -> session
                        .sessionFixation(fixation -> fixation.migrateSession())
                )

                .headers(headers -> headers
                        .contentTypeOptions(contentType -> {})
                        .frameOptions(frame -> frame.deny())
                        .referrerPolicy(referrer -> referrer.policy(
                                ReferrerPolicyHeaderWriter.ReferrerPolicy.STRICT_ORIGIN_WHEN_CROSS_ORIGIN
                        ))
                        .permissionsPolicy(permissions -> permissions.policy(
                                "camera=(), microphone=(), geolocation=(), payment=(self)"
                        ))
                )
                .addFilterBefore(new AuthenticationRateLimitFilter(rateLimits),
                        UsernamePasswordAuthenticationFilter.class);

        if (clientRegistrations.getIfAvailable() != null) {
            http.oauth2Login(oauth -> oauth
                    .loginPage("/login.html")
                    .userInfoEndpoint(userInfo -> userInfo.userService(oauth2UserService))
                    .successHandler(oauthSuccessHandler)
                    .failureUrl("/login.html?ssoError=true")
            );
        }

        return http.build();
    }
}
