package com.mitraa.hackathon.invoice;

import com.mitraa.hackathon.payment.Payment;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

@Service
public class InvoiceService {

    private static final DateTimeFormatter DATE_TIME =
            DateTimeFormatter
                    .ofPattern(
                            "dd MMM uuuu, HH:mm 'UTC'",
                            Locale.ENGLISH
                    )
                    .withZone(ZoneOffset.UTC);

    private final InvoiceRepository invoices;

    public InvoiceService(InvoiceRepository invoices) {
        this.invoices = invoices;
    }

    @Transactional
    public Invoice createForPaidPayment(Payment payment) {

        if (
                payment.getStatus() == null ||
                !"PAID".equals(payment.getStatus().name())
        ) {
            throw new IllegalStateException(
                    "An invoice can only be created for a paid payment."
            );
        }

        if (
                payment.getGatewayPaymentId() == null ||
                payment.getGatewayPaymentId().isBlank()
        ) {
            throw new IllegalStateException(
                    "The captured gateway payment ID is required."
            );
        }

        return invoices
                .findByPayment(payment)
                .orElseGet(() -> {

                    var registration =
                            payment.getRegistration();

                    var user =
                            registration.getUser();

                    Invoice invoice =
                            new Invoice();

                    invoice.setPayment(payment);

                    invoice.setPlayerId(
                            registration.getRegistrationCode()
                    );

                    invoice.setParticipantName(
                            user.getFullName()
                    );

                    invoice.setParticipantEmail(
                            user.getEmail()
                    );

                    invoice.setEntryType(
                            registration
                                    .getParticipationType()
                                    .name()
                    );

                    invoice.setAmountMinor(
                            payment.getAmountMinor()
                    );

                    invoice.setCurrency(
                            payment
                                    .getCurrency()
                                    .toUpperCase(Locale.ROOT)
                    );

                    /*
                     * Internal payment tracking.
                     * These values remain stored in DB.
                     * They are simply not shown on the PDF.
                     */
                    invoice.setGatewayOrderId(
                            payment.getGatewayOrderId()
                    );

                    invoice.setGatewayPaymentId(
                            payment.getGatewayPaymentId()
                    );

                    invoice.setPaymentDate(
                            payment.getPaidAt() == null
                                    ? Instant.now()
                                    : payment.getPaidAt()
                    );

                    invoice.setPaymentStatus(
                            payment.getStatus().name()
                    );

                    invoices.saveAndFlush(invoice);

                    int year =
                            invoice
                                    .getPaymentDate()
                                    .atZone(ZoneOffset.UTC)
                                    .getYear();

                    invoice.setInvoiceNumber(
                            "MITRAA-INV-" +
                            year +
                            "-" +
                            String.format(
                                    "%08d",
                                    invoice.getId()
                            )
                    );

                    return invoices.save(invoice);
                });
    }

    @Transactional(readOnly = true)
    public Invoice requireByNumber(
            String invoiceNumber
    ) {

        return invoices
                .findByInvoiceNumber(invoiceNumber)
                .orElseThrow(
                        () ->
                                new InvoiceNotFoundException(
                                        invoiceNumber
                                )
                );
    }

    @Transactional(readOnly = true)
    public byte[] generatePdf(
            String invoiceNumber
    ) {

        return generatePdf(
                requireByNumber(invoiceNumber)
        );
    }

    public byte[] generatePdf(
            Invoice invoice
    ) {

        try (
                PDDocument document =
                        new PDDocument();

                ByteArrayOutputStream output =
                        new ByteArrayOutputStream()
        ) {

            PDPage page =
                    new PDPage(
                            PDRectangle.A4
                    );

            document.addPage(page);

            try (
                    PDPageContentStream content =
                            new PDPageContentStream(
                                    document,
                                    page
                            )
            ) {

                float width =
                        page
                                .getMediaBox()
                                .getWidth();

                /*
                 * =================================================
                 * HEADER BACKGROUND
                 * =================================================
                 */
                content.setNonStrokingColor(
                        36,
                        16,
                        79
                );

                content.addRect(
                        0,
                        700,
                        width,
                        142
                );

                content.fill();

                /*
                 * =================================================
                 * MITRAA LOGO
                 * =================================================
                 *
                 * Expected file:
                 *
                 * src/main/resources/invoice/mitraa-white-logo.png
                 *
                 * IMPORTANT:
                 * The PNG itself should have transparent background.
                 */
                ClassPathResource logoResource =
                        new ClassPathResource(
                                "invoice/mitraa-white-logo.png"
                        );

                PDImageXObject logo =
                        PDImageXObject
                                .createFromByteArray(
                                        document,
                                        logoResource
                                                .getInputStream()
                                                .readAllBytes(),
                                        "mitraa-white-logo"
                                );

                /*
                 * No background rectangle.
                 * No border.
                 * Logo is drawn directly over the purple header.
                 */
                content.drawImage(
                        logo,
                        46,
                        758,
                        130,
                        40
                );

                /*
                 * Company subtitle under logo.
                 */
                text(
                        content,
                        PDType1Font.HELVETICA,
                        10,
                        46,
                        742,
                        "Technology Private Limited",
                        225,
                        218,
                        238
                );

                /*
                 * Payment invoice title.
                 */
                text(
                        content,
                        PDType1Font.HELVETICA_BOLD,
                        18,
                        400,
                        790,
                        "PAYMENT INVOICE",
                        255,
                        255,
                        255
                );

                text(
                        content,
                        PDType1Font.HELVETICA,
                        10,
                        400,
                        770,
                        invoice.getInvoiceNumber(),
                        215,
                        204,
                        240
                );

                /*
                 * =================================================
                 * PAYMENT CONFIRMATION
                 * =================================================
                 */
                text(
                        content,
                        PDType1Font.HELVETICA_BOLD,
                        14,
                        46,
                        654,
                        "Payment confirmation",
                        36,
                        16,
                        79
                );

                text(
                        content,
                        PDType1Font.HELVETICA,
                        10,
                        46,
                        632,
                        "Thank you. Your MiTRAA Hackathons registration is confirmed.",
                        70,
                        63,
                        82
                );

                /*
                 * =================================================
                 * INVOICE DETAILS
                 * =================================================
                 */
                float y = 590;

                y = row(
                        content,
                        y,
                        "Invoice number",
                        invoice.getInvoiceNumber()
                );

                y = row(
                        content,
                        y,
                        "Player ID",
                        invoice.getPlayerId()
                );

                y = row(
                        content,
                        y,
                        "Participant",
                        invoice.getParticipantName()
                );

                y = row(
                        content,
                        y,
                        "Email",
                        invoice.getParticipantEmail()
                );

                y = row(
                        content,
                        y,
                        "Entry",
                        titleCase(
                                invoice.getEntryType()
                        ) +
                        " entry"
                );

                y = row(
                        content,
                        y,
                        "Amount paid",
                        money(invoice)
                );

                /*
                 * Razorpay Order ID removed from customer invoice.
                 * Razorpay Payment ID removed from customer invoice.
                 *
                 * Both are still preserved internally in DB.
                 */

                y = row(
                        content,
                        y,
                        "Payment date",
                        DATE_TIME.format(
                                invoice.getPaymentDate()
                        )
                );

                y = row(
                        content,
                        y,
                        "Payment status",
                        invoice.getPaymentStatus()
                );

                /*
                 * =================================================
                 * FOOTER
                 * =================================================
                 */
                content.setNonStrokingColor(
                        247,
                        244,
                        252
                );

                content.addRect(
                        46,
                        105,
                        width - 92,
                        94
                );

                content.fill();

                text(
                        content,
                        PDType1Font.HELVETICA_BOLD,
                        11,
                        62,
                        176,
                        "Issued by",
                        36,
                        16,
                        79
                );

                text(
                        content,
                        PDType1Font.HELVETICA,
                        10,
                        62,
                        157,
                        "MY MiTRAA Technology Private Limited",
                        70,
                        63,
                        82
                );

                text(
                        content,
                        PDType1Font.HELVETICA,
                        10,
                        62,
                        140,
                        "info@mitratechgroup.com  |  +91 99383 30784",
                        70,
                        63,
                        82
                );

                text(
                        content,
                        PDType1Font.HELVETICA,
                        9,
                        62,
                        118,
                        "Registration fee receipt. Keep this document for your records.",
                        105,
                        96,
                        118
                );
            }

            document.save(output);

            return output.toByteArray();

        } catch (IOException exception) {

            throw new IllegalStateException(
                    "Could not generate the invoice PDF.",
                    exception
            );
        }
    }

    private float row(
            PDPageContentStream content,
            float y,
            String label,
            String value
    ) throws IOException {

        content.setStrokingColor(
                222,
                216,
                232
        );

        content.moveTo(
                46,
                y - 10
        );

        content.lineTo(
                549,
                y - 10
        );

        content.stroke();

        text(
                content,
                PDType1Font.HELVETICA_BOLD,
                9,
                46,
                y + 7,
                label.toUpperCase(
                        Locale.ROOT
                ),
                113,
                48,
                219
        );

        text(
                content,
                PDType1Font.HELVETICA,
                10,
                215,
                y + 7,
                value,
                43,
                36,
                52
        );

        return y - 42;
    }

    private void text(
            PDPageContentStream content,
            PDType1Font font,
            float size,
            float x,
            float y,
            String value,
            int red,
            int green,
            int blue
    ) throws IOException {

        content.beginText();

        content.setFont(
                font,
                size
        );

        content.setNonStrokingColor(
                red,
                green,
                blue
        );

        content.newLineAtOffset(
                x,
                y
        );

        content.showText(
                pdfSafe(value)
        );

        content.endText();
    }

    private String money(
            Invoice invoice
    ) {

        BigDecimal amount =
                BigDecimal
                        .valueOf(
                                invoice.getAmountMinor()
                        )
                        .movePointLeft(2)
                        .setScale(
                                2,
                                RoundingMode.UNNECESSARY
                        );

        return invoice.getCurrency() +
                " " +
                amount.toPlainString();
    }

    private String titleCase(
            String value
    ) {

        String lower =
                value == null
                        ? ""
                        : value.toLowerCase(
                                Locale.ROOT
                        );

        return lower.isEmpty()
                ? lower
                : Character
                        .toUpperCase(
                                lower.charAt(0)
                        ) +
                        lower.substring(1);
    }

    private String pdfSafe(
            String value
    ) {

        if (value == null) {
            return "";
        }

        return value
                .replace(
                        '\u2013',
                        '-'
                )
                .replace(
                        '\u2014',
                        '-'
                )
                .replace(
                        '\u2022',
                        '|'
                )
                .replaceAll(
                        "[^\\x20-\\x7E]",
                        "?"
                );
    }

    public static class InvoiceNotFoundException
            extends RuntimeException {

        public InvoiceNotFoundException(
                String invoiceNumber
        ) {

            super(
                    "Invoice not found: " +
                    invoiceNumber
            );
        }
    }
}