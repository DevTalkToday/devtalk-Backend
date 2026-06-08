package com.example.demo.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class CorsOriginUtilsTest {
    @Test
    void parseAllowedOriginsSplitsCommaSeparatedOrigins() {
        List<String> origins = CorsOriginUtils.parseAllowedOrigins(
                "http://localhost:3000, https://devtalk.kr , https://*.vercel.app"
        );

        assertEquals(
                List.of("http://localhost:3000", "https://devtalk.kr", "https://*.vercel.app"),
                origins
        );
    }

    @Test
    void containsLocalDevelopmentOriginDetectsAnyLocalEntry() {
        assertTrue(CorsOriginUtils.containsLocalDevelopmentOrigin("https://devtalk.kr,http://localhost:3000"));
        assertFalse(CorsOriginUtils.containsLocalDevelopmentOrigin("https://devtalk.kr,https://*.vercel.app"));
    }
}
