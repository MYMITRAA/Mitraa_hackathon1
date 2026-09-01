package com.mitraa.hackathon.payment;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mitraa.hackathon.notification.NotificationService;
import com.mitraa.hackathon.invoice.InvoiceService;
import com.mitraa.hackathon.registration.RegistrationRepository;
import com.mitraa.hackathon.registration.RegistrationStatus;
import com.mitraa.hackathon.submission.SubmissionRepository;
import com.mitraa.hackathon.submission.SubmissionStatus;
import com.mitraa.hackathon.user.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Map;
import java.time.Instant;
import org.springframework.web.bind.annotation.GetMapping;

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

    @Value("${app.payment.razorpay.key-id:}") String keyId;
    @Value("${app.payment.razorpay.key-secret:}") String keySecret;
    @Value("${app.payment.razorpay.webhook-secret:}") String webhookSecret;

    public PaymentController(UserRepository users, RegistrationRepository registrations,
                             PaymentRepository payments, NotificationService notifications,
                             InvoiceService invoices, PricingService pricing,
                             SubmissionRepository submissions, ObjectMapper json) {
        this.users = users;
        this.registrations = registrations;
        this.payments = payments;
        this.notifications = notifications;
        this.invoices = invoices;
        this.pricing = pricing;
        this.submissions = submissions;
        this.json = json;
    }

    @PostMapping("/create-order")
    @Transactional
    public ResponseEntity<?> createOrder(Authentication auth) throws Exception {
        if (auth == null) return ResponseEntity.status(401).body(Map.of("message", "Login is required."));
        var user = users.findByEmailIgnoreCase(auth.getName()).orElseThrow();
        var registration = registrations.findByUser(user).orElseThrow();

        if (!"DOB_VERIFIED".equals(registration.getAgeVerificationStatus())) {
            return ResponseEntity.status(409).body(Map.of(
                    "status", "VERIFICATION_REQUIRED",
                    "message", "The registration date of birth must pass the 10–35 eligibility check before payment."));
        }
        if (registration.isGuardianConsentRequired() && !registration.isGuardianConsentReceived()) {
            return ResponseEntity.status(409).body(Map.of("status","GUARDIAN_REQUIRED","message","Verified guardian consent is required before payment."));
        }
        var submission = submissions.findByRegistration(registration);
        if (submission.isEmpty() || submission.get().getStatus() == SubmissionStatus.DRAFT) {
            return ResponseEntity.status(409).body(Map.of(
                    "status", "SUBMISSION_REQUIRED",
                    "message", "Final POC submission is required before payment."));
        }
        if (registration.getStatus() == RegistrationStatus.CONFIRMED) {
            return ResponseEntity.status(409).body(Map.of("status","ALREADY_ACTIVE","message","Registration is already active."));
        }
        if (registration.getStatus() != RegistrationStatus.PENDING_PAYMENT) {
            return ResponseEntity.status(409).body(Map.of("status","FLOW_REQUIRED","message","Complete all eligibility steps before payment."));
        }
        if (keyId.isBlank() || keySecret.isBlank()) {
            return ResponseEntity.status(503).body(Map.of(
                    "status", "NOT_CONFIGURED",
                    "message", "Razorpay credentials are not configured."));
        }

        var existing=payments.findTopByRegistrationAndStatusOrderByCreatedAtDesc(registration,PaymentStatus.CREATED);
        if(existing.isPresent()){Payment p=existing.get();return ResponseEntity.ok(Map.of("keyId",keyId,"orderId",p.getGatewayOrderId(),"amount",p.getAmountMinor(),"currency",p.getCurrency(),"displayAmount",p.getCurrency()+" "+String.format("%.2f",p.getAmountMinor()/100.0),"registrationId",registration.getRegistrationCode()));}
        EntryPrice entryPrice = pricing.priceFor(
                registration.getCountry(), registration.getParticipationType());
        String receipt = registration.getRegistrationCode();
        String requestBody = json.writeValueAsString(Map.of(
                "amount", entryPrice.amountMinor(),
                "currency", entryPrice.currency(),
                "receipt", receipt,
                "notes", Map.of(
                        "registrationId", receipt,
                        "country", registration.getCountry(),
                        "entryType", registration.getParticipationType().name())));

        String basic = Base64.getEncoder().encodeToString(
                (keyId + ":" + keySecret).getBytes(StandardCharsets.UTF_8));
        HttpRequest request = HttpRequest.newBuilder(URI.create("https://api.razorpay.com/v1/orders"))
                .header("Authorization", "Basic " + basic)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                .build();
        HttpResponse<String> response = HttpClient.newHttpClient()
                .send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() / 100 != 2) {
            return ResponseEntity.status(502).body(Map.of(
                    "status", "GATEWAY_ERROR",
                    "message", "Payment gateway rejected order creation."));
        }

        JsonNode node = json.readTree(response.body());
        Payment payment = new Payment();
        payment.setRegistration(registration);
        payment.setAmountMinor(entryPrice.amountMinor());
        payment.setCurrency(entryPrice.currency());
        payment.setGatewayOrderId(node.path("id").asText());
        payments.save(payment);

        return ResponseEntity.ok(Map.of(
                "keyId", keyId,
                "orderId", payment.getGatewayOrderId(),
                "amount", entryPrice.amountMinor(),
                "currency", entryPrice.currency(),
                "displayAmount", entryPrice.displayAmount(),
                "registrationId", receipt));
    }

    @GetMapping("/status") @Transactional(readOnly=true)
    public Map<String,Object> status(Authentication auth){var user=users.findByEmailIgnoreCase(auth.getName()).orElseThrow();var registration=registrations.findByUser(user).orElseThrow();var paid=payments.findTopByRegistrationAndStatusOrderByCreatedAtDesc(registration,PaymentStatus.PAID);return Map.of("registrationStatus",registration.getStatus().name(),"paid",paid.isPresent(),"paymentId",paid.map(Payment::getGatewayPaymentId).orElse(""));}

    @PostMapping("/webhook")
    @Transactional
    public ResponseEntity<?> webhook(
            @RequestHeader(value = "X-Razorpay-Signature", required = false) String signature,
            @RequestBody String body) throws Exception {
        if (webhookSecret.isBlank() || signature == null
                || !constantTime(hmac(body, webhookSecret), signature)) {
            return ResponseEntity.status(401).body(Map.of("received", false));
        }

        JsonNode root = json.readTree(body);
        String event=root.path("event").asText();
        if ("payment.failed".equals(event)) {JsonNode failed=root.path("payload").path("payment").path("entity");payments.findByGatewayOrderId(failed.path("order_id").asText()).ifPresent(p->{if(p.getStatus()==PaymentStatus.CREATED){p.setGatewayPaymentId(failed.path("id").asText());p.setStatus(PaymentStatus.FAILED);payments.save(p);}});return ResponseEntity.ok(Map.of("received",true));}
        if (!"payment.captured".equals(event)) {
            return ResponseEntity.ok(Map.of("received", true));
        }

        JsonNode entity = root.path("payload").path("payment").path("entity");
        Payment payment = payments.findByGatewayOrderIdForUpdate(
                entity.path("order_id").asText()).orElse(null);
        if (payment != null && payment.getStatus() != PaymentStatus.PAID) {
            long capturedAmount = entity.path("amount").asLong(-1);
            String capturedCurrency = entity.path("currency").asText("");
            if (capturedAmount != payment.getAmountMinor()
                    || !payment.getCurrency().equalsIgnoreCase(capturedCurrency)) {
                return ResponseEntity.status(409).body(Map.of(
                        "received", false,
                        "message", "Captured amount or currency does not match the server order."));
            }
            payment.setGatewayPaymentId(entity.path("id").asText());
            payment.setStatus(PaymentStatus.PAID);
            payment.setPaidAt(Instant.now());
            payment.getRegistration().setStatus(RegistrationStatus.CONFIRMED);
            payment.getRegistration().setActivatedAt(Instant.now());
            payments.save(payment);
            registrations.save(payment.getRegistration());
            var invoice = invoices.createForPaidPayment(payment);
            notifications.queuePaymentConfirmed(payment.getRegistration(), invoice);
        }
        return ResponseEntity.ok(Map.of("received", true));
    }

    private String hmac(String body, String secret) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return HexFormat.of().formatHex(mac.doFinal(body.getBytes(StandardCharsets.UTF_8)));
    }

    private boolean constantTime(String first, String second) {
        return java.security.MessageDigest.isEqual(
                first.getBytes(StandardCharsets.UTF_8), second.getBytes(StandardCharsets.UTF_8));
    }
}
