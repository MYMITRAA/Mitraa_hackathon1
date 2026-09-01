package com.mitraa.hackathon.registration;
import com.mitraa.hackathon.user.User;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
public interface RegistrationRepository extends JpaRepository<Registration,Long>{ Optional<Registration> findByUser(User user); Optional<Registration> findByRegistrationCode(String code); long countByParticipationType(ParticipationType type); long countByStatus(RegistrationStatus status); }
