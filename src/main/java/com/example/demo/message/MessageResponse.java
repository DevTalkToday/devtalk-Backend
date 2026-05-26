package com.example.demo.message;

import java.time.Instant;

public record MessageResponse(
        Long id,
        MessageUserResponse sender,
        MessageUserResponse recipient,
        String body,
        Instant createdAt,
        Instant readAt,
        boolean mine
) {
}
