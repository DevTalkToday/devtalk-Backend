package com.example.demo.config;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

public final class CorsOriginUtils {
    private static final String DEFAULT_ALLOWED_ORIGIN = "http://localhost:3000";

    private CorsOriginUtils() {
    }

    public static List<String> parseAllowedOrigins(String configuredAllowedOrigins) {
        if (configuredAllowedOrigins == null || configuredAllowedOrigins.isBlank()) {
            return List.of(DEFAULT_ALLOWED_ORIGIN);
        }

        List<String> origins = Arrays.stream(configuredAllowedOrigins.split(","))
                .map(String::trim)
                .filter(origin -> !origin.isEmpty())
                .distinct()
                .toList();

        return origins.isEmpty() ? List.of(DEFAULT_ALLOWED_ORIGIN) : origins;
    }

    public static boolean containsLocalDevelopmentOrigin(String configuredAllowedOrigins) {
        return parseAllowedOrigins(configuredAllowedOrigins).stream()
                .anyMatch(CorsOriginUtils::isLocalDevelopmentOrigin);
    }

    private static boolean isLocalDevelopmentOrigin(String originPattern) {
        String normalized = originPattern.toLowerCase(Locale.ROOT);
        return normalized.contains("localhost")
                || normalized.contains("127.0.0.1")
                || normalized.contains("[::1]");
    }
}
