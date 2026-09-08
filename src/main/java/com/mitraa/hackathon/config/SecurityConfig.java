package com.mitraa.hackathon.config;

import com.mitraa.hackathon.auth.sso.GitHubEmailOAuth2UserService;
import com.mitraa.hackathon.auth.sso.OAuthLoginSuccessHandler;
import com.mitraa.hackathon.payment.PaymentRepository;
import com.mitraa.hackathon.payment.PaymentStatus;
import com.mitraa.hackathon.registration.RegistrationRepository;
import com.mitraa.hackathon.security.AuthenticationRateLimitFilter;
import com.mitraa.hackathon.security.RateLimitProperties;
import com.mitraa.hackathon.user.UserRepository;

import jakarta.servlet.http.HttpSession;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.context.SecurityContextHolder;
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
            RateLimitProperties rateLimits,
            UserRepository users,
            RegistrationRepository registrations,
            PaymentRepository payments) throws Exception {

        http

                // =========================================================
                // AUTHORIZATION
                // =========================================================

                .authorizeHttpRequests(auth -> auth

                        // -------------------------------------------------
                        // Public pages and static files
                        // -------------------------------------------------

                        .requestMatchers(
                                "/",
                                "/404.html",
                                "/404.css",
                                "/",
                                "/arenas",
                                "/mission",
                                "/quest",
                                "/register",
                                "/login",
                                "/verify-email",
                                "/forgot-password",

                                // Payment activation page
                                "/payment",
                                "/payment.js",

                                "/profile.css",
                                "/privacy",
                                "/terms",
                                "/rules",
                                "/code-of-conduct",
                                "/refund-policy",
                                "/child-safety",

                                // Legacy .html URLs: allowed only so they can redirect to clean URLs.
                                "/index.html", "/arenas.html", "/mission.html", "/quest.html",
                                "/register.html", "/login.html", "/verify-email.html", "/forgot-password.html",
                                "/dashboard.html", "/admin.html", "/payment.html", "/profile.html", "/change-password.html",
                                "/privacy.html", "/terms.html", "/rules.html", "/code-of-conduct.html",
                                "/refund-policy.html", "/child-safety.html",

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
                                "/login-message.js",

                                "/assets/**",

                                "/robots.txt",
                                "/sitemap.xml"

                        ).permitAll()


                        // -------------------------------------------------
                        // Public authentication APIs
                        // -------------------------------------------------

                        .requestMatchers(
                                "/api/auth/register",
                                "/api/auth/verify-email",
                                "/api/auth/resend-otp",
                                "/api/auth/forgot-password",
                                "/api/auth/reset-password",
                                "/api/auth/sso/providers",

                                // CSRF token is safe to expose publicly.
                                // Actual POST requests still require CSRF.
                                "/api/auth/csrf"

                        ).permitAll()


                        // -------------------------------------------------
                        // PAYMENT ACTIVATION APIs
                        // -------------------------------------------------
                        //
                        // Payment happens BEFORE LOGIN.
                        //
                        // These endpoints therefore MUST be public.
                        //

                        .requestMatchers(
                                "/api/payments/create-order-after-verification",
                                "/api/payments/verification-status",
                                "/api/payments/verify-after-checkout"

                        ).permitAll()


                        // -------------------------------------------------
                        // OAuth endpoints
                        // -------------------------------------------------

                        .requestMatchers(
                                "/oauth2/**",
                                "/login/oauth2/**"

                        ).permitAll()


                        // -------------------------------------------------
                        // Provider webhooks
                        // -------------------------------------------------

                        .requestMatchers(
                                "/api/verification/webhook",
                                "/api/payments/webhook"

                        ).permitAll()


                        // -------------------------------------------------
                        // Public health endpoint
                        // -------------------------------------------------

                        .requestMatchers("/actuator/health")
                        .permitAll()


                        // -------------------------------------------------
                        // Admin and Super Admin
                        // -------------------------------------------------

                        .requestMatchers(
                                "/admin",
                                "/admin.js",
                                "/admin.css",
                                "/api/admin/**"

                        ).hasAnyRole(
                                "ADMIN",
                                "SUPER_ADMIN"
                        )


                        // -------------------------------------------------
                        // Participant-only access
                        // -------------------------------------------------

                        .requestMatchers(
                                "/dashboard",
                                "/profile",
                                "/profile.js",
                                "/api/profile/**",
                                "/api/payments/**",
                                "/api/verification/**",
                                "/api/guardian/**",
                                "/api/teams/**",
                                "/api/submissions/**"

                        ).hasRole("PARTICIPANT")


                        // -------------------------------------------------
                        // Authenticated account APIs
                        // -------------------------------------------------

                        .requestMatchers(
                                "/api/auth/me",
                                "/api/invoices/**"

                        ).authenticated()


                        // -------------------------------------------------
                        // Everything else
                        // -------------------------------------------------

                        .anyRequest().authenticated()
                )


                // =========================================================
                // CSRF
                // =========================================================

                .csrf(csrf -> csrf

                        .csrfTokenRepository(
                                CookieCsrfTokenRepository.withHttpOnlyFalse()
                        )

                        .csrfTokenRequestHandler(
                                new CsrfTokenRequestAttributeHandler()
                        )

                        // Webhooks and selected authentication endpoints
                        // intentionally do not require CSRF.
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


                // =========================================================
                // FORM LOGIN
                // =========================================================

                .formLogin(form -> form

                        .loginPage("/login")

                        .loginProcessingUrl("/api/auth/login")

                        .usernameParameter("email")

                        .passwordParameter("password")

                        .successHandler(
                                (request, response, authentication) -> {

                                    boolean administrator =
                                            authentication
                                                    .getAuthorities()
                                                    .stream()
                                                    .anyMatch(authority ->
                                                            "ROLE_ADMIN"
                                                                    .equals(
                                                                            authority
                                                                                    .getAuthority()
                                                                    )
                                                                    ||
                                                            "ROLE_SUPER_ADMIN"
                                                                    .equals(
                                                                            authority
                                                                                    .getAuthority()
                                                                    )
                                                    );


                                    // ------------------------------------
                                    // ADMIN LOGIN
                                    // ------------------------------------

                                    if (administrator) {

                                        response.sendRedirect(
                                                "/admin"
                                        );

                                        return;
                                    }


                                    // ------------------------------------
                                    // PARTICIPANT LOGIN
                                    // ------------------------------------

                                    var user =
                                            users.findByEmailIgnoreCase(
                                                    authentication.getName()
                                            ).orElse(null);


                                    if (user == null) {

                                        response.sendRedirect(
                                                "/login?error=true"
                                        );

                                        return;
                                    }


                                    var registration =
                                            registrations
                                                    .findByUser(user)
                                                    .orElse(null);


                                    boolean paid =
                                            registration != null
                                                    &&
                                            payments
                                                    .findTopByRegistrationAndStatusOrderByCreatedAtDesc(
                                                            registration,
                                                            PaymentStatus.PAID
                                                    )
                                                    .isPresent();


                                    // ------------------------------------
                                    // PAYMENT NOT COMPLETED
                                    // ------------------------------------

                                    if (!paid) {

                                        // Remove authenticated context
                                        SecurityContextHolder
                                                .clearContext();


                                        // Create fresh payment session
                                        HttpSession oldSession =
                                                request.getSession(false);


                                        if (oldSession != null) {
                                            oldSession.invalidate();
                                        }


                                        HttpSession paymentSession =
                                                request.getSession(true);


                                        paymentSession.setAttribute(
                                                "MITRAA_PENDING_PAYMENT_EMAIL",
                                                user.getEmail()
                                        );


                                        paymentSession.setAttribute(
                                                "MITRAA_PENDING_PAYMENT_AT",
                                                java.time.Instant.now()
                                        );


                                        response.sendRedirect(
                                                "/login?paymentRequired=true"
                                        );

                                        return;
                                    }


                                    // ------------------------------------
                                    // PAYMENT COMPLETED
                                    // ------------------------------------

                                    response.sendRedirect(
                                            "/dashboard"
                                    );
                                }
                        )

                        .failureUrl(
                                "/login?error=true"
                        )

                        .permitAll()
                )


                // =========================================================
                // LOGOUT
                // =========================================================

                .logout(logout -> logout

                        .logoutUrl("/api/auth/logout")

                        .logoutSuccessUrl("/")

                        .invalidateHttpSession(true)

                        .deleteCookies("JSESSIONID")
                )


                // =========================================================
                // SESSION MANAGEMENT
                // =========================================================

                .sessionManagement(session -> session

                        .sessionFixation(fixation ->
                                fixation.migrateSession()
                        )
                )


                // =========================================================
                // SECURITY HEADERS
                // =========================================================

                .headers(headers -> headers

                        .contentTypeOptions(
                                contentType -> {}
                        )

                        .frameOptions(frame ->
                                frame.deny()
                        )

                        .referrerPolicy(referrer ->
                                referrer.policy(
                                        ReferrerPolicyHeaderWriter
                                                .ReferrerPolicy
                                                .STRICT_ORIGIN_WHEN_CROSS_ORIGIN
                                )
                        )

                        .permissionsPolicy(permissions ->
                                permissions.policy(
                                        "camera=(), microphone=(), " +
                                        "geolocation=(), payment=(self)"
                                )
                        )
                )


                // =========================================================
                // RATE LIMITING
                // =========================================================

                .addFilterBefore(
                        new AuthenticationRateLimitFilter(rateLimits),
                        UsernamePasswordAuthenticationFilter.class
                );


        // =============================================================
        // GOOGLE / GITHUB OAUTH2
        // =============================================================

        if (clientRegistrations.getIfAvailable() != null) {

            http.oauth2Login(oauth -> oauth

                    .loginPage("/login")

                    .userInfoEndpoint(userInfo ->
                            userInfo.userService(oauth2UserService)
                    )

                    .successHandler(oauthSuccessHandler)

                    .failureUrl(
                            "/login?ssoError=true"
                    )
            );
        }


        return http.build();
    }
}