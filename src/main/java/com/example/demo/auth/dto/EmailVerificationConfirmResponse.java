package com.example.demo.auth.dto;

public record EmailVerificationConfirmResponse(
        String email,
        boolean verified
) {
}
