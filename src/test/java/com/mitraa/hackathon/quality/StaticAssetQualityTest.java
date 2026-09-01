package com.mitraa.hackathon.quality;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class StaticAssetQualityTest {
    private String resource(String path) throws IOException {
        try (var stream = getClass().getResourceAsStream(path)) {
            assertThat(stream).as("Resource %s", path).isNotNull();
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    @Test
    void everyHtmlPageLoadsSharedQualityLayer() throws IOException {
        var pages = ListHolder.PAGES;
        for (String page : pages) {
            var html = resource("/static/" + page);
            assertThat(html).contains("href=\"quality.css\"");
            assertThat(html).contains("src=\"quality.js\"");
            assertThat(html).contains("name=\"viewport\"");
        }
    }

    @Test
    void homepageContainsOfficialSupportChannels() throws IOException {
        var html = resource("/static/index.html");
        assertThat(html).contains("mailto:info@mitratechgroup.com");
        assertThat(html).contains("tel:+919938330784");
        assertThat(html).contains("linkedin.com/company/mitra-technology-pvt-ltd");
        assertThat(html).contains("instagram.com/mymitraa");
    }

    @Test
    void qualityAssetsArePresent() throws IOException {
        assertThat(resource("/static/quality.css")).contains(":focus-visible", "prefers-reduced-motion");
        assertThat(resource("/static/quality.js")).contains("skip-link", "aria-current");
    }

    private static final class ListHolder {
        private static final String[] PAGES = {
                "index.html", "arenas.html", "mission.html", "quest.html",
                "register.html", "login.html", "verify-email.html", "forgot-password.html",
                "dashboard.html", "admin.html", "privacy.html", "terms.html", "rules.html",
                "code-of-conduct.html", "refund-policy.html", "child-safety.html"
        };
    }
}
