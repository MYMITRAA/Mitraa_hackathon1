package com.mitraa.hackathon.admin;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ChangePasswordRequest(

        @NotBlank(message = "Current password is required")
        @Size(max = 72, message = "Current password is invalid")
        String oldPassword,

        @NotBlank(message = "New password is required")
        @Size(
                min = 8,
                max = 12,
                message = "New password must contain 8–12 characters"
        )
        String newPassword,

        @NotBlank(message = "Confirm password is required")
        @Size(
                min = 8,
                max = 12,
                message = "Confirm password must contain 8–12 characters"
        )
        String confirmPassword

) {
}