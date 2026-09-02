package com.mitraa.hackathon.user;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/profile")
public class ProfileController {

    private final ProfileService profiles;

    public ProfileController(ProfileService profiles) {
        this.profiles = profiles;
    }

    @GetMapping
    public ProfileService.ProfileView get(Authentication authentication) {
        return profiles.getProfile(requireEmail(authentication));
    }

    @PutMapping
    public ProfileService.ProfileView update(Authentication authentication,
                                             @Valid @RequestBody ProfileUpdateRequest request) {
        return profiles.updateProfile(requireEmail(authentication), request);
    }

    @PostMapping("/email/resend")
    public ProfileService.ProfileView resend(Authentication authentication) {
        return profiles.resendEmailChangeCode(requireEmail(authentication));
    }

    @PostMapping("/email/verify")
    public ResponseEntity<?> verify(Authentication authentication,
                                    @RequestBody Map<String, String> body,
                                    HttpServletRequest request) {
        ProfileService.ProfileView profile = profiles.verifyEmailChange(
                requireEmail(authentication), body.get("otp"));
        HttpSession session = request.getSession(false);
        if (session != null) {
            session.invalidate();
        }
        return ResponseEntity.ok(Map.of(
                "message", "Email verified successfully. Please sign in with your new email address.",
                "email", profile.email(),
                "emailVerified", true,
                "loginRequired", true));
    }

    private String requireEmail(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new IllegalArgumentException("Sign in to manage your profile.");
        }
        return authentication.getName();
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<?> badRequest(IllegalArgumentException exception) {
        return ResponseEntity.badRequest().body(Map.of("message", exception.getMessage()));
    }
}
