package com.mitraa.hackathon.guardian;
import com.mitraa.hackathon.registration.Registration;
import jakarta.persistence.*;
import java.time.Instant;
@Entity @Table(name="guardian_consents")
public class GuardianConsent {
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
 @ManyToOne(optional=false,fetch=FetchType.LAZY) @JoinColumn(name="registration_id") private Registration registration;
 @Column(nullable=false) private String codeHash;
 @Column(nullable=false) private Instant expiresAt;
 @Column(nullable=false) private int attempts;
 private Instant consumedAt;
 @Column(length=64) private String consentIp;
 @Column(length=500) private String consentUserAgent;
 @Column(length=40) private String legalVersion;
 private Instant consentedAt;
 @Column(nullable=false,updatable=false) private Instant createdAt=Instant.now();
 public Long getId(){return id;} public Registration getRegistration(){return registration;} public void setRegistration(Registration v){registration=v;} public String getCodeHash(){return codeHash;} public void setCodeHash(String v){codeHash=v;} public Instant getExpiresAt(){return expiresAt;} public void setExpiresAt(Instant v){expiresAt=v;} public int getAttempts(){return attempts;} public void setAttempts(int v){attempts=v;} public Instant getConsumedAt(){return consumedAt;} public void setConsumedAt(Instant v){consumedAt=v;} public Instant getCreatedAt(){return createdAt;}
 public String getConsentIp(){return consentIp;} public void setConsentIp(String v){consentIp=v;} public String getConsentUserAgent(){return consentUserAgent;} public void setConsentUserAgent(String v){consentUserAgent=v;} public String getLegalVersion(){return legalVersion;} public void setLegalVersion(String v){legalVersion=v;} public Instant getConsentedAt(){return consentedAt;} public void setConsentedAt(Instant v){consentedAt=v;}
}
