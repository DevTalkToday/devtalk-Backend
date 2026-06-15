package com.example.demo.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

class AuthRateLimitServiceTest {
    private final AuthRateLimitService service = new AuthRateLimitService(Clock.fixed(Instant.parse("2026-06-15T00:00:00Z"), ZoneOffset.UTC));

    @Test
    void checkLoginIdentifierAllowedRejectsSixthAttempt() {
        for (int attempt = 0; attempt < 5; attempt += 1) {
            assertDoesNotThrow(() -> service.checkLoginIdentifierAllowed("user@example.com"));
        }

        ResponseStatusException error = assertThrows(
                ResponseStatusException.class,
                () -> service.checkLoginIdentifierAllowed("user@example.com")
        );

        assertEquals(HttpStatus.TOO_MANY_REQUESTS, error.getStatusCode());
        assertEquals("Too many login attempts", error.getReason());
    }

    @Test
    void resetLoginIdentifierClearsThrottleWindow() {
        for (int attempt = 0; attempt < 5; attempt += 1) {
            service.checkLoginIdentifierAllowed("user@example.com");
        }

        service.resetLoginIdentifier("user@example.com");

        assertDoesNotThrow(() -> service.checkLoginIdentifierAllowed("user@example.com"));
    }

    @Test
    void checkGuestTokenIpAllowedRejectsTwentyFirstAttempt() {
        for (int attempt = 0; attempt < 20; attempt += 1) {
            assertDoesNotThrow(() -> service.checkGuestTokenIpAllowed("203.0.113.10"));
        }

        ResponseStatusException error = assertThrows(
                ResponseStatusException.class,
                () -> service.checkGuestTokenIpAllowed("203.0.113.10")
        );

        assertEquals(HttpStatus.TOO_MANY_REQUESTS, error.getStatusCode());
        assertEquals("Guest token request is too frequent", error.getReason());
    }
}
