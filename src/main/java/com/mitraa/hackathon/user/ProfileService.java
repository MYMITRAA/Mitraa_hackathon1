package com.mitraa.hackathon.user;

import com.mitraa.hackathon.auth.AccountCode;
import com.mitraa.hackathon.auth.AccountCodeRepository;
import com.mitraa.hackathon.notification.NotificationService;
import com.mitraa.hackathon.registration.Registration;
import com.mitraa.hackathon.registration.RegistrationRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;

@Service
public class ProfileService {

    private static final String EMAIL_CHANGE = "EMAIL_CHANGE";
    private static final int MAX_ATTEMPTS = 5;
    private static final Duration OTP_EXPIRY = Duration.ofMinutes(10);

    private final UserRepository users;
    private final RegistrationRepository registrations;
    private final AccountCodeRepository codes;
    private final PasswordEncoder encoder;
    private final NotificationService notifications;
    private final SecureRandom random = new SecureRandom();

    public ProfileService(UserRepository users,
                          RegistrationRepository registrations,
                          AccountCodeRepository codes,
                          PasswordEncoder encoder,
                          NotificationService notifications) {
        this.users = users;
        this.registrations = registrations;
        this.codes = codes;
        this.encoder = encoder;
        this.notifications = notifications;
    }

    @Transactional(readOnly = true)
    public ProfileView getProfile(String authenticatedEmail) {
        User user = findUser(authenticatedEmail);
        Registration registration = registrations.findByUser(user).orElse(null);
        return view(user, registration);
    }

    @Transactional
    public ProfileView updateProfile(String authenticatedEmail, ProfileUpdateRequest request) {
        User user = findUser(authenticatedEmail);
        Registration registration = registrations.findByUser(user)
                .orElseThrow(() -> new IllegalArgumentException("Registration was not found for this account."));

        String fullName = request.fullName().trim();
        String requestedEmail = normalizeEmail(request.email());
        String phone = normalizePhone(request.phone());

        user.setFullName(fullName);
        registration.setPhone(phone);

        if (!requestedEmail.equalsIgnoreCase(user.getEmail())) {
            users.findByEmailIgnoreCase(requestedEmail).ifPresent(existing -> {
                if (!existing.getId().equals(user.getId())) {
                    throw new IllegalArgumentException("This email address is already registered.");
                }
            });

            user.setPendingEmail(requestedEmail);
            user.setPendingEmailRequestedAt(Instant.now());
            issueEmailChangeCode(user, requestedEmail);
        } else {
            user.setPendingEmail(null);
            user.setPendingEmailRequestedAt(null);
            invalidateUnusedEmailChangeCode(user);
        }

        users.save(user);
        registrations.save(registration);
        return view(user, registration);
    }

    @Transactional
    public ProfileView resendEmailChangeCode(String authenticatedEmail) {
        User user = findUser(authenticatedEmail);
        if (user.getPendingEmail() == null || user.getPendingEmail().isBlank()) {
            throw new IllegalArgumentException("Enter and save a new email address first.");
        }
        user.setPendingEmailRequestedAt(Instant.now());
        issueEmailChangeCode(user, user.getPendingEmail());
        return view(user, registrations.findByUser(user).orElse(null));
    }

    @Transactional(noRollbackFor = IllegalArgumentException.class)
    public ProfileView verifyEmailChange(String authenticatedEmail, String otp) {
        User user = findUser(authenticatedEmail);
        String pendingEmail = normalizeEmail(user.getPendingEmail());
        if (pendingEmail.isBlank()) {
            throw new IllegalArgumentException("No email change is waiting for verification.");
        }

        users.findByEmailIgnoreCase(pendingEmail).ifPresent(existing -> {
            if (!existing.getId().equals(user.getId())) {
                throw new IllegalArgumentException("This email address is already registered.");
            }
        });

        consume(user, otp);
        user.setEmail(pendingEmail);
        user.setEmailVerifiedAt(Instant.now());
        user.setPendingEmail(null);
        user.setPendingEmailRequestedAt(null);
        users.save(user);
        notifications.queueProfileEmailChanged(user);
        return view(user, registrations.findByUser(user).orElse(null));
    }

    private void issueEmailChangeCode(User user, String destinationEmail) {
        invalidateUnusedEmailChangeCode(user);
        String otp = String.format("%06d", random.nextInt(1_000_000));
        AccountCode code = new AccountCode();
        code.setUser(user);
        code.setPurpose(EMAIL_CHANGE);
        code.setCodeHash(encoder.encode(otp));
        code.setExpiresAt(Instant.now().plus(OTP_EXPIRY));
        code.setAttempts(0);
        codes.save(code);
        notifications.queueProfileEmailCode(user, destinationEmail, otp);
    }

    private void invalidateUnusedEmailChangeCode(User user) {
        codes.findTopByUserAndPurposeAndConsumedAtIsNullOrderByCreatedAtDesc(user, EMAIL_CHANGE)
                .ifPresent(code -> {
                    code.setConsumedAt(Instant.now());
                    codes.save(code);
                });
    }

    private void consume(User user, String enteredOtp) {
        AccountCode code = codes.findTopByUserAndPurposeAndConsumedAtIsNullOrderByCreatedAtDesc(user, EMAIL_CHANGE)
                .orElseThrow(() -> new IllegalArgumentException("Invalid or expired verification code."));
        Instant now = Instant.now();
        if (code.getExpiresAt() == null || code.getExpiresAt().isBefore(now)) {
            throw new IllegalArgumentException("OTP has expired. Please request a new code.");
        }
        if (code.getAttempts() >= MAX_ATTEMPTS) {
            throw new IllegalArgumentException("Too many attempts. Please request a new code.");
        }
        code.setAttempts(code.getAttempts() + 1);
        String cleaned = enteredOtp == null ? "" : enteredOtp.trim();
        if (!cleaned.matches("\\d{6}") || !encoder.matches(cleaned, code.getCodeHash())) {
            codes.save(code);
            throw new IllegalArgumentException("Invalid OTP. Please check the code and try again.");
        }
        code.setConsumedAt(now);
        codes.save(code);
    }

    private User findUser(String email) {
        return users.findByEmailIgnoreCase(normalizeEmail(email))
                .orElseThrow(() -> new IllegalArgumentException("Account was not found."));
    }

    private String normalizeEmail(String email) {
        return email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
    }

    private String normalizePhone(String phone) {
        String value = phone == null ? "" : phone.trim().replaceAll("[\\s()-]", "");
        if (!value.matches("^\\+?[0-9]{6,15}$")) {
            throw new IllegalArgumentException("Phone number must contain 6–15 digits and may start with +.");
        }
        return value;
    }

    private ProfileView view(User user, Registration registration) {
        return new ProfileView(
                user.getFullName(),
                user.getEmail(),
                registration == null ? "" : registration.getPhone(),
                user.getEmailVerifiedAt() != null,
                user.getPendingEmail(),
                user.getPendingEmail() != null && !user.getPendingEmail().isBlank()
        );
    }

    public record ProfileView(
            String fullName,
            String email,
            String phone,
            boolean emailVerified,
            String pendingEmail,
            boolean emailVerificationPending) {
    }
}
