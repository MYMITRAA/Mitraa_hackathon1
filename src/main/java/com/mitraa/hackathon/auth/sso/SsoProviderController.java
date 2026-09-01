package com.mitraa.hackathon.auth.sso;

import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth/sso")
public class SsoProviderController {
    private final ObjectProvider<ClientRegistrationRepository> registrations;

    public SsoProviderController(ObjectProvider<ClientRegistrationRepository> registrations) {
        this.registrations = registrations;
    }

    @GetMapping("/providers")
    public Map<String, Object> providers() {
        boolean enabled = registrations.getIfAvailable() != null;
        return Map.of("enabled", enabled,
                "providers", enabled ? List.of("google", "github") : List.of());
    }
}
