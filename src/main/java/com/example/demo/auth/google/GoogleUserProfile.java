package com.example.demo.auth.google;

public record GoogleUserProfile(
        String providerUserId,
        String email,
        String name
) {
}
