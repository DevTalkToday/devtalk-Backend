package com.example.demo.auth;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AuthRateLimitService {
    private static final int MAX_LOGIN_ATTEMPTS_PER_IDENTIFIER = 5;
    private static final int MAX_LOGIN_ATTEMPTS_PER_IP = 25;
    private static final Duration LOGIN_WINDOW = Duration.ofMinutes(15);
    private static final int MAX_GUEST_TOKEN_REQUESTS_PER_IP = 20;
    private static final Duration GUEST_TOKEN_WINDOW = Duration.ofMinutes(1);
    private static final String LOGIN_THROTTLED_REASON = "Too many login attempts";
    private static final String GUEST_TOKEN_THROTTLED_REASON = "Guest token request is too frequent";

    private final Clock clock;
    private final ConcurrentHashMap<String, Deque<Instant>> attemptsByKey = new ConcurrentHashMap<>();

    public AuthRateLimitService() {
        this(Clock.systemUTC());
    }

    AuthRateLimitService(Clock clock) {
        this.clock = clock;
    }

    public void checkLoginIdentifierAllowed(String identifier) {
        checkAllowed(bucketKey("login:identifier", normalizeIdentifier(identifier)), MAX_LOGIN_ATTEMPTS_PER_IDENTIFIER, LOGIN_WINDOW,
                LOGIN_THROTTLED_REASON);
    }

    public void checkLoginIpAllowed(String clientIp) {
        checkAllowed(bucketKey("login:ip", normalizeClientIp(clientIp)), MAX_LOGIN_ATTEMPTS_PER_IP, LOGIN_WINDOW, LOGIN_THROTTLED_REASON);
    }

    public void resetLoginIdentifier(String identifier) {
        attemptsByKey.remove(bucketKey("login:identifier", normalizeIdentifier(identifier)));
    }

    public void checkGuestTokenIpAllowed(String clientIp) {
        checkAllowed(bucketKey("guest:ip", normalizeClientIp(clientIp)), MAX_GUEST_TOKEN_REQUESTS_PER_IP, GUEST_TOKEN_WINDOW,
                GUEST_TOKEN_THROTTLED_REASON);
    }

    private void checkAllowed(String key, int limit, Duration window, String reason) {
        if (!tryConsume(key, limit, window)) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, reason);
        }
    }

    private boolean tryConsume(String key, int limit, Duration window) {
        Instant now = clock.instant();
        Deque<Instant> attempts = attemptsByKey.computeIfAbsent(key, unused -> new ArrayDeque<>());

        synchronized (attempts) {
            pruneExpiredAttempts(attempts, now, window);
            if (attempts.size() >= limit) {
                return false;
            }

            attempts.addLast(now);
            return true;
        }
    }

    private static void pruneExpiredAttempts(Deque<Instant> attempts, Instant now, Duration window) {
        Instant cutoff = now.minus(window);
        while (!attempts.isEmpty() && attempts.peekFirst().isBefore(cutoff)) {
            attempts.removeFirst();
        }
    }

    private static String bucketKey(String namespace, String value) {
        return namespace + ":" + value;
    }

    private static String normalizeIdentifier(String value) {
        String normalized = value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
        return normalized.isBlank() ? "<blank>" : normalized;
    }

    private static String normalizeClientIp(String value) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isEmpty()) {
            return "<unknown>";
        }

        int separatorIndex = normalized.indexOf(',');
        if (separatorIndex >= 0) {
            normalized = normalized.substring(0, separatorIndex).trim();
        }

        return normalized.isEmpty() ? "<unknown>" : normalized;
    }
}
