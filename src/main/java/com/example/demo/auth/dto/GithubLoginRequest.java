package com.example.demo.auth.dto;

import jakarta.validation.constraints.NotBlank;

public record GithubLoginRequest(
        @NotBlank String code,
        @NotBlank String redirectUri,
        String codeVerifier
) {
}
