package com.mitraa.hackathon.admin;

import com.mitraa.hackathon.common.InputNormalizer;
import com.mitraa.hackathon.guardian.GuardianConsent;
import com.mitraa.hackathon.guardian.GuardianConsentRepository;
import com.mitraa.hackathon.invoice.InvoiceRepository;
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
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.time.LocalDate;
import java.time.Period;
import java.time.ZoneOffset;
import java.util.Comparator;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/admin")
public class AdminController {

    private final UserRepository users;
    private final RegistrationRepository registrations;
    private final PaymentRepository payments;
    private final InvoiceRepository invoices;
    private final NotificationOutboxRepository notifications;
    private final TeamRepository teams;
    private final SubmissionRepository submissions;
    private final GuardianConsentRepository guardianConsents;
    private final PasswordEncoder encoder;
    private final AdminUserLifecycleService userLifecycle;
    private final AdminUserExcelService userExcel;

    public AdminController(
            UserRepository users,
            RegistrationRepository registrations,
            PaymentRepository payments,
            InvoiceRepository invoices,
            NotificationOutboxRepository notifications,
            TeamRepository teams,
            SubmissionRepository submissions,
            GuardianConsentRepository guardianConsents,
            PasswordEncoder encoder,
            AdminUserLifecycleService userLifecycle,
            AdminUserExcelService userExcel
    ) {
        this.users = users;
        this.registrations = registrations;
        this.payments = payments;
        this.invoices = invoices;
        this.notifications = notifications;
        this.teams = teams;
        this.submissions = submissions;
        this.guardianConsents = guardianConsents;
        this.encoder = encoder;
        this.userLifecycle = userLifecycle;
        this.userExcel = userExcel;
    }

    @GetMapping("/metrics")
    @Transactional(readOnly = true)
    public Map<String, Object> metrics() {
        long inrRevenue = payments.findAll()
                .stream()
                .filter(payment ->
                        payment.getStatus() == PaymentStatus.PAID
                        && "INR".equals(payment.getCurrency()))
                .mapToLong(Payment::getAmountMinor)
                .sum();

        long usdRevenue = payments.findAll()
                .stream()
                .filter(payment ->
                        payment.getStatus() == PaymentStatus.PAID
                        && "USD".equals(payment.getCurrency()))
                .mapToLong(Payment::getAmountMinor)
                .sum();

        long teamMemberCount = teams.findAll()
                .stream()
                .mapToLong(team -> team.getMembers().size() + 1L)
                .sum();

        Map<String, Object> metrics = new LinkedHashMap<>();

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

        metrics.put("teams", teams.count());
        metrics.put("teamMembers", teamMemberCount);

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

        metrics.put("revenueInr", inrRevenue / 100.0);
        metrics.put("revenueUsd", usdRevenue / 100.0);

        metrics.put(
                "pocDrafts",
                submissions.countByStatus(SubmissionStatus.DRAFT)
        );

        metrics.put(
                "pocSubmitted",
                submissions.countByStatus(SubmissionStatus.SUBMITTED)
        );

        metrics.put(
                "notificationsPending",
                notifications.countByStatus(NotificationStatus.PENDING)
        );

        metrics.put(
                "notificationsFailed",
                notifications.countByStatus(NotificationStatus.FAILED)
        );

        return metrics;
    }

    @GetMapping("/users")
    @Transactional(readOnly = true)
    public List<Map<String, Object>> allUsers(Authentication authentication) {
        boolean includeSuperAdmins = isSuperAdmin(authentication);
        return users.findAll()
                .stream()
                .filter(user ->
                        includeSuperAdmins
                        || (user.getRole() != Role.SUPER_ADMIN
                            && user.getRole() != Role.ADMIN)
                )
                .sorted(
                        Comparator.comparing(User::getCreatedAt).reversed()
                )
                .map(this::userView)
                .toList();
    }

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
                        || user.getRole() == Role.SUPER_ADMIN
                )
                .sorted(
                        Comparator.comparing(User::getCreatedAt)
                                .reversed()
                )
                .map(this::userView)
                .toList();
    }

    @PostMapping("/administrators")
    @Transactional
    public Map<String, Object> addAdministrator(
            @Valid @RequestBody AdministratorRequest body,
            Authentication authentication
    ) {
        requireSuperAdmin(authentication);

        String normalizedEmail =
                body.email().trim().toLowerCase(Locale.ROOT);

        User user = users.findByEmailIgnoreCase(normalizedEmail)
                .orElseThrow(() -> new IllegalArgumentException(
                        "No registered user was found for this email address."
                ));

        if (user.getRole() == Role.SUPER_ADMIN) {
            throw new IllegalArgumentException(
                    "This account is already a super administrator."
            );
        }

        user.setRole(Role.ADMIN);
        user.setEnabled(true);

        return userView(users.save(user));
    }

    @DeleteMapping("/administrators/{id}")
    @Transactional
    public Map<String, String> removeAdministrator(
            @PathVariable Long id,
            Authentication authentication
    ) {
        requireSuperAdmin(authentication);

        User user = users.findById(id)
                .orElseThrow(() ->
                        new NoSuchElementException("Administrator not found."));

        if (user.getRole() == Role.SUPER_ADMIN) {
            throw new IllegalArgumentException(
                    "Super administrator access cannot be removed from this table."
            );
        }

        if (user.getRole() != Role.ADMIN) {
            throw new IllegalArgumentException(
                    "This user does not currently have administrator access."
            );
        }

        if (user.getEmail().equalsIgnoreCase(authentication.getName())) {
            throw new IllegalArgumentException(
                    "You cannot remove your own administrator access."
            );
        }

        user.setRole(Role.PARTICIPANT);
        users.save(user);

        return Map.of(
                "message",
                "Administrator access removed. The user account and audit records were retained."
        );
    }

    @PostMapping("/users")
    @ResponseStatus(HttpStatus.CREATED)
    @Transactional
    public Map<String, Object> createUser(
            @Valid @RequestBody CreateUser body,
            Authentication authentication
    ) {
        protectSuperAdminRole(body.role(), authentication);

        String normalizedEmail =
                body.email().trim().toLowerCase(Locale.ROOT);

        if (users.existsByEmailIgnoreCase(normalizedEmail)) {
            throw new IllegalArgumentException(
                    "Email is already registered."
            );
        }

        User user = new User();

        InputNormalizer.requireNoOuterPasswordWhitespace(body.password());
        user.setFullName(InputNormalizer.capitalizeFirstCharacter(body.fullName()));
        user.setEmail(normalizedEmail);
        user.setPasswordHash(encoder.encode(body.password()));
        user.setRole(body.role());
        user.setEnabled(body.enabled());

        if (body.emailVerified()) {
            user.setEmailVerifiedAt(Instant.now());
        } else {
            user.setEmailVerifiedAt(null);
        }

        return userView(users.save(user));
    }

    @PutMapping("/users/{id}")
    @Transactional
    public Map<String, Object> updateUser(
            @PathVariable Long id,
            @Valid @RequestBody UpdateUser body,
            Authentication authentication
    ) {
        User user = users.findById(id)
                .orElseThrow(() ->
                        new NoSuchElementException("User not found."));

        protectPrivilegedUserRecord(user, authentication, "edit");

        protectSuperAdminRole(body.role(), authentication);

        String normalizedEmail =
                body.email().trim().toLowerCase(Locale.ROOT);

        users.findByEmailIgnoreCase(normalizedEmail)
                .filter(existing ->
                        !existing.getId().equals(id))
                .ifPresent(existing -> {
                    throw new IllegalArgumentException(
                            "Email is already registered."
                    );
                });

        user.setFullName(InputNormalizer.capitalizeFirstCharacter(body.fullName()));
        user.setEmail(normalizedEmail);
        user.setRole(body.role());
        user.setEnabled(body.enabled());

        if (body.emailVerified()
                && user.getEmailVerifiedAt() == null) {
            user.setEmailVerifiedAt(Instant.now());
        }

        if (!body.emailVerified()) {
            user.setEmailVerifiedAt(null);
        }

        if (body.newPassword() != null
                && !body.newPassword().isBlank()) {
            InputNormalizer.requireNoOuterPasswordWhitespace(body.newPassword());
            user.setPasswordHash(
                    encoder.encode(body.newPassword())
            );
        }

        return userView(users.save(user));
    }

    @PatchMapping("/users/{id}/access")
    @Transactional
    public Map<String, Object> changeUserAccess(
            @PathVariable Long id,
            @Valid @RequestBody UserAccessRequest body,
            Authentication authentication
    ) {
        return userView(userLifecycle.setEnabled(id, body.enabled(), authentication));
    }

    @DeleteMapping("/users/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void permanentlyDeleteUser(
            @PathVariable Long id,
            Authentication authentication
    ) {
        userLifecycle.permanentlyDelete(id, authentication);
    }

    @GetMapping(value = "/users/export", produces = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
    public ResponseEntity<byte[]> exportAllUsers(Authentication authentication) {
        byte[] workbook = userExcel.exportAllUsers(isSuperAdmin(authentication));
        String filename = "mitraa-all-users-" + LocalDate.now(ZoneOffset.UTC) + ".xlsx";

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(MediaType.parseMediaType(
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .contentLength(workbook.length)
                .body(workbook);
    }

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
                            registration.getRegistrationCode()
                    );

                    item.put(
                            "name",
                            registration.getUser().getFullName()
                    );

                    item.put(
                            "email",
                            registration.getUser().getEmail()
                    );

                    item.put(
                            "country",
                            registration.getCountry()
                    );

                    item.put(
                            "type",
                            registration.getParticipationType().name()
                    );

                    item.put(
                            "domain",
                            registration.getDomain()
                    );

                    item.put(
                            "verification",
                            registration.getAgeVerificationStatus()
                    );

                    item.put(
                            "status",
                            registration.getStatus().name()
                    );

                    return item;
                })
                .toList();
    }

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
                            registration.getRegistrationCode()
                    );

                    item.put(
                            "name",
                            registration.getUser().getFullName()
                    );

                    item.put(
                            "email",
                            registration.getUser().getEmail()
                    );

                    item.put(
                            "emailVerified",
                            registration.getUser()
                                    .getEmailVerifiedAt() != null
                    );

                    item.put(
                            "dateOfBirth",
                            registration.getDateOfBirth()
                    );

                    item.put(
                            "age",
                            calculateAge(registration.getDateOfBirth())
                    );

                    item.put("phone", registration.getPhone());
                    item.put("country", registration.getCountry());
                    item.put("city", registration.getCity());

                    item.put(
                            "type",
                            registration.getParticipationType().name()
                    );

                    item.put("domain", registration.getDomain());

                    item.put(
                            "eligibility",
                            registration.getAgeVerificationStatus()
                    );

                    item.put(
                            "status",
                            registration.getStatus().name()
                    );

                    item.put(
                            "guardianRequired",
                            registration.isGuardianConsentRequired()
                    );

                    item.put(
                            "guardianReceived",
                            registration.isGuardianConsentReceived()
                    );

                    item.put(
                            "guardianName",
                            value(registration.getGuardianName())
                    );

                    item.put(
                            "guardianRelationship",
                            value(registration.getGuardianRelationship())
                    );

                    item.put(
                            "guardianEmail",
                            value(registration.getGuardianEmail())
                    );

                    item.put(
                            "guardianPhone",
                            value(registration.getGuardianPhone())
                    );

                    item.put(
                            "guardianCountry",
                            value(registration.getGuardianCountry())
                    );

                    item.put(
                            "termsAcceptedAt",
                            registration.getTermsAcceptedAt()
                    );

                    item.put(
                            "privacyAcceptedAt",
                            registration.getPrivacyAcceptedAt()
                    );

                    item.put(
                            "rulesAcceptedAt",
                            registration.getRulesAcceptedAt()
                    );

                    item.put(
                            "marketingConsent",
                            registration.isMarketingConsent()
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

    @GetMapping("/teams")
    @Transactional(readOnly = true)
    public List<Map<String, Object>> teamDetails() {
        return teams.findAll()
                .stream()
                .sorted(
                        Comparator.comparing(Team::getCreatedAt)
                                .reversed()
                )
                .map(team -> {
                    Map<String, Object> item =
                            new LinkedHashMap<>();

                    item.put("id", team.getId());
                    item.put("teamName", team.getName());
                    item.put("teamCode", team.getCode());

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

                    item.put("createdAt", team.getCreatedAt());

                    item.put(
                            "members",
                            team.getMembers()
                                    .stream()
                                    .map(member -> {
                                        Map<String, Object> memberView =
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
                                                        member.getDateOfBirth()
                                                )
                                        );

                                        memberView.put(
                                                "country",
                                                member.getCountry()
                                        );

                                        memberView.put(
                                                "guardianConsent",
                                                member.isGuardianConsent()
                                        );

                                        return memberView;
                                    })
                                    .toList()
                    );

                    return item;
                })
                .toList();
    }

    @GetMapping("/payments")
    @Transactional(readOnly = true)
    public List<Map<String, Object>> paymentDetails(
            @RequestParam(required = false) String email
    ) {
        String emailQuery = email == null ? "" : email.trim().toLowerCase(Locale.ROOT);

        return payments.findAll()
                .stream()
                .filter(payment -> emailQuery.isBlank()
                        || payment.getRegistration().getUser().getEmail().toLowerCase(Locale.ROOT).contains(emailQuery))
                .sorted(
                        Comparator.comparing(Payment::getCreatedAt)
                                .reversed()
                )
                .map(payment -> {
                    Map<String, Object> item =
                            new LinkedHashMap<>();

                    item.put("id", payment.getId());

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
                            payment.getAmountMinor() / 100.0
                    );

                    item.put("currency", payment.getCurrency());

                    item.put(
                            "status",
                            payment.getStatus().name()
                    );

                    item.put(
                            "orderId",
                            value(payment.getGatewayOrderId())
                    );

                    item.put(
                            "paymentId",
                            value(payment.getGatewayPaymentId())
                    );

                    item.put(
                            "createdAt",
                            payment.getCreatedAt()
                    );

                    item.put(
                            "paidAt",
                            payment.getPaidAt()
                    );

                    item.put(
                            "invoiceNumber",
                            invoices.findByPayment(payment)
                                    .map(invoice -> invoice.getInvoiceNumber())
                                    .orElse(null)
                    );

                    return item;
                })
                .toList();
    }

    @GetMapping("/submissions")
    @Transactional(readOnly = true)
    public List<Map<String, Object>> submissionDetails() {
        return submissions.findAll()
                .stream()
                .sorted(
                        Comparator.comparing(Submission::getUpdatedAt)
                                .reversed()
                )
                .map(submission -> {
                    Map<String, Object> item =
                            new LinkedHashMap<>();

                    item.put("id", submission.getId());

                    item.put(
                            "registrationId",
                            submission.getRegistration()
                                    .getRegistrationCode()
                    );

                    item.put(
                            "participant",
                            submission.getRegistration()
                                    .getUser()
                                    .getFullName()
                    );

                    item.put(
                            "email",
                            submission.getRegistration()
                                    .getUser()
                                    .getEmail()
                    );

                    item.put(
                            "entryType",
                            submission.getRegistration()
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
                            value(submission.getRepositoryUrl())
                    );

                    item.put(
                            "demoUrl",
                            value(submission.getDemoUrl())
                    );

                    item.put(
                            "status",
                            submission.getStatus().name()
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

    @GetMapping("/guardian-consents")
    @Transactional(readOnly = true)
    public List<Map<String, Object>> guardianConsentDetails() {
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

                    item.put("id", consent.getId());

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

                    item.put("attempts", consent.getAttempts());
                    item.put("expiresAt", consent.getExpiresAt());
                    item.put("consumedAt", consent.getConsumedAt());
                    item.put("consentedAt", consent.getConsentedAt());

                    item.put(
                            "legalVersion",
                            value(consent.getLegalVersion())
                    );

                    String status;

                    if (consent.getConsentedAt() != null) {
                        status = "CONSENTED";
                    } else if (consent.getConsumedAt() != null) {
                        status = "REPLACED_OR_USED";
                    } else if (consent.getExpiresAt()
                            .isBefore(Instant.now())) {
                        status = "EXPIRED";
                    } else {
                        status = "PENDING";
                    }

                    item.put("status", status);

                    return item;
                })
                .toList();
    }

    @GetMapping("/notifications")
    @Transactional(readOnly = true)
    public List<Map<String, Object>> notificationDetails(Authentication authentication) {
        boolean includeSuperAdmins = isSuperAdmin(authentication);
        Set<String> protectedEmails = includeSuperAdmins
                ? Set.of()
                : superAdminEmails();

        return notifications.findAll()
                .stream()
                .filter(notification -> includeSuperAdmins
                        || !containsProtectedEmail(notification, protectedEmails))
                .sorted(
                        Comparator.comparing(
                                NotificationOutbox::getCreatedAt
                        ).reversed()
                )
                .limit(500)
                .map(notification -> {
                    Map<String, Object> item =
                            new LinkedHashMap<>();

                    item.put("id", notification.getId());

                    item.put(
                            "channel",
                            notification.getChannel().name()
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
                            notification.getStatus().name()
                    );

                    item.put(
                            "attempts",
                            notification.getAttempts()
                    );

                    item.put(
                            "lastError",
                            value(notification.getLastError())
                    );

                    item.put(
                            "createdAt",
                            notification.getCreatedAt()
                    );

                    return item;
                })
                .toList();
    }

    @GetMapping(value = "/registrations/export", produces = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
    @Transactional(readOnly = true)
    public ResponseEntity<byte[]> exportRegistrations() {
        List<Map<String, Object>> data = registrationDetails();
        List<List<Object>> rows = new java.util.ArrayList<>();
        for (int index = 0; index < data.size(); index++) {
            Map<String, Object> item = data.get(index);
            rows.add(row(index + 1, item.get("registrationId"), item.get("name"), item.get("email"),
                    yesNo(item.get("emailVerified")), item.get("dateOfBirth"), item.get("age"), item.get("phone"),
                    item.get("country"), item.get("city"), item.get("type"), item.get("domain"),
                    item.get("eligibility"), item.get("status"), yesNo(item.get("guardianRequired")),
                    yesNo(item.get("guardianReceived")), item.get("guardianName"), item.get("guardianRelationship"),
                    item.get("guardianEmail"), item.get("guardianPhone"), item.get("guardianCountry"),
                    item.get("termsAcceptedAt"), item.get("privacyAcceptedAt"), item.get("rulesAcceptedAt"),
                    yesNo(item.get("marketingConsent")), item.get("createdAt"), item.get("activatedAt")));
        }
        return excelResponse("mitraa-registrations", "Registrations",
                List.of("S.No.", "Player ID", "Participant", "Email", "Email verified", "DOB", "Age", "Phone",
                        "Country", "City", "Entry type", "Domain", "Eligibility", "Status", "Guardian required",
                        "Guardian received", "Guardian name", "Relationship", "Guardian email", "Guardian phone",
                        "Guardian country", "Terms accepted", "Privacy accepted", "Rules accepted",
                        "Marketing consent", "Created (UTC)", "Activated (UTC)"), rows);
    }

    @GetMapping(value = "/teams/export", produces = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
    @Transactional(readOnly = true)
    public ResponseEntity<byte[]> exportTeams() {
        List<List<Object>> rows = new java.util.ArrayList<>();
        List<Team> orderedTeams = teams.findAll().stream()
                .sorted(Comparator.comparing(Team::getCreatedAt).reversed()).toList();
        int serial = 1;
        for (Team team : orderedTeams) {
            Registration registration = team.getRegistration();
            rows.add(row(serial++, team.getName(), team.getCode(), registration.getRegistrationCode(), "LEADER",
                    registration.getUser().getFullName(), registration.getUser().getEmail(), registration.getDateOfBirth(),
                    calculateAge(registration.getDateOfBirth()), registration.getPhone(), registration.getCountry(),
                    "NOT APPLICABLE", registration.getStatus().name(), team.getCreatedAt()));
            for (var member : team.getMembers()) {
                rows.add(row(serial++, team.getName(), team.getCode(), registration.getRegistrationCode(), "MEMBER",
                        member.getFullName(), member.getEmail(), member.getDateOfBirth(),
                        calculateAge(member.getDateOfBirth()), "", member.getCountry(),
                        member.isGuardianConsent() ? "YES" : "NO", registration.getStatus().name(), team.getCreatedAt()));
            }
        }
        return excelResponse("mitraa-teams-and-members", "Teams and members",
                List.of("S.No.", "Team", "Team code", "Player ID", "Role", "Name", "Email", "DOB", "Age",
                        "Phone", "Country", "Guardian consent", "Status", "Created (UTC)"), rows);
    }

    @GetMapping(value = "/payments/export", produces = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
    @Transactional(readOnly = true)
    public ResponseEntity<byte[]> exportPayments() {
        List<Map<String, Object>> data = paymentDetails(null);
        List<List<Object>> rows = new java.util.ArrayList<>();
        for (int index = 0; index < data.size(); index++) {
            Map<String, Object> item = data.get(index);
            rows.add(row(index + 1, item.get("registrationId"), item.get("participant"), item.get("email"),
                    item.get("amount"), item.get("currency"), item.get("status"), item.get("orderId"),
                    item.get("paymentId"), item.get("createdAt"), item.get("paidAt")));
        }
        return excelResponse("mitraa-payments", "Payments",
                List.of("S.No.", "Player ID", "Participant", "Email", "Amount", "Currency", "Status",
                        "Gateway order", "Payment ID", "Created (UTC)", "Paid (UTC)"), rows);
    }

    @GetMapping(value = "/submissions/export", produces = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
    @Transactional(readOnly = true)
    public ResponseEntity<byte[]> exportSubmissions() {
        List<Map<String, Object>> data = submissionDetails();
        List<List<Object>> rows = new java.util.ArrayList<>();
        for (int index = 0; index < data.size(); index++) {
            Map<String, Object> item = data.get(index);
            rows.add(row(index + 1, item.get("registrationId"), item.get("participant"), item.get("email"),
                    item.get("entryType"), item.get("projectName"), item.get("technologyStack"),
                    item.get("repositoryUrl"), item.get("demoUrl"), item.get("status"),
                    item.get("updatedAt"), item.get("submittedAt")));
        }
        return excelResponse("mitraa-submissions", "Submissions",
                List.of("S.No.", "Player ID", "Participant", "Email", "Entry type", "Project", "Technology",
                        "Repository URL", "Demo URL", "Status", "Updated (UTC)", "Submitted (UTC)"), rows);
    }

    @GetMapping(value = "/guardian-consents/export", produces = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
    @Transactional(readOnly = true)
    public ResponseEntity<byte[]> exportGuardianConsents() {
        List<Map<String, Object>> data = guardianConsentDetails();
        List<List<Object>> rows = new java.util.ArrayList<>();
        for (int index = 0; index < data.size(); index++) {
            Map<String, Object> item = data.get(index);
            rows.add(row(index + 1, item.get("registrationId"), item.get("participant"), item.get("guardianName"),
                    item.get("guardianEmail"), item.get("status"), item.get("attempts"), item.get("expiresAt"),
                    item.get("consumedAt"), item.get("consentedAt"), item.get("legalVersion")));
        }
        return excelResponse("mitraa-guardian-consents", "Guardian consents",
                List.of("S.No.", "Player ID", "Participant", "Guardian", "Guardian email", "Status", "Attempts",
                        "Expires (UTC)", "Consumed (UTC)", "Consented (UTC)", "Legal version"), rows);
    }

    @GetMapping(value = "/notifications/export", produces = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
    public ResponseEntity<byte[]> exportNotifications(Authentication authentication) {
        List<List<Object>> rows = new java.util.ArrayList<>();
        boolean includeSuperAdmins = isSuperAdmin(authentication);
        Set<String> protectedEmails = includeSuperAdmins ? Set.of() : superAdminEmails();
        List<NotificationOutbox> data = notifications.findAll().stream()
                .filter(notification -> includeSuperAdmins
                        || !containsProtectedEmail(notification, protectedEmails))
                .sorted(Comparator.comparing(NotificationOutbox::getCreatedAt).reversed()).toList();
        for (int index = 0; index < data.size(); index++) {
            NotificationOutbox item = data.get(index);
            rows.add(row(index + 1, item.getChannel().name(), item.getRecipient(), item.getEventType(),
                    item.getSubject(), item.getReferenceId(), item.getStatus().name(), item.getAttempts(),
                    value(item.getLastError()), item.getCreatedAt()));
        }
        return excelResponse("mitraa-notifications", "Notifications",
                List.of("S.No.", "Channel", "Recipient", "Event", "Subject", "Reference", "Status",
                "Attempts", "Last error", "Created (UTC)"), rows);
    }

    private Set<String> superAdminEmails() {
        return users.findAll().stream()
                .filter(user -> user.getRole() == Role.SUPER_ADMIN)
                .map(user -> user.getEmail().toLowerCase(Locale.ROOT))
                .collect(Collectors.toSet());
    }

    private boolean containsProtectedEmail(NotificationOutbox notification, Set<String> protectedEmails) {
        String recipient = value(notification.getRecipient()).toLowerCase(Locale.ROOT);
        String reference = value(notification.getReferenceId()).toLowerCase(Locale.ROOT);
        return protectedEmails.contains(recipient) || protectedEmails.contains(reference);
    }

    private ResponseEntity<byte[]> excelResponse(
            String filenamePrefix, String sheetName, List<String> headings, List<List<Object>> rows) {
        byte[] workbook = userExcel.exportRows(sheetName, headings, rows);
        String filename = filenamePrefix + "-" + LocalDate.now(ZoneOffset.UTC) + ".xlsx";
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(MediaType.parseMediaType(
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .contentLength(workbook.length)
                .body(workbook);
    }

    private List<Object> row(Object... values) {
        return Arrays.asList(values);
    }

    private String yesNo(Object value) {
        return Boolean.TRUE.equals(value) ? "YES" : "NO";
    }

    private int calculateAge(LocalDate dateOfBirth) {
        return Period.between(
                dateOfBirth,
                LocalDate.now(ZoneOffset.UTC)
        ).getYears();
    }

    private String value(String value) {
        return value == null ? "" : value;
    }

    private boolean isSuperAdmin(Authentication authentication) {
        return authentication != null
                && authentication.getAuthorities()
                    .stream()
                    .anyMatch(authority ->
                            "ROLE_SUPER_ADMIN".equals(
                                    authority.getAuthority()
                            )
                    );
    }

    private void requireSuperAdmin(Authentication authentication) {
        if (!isSuperAdmin(authentication)) {
            throw new IllegalArgumentException(
                    "Only a super administrator can manage administrator access."
            );
        }
    }

    /**
     * Protects privileged accounts from changes made through the general
     * user-management endpoints. In particular, an ADMIN can never change a
     * SUPER_ADMIN record (name, email, password, role, verification state or
     * enabled state). The same rule also prevents an ADMIN from modifying a
     * different ADMIN account.
     */
    private void protectPrivilegedUserRecord(
            User targetUser,
            Authentication authentication,
            String action
    ) {
        boolean privilegedTarget = targetUser.getRole() == Role.ADMIN
                || targetUser.getRole() == Role.SUPER_ADMIN;

        if (privilegedTarget && !isSuperAdmin(authentication)) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "Administrators cannot " + action
                            + " an ADMIN or SUPER_ADMIN account."
            );
        }
    }

    private void protectSuperAdminRole(
            Role requestedRole,
            Authentication authentication
    ) {
        if ((requestedRole == Role.ADMIN
                || requestedRole == Role.SUPER_ADMIN)
                && !isSuperAdmin(authentication)) {
            throw new IllegalArgumentException(
                    "Only a super administrator can assign "
                    + "administrator roles."
            );
        }
    }

    private Map<String, Object> userView(User user) {
        Map<String, Object> item = new LinkedHashMap<>();

        item.put("id", user.getId());
        item.put("fullName", InputNormalizer.capitalizeFirstCharacter(user.getFullName()));
        item.put("email", user.getEmail());
        item.put("role", user.getRole().name());
        item.put("enabled", user.isEnabled());

        item.put(
                "emailVerified",
                user.getEmailVerifiedAt() != null
        );

        item.put("createdAt", user.getCreatedAt());

        return item;
    }

    public record CreateUser(
            @NotBlank
            @Size(max = 120)
            String fullName,

            @Email
            @NotBlank
            @Size(max = 190)
            String email,

            @NotBlank
            @Size(min = 8, max = 12)
            @jakarta.validation.constraints.Pattern(
                    regexp = "^\\S(?:.*\\S)?$",
                    message = "Password must not start or end with a space"
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

    public record UserAccessRequest(boolean enabled) {
    }

    public record UpdateUser(
            @NotBlank
            @Size(max = 120)
            String fullName,

            @Email
            @NotBlank
            @Size(max = 190)
            String email,

            @Size(min = 8, max = 12)
            @jakarta.validation.constraints.Pattern(
                    regexp = "^\\S(?:.*\\S)?$",
                    message = "Password must not start or end with a space"
            )
            String newPassword,

            @NotNull
            Role role,

            boolean enabled,
            boolean emailVerified
    ) {
    }

    @ExceptionHandler({
            IllegalArgumentException.class,
            NoSuchElementException.class
    })
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Map<String, String> handleBadRequest(Exception exception) {
        return Map.of("message", exception.getMessage());
    }
}