package com.mitraa.hackathon.guardian;
import com.mitraa.hackathon.registration.Registration;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.time.Instant;
public interface GuardianConsentRepository extends JpaRepository<GuardianConsent,Long>{Optional<GuardianConsent> findTopByRegistrationAndConsumedAtIsNullOrderByCreatedAtDesc(Registration registration);long countByRegistrationAndCreatedAtAfter(Registration registration,Instant after);}
