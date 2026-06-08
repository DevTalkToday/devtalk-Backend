package com.example.demo.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.cors.CorsConfiguration;

class SecurityConfigTest {
    @Test
    void corsConfigurationAllowsConfiguredVercelPreviewOrigins() {
        SecurityConfig securityConfig = new SecurityConfig(
                "http://localhost:3000,https://devtalk.kr,https://*.vercel.app"
        );

        CorsConfiguration configuration = securityConfig.corsConfigurationSource()
                .getCorsConfiguration(new MockHttpServletRequest("GET", "/auth/me"));

        assertEquals("https://preview-123.vercel.app", configuration.checkOrigin("https://preview-123.vercel.app"));
        assertEquals("https://devtalk.kr", configuration.checkOrigin("https://devtalk.kr"));
        assertNull(configuration.checkOrigin("https://malicious.example.com"));
    }
}
