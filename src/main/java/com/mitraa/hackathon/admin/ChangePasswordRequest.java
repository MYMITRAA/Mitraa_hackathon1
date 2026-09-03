package com.mitraa.hackathon.admin;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.Pattern;

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
        @Pattern(regexp = "^\\S(?:.*\\S)?$", message = "New password must not start or end with a space")
        String newPassword,

        @NotBlank(message = "Confirm password is required")
        @Size(
                min = 8,
                max = 12,
                message = "Confirm password must contain 8–12 characters"
        )
        @Pattern(regexp = "^\\S(?:.*\\S)?$", message = "Confirm password must not start or end with a space")
        String confirmPassword

) {
}
