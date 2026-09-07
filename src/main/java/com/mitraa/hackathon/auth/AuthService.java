package com.mitraa.hackathon.auth;

import com.mitraa.hackathon.registration.*;
import com.mitraa.hackathon.user.*;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Clock;
import java.time.LocalDate;
import java.time.Period;
import java.time.Instant;
import java.util.Locale;
import java.util.UUID;
import com.mitraa.hackathon.notification.NotificationService;
import com.mitraa.hackathon.common.InputNormalizer;

@Service
public class AuthService {
 private final UserRepository users; private final RegistrationRepository registrations; private final PasswordEncoder encoder; private final NotificationService notifications; private final AccountSecurityService accountSecurity; private final Clock clock=Clock.systemUTC();
 public AuthService(UserRepository u, RegistrationRepository r, PasswordEncoder e, NotificationService n,AccountSecurityService a){users=u;registrations=r;encoder=e;notifications=n;accountSecurity=a;}
 @Transactional
 public Registration register(RegisterRequest req){
   String email=req.email().trim().toLowerCase(Locale.ROOT);
   if(users.existsByEmailIgnoreCase(email)) throw new IllegalArgumentException("An account already exists for this email.");
   int age=Period.between(req.dateOfBirth(), LocalDate.now(clock)).getYears();
   if(age<10 || age>35) throw new IllegalArgumentException("Participants must be aged 10–35.");
   if(age<18 && !req.guardianConsent()) throw new IllegalArgumentException("A guardian must be available to complete consent for participants under 18.");
   if(age<18 && (req.guardianName()==null||req.guardianName().isBlank()||req.guardianEmail()==null||req.guardianEmail().isBlank()||req.guardianRelationship()==null||req.guardianRelationship().isBlank()||req.guardianPhone()==null||req.guardianPhone().isBlank()||req.guardianCountry()==null||req.guardianCountry().isBlank())) throw new IllegalArgumentException("Guardian name, relationship, email, mobile number and country are required for participants aged 10–17.");
   InputNormalizer.requireNoOuterPasswordWhitespace(req.password());
   User user=new User(); user.setFullName(InputNormalizer.capitalizeFirstCharacter(req.fullName())); user.setEmail(email); user.setPasswordHash(encoder.encode(req.password())); user.setRole(Role.PARTICIPANT); user.setEnabled(false); users.save(user);
   Instant consentTime=Instant.now(); Registration reg=new Registration(); reg.setUser(user); reg.setRegistrationCode(code(req.participationType())); reg.setDateOfBirth(req.dateOfBirth()); reg.setPhone(InputNormalizer.normalizePhone(req.phone(), req.country())); reg.setCountry(req.country().trim()); reg.setCity(req.city().trim()); reg.setParticipationType(req.participationType()); reg.setDomain(req.domain().trim()); reg.setAgeVerificationStatus("DOB_VERIFIED"); reg.setGuardianConsentRequired(age<18); reg.setGuardianConsentReceived(age>=18); reg.setStatus(RegistrationStatus.PENDING_PAYMENT); reg.setGuardianName(age<18?InputNormalizer.capitalizeFirstCharacter(req.guardianName()):null); reg.setGuardianEmail(age<18?req.guardianEmail().trim().toLowerCase(Locale.ROOT):null); reg.setGuardianRelationship(age<18?req.guardianRelationship().trim():null); reg.setGuardianPhone(age<18?req.guardianPhone().trim():null); reg.setGuardianCountry(age<18?req.guardianCountry().trim():null); reg.setTermsAcceptedAt(consentTime); reg.setPrivacyAcceptedAt(consentTime); reg.setRulesAcceptedAt(consentTime); reg.setMarketingConsent(req.marketingConsent()); registrations.save(reg);
   notifications.queueRegistrationCreated(reg); accountSecurity.sendVerification(user); return reg;
 }
 @Transactional
 public void changePassword(String email, ChangePasswordRequest request){
   User user=users.findByEmailIgnoreCase(email).orElseThrow(() -> new IllegalArgumentException("Account not found."));
   InputNormalizer.requireNoOuterPasswordWhitespace(request.newPassword());
   InputNormalizer.requireNoOuterPasswordWhitespace(request.confirmPassword());
   if(!encoder.matches(request.oldPassword(),user.getPasswordHash())) throw new IllegalArgumentException("Current password is incorrect.");
   if(!request.newPassword().equals(request.confirmPassword())) throw new IllegalArgumentException("New password and confirm password do not match.");
   if(encoder.matches(request.newPassword(),user.getPasswordHash())) throw new IllegalArgumentException("New password must be different from the current password.");
   user.setPasswordHash(encoder.encode(request.newPassword()));
   users.save(user);
   notifications.queuePasswordChanged(user);
 }
 private String code(ParticipationType type){String prefix=type==ParticipationType.TEAM?"T":"I"; return "MITRAA26-"+prefix+"-"+UUID.randomUUID().toString().replace("-","").substring(0,8).toUpperCase(Locale.ROOT);}
}
