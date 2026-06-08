package com.example.demo.auth.dto;

public record EmailVerificationRequestResponse(
        String email,
        long expiresInSeconds,
        boolean emailSent,
        String debugCode
) {
}
