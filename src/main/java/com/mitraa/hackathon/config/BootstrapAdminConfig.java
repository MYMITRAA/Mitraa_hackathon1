package com.mitraa.hackathon.config;

import com.mitraa.hackathon.user.Role;
import com.mitraa.hackathon.user.User;
import com.mitraa.hackathon.user.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Instant;

@Configuration
public class BootstrapAdminConfig {

    @Bean
    CommandLineRunner bootstrapAdmin(
            UserRepository users,
            PasswordEncoder encoder,
            @Value("${app.bootstrap-admin.enabled:false}")
            boolean enabled,
            @Value("${app.bootstrap-admin.email:}")
            String email,
            @Value("${app.bootstrap-admin.password:}")
            String password
    ) {
        return args -> {
            if (enabled && (email.isBlank() || password.length() < 12)) {
                throw new IllegalStateException(
                        "Bootstrap admin requires a valid email and a password of at least 12 characters."
                );
            }
            if (enabled && !users.existsByEmailIgnoreCase(email)) {
                User user = new User();

                user.setFullName("MiTRAA Super Admin");
                user.setEmail(email.trim().toLowerCase());
                user.setPasswordHash(encoder.encode(password));
                user.setRole(Role.SUPER_ADMIN);
                user.setEnabled(true);
                user.setEmailVerifiedAt(Instant.now());

                users.save(user);
            }
        };
    }
}
