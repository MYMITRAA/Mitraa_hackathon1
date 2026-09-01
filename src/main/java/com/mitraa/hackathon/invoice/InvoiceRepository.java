package com.mitraa.hackathon.invoice;

import com.mitraa.hackathon.payment.Payment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface InvoiceRepository extends JpaRepository<Invoice, Long> {
    Optional<Invoice> findByPayment(Payment payment);
    Optional<Invoice> findByInvoiceNumber(String invoiceNumber);
}
