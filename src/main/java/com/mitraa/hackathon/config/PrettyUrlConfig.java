package com.mitraa.hackathon.config;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.DispatcherType;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.io.IOException;
import java.util.Map;

@Configuration
public class PrettyUrlConfig implements WebMvcConfigurer {

    private static final Map<String, String> LEGACY_URLS = Map.ofEntries(
            Map.entry("/index.html", "/"),
            Map.entry("/arenas.html", "/arenas"),
            Map.entry("/mission.html", "/mission"),
            Map.entry("/quest.html", "/quest"),
            Map.entry("/register.html", "/register"),
            Map.entry("/login.html", "/login"),
            Map.entry("/verify-email.html", "/verify-email"),
            Map.entry("/forgot-password.html", "/forgot-password"),
            Map.entry("/dashboard.html", "/dashboard"),
            Map.entry("/admin.html", "/admin"),
            Map.entry("/payment.html", "/payment"),
            Map.entry("/profile.html", "/profile"),
            Map.entry("/change-password.html", "/change-password"),
            Map.entry("/privacy.html", "/privacy"),
            Map.entry("/terms.html", "/terms"),
            Map.entry("/rules.html", "/rules"),
            Map.entry("/code-of-conduct.html", "/code-of-conduct"),
            Map.entry("/refund-policy.html", "/refund-policy"),
            Map.entry("/child-safety.html", "/child-safety")
    );

    @Override
    public void addViewControllers(ViewControllerRegistry registry) {
        registry.addViewController("/").setViewName("forward:/index.html");
        registry.addViewController("/arenas").setViewName("forward:/arenas.html");
        registry.addViewController("/mission").setViewName("forward:/mission.html");
        registry.addViewController("/quest").setViewName("forward:/quest.html");
        registry.addViewController("/register").setViewName("forward:/register.html");
        registry.addViewController("/login").setViewName("forward:/login.html");
        registry.addViewController("/verify-email").setViewName("forward:/verify-email.html");
        registry.addViewController("/forgot-password").setViewName("forward:/forgot-password.html");
        registry.addViewController("/dashboard").setViewName("forward:/dashboard.html");
        registry.addViewController("/admin").setViewName("forward:/admin.html");
        registry.addViewController("/payment").setViewName("forward:/payment.html");
        registry.addViewController("/profile").setViewName("forward:/profile.html");
        registry.addViewController("/change-password").setViewName("forward:/change-password.html");
        registry.addViewController("/privacy").setViewName("forward:/privacy.html");
        registry.addViewController("/terms").setViewName("forward:/terms.html");
        registry.addViewController("/rules").setViewName("forward:/rules.html");
        registry.addViewController("/code-of-conduct").setViewName("forward:/code-of-conduct.html");
        registry.addViewController("/refund-policy").setViewName("forward:/refund-policy.html");
        registry.addViewController("/child-safety").setViewName("forward:/child-safety.html");
    }

    /**
     * Redirects old public .html URLs only on the browser's original request.
     * Internal forwards from the clean routes are DispatcherType.FORWARD and are
     * deliberately ignored, preventing a redirect loop.
     */
    @Bean
    @Order(Ordered.HIGHEST_PRECEDENCE)
    public Filter legacyHtmlUrlRedirectFilter() {
        return (ServletRequest request, ServletResponse response, FilterChain chain) -> {
            if (request.getDispatcherType() == DispatcherType.REQUEST
                    && request instanceof HttpServletRequest httpRequest
                    && response instanceof HttpServletResponse httpResponse) {
                String path = httpRequest.getRequestURI();
                String target = LEGACY_URLS.get(path);
                if (target != null) {
                    String query = httpRequest.getQueryString();
                    if (query != null && !query.isBlank()) {
                        target += "?" + query;
                    }
                    httpResponse.sendRedirect(target);
                    return;
                }
            }
            chain.doFilter(request, response);
        };
    }
}
