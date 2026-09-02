package com.mitraa.hackathon.auth;
import com.mitraa.hackathon.registration.ParticipationType;
import jakarta.validation.constraints.*;
import java.time.LocalDate;
public record RegisterRequest(
 @NotBlank @Size(max=120) String fullName,
 @NotBlank @Email @Size(max=190) String email,
 @NotBlank @Size(min=8,max=12, message="Password must contain 8–12 characters") String password,
 @NotNull @Past LocalDate dateOfBirth,
 @NotBlank @Size(max=40) String phone,
 @NotBlank @Size(max=80) String country,
 @NotBlank @Size(max=80) String city,
 @NotNull ParticipationType participationType,
 @NotBlank @Size(max=160) String domain,
 @AssertTrue(message="Terms acceptance is required") boolean termsAccepted,
 @AssertTrue(message="Privacy Policy acceptance is required") boolean privacyAccepted,
 @AssertTrue(message="Hackathon Rules acceptance is required") boolean rulesAccepted,
 boolean marketingConsent,
 boolean guardianConsent,
 @Size(max=120) String guardianName,
 @Email @Size(max=190) String guardianEmail,
 @Size(max=40) String guardianRelationship,
 @Size(max=40) String guardianPhone,
 @Size(max=80) String guardianCountry
) {}
