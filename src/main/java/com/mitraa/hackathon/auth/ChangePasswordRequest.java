package com.mitraa.hackathon.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.Pattern;

public record ChangePasswordRequest(
        @NotBlank @Size(max = 72) String oldPassword,
        @NotBlank
        @Size(min = 8, max = 12, message = "New password must contain 8–12 characters")
        @Pattern(regexp = "^\\S(?:.*\\S)?$", message = "New password must not start or end with a space")
        String newPassword,

        @NotBlank
        @Size(min = 8, max = 12, message = "Confirm password must contain 8–12 characters")
        @Pattern(regexp = "^\\S(?:.*\\S)?$", message = "Confirm password must not start or end with a space")
        String confirmPassword) {
}
