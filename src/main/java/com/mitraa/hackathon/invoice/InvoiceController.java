package com.mitraa.hackathon.invoice;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;

@RestController
@RequestMapping("/api/invoices")
public class InvoiceController {
    private final InvoiceService invoiceService;

    public InvoiceController(InvoiceService invoiceService) {
        this.invoiceService = invoiceService;
    }

    @GetMapping("/{invoiceNumber}/download")
    public ResponseEntity<byte[]> download(@PathVariable String invoiceNumber, Authentication authentication) {
        Invoice invoice = invoiceService.requireByNumber(invoiceNumber);
        if (!mayAccess(authentication, invoice)) {
            return ResponseEntity.status(403).build();
        }

        byte[] pdf = invoiceService.generatePdf(invoice);
        String filename = "MiTRAA-Invoice-" + invoice.getInvoiceNumber() + ".pdf";
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentLength(pdf.length)
                .body(pdf);
    }

    private boolean mayAccess(Authentication authentication, Invoice invoice) {
        if (authentication == null || !authentication.isAuthenticated()) return false;
        boolean admin = authentication.getAuthorities().stream().anyMatch(authority ->
                "ROLE_ADMIN".equals(authority.getAuthority())
                        || "ROLE_SUPER_ADMIN".equals(authority.getAuthority()));
        return admin || invoice.getParticipantEmail().equalsIgnoreCase(authentication.getName());
    }

    @ExceptionHandler(InvoiceService.InvoiceNotFoundException.class)
    ResponseEntity<byte[]> notFound() {
        return ResponseEntity.status(404)
                .contentType(new MediaType("text", "plain", StandardCharsets.UTF_8))
                .body("Invoice not found.".getBytes(StandardCharsets.UTF_8));
    }
}
