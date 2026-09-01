package com.mitraa.hackathon.notification;

import com.mitraa.hackathon.invoice.Invoice;
import com.mitraa.hackathon.registration.Registration;
import com.mitraa.hackathon.registration.RegistrationRepository;
import com.mitraa.hackathon.user.User;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class NotificationService {

    private final NotificationOutboxRepository outbox;
    private final RegistrationRepository registrations;
    private final String publicBaseUrl;

    public NotificationService(
            NotificationOutboxRepository outbox,
            RegistrationRepository registrations,
            @Value("${app.public-base-url:http://localhost:8080}")
            String publicBaseUrl
    ) {
        this.outbox = outbox;
        this.registrations = registrations;
        this.publicBaseUrl =
                publicBaseUrl.replaceAll("/+$", "");
    }

    @Transactional
    public void queueRegistrationCreated(
            Registration registration
    ) {
        String nextStep =
                registration.isGuardianConsentRequired()
                ? "After email verification, complete guardian "
                  + "consent and payment from your dashboard."
                : "After email verification, sign in and "
                  + "complete payment to activate your entry.";

        String content =
                greeting(registration.getUser())
                + "<p style='margin:0 0 18px;"
                + "color:#4d4660;line-height:1.7'>"
                + "Your registration has been received and your "
                + "date of birth passed the 10–35 eligibility check."
                + "</p>"
                + detailCard(
                        "Your Player ID",
                        registration.getRegistrationCode(),
                        "Entry: "
                        + registration
                            .getParticipationType()
                            .name()
                        + " &nbsp;•&nbsp; Arena: "
                        + safe(registration.getDomain())
                )
                + notice(nextStep)
                + footerText(
                        "Keep your Player ID for all event "
                        + "communication and support requests."
                );

        queue(
                NotificationChannel.EMAIL,
                registration.getUser().getEmail(),
                "Your MiTRAA registration is received — "
                        + registration.getRegistrationCode(),
                emailShell(
                        "REGISTRATION RECEIVED",
                        "Welcome to MiTRAA Hackathons 2026",
                        content
                ),
                "REGISTRATION_CREATED",
                registration.getRegistrationCode()
        );
    }

    @Transactional
    public void queueAccountCode(
            User user,
            String code,
            String purpose
    ) {
        boolean verification =
                "EMAIL_VERIFY".equals(purpose);

        String title = verification
                ? "Verify your MiTRAA account"
                : "Reset your MiTRAA password";

        String label = verification
                ? "EMAIL VERIFICATION"
                : "PASSWORD RECOVERY";

        String action = verification
                ? "activate your account"
                : "reset your password";

        String content =
                greeting(user)
                + "<p style='margin:0 0 18px;"
                + "color:#4d4660;line-height:1.7'>"
                + "Use this single-use code to "
                + action
                + ". It expires in <b>10 minutes</b>."
                + "</p>"
                + otpCard(code)
                + notice(
                        "Do not forward this email or share this "
                        + "code. MiTRAA staff will never ask for "
                        + "your password or OTP."
                )
                + footerText(
                        "If you did not request this action, "
                        + "you can safely ignore this email."
                );

        queue(
                NotificationChannel.EMAIL,
                user.getEmail(),
                title,
                emailShell(label, title, content),
                purpose,
                user.getEmail()
        );
    }

    @Transactional
    public void queueWelcome(User user) {
        Registration registration =
                registrations.findByUser(user).orElse(null);

        String playerId = registration == null
                ? "Available in your dashboard"
                : registration.getRegistrationCode();

        String nextStep =
                registration != null
                && registration.isGuardianConsentRequired()
                ? "Sign in to request guardian consent. Payment "
                  + "unlocks after the guardian confirms."
                : "Sign in to complete secure payment and "
                  + "activate your registration.";

        String content =
                greeting(user)
                + "<p style='margin:0 0 18px;"
                + "color:#4d4660;line-height:1.7'>"
                + "Your email address is verified successfully. "
                + "Your MiTRAA account is now ready for the next "
                + "registration step."
                + "</p>"
                + detailCard(
                        "Your Player ID",
                        playerId,
                        "Email status: VERIFIED"
                )
                + notice(nextStep)
                + actionButton(
                        "Open secure login",
                        publicBaseUrl + "/login.html"
                )
                + footerText(
                        "Your entry becomes active only after all "
                        + "required consent and payment steps are "
                        + "confirmed."
                );

        queue(
                NotificationChannel.EMAIL,
                user.getEmail(),
                "Email verified — continue your MiTRAA entry",
                emailShell(
                        "EMAIL VERIFIED",
                        "Your account is verified",
                        content
                ),
                "EMAIL_VERIFIED",
                user.getEmail()
        );
    }

    @Transactional
    public void queuePaymentConfirmed(
            Registration registration,
            Invoice invoice
    ) {
        if (outbox.existsByEventTypeAndReferenceId(
                "PAYMENT_CONFIRMED", invoice.getInvoiceNumber())) {
            return;
        }
        String amount = invoice.getCurrency()
                + " "
                + java.math.BigDecimal.valueOf(invoice.getAmountMinor())
                    .movePointLeft(2)
                    .setScale(2)
                    .toPlainString();
        String invoiceUrl = publicBaseUrl
                + "/api/invoices/"
                + invoice.getInvoiceNumber()
                + "/download";
        String content =
                greeting(registration.getUser())
                + detailCard(
                        "Amount paid",
                        amount,
                        "Invoice: " + safe(invoice.getInvoiceNumber())
                        + " &nbsp;•&nbsp; Player ID: "
                        + safe(registration.getRegistrationCode())
                )
                + notice(
                        "Your payment was captured securely and your "
                        + "MiTRAA Hackathons registration is now active."
                )
                + actionButton("Download your invoice", invoiceUrl)
                + footerText(
                        "A PDF invoice is attached to this email. The download "
                        + "button requires you to sign in to your MiTRAA account."
                );

        queue(
                NotificationChannel.EMAIL,
                registration.getUser().getEmail(),
                "Payment confirmed — invoice " + invoice.getInvoiceNumber(),
                emailShell(
                        "PAYMENT CONFIRMED",
                        "Your registration is active",
                        content
                ),
                "PAYMENT_CONFIRMED",
                invoice.getInvoiceNumber()
        );
    }

    @Transactional
    public void queueAgeApproved(
            Registration registration
    ) {
        String message =
                registration.isGuardianConsentRequired()
                ? "Eligibility is approved. Your registered "
                  + "guardian must now confirm participation."
                : "Eligibility is approved. Secure payment is "
                  + "now unlocked.";

        queue(
                NotificationChannel.EMAIL,
                registration.getUser().getEmail(),
                "MiTRAA eligibility approved",
                emailShell(
                        "ELIGIBILITY APPROVED",
                        "Age eligibility confirmed",
                        greeting(registration.getUser())
                                + notice(message)
                ),
                "AGE_APPROVED",
                registration.getRegistrationCode()
        );
    }

    @Transactional
    public void queueGuardianConsent(
            Registration registration,
            String code
    ) {
        String content =
                "<p style='margin:0 0 18px;"
                + "color:#4d4660;line-height:1.7'>"
                + "Hello "
                + safe(registration.getGuardianName())
                + ",</p>"
                + "<p style='margin:0 0 18px;"
                + "color:#4d4660;line-height:1.7'>"
                + "A participant has identified you as their "
                + "guardian. Review the legal policies and use "
                + "this code to confirm participation for <b>"
                + safe(
                        registration
                            .getUser()
                            .getFullName()
                )
                + "</b>.</p>"
                + otpCard(code)
                + notice(
                        "This code expires in 10 minutes. Share it "
                        + "only after reviewing the Terms, Privacy "
                        + "Policy and Child Safety Policy."
                )
                + footerText(
                        "Player ID: "
                        + safe(
                                registration
                                    .getRegistrationCode()
                        )
                );

        queue(
                NotificationChannel.EMAIL,
                registration.getGuardianEmail(),
                "Guardian consent required — "
                        + "MiTRAA Hackathons",
                emailShell(
                        "GUARDIAN CONSENT",
                        "Approve hackathon participation",
                        content
                ),
                "GUARDIAN_CONSENT",
                registration.getRegistrationCode()
        );
    }

    @Transactional
    public void queueGuardianConfirmed(
            Registration registration
    ) {
        queue(
                NotificationChannel.EMAIL,
                registration.getUser().getEmail(),
                "Guardian consent confirmed",
                emailShell(
                        "CONSENT VERIFIED",
                        "Guardian consent confirmed",
                        greeting(registration.getUser())
                                + notice(
                                    "Guardian consent is verified. "
                                    + "Secure payment is now unlocked."
                                )
                                + footerText(
                                    "Player ID: "
                                    + safe(
                                        registration
                                            .getRegistrationCode()
                                    )
                                )
                ),
                "GUARDIAN_CONFIRMED",
                registration.getRegistrationCode()
        );
    }

    @Transactional
    public void queuePasswordChanged(User user) {
        String content =
                greeting(user)
                + notice(
                        "Your password was changed successfully. "
                        + "You can now sign in using the new password."
                )
                + footerText(
                        "If this was not you, contact "
                        + "info@mitratechgroup.com immediately."
                );

        queue(
                NotificationChannel.EMAIL,
                user.getEmail(),
                "Your MiTRAA password was changed",
                emailShell(
                        "SECURITY NOTICE",
                        "Password changed",
                        content
                ),
                "PASSWORD_CHANGED",
                user.getEmail()
        );
    }

    private String emailShell(
            String label,
            String title,
            String content
    ) {
        return "<!doctype html>"
                + "<html>"
                + "<body style='margin:0;"
                + "background:#f2effa;"
                + "font-family:Arial,Helvetica,sans-serif;"
                + "color:#20143d'>"

                + "<table role='presentation' width='100%' "
                + "cellpadding='0' cellspacing='0'>"
                + "<tr>"
                + "<td align='center' "
                + "style='padding:36px 14px'>"

                + "<table role='presentation' width='600' "
                + "cellpadding='0' cellspacing='0' "
                + "style='width:100%;max-width:600px;"
                + "background:#ffffff;"
                + "border-radius:22px;"
                + "overflow:hidden;"
                + "box-shadow:0 16px 50px "
                + "rgba(44,20,91,.16)'>"

                + "<tr>"
                + "<td style='padding:30px 34px;"
                + "background:#35116f;"
                + "background:linear-gradient("
                + "135deg,#24104f,#8737ff 62%,#04bed4);"
                + "color:#ffffff'>"

                + "<div style='font-size:25px;"
                + "font-weight:900;"
                + "letter-spacing:.02em'>"
                + "MY MiTRAA"
                + "</div>"

                + "<div style='font-size:13px;"
                + "opacity:.86;"
                + "margin-top:4px'>"
                + "Hackathons 2026 • "
                + "Human Vision. AI Execution."
                + "</div>"

                + "</td>"
                + "</tr>"

                + "<tr>"
                + "<td style='padding:34px'>"

                + "<div style='font-size:12px;"
                + "font-weight:800;"
                + "letter-spacing:.14em;"
                + "color:#7a35da;"
                + "margin-bottom:10px'>"
                + safe(label)
                + "</div>"

                + "<h1 style='font-size:26px;"
                + "line-height:1.25;"
                + "margin:0 0 22px;"
                + "color:#21113f'>"
                + safe(title)
                + "</h1>"

                + content

                + "</td>"
                + "</tr>"

                + "<tr>"
                + "<td style='padding:20px 34px;"
                + "background:#171025;"
                + "color:#c8bfd8;"
                + "font-size:12px;"
                + "line-height:1.6'>"

                + "MY MiTRAA Technology Private Limited"
                + "<br>"
                + "info@mitratechgroup.com"
                + " • +91 99383 30784"

                + "</td>"
                + "</tr>"

                + "</table>"
                + "</td>"
                + "</tr>"
                + "</table>"
                + "</body>"
                + "</html>";
    }

    private String greeting(User user) {
        return "<p style='margin:0 0 18px;"
                + "color:#4d4660;"
                + "line-height:1.7'>"
                + "Hello <b>"
                + safe(user.getFullName())
                + "</b>,"
                + "</p>";
    }

    private String otpCard(String code) {
        return "<div style='margin:24px 0;"
                + "padding:22px;"
                + "text-align:center;"
                + "background:#f4efff;"
                + "border:1px solid #d7c7ff;"
                + "border-radius:16px'>"

                + "<div style='font-size:11px;"
                + "font-weight:800;"
                + "letter-spacing:.14em;"
                + "color:#675b7a;"
                + "margin-bottom:10px'>"
                + "ONE-TIME CODE"
                + "</div>"

                + "<div style='font-size:38px;"
                + "line-height:1;"
                + "letter-spacing:12px;"
                + "font-weight:900;"
                + "color:#7130db'>"
                + safe(code)
                + "</div>"

                + "</div>";
    }

    private String detailCard(
            String label,
            String value,
            String detail
    ) {
        return "<div style='margin:24px 0;"
                + "padding:20px;"
                + "background:#171025;"
                + "color:#ffffff;"
                + "border-radius:16px'>"

                + "<div style='font-size:11px;"
                + "font-weight:800;"
                + "letter-spacing:.14em;"
                + "color:#64e5f0'>"
                + safe(label)
                + "</div>"

                + "<div style='font-size:26px;"
                + "font-weight:900;"
                + "margin:8px 0'>"
                + safe(value)
                + "</div>"

                + "<div style='font-size:13px;"
                + "color:#c9c0da;"
                + "line-height:1.6'>"
                + detail
                + "</div>"

                + "</div>";
    }

    private String notice(String message) {
        return "<div style='margin:20px 0;"
                + "padding:16px 18px;"
                + "border-left:4px solid #7130db;"
                + "background:#f7f4fc;"
                + "color:#42384f;"
                + "line-height:1.6'>"
                + message
                + "</div>";
    }

    private String actionButton(
            String label,
            String href
    ) {
        return "<div style='margin:26px 0'>"
                + "<a href='"
                + href
                + "' style='display:inline-block;"
                + "padding:14px 22px;"
                + "background:#7130db;"
                + "color:#ffffff;"
                + "text-decoration:none;"
                + "border-radius:12px;"
                + "font-weight:800'>"
                + safe(label)
                + "</a>"
                + "</div>";
    }

    private String footerText(String message) {
        return "<p style='margin:24px 0 0;"
                + "color:#756d82;"
                + "font-size:12px;"
                + "line-height:1.6'>"
                + message
                + "</p>";
    }

    private String safe(String value) {
        if (value == null) {
            return "";
        }

        return value
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }

    private void queue(
            NotificationChannel channel,
            String to,
            String subject,
            String body,
            String event,
            String reference
    ) {
        NotificationOutbox notification =
                new NotificationOutbox();

        notification.setChannel(channel);
        notification.setRecipient(to);
        notification.setSubject(subject);
        notification.setBody(body);
        notification.setEventType(event);
        notification.setReferenceId(reference);

        outbox.save(notification);
    }
}
