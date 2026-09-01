package com.mitraa.hackathon.registration;

import com.mitraa.hackathon.user.User;
import jakarta.persistence.*;
import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name="registrations", uniqueConstraints=@UniqueConstraint(name="uk_registration_code", columnNames="registrationCode"))
public class Registration {
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
 @OneToOne(optional=false, fetch=FetchType.LAZY) @JoinColumn(name="user_id", nullable=false, unique=true) private User user;
 @Column(nullable=false, unique=true, length=32) private String registrationCode;
 @Column(nullable=false) private LocalDate dateOfBirth;
 @Column(nullable=false, length=40) private String phone;
 @Column(nullable=false, length=80) private String country;
 @Column(nullable=false, length=80) private String city;
 @Enumerated(EnumType.STRING) @Column(nullable=false, length=20) private ParticipationType participationType;
 @Column(nullable=false, length=160) private String domain;
 @Enumerated(EnumType.STRING) @Column(nullable=false, length=30) private RegistrationStatus status = RegistrationStatus.PENDING_VERIFICATION;
 @Column(nullable=false) private boolean guardianConsentRequired;
 @Column(nullable=false) private boolean guardianConsentReceived;
 @Column(length=120) private String guardianName;
 @Column(length=190) private String guardianEmail;
 @Column(length=40) private String guardianRelationship;
 @Column(length=40) private String guardianPhone;
 @Column(length=80) private String guardianCountry;
 @Column(nullable=false, length=30) private String ageVerificationStatus="NOT_STARTED";
 @Column(length=120) private String verificationApplicantId;
 @Column(nullable=false) private Instant termsAcceptedAt;
 @Column(nullable=false) private Instant privacyAcceptedAt;
 @Column(nullable=false) private Instant rulesAcceptedAt;
 @Column(nullable=false) private boolean marketingConsent;
 @Column(nullable=false, updatable=false) private Instant createdAt=Instant.now();
 private Instant activatedAt;
 public Long getId(){return id;} public User getUser(){return user;} public void setUser(User v){user=v;} public String getRegistrationCode(){return registrationCode;} public void setRegistrationCode(String v){registrationCode=v;}
 public LocalDate getDateOfBirth(){return dateOfBirth;} public void setDateOfBirth(LocalDate v){dateOfBirth=v;} public String getPhone(){return phone;} public void setPhone(String v){phone=v;} public String getCountry(){return country;} public void setCountry(String v){country=v;} public String getCity(){return city;} public void setCity(String v){city=v;}
 public ParticipationType getParticipationType(){return participationType;} public void setParticipationType(ParticipationType v){participationType=v;} public String getDomain(){return domain;} public void setDomain(String v){domain=v;} public RegistrationStatus getStatus(){return status;} public void setStatus(RegistrationStatus v){status=v;} public Instant getCreatedAt(){return createdAt;}
 public boolean isGuardianConsentRequired(){return guardianConsentRequired;} public void setGuardianConsentRequired(boolean v){guardianConsentRequired=v;} public boolean isGuardianConsentReceived(){return guardianConsentReceived;} public void setGuardianConsentReceived(boolean v){guardianConsentReceived=v;} public String getAgeVerificationStatus(){return ageVerificationStatus;} public void setAgeVerificationStatus(String v){ageVerificationStatus=v;} public String getVerificationApplicantId(){return verificationApplicantId;} public void setVerificationApplicantId(String v){verificationApplicantId=v;}
 public String getGuardianName(){return guardianName;} public void setGuardianName(String v){guardianName=v;} public String getGuardianEmail(){return guardianEmail;} public void setGuardianEmail(String v){guardianEmail=v;} public Instant getActivatedAt(){return activatedAt;} public void setActivatedAt(Instant v){activatedAt=v;}
 public String getGuardianRelationship(){return guardianRelationship;} public void setGuardianRelationship(String v){guardianRelationship=v;} public String getGuardianPhone(){return guardianPhone;} public void setGuardianPhone(String v){guardianPhone=v;} public String getGuardianCountry(){return guardianCountry;} public void setGuardianCountry(String v){guardianCountry=v;}
 public Instant getTermsAcceptedAt(){return termsAcceptedAt;} public void setTermsAcceptedAt(Instant v){termsAcceptedAt=v;} public Instant getPrivacyAcceptedAt(){return privacyAcceptedAt;} public void setPrivacyAcceptedAt(Instant v){privacyAcceptedAt=v;} public Instant getRulesAcceptedAt(){return rulesAcceptedAt;} public void setRulesAcceptedAt(Instant v){rulesAcceptedAt=v;} public boolean isMarketingConsent(){return marketingConsent;} public void setMarketingConsent(boolean v){marketingConsent=v;}
}
