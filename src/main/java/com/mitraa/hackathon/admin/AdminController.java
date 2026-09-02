package com.mitraa.hackathon.admin;

import com.mitraa.hackathon.guardian.GuardianConsent;
import com.mitraa.hackathon.guardian.GuardianConsentRepository;
import com.mitraa.hackathon.notification.NotificationOutbox;
import com.mitraa.hackathon.notification.NotificationOutboxRepository;
import com.mitraa.hackathon.notification.NotificationStatus;
import com.mitraa.hackathon.payment.Payment;
import com.mitraa.hackathon.payment.PaymentRepository;
import com.mitraa.hackathon.payment.PaymentStatus;
import com.mitraa.hackathon.registration.ParticipationType;
import com.mitraa.hackathon.registration.Registration;
import com.mitraa.hackathon.registration.RegistrationRepository;
import com.mitraa.hackathon.registration.RegistrationStatus;
import com.mitraa.hackathon.submission.Submission;
import com.mitraa.hackathon.submission.SubmissionRepository;
import com.mitraa.hackathon.submission.SubmissionStatus;
import com.mitraa.hackathon.team.Team;
import com.mitraa.hackathon.team.TeamRepository;
import com.mitraa.hackathon.user.Role;
import com.mitraa.hackathon.user.User;
import com.mitraa.hackathon.user.UserRepository;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.time.LocalDate;
import java.time.Period;
import java.time.ZoneOffset;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.NoSuchElementException;

@RestController
@RequestMapping("/api/admin")
public class AdminController {

    private final UserRepository users;
    private final RegistrationRepository registrations;
    private final PaymentRepository payments;
    private final NotificationOutboxRepository notifications;
    private final TeamRepository teams;
    private final SubmissionRepository submissions;
    private final GuardianConsentRepository guardianConsents;
    private final PasswordEncoder encoder;

    public AdminController(
            UserRepository users,
            RegistrationRepository registrations,
            PaymentRepository payments,
            NotificationOutboxRepository notifications,
            TeamRepository teams,
            SubmissionRepository submissions,
            GuardianConsentRepository guardianConsents,
            PasswordEncoder encoder
    ) {
        this.users = users;
        this.registrations = registrations;
        this.payments = payments;
        this.notifications = notifications;
        this.teams = teams;
        this.submissions = submissions;
        this.guardianConsents = guardianConsents;
        this.encoder = encoder;
    }

    /*
     * ============================================================
     * DASHBOARD METRICS
     * ============================================================
     */

    @GetMapping("/metrics")
    @Transactional(readOnly = true)
    public Map<String, Object> metrics() {

        long inrRevenue = payments.findAll()
                .stream()
                .filter(payment ->
                        payment.getStatus() == PaymentStatus.PAID
                                && "INR".equals(
                                        payment.getCurrency()
                                )
                )
                .mapToLong(Payment::getAmountMinor)
                .sum();

        long usdRevenue = payments.findAll()
                .stream()
                .filter(payment ->
                        payment.getStatus() == PaymentStatus.PAID
                                && "USD".equals(
                                        payment.getCurrency()
                                )
                )
                .mapToLong(Payment::getAmountMinor)
                .sum();

        long teamMemberCount = teams.findAll()
                .stream()
                .mapToLong(team ->
                        team.getMembers().size() + 1L
                )
                .sum();

        Map<String, Object> metrics =
                new LinkedHashMap<>();

        metrics.put(
                "participants",
                users.countByRole(Role.PARTICIPANT)
        );

        metrics.put(
                "registrations",
                registrations.count()
        );

        metrics.put(
                "individual",
                registrations.countByParticipationType(
                        ParticipationType.INDIVIDUAL
                )
        );

        metrics.put(
                "teamEntries",
                registrations.countByParticipationType(
                        ParticipationType.TEAM
                )
        );

        metrics.put(
                "teams",
                teams.count()
        );

        metrics.put(
                "teamMembers",
                teamMemberCount
        );

        metrics.put(
                "confirmed",
                registrations.countByStatus(
                        RegistrationStatus.CONFIRMED
                )
        );

        metrics.put(
                "pendingPayment",
                registrations.countByStatus(
                        RegistrationStatus.PENDING_PAYMENT
                )
        );

        metrics.put(
                "revenueInr",
                inrRevenue / 100.0
        );

        metrics.put(
                "revenueUsd",
                usdRevenue / 100.0
        );

        metrics.put(
                "pocDrafts",
                submissions.countByStatus(
                        SubmissionStatus.DRAFT
                )
        );

        metrics.put(
                "pocSubmitted",
                submissions.countByStatus(
                        SubmissionStatus.SUBMITTED
                )
        );

        metrics.put(
                "notificationsPending",
                notifications.countByStatus(
                        NotificationStatus.PENDING
                )
        );

        metrics.put(
                "notificationsFailed",
                notifications.countByStatus(
                        NotificationStatus.FAILED
                )
        );

        return metrics;
    }

    /*
     * ============================================================
     * USERS
     * ============================================================
     */

    @GetMapping("/users")
    @Transactional(readOnly = true)
    public List<Map<String, Object>> allUsers() {

        return users.findAll()
                .stream()
                .sorted(
                        Comparator.comparing(
                                User::getCreatedAt
                        ).reversed()
                )
                .map(this::userView)
                .toList();
    }

    @PostMapping("/users")
    @ResponseStatus(HttpStatus.CREATED)
    @Transactional
    public Map<String, Object> createUser(
            @Valid
            @RequestBody
            CreateUser body,

            Authentication authentication
    ) {
        protectAdministratorRole(
                body.role(),
                authentication
        );

        String normalizedEmail =
                body.email()
                        .trim()
                        .toLowerCase(Locale.ROOT);

        if (
                users.existsByEmailIgnoreCase(
                        normalizedEmail
                )
        ) {
            throw new IllegalArgumentException(
                    "Email is already registered."
            );
        }

        User user = new User();

        user.setFullName(
                body.fullName().trim()
        );

        user.setEmail(normalizedEmail);

        user.setPasswordHash(
                encoder.encode(body.password())
        );

        user.setRole(body.role());
        user.setEnabled(body.enabled());

        if (body.emailVerified()) {
            user.setEmailVerifiedAt(
                    Instant.now()
            );
        } else {
            user.setEmailVerifiedAt(null);
        }

        return userView(
                users.save(user)
        );
    }

    @PutMapping("/users/{id}")
    @Transactional
    public Map<String, Object> updateUser(
            @PathVariable
            Long id,

            @Valid
            @RequestBody
            UpdateUser body,

            Authentication authentication
    ) {
        User user = users.findById(id)
                .orElseThrow(() ->
                        new NoSuchElementException(
                                "User not found."
                        )
                );

        /*
         * A normal Administrator cannot edit another Administrator
         * or a Super Administrator.
         */
        if (
                isAdministratorRole(user.getRole())
                        && !isSuperAdmin(authentication)
        ) {
            throw new IllegalArgumentException(
                    "Only a Super Administrator can edit "
                            + "an Administrator account."
            );
        }

        /*
         * Only Super Administrators can assign ADMIN or
         * SUPER_ADMIN roles.
         */
        protectAdministratorRole(
                body.role(),
                authentication
        );

        /*
         * Prevent changing the final Super Administrator account
         * to a lower role.
         */
        if (
                user.getRole() == Role.SUPER_ADMIN
                        && body.role()
                        != Role.SUPER_ADMIN
        ) {
            long superAdminCount = users.findAll()
                    .stream()
                    .filter(existingUser ->
                            existingUser.getRole()
                                    == Role.SUPER_ADMIN
                    )
                    .count();

            if (superAdminCount <= 1) {
                throw new IllegalArgumentException(
                        "The final Super Administrator "
                                + "cannot be demoted."
                );
            }
        }

        String normalizedEmail =
                body.email()
                        .trim()
                        .toLowerCase(Locale.ROOT);

        users.findByEmailIgnoreCase(
                        normalizedEmail
                )
                .filter(existing ->
                        !existing.getId().equals(id)
                )
                .ifPresent(existing -> {
                    throw new IllegalArgumentException(
                            "Email is already registered."
                    );
                });

        user.setFullName(
                body.fullName().trim()
        );

        user.setEmail(normalizedEmail);
        user.setRole(body.role());
        user.setEnabled(body.enabled());

        if (
                body.emailVerified()
                        && user.getEmailVerifiedAt() == null
        ) {
            user.setEmailVerifiedAt(
                    Instant.now()
            );
        }

        if (!body.emailVerified()) {
            user.setEmailVerifiedAt(null);
        }

        if (
                body.newPassword() != null
                        && !body.newPassword().isBlank()
        ) {
            user.setPasswordHash(
                    encoder.encode(
                            body.newPassword()
                    )
            );
        }

        return userView(
                users.save(user)
        );
    }

    @DeleteMapping("/users/{id}")
    @Transactional
    public Map<String, String> deactivateUser(
            @PathVariable
            Long id,

            Authentication authentication
    ) {
        User user = users.findById(id)
                .orElseThrow(() ->
                        new NoSuchElementException(
                                "User not found."
                        )
                );

        if (
                authentication != null
                        && user.getEmail()
                        .equalsIgnoreCase(
                                authentication.getName()
                        )
        ) {
            throw new IllegalArgumentException(
                    "You cannot deactivate your "
                            + "own active session."
            );
        }

        if (
                isAdministratorRole(user.getRole())
                        && !isSuperAdmin(authentication)
        ) {
            throw new IllegalArgumentException(
                    "Only a Super Administrator can "
                            + "deactivate an Administrator account."
            );
        }

        if (user.getRole() == Role.SUPER_ADMIN) {
            throw new IllegalArgumentException(
                    "A Super Administrator cannot be disabled "
                            + "from the user table."
            );
        }

        user.setEnabled(false);
        users.save(user);

        return Map.of(
                "message",
                "User access deactivated. Records are retained "
                        + "for payment and legal audit."
        );
    }

    /*
     * ============================================================
     * ADMINISTRATOR MANAGEMENT
     * ============================================================
     */

    @GetMapping("/administrators")
    @Transactional(readOnly = true)
    public List<Map<String, Object>> administrators(
            Authentication authentication
    ) {
        requireSuperAdmin(authentication);

        return users.findAll()
                .stream()
                .filter(user ->
                        user.getRole() == Role.ADMIN
                                || user.getRole()
                                == Role.SUPER_ADMIN
                )
                .sorted(
                        Comparator.comparing(
                                User::getCreatedAt
                        ).reversed()
                )
                .map(this::userView)
                .toList();
    }

    @PostMapping("/administrators")
    @ResponseStatus(HttpStatus.CREATED)
    @Transactional
    public Map<String, Object> addAdministrator(
            @Valid
            @RequestBody
            AdministratorRequest body,

            Authentication authentication
    ) {
        requireSuperAdmin(authentication);

        String normalizedEmail =
                body.email()
                        .trim()
                        .toLowerCase(Locale.ROOT);

        User user = users
                .findByEmailIgnoreCase(
                        normalizedEmail
                )
                .orElseThrow(() ->
                        new IllegalArgumentException(
                                "No registered user was found "
                                        + "for this email address."
                        )
                );

        if (
                user.getRole() == Role.SUPER_ADMIN
        ) {
            throw new IllegalArgumentException(
                    "This account is already a "
                            + "Super Administrator."
            );
        }

        if (
                user.getRole() == Role.ADMIN
        ) {
            throw new IllegalArgumentException(
                    "This account is already an Administrator."
            );
        }

        user.setRole(Role.ADMIN);
        user.setEnabled(true);

        return userView(
                users.save(user)
        );
    }

    @DeleteMapping("/administrators/{id}")
    @Transactional
    public Map<String, String> removeAdministrator(
            @PathVariable
            Long id,

            Authentication authentication
    ) {
        requireSuperAdmin(authentication);

        User user = users.findById(id)
                .orElseThrow(() ->
                        new NoSuchElementException(
                                "Administrator not found."
                        )
                );

        if (
                user.getRole()
                        == Role.SUPER_ADMIN
        ) {
            throw new IllegalArgumentException(
                    "Super Administrator access cannot "
                            + "be removed from this table."
            );
        }

        if (
                user.getRole() != Role.ADMIN
        ) {
            throw new IllegalArgumentException(
                    "This user does not currently have "
                            + "Administrator access."
            );
        }

        if (
                authentication != null
                        && user.getEmail()
                        .equalsIgnoreCase(
                                authentication.getName()
                        )
        ) {
            throw new IllegalArgumentException(
                    "You cannot remove your own "
                            + "Administrator access."
            );
        }

        /*
         * Removing an Administrator does not delete their account.
         * It safely returns them to PARTICIPANT role.
         */
        user.setRole(Role.PARTICIPANT);
        user.setEnabled(true);

        users.save(user);

        return Map.of(
                "message",
                "Administrator access removed. "
                        + "The account and audit records "
                        + "were retained."
        );
    }

    /*
     * ============================================================
     * PARTICIPANTS
     * ============================================================
     */

    @GetMapping("/participants")
    @Transactional(readOnly = true)
    public List<Map<String, Object>> participants() {

        return registrations.findAll()
                .stream()
                .map(registration -> {

                    Map<String, Object> item =
                            new LinkedHashMap<>();

                    item.put(
                            "registrationId",
                            registration
                                    .getRegistrationCode()
                    );

                    item.put(
                            "name",
                            registration
                                    .getUser()
                                    .getFullName()
                    );

                    item.put(
                            "email",
                            registration
                                    .getUser()
                                    .getEmail()
                    );

                    item.put(
                            "country",
                            registration.getCountry()
                    );

                    item.put(
                            "type",
                            registration
                                    .getParticipationType()
                                    .name()
                    );

                    item.put(
                            "domain",
                            registration.getDomain()
                    );

                    item.put(
                            "verification",
                            registration
                                    .getAgeVerificationStatus()
                    );

                    item.put(
                            "status",
                            registration
                                    .getStatus()
                                    .name()
                    );

                    return item;
                })
                .toList();
    }

    /*
     * ============================================================
     * REGISTRATIONS
     * ============================================================
     */

    @GetMapping("/registrations")
    @Transactional(readOnly = true)
    public List<Map<String, Object>> registrationDetails() {

        return registrations.findAll()
                .stream()
                .sorted(
                        Comparator.comparing(
                                Registration::getCreatedAt
                        ).reversed()
                )
                .map(registration -> {

                    Map<String, Object> item =
                            new LinkedHashMap<>();

                    item.put(
                            "registrationId",
                            registration
                                    .getRegistrationCode()
                    );

                    item.put(
                            "name",
                            registration
                                    .getUser()
                                    .getFullName()
                    );

                    item.put(
                            "email",
                            registration
                                    .getUser()
                                    .getEmail()
                    );

                    item.put(
                            "emailVerified",
                            registration
                                    .getUser()
                                    .getEmailVerifiedAt()
                                    != null
                    );

                    item.put(
                            "dateOfBirth",
                            registration.getDateOfBirth()
                    );

                    item.put(
                            "age",
                            calculateAge(
                                    registration
                                            .getDateOfBirth()
                            )
                    );

                    item.put(
                            "phone",
                            registration.getPhone()
                    );

                    item.put(
                            "country",
                            registration.getCountry()
                    );

                    item.put(
                            "city",
                            registration.getCity()
                    );

                    item.put(
                            "type",
                            registration
                                    .getParticipationType()
                                    .name()
                    );

                    item.put(
                            "domain",
                            registration.getDomain()
                    );

                    item.put(
                            "eligibility",
                            registration
                                    .getAgeVerificationStatus()
                    );

                    item.put(
                            "status",
                            registration
                                    .getStatus()
                                    .name()
                    );

                    item.put(
                            "guardianRequired",
                            registration
                                    .isGuardianConsentRequired()
                    );

                    item.put(
                            "guardianReceived",
                            registration
                                    .isGuardianConsentReceived()
                    );

                    item.put(
                            "guardianName",
                            value(
                                    registration
                                            .getGuardianName()
                            )
                    );

                    item.put(
                            "guardianRelationship",
                            value(
                                    registration
                                            .getGuardianRelationship()
                            )
                    );

                    item.put(
                            "guardianEmail",
                            value(
                                    registration
                                            .getGuardianEmail()
                            )
                    );

                    item.put(
                            "guardianPhone",
                            value(
                                    registration
                                            .getGuardianPhone()
                            )
                    );

                    item.put(
                            "guardianCountry",
                            value(
                                    registration
                                            .getGuardianCountry()
                            )
                    );

                    item.put(
                            "termsAcceptedAt",
                            registration
                                    .getTermsAcceptedAt()
                    );

                    item.put(
                            "privacyAcceptedAt",
                            registration
                                    .getPrivacyAcceptedAt()
                    );

                    item.put(
                            "rulesAcceptedAt",
                            registration
                                    .getRulesAcceptedAt()
                    );

                    item.put(
                            "marketingConsent",
                            registration
                                    .isMarketingConsent()
                    );

                    item.put(
                            "createdAt",
                            registration.getCreatedAt()
                    );

                    item.put(
                            "activatedAt",
                            registration.getActivatedAt()
                    );

                    return item;
                })
                .toList();
    }

    /*
     * ============================================================
     * TEAMS
     * ============================================================
     */

    @GetMapping("/teams")
    @Transactional(readOnly = true)
    public List<Map<String, Object>> teamDetails() {

        return teams.findAll()
                .stream()
                .sorted(
                        Comparator.comparing(
                                Team::getCreatedAt
                        ).reversed()
                )
                .map(team -> {

                    Map<String, Object> item =
                            new LinkedHashMap<>();

                    item.put(
                            "id",
                            team.getId()
                    );

                    item.put(
                            "teamName",
                            team.getName()
                    );

                    item.put(
                            "teamCode",
                            team.getCode()
                    );

                    item.put(
                            "registrationId",
                            team.getRegistration()
                                    .getRegistrationCode()
                    );

                    item.put(
                            "leader",
                            team.getRegistration()
                                    .getUser()
                                    .getFullName()
                    );

                    item.put(
                            "leaderEmail",
                            team.getRegistration()
                                    .getUser()
                                    .getEmail()
                    );

                    item.put(
                            "status",
                            team.getRegistration()
                                    .getStatus()
                                    .name()
                    );

                    item.put(
                            "memberCount",
                            team.getMembers().size() + 1
                    );

                    item.put(
                            "createdAt",
                            team.getCreatedAt()
                    );

                    item.put(
                            "members",
                            team.getMembers()
                                    .stream()
                                    .map(member -> {

                                        Map<String, Object>
                                                memberView =
                                                new LinkedHashMap<>();

                                        memberView.put(
                                                "id",
                                                member.getId()
                                        );

                                        memberView.put(
                                                "name",
                                                member.getFullName()
                                        );

                                        memberView.put(
                                                "email",
                                                member.getEmail()
                                        );

                                        memberView.put(
                                                "dateOfBirth",
                                                member.getDateOfBirth()
                                        );

                                        memberView.put(
                                                "age",
                                                calculateAge(
                                                        member
                                                                .getDateOfBirth()
                                                )
                                        );

                                        memberView.put(
                                                "country",
                                                member.getCountry()
                                        );

                                        memberView.put(
                                                "guardianConsent",
                                                member
                                                        .isGuardianConsent()
                                        );

                                        return memberView;
                                    })
                                    .toList()
                    );

                    return item;
                })
                .toList();
    }

    /*
     * ============================================================
     * PAYMENTS
     * ============================================================
     */

    @GetMapping("/payments")
    @Transactional(readOnly = true)
    public List<Map<String, Object>> paymentDetails() {

        return payments.findAll()
                .stream()
                .sorted(
                        Comparator.comparing(
                                Payment::getCreatedAt
                        ).reversed()
                )
                .map(payment -> {

                    Map<String, Object> item =
                            new LinkedHashMap<>();

                    item.put(
                            "id",
                            payment.getId()
                    );

                    item.put(
                            "registrationId",
                            payment.getRegistration()
                                    .getRegistrationCode()
                    );

                    item.put(
                            "participant",
                            payment.getRegistration()
                                    .getUser()
                                    .getFullName()
                    );

                    item.put(
                            "email",
                            payment.getRegistration()
                                    .getUser()
                                    .getEmail()
                    );

                    item.put(
                            "amount",
                            payment.getAmountMinor()
                                    / 100.0
                    );

                    item.put(
                            "currency",
                            payment.getCurrency()
                    );

                    item.put(
                            "status",
                            payment.getStatus().name()
                    );

                    item.put(
                            "orderId",
                            value(
                                    payment
                                            .getGatewayOrderId()
                            )
                    );

                    item.put(
                            "paymentId",
                            value(
                                    payment
                                            .getGatewayPaymentId()
                            )
                    );

                    item.put(
                            "createdAt",
                            payment.getCreatedAt()
                    );

                    item.put(
                            "paidAt",
                            payment.getPaidAt()
                    );

                    return item;
                })
                .toList();
    }

    /*
     * ============================================================
     * SUBMISSIONS
     * ============================================================
     */

    @GetMapping("/submissions")
    @Transactional(readOnly = true)
    public List<Map<String, Object>> submissionDetails() {

        return submissions.findAll()
                .stream()
                .sorted(
                        Comparator.comparing(
                                Submission::getUpdatedAt
                        ).reversed()
                )
                .map(submission -> {

                    Map<String, Object> item =
                            new LinkedHashMap<>();

                    item.put(
                            "id",
                            submission.getId()
                    );

                    item.put(
                            "registrationId",
                            submission
                                    .getRegistration()
                                    .getRegistrationCode()
                    );

                    item.put(
                            "participant",
                            submission
                                    .getRegistration()
                                    .getUser()
                                    .getFullName()
                    );

                    item.put(
                            "email",
                            submission
                                    .getRegistration()
                                    .getUser()
                                    .getEmail()
                    );

                    item.put(
                            "entryType",
                            submission
                                    .getRegistration()
                                    .getParticipationType()
                                    .name()
                    );

                    item.put(
                            "projectName",
                            submission.getProjectName()
                    );

                    item.put(
                            "technologyStack",
                            submission.getTechnologyStack()
                    );

                    item.put(
                            "repositoryUrl",
                            value(
                                    submission
                                            .getRepositoryUrl()
                            )
                    );

                    item.put(
                            "demoUrl",
                            value(
                                    submission.getDemoUrl()
                            )
                    );

                    item.put(
                            "status",
                            submission
                                    .getStatus()
                                    .name()
                    );

                    item.put(
                            "updatedAt",
                            submission.getUpdatedAt()
                    );

                    item.put(
                            "submittedAt",
                            submission.getSubmittedAt()
                    );

                    return item;
                })
                .toList();
    }

    /*
     * ============================================================
     * GUARDIAN CONSENTS
     * ============================================================
     */

    @GetMapping("/guardian-consents")
    @Transactional(readOnly = true)
    public List<Map<String, Object>>
    guardianConsentDetails() {

        return guardianConsents.findAll()
                .stream()
                .sorted(
                        Comparator.comparing(
                                GuardianConsent::getCreatedAt
                        ).reversed()
                )
                .map(consent -> {

                    Map<String, Object> item =
                            new LinkedHashMap<>();

                    item.put(
                            "id",
                            consent.getId()
                    );

                    item.put(
                            "registrationId",
                            consent.getRegistration()
                                    .getRegistrationCode()
                    );

                    item.put(
                            "participant",
                            consent.getRegistration()
                                    .getUser()
                                    .getFullName()
                    );

                    item.put(
                            "guardianName",
                            value(
                                    consent.getRegistration()
                                            .getGuardianName()
                            )
                    );

                    item.put(
                            "guardianEmail",
                            value(
                                    consent.getRegistration()
                                            .getGuardianEmail()
                            )
                    );

                    item.put(
                            "attempts",
                            consent.getAttempts()
                    );

                    item.put(
                            "expiresAt",
                            consent.getExpiresAt()
                    );

                    item.put(
                            "consumedAt",
                            consent.getConsumedAt()
                    );

                    item.put(
                            "consentedAt",
                            consent.getConsentedAt()
                    );

                    item.put(
                            "legalVersion",
                            value(
                                    consent.getLegalVersion()
                            )
                    );

                    String status;

                    if (
                            consent.getConsentedAt()
                                    != null
                    ) {
                        status = "CONSENTED";

                    } else if (
                            consent.getConsumedAt()
                                    != null
                    ) {
                        status = "REPLACED_OR_USED";

                    } else if (
                            consent.getExpiresAt()
                                    .isBefore(Instant.now())
                    ) {
                        status = "EXPIRED";

                    } else {
                        status = "PENDING";
                    }

                    item.put(
                            "status",
                            status
                    );

                    return item;
                })
                .toList();
    }

    /*
     * ============================================================
     * NOTIFICATIONS
     * ============================================================
     */

    @GetMapping("/notifications")
    @Transactional(readOnly = true)
    public List<Map<String, Object>>
    notificationDetails() {

        return notifications.findAll()
                .stream()
                .sorted(
                        Comparator.comparing(
                                NotificationOutbox::getCreatedAt
                        ).reversed()
                )
                .limit(500)
                .map(notification -> {

                    Map<String, Object> item =
                            new LinkedHashMap<>();

                    item.put(
                            "id",
                            notification.getId()
                    );

                    item.put(
                            "channel",
                            notification
                                    .getChannel()
                                    .name()
                    );

                    item.put(
                            "recipient",
                            notification.getRecipient()
                    );

                    item.put(
                            "subject",
                            notification.getSubject()
                    );

                    item.put(
                            "eventType",
                            notification.getEventType()
                    );

                    item.put(
                            "referenceId",
                            notification.getReferenceId()
                    );

                    item.put(
                            "status",
                            notification
                                    .getStatus()
                                    .name()
                    );

                    item.put(
                            "attempts",
                            notification.getAttempts()
                    );

                    item.put(
                            "lastError",
                            value(
                                    notification.getLastError()
                            )
                    );

                    item.put(
                            "createdAt",
                            notification.getCreatedAt()
                    );

                    return item;
                })
                .toList();
    }

    /*
     * ============================================================
     * PRIVATE HELPERS
     * ============================================================
     */

    private int calculateAge(
            LocalDate dateOfBirth
    ) {
        if (dateOfBirth == null) {
            return 0;
        }

        return Period.between(
                dateOfBirth,
                LocalDate.now(ZoneOffset.UTC)
        ).getYears();
    }

    private String value(String value) {
        return value == null
                ? ""
                : value;
    }

    private boolean isAdministratorRole(
            Role role
    ) {
        return role == Role.ADMIN
                || role == Role.SUPER_ADMIN;
    }

    private boolean isSuperAdmin(
            Authentication authentication
    ) {
        return authentication != null
                && authentication
                .getAuthorities()
                .stream()
                .anyMatch(authority ->
                        "ROLE_SUPER_ADMIN".equals(
                                authority.getAuthority()
                        )
                );
    }

    private void requireSuperAdmin(
            Authentication authentication
    ) {
        if (!isSuperAdmin(authentication)) {
            throw new IllegalArgumentException(
                    "Only a Super Administrator can manage "
                            + "Administrator access."
            );
        }
    }

    private void protectAdministratorRole(
            Role requestedRole,
            Authentication authentication
    ) {
        if (
                isAdministratorRole(requestedRole)
                        && !isSuperAdmin(authentication)
        ) {
            throw new IllegalArgumentException(
                    "Only a Super Administrator can assign "
                            + "Administrator roles."
            );
        }
    }

    private Map<String, Object> userView(
            User user
    ) {
        Map<String, Object> item =
                new LinkedHashMap<>();

        item.put(
                "id",
                user.getId()
        );

        item.put(
                "fullName",
                user.getFullName()
        );

        item.put(
                "email",
                user.getEmail()
        );

        item.put(
                "role",
                user.getRole().name()
        );

        item.put(
                "enabled",
                user.isEnabled()
        );

        item.put(
                "emailVerified",
                user.getEmailVerifiedAt()
                        != null
        );

        item.put(
                "createdAt",
                user.getCreatedAt()
        );

        return item;
    }

    /*
     * ============================================================
     * REQUEST RECORDS
     * ============================================================
     */

    public record CreateUser(

            @NotBlank
            @Size(max = 120)
            String fullName,

            @Email
            @NotBlank
            @Size(max = 190)
            String email,

            @NotBlank
            @Size(
                    min = 8,
                    max = 12,
                    message =
                            "Password must contain 8–12 characters"
            )
            String password,

            @NotNull
            Role role,

            boolean enabled,

            boolean emailVerified

    ) {
    }

    public record AdministratorRequest(

            @Email
            @NotBlank
            @Size(max = 190)
            String email

    ) {
    }

    public record UpdateUser(

            @NotBlank
            @Size(max = 120)
            String fullName,

            @Email
            @NotBlank
            @Size(max = 190)
            String email,

            @Size(
                    min = 8,
                    max = 12,
                    message =
                            "Password must contain 8–12 characters"
            )
            String newPassword,

            @NotNull
            Role role,

            boolean enabled,

            boolean emailVerified

    ) {
    }

    /*
     * ============================================================
     * ERROR RESPONSE
     * ============================================================
     */

    @ExceptionHandler({
            IllegalArgumentException.class,
            NoSuchElementException.class
    })
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Map<String, String> handleBadRequest(
            Exception exception
    ) {
        return Map.of(
                "message",
                exception.getMessage()
        );
    }
}