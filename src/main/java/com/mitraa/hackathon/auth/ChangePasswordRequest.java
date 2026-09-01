package com.mitraa.hackathon.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ChangePasswordRequest(
        @NotBlank @Size(max = 72) String oldPassword,
        @NotBlank @Size(min = 10, max = 72) String newPassword,
        @NotBlank @Size(min = 10, max = 72) String confirmPassword) {
}
