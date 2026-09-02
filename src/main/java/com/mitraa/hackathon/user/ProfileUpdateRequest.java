package com.mitraa.hackathon.user;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record ProfileUpdateRequest(
        @NotBlank @Size(max = 120) String fullName,
        @NotBlank @Email @Size(max = 190) String email,
        @NotBlank
        @Pattern(regexp = "^\\+?[0-9]{6,15}$", message = "Phone number must contain 6–15 digits and may start with +")
        String phone) {
}
