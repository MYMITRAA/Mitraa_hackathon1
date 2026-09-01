package com.mitraa.hackathon.payment;

import com.mitraa.hackathon.registration.Registration;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface PaymentRepository extends JpaRepository<Payment, Long> {
    Optional<Payment> findByGatewayOrderId(String id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Payment p where p.gatewayOrderId = :orderId")
    Optional<Payment> findByGatewayOrderIdForUpdate(@Param("orderId") String orderId);

    Optional<Payment> findTopByRegistrationAndStatusOrderByCreatedAtDesc(
            Registration registration, PaymentStatus status);

    long countByStatus(PaymentStatus status);
}
