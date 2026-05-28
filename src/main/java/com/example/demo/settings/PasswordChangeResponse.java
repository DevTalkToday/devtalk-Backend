package com.example.demo.settings;

import java.time.Instant;

public record PasswordChangeResponse(
        Instant changedAt
) {
}
