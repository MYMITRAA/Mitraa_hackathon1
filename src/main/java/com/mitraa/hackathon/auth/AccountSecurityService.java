package com.mitraa.hackathon.auth;

import com.mitraa.hackathon.notification.NotificationService;
import com.mitraa.hackathon.user.User;
import com.mitraa.hackathon.user.UserRepository;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;

@Service
public class AccountSecurityService {

    private static final String VERIFY = "EMAIL_VERIFY";
    private static final String RESET = "PASSWORD_RESET";

    private static final int MAX_ATTEMPTS = 5;
    private static final long OTP_EXPIRY_MINUTES = 10;

    private final UserRepository users;
    private final AccountCodeRepository codes;
    private final PasswordEncoder encoder;
    private final NotificationService notifications;

    private final SecureRandom random = new SecureRandom();

    public AccountSecurityService(
            UserRepository users,
            AccountCodeRepository codes,
            PasswordEncoder encoder,
            NotificationService notifications) {

        this.users = users;
        this.codes = codes;
        this.encoder = encoder;
        this.notifications = notifications;
    }

    // ============================================================
    // SEND EMAIL VERIFICATION OTP
    // ============================================================

    @Transactional
    public void sendVerification(User user) {

        if (user == null) {
            throw new IllegalArgumentException("User cannot be null.");
        }

        if (user.getEmail() == null || user.getEmail().isBlank()) {
            throw new IllegalArgumentException(
                    "Email address is required for verification."
            );
        }

        if (user.getEmailVerifiedAt() != null) {
            return;
        }

        issue(user, VERIFY);
    }

    // ============================================================
    // RESEND EMAIL VERIFICATION OTP
    // ============================================================

    @Transactional
    public void resendVerification(String email) {

        String normalizedEmail = normalize(email);

        if (normalizedEmail.isBlank()) {
            throw new IllegalArgumentException(
                    "Email address is required."
            );
        }

        users.findByEmailIgnoreCase(normalizedEmail)
                .filter(user -> user.getEmailVerifiedAt() == null)
                .ifPresent(user -> issue(user, VERIFY));
    }

    // ============================================================
    // VERIFY EMAIL OTP
    // ============================================================

    @Transactional(noRollbackFor = IllegalArgumentException.class)
    public void verifyEmail(String email, String otp) {

        String normalizedEmail = normalize(email);

        if (normalizedEmail.isBlank()) {
            throw new IllegalArgumentException(
                    "Invalid or expired verification code."
            );
        }

        if (otp == null || otp.trim().isBlank()) {
            throw new IllegalArgumentException(
                    "Please enter the 6-digit OTP."
            );
        }

        User user = users
                .findByEmailIgnoreCase(normalizedEmail)
                .orElseThrow(() ->
                        new IllegalArgumentException(
                                "Invalid or expired verification code."
                        )
                );

        if (user.getEmailVerifiedAt() != null) {
            throw new IllegalArgumentException(
                    "Email is already verified."
            );
        }

        consume(user, VERIFY, otp);

        user.setEmailVerifiedAt(Instant.now());
        user.setEnabled(true);

        users.save(user);

        notifications.queueWelcome(user);
    }

    // ============================================================
    // REQUEST PASSWORD RESET
    // ============================================================

    @Transactional
    public void requestPasswordReset(String email) {

        String normalizedEmail = normalize(email);

        if (normalizedEmail.isBlank()) {
            return;
        }

        users.findByEmailIgnoreCase(normalizedEmail)
                .ifPresent(user -> issue(user, RESET));
    }

    // ============================================================
    // RESET PASSWORD
    // ============================================================

    @Transactional(noRollbackFor = IllegalArgumentException.class)
    public void resetPassword(
            String email,
            String otp,
            String password) {

        if (password == null
                || password.length() < 8
                || password.length() > 12) {

            throw new IllegalArgumentException(
                    "Password must contain 8–12 characters."
            );
        }

        String normalizedEmail = normalize(email);

        User user = users
                .findByEmailIgnoreCase(normalizedEmail)
                .orElseThrow(() ->
                        new IllegalArgumentException(
                                "Invalid or expired reset code."
                        )
                );

        consume(user, RESET, otp);

        user.setPasswordHash(
                encoder.encode(password)
        );

        users.save(user);

        notifications.queuePasswordChanged(user);
    }

    // ============================================================
    // GENERATE AND SAVE OTP
    // ============================================================

    private void issue(User user, String purpose) {

        /*
         * When a new OTP is generated, invalidate the previous
         * unconsumed OTP so only the latest OTP can be used.
         */
        codes.findTopByUserAndPurposeAndConsumedAtIsNullOrderByCreatedAtDesc(
                user,
                purpose
        ).ifPresent(previousCode -> {

            previousCode.setConsumedAt(Instant.now());
            codes.save(previousCode);
        });

        /*
         * Generate a 6-digit OTP.
         */
        String otp = String.format(
                "%06d",
                random.nextInt(1_000_000)
        );

        /*
         * Create account code record.
         */
        AccountCode accountCode = new AccountCode();

        accountCode.setUser(user);
        accountCode.setPurpose(purpose);

        /*
         * Store only the hashed OTP.
         */
        accountCode.setCodeHash(
                encoder.encode(otp)
        );

        /*
         * OTP is valid for 10 minutes from generation.
         */
        accountCode.setExpiresAt(
                Instant.now().plus(
                        Duration.ofMinutes(OTP_EXPIRY_MINUTES)
                )
        );

        accountCode.setAttempts(0);

        codes.save(accountCode);

        /*
         * Queue email containing the actual OTP.
         */
        notifications.queueAccountCode(
                user,
                otp,
                purpose
        );
    }

    // ============================================================
    // VERIFY OTP
    // ============================================================

    private void consume(
            User user,
            String purpose,
            String enteredOtp) {

        AccountCode accountCode =
                codes.findTopByUserAndPurposeAndConsumedAtIsNullOrderByCreatedAtDesc(
                        user,
                        purpose
                ).orElseThrow(() ->
                        new IllegalArgumentException(
                                "Invalid or expired code."
                        )
                );

        Instant now = Instant.now();

        /*
         * Check whether OTP has expired.
         */
        if (accountCode.getExpiresAt() == null
                || accountCode.getExpiresAt().isBefore(now)) {

            throw new IllegalArgumentException(
                    "OTP has expired. Please request a new code."
            );
        }

        /*
         * Check maximum attempts.
         */
        if (accountCode.getAttempts() >= MAX_ATTEMPTS) {

            throw new IllegalArgumentException(
                    "Too many attempts. Please request a new code."
            );
        }

        /*
         * Count this attempt.
         */
        accountCode.setAttempts(
                accountCode.getAttempts() + 1
        );

        String cleanedOtp =
                enteredOtp == null
                        ? ""
                        : enteredOtp.trim();

        /*
         * OTP must contain exactly 6 digits.
         */
        if (!cleanedOtp.matches("\\d{6}")) {

            codes.save(accountCode);

            throw new IllegalArgumentException(
                    "Invalid OTP. Please enter the 6-digit code."
            );
        }

        /*
         * Compare entered OTP with stored hash.
         */
        if (!encoder.matches(
                cleanedOtp,
                accountCode.getCodeHash()
        )) {

            codes.save(accountCode);

            throw new IllegalArgumentException(
                    "Invalid OTP. Please check the code and try again."
            );
        }

        /*
         * OTP is correct.
         * Mark it as consumed so it cannot be used again.
         */
        accountCode.setConsumedAt(now);

        codes.save(accountCode);
    }

    // ============================================================
    // NORMALIZE EMAIL
    // ============================================================

    private String normalize(String email) {

        if (email == null) {
            return "";
        }

        return email
                .trim()
                .toLowerCase(Locale.ROOT);
    }
}
