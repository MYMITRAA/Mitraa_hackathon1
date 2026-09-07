package com.mitraa.hackathon.payment;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mitraa.hackathon.invoice.InvoiceService;
import com.mitraa.hackathon.notification.NotificationService;
import com.mitraa.hackathon.registration.Registration;
import com.mitraa.hackathon.registration.RegistrationRepository;
import com.mitraa.hackathon.registration.RegistrationStatus;
import com.mitraa.hackathon.submission.SubmissionRepository;
import com.mitraa.hackathon.submission.SubmissionStatus;
import com.mitraa.hackathon.user.UserRepository;

import jakarta.servlet.http.HttpSession;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Map;

@RestController
@RequestMapping("/api/payments")
public class PaymentController {

    private final UserRepository users;
    private final RegistrationRepository registrations;
    private final PaymentRepository payments;
    private final NotificationService notifications;
    private final InvoiceService invoices;
    private final SubmissionRepository submissions;
    private final PricingService pricing;
    private final ObjectMapper json;

    @Value("${app.payment.razorpay.key-id:}")
    String keyId;

    @Value("${app.payment.razorpay.key-secret:}")
    String keySecret;

    @Value("${app.payment.razorpay.webhook-secret:}")
    String webhookSecret;

    public PaymentController(
            UserRepository users,
            RegistrationRepository registrations,
            PaymentRepository payments,
            NotificationService notifications,
            InvoiceService invoices,
            PricingService pricing,
            SubmissionRepository submissions,
            ObjectMapper json) {

        this.users = users;
        this.registrations = registrations;
        this.payments = payments;
        this.notifications = notifications;
        this.invoices = invoices;
        this.pricing = pricing;
        this.submissions = submissions;
        this.json = json;
    }

    // ============================================================
    // OLD LOGIN-BASED PAYMENT ENDPOINT
    // ============================================================

    @PostMapping("/create-order")
    @Transactional
    public ResponseEntity<?> createOrder(Authentication auth) throws Exception {

        if (auth == null) {
            return ResponseEntity.status(401)
                    .body(Map.of("message", "Login is required."));
        }

        var user = users.findByEmailIgnoreCase(auth.getName())
                .orElseThrow();

        var registration = registrations.findByUser(user)
                .orElseThrow();

        if (!"DOB_VERIFIED".equals(
                registration.getAgeVerificationStatus())) {

            return ResponseEntity.status(409).body(Map.of(
                    "status", "VERIFICATION_REQUIRED",
                    "message",
                    "The registration date of birth must pass the 10–35 eligibility check before payment."
            ));
        }

        if (registration.isGuardianConsentRequired()
                && !registration.isGuardianConsentReceived()) {

            return ResponseEntity.status(409).body(Map.of(
                    "status", "GUARDIAN_REQUIRED",
                    "message",
                    "Verified guardian consent is required before payment."
            ));
        }

        var submission = submissions.findByRegistration(registration);

        if (submission.isEmpty()
                || submission.get().getStatus() == SubmissionStatus.DRAFT) {

            return ResponseEntity.status(409).body(Map.of(
                    "status", "SUBMISSION_REQUIRED",
                    "message",
                    "Final POC submission is required before payment."
            ));
        }

        if (registration.getStatus() == RegistrationStatus.CONFIRMED) {

            return ResponseEntity.status(409).body(Map.of(
                    "status", "ALREADY_ACTIVE",
                    "message", "Registration is already active."
            ));
        }

        if (registration.getStatus()
                != RegistrationStatus.PENDING_PAYMENT) {

            return ResponseEntity.status(409).body(Map.of(
                    "status", "FLOW_REQUIRED",
                    "message",
                    "Complete all eligibility steps before payment."
            ));
        }

        return createRazorpayOrder(registration);
    }


    // ============================================================
    // NEW PAYMENT-FIRST FLOW
    // REGISTER -> OTP -> PAYMENT -> LOGIN
    // ============================================================

    @PostMapping("/create-order-after-verification")
    @Transactional
    public ResponseEntity<?> createOrderAfterVerification(
            HttpSession session) throws Exception {

        RegistrationContext context =
                verifiedRegistration(session);

        if (context == null) {

            return ResponseEntity.status(401).body(Map.of(
                    "status", "PAYMENT_SESSION_EXPIRED",
                    "message",
                    "Payment session expired. Please verify your email again."
            ));
        }

        Registration registration = context.registration();

        if (registration.getStatus()
                == RegistrationStatus.CANCELLED
                || registration.getStatus()
                == RegistrationStatus.DISQUALIFIED) {

            return ResponseEntity.status(409).body(Map.of(
                    "status", "REGISTRATION_NOT_ALLOWED",
                    "message",
                    "This registration cannot be activated."
            ));
        }

        if (!"DOB_VERIFIED".equals(
                registration.getAgeVerificationStatus())) {

            return ResponseEntity.status(409).body(Map.of(
                    "status", "VERIFICATION_REQUIRED",
                    "message",
                    "Your eligibility verification must be completed before payment."
            ));
        }

        // Already paid?
        var paidPayment =
                payments.findTopByRegistrationAndStatusOrderByCreatedAtDesc(
                        registration,
                        PaymentStatus.PAID
                );

        if (paidPayment.isPresent()) {

            return ResponseEntity.ok(Map.of(
                    "status", "ALREADY_PAID",
                    "paid", true,
                    "registrationId",
                    registration.getRegistrationCode()
            ));
        }

        return createRazorpayOrder(registration);
    }


    // ============================================================
    // CREATE RAZORPAY ORDER
    // ============================================================

    private ResponseEntity<?> createRazorpayOrder(
            Registration registration) throws Exception {

        if (keyId.isBlank() || keySecret.isBlank()) {

            return ResponseEntity.status(503).body(Map.of(
                    "status", "NOT_CONFIGURED",
                    "message",
                    "Razorpay credentials are not configured."
            ));
        }

        // Reuse an existing CREATED order.
        var existing =
                payments.findTopByRegistrationAndStatusOrderByCreatedAtDesc(
                        registration,
                        PaymentStatus.CREATED
                );

        if (existing.isPresent()) {

            Payment p = existing.get();

            return ResponseEntity.ok(Map.of(
                    "keyId", keyId,
                    "orderId", p.getGatewayOrderId(),
                    "amount", p.getAmountMinor(),
                    "currency", p.getCurrency(),
                    "displayAmount",
                    p.getCurrency() + " "
                            + String.format(
                                    Locale.ROOT,
                                    "%.2f",
                                    p.getAmountMinor() / 100.0
                            ),
                    "registrationId",
                    registration.getRegistrationCode()
            ));
        }

        EntryPrice entryPrice =
                pricing.priceFor(
                        registration.getCountry(),
                        registration.getParticipationType()
                );

        String receipt =
                registration.getRegistrationCode();

        String requestBody =
                json.writeValueAsString(
                        Map.of(
                                "amount",
                                entryPrice.amountMinor(),

                                "currency",
                                entryPrice.currency(),

                                "receipt",
                                receipt,

                                "notes",
                                Map.of(
                                        "registrationId",
                                        receipt,

                                        "country",
                                        registration.getCountry(),

                                        "entryType",
                                        registration
                                                .getParticipationType()
                                                .name()
                                )
                        )
                );

        String basic =
                Base64.getEncoder().encodeToString(
                        (keyId + ":" + keySecret)
                                .getBytes(StandardCharsets.UTF_8)
                );

        HttpRequest request =
                HttpRequest.newBuilder(
                                URI.create(
                                        "https://api.razorpay.com/v1/orders"
                                )
                        )
                        .header(
                                "Authorization",
                                "Basic " + basic
                        )
                        .header(
                                "Content-Type",
                                "application/json"
                        )
                        .POST(
                                HttpRequest.BodyPublishers
                                        .ofString(requestBody)
                        )
                        .build();

        HttpResponse<String> response =
                HttpClient.newHttpClient()
                        .send(
                                request,
                                HttpResponse.BodyHandlers.ofString()
                        );

        if (response.statusCode() / 100 != 2) {

            return ResponseEntity.status(502).body(Map.of(
                    "status", "GATEWAY_ERROR",
                    "message",
                    "Payment gateway rejected order creation."
            ));
        }

        JsonNode node =
                json.readTree(response.body());

        Payment payment = new Payment();

        payment.setRegistration(registration);
        payment.setAmountMinor(
                entryPrice.amountMinor()
        );
        payment.setCurrency(
                entryPrice.currency()
        );
        payment.setGatewayOrderId(
                node.path("id").asText()
        );

        payments.save(payment);

        return ResponseEntity.ok(Map.of(
                "keyId", keyId,
                "orderId",
                payment.getGatewayOrderId(),
                "amount",
                entryPrice.amountMinor(),
                "currency",
                entryPrice.currency(),
                "displayAmount",
                entryPrice.displayAmount(),
                "registrationId",
                receipt
        ));
    }


    // ============================================================
    // VERIFY RAZORPAY PAYMENT AFTER CHECKOUT
    // ============================================================

    @PostMapping("/verify-after-checkout")
    @Transactional
    public ResponseEntity<?> verifyAfterCheckout(
            @RequestBody Map<String, String> body,
            HttpSession session) throws Exception {

        RegistrationContext context =
                verifiedRegistration(session);

        if (context == null) {

            return ResponseEntity.status(401).body(Map.of(
                    "status", "PAYMENT_SESSION_EXPIRED",
                    "message",
                    "Payment session expired. Please verify your email again."
            ));
        }

        String razorpayPaymentId =
                body.get("razorpay_payment_id");

        String razorpayOrderId =
                body.get("razorpay_order_id");

        String razorpaySignature =
                body.get("razorpay_signature");

        if (isBlank(razorpayPaymentId)
                || isBlank(razorpayOrderId)
                || isBlank(razorpaySignature)) {

            return ResponseEntity.badRequest().body(Map.of(
                    "status", "INVALID_PAYMENT_RESPONSE",
                    "message",
                    "Incomplete Razorpay payment response."
            ));
        }

        Registration registration =
                context.registration();

        // --------------------------------------------------------
        // Find our server-created order
        // --------------------------------------------------------

        Payment payment =
                payments.findByGatewayOrderIdForUpdate(
                        razorpayOrderId
                ).orElse(null);

        if (payment == null) {

            return ResponseEntity.status(404).body(Map.of(
                    "status", "ORDER_NOT_FOUND",
                    "message",
                    "Payment order was not found."
            ));
        }

        // Prevent another registration from using this order.
        if (!payment.getRegistration()
                .getId()
                .equals(registration.getId())) {

            return ResponseEntity.status(403).body(Map.of(
                    "status", "ORDER_MISMATCH",
                    "message",
                    "Payment order does not belong to this registration."
            ));
        }

        // Already processed.
        if (payment.getStatus() == PaymentStatus.PAID) {

            return ResponseEntity.ok(Map.of(
                    "status", "PAID",
                    "paid", true,
                    "paymentId",
                    payment.getGatewayPaymentId() == null
                            ? razorpayPaymentId
                            : payment.getGatewayPaymentId()
            ));
        }

        // --------------------------------------------------------
        // Verify Razorpay signature
        // --------------------------------------------------------

        String signaturePayload =
                razorpayOrderId
                        + "|"
                        + razorpayPaymentId;

        String expectedSignature =
                hmac(
                        signaturePayload,
                        keySecret
                );

        if (!constantTime(
                expectedSignature,
                razorpaySignature
        )) {

            return ResponseEntity.status(400).body(Map.of(
                    "status", "SIGNATURE_INVALID",
                    "message",
                    "Razorpay payment signature verification failed."
            ));
        }

        // --------------------------------------------------------
        // Verify payment directly with Razorpay
        // --------------------------------------------------------

        JsonNode razorpayPayment =
                fetchRazorpayPayment(
                        razorpayPaymentId
                );

        String returnedOrderId =
                razorpayPayment
                        .path("order_id")
                        .asText("");

        long returnedAmount =
                razorpayPayment
                        .path("amount")
                        .asLong(-1);

        String returnedCurrency =
                razorpayPayment
                        .path("currency")
                        .asText("");

        String paymentStatus =
                razorpayPayment
                        .path("status")
                        .asText("");

        // Order must match.
        if (!razorpayOrderId.equals(returnedOrderId)) {

            return ResponseEntity.status(409).body(Map.of(
                    "status", "ORDER_MISMATCH",
                    "message",
                    "Razorpay order verification failed."
            ));
        }

        // Amount must match our database.
        if (returnedAmount != payment.getAmountMinor()) {

            return ResponseEntity.status(409).body(Map.of(
                    "status", "AMOUNT_MISMATCH",
                    "message",
                    "Payment amount does not match the registration fee."
            ));
        }

        // Currency must match.
        if (!payment.getCurrency()
                .equalsIgnoreCase(returnedCurrency)) {

            return ResponseEntity.status(409).body(Map.of(
                    "status", "CURRENCY_MISMATCH",
                    "message",
                    "Payment currency does not match the registration fee."
            ));
        }

        // Razorpay payment must be captured.
        if (!"captured".equalsIgnoreCase(paymentStatus)) {

            return ResponseEntity.status(409).body(Map.of(
                    "status", "PAYMENT_NOT_CAPTURED",
                    "paid", false,
                    "message",
                    "Payment was received but has not yet been captured by Razorpay."
            ));
        }

        // --------------------------------------------------------
        // MARK PAYMENT AS PAID
        // --------------------------------------------------------

        payment.setGatewayPaymentId(
                razorpayPaymentId
        );

        payment.setStatus(
                PaymentStatus.PAID
        );

        payment.setPaidAt(
                Instant.now()
        );

        registration.setStatus(
                RegistrationStatus.CONFIRMED
        );

        registration.setActivatedAt(
                Instant.now()
        );

        payments.save(payment);
        registrations.save(registration);

        // --------------------------------------------------------
        // CREATE INVOICE
        // --------------------------------------------------------

        var invoice =
                invoices.createForPaidPayment(
                        payment
                );

        // --------------------------------------------------------
        // QUEUE PAYMENT CONFIRMATION EMAIL
        //
        // Existing NotificationDispatcher will attach
        // the generated PDF invoice.
        // --------------------------------------------------------

        notifications.queuePaymentConfirmed(
                registration,
                invoice
        );

        // --------------------------------------------------------
        // PAYMENT SESSION NO LONGER NEEDED
        // --------------------------------------------------------

        session.removeAttribute(
                "MITRAA_PENDING_PAYMENT_EMAIL"
        );

        session.removeAttribute(
                "MITRAA_PENDING_PAYMENT_AT"
        );

        return ResponseEntity.ok(Map.of(
                "status", "PAID",
                "paid", true,
                "paymentId", razorpayPaymentId,
                "invoiceNumber",
                invoice.getInvoiceNumber(),
                "redirectUrl", "/login.html"
        ));
    }


    // ============================================================
    // PAYMENT STATUS
    // ============================================================

    @GetMapping("/status")
    @Transactional(readOnly = true)
    public Map<String, Object> status(
            Authentication auth) {

        var user =
                users.findByEmailIgnoreCase(
                        auth.getName()
                ).orElseThrow();

        var registration =
                registrations.findByUser(user)
                        .orElseThrow();

        var paid =
                payments
                        .findTopByRegistrationAndStatusOrderByCreatedAtDesc(
                                registration,
                                PaymentStatus.PAID
                        );

        return Map.of(
                "registrationStatus",
                registration.getStatus().name(),

                "paid",
                paid.isPresent(),

                "paymentId",
                paid.map(
                        Payment::getGatewayPaymentId
                ).orElse("")
        );
    }


    // ============================================================
    // PAYMENT VERIFICATION STATUS
    // ============================================================

    @GetMapping("/verification-status")
    @Transactional(readOnly = true)
    public ResponseEntity<?> verificationStatus(
            HttpSession session) {

        RegistrationContext context =
                verifiedRegistration(session);

        if (context == null) {

            return ResponseEntity.status(401).body(Map.of(
                    "status", "PAYMENT_SESSION_EXPIRED",
                    "message",
                    "Payment session expired. Please verify your email again."
            ));
        }

        Registration registration =
                context.registration();

        var paid =
                payments
                        .findTopByRegistrationAndStatusOrderByCreatedAtDesc(
                                registration,
                                PaymentStatus.PAID
                        );

        return ResponseEntity.ok(Map.of(
                "registrationId",
                registration.getRegistrationCode(),

                "email",
                context.email(),

                "country",
                registration.getCountry(),

                "participationType",
                registration
                        .getParticipationType()
                        .name(),

                "registrationStatus",
                registration
                        .getStatus()
                        .name(),

                "paid",
                paid.isPresent(),

                "paymentId",
                paid.map(
                        Payment::getGatewayPaymentId
                ).orElse("")
        ));
    }


    // ============================================================
    // RAZORPAY WEBHOOK
    // ============================================================

    @PostMapping("/webhook")
    @Transactional
    public ResponseEntity<?> webhook(
            @RequestHeader(
                    value = "X-Razorpay-Signature",
                    required = false
            )
            String signature,

            @RequestBody String body) throws Exception {

        if (webhookSecret.isBlank()
                || signature == null
                || !constantTime(
                        hmac(body, webhookSecret),
                        signature
                )) {

            return ResponseEntity.status(401)
                    .body(Map.of("received", false));
        }

        JsonNode root =
                json.readTree(body);

        String event =
                root.path("event").asText();

        // --------------------------------------------------------
        // FAILED PAYMENT
        // --------------------------------------------------------

        if ("payment.failed".equals(event)) {

            JsonNode failed =
                    root.path("payload")
                            .path("payment")
                            .path("entity");

            payments.findByGatewayOrderId(
                    failed.path("order_id").asText()
            ).ifPresent(p -> {

                if (p.getStatus()
                        == PaymentStatus.CREATED) {

                    p.setGatewayPaymentId(
                            failed.path("id").asText()
                    );

                    p.setStatus(
                            PaymentStatus.FAILED
                    );

                    payments.save(p);
                }
            });

            return ResponseEntity.ok(
                    Map.of("received", true)
            );
        }

        // --------------------------------------------------------
        // ONLY PROCESS CAPTURED
        // --------------------------------------------------------

        if (!"payment.captured".equals(event)) {

            return ResponseEntity.ok(
                    Map.of("received", true)
            );
        }

        JsonNode entity =
                root.path("payload")
                        .path("payment")
                        .path("entity");

        Payment payment =
                payments.findByGatewayOrderIdForUpdate(
                        entity.path("order_id").asText()
                ).orElse(null);

        if (payment != null
                && payment.getStatus()
                != PaymentStatus.PAID) {

            long capturedAmount =
                    entity.path("amount")
                            .asLong(-1);

            String capturedCurrency =
                    entity.path("currency")
                            .asText("");

            if (capturedAmount
                    != payment.getAmountMinor()
                    || !payment.getCurrency()
                            .equalsIgnoreCase(
                                    capturedCurrency
                            )) {

                return ResponseEntity.status(409)
                        .body(Map.of(
                                "received", false,
                                "message",
                                "Captured amount or currency does not match the server order."
                        ));
            }

            payment.setGatewayPaymentId(
                    entity.path("id").asText()
            );

            payment.setStatus(
                    PaymentStatus.PAID
            );

            payment.setPaidAt(
                    Instant.now()
            );

            payment.getRegistration()
                    .setStatus(
                            RegistrationStatus.CONFIRMED
                    );

            payment.getRegistration()
                    .setActivatedAt(
                            Instant.now()
                    );

            payments.save(payment);

            registrations.save(
                    payment.getRegistration()
            );

            var invoice =
                    invoices.createForPaidPayment(
                            payment
                    );

            notifications.queuePaymentConfirmed(
                    payment.getRegistration(),
                    invoice
            );
        }

        return ResponseEntity.ok(
                Map.of("received", true)
        );
    }


    // ============================================================
    // RAZORPAY PAYMENT API VERIFICATION
    // ============================================================

    private JsonNode fetchRazorpayPayment(
            String paymentId) throws Exception {

        String basic =
                Base64.getEncoder().encodeToString(
                        (keyId + ":" + keySecret)
                                .getBytes(
                                        StandardCharsets.UTF_8
                                )
                );

        HttpRequest request =
                HttpRequest.newBuilder(
                                URI.create(
                                        "https://api.razorpay.com/v1/payments/"
                                                + paymentId
                                )
                        )
                        .header(
                                "Authorization",
                                "Basic " + basic
                        )
                        .header(
                                "Content-Type",
                                "application/json"
                        )
                        .GET()
                        .build();

        HttpResponse<String> response =
                HttpClient.newHttpClient()
                        .send(
                                request,
                                HttpResponse.BodyHandlers.ofString()
                        );

        if (response.statusCode() / 100 != 2) {

            throw new IllegalStateException(
                    "Unable to verify Razorpay payment."
            );
        }

        return json.readTree(
                response.body()
        );
    }


    // ============================================================
    // PAYMENT SESSION
    // ============================================================

    private RegistrationContext verifiedRegistration(
            HttpSession session) {

        Object emailObject =
                session.getAttribute(
                        "MITRAA_PENDING_PAYMENT_EMAIL"
                );

        Object timeObject =
                session.getAttribute(
                        "MITRAA_PENDING_PAYMENT_AT"
                );

        if (emailObject == null
                || timeObject == null) {

            return null;
        }

        String email =
                String.valueOf(emailObject)
                        .trim()
                        .toLowerCase(
                                Locale.ROOT
                        );

        Instant createdAt;

        if (timeObject instanceof Instant instant) {
            createdAt = instant;
        } else {
            return null;
        }

        // Payment session valid for 30 minutes.
        if (createdAt.plusSeconds(30 * 60)
                .isBefore(Instant.now())) {

            session.removeAttribute(
                    "MITRAA_PENDING_PAYMENT_EMAIL"
            );

            session.removeAttribute(
                    "MITRAA_PENDING_PAYMENT_AT"
            );

            return null;
        }

        var user =
                users.findByEmailIgnoreCase(
                        email
                ).orElse(null);

        if (user == null
                || user.getEmailVerifiedAt() == null) {

            return null;
        }

        var registration =
                registrations.findByUser(user)
                        .orElse(null);

        if (registration == null) {
            return null;
        }

        return new RegistrationContext(
                email,
                registration
        );
    }


    // ============================================================
    // HELPERS
    // ============================================================

    private String hmac(
            String value,
            String secret) throws Exception {

        Mac mac =
                Mac.getInstance(
                        "HmacSHA256"
                );

        mac.init(
                new SecretKeySpec(
                        secret.getBytes(
                                StandardCharsets.UTF_8
                        ),
                        "HmacSHA256"
                )
        );

        return HexFormat.of().formatHex(
                mac.doFinal(
                        value.getBytes(
                                StandardCharsets.UTF_8
                        )
                )
        );
    }


    private boolean constantTime(
            String first,
            String second) {

        return java.security.MessageDigest.isEqual(
                first.getBytes(
                        StandardCharsets.UTF_8
                ),
                second.getBytes(
                        StandardCharsets.UTF_8
                )
        );
    }


    private boolean isBlank(String value) {
        return value == null
                || value.isBlank();
    }


    private record RegistrationContext(
            String email,
            Registration registration
    ) {}
}